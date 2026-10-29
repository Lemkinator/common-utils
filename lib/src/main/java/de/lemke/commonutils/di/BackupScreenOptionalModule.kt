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
package de.lemke.commonutils.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.backup.BackupScreenDeps

/** Declares [BackupScreenDeps] optional, so an app without the backup module still builds and shows no Backup category. */
@Module
@InstallIn(SingletonComponent::class)
internal interface BackupScreenOptionalModule {
    @BindsOptionalOf
    fun backupScreen(): BackupScreenDeps
}
