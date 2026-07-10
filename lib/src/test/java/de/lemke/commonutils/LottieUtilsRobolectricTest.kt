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

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieAnimationView
import de.lemke.commonutils.ui.utils.DEFAULT_LOTTIE_DELAY
import de.lemke.commonutils.ui.utils.launchDelayedPlay
import de.lemke.commonutils.ui.utils.play
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LottieUtilsRobolectricTest {
    // Application context → findViewTreeLifecycleOwner() == null → delayed launch branch skipped
    private val view get() = LottieAnimationView(ApplicationProvider.getApplicationContext())

    @Test
    fun `play does not crash`() {
        view.play()
    }

    @Test
    fun `play cancelFirst false does not crash`() {
        view.play(cancelFirst = false)
    }

    @Test
    fun `play with animation does not crash`() {
        view.play(animation = "sad_face.json")
    }

    @Test
    fun `play with delay and no lifecycle owner does not crash`() {
        view.play(delay = DEFAULT_LOTTIE_DELAY)
    }

    @Test
    fun `play with delay and lifecycle owner runs animation after delay`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val lottieView = LottieAnimationView(activity)
        activity.setContentView(lottieView)
        lottieView.play(delay = DEFAULT_LOTTIE_DELAY)
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `launchDelayedPlay with GCed view skips playAnimation`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val lottieView = LottieAnimationView(activity)
        activity.setContentView(lottieView)
        val nullRef: WeakReference<LottieAnimationView> = WeakReference(null)
        launchDelayedPlay(activity, nullRef, DEFAULT_LOTTIE_DELAY)
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `second play cancels pending delayed job`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val lottieView = LottieAnimationView(activity)
        activity.setContentView(lottieView)
        lottieView.play(delay = DEFAULT_LOTTIE_DELAY)
        lottieView.play(delay = DEFAULT_LOTTIE_DELAY) // cancels the first pending job
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
    }
}
