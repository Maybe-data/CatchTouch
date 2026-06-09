package com.catchtouch.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FanMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isLeftFan = true
    var fanRadius = 0

    private val fanPath = Path()
    private val fanRegion = Region()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val r = fanRadius.toFloat()
        fanPath.reset()
        if (isLeftFan) {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return fanRegion.contains(event.x.toInt(), event.y.toInt())
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
