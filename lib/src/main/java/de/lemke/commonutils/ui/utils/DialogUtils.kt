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
import android.app.Dialog
import android.content.Context
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle.State.STARTED
import de.lemke.commonutils.R
import dev.oneuiproject.oneui.ktx.activity
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private const val TOS_DIALOG_TAG = "commonutils_tos"

private val pendingDialogTags = WeakHashMap<FragmentManager, MutableSet<String?>>()
private val shownDialogs = WeakHashMap<Activity, MutableMap<String, WeakReference<Dialog>>>()

/**
 * Shows this dialog under [tag] unless [fragmentManager] holds a dialog with that tag that has not gone, or is about to
 * add one.
 *
 * [DialogFragment.show] commits asynchronously, so the tag counts as pending until the fragment attaches, and a fast
 * second call skips before the first dialog is added. A dismissed dialog whose removal has not run yet counts as gone.
 * @return true if this dialog was shown, false if one with [tag] exists or the state of [fragmentManager] is saved.
 */
@MainThread
fun DialogFragment.showOnce(
    fragmentManager: FragmentManager,
    tag: String,
): Boolean {
    val pendingTags =
        pendingDialogTags.getOrPut(fragmentManager) {
            mutableSetOf<String?>().also { tags -> fragmentManager.addFragmentOnAttachListener { _, fragment -> tags -= fragment.tag } }
        }
    val existing = fragmentManager.findFragmentByTag(tag) as? DialogFragment
    val canShow = !fragmentManager.isStateSaved && tag !in pendingTags && (existing == null || existing.isGone)
    if (canShow) {
        show(fragmentManager, tag)
        pendingTags += tag
    }
    return canShow
}

/**
 * A dialog fragment is gone once it is removing, or once it started without a showing dialog; before it starts, a
 * fragment restored after a configuration change has no dialog yet but shows one on start.
 */
private val DialogFragment.isGone: Boolean
    get() = isRemoving || lifecycle.currentState.isAtLeast(STARTED) && dialog?.isShowing != true

/**
 * Shows this dialog under [tag] unless the dialog last shown under [tag] in the same activity still shows.
 *
 * Dialogs under other tags never block it, so a dialog opened from another dialog's button shows. Without an activity
 * the dialog shows unguarded.
 * @return true if this dialog was shown, false if the one under [tag] still shows.
 */
@MainThread
fun Dialog.showOnce(tag: String): Boolean {
    val shown = context.activity?.let { shownDialogs.getOrPut(it, ::mutableMapOf) }
    if (shown?.get(tag)?.get()?.isShowing == true) return false
    shown?.set(tag, WeakReference(this))
    show()
    return true
}

/**
 * Creates the dialog and shows it under [tag] unless the dialog last shown under [tag] in the same activity still
 * shows; see [Dialog.showOnce].
 * @return the shown dialog, or null if the one under [tag] still shows.
 */
@MainThread
fun AlertDialog.Builder.showOnce(tag: String): AlertDialog? = create().takeIf { it.showOnce(tag) }

/** Shows the terms of service unless they already show in this context's activity. */
internal fun Context.showTosDialogOnce(): AlertDialog? =
    AlertDialog
        .Builder(this)
        .setTitle(R.string.commonutils_tos)
        .setMessage(R.string.commonutils_tos_content)
        .setPositiveButton(R.string.commonutils_ok, null)
        .showOnce(TOS_DIALOG_TAG)
