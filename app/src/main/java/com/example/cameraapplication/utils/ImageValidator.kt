package com.example.cameraapplication.utils

import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.pow

object ImageValidator {

    fun isImageBlurry(photoFile: File, threshold: Double = 1000.0): Boolean {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return true

        val grayValues = mutableListOf<Int>()
        for (x in 0 until bitmap.width step 10) {
            for (y in 0 until bitmap.height step 10) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                        0.587 * ((pixel shr 8) and 0xFF) +
                        0.114 * (pixel and 0xFF)).toInt()
                grayValues.add(gray)
            }
        }

        val mean = grayValues.average()
        val variance = grayValues.map { (it - mean).pow(2) }.average()

        bitmap.recycle()

        return variance < threshold
    }



    fun isImageTooDark(photoFile: File, brightnessThreshold: Int = 50): Boolean {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return true

        var totalBrightness = 0L
        var pixelCount = 0

        for (x in 0 until bitmap.width step 10) {
            for (y in 0 until bitmap.height step 10) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = ((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)
                totalBrightness += brightness / 3
                pixelCount++
            }
        }

        bitmap.recycle()

        val avgBrightness = totalBrightness / pixelCount
        return avgBrightness < brightnessThreshold
    }



}