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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.DrainMainLooperRule
import de.lemke.commonutils.R
import de.lemke.commonutils.backup.BackupScreenDeps
import de.lemke.commonutils.backup.BackupSectionLabel
import de.lemke.commonutils.backup.BackupState
import de.lemke.commonutils.backup.BackupViewModel
import de.lemke.commonutils.backup.FakeBackupDocuments
import de.lemke.commonutils.backup.HmacSha256BackupSigner
import de.lemke.commonutils.backup.ZipBackupContainer
import de.lemke.commonutils.backup.ZipRead
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.Item
import de.lemke.commonutils.backup.core.MemorySection
import de.lemke.commonutils.backup.core.MergeableMemorySection
import de.lemke.commonutils.backup.core.SectionInfo
import de.lemke.commonutils.backup.core.backupFilesOf
import de.lemke.commonutils.backup.core.entriesJson
import de.lemke.commonutils.backup.core.item
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.receivePickerResult
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import de.lemke.commonutils.test.R as TestR

private val exportUri: Uri = "content://documents/export".toUri()
private val importUri: Uri = "content://documents/import".toUri()

private object DirectTransaction : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

private fun engineOf(vararg sections: BackupSection<*>) =
    BackupEngine(sections.toSet(), DirectTransaction, BackupApp("oneurl", 100), { "2026-10-27T14:30:00+01:00" })

