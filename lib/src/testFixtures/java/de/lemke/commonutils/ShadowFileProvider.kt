/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.lemke.commonutils

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_APPEND
import android.os.ParcelFileDescriptor.MODE_CREATE
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.os.ParcelFileDescriptor.MODE_READ_WRITE
import android.os.ParcelFileDescriptor.MODE_TRUNCATE
import android.os.ParcelFileDescriptor.MODE_WRITE_ONLY
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.xmlpull.v1.XmlPullParserException

/**
 * Robolectric shadow re-implementing [FileProvider]'s file-to-URI and URI-to-file mapping with every
 * path normalized to `/` separators before matching/stripping. Stock `SimplePathStrategy` compares raw
 * `File.getCanonicalPath()` strings, which are backslash-separated on Windows, so the real
 * implementation throws for every file there. Apply with
 * `@Config(shadows = [ShadowFileProvider::class])` on any Robolectric test that reaches
 * `FileProvider` for real (i.e. not `mockkStatic`'d). An unshadowed test that calls the
 * stock `getUriForFile` must call [resetFileProviderCache] in `@Before` and `@After` instead.
 *
 * Shadowed entry points: both static `getUriForFile` overloads, and the provider's `query`, `getType`,
 * `openFile` and `delete`. `ClipData.newUri` reaches `getType`, and `ContentResolver.openInputStream`
 * and `openOutputStream` reach `openFile`. Each call resolves the roots anew, so no path strategy
 * survives from an earlier test.
 *
 * The shadow always reads the authority's `android.support.FILE_PROVIDER_PATHS` `<meta-data>`. It does
 * not support a FileProvider subclass that supplies its paths through the `FileProvider(@XmlRes int)`
 * constructor without that `<meta-data>`.
 */
@Implements(FileProvider::class, isInAndroidSdk = false)
class ShadowFileProvider {
    @RealObject
    private lateinit var realProvider: FileProvider

    @Implementation
    protected fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val file = fileForUri(uri)
        val columns =
            (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
                .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        val values =
            columns.map { column ->
                if (column == OpenableColumns.DISPLAY_NAME) uri.getQueryParameter(DISPLAY_NAME_PARAMETER) ?: file.name else file.length()
            }
        return MatrixCursor(columns.toTypedArray(), 1).apply { addRow(values) }
    }

    @Implementation
    protected fun getType(uri: Uri): String {
        val name = fileForUri(uri).name
        val lastDot = name.lastIndexOf('.')
        val mimeType = if (lastDot >= 0) MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(lastDot + 1)) else null
        return mimeType ?: "application/octet-stream"
    }

    @Implementation
    protected fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = if (fileForUri(uri).delete()) 1 else 0

    @Implementation
    protected fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(fileForUri(uri), modeBits(mode))

    private fun fileForUri(uri: Uri): File {
        val path = uri.encodedPath.orEmpty()
        val splitIndex = path.indexOf('/', 1)
        require(splitIndex != -1) { "Unable to find path from root: $uri" }
        val roots = pathStrategyRoots(checkNotNull(realProvider.context), checkNotNull(uri.authority))
        val root =
            roots[Uri.decode(path.substring(1, splitIndex))]
                ?: throw IllegalArgumentException("Unable to find configured root for $uri")
        val unresolved = File(root, Uri.decode(path.substring(splitIndex + 1)))
        val file =
            try {
                unresolved.canonicalFile
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to resolve canonical path for $unresolved", e)
            }
        if (!belongsToRoot(file.path.toUnixPath(), root.path.toUnixPath())) {
            throw SecurityException("Resolved path jumped beyond configured root")
        }
        return file
    }

    companion object {
        private const val DISPLAY_NAME_PARAMETER = "displayName"

        @Implementation
        @JvmStatic
        fun getUriForFile(
            context: Context,
            authority: String,
            file: File,
        ): Uri {
            val path = file.canonicalPath.toUnixPath()
            val (name, rootPath) =
                pathStrategyRoots(context, authority)
                    .map { (name, root) -> name to root.path.toUnixPath() }
                    .filter { (_, rootPath) -> belongsToRoot(path, rootPath) }
                    .maxByOrNull { (_, rootPath) -> rootPath.length }
                    ?: throw IllegalArgumentException("Failed to find configured root that contains $path")
            val relative = if (rootPath.endsWith("/")) path.substring(rootPath.length) else path.substring(rootPath.length + 1)
            val encodedPath = "${Uri.encode(name)}/${Uri.encode(relative, "/")}"
            return Uri
                .Builder()
                .scheme("content")
                .authority(authority)
                .encodedPath(encodedPath)
                .build()
        }

        @Implementation
        @JvmStatic
        fun getUriForFile(
            context: Context,
            authority: String,
            file: File,
            displayName: String,
        ): Uri = getUriForFile(context, authority, file).buildUpon().appendQueryParameter(DISPLAY_NAME_PARAMETER, displayName).build()

        private fun belongsToRoot(
            filePath: String,
            rootPath: String,
        ): Boolean = filePath.trimEnd('/').startsWith("${rootPath.trimEnd('/')}/")

        private fun String.toUnixPath(): String = replace(File.separatorChar, '/')

        private fun modeBits(mode: String): Int =
            when (mode) {
                "r" -> MODE_READ_ONLY
                "w", "wt" -> MODE_WRITE_ONLY or MODE_CREATE or MODE_TRUNCATE
                "wa" -> MODE_WRITE_ONLY or MODE_CREATE or MODE_APPEND
                "rw" -> MODE_READ_WRITE or MODE_CREATE
                "rwt" -> MODE_READ_WRITE or MODE_CREATE or MODE_TRUNCATE
                else -> throw IllegalArgumentException("Invalid mode: $mode")
            }

        // FileProvider.parsePathStrategy is private; reflecting into it reuses its <…-path> meta-data
        // parsing and root-directory resolution instead of duplicating both. getPathStrategy would
        // return FileProvider's static per-authority cache entry, whose roots point into the data
        // directory of whichever Robolectric test populated it first.
        private fun pathStrategyRoots(
            context: Context,
            authority: String,
        ): Map<String, File> =
            try {
                val parsePathStrategy =
                    FileProvider::class.java.getDeclaredMethod(
                        "parsePathStrategy",
                        Context::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                    )
                parsePathStrategy.isAccessible = true
                val strategy = parsePathStrategy.invoke(null, context, authority, 0)
                val mRoots = strategy.javaClass.getDeclaredField("mRoots")
                mRoots.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                mRoots.get(strategy) as Map<String, File>
            } catch (e: InvocationTargetException) {
                // parsePathStrategy itself threw (e.g. no manifest <provider> for this authority) -
                // that's a real usage error, not a sign the reflected internals moved. Stock
                // getPathStrategy wraps the checked parse errors, and callers catch RuntimeException.
                throw when (val cause = e.cause) {
                    is IOException, is XmlPullParserException ->
                        IllegalArgumentException("Failed to parse android.support.FILE_PROVIDER_PATHS meta-data", cause)
                    null -> e
                    else -> cause
                }
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException(
                    "FileProvider internals changed: expected private static " +
                        "parsePathStrategy(Context,String,int) and SimplePathStrategy.mRoots",
                    e,
                )
            }
    }
}
