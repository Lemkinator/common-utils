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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import de.lemke.commonutils.ui.utils.requireSupportActionBar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Activity whose theme has no window action bar and that installs no toolbar. */
class NoActionBarTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.CommonUtils_AppTheme)
        super.onCreate(savedInstanceState)
    }
}

/** Activity that installs a toolbar as its support action bar. */
class ToolbarTestActivity : AppCompatActivity() {
    val toolbar: Toolbar by lazy { Toolbar(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.CommonUtils_AppTheme)
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionBarUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @Test
    fun `requireSupportActionBar returns the installed support action bar`() {
        val activity =
            Robolectric
                .buildActivity(ToolbarTestActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()

        activity.requireSupportActionBar() shouldBeSameInstanceAs activity.supportActionBar
    }

    @Test
    fun `requireSupportActionBar throws when the activity has no support action bar`() {
        val activity =
            Robolectric
                .buildActivity(NoActionBarTestActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()

        val thrown = shouldThrow<IllegalStateException> { activity.requireSupportActionBar() }

        thrown.message shouldBe "The activity has no support action bar: call setSupportActionBar first"
    }
}
