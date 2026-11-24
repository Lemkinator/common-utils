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
import android.content.pm.PackageManager
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Robolectric shadow re-implementing [FileProvider]'s file-to-URI and URI-to-file mapping with every
 * path normalized to `/` separators before matching/stripping. Stock `SimplePathStrategy` compares raw
 * `File.getCanonicalPath()` strings, which are backslash-separated on Windows, so the real
 * implementation throws for every file there. Apply with
 * `@Config(shadows = [ShadowFileProvider::class])` on any Robolectric test that reaches
 * `FileProvider` for real (i.e. not `mockkStatic`'d).
 *
 * Shadowed entry points: both static `getUriForFile` overloads, and the provider's `query`, `getType`,
 * `openFile` and `delete`. The provider entry points take the authority from the URI, not from the
 * provider's `android:authorities`. `ClipData.newUri` reaches `getType`, and
 * `ContentResolver.openInputStream` and `openOutputStream` reach `openFile`. Each call resolves the
 * roots anew, so no path strategy survives from an earlier test.
 *
 * The shadow always reads the authority's `android.support.FILE_PROVIDER_PATHS` `<meta-data>`. It does
 * not support a FileProvider subclass that supplies its paths through the `FileProvider(@XmlRes int)`
 * constructor without that `<meta-data>`. It does not support the `external-path` and `external-media-path`
 * tags, which stock FileProvider supports; a paths XML that declares one throws [UnsupportedOperationException].
 */
@Implements(FileProvider::class, isInAndroidSdk = false)
public open class ShadowFileProvider {
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
        val file = File(root, Uri.decode(path.substring(splitIndex + 1))).resolvedCanonical()
        if (!belongsToRoot(file.path.toUnixPath(), root.path.toUnixPath())) {
            throw SecurityException("Resolved path jumped beyond configured root")
        }
        return file
    }

    public companion object {
        private const val DISPLAY_NAME_PARAMETER = "displayName"
        private const val FILE_PROVIDER_PATHS = "android.support.FILE_PROVIDER_PATHS"

        @Implementation
        @JvmStatic
        public fun getUriForFile(
            context: Context,
            authority: String,
            file: File,
        ): Uri {
            val path = file.resolvedCanonical().path.toUnixPath()
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
        public fun getUriForFile(
            context: Context,
            authority: String,
            file: File,
            displayName: String,
        ): Uri = getUriForFile(context, authority, file).buildUpon().appendQueryParameter(DISPLAY_NAME_PARAMETER, displayName).build()

        private fun belongsToRoot(
            filePath: String,
            rootPath: String,
        ): Boolean = filePath.trimEnd('/').startsWith("${rootPath.trimEnd('/')}/")

        private fun File.resolvedCanonical(): File =
            try {
                canonicalFile
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to resolve canonical path for $this", e)
            }

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

        private fun pathStrategyRoots(
            context: Context,
            authority: String,
        ): Map<String, File> {
            val packageManager = context.packageManager
            val provider =
                requireNotNull(packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)) {
                    "Couldn't find meta-data for provider with authority $authority"
                }
            val parser =
                requireNotNull(provider.loadXmlMetaData(packageManager, FILE_PROVIDER_PATHS)) {
                    "Missing $FILE_PROVIDER_PATHS meta-data"
                }
            try {
                return parser.readRoots(context)
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to parse $FILE_PROVIDER_PATHS meta-data", e)
            } catch (e: XmlPullParserException) {
                throw IllegalArgumentException("Failed to parse $FILE_PROVIDER_PATHS meta-data", e)
            } finally {
                parser.close()
            }
        }

        private fun XmlPullParser.readRoots(context: Context): Map<String, File> {
            val roots = mutableMapOf<String, File>()
            while (next() != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) context.rootEntry(this)?.let { roots += it }
            }
            return roots
        }

        private fun Context.rootEntry(parser: XmlPullParser): Pair<String, File>? {
            val target = RootTag.of(parser.name)?.let { rootOf(it) } ?: return null
            val name = parser.getAttributeValue(null, "name")
            require(!name.isNullOrEmpty()) { "Name must not be empty" }
            val path = parser.getAttributeValue(null, "path")
            return name to (if (path == null) target else File(target, path)).resolvedCanonical()
        }

        private fun Context.rootOf(tag: RootTag): File? =
            when (tag) {
                RootTag.Root -> File("/")
                RootTag.Files -> filesDir
                RootTag.Cache -> cacheDir
                RootTag.ExternalFiles -> getExternalFilesDirs(null).firstOrNull()
                RootTag.ExternalCache -> externalCacheDirs.firstOrNull()
                RootTag.ExternalStorage, RootTag.ExternalMedia ->
                    throw UnsupportedOperationException("${tag.xmlName} is not supported by ShadowFileProvider")
            }
    }
}

private enum class RootTag(
    val xmlName: String,
) {
    Root("root-path"),
    Files("files-path"),
    Cache("cache-path"),
    ExternalFiles("external-files-path"),
    ExternalCache("external-cache-path"),
    ExternalStorage("external-path"),
    ExternalMedia("external-media-path"),
    ;

    companion object {
        fun of(xmlName: String): RootTag? = entries.firstOrNull { it.xmlName == xmlName }
    }
}
