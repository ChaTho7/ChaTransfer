package com.chatho.chatransfer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chatho.chatransfer.R

class StraightLineView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val linePaint = Paint()

    init {
        linePaint.strokeWidth = 3f
        linePaint.color = ContextCompat.getColor(context, R.color.cyan)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val startX = 100f
        val startY = 7f
        val endX = canvas.width - 100f
        val endY = 7f
        canvas.drawLine(startX, startY, endX, endY, linePaint)
    }
}
