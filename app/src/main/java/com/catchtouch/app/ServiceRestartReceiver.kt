package com.catchtouch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AntiTouchService.ACTION_RESTART_SERVICE) {
            Log.d("CatchTouch", "ServiceRestartReceiver: restarting service")
            if (SettingsManager.isEnabled(context)) {
                try {
                    context.startService(Intent(context, AntiTouchService::class.java))
                } catch (_: Exception) {}
            }
        }
    }
}
