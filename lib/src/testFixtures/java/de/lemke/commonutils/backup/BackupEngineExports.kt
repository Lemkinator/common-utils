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
import de.lemke.commonutils.backup.core.BackupExport

/** The files of the export; fails the test if the export is [BackupExport.TooLarge]. */
public suspend fun BackupEngine.exportFiles(): Map<String, ByteArray> =
    when (val export = export()) {
        is BackupExport.Files -> export.files
        BackupExport.TooLarge -> error("The export exceeds a limit of the import")
    }
