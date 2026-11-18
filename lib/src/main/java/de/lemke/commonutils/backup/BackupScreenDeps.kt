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

import de.lemke.commonutils.backup.core.BackupEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * The backup dependencies of the app's Hilt graph. `CommonUtilsBackupModule` binds it; the common-utils settings screen
 * shows the Backup category only if the graph holds it. An app view model injects it to host its own backup screen:
 * [stateHolder] builds the [BackupStateHolder] and [sectionLabels] names the sections of an import preview.
 */
public class BackupScreenDeps internal constructor(
    internal val engine: BackupEngine,
    internal val documents: BackupDocuments,
    /** The label of each backup section, for `bindBackupPreferences`. */
    public val sectionLabels: Set<BackupSectionLabel>,
    internal val dispatcher: CoroutineDispatcher,
) {
    /**
     * Creates a [BackupStateHolder] that runs in [scope], such as the `viewModelScope` of the view model that holds it.
     * Each call creates an independent holder with its own state. The caller owns it and keeps one per screen in its
     * view model.
     */
    public fun stateHolder(scope: CoroutineScope): BackupStateHolder = BackupStateHolder(scope, engine, documents, dispatcher)
}
