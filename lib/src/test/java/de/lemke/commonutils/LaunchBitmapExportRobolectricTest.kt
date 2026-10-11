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
package de.lemke.commonutils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.ShareTarget
import de.lemke.commonutils.ui.utils.launchBitmapExport
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchBitmapExportRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    private val shareUri: Uri = Uri.parse("content://de.lemke.commonutils.test.fileprovider/share/icon.png")
    private val share = BitmapShareFile.Written(shareUri)

    private fun activity(): LatchTestActivity =
        Robolectric
            .buildActivity(LatchTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun Intent.streamUri(): Uri? = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)

    @Test
    fun `OpenPicker launches the document picker for a timestamped PNG and is handled`() {
        val picker = RecordingIntentLauncher()

        activity().launchBitmapExport(BitmapExport.OpenPicker("icon"), picker).shouldBeTrue()

        val intent = picker.launched.single()
        intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        intent.type shouldBe "image/png"
        intent.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch Regex("""icon_\d{4}_\d{2}_\d{2}_\d{2}_\d{2}_\d{2}\.png""")
    }

    @Test
    fun `OpenPicker whose launch the latch drops stays pending without a toast`() {
        activity().launchBitmapExport(BitmapExport.OpenPicker("icon"), RecordingIntentLauncher(admits = false)).shouldBeFalse()

        ShadowToast.shownToastCount() shouldBe 0
    }

    @Test
    fun `OpenPicker without a document picker shows the saving error once and is handled`() {
        val picker = RecordingIntentLauncher(ActivityNotFoundException("no picker"))

        activity().launchBitmapExport(BitmapExport.OpenPicker("icon"), picker).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
    }

    @Test
    fun `Share to the share sheet starts a chooser for the file and is handled`() {
        val activity = activity()

        activity.launchBitmapExport(BitmapExport.Share(share), RecordingIntentLauncher()).shouldBeTrue()

        val chooser = shadowOf(activity).nextStartedActivity.shouldNotBeNull()
        chooser.action shouldBe Intent.ACTION_CHOOSER
        val target = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java).shouldNotBeNull()
        target.action shouldBe Intent.ACTION_SEND
        target.type shouldBe "image/png"
        target.streamUri() shouldBe shareUri
    }

    @Test
    fun `Share through Quick Share starts a send intent for the file and is handled`() {
        val activity = activity()

        activity.launchBitmapExport(BitmapExport.Share(share, ShareTarget.QUICK_SHARE), RecordingIntentLauncher()).shouldBeTrue()

        val intent = shadowOf(activity).nextStartedActivity.shouldNotBeNull()
        intent.action shouldBe Intent.ACTION_SEND
        intent.type shouldBe "image/png"
        intent.streamUri() shouldBe shareUri
    }

    @Test
    fun `Share while the latch holds an earlier launch stays pending`() {
        val activity = activity()
        activity.singleLaunchActivity(Intent(activity, Activity::class.java)).shouldBeTrue()
        shadowOf(activity).clearNextStartedActivities()

        activity.launchBitmapExport(BitmapExport.Share(share), RecordingIntentLauncher()).shouldBeFalse()

        shadowOf(activity).nextStartedActivity.shouldBeNull()
    }

    @Test
    fun `SaveFinished shows the save message and is handled`() {
        activity()
            .launchBitmapExport(BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM)), RecordingIntentLauncher())
            .shouldBeTrue()

        ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
    }

    @Test
    fun `Copy sets the clip and is handled`() {
        val activity = activity()
        val clip = ClipData.newPlainText("label", "text")

        activity.launchBitmapExport(BitmapExport.Copy(clip), RecordingIntentLauncher()).shouldBeTrue()

        activity
            .getSystemService(ClipboardManager::class.java)
            .primaryClip
            .shouldNotBeNull()
            .getItemAt(0)
            .text shouldBe "text"
        ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
    }

    @Test
    fun `CopyFailed shows the sharing error once and is handled`() {
        val picker = RecordingIntentLauncher()

        activity().launchBitmapExport(BitmapExport.CopyFailed, picker).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Sharing content is not supported on your device."
        picker.launched.shouldBeEmpty()
    }

    @Test
    fun `ShareFailed shows the sharing error once and is handled`() {
        activity().launchBitmapExport(BitmapExport.ShareFailed, RecordingIntentLauncher()).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Sharing content is not supported on your device."
    }
}
