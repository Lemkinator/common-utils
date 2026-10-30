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
package de.lemke.commonutils.ui.utils

import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import de.lemke.commonutils.R
import de.lemke.commonutils.backup.BackupSectionLabel
import de.lemke.commonutils.backup.BackupState
import de.lemke.commonutils.backup.BackupStateHolder
import de.lemke.commonutils.backup.core.ImportMode
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "BackupPreferences"
private const val MIME_ZIP = "application/zip"
private const val MIME_OCTET_STREAM = "application/octet-stream"
private const val PREVIEW_DIALOG_TAG = "commonutils_backup_preview"
private const val REPLACE_DIALOG_TAG = "commonutils_backup_replace"
private const val RESULT_DIALOG_TAG = "commonutils_backup_result"

/**
 * Wires the "Export backup" and "Import backup" entries of `preferences_backup` to the [BackupStateHolder] that [holder]
 * returns: the pickers, the preview, the Replace warning and the result dialogs. The preview names a section by its
 * entry in [sectionLabels], or by its id if it has none.
 *
 * Call it in `onCreate`, after the preferences are inflated: it registers the picker launchers. A dialog stays tied to
 * the state of the holder, so a rotation shows it again once and keeps the parsed file.
 */
public fun PreferenceFragmentCompat.bindBackupPreferences(
    sectionLabels: Set<BackupSectionLabel>,
    holder: () -> BackupStateHolder,
) {
    val labels = sectionLabels.associate { it.sectionId to it.label }
    val exportPicker = registerForDocumentPick { holder().onExportTargetPicked(it) }
    val importPicker = registerForOpenDocument { holder().onImportFilePicked(it) }
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_export_backup))?.apply {
        onSingleLaunchClick {
            val label = requireContext().applicationInfo.loadLabel(requireContext().packageManager).toString()
            holder().export(label)
        }
    } ?: Log.w(TAG, "export backup preference is null, skipping initialization")
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_import_backup))?.apply {
        onSingleLaunchClick { holder().import() }
    } ?: Log.w(TAG, "import backup preference is null, skipping initialization")
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            holder().state.collect { state ->
                when (state) {
                    BackupState.Idle, BackupState.Working -> Unit
                    is BackupState.PickExportTarget ->
                        if (launchCreateDocument(state.fileName, MIME_OCTET_STREAM, state.extension.value, exportPicker).isSettled) {
                            holder().onHandled(state)
                        }
                    BackupState.PickImportFile ->
                        if (launchOpenDocument(MIME_ZIP, importPicker, listOf(MIME_OCTET_STREAM)).isSettled) holder().onHandled(state)
                    is BackupState.Preview -> showPreview(state, labels, holder())
                    is BackupState.ConfirmReplace -> showReplaceWarning(holder())
                    is BackupState.Result -> showResult(state, holder())
                }
            }
        }
    }
}

private fun Fragment.showPreview(
    state: BackupState.Preview,
    labels: Map<String, Int>,
    holder: BackupStateHolder,
) {
    val builder =
        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.commonutils_import_backup)
            .setMessage(requireContext().backupPreviewMessage(state.preview, labels))
            .setNegativeButton(designR.string.oui_des_common_cancel) { _, _ -> holder.cancel() }
            .setOnCancelListener { holder.cancel() }
    if (ImportMode.MERGE in state.preview.modes) {
        builder
            .setPositiveButton(R.string.commonutils_backup_merge) { _, _ -> holder.restore(ImportMode.MERGE) }
            .setNeutralButton(R.string.commonutils_backup_replace) { _, _ -> holder.restore(ImportMode.REPLACE) }
    } else {
        builder.setPositiveButton(R.string.commonutils_backup_replace) { _, _ -> holder.restore(ImportMode.REPLACE) }
    }
    builder.showOnce(PREVIEW_DIALOG_TAG)
}

private fun Fragment.showReplaceWarning(holder: BackupStateHolder) {
    AlertDialog
        .Builder(requireContext())
        .setTitle(R.string.commonutils_backup_replace_title)
        .setMessage(R.string.commonutils_backup_replace_warning)
        .setNegativeButton(designR.string.oui_des_common_cancel) { _, _ -> holder.cancel() }
        .setPositiveButton(R.string.commonutils_backup_replace) { _, _ -> holder.confirmReplace() }
        .setOnCancelListener { holder.cancel() }
        .showOnce(REPLACE_DIALOG_TAG)
}

private fun Fragment.showResult(
    state: BackupState.Result,
    holder: BackupStateHolder,
) {
    AlertDialog
        .Builder(requireContext())
        .setMessage(requireContext().backupResultMessage(state.result))
        .setPositiveButton(R.string.commonutils_ok) { _, _ -> holder.onHandled(state) }
        .setOnCancelListener { holder.onHandled(state) }
        .showOnce(RESULT_DIALOG_TAG)
}
