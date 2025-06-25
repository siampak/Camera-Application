package com.example.cameraapplication.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayFrameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    var aspectRatio: Float = 4f / 3f // Default to ID Photo

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate frame size based on aspect ratio
        val frameWidth: Float
        val frameHeight: Float

        if (viewWidth / viewHeight > aspectRatio) {
            frameHeight = viewHeight * 0.8f
            frameWidth = frameHeight * aspectRatio
        } else {
            frameWidth = viewWidth * 0.8f
            frameHeight = frameWidth / aspectRatio
        }

        val left = (viewWidth - frameWidth) / 2
        val top = (viewHeight - frameHeight) / 2
        val right = left + frameWidth
        val bottom = top + frameHeight

        // Draw frame rectangle
        canvas.drawRect(left, top, right, bottom, paint)
    }

    fun updateAspectRatio(ratio: Float) {
        aspectRatio = ratio
        invalidate()
    }
    fun updateFrameColor(isAligned: Boolean) {
        paint.color = if (isAligned) Color.GREEN else Color.RED
        invalidate()
    }
}
