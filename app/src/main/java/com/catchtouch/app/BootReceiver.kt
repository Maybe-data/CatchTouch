package com.catchtouch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.accessibility.selecttospeak.SelectToSpeakService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                // 无障碍服务由系统根据 ENABLED_ACCESSIBILITY_SERVICES 自动重绑；
                // 这里只做静默重开兜底（需 WRITE_SECURE_SETTINGS，无授权时空转）。
                // 不 startService：未绑定的实例拿不到 onServiceConnected，纯属僵尸进程。
                SelectToSpeakService.trySilentRestore(context)
            }
        }
    }
}
