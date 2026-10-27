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
import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.EXTRA_MIME_TYPES
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R

private const val TAG = "OpenDocumentUtils"

private fun ActivityResult.toPickedUri(): Uri? = data?.data?.takeIf { resultCode == Activity.RESULT_OK }

/**
 * Registers the launcher for [launchOpenDocument]; [onPick] receives the URI of the picked document, or null when the
 * user canceled or the picker returned no URI.
 *
 * Register it like `registerForActivityResult`, before this activity is created. Its launches are gated launches; see
 * [registerForSingleLaunchResult].
 */
@MainThread
public fun ComponentActivity.registerForOpenDocument(onPick: (Uri?) -> Unit): SingleLaunchResultLauncher<Intent> =
    registerForSingleLaunchResult(StartActivityForResult()) { onPick(it.toPickedUri()) }

/**
 * Registers the launcher for [launchOpenDocument] with this fragment; [onPick] receives the URI of the picked document,
 * or null when the user canceled or the picker returned no URI.
 * @see ComponentActivity.registerForOpenDocument
 */
@MainThread
public fun Fragment.registerForOpenDocument(onPick: (Uri?) -> Unit): SingleLaunchResultLauncher<Intent> =
    registerForSingleLaunchResult(StartActivityForResult()) { onPick(it.toPickedUri()) }

/**
 * Launches the `ACTION_OPEN_DOCUMENT` picker through [launcher] to pick an existing document of [mimeType], or of one
 * of [alsoAccept].
 *
 * A device without a picker shows the error toast and returns [LaunchOutcome.Failed]. A launch the latch drops returns
 * [LaunchOutcome.Dropped], so the caller keeps its import pending until the activity resumes. Register [launcher] with
 * [registerForOpenDocument].
 */
public fun Fragment.launchOpenDocument(
    mimeType: String,
    launcher: SingleLaunchResultLauncher<Intent>,
    alsoAccept: List<String> = emptyList(),
): LaunchOutcome = requireContext().launchOpenDocument(mimeType, launcher, alsoAccept)

/**
 * Launches the `ACTION_OPEN_DOCUMENT` picker through [launcher] to pick an existing document of [mimeType], or of one
 * of [alsoAccept].
 *
 * A device without a picker shows the error toast and returns [LaunchOutcome.Failed]. A launch the latch drops returns
 * [LaunchOutcome.Dropped], so the caller keeps its import pending until the activity resumes. Register [launcher] with
 * [registerForOpenDocument].
 */
public fun Context.launchOpenDocument(
    mimeType: String,
    launcher: SingleLaunchResultLauncher<Intent>,
    alsoAccept: List<String> = emptyList(),
): LaunchOutcome =
    // Document providers and system services throw an open-ended exception set; every failure must toast, not crash.
    @Suppress("TooGenericExceptionCaught")
    try {
        val intent = Intent(ACTION_OPEN_DOCUMENT).addCategory(CATEGORY_OPENABLE)
        if (alsoAccept.isEmpty()) {
            intent.setType(mimeType)
        } else {
            intent.setType("*/*").putExtra(EXTRA_MIME_TYPES, (listOf(mimeType) + alsoAccept).toTypedArray())
        }
        val launched = launcher.tryLaunch(intent)
        if (launched) LaunchOutcome.Started else LaunchOutcome.Dropped
    } catch (e: Exception) {
        Log.e(TAG, "Error launching document picker", e)
        toast(R.string.commonutils_error_opening_content_is_not_supported_on_device)
        LaunchOutcome.Failed
    }
