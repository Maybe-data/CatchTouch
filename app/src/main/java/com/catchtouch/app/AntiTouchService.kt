package com.catchtouch.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

@SuppressLint("AccessibilityService")
class AntiTouchService : AccessibilityService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: AntiTouchService? = null
        var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "catchtouch_service"
        private const val NOTIFICATION_ID = 1
        private const val RESTART_REQUEST_CODE = 1001
        private const val WATCHDOG_INTERVAL = 500L
        private const val DEBOUNCE_MS = 400L
        private const val DEBOUNCE_OFF_MS = 1500L
        const val ACTION_RESTART_SERVICE = "com.catchtouch.app.RESTART_SERVICE"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var maskViews = mutableListOf<View>()
    private var keepAliveView: View? = null
    private var lastForegroundPkg = ""
    private var isForeground = false
    private var overlayToastView: View? = null
    private var toastRemoveRunnable: Runnable? = null
    private var pendingToastText: String? = null
    private val toastDelayRunnable = Runnable {
        val text = pendingToastText
        pendingToastText = null
        if (text != null) showToastNow(text)
    }
    private var wasEnabledBeforeDestroy = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var pendingPkg = ""
    private var pendingTime = 0L

    private fun WindowManager.LayoutParams.allowCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "catchtouch:service")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld != true) wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() }
        catch (_: Exception) {}
    }

    private fun detectForegroundApp(): String {
        try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return fallbackDetect()
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(0, now - 1000, now)
            if (!stats.isNullOrEmpty()) {
                val top = stats
                    .filter { it.packageName != packageName && it.lastTimeUsed > 0 }
                    .maxByOrNull { it.lastTimeUsed }
                if (top != null) return top.packageName
            }
        } catch (_: Exception) {}
        return fallbackDetect()
    }

    private fun fallbackDetect(): String {
        try {
            val pkg = rootInActiveWindow?.packageName?.toString() ?: ""
            if (pkg.isNotEmpty() && pkg != packageName) return pkg
        } catch (_: Exception) {}
        return ""
    }

    private fun updateForegroundPkg(detected: String): Boolean {
        if (detected.isEmpty() || detected == lastForegroundPkg) {
            pendingPkg = ""
            return false
        }
        val currentInList = SettingsManager.getSelectedApps(this).let {
            it.isEmpty() || it.contains(lastForegroundPkg)
        }
        val newInList = SettingsManager.getSelectedApps(this).let {
            it.isEmpty() || it.contains(detected)
        }
        val debounceMs = if (currentInList && !newInList) DEBOUNCE_OFF_MS else DEBOUNCE_MS
        val now = SystemClock.elapsedRealtime()
        if (detected == pendingPkg && (now - pendingTime) >= debounceMs) {
            lastForegroundPkg = detected
            pendingPkg = ""
            return true
        }
        if (detected != pendingPkg) {
            pendingPkg = detected
            pendingTime = now
        }
        return false
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                if (SettingsManager.isEnabled(this@AntiTouchService)) {
                    if (updateForegroundPkg(detectForegroundApp())) {
                        applyMaskState()
                    } else {
                        val shouldShow = shouldShowMask()
                        val isShowing = maskViews.isNotEmpty() && maskViews.any { it.isAttachedToWindow }
                        if (shouldShow != isShowing) applyMaskState()
                    }
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, WATCHDOG_INTERVAL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        wasEnabledBeforeDestroy = SettingsManager.isEnabled(this)
        ServiceRestartReceiver.cancelChecks(this)
        startForegroundNotification()
        addKeepAliveOverlay()
        lastForegroundPkg = detectForegroundApp()
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL)
        if (SettingsManager.isEnabled(this)) {
            acquireWakeLock()
            applyMaskState()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !SettingsManager.isEnabled(this)) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isEmpty() || pkg == packageName) return
            if (updateForegroundPkg(pkg)) applyMaskState()
        } else if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (updateForegroundPkg(detectForegroundApp())) applyMaskState()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        wasEnabledBeforeDestroy = SettingsManager.isEnabled(this)
        handler.removeCallbacks(watchdogRunnable)
        releaseWakeLock()
        removeMasks()
        removeKeepAliveOverlay()
        instance = null
        isRunning = false
        isForeground = false
        if (wasEnabledBeforeDestroy) ServiceRestartReceiver.scheduleNextCheck(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (SettingsManager.isEnabled(this)) restartServiceViaAlarm()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun restartServiceViaAlarm() {
        listOf(2000L, 8000L).forEachIndexed { i, delay ->
            try {
                val intent = Intent(this, ServiceRestartReceiver::class.java).apply {
                    action = ACTION_RESTART_SERVICE
                }
                val pi = PendingIntent.getBroadcast(
                    this, RESTART_REQUEST_CODE + i, intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                (getSystemService(ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delay, pi
                )
            } catch (_: Exception) {}
        }
    }

    private fun shouldShowMask(): Boolean {
        if (!SettingsManager.isEnabled(this)) return false
        val apps = SettingsManager.getSelectedApps(this)
        return apps.isEmpty() || apps.contains(lastForegroundPkg)
    }

    private fun applyMaskState() {
        val shouldShow = shouldShowMask()
        val isShowing = maskViews.isNotEmpty() && maskViews.any { it.isAttachedToWindow }
        if (shouldShow && !isShowing) {
            if (maskViews.isNotEmpty()) removeMasks()
            addMasks()
            updateNotification()
            updateTileState()
            showToast("遮罩已生效")
        } else if (!shouldShow && isShowing) {
            removeMasks()
            updateNotification()
            updateTileState()
            showToast("遮罩已关闭")
        }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CatchTouch服务", NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                description = "保持CatchTouch无障碍服务运行"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        updateNotification()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        val fgLabel = if (lastForegroundPkg.isNotEmpty()) {
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(lastForegroundPkg, 0)
                ).toString()
            } catch (_: Exception) { lastForegroundPkg }
        } else "无"
        val text = if (SettingsManager.isEnabled(this)) "防误触运行中 · 当前: $fgLabel" else "防误触服务运行中"
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CatchTouch")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pi)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val notification = builder.build()
        if (isForeground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
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

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        volumeDownTime = System.currentTimeMillis()
                        longPressHandled = false
                        handler.removeCallbacks(longPressCheck)
                        handler.postDelayed(longPressCheck, 600L)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
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
        if (SettingsManager.isEnabled(this)) disableMask() else enableMask()
    }

    fun enableMask(): Boolean {
        SettingsManager.setEnabled(this, true)
        wasEnabledBeforeDestroy = true
        acquireWakeLock()
        lastForegroundPkg = detectForegroundApp()
        return if (shouldShowMask()) {
            if (maskViews.isEmpty() || maskViews.none { it.isAttachedToWindow }) {
                if (maskViews.isNotEmpty()) removeMasks()
                val count = addMasks()
                updateNotification()
                updateTileState()
                if (maskViews.isNotEmpty()) {
                    showToast("遮罩已开启（${count}个区域）")
                    true
                } else {
                    showToast("遮罩创建失败")
                    SettingsManager.setEnabled(this, false)
                    false
                }
            } else {
                updateNotification()
                updateTileState()
                showToast("遮罩已开启（${maskViews.size}个区域）")
                true
            }
        } else {
            updateNotification()
            updateTileState()
            showToast("当前应用不在名单中，切到目标应用时自动生效")
            true
        }
    }

    fun disableMask() {
        SettingsManager.setEnabled(this, false)
        wasEnabledBeforeDestroy = false
        releaseWakeLock()
        removeMasks()
        updateNotification()
        updateTileState()
        showToast("遮罩已关闭")
    }

    private fun addMasks(): Int {
        if (maskViews.isNotEmpty()) return maskViews.size
        val mode = SettingsManager.getMode(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels
        try {
            when (mode) {
                MaskMode.MODE_ONE -> addRectMasks(wm, sw, sh,
                    SettingsManager.getTop(this), SettingsManager.getBottom(this),
                    SettingsManager.getLeft(this), SettingsManager.getRight(this))
                MaskMode.MODE_TWO -> addFanMasks(wm, sw, sh, SettingsManager.getThumb(this))
                MaskMode.MIXED -> {
                    addRectMasks(wm, sw, sh,
                        SettingsManager.getTop(this), SettingsManager.getBottom(this),
                        SettingsManager.getLeft(this), SettingsManager.getRight(this))
                    addFanMasks(wm, sw, sh, SettingsManager.getThumb(this))
                }
            }
        } catch (_: Exception) {}
        return maskViews.size
    }

    private fun addRectMasks(
        wm: WindowManager, sw: Int, sh: Int,
        topP: Float, bottomP: Float, leftP: Float, rightP: Float
    ) {
        val topH = (sh * topP).toInt()
        val bottomH = (sh * bottomP).toInt()
        val leftW = (sw * leftP).toInt()
        val rightW = (sw * rightP).toInt()
        if (topP > 0f) addBlockView(wm, sw, topH, Gravity.TOP or Gravity.START, 0)
        if (bottomP > 0f) addBlockView(wm, sw, bottomH, Gravity.BOTTOM or Gravity.START, 0)
        if (leftP > 0f) addBlockView(wm, leftW, sh - topH - bottomH, Gravity.TOP or Gravity.START, topH)
        if (rightP > 0f) addBlockView(wm, rightW, sh - topH - bottomH, Gravity.TOP or Gravity.END, topH)
    }

    private fun addFanMasks(wm: WindowManager, sw: Int, sh: Int, thumbP: Float) {
        if (thumbP <= 0f) return
        val radius = (Math.max(sw, sh) * thumbP).toInt()
        val leftView = FanMaskView(this).apply { isLeftFan = true; fanRadius = radius }
        wm.addView(leftView, WindowManager.LayoutParams(
            radius, radius,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            allowCutout()
        })
        maskViews.add(leftView)
        val rightView = FanMaskView(this).apply { isLeftFan = false; fanRadius = radius }
        wm.addView(rightView, WindowManager.LayoutParams(
            radius, radius,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            allowCutout()
        })
        maskViews.add(rightView)
    }

    private fun addBlockView(wm: WindowManager, width: Int, height: Int, gravity: Int, y: Int) {
        val view = BlockView(this)
        wm.addView(view, WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.y = y
            allowCutout()
        })
        maskViews.add(view)
    }

    private fun addKeepAliveOverlay() {
        try {
            if (keepAliveView != null) return
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = View(this)
            wm.addView(view, WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START })
            keepAliveView = view
        } catch (_: Exception) {}
    }

    private fun removeKeepAliveOverlay() {
        try { keepAliveView?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        catch (_: Exception) {}
        keepAliveView = null
    }

    private fun removeMasks() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        maskViews.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        maskViews.clear()
    }

    private fun updateTileState() { AntiTouchTileService.instance?.updateTile() }

    private fun showToast(text: String) {
        pendingToastText = text
        handler.removeCallbacks(toastDelayRunnable)
        handler.postDelayed(toastDelayRunnable, 300L)
    }

    private fun showToastNow(text: String) {
        handler.post {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                overlayToastView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                toastRemoveRunnable?.let { handler.removeCallbacks(it) }
                val container = FrameLayout(this).apply {
                    addView(TextView(this@AntiTouchService).apply {
                        this.text = text
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        setPadding(36, 20, 36, 20)
                        background = GradientDrawable().apply {
                            setColor(0x9E9E9E9E.toInt())
                            setCornerRadius(24f)
                        }
                    })
                    setPadding(0, 0, 0, 120)
                }
                wm.addView(container, WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    allowCutout()
                })
                overlayToastView = container
                val removeRunnable = Runnable {
                    try { wm.removeView(container) } catch (_: Exception) {}
                    if (overlayToastView == container) overlayToastView = null
                }
                toastRemoveRunnable = removeRunnable
                handler.postDelayed(removeRunnable, 1000L)
            } catch (_: Exception) {}
        }
    }
}
