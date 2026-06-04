package com.catchtouch.app

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

class BlockView(context: Context) : View(context) {
    override fun onDraw(canvas: Canvas) {}
    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
