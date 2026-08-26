package com.catchtouch.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import java.io.File
import java.util.Date

class CatchTouchApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var crashSaving = false

    /** 无障碍开关变化后延迟检查，若服务被关且已授权静默重开则写回 */
    private val restoreCheck = Runnable {
        if (!SelectToSpeakService.isRunning) {
            SelectToSpeakService.trySilentRestore(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        registerA11yGuard()
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (!crashSaving) {
                crashSaving = true
                try {
                    val f = File(filesDir, "crash.log")
                    // ponytail: 简单追加+超限清空，需要按次分档时再换日志库
                    if (f.length() > 512 * 1024) f.delete()
                    f.appendText("==== ${Date()} [${t.name}] ====\n${Log.getStackTraceString(e)}\n\n")
                } catch (_: Exception) {}
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun registerA11yGuard() {
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        contentResolver.registerContentObserver(
            uri, false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    handler.removeCallbacks(restoreCheck)
                    handler.postDelayed(restoreCheck, 300)
                }
            }
        )
    }
}
