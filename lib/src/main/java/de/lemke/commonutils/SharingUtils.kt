@file:Suppress("NOTHING_TO_INLINE", "unused")

package de.lemke.commonutils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

private const val SAMSUNG_QUICK_SHARE_PACKAGE = "com.samsung.android.app.sharelive"
private const val MIME_TYPE_TEXT = "text/plain"
private const val MIME_TYPE_PNG = "image/png"
private const val TAG = "SharingUtils"

fun Fragment.shareApp(): Boolean = requireContext().shareApp()

fun Context.shareApp(): Boolean = safeStartActivity(Intent.createChooser(Intent().apply {
    action = ACTION_SEND
    type = MIME_TYPE_TEXT
    putExtra(Intent.EXTRA_TEXT, getString(R.string.playstore_link) + packageName)
}, null))

fun Fragment.shareText(text: String, title: String? = null): Boolean = requireContext().shareText(text, title)

fun Context.shareText(text: String, title: String? = null): Boolean {
    Intent().apply {
        action = ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        type = MIME_TYPE_TEXT
        return safeStartActivity(Intent.createChooser(this, title))
    }
}

fun Fragment.copyToClipboard(text: String, label: String): Boolean = requireContext().copyToClipboard(text, label)

fun Context.copyToClipboard(text: String, label: String): Boolean {
    (getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    toast(R.string.copied_to_clipboard)
    return true
}

fun Context.copyToClipboard(bitmap: Bitmap, label: String, shareFileName: String): Boolean {
    val cacheFile = File(cacheDir, shareFileName)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    val clip = ClipData.newUri(contentResolver, label, cacheFile.getFileUri(this))
    (getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    toast(R.string.copied_to_clipboard)
    return true
}

fun Bitmap.copyToClipboard(context: Context, label: String, shareFileName: String): Boolean =
    context.copyToClipboard(this, label, shareFileName)

fun Fragment.shareBitmap(bitmap: Bitmap, shareFileName: String, shareText: String? = null): Boolean =
    bitmap.share(requireContext(), shareFileName, shareText)

fun Context.shareBitmap(bitmap: Bitmap, shareFileName: String, shareText: String? = null): Boolean =
    bitmap.share(this, shareFileName, shareText)

fun Bitmap.share(context: Context, shareFileName: String, shareText: String? = null): Boolean = try {
    val cacheFile = File(context.cacheDir, shareFileName)
    compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    val uri = cacheFile.getFileUri(context)
    Intent(ACTION_SEND).apply {
        clipData = ClipData.newRawUri(shareFileName, uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        shareText?.let { putExtra(Intent.EXTRA_TEXT, it) }
        type = MIME_TYPE_PNG
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.safeStartActivity(Intent.createChooser(this, null))
    }
    true
} catch (e: Exception) {
    e.printStackTrace()
    context.toast(R.string.error_share_content_not_supported_on_device)
    false
}

fun Fragment.quickShareBitmap(bitmap: Bitmap, shareFileName: String): Boolean = bitmap.quickShare(requireContext(), shareFileName)

fun Context.quickShareBitmap(bitmap: Bitmap, shareFileName: String): Boolean = bitmap.quickShare(this, shareFileName)

fun Bitmap.quickShare(context: Context, shareFileName: String): Boolean {
    val cacheFile = File(context.cacheDir, shareFileName)
    compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    context.createBaseIntent().apply {
        type = MIME_TYPE_PNG
        putExtra(Intent.EXTRA_STREAM, cacheFile.getFileUri(context))
        return start(context)
    }
}

inline fun File.share(context: Context): Boolean = listOf(this).share(context)

fun List<File>.share(context: Context): Boolean {
    val contentUris = map { f -> f.getFileUri(context) }

    if (contentUris.isEmpty()) {
        Log.e(TAG, "No file to share.")
        return false
    }

    context.createBaseIntent().apply {
        type = MIME_TYPE_PNG
        if (contentUris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, contentUris[0])
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            putExtra(Intent.EXTRA_STREAM, ArrayList(contentUris))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context)
    }
}

private inline fun Context.createBaseIntent() =
    Intent().apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        action = ACTION_SEND
        if (isSamsungQuickShareAvailable()) {
            `package` = SAMSUNG_QUICK_SHARE_PACKAGE
        }
    }

private inline fun Intent.start(context: Context): Boolean {
    try {
        context.startActivity(this)
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start activity with specific package: ${e.message}")
        // Fallback to default chooser if specific package fails
        `package` = null
        return context.safeStartActivity(this)
    }
}

private inline fun Context.safeStartActivity(intent: Intent): Boolean {
    try {
        startActivity(intent)
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e(TAG, "Failed to start activity: ${e.message}")
        toast(R.string.error_share_content_not_supported_on_device)
        return false
    }
}

fun Context.isSamsungQuickShareAvailable(): Boolean {
    return try {
        packageManager.getPackageInfo(SAMSUNG_QUICK_SHARE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }.also {
        Log.i(TAG, "isSamsungQuickShareAvailable: $it")
    }
}

inline fun File.getFileUri(context: Context): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

