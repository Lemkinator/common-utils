@file:Suppress("NOTHING_TO_INLINE", "unused")

package de.lemke.commonutils


import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

private const val TAG = "ExportUtils"
private const val MIME_TYPE_PNG = "image/png"
private const val EXTENSION_PNG = ".png"

enum class SaveLocation {
    CUSTOM,
    DOWNLOADS,
    PICTURES,
    DCIM,
    ;

    companion object {
        val default = CUSTOM

        fun fromStringOrDefault(string: String?): SaveLocation = entries.firstOrNull { it.toString() == string } ?: default
    }

    fun toLocalizedString(context: Context): String {
        return when (this) {
            CUSTOM -> context.getString(R.string.custom)
            DOWNLOADS -> "Downloads"
            PICTURES -> "Pictures"
            DCIM -> "DCIM"
        }
    }
}

fun Context.exportBitmap(
    saveLocation: SaveLocation,
    bitmap: Bitmap,
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>?
) {
    if (saveLocation != SaveLocation.CUSTOM) {
        try {
            val dir: String = when (saveLocation) {
                SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
                SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
                SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
                else -> Environment.DIRECTORY_DOWNLOADS // should never happen
            }
            Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), filename.toSafeFileName(EXTENSION_PNG)).toPath())
                .use<OutputStream, Boolean> { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            toast(getString(R.string.image_saved) + ": ${saveLocation.toLocalizedString(this)}")
        } catch (e: IOException) {
            e.printStackTrace()
            toast(R.string.error_creating_file)
        }
    } else {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = MIME_TYPE_PNG
        intent.putExtra(Intent.EXTRA_TITLE, filename.toSafeFileName(EXTENSION_PNG))
        activityResultLauncher?.launch(intent)
    }
}

fun Fragment.exportBitmap(
    saveLocation: SaveLocation,
    bitmap: Bitmap,
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>?
) = requireContext().exportBitmap(saveLocation, bitmap, filename, activityResultLauncher)

fun Context.saveBitmapToUri(uri: Uri?, bitmap: Bitmap?) {
    try {
        contentResolver.openOutputStream(uri!!)!!.use { outputStream ->
            if (bitmap?.compress(Bitmap.CompressFormat.PNG, 100, outputStream) == true) {
                toast(R.string.image_saved)
            } else {
                toast(R.string.error_saving_image)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        toast(R.string.error_creating_file)
    }
}

