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

/** Why a file imports nowhere. A refusal happens before any write. */
public sealed interface BackupRefusal {
    /** No manifest, bad JSON, a wrong kind, or a bad section file. */
    public data object NotABackup : BackupRefusal

    public data class OtherApp(val app: String) : BackupRefusal

    public data class NewerFormat(val format: Int) : BackupRefusal

    public data class NewerSection(val id: String, val version: Int) : BackupRefusal

    public data class TooOldSection(val id: String, val version: Int) : BackupRefusal

    /** No section of the file is known to this app version, so there is nothing to restore. */
    public data object NoKnownSection : BackupRefusal

    /** A section file exceeds the limits on entries, nesting depth or values per entry. */
    public data object TooLarge : BackupRefusal

    /** A signing app got a file that is unsigned or changed since export. */
    public data object Tampered : BackupRefusal
}
