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

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import de.lemke.commonutils.backup.BackupDocuments
import de.lemke.commonutils.backup.BackupScreenDeps
import de.lemke.commonutils.backup.DefaultBackupDocuments
import de.lemke.commonutils.backup.HmacSha256BackupSigner
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupSigner
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.Item
import de.lemke.commonutils.backup.core.MergeableMemorySection
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val sharedStore = mutableListOf<Item>()

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsBackupModuleHiltTest {
    /** What an app declares to opt in to [CommonUtilsBackupModule]. */
    @Module(includes = [CommonUtilsBackupModule::class])
    @InstallIn(SingletonComponent::class)
    interface AppBackupModule {
        companion object {
            @Provides
            @IntoSet
            fun section(): BackupSection<*> = MergeableMemorySection(store = sharedStore)

            @Provides
            fun app(): BackupApp = BackupApp("oneurl", 100)

            @Provides
            fun transaction(): BackupTransaction =
                object : BackupTransaction {
                    override suspend fun <R> run(block: suspend () -> R): R = block()
                }
        }
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Inject
    lateinit var engine: BackupEngine

    @Inject
    lateinit var documents: BackupDocuments

    @Inject
    lateinit var backupScreen: Optional<BackupScreenDeps>

    @Before
    fun setUp() {
        sharedStore.clear()
        hiltRule.inject()
    }

    @Test
    fun `an app that includes the module gets an engine over its sections`(): Unit =
        runBlocking {
            sharedStore += Item("a", 1)

            engine.export().keys shouldBe setOf("manifest.json", "sections/items.json")
            engine.modes shouldBe setOf(ImportMode.MERGE, ImportMode.REPLACE)
        }

    @Test
    fun `an app without a signer binding exports no signature`(): Unit =
        runBlocking {
            ("signature.json" in engine.export()) shouldBe false
        }

    @Test
    fun `the module binds the default documents`() {
        documents.shouldBeInstanceOf<DefaultBackupDocuments>()
    }

    @Test
    fun `the module makes the settings screen deps present`() {
        backupScreen.isPresent shouldBe true
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsBackupModuleSignerHiltTest {
    /** What an app declares to opt in to [CommonUtilsBackupModule] and sign its backups. */
    @Module(includes = [CommonUtilsBackupModule::class])
    @InstallIn(SingletonComponent::class)
    interface SigningBackupModule {
        companion object {
            @Provides
            @IntoSet
            fun section(): BackupSection<*> = MergeableMemorySection(store = sharedStore)

            @Provides
            fun app(): BackupApp = BackupApp("oneurl", 100)

            @Provides
            fun transaction(): BackupTransaction =
                object : BackupTransaction {
                    override suspend fun <R> run(block: suspend () -> R): R = block()
                }

            @Provides
            fun signer(): BackupSigner = HmacSha256BackupSigner("test-key", "Jefe".encodeToByteArray())
        }
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Inject
    lateinit var engine: BackupEngine

    @Before
    fun setUp() {
        sharedStore.clear()
        hiltRule.inject()
    }

    @Test
    fun `an app that binds a signer exports a signature naming its key`(): Unit =
        runBlocking {
            sharedStore += Item("a", 1)

            val files = engine.export()

            files.keys shouldBe setOf("manifest.json", "sections/items.json", "signature.json")
            files.getValue("signature.json").decodeToString() shouldContain "\"keyId\":\"test-key\""
        }
}
