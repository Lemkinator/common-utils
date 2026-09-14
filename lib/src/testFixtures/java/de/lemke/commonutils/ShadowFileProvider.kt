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
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric shadow re-implementing [FileProvider.getUriForFile] with every path normalized to
 * `/` separators before matching/stripping. Stock `SimplePathStrategy.belongsToRoot` compares raw
 * `File.getCanonicalPath()` strings, which are backslash-separated on Windows, so the real
 * implementation throws `IllegalArgumentException` for every file there. Apply with
 * `@Config(shadows = [ShadowFileProvider::class])` on any Robolectric test that calls
 * `FileProvider.getUriForFile` for real (i.e. not `mockkStatic`'d).
 */
@Implements(FileProvider::class)
class ShadowFileProvider {
    companion object {
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
        ): Uri = getUriForFile(context, authority, file).buildUpon().appendQueryParameter("displayName", displayName).build()

        private fun belongsToRoot(
            filePath: String,
            rootPath: String,
        ): Boolean = filePath.trimEnd('/').startsWith("${rootPath.trimEnd('/')}/")

        private fun String.toUnixPath(): String = replace(File.separatorChar, '/')

        // FileProvider.getPathStrategy/parsePathStrategy are private; reflecting into them reuses
        // its <…-path> meta-data parsing and root-directory resolution instead of duplicating both.
        private fun pathStrategyRoots(
            context: Context,
            authority: String,
        ): Map<String, File> {
            val getPathStrategy =
                FileProvider::class.java.getDeclaredMethod(
                    "getPathStrategy",
                    Context::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                )
            getPathStrategy.isAccessible = true
            val strategy = getPathStrategy.invoke(null, context, authority, 0)
            val mRoots = strategy.javaClass.getDeclaredField("mRoots")
            mRoots.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return mRoots.get(strategy) as Map<String, File>
        }
    }
}
