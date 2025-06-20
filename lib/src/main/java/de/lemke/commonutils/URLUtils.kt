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

fun Fragment.openURL(url: String?): Boolean =
    requireContext().openURL(url)

fun Context.openURL(url: String?): Boolean = try {
    if (url.isNullOrBlank()) {
        Log.e(TAG, "link is null or blank")
        toast(getString(R.string.commonutils_error_cant_open_url))
        false
    } else {
        startActivity(Intent(ACTION_VIEW, url.toUri()))
        true
    }
} catch (e: ActivityNotFoundException) {
    e.printStackTrace()
    toast(getString(R.string.commonutils_no_browser_app_installed))
    false
} catch (e: Exception) {
    e.printStackTrace()
    toast(getString(R.string.commonutils_error_cant_open_url))
    false
}
