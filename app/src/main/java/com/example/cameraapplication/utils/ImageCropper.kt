package com.example.cameraapplication.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageCropper {

    suspend fun cropImageToAspectRatio(file: File, aspectRatio: Float)= withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 } // DownSample to reduce memory
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        val originalRatio = originalWidth.toFloat() / originalHeight.toFloat()

        var cropWidth = originalWidth
        var cropHeight = originalHeight

        if (originalRatio > aspectRatio) {
            // Image is wider than desired ratio, crop sides
            cropWidth = (originalHeight * aspectRatio).toInt()
        } else {
            // Image is taller than desired ratio, crop top/bottom
            cropHeight = (originalWidth / aspectRatio).toInt()
        }

        val xOffset = (originalWidth - cropWidth) / 2
        val yOffset = (originalHeight - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(originalBitmap, xOffset, yOffset, cropWidth, cropHeight)

        // Overwrite the original file with cropped bitmap
        FileOutputStream(file).use { outStream ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            outStream.flush()
        }

        originalBitmap.recycle()
        croppedBitmap.recycle()
    }



}