private fun zip(files: Map<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
        .also {
            ZipBackupContainer.write(files, it)
        }.toByteArray()

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class BackupPreferencesRobolectricTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    private val fakeDocuments = FakeBackupDocuments()

    private fun screenOf(engine: BackupEngine) =
        BackupScreenDeps(
            engine,
            fakeDocuments,
            setOf(BackupSectionLabel("items", TestR.string.test_section_label_urls)),
        )

    private val store = mutableListOf<Item>()

    @BindValue
    @JvmField
    var backupScreen: BackupScreenDeps = screenOf(engineOf(MergeableMemorySection(store = store)))

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        CommonUtilsSettingsActivity.preferences = emptyList()
        CommonUtilsSettingsActivity.relativeLinksCard = true
    }

    private fun launch(block: (CommonUtilsSettingsActivity) -> Unit) {
        CommonUtilsSettingsActivity.relativeLinksCard = false
        CommonUtilsSettingsActivity.preferences = listOf(R.xml.preferences_backup)
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { it.onActivity(block) }
    }

    private fun fragmentOf(activity: CommonUtilsSettingsActivity) =
        activity.supportFragmentManager.fragments
            .filterIsInstance<PreferenceFragmentCompat>()
            .first()

    private fun Context.isOwnedBy(activity: Activity): Boolean =
        generateSequence(this) { (it as? ContextWrapper)?.baseContext }.any { it === activity }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun CommonUtilsSettingsActivity.click(keyRes: Int) {
        val fragment = fragmentOf(this)
        val preference = fragment.findPreference<Preference>(getString(keyRes))!!
        Preference::class.java
            .getDeclaredMethod("callClickListener")
            .also { it.isAccessible = true }
            .invoke(preference)
        idle()
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun AlertDialog.message(): String = findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun AlertDialog.click(button: Int) {
        getButton(button).performClick()
        idle()
    }

    private fun CommonUtilsSettingsActivity.pickImportFile(vararg lines: String) {
        fakeDocuments.files[importUri] = zip(backupFilesOf(SectionInfo("items", 1, lines.size) to entriesJson(*lines)))
        click(R.string.commonutils_preference_key_import_backup)
        receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
        idle()
    }

    @Test
    fun `export click opens the create-document picker with a zip name`() {
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_export_backup)
            val picker = activity.receivePickerResult(Activity.RESULT_CANCELED, null)
            picker.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            picker.type shouldBe "application/zip"
            picker.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch Regex(".*_backup_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.zip")
        }
    }

    @Test
    fun `import click opens the open-document picker for a zip or an octet stream`() {
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_import_backup)
            val picker = activity.receivePickerResult(Activity.RESULT_CANCELED, null)
            picker.action shouldBe Intent.ACTION_OPEN_DOCUMENT
            picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toList() shouldContainExactly
                listOf("application/zip", "application/octet-stream")
        }
    }

    @Test
    fun `preview dialog shows the skipped count, the modes text and Merge and Replace`() {
        launch { activity ->
            activity.pickImportFile(item("a"), item("bad", -1), item("b"))

            val dialog = latestDialog()
            dialog.message() shouldContain "Saved links: 2 entries, 1 broken entry skipped"
            dialog.message() shouldContain
                "Merge adds the backup to your data. Entries you deleted since then come back. Replace swaps all data for the backup."
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString() shouldBe "Merge"
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString() shouldBe "Replace"
        }
    }

    @Test
    fun `replace-only preview shows no Merge button and no modes text`() {
        backupScreen = screenOf(engineOf(MemorySection(store = store)))
        launch { activity ->
            activity.pickImportFile(item("a"))

            val dialog = latestDialog()
            dialog.message() shouldNotContain "Merge"
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString() shouldBe "Replace"
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility shouldBe android.view.View.GONE
        }
    }

    @Test
    fun `preview names a skipped unknown section`() {
        launch { activity ->
            fakeDocuments.files[importUri] =
                zip(backupFilesOf(SectionInfo("items", 1, 0) to entriesJson(), SectionInfo("extra", 1, 0) to entriesJson()))
            activity.click(R.string.commonutils_preference_key_import_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
            idle()

            latestDialog().message() shouldContain "This backup holds parts this app version does not know: extra. They are skipped."
        }
    }

    @Test
    fun `Replace shows the data-loss warning before any write`() {
        store += Item("local")
        launch { activity ->
            activity.pickImportFile(item("a"))

            latestDialog().click(AlertDialog.BUTTON_NEUTRAL)

            val warning = latestDialog()
            warning.message() shouldBe
                "Attention: This deletes the current data of the parts in this backup and replaces it with the backup. This cannot be undone."
            store shouldContainExactly listOf(Item("local"))

            warning.click(AlertDialog.BUTTON_POSITIVE)

            store shouldContainExactly listOf(Item("a"))
            latestDialog().message() shouldBe "Backup imported: 1 entry restored, 1 previous entry removed, 0 broken entries skipped."
        }
    }

    @Test
    fun `canceling the preview writes nothing`() {
        launch { activity ->
            activity.pickImportFile(item("a"))

            latestDialog().click(AlertDialog.BUTTON_NEGATIVE)

            store shouldBe emptyList()
        }
    }

    @Test
    fun `a newer section shows the update-the-app text`() {
        launch { activity ->
            fakeDocuments.files[importUri] = zip(backupFilesOf(SectionInfo("items", 2, 1) to entriesJson(item("a"))))
            activity.click(R.string.commonutils_preference_key_import_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
            idle()

            latestDialog().message() shouldBe "This backup was made with a newer version of the app. Please update the app."
            store shouldBe emptyList()
        }
    }

    @Test
    fun `an export then an import restores the entries`() {
        store += listOf(Item("a", 1), Item("b", 2))
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_export_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(exportUri))
            idle()
            latestDialog().message() shouldBe "Backup exported"
            latestDialog().click(AlertDialog.BUTTON_POSITIVE)
            store.clear()
            fakeDocuments.files[importUri] = fakeDocuments.files.getValue(exportUri)

            activity.click(R.string.commonutils_preference_key_import_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
            idle()
            latestDialog().click(AlertDialog.BUTTON_POSITIVE)

            store shouldContainExactly listOf(Item("a", 1), Item("b", 2))
            latestDialog().message() shouldBe "Backup imported: 2 added, 0 updated, 0 kept, 0 broken entries skipped."
        }
    }

    @Test
    fun `settings activity with a signer binding refuses an edited export`() {
        val signer = HmacSha256BackupSigner("test", "Jefe".encodeToByteArray())
        backupScreen =
            screenOf(
                BackupEngine(setOf(MergeableMemorySection(store = store)), DirectTransaction, BackupApp("oneurl", 100), {
                    "2026-10-27T14:30:00+01:00"
                }, signer),
            )
        store += Item("a", 1)
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_export_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(exportUri))
            idle()
            latestDialog().click(AlertDialog.BUTTON_POSITIVE)
            val files = (ZipBackupContainer.read(ByteArrayInputStream(fakeDocuments.files.getValue(exportUri))) as ZipRead.Files).files
            val edited =
                files
                    .getValue("sections/items.json")
                    .decodeToString()
                    .replace("\"value\":1", "\"value\":99")
                    .encodeToByteArray()

            fakeDocuments.files[importUri] = zip(files + ("sections/items.json" to edited))
            activity.click(R.string.commonutils_preference_key_import_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
            idle()
            latestDialog().message() shouldBe "This backup was changed outside the app."

            latestDialog().click(AlertDialog.BUTTON_POSITIVE)
            fakeDocuments.files[importUri] = fakeDocuments.files.getValue(exportUri)
            activity.click(R.string.commonutils_preference_key_import_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(importUri))
            idle()
            latestDialog().message() shouldContain "Saved links: 1 entry"
        }
    }

    @Test
    fun `a failed export write shows the export error`() {
        fakeDocuments.failWrite = true
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_export_backup)
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(exportUri))
            idle()

            latestDialog().message() shouldBe "Error exporting backup"
        }
    }

    @Test
    fun `recreating the activity during the preview shows the preview once and keeps the file`() {
        CommonUtilsSettingsActivity.relativeLinksCard = false
        CommonUtilsSettingsActivity.preferences = listOf(R.xml.preferences_backup)
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { it.pickImportFile(item("a")) }
            scenario.recreate()
            idle()

            scenario.onActivity {
                ShadowDialog.getShownDialogs().count { dialog -> dialog.isShowing && dialog.context.isOwnedBy(it) } shouldBe 1
                latestDialog().click(AlertDialog.BUTTON_POSITIVE)
            }
            store shouldContainExactly listOf(Item("a"))
        }
    }

    @Test
    fun `setupCommonUtilsSettingsActivity with a backup in the graph serves the backup entries`() {
        setupCommonUtilsSettingsActivity(R.xml.preferences_backup, relativeLinksCard = false)

        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            it.onActivity { activity ->
                activity.click(R.string.commonutils_preference_key_import_backup)

                activity.receivePickerResult(Activity.RESULT_CANCELED, null).action shouldBe Intent.ACTION_OPEN_DOCUMENT
            }
        }
    }

    @Test
    fun `a picker result reaches the screen restored after process death`() {
        CommonUtilsSettingsActivity.relativeLinksCard = false
        CommonUtilsSettingsActivity.preferences = listOf(R.xml.preferences_backup)
        store += Item("a")
        val first = Robolectric.buildActivity(CommonUtilsSettingsActivity::class.java).setup()
        first.get().click(R.string.commonutils_preference_key_export_backup)
        val requestCode = shadowOf(first.get()).peekNextStartedActivityForResult().requestCode
        val savedState = Bundle().also { first.saveInstanceState(it) }
        first.pause().stop().destroy()

        val second =
            Robolectric
                .buildActivity(
                    CommonUtilsSettingsActivity::class.java,
                ).create(savedState)
                .start()
                .restoreInstanceState(savedState)
        second.resume().visible()
        second.get().activityResultRegistry.dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(exportUri))
        idle()

        val files = (ZipBackupContainer.read(ByteArrayInputStream(fakeDocuments.files.getValue(exportUri))) as ZipRead.Files).files
        files.keys shouldBe setOf("manifest.json", "sections/items.json")
        second.pause().stop().destroy()
    }

    @Test
    fun `a picker launch the latch drops keeps the request pending`() {
        launch { activity ->
            activity.click(R.string.commonutils_preference_key_export_backup)
            val holder = ViewModelProvider(activity.supportFragmentManager.fragments.first())[BackupViewModel::class.java].backup

            holder.import()
            idle()
            holder.state.value shouldBe BackupState.PickImportFile

            holder.cancel()
            holder.export("Notes")
            idle()
            holder.state.value shouldBe BackupState.PickExportTarget("Notes_backup")
        }
    }

    @Test
    fun `a settings screen without the backup entries renders and skips their binding`() {
        CommonUtilsSettingsActivity.relativeLinksCard = false
        CommonUtilsSettingsActivity.preferences = listOf(TestR.xml.preferences_empty)
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            it.onActivity { activity ->
                val fragment = fragmentOf(activity)
                fragment.isResumed shouldBe true
                fragment.preferenceScreen.preferenceCount shouldBe 0
                fragment.findPreference<Preference>(activity.getString(R.string.commonutils_preference_key_export_backup)) shouldBe null
                fragment.findPreference<Preference>(activity.getString(R.string.commonutils_preference_key_import_backup)) shouldBe null
            }
        }
    }
}
