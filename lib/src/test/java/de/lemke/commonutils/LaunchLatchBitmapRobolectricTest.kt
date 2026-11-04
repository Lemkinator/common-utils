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

import android.graphics.Bitmap
import android.net.Uri
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.CacheFileKind
import de.lemke.commonutils.ui.utils.LaunchOutcome
import de.lemke.commonutils.ui.utils.createBitmapShareFile
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchLatchBitmapRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `idle bitmap share input writes its file and shares it`() {
        val activity = resumed().get()

        activity
            .singleLaunchSuspending(
                work = {
                    activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png", Dispatchers.Unconfined)
                },
                then = { activity.shareBitmap(it) shouldBe LaunchOutcome.Started },
            ).shouldBeTrue()
        shadowLooper.idle()

        shadowOf(activity).nextStartedActivity.shouldNotBeNull()
        activity.cacheWriteFiles("share").map { it.name } shouldContainExactly listOf("shared.png")
    }

    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `leaving bitmap share input drops before its write`() {
        val activity = resumed().get()
        activity.launchScreen()

        activity
            .singleLaunchSuspending(
                work = { activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png") },
                then = { activity.shareBitmap(it) },
            ).shouldBeFalse()
        shadowLooper.idle()

        activity.cacheWriteFiles("share").shouldBeEmpty()
    }

    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `leaving bitmap share uri outside an input writes nothing`() =
        runTest {
            val activity = resumed().get()
            val shareFile = activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png")
            activity.shareBitmap(shareFile) shouldBe LaunchOutcome.Started
            val pending = activity.cacheWriteFiles("share").single()

            activity.createBitmapShareFile(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888), "shared.png") shouldBe
                BitmapShareFile.Dropped

            activity.cacheWriteFiles("share") shouldContainExactly listOf(pending)
        }

    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `leaving bitmap share uri outside an input keeps the file of an expired share`() =
        runTest {
            val activity = resumed().get()
            val expired = activity.expiredCacheWrite(CacheFileKind.SHARE, "expired")
            activity.launchScreen().shouldBeTrue()

            activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png") shouldBe
                BitmapShareFile.Dropped

            activity.cacheWriteFiles("share") shouldContainExactly listOf(expired)
            expired.readText() shouldBe "expired"
        }

    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `leaving bitmap share uri writes as the work of the held input but not outside it`() {
        val activity = resumed().get()
        val work = CompletableDeferred<Unit>()
        val files = mutableListOf<BitmapShareFile>()
        activity.singleLaunchSuspending(
            work = {
                work.await()
                activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png", Dispatchers.Unconfined)
            },
            then = { files += it },
        )
        activity.launchScreen().shouldBeTrue()

        runTest {
            activity.createBitmapShareFile(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "outside.png") shouldBe
                BitmapShareFile.Dropped
        }
        work.complete(Unit)
        shadowLooper.idle()

        files.single().shouldBeInstanceOf<BitmapShareFile.Written>()
        activity.cacheWriteFiles("share").map { it.name } shouldContainExactly listOf("shared.png")
    }

    @Test
    fun `leaving bitmap share and quick share drop`() {
        val activity = resumed().get()
        activity.launchScreen()
        shadowOf(activity).clearNextStartedActivities()
        val shareFile = BitmapShareFile.Written(Uri.parse("content://de.lemke.commonutils.test.fileprovider/cache_root/share/shared.png"))

        activity.shareBitmap(shareFile) shouldBe LaunchOutcome.Dropped
        activity.quickShareBitmap(shareFile) shouldBe LaunchOutcome.Dropped

        shadowOf(activity).nextStartedActivity.shouldBeNull()
    }

    @Test
    fun `leaving export picker launch drops`() {
        val activity = resumed().get()
        activity.launchScreen()
        shadowOf(activity).clearNextStartedActivities()

        activity.exportBitmap("test", activity.launcher) shouldBe LaunchOutcome.Dropped

        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }
}
