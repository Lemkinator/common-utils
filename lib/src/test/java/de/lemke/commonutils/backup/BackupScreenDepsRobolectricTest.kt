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

import androidx.core.net.toUri
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.MemorySection
import de.lemke.commonutils.ui.utils.DocumentPick
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun engine() =
    BackupEngine(
        setOf(MemorySection()),
        DirectBackupTransaction(),
        BackupApp("notes", 1, BackupFileExtension(".notes")),
        { "2026-10-27T14:30:00+01:00" },
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupScreenDepsRobolectricTest {
    @Test
    fun `testBackupScreenDeps keeps the section labels and defaults to none`() {
        val labels = setOf(BackupSectionLabel("items", 7))

        testBackupScreenDeps(engine(), UnconfinedTestDispatcher(), sectionLabels = labels).sectionLabels shouldBe labels
        testBackupScreenDeps(engine(), UnconfinedTestDispatcher()).sectionLabels shouldBe emptySet()
    }

    @Test
    fun `stateHolder builds a holder over the engine of the deps that exports into their documents`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val documents = FakeBackupDocuments()
            val holder = testBackupScreenDeps(engine(), UnconfinedTestDispatcher(testScheduler), documents).stateHolder(this)
            val target = "content://documents/export".toUri()

            holder.export("Notes")
            holder.state.value shouldBe BackupState.PickExportTarget("Notes_backup", BackupFileExtension(".notes"))
            holder.onExportTargetPicked(DocumentPick.Created(target))

            holder.state.value shouldBe BackupState.Result(BackupResult.Exported)
            documents.files.keys shouldContainExactly listOf(target)
        }
}
