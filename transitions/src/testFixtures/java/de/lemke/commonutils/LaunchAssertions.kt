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

import android.content.Intent
import android.os.Bundle
import de.lemke.commonutils.ui.utils.DEFAULT_DURATION
import de.lemke.commonutils.ui.utils.DEFAULT_FADE_MODE
import de.lemke.commonutils.ui.utils.DEFAULT_TRANSITION_NAME

private const val ANIM_TYPE_KEY = "android:activity.animType"
private const val ANIM_SCENE_TRANSITION = 5

private val Bundle?.startsSceneTransition: Boolean get() = this?.getInt(ANIM_TYPE_KEY) == ANIM_SCENE_TRANSITION

/**
 * Fails unless this started intent and its [options] are the pop-over launch of `popOverOrTransformToActivity` for [cls]:
 * single-top, no container transform and no scene transition. A Robolectric test passes the `intent` and `options` of
 * the `ShadowActivity.IntentForResult` it reads.
 */
public fun Intent.shouldBePopOverTo(
    cls: Class<*>,
    options: Bundle?,
) {
    shouldTarget(cls)
    shouldHaveNoContainerTransform()
    if (flags and Intent.FLAG_ACTIVITY_SINGLE_TOP == 0) {
        throw AssertionError("Expected a single-top pop-over launch of <${cls.name}> but the intent flags are $flags")
    }
    if (options == null || options.startsSceneTransition) {
        throw AssertionError("Expected a pop-over launch of <${cls.name}> with plain activity options but options were $options")
    }
}

/**
 * Fails unless this started intent and its [options] are the container transform of `View.transformToActivity`, or the
 * non-pop-over arm of `popOverOrTransformToActivity`, to [cls]: [transitionName], [duration] and [fadeMode] as extras, no
 * single-top flag and a scene transition in the options. The defaults are those of the launch functions. A Robolectric
 * test passes the `intent` and `options` of the `ShadowActivity.IntentForResult` it reads.
 */
public fun Intent.shouldBeContainerTransformTo(
    cls: Class<*>,
    options: Bundle?,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    shouldTarget(cls)
    shouldBeContainerTransformTo(transitionName, duration, fadeMode)
    if (flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0) {
        throw AssertionError("Expected a container transform to <${cls.name}> without a single-top flag but the intent flags are $flags")
    }
    if (!options.startsSceneTransition) {
        throw AssertionError("Expected a container transform to <${cls.name}> with a scene transition but options were $options")
    }
}

private fun Intent.shouldTarget(cls: Class<*>) {
    val actual = component?.className
    if (actual != cls.name) throw AssertionError("Expected a launch of <${cls.name}> but was <$actual>")
}
