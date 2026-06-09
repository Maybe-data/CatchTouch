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
        val wf = w.toFloat()
        val hf = h.toFloat()
        val r = radius.toFloat()
        fanPath.reset()
        if (isLeft) {
            fanPath.moveTo(0f, hf)
            fanPath.lineTo(0f, hf - r)
            fanPath.arcTo(-r, hf - r, r, hf + r, 270f, 90f, false)
            fanPath.lineTo(0f, hf)
        } else {
            fanPath.moveTo(wf, hf)
            fanPath.lineTo(wf - r, hf)
            fanPath.arcTo(wf - r, hf - r, wf + r, hf + r, 180f, 90f, false)
            fanPath.lineTo(wf, hf)
        }
        fanPath.close()
        fanRegion.setEmpty()
        fanRegion.setPath(fanPath, Region(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {}

    override fun onTouchEvent(event: MotionEvent): Boolean =
        fanRegion.contains(event.x.toInt(), event.y.toInt())
}
