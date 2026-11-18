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
 * A [BackupScreenDeps] over [engine], [documents] and [dispatcher], for a test of a view model that injects it.
 * [sectionLabels] defaults to none. The production binding comes from `CommonUtilsBackupModule`; a Hilt test binds this
 * one with `@BindValue`.
 */
public fun testBackupScreenDeps(
    engine: BackupEngine,
    dispatcher: CoroutineDispatcher,
    documents: BackupDocuments = FakeBackupDocuments(),
    sectionLabels: Set<BackupSectionLabel> = emptySet(),
): BackupScreenDeps = BackupScreenDeps(engine, documents, sectionLabels, dispatcher)
