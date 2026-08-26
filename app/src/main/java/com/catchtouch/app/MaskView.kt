package com.catchtouch.app

import android.content.Context
import android.graphics.Canvas
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
        color = 0x1FCE93D8
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = 0xFF7B1FA2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    var maskMode: MaskMode = MaskMode.MODE_ONE
    var topPercent: Float = 0.05f
    var bottomPercent: Float = 0.05f
    var leftPercent: Float = 0.05f
    var rightPercent: Float = 0.05f
    var thumbLeftPercent: Float = 0.15f
    var thumbRightPercent: Float = 0.15f
    var leftHoleHeightPercent: Float = 0f
    var leftHolePosPercent: Float = 0.5f
    var rightHoleHeightPercent: Float = 0f
    var rightHolePosPercent: Float = 0.5f
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
            drawSide(canvas, w, h, true, topH, h - bottomH, w * leftPercent)
        }
        if (rightPercent > 0f) {
            drawSide(canvas, w, h, false, topH, h - bottomH, w * rightPercent)
        }
    }

    /** 与服务端 addSideMaskWithHole 相同的分段逻辑，绘制带孔竖条预览 */
    private fun drawSide(canvas: Canvas, w: Float, h: Float, isLeft: Boolean, stripTop: Float, stripBottom: Float, sideW: Float) {
        val holeHPct = if (isLeft) leftHoleHeightPercent else rightHoleHeightPercent
        val innerX = if (isLeft) sideW else w - sideW
        fun block(top: Float, bottom: Float) {
            if (bottom - top <= 0f) return
            val x1 = if (isLeft) 0f else innerX
            val x2 = if (isLeft) sideW else w
            canvas.drawRect(x1, top, x2, bottom, maskPaint)
            canvas.drawLine(innerX, top, innerX, bottom, borderPaint)
        }
        if (holeHPct <= 0f || stripBottom <= stripTop) {
            block(stripTop, stripBottom)
            return
        }
        val posPct = if (isLeft) leftHolePosPercent else rightHolePosPercent
        val holeH = h * holeHPct
        var holeBottom = h - h * posPct // 位置从底部向上计
        var holeTop = holeBottom - holeH
        if (holeBottom - holeTop >= stripBottom - stripTop) return // 孔覆盖整条：不绘制该侧
        if (holeBottom > stripBottom) { holeTop -= holeBottom - stripBottom; holeBottom = stripBottom }
        if (holeTop < stripTop) { holeBottom += stripTop - holeTop; holeTop = stripTop }
        block(stripTop, holeTop)
        block(holeBottom, stripBottom)
    }

    private fun drawFanPreview(canvas: Canvas, w: Float, h: Float) {
        val base = Math.max(w, h)
        if (thumbLeftPercent > 0f) {
            val rl = base * thumbLeftPercent
            val leftPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h - rl)
                arcTo(-rl, h - rl, rl, h + rl, 270f, 90f, false)
                lineTo(0f, h)
                close()
            }
            canvas.drawPath(leftPath, maskPaint)
            canvas.drawPath(leftPath, borderPaint)
        }
        if (thumbRightPercent > 0f) {
            val rr = base * thumbRightPercent
            val rightPath = Path().apply {
                moveTo(w, h)
                lineTo(w - rr, h)
                arcTo(w - rr, h - rr, w + rr, h + rr, 180f, 90f, false)
                lineTo(w, h)
                close()
            }
            canvas.drawPath(rightPath, maskPaint)
            canvas.drawPath(rightPath, borderPaint)
        }
    }
}
