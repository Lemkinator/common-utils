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

/**
 * What the common-utils settings screen needs from the app's Hilt graph to offer a backup. `CommonUtilsBackupModule`
 * binds it; the screen shows the Backup category only if the graph holds it.
 */
public class BackupScreenDeps internal constructor(
    internal val engine: BackupEngine,
    internal val documents: BackupDocuments,
    internal val sectionLabels: Set<BackupSectionLabel>,
    internal val dispatcher: CoroutineDispatcher,
)
