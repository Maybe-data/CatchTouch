package com.catchtouch.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class MaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#1FCE93D8")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#7B1FA2")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#9C27B0")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    var maskMode: MaskMode = MaskMode.MODE_ONE
    var topPercent: Float = 0.05f
    var bottomPercent: Float = 0.05f
    var leftPercent: Float = 0.05f
    var rightPercent: Float = 0.05f
    var thumbPercent: Float = 0.15f
    var showPreview: Boolean = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showPreview) return

        val w = width.toFloat()
        val h = height.toFloat()

        when (maskMode) {
            MaskMode.MODE_ONE -> drawRectPreview(canvas, w, h)
            MaskMode.MODE_TWO -> drawFanPreview(canvas, w, h)
            MaskMode.MIXED -> {
                drawRectPreview(canvas, w, h)
                drawFanPreview(canvas, w, h)
            }
        }


    }

    private fun drawRectPreview(canvas: Canvas, w: Float, h: Float) {
        val topH = h * topPercent
        val bottomH = h * bottomPercent
        val leftW = w * leftPercent
        val rightW = w * rightPercent

        if (topPercent > 0f) {
            canvas.drawRect(0f, 0f, w, topH, maskPaint)
            canvas.drawLine(0f, topH, w, topH, borderPaint)
        }
        if (bottomPercent > 0f) {
            canvas.drawRect(0f, h - bottomH, w, h, maskPaint)
            canvas.drawLine(0f, h - bottomH, w, h - bottomH, borderPaint)
        }
        if (leftPercent > 0f) {
            canvas.drawRect(0f, topH, leftW, h - bottomH, maskPaint)
            canvas.drawLine(leftW, topH, leftW, h - bottomH, borderPaint)
        }
        if (rightPercent > 0f) {
            canvas.drawRect(w - rightW, topH, w, h - bottomH, maskPaint)
            canvas.drawLine(w - rightW, topH, w - rightW, h - bottomH, borderPaint)
        }
    }

    private fun drawFanPreview(canvas: Canvas, w: Float, h: Float) {
        if (thumbPercent <= 0f) return

        val r = Math.max(w, h) * thumbPercent

        val leftPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h - r)
            arcTo(-r, h - r, r, h + r, 270f, 90f, false)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(leftPath, maskPaint)
        canvas.drawPath(leftPath, borderPaint)

        val rightPath = Path().apply {
            moveTo(w, h)
            lineTo(w - r, h)
            arcTo(w - r, h - r, w + r, h + r, 180f, 90f, false)
            lineTo(w, h)
            close()
        }
        canvas.drawPath(rightPath, maskPaint)
        canvas.drawPath(rightPath, borderPaint)
    }

    private fun drawCenterInfo(canvas: Canvas, w: Float, h: Float) {
        val lines = mutableListOf<String>()
        lines.add(maskMode.label)
        if (maskMode == MaskMode.MODE_ONE || maskMode == MaskMode.MIXED) {
            lines.add("顶:${(topPercent * 100).toInt()}% 底:${(bottomPercent * 100).toInt()}%")
            lines.add("左:${(leftPercent * 100).toInt()}% 右:${(rightPercent * 100).toInt()}%")
        }
        if (maskMode == MaskMode.MODE_TWO || maskMode == MaskMode.MIXED) {
            lines.add("拇指:${(thumbPercent * 100).toInt()}%")
        }
        val lineHeight = 40f
        val startY = h / 2 - (lines.size - 1) * lineHeight / 2
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, w / 2, startY + i * lineHeight, textPaint)
        }
    }
}
