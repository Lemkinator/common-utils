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

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText

/** Runs [block] on a Konsist scope of [sources] (file name to content) written to a temp directory, and returns the directory name too. */
internal fun <T> withSourceScope(
    sources: Map<String, String>,
    block: (scope: KoScope, directoryName: String) -> T,
): T {
    val dir = createTempDirectory(Path("build", "tmp").toAbsolutePath(), "konsist-probe")
    try {
        sources.forEach { (name, content) -> dir.resolve(name).writeText(content) }
        return block(Konsist.scopeFromExternalDirectories(listOf(dir.toString())), dir.name)
    } finally {
        dir.toFile().deleteRecursively()
    }
}

internal fun <T> withSourceScope(
    source: String,
    block: (scope: KoScope) -> T,
): T = withSourceScope(mapOf("Probe.kt" to source)) { scope, _ -> block(scope) }
