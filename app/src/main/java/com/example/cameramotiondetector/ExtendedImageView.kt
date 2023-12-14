package com.example.cameramotiondetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class ExtendedImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    public var boxes = arrayOf<Array<Int>>()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        boxes.forEach {
            val paint = Paint()
            paint.color = Color.RED
            paint.alpha = 50
            paint.strokeWidth = 3f
            canvas!!.drawRect(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat(), paint)
        }

    }
}