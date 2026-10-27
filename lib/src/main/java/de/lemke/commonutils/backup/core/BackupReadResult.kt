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

public sealed interface BackupReadResult {
    public data class Refused(val reason: BackupRefusal) : BackupReadResult

    /** A file that passed every check. It holds the decoded entries; nothing is written yet. */
    public class Ready internal constructor(
        public val preview: BackupPreview,
        internal val sections: List<ReadSection<*>>,
    ) : BackupReadResult
}

internal class ReadSection<E : Any>(val section: BackupSection<E>, val entries: List<E>, val skipped: Int)
