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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SAMSUNG_QUICK_SHARE_PACKAGE = "com.samsung.android.app.sharelive"
private const val MIME_TYPE_TEXT = "text/plain"
private const val MIME_TYPE_PNG = "image/png"
private const val TAG = "SharingUtils"

fun Fragment.shareApp() = requireContext().shareApp()

fun Context.shareApp() {
    startActivity(Intent.createChooser(Intent().apply {
        action = ACTION_SEND
        type = MIME_TYPE_TEXT
        putExtra(Intent.EXTRA_TEXT, getString(R.string.playstore_link) + packageName)
    }, null))
}

fun Context.shareText(text: String) {
    Intent().apply {
        action = ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = MIME_TYPE_TEXT
        startActivity(Intent.createChooser(this, null))
    }
}

fun Context.copyToClipboard(text: String, label: String): Boolean {
    (getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    toast(R.string.copied_to_clipboard)
    return true
}

fun Bitmap.copyToClipboard(context: Context, label: String, shareFileName: String): Boolean {
    val cacheFile = File(context.cacheDir, shareFileName)
    compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    val clip = ClipData.newUri(context.contentResolver, label, cacheFile.getFileUri(context))
    (context.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    context.toast(R.string.copied_to_clipboard)
    return true
}

fun Bitmap.share(context: Context, shareFileName: String) {
    val cacheFile = File(context.cacheDir, shareFileName)
    compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    val sendIntent = Intent().apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        action = ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, cacheFile.getFileUri(context))
        type = MIME_TYPE_PNG
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

fun Fragment.shareBitmap(bitmap: Bitmap, shareFileName: String) = requireContext().shareBitmap(bitmap, shareFileName)

fun Context.shareBitmap(bitmap: Bitmap, shareFileName: String) {
    val cacheFile = File(cacheDir, shareFileName)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    Intent().apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        action = ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, cacheFile.getFileUri(this@shareBitmap))
        type = MIME_TYPE_PNG
        startActivity(Intent.createChooser(this, null))
    }
}

fun Fragment.quickShareBitmap(bitmap: Bitmap, shareFileName: String) = requireContext().quickShareBitmap(bitmap, shareFileName)

fun Context.quickShareBitmap(bitmap: Bitmap, shareFileName: String) {
    val cacheFile = File(cacheDir, shareFileName)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
    createBaseIntent().apply {
        type = MIME_TYPE_PNG
        putExtra(Intent.EXTRA_STREAM, cacheFile.getFileUri(this@quickShareBitmap))
        start(this@quickShareBitmap)
    }
}

inline fun File.share(context: Context) {
    listOf(this).share(context)
}

fun List<File>.share(context: Context) {
    val contentUris = map { f -> f.getFileUri(context) }

    if (contentUris.isEmpty()) {
        Log.e(TAG, "No file to share.")
        return
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
        start(context)
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

private inline fun Intent.start(context: Context) {
    try {
        context.startActivity(this)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start activity with specific package: ${e.message}")
        // Fallback to default chooser if specific package fails
        `package` = null
        context.startActivity(Intent.createChooser(this, null))
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

inline fun String.toSafeFileName(extension: String): String =
    "${this}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())}"
        .replace("https://", "")
        .replace("[^a-zA-Z0-9]+".toRegex(), "_")
        .replace("_+".toRegex(), "_")
        .replace("^_".toRegex(), "") +
            extension