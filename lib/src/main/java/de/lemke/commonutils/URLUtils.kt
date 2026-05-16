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
@file:Suppress("unused")

package de.lemke.commonutils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.util.Log
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import java.net.URLEncoder

private const val TAG = "URLUtils"

fun String.withHttps() = if (this.startsWith("http://") || this.startsWith("https://")) this else "https://$this"

fun String.withoutHttps() = this.removePrefix("https://").removePrefix("http://").removeSuffix("/")

fun String.urlEncodeAmpersand() = this.replace("&", "%26")

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

fun Fragment.openURL(url: String?): Boolean = requireContext().openURL(url)

fun Context.openURL(url: String?): Boolean =
    try {
        if (url.isNullOrBlank()) {
            Log.e(TAG, "link is null or blank")
            toast(getString(R.string.commonutils_error_cant_open_url))
            false
        } else {
            startActivity(Intent(ACTION_VIEW, url.toUri()))
            true
        }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No browser app installed", e)
        toast(getString(R.string.commonutils_no_browser_app_installed))
        false
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open URL", e)
        toast(getString(R.string.commonutils_error_cant_open_url))
        false
    }
