package com.catchtouch.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ShizukuHelper {

    const val REQUEST_CODE = 7001
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** 检测到 Shizuku（服务运行中）时才显示入口 */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasShizukuPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /**
     * 通过 Shizuku 用户服务以 shell 身份执行 pm grant（与 adb 等效），授予 WRITE_SECURE_SETTINGS。
     * 成功后立即尝试静默重开，并在主线程回调结果。
     */
    fun grantWriteSecureSettings(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!hasShizukuPermission()) {
            onResult(false, "未获得 Shizuku 授权")
            return
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, GrantService::class.java))
            .version(1)
            .daemon(false)
            .processNameSuffix("grant")
        Thread {
            var ok = false
            var msg = "已授权静默重开"
            try {
                val latch = CountDownLatch(1)
                val binderRef = AtomicReference<IBinder?>()
                Shizuku.bindUserService(args, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        binderRef.set(binder)
                        latch.countDown()
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                })
                latch.await(10, TimeUnit.SECONDS)
                val binder = binderRef.get() ?: throw IllegalStateException("连接 Shizuku 服务超时")
                ok = IGrantService.Stub.asInterface(binder).pmGrant(context.packageName, PERMISSION)
                if (!ok) msg = "pm grant 失败"
                Shizuku.unbindUserService(args, null, false)
            } catch (e: Throwable) {
                msg = "执行失败: ${e.message}"
            }
            if (ok) SelectToSpeakService.trySilentRestore(context)
            Handler(Looper.getMainLooper()).post { onResult(ok, msg) }
        }.start()
    }
}
