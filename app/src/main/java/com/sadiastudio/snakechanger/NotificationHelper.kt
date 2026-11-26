package com.sadiastudio.snakechanger

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sgs_24h_reminder_channel"
        const val CHANNEL_NAME = "Snake Changer 24h Reminder"
        const val NOTIFICATION_ID = 1024
        const val REQUEST_CODE_24H = 2024
        const val INTERVAL_24H_MS = 24L * 60L * 60L * 1000L
        const val PREFS_NAME = "sgs_native_prefs"
        const val KEY_REMINDER_ENABLED = "pref_reminder_24h"
        const val KEY_LAST_SCHEDULE_TS = "last_24h_reminder_ts"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "24-hour retention reminder for Snake Changer - SGS"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun isReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, true)
    }

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            schedule24HourReminder()
        } else {
            cancel24HourReminder()
        }
    }

    fun schedule24HourReminder() {
        createChannelIfNeeded()
        if (!isReminderEnabled()) {
            cancel24HourReminder()
            return
        }

        val triggerAtMillis = System.currentTimeMillis() + INTERVAL_24H_MS
        prefs.edit().putLong(KEY_LAST_SCHEDULE_TS, triggerAtMillis).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildReminderPendingIntent()

        try {
            alarmManager.cancel(pendingIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    fun cancel24HourReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarmManager.cancel(buildReminderPendingIntent())
        } catch (_: Exception) {
        }
    }

    private fun buildReminderPendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "${GameData.PACKAGE_NAME}.ACTION_24H_REMINDER"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE_24H, intent, flags)
    }

    fun showReminderNotificationNow() {
        createChannelIfNeeded()
        if (!isReminderEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            contentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${GameData.GAME_NAME} - ${GameData.BRAND}")
            .setContentText("24h reminder: Collect Power Coins and beat your level Best Score!")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val helper = NotificationHelper(context.applicationContext)
            if (helper.isReminderEnabled()) {
                helper.showReminderNotificationNow()
                helper.schedule24HourReminder()
            }
        }
    }
}
