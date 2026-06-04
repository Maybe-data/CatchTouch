package com.catchtouch.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Region
import android.view.MotionEvent
import android.view.View

class FanMaskView(
    context: Context,
    private val isLeft: Boolean,
    private val radius: Int
) : View(context) {

    private val fanPath = Path()
    private val fanRegion = Region()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildPath(w.toFloat(), h.toFloat())
    }

    private fun buildPath(w: Float, h: Float) {
        val r = radius.toFloat()
        fanPath.reset()

        if (isLeft) {
            // 圆心在左下角 (0, h)
            // 边1：沿底边向右 → (r, h)
            // 边2：沿左边向上 → (0, h-r)
            // 从边2(0,h-r)沿圆弧到边1(r,h)，即从270°顺时针到0°(360°)
            fanPath.moveTo(0f, h)
            fanPath.lineTo(0f, h - r)
            // arcTo: oval rect 以(0,h)为圆心、r为半径 → left=0-r, top=h-r, right=0+r, bottom=h+r
            // 但圆心是(0,h)，所以 oval = (-r, h-r, r, h+r)
            // startAngle=270°(正上方), sweepAngle=90°(顺时针到0°即正右方)
            fanPath.arcTo(-r, h - r, r, h + r, 270f, 90f, false)
            fanPath.lineTo(0f, h)
        } else {
            // 圆心在右下角 (w, h)
            // 边1：沿底边向左 → (w-r, h)
            // 边2：沿右边向上 → (w, h-r)
            // 从边1(w-r,h)沿圆弧到边2(w,h-r)，即从180°顺时针到270°
            fanPath.moveTo(w, h)
            fanPath.lineTo(w - r, h)
            // oval 以(w,h)为圆心 → (w-r, h-r, w+r, h+r)
            // startAngle=180°(正左方), sweepAngle=90°(顺时针到270°即正上方)
            fanPath.arcTo(w - r, h - r, w + r, h + r, 180f, 90f, false)
            fanPath.lineTo(w, h)
        }
        fanPath.close()

        fanRegion.setEmpty()
        val clip = Region(0, 0, width, height)
        fanRegion.setPath(fanPath, clip)
    }

    override fun onDraw(canvas: Canvas) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return fanRegion.contains(event.x.toInt(), event.y.toInt())
    }
}
