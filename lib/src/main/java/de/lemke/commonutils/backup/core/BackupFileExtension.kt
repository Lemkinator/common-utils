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
package de.lemke.commonutils.backup.core

private val EXTENSION_FORM = Regex("[.][a-z0-9]{1,16}")

/**
 * The file extension under which an app saves its backups, a dot and 1 to 16 lowercase letters or digits, such as `".oneurl"`.
 * The content stays a zip; the extension only names the file.
 */
@JvmInline
public value class BackupFileExtension(public val value: String) {
    init {
        require(EXTENSION_FORM.matches(value)) { "A backup file extension is a dot and 1 to 16 lowercase letters or digits, was '$value'" }
    }

    override fun toString(): String = value
}
