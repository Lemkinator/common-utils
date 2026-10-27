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

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import dagger.multibindings.Multibinds
import de.lemke.commonutils.backup.BackupDocuments
import de.lemke.commonutils.backup.DefaultBackupDocuments
import de.lemke.commonutils.backup.backupTimestamp
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupSigner
import de.lemke.commonutils.backup.core.BackupTransaction
import java.time.OffsetDateTime
import java.util.Optional

/**
 * Opt-in Hilt bindings for the backup: a [BackupEngine] over the sections the app binds, and [BackupDocuments].
 *
 * It installs itself nowhere. An app includes it from its own module and binds [BackupApp], [BackupTransaction] and its
 * sections with `@Binds @IntoSet`; a [BackupSigner] binding makes it sign. It needs the bindings of
 * [CommonUtilsCoroutinesModule] for `@IoDispatcher`.
 * ```
 * @Module(includes = [CommonUtilsBackupModule::class])
 * @InstallIn(SingletonComponent::class)
 * interface AppBackupModule
 * ```
 */
@Module
@DisableInstallInCheck
public interface CommonUtilsBackupModule {
    @Multibinds
    public fun sections(): Set<@JvmSuppressWildcards BackupSection<*>>

    @Binds
    public fun documents(impl: DefaultBackupDocuments): BackupDocuments

    @BindsOptionalOf
    public fun signer(): BackupSigner

    public companion object {
        @Provides
        public fun engine(
            sections: Set<@JvmSuppressWildcards BackupSection<*>>,
            transaction: BackupTransaction,
            app: BackupApp,
            signer: Optional<BackupSigner>,
        ): BackupEngine = BackupEngine(sections, transaction, app, { backupTimestamp(OffsetDateTime.now()) }, signer.orElse(null))
    }
}
