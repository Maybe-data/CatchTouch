package com.catchtouch.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.media.AudioManager

class AntiTouchService : AccessibilityService() {

    companion object {
        var instance: AntiTouchService? = null
        var isRunning: Boolean = false
            private set
        private const val TAG = "CatchTouch"
        private const val CHANNEL_ID = "catchtouch_service"
        private const val NOTIFICATION_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private var maskViews = mutableListOf<android.view.View>()
    private var lastForegroundPkg: String = ""
    private var isForeground = false
    private var overlayToastView: android.view.View? = null
    private var toastRemoveRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.d(TAG, "onServiceConnected")
        startForegroundNotification()
        if (SettingsManager.isEnabled(this)) {
            val selectedApps = SettingsManager.getSelectedApps(this)
            if (selectedApps.isEmpty() || selectedApps.contains(lastForegroundPkg)) {
                addMasks()
            }
        }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "CatchTouch服务", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            description = "保持CatchTouch无障碍服务运行"
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CatchTouch")
            .setContentText("防误触服务运行中")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isForeground = true
        Log.d(TAG, "foreground notification started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!SettingsManager.isEnabled(this)) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""
            Log.d(TAG, "WINDOW_STATE: pkg=$pkg cls=$className last=$lastForegroundPkg")
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == lastForegroundPkg) return

        val isRealApp = try {
            packageManager.getLaunchIntentForPackage(pkg) != null ||
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg), 0).isNotEmpty()
        } catch (_: Exception) { false }
        if (!isRealApp) return

        lastForegroundPkg = pkg
        Log.d(TAG, "foreground changed: $pkg")

        val selectedApps = SettingsManager.getSelectedApps(this)
        if (selectedApps.isEmpty()) {
            if (maskViews.isEmpty()) {
                addMasks()
                updateTileState()
            }
            return
        }

        val shouldShow = selectedApps.contains(pkg)
        Log.d(TAG, "shouldShow=$shouldShow for $pkg, selected=$selectedApps, masks=${maskViews.size}")
        if (shouldShow && maskViews.isEmpty()) {
            showToast("CatchTouch: 遮罩已生效")
            handler.postDelayed({ addMasks(); updateTileState() }, 500)
        } else if (!shouldShow && maskViews.isNotEmpty()) {
            removeMasks()
            updateTileState()
            showToast("CatchTouch: 遮罩已关闭")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        removeMasks()
        instance = null
        isRunning = false
        isForeground = false
        Log.d(TAG, "onDestroy")
    }

    private var volumeDownTime = 0L
    private var longPressHandled = false
    private val longPressCheck = Runnable {
        if (!longPressHandled && volumeDownTime > 0L) {
            longPressHandled = true
            toggleMask()
        }
    }

    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        volumeDownTime = System.currentTimeMillis()
                        longPressHandled = false
                        handler.removeCallbacks(longPressCheck)
                        handler.postDelayed(longPressCheck, 600L)
                    }
                    return true
                }
                android.view.KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressCheck)
                    if (!longPressHandled) {
                        (getSystemService(AUDIO_SERVICE) as AudioManager).adjustVolume(
                            AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                        )
                    }
                    volumeDownTime = 0L
                    return true
                }
            }
        }

        return super.onKeyEvent(event)
    }

    private fun toggleMask() {
        if (SettingsManager.isEnabled(this)) {
            disableMask()
        } else {
            enableMask()
        }
    }

    fun enableMask(): Boolean {
        SettingsManager.setEnabled(this, true)
        val selectedApps = SettingsManager.getSelectedApps(this)
        val shouldShowNow = selectedApps.isEmpty() || selectedApps.contains(lastForegroundPkg)
        Log.d(TAG, "enableMask: shouldShowNow=$shouldShowNow, lastPkg=$lastForegroundPkg, selected=$selectedApps")
        if (shouldShowNow) {
            val count = addMasks()
            updateTileState()
            val success = maskViews.isNotEmpty()
            if (success) {
                showToast("遮罩已开启（${count}个区域）")
            } else {
                showToast("遮罩创建失败，请检查参数")
                SettingsManager.setEnabled(this, false)
            }
            return success
        } else {
            updateTileState()
            showToast("CatchTouch已启用，切到目标应用时自动生效")
            return true
        }
    }

    fun disableMask() {
        SettingsManager.setEnabled(this, false)
        removeMasks()
        updateTileState()
        handler.post {
            showToast("遮罩已关闭")
        }
    }

    fun isMaskActive(): Boolean = maskViews.isNotEmpty()

    private fun addMasks(): Int {
        if (maskViews.isNotEmpty()) return maskViews.size

        val mode = SettingsManager.getMode(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val realMetrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(realMetrics)
        val sw = realMetrics.widthPixels
        val sh = realMetrics.heightPixels

        val topP = SettingsManager.getTop(this)
        val bottomP = SettingsManager.getBottom(this)
        val leftP = SettingsManager.getLeft(this)
        val rightP = SettingsManager.getRight(this)
        val thumbP = SettingsManager.getThumb(this)

        try {
            when (mode) {
                MaskMode.MODE_ONE -> addRectMasks(wm, sw, sh, topP, bottomP, leftP, rightP)
                MaskMode.MODE_TWO -> addFanMasks(wm, sw, sh, thumbP)
                MaskMode.MIXED -> {
                    addRectMasks(wm, sw, sh, topP, bottomP, leftP, rightP)
                    addFanMasks(wm, sw, sh, thumbP)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "addMasks error", e)
            handler.post {
                showToast("遮罩创建异常: ${e.message}")
            }
        }

        Log.d(TAG, "addMasks: count=${maskViews.size}")
        return maskViews.size
    }

    private fun addRectMasks(
        wm: WindowManager, sw: Int, sh: Int,
        topP: Float, bottomP: Float, leftP: Float, rightP: Float
    ) {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val topH = (sh * topP).toInt()
        val bottomH = (sh * bottomP).toInt()
        val leftW = (sw * leftP).toInt()
        val rightW = (sw * rightP).toInt()

        if (topP > 0f) {
            addBlockView(wm, sw, topH, Gravity.TOP or Gravity.START, 0, 0, flags)
        }
        if (bottomP > 0f) {
            addBlockView(wm, sw, bottomH, Gravity.BOTTOM or Gravity.START, 0, 0, flags)
        }
        if (leftP > 0f) {
            addBlockView(wm, leftW, sh - topH - bottomH, Gravity.TOP or Gravity.START, 0, topH, flags)
        }
        if (rightP > 0f) {
            addBlockView(wm, rightW, sh - topH - bottomH, Gravity.TOP or Gravity.END, 0, topH, flags)
        }
    }

    private fun addFanMasks(
        wm: WindowManager, sw: Int, sh: Int, thumbP: Float
    ) {
        if (thumbP <= 0f) return

        val radius = (Math.max(sw, sh) * thumbP).toInt()
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val leftView = FanMaskView(this, true, radius)
        val leftParams = WindowManager.LayoutParams(
            radius, radius,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(leftView, leftParams)
        maskViews.add(leftView)

        val rightView = FanMaskView(this, false, radius)
        val rightParams = WindowManager.LayoutParams(
            radius, radius,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(rightView, rightParams)
        maskViews.add(rightView)
    }

    private fun addBlockView(
        wm: WindowManager, width: Int, height: Int,
        gravity: Int, x: Int, y: Int, flags: Int
    ) {
        val view = BlockView(this)
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(view, params)
        maskViews.add(view)
    }

    private fun removeMasks() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        for (view in maskViews) {
            try { wm.removeView(view) } catch (_: Exception) {}
        }
        maskViews.clear()
    }

    private fun updateTileState() {
        AntiTouchTileService.instance?.updateTile()
    }

    fun refreshMasks() {
        removeMasks()
        if (SettingsManager.isEnabled(this)) addMasks()
    }

    private fun showToast(text: String) {
        handler.post {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                overlayToastView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                toastRemoveRunnable?.let { handler.removeCallbacks(it) }

                val container = FrameLayout(this)
                val tv = TextView(this).apply {
                    this.text = text
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 15f
                    setPadding(36, 20, 36, 20)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x9E9E9E9E.toInt())
                        setCornerRadius(24f)
                    }
                }
                container.addView(tv)
                container.setPadding(0, 0, 0, 120)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }

                wm.addView(container, params)
                overlayToastView = container
                val removeRunnable = Runnable {
                    try { wm.removeView(container) } catch (_: Exception) {}
                    if (overlayToastView == container) overlayToastView = null
                }
                toastRemoveRunnable = removeRunnable
                handler.postDelayed(removeRunnable, 1800L)
            } catch (e: Exception) {
                Log.e(TAG, "showToast error", e)
            }
        }
    }
}
