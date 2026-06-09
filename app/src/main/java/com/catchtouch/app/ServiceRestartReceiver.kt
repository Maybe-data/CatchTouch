package com.catchtouch.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AntiTouchService.ACTION_RESTART_SERVICE) return
        if (!SettingsManager.isEnabled(context) || AntiTouchService.isRunning) return
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val stillEnabled = enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        if (!stillEnabled) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel("catchtouch_dead", "服务已停止", NotificationManager.IMPORTANCE_HIGH)
                )
                nm.notify(9999, android.app.Notification.Builder(context, "catchtouch_dead")
                    .setContentTitle("CatchTouch服务已停止")
                    .setContentText("点击重新开启无障碍服务")
                    .setSmallIcon(R.drawable.ic_tile)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context, 0,
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .setAutoCancel(true)
                    .build()
                )
            } catch (_: Exception) {}
        }
        scheduleNextCheck(context)
    }

    companion object {
        private const val CHECK_REQUEST_CODE = 2001

        fun scheduleNextCheck(context: Context) {
            try {
                val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                    action = AntiTouchService.ACTION_RESTART_SERVICE
                }
                val pi = PendingIntent.getBroadcast(
                    context, CHECK_REQUEST_CODE, intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5000, pi
                )
            } catch (_: Exception) {}
        }

        fun cancelChecks(context: Context) {
            try {
                val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                    action = AntiTouchService.ACTION_RESTART_SERVICE
                }
                PendingIntent.getBroadcast(
                    context, CHECK_REQUEST_CODE, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )?.cancel()
            } catch (_: Exception) {}
        }
    }
}
