package com.catchtouch.app

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        private const val RESTART_REQUEST_CODE = 1001
        private const val WATCHDOG_INTERVAL = 3000L
        const val ACTION_RESTART_SERVICE = "com.catchtouch.app.RESTART_SERVICE"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var maskViews = mutableListOf<android.view.View>()
    private var lastForegroundPkg: String = ""
    private var isForeground = false
    private var overlayToastView: android.view.View? = null
    private var toastRemoveRunnable: Runnable? = null
    private var wasEnabledBeforeDestroy = false

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                val detected = detectForegroundApp()
                if (detected.isNotEmpty() && detected != lastForegroundPkg && detected != packageName) {
                    Log.d(TAG, "[WATCHDOG] fg changed: $lastForegroundPkg -> $detected")
                    lastForegroundPkg = detected
                }
                applyMaskStateForCurrentApp()
            } catch (e: Exception) {
                Log.e(TAG, "watchdog error", e)
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        wasEnabledBeforeDestroy = SettingsManager.isEnabled(this)
        Log.d(TAG, "onServiceConnected")
        startForegroundNotification()

        lastForegroundPkg = detectForegroundApp()
        Log.d(TAG, "detected foreground: $lastForegroundPkg")

        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL)

        if (SettingsManager.isEnabled(this)) {
            applyMaskStateForCurrentApp()
        }
    }

    private fun detectForegroundApp(): String {
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.isNotEmpty() && pkg != packageName) {
                    return pkg
                }
            }
        } catch (_: Exception) {}
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                val tasks = am.appTasks
                if (tasks != null) {
                    for (task in tasks) {
                        val top = task.taskInfo.topActivity
                        if (top != null && top.packageName != packageName) {
                            return top.packageName
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
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
        val eventType = event.eventType
        val eventPkg = event.packageName?.toString() ?: ""
        val eventCls = event.className?.toString() ?: ""

        if (!SettingsManager.isEnabled(this)) return

        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            Log.d(TAG, "[EVENT] WINDOWS_CHANGED pkg=$eventPkg cls=$eventCls")
            val detected = detectForegroundApp()
            if (detected.isNotEmpty() && detected != lastForegroundPkg && detected != packageName) {
                Log.d(TAG, "[WIN_SWITCH] $lastForegroundPkg -> $detected (detected from windows change)")
                lastForegroundPkg = detected
                applyMaskStateForCurrentApp()
            } else if (detected.isNotEmpty() && detected == lastForegroundPkg) {
                applyMaskStateForCurrentApp()
            }
            return
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        Log.d(TAG, "[EVENT] type=0x20 pkg=$eventPkg cls=$eventCls")

        val pkg = eventPkg
        if (pkg.isEmpty()) return
        if (pkg == packageName) {
            Log.d(TAG, "[EVENT] skipped: self package")
            return
        }

        val realApp = isRealApp(pkg)
        Log.d(TAG, "[EVENT] isRealApp=$realApp for $pkg")
        if (!realApp) return

        if (pkg != lastForegroundPkg) {
            Log.d(TAG, "[SWITCH] $lastForegroundPkg -> $pkg")
            lastForegroundPkg = pkg
        } else {
            Log.d(TAG, "[EVENT] same pkg=$pkg, re-check state")
        }

        applyMaskStateForCurrentApp()
    }

    private fun isRealApp(pkg: String): Boolean {
        return try {
            packageManager.getLaunchIntentForPackage(pkg) != null ||
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg), 0).isNotEmpty()
        } catch (_: Exception) { false }
    }

    private fun shouldShowMask(): Boolean {
        if (!SettingsManager.isEnabled(this)) return false
        val selectedApps = SettingsManager.getSelectedApps(this)
        return selectedApps.isEmpty() || selectedApps.contains(lastForegroundPkg)
    }

    private fun applyMaskStateForCurrentApp() {
        val shouldShow = shouldShowMask()
        val isShowing = maskViews.isNotEmpty() && maskViews.any { it.isAttachedToWindow }
        val selectedApps = SettingsManager.getSelectedApps(this)

        Log.d(TAG, "[RECONCILE] lastPkg=$lastForegroundPkg shouldShow=$shouldShow isShowing=$isShowing masks=${maskViews.size} selectedApps=$selectedApps")

        if (shouldShow && !isShowing) {
            if (maskViews.isNotEmpty()) {
                Log.d(TAG, "[RECONCILE] cleaning detached masks before re-add")
                removeMasks()
            }
            addMasks()
            updateTileState()
            if (selectedApps.isNotEmpty()) {
                showToast("CatchTouch: 遮罩已生效")
            }
            Log.d(TAG, "[RECONCILE] => MASKS ADDED, count=${maskViews.size}")
        } else if (!shouldShow && isShowing) {
            removeMasks()
            updateTileState()
            if (selectedApps.isNotEmpty()) {
                showToast("CatchTouch: 遮罩已关闭")
            }
            Log.d(TAG, "[RECONCILE] => MASKS REMOVED")
        } else {
            Log.d(TAG, "[RECONCILE] => NO CHANGE (shouldShow=$shouldShow isShowing=$isShowing)")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        wasEnabledBeforeDestroy = SettingsManager.isEnabled(this)
        handler.removeCallbacks(watchdogRunnable)
        removeMasks()
        instance = null
        isRunning = false
        isForeground = false
        Log.d(TAG, "onDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved - scheduling restart")
        if (SettingsManager.isEnabled(this)) {
            restartServiceViaAlarm()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun restartServiceViaAlarm() {
        try {
            val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
                action = ACTION_RESTART_SERVICE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, RESTART_REQUEST_CODE, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1500,
                pendingIntent
            )
            Log.d(TAG, "scheduled service restart via alarm")
        } catch (e: Exception) {
            Log.e(TAG, "restartServiceViaAlarm failed", e)
        }
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
        val wasEnabled = SettingsManager.isEnabled(this)
        Log.d(TAG, "[TOGGLE] vol long press: wasEnabled=$wasEnabled lastPkg=$lastForegroundPkg masks=${maskViews.size}")
        if (wasEnabled) {
            disableMask()
        } else {
            enableMask()
        }
    }

    fun enableMask(): Boolean {
        SettingsManager.setEnabled(this, true)
        wasEnabledBeforeDestroy = true
        Log.d(TAG, "[ENABLE] lastPkg=$lastForegroundPkg masks=${maskViews.size}")

        val selectedApps = SettingsManager.getSelectedApps(this)
        val shouldShowNow = selectedApps.isEmpty() || selectedApps.contains(lastForegroundPkg)
        Log.d(TAG, "[ENABLE] shouldShowNow=$shouldShowNow selectedApps=$selectedApps")

        if (shouldShowNow) {
            if (maskViews.isEmpty() || maskViews.none { it.isAttachedToWindow }) {
                if (maskViews.isNotEmpty()) removeMasks()
                val count = addMasks()
                updateTileState()
                if (maskViews.isNotEmpty()) {
                    showToast("遮罩已开启（${count}个区域）")
                    return true
                } else {
                    showToast("遮罩创建失败，请检查参数")
                    SettingsManager.setEnabled(this, false)
                    return false
                }
            } else {
                updateTileState()
                showToast("遮罩已开启（${maskViews.size}个区域）")
                return true
            }
        } else {
            updateTileState()
            showToast("CatchTouch已启用，切到目标应用时自动生效")
            return true
        }
    }

    fun disableMask() {
        Log.d(TAG, "[DISABLE] masks=${maskViews.size}")
        SettingsManager.setEnabled(this, false)
        wasEnabledBeforeDestroy = false
        removeMasks()
        updateTileState()
        showToast("遮罩已关闭")
    }

    fun isMaskActive(): Boolean = maskViews.isNotEmpty()

    private fun addMasks(): Int {
        if (maskViews.isNotEmpty()) return maskViews.size

        val mode = SettingsManager.getMode(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
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
