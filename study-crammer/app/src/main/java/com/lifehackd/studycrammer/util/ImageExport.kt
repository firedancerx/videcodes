package com.lifehackd.studycrammer.util

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PNG export of the lockscreen card: share sheet, gallery, or set as lock screen wallpaper. */
object ImageExport {
    private const val ALBUM = "StudyCrammer"

    /** GraphicsLayer snapshots can be HARDWARE bitmaps; compress()/WallpaperManager need software ones. */
    fun softwareCopy(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

    suspend fun share(context: Context, bitmap: Bitmap, fileName: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { softwareCopy(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your cheat card"))
    }

    /** API 29+: MediaStore, no permission. API 26–28: caller must hold WRITE_EXTERNAL_STORAGE. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val bmp = softwareCopy(bitmap)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                try {
                    resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        ?: error("no output stream")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                    .apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                true
            }
        }.getOrDefault(false)
    }

    suspend fun setAsLockscreen(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            WallpaperManager.getInstance(context)
                .setBitmap(softwareCopy(bitmap), null, true, WallpaperManager.FLAG_LOCK) != 0
        }.getOrDefault(false)
    }
}
