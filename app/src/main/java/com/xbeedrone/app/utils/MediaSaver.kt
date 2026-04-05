package com.xbeedrone.app.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Zapisuje zdjęcia i wideo z drona do galerii telefonu.
 */
object MediaSaver {

    private const val TAG = "MediaSaver"
    private const val DIR_NAME = "XBeeDrone"

    /**
     * Zapisuje klatkę (Bitmap) jako zdjęcie JPEG do galerii.
     * @return true jeśli sukces
     */
    fun savePhoto(context: Context, bitmap: Bitmap): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "DRONE_$timestamp.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ – MediaStore API
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$DIR_NAME")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false

                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                // Android 9 i starsze – bezpośredni zapis do pliku
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    DIR_NAME
                ).also { it.mkdirs() }

                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "savePhoto failed: ${e.message}")
            false
        }
    }

    /**
     * Zwraca ścieżkę do katalogu zapisu wideo.
     */
    fun getVideoOutputDir(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                DIR_NAME
            ).also { it.mkdirs() }
        }
    }

    /**
     * Generuje nazwę pliku wideo z aktualnym znacznikiem czasu.
     */
    fun generateVideoFilename(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "DRONE_VIDEO_$ts.mp4"
    }
}
