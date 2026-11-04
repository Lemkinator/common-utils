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
import java.io.StringReader
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Reads a GitHub Packages credential from `github.properties` in the root project, then from the Gradle properties,
 * then from the environment variable of the same name in UPPER_SNAKE_CASE (`ghAccessToken` -> `GH_ACCESS_TOKEN`).
 *
 * The token needs at least the `read:packages` scope: https://github.com/settings/tokens/new
 */
internal fun Project.githubProperty(key: String): String {
    val file = rootProject.layout.projectDirectory.file("github.properties")
    val fromFile =
        providers.fileContents(file).asText.map { text ->
            Properties().apply { load(StringReader(text)) }.getProperty(key)
        }
    return fromFile
        .orElse(providers.gradleProperty(key))
        .orElse(providers.environmentVariable(key.toEnvVarStyle()))
        .orNull
        ?: throw GradleException("Property $key not found")
}

private fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
