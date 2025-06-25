package com.example.cameraapplication.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageSaver {

    suspend fun fixImageRotation(context: Context, photoFile: File, surfaceRotation: Int) = withContext(Dispatchers.IO) {
        val ei = ExifInterface(photoFile.absolutePath)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val exifDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val surfaceDegrees = when (surfaceRotation) {
            android.view.Surface.ROTATION_0 -> 0
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }

        val totalRotation = (exifDegrees + surfaceDegrees) % 360

        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

        val finalBitmap = if (totalRotation != 0) {
            rotateImage(bitmap, totalRotation.toFloat())
        } else {
            bitmap
        }

        FileOutputStream(photoFile).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
        }

        bitmap.recycle()
        if (bitmap != finalBitmap) finalBitmap.recycle()

        val newExif = ExifInterface(photoFile.absolutePath)
        newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        newExif.saveAttributes()
    }

    suspend fun savePhotoToGallery(context: Context, photoFile: File) = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraApp")
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                photoFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}