package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.catchtouch.app.AntiTouchTileService
import com.catchtouch.app.BlockView
import com.catchtouch.app.FanMaskView
import com.catchtouch.app.MainActivity
import com.catchtouch.app.MaskMode
import com.catchtouch.app.R
import com.catchtouch.app.SettingsManager

/**
 * FQCN 伪装成 Google 系统"随选朗读"(Select to Speak) 服务：
 * 部分 ROM 清理后台时按组件名白名单放行 Google 无障碍服务。
 * 仅改类的完整包名，applicationId 仍为 com.catchtouch.app。
 */
@SuppressLint("AccessibilityService")
class SelectToSpeakService : AccessibilityService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: SelectToSpeakService? = null
        var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "catchtouch_service"
        private const val NOTIFICATION_ID = 1
        private const val WATCHDOG_INTERVAL = 500L
        private const val DEBOUNCE_MS = 500L
        private const val RESTORE_THROTTLE_MS = 1000L

        @Volatile
        private var lastRestoreAttempt = 0L

        fun hasSecureWritePermission(context: android.content.Context): Boolean =
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED

        /**
         * 静默重开无障碍：需 adb 授予 WRITE_SECURE_SETTINGS 后生效。
         * 将本服务组件写回 ENABLED_ACCESSIBILITY_SERVICES，系统随即重新绑定。
         */
        fun trySilentRestore(context: android.content.Context): Boolean {
            if (!SettingsManager.wasA11yEnabled(context)) return false
            if (!hasSecureWritePermission(context)) return false
            val now = SystemClock.elapsedRealtime()
            if (now - lastRestoreAttempt < RESTORE_THROTTLE_MS) return false
            lastRestoreAttempt = now
            return try {
                val cn = ComponentName(context, SelectToSpeakService::class.java).flattenToString()
                val cr = context.contentResolver
                val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                if (current.split(':').contains(cn)) return true
                val updated = if (current.isBlank()) cn else "$current:$cn"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                true
            } catch (_: Exception) {
                false
            }
        }
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
    private var pendingPkg = ""
    private var pendingTime = 0L

    private fun WindowManager.LayoutParams.allowCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun detectForegroundApp(): String {
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
        val now = SystemClock.elapsedRealtime()
        if (detected == pendingPkg && (now - pendingTime) >= DEBOUNCE_MS) {
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
                if (SettingsManager.isEnabled(this@SelectToSpeakService)) {
                    if (updateForegroundPkg(detectForegroundApp())) {
                        applyMaskState()
                    } else {
                        val shouldShow = shouldShowMask()
                        val isShowing = maskViews.isNotEmpty()
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
        SettingsManager.setWasA11yEnabled(this, true)
        startForegroundNotification()
        addKeepAliveOverlay()
        lastForegroundPkg = detectForegroundApp()
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL)
        if (SettingsManager.isEnabled(this)) {
            applyMaskState()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isEmpty() || pkg == packageName) return

            val apps = SettingsManager.getSelectedApps(this)
            if ((apps.isEmpty() || apps.contains(pkg)) && !SettingsManager.isEnabled(this)) {
                SettingsManager.setEnabled(this, true)
                lastForegroundPkg = pkg
                applyMaskState()
                return
            }

            if (updateForegroundPkg(pkg)) applyMaskState()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(watchdogRunnable)
        removeMasks()
        removeKeepAliveOverlay()
        instance = null
        isRunning = false
        isForeground = false
        // 静默重开：服务被关闭/解绑的瞬间把组件写回系统设置，触发立即重绑
        trySilentRestore(this)
    }

    private fun shouldShowMask(): Boolean {
        if (!SettingsManager.isEnabled(this)) return false
        val apps = SettingsManager.getSelectedApps(this)
        return apps.isEmpty() || apps.contains(lastForegroundPkg)
    }

    private fun applyMaskState() {
        val shouldShow = shouldShowMask()
        val isShowing = maskViews.isNotEmpty()
        if (shouldShow && !isShowing) {
            removeMasks()
            addMasks()
            updateNotification()
            updateTileState()
            showToast("遮罩已生效")
        } else if (!shouldShow && isShowing) {
            removeMasks()
            updateNotification()
            updateTileState()
            showToast("遮罩已关闭")
        } else {
            updateNotification()
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
        val text = if (SettingsManager.isEnabled(this)) {
            val inTargetList = SettingsManager.getSelectedApps(this).let {
                it.isEmpty() || it.contains(lastForegroundPkg)
            }
            if (!inTargetList) "防误触服务运行中·无遮罩"
            else {
                val maskShowing = maskViews.isNotEmpty()
                if (maskShowing) "防误触遮罩开启 · 当前: $fgLabel" else "防误触遮罩关闭 · 当前: $fgLabel"
            }
        } else "防误触服务关闭"
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

    private var volumeUpTime = 0L
    private var longPressHandled = false
    private val longPressCheck = Runnable {
        if (!longPressHandled && volumeUpTime > 0L) {
            longPressHandled = true
            toggleMask()
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        volumeUpTime = System.currentTimeMillis()
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
                            AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                        )
                    }
                    volumeUpTime = 0L
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
        lastForegroundPkg = detectForegroundApp()
        return if (shouldShowMask()) {
            if (maskViews.isEmpty() || maskViews.none { it.isAttachedToWindow }) {
                if (maskViews.isNotEmpty()) removeMasks()
                addMasks()
                updateNotification()
                updateTileState()
                if (maskViews.isNotEmpty()) {
                    showToast("遮罩已开启(${SettingsManager.getMode(this).label})")
                    true
                } else {
                    showToast("遮罩创建失败")
                    SettingsManager.setEnabled(this, false)
                    false
                }
            } else {
                updateNotification()
                updateTileState()
                showToast("遮罩已开启(${SettingsManager.getMode(this).label})")
                true
            }
        } else {
            updateNotification()
            updateTileState()
            showToast("防误触服务开启,当前非目标应用")
            true
        }
    }

    fun disableMask() {
        SettingsManager.setEnabled(this, false)
        removeMasks()
        updateNotification()
        updateTileState()
        showToast("防误触服务关闭")
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
                MaskMode.MODE_TWO -> addFanMasks(wm, sw, sh,
                    SettingsManager.getThumbLeft(this), SettingsManager.getThumbRight(this))
                MaskMode.MIXED -> {
                    addRectMasks(wm, sw, sh,
                        SettingsManager.getTop(this), SettingsManager.getBottom(this),
                        SettingsManager.getLeft(this), SettingsManager.getRight(this))
                    addFanMasks(wm, sw, sh,
                        SettingsManager.getThumbLeft(this), SettingsManager.getThumbRight(this))
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
        if (topP > 0f) addBlockView(wm, sw, topH, Gravity.TOP or Gravity.START, 0)
        if (bottomP > 0f) addBlockView(wm, sw, bottomH, Gravity.BOTTOM or Gravity.START, 0)
        addSideMaskWithHole(wm, sw, sh, topH, bottomH, leftP, true)
        addSideMaskWithHole(wm, sw, sh, topH, bottomH, rightP, false)
    }

    /**
     * 单侧竖条遮罩：无挖孔时整条一块；有挖孔时拆为上下两段，
     * 孔处不放置视图（触摸自然穿透）。孔自动夹紧在竖条范围内。
     */
    private fun addSideMaskWithHole(
        wm: WindowManager, sw: Int, sh: Int,
        topH: Int, bottomH: Int, sideP: Float, isLeft: Boolean
    ) {
        if (sideP <= 0f) return
        val sideW = (sw * sideP).toInt()
        val stripTop = topH
        val stripBottom = sh - bottomH
        val gravity = if (isLeft) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END

        fun block(top: Int, bottom: Int) {
            if (bottom - top > 0) addBlockView(wm, sideW, bottom - top, gravity, top)
        }

        val holeHPct = if (isLeft) SettingsManager.getLeftHoleHeight(this) else SettingsManager.getRightHoleHeight(this)
        if (holeHPct <= 0f || stripBottom <= stripTop) {
            block(stripTop, stripBottom)
            return
        }

        val posPct = if (isLeft) SettingsManager.getLeftHolePos(this) else SettingsManager.getRightHolePos(this)
        val holeH = (sh * holeHPct).toInt()
        var holeBottom = sh - (sh * posPct).toInt() // 位置从底部向上计
        var holeTop = holeBottom - holeH
        if (holeBottom - holeTop >= stripBottom - stripTop) return // 孔覆盖整条：该侧完全放行
        if (holeBottom > stripBottom) { holeTop -= holeBottom - stripBottom; holeBottom = stripBottom }
        if (holeTop < stripTop) { holeBottom += stripTop - holeTop; holeTop = stripTop }

        block(stripTop, holeTop)
        block(holeBottom, stripBottom)
    }

    private fun addFanMasks(wm: WindowManager, sw: Int, sh: Int, leftP: Float, rightP: Float) {
        if (leftP <= 0f && rightP <= 0f) return
        val base = Math.max(sw, sh)
        if (leftP > 0f) {
            val radiusL = (base * leftP).toInt()
            val leftView = FanMaskView(this).apply { isLeftFan = true; fanRadius = radiusL }
            wm.addView(leftView, WindowManager.LayoutParams(
                radiusL, radiusL,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                allowCutout()
            })
            maskViews.add(leftView)
        }
        if (rightP > 0f) {
            val radiusR = (base * rightP).toInt()
            val rightView = FanMaskView(this).apply { isLeftFan = false; fanRadius = radiusR }
            wm.addView(rightView, WindowManager.LayoutParams(
                radiusR, radiusR,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                allowCutout()
            })
            maskViews.add(rightView)
        }
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
        handler.postDelayed(toastDelayRunnable, 1000L)
    }

    private fun showToastNow(text: String) {
        handler.post {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                overlayToastView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                toastRemoveRunnable?.let { handler.removeCallbacks(it) }
                val container = FrameLayout(this).apply {
                    addView(TextView(this@SelectToSpeakService).apply {
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
