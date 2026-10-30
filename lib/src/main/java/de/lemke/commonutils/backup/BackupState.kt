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
package de.lemke.commonutils.backup

import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.BackupPreview
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupRefusal
import de.lemke.commonutils.backup.core.RestoreReport

/**
 * State of the export or import that a [BackupStateHolder] runs.
 *
 * A RESUMED collector acts on each state and reports it to [BackupStateHolder.onHandled] once its launch is settled.
 * [Preview] and [ConfirmReplace] stay until the user decides, so a rotation shows the same dialog again.
 */
public sealed interface BackupState {
    /** No export or import runs and no result waits. */
    public data object Idle : BackupState

    /** An export or import runs; the holder ignores new ones until it finishes. */
    public data object Working : BackupState

    /** The screen opens the create-document picker for a file named after [fileName] with [extension], and adds the timestamp. */
    public data class PickExportTarget(val fileName: String, val extension: BackupFileExtension) : BackupState

    /** The screen opens the open-document picker. */
    public data object PickImportFile : BackupState

    /** The screen shows [preview] and offers the modes in [BackupPreview.modes]. */
    public class Preview internal constructor(
        public val preview: BackupPreview,
        internal val ready: BackupReadResult.Ready,
    ) : BackupState

    /** The screen warns that Replace deletes the current data of the sections in [preview]. */
    public class ConfirmReplace internal constructor(
        public val preview: BackupPreview,
        internal val ready: BackupReadResult.Ready,
    ) : BackupState

    /** An export or import ended; the screen shows [result] and reports it handled. */
    public data class Result(val result: BackupResult) : BackupState
}

public sealed interface BackupResult {
    public data object Exported : BackupResult

    public data object ExportFailed : BackupResult

    /** The data exceeds a limit of the import, so the export wrote no backup. */
    public data object ExportTooLarge : BackupResult

    public data class Restored(val report: RestoreReport) : BackupResult

    /** The file imports nowhere, for the reason in [reason]. Nothing was written. */
    public data class Refused(val reason: BackupRefusal) : BackupResult

    /** The file is unreadable, no zip, or larger than the size cap. */
    public data object ReadFailed : BackupResult

    /** The restore threw; the transaction rolled back, so no section stays wiped. */
    public data object RestoreFailed : BackupResult
}
