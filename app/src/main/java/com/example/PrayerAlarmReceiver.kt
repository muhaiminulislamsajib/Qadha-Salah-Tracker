package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "salah_reminders_channel"
        const val CHANNEL_NAME = "Salah & Qadha Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prayerId = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID) ?: ""
        val prayer = PrayerType.fromId(prayerId)

        Log.d("PrayerAlarmReceiver", "Received intent action: $action for prayer: ${prayer.displayName}")

        // 1. Wake screen if off or locked so alert is immediately visible
        wakeScreen(context)

        if (action == PrayerAlarmScheduler.ACTION_PRAYER_REMINDER) {
            // Clear pending reminder state from preferences
            PrayerAlarmScheduler.clearPendingReminder(context, prayer)

            // Show notification to user
            showPrayerNotification(
                context = context,
                prayer = prayer,
                title = "Salah Reminder: ${prayer.displayName}",
                message = "Have you prayed your ${prayer.displayName} Salah? Tap to confirm or add to Qadha."
            )
        } else if (action == PrayerAlarmScheduler.ACTION_PRAYER_ALARM) {
            // Start of Waqt alert
            showPrayerNotification(
                context = context,
                prayer = prayer,
                title = "Waqt Started: ${prayer.displayName}",
                message = "The time for ${prayer.displayName} Salah has arrived. Don't forget to pray on time!"
            )
            // Re-schedule alarms to maintain daily loop
            PrayerAlarmScheduler.scheduleAllAlarms(context)
        }

        // 2. Launch the over-the-app dialog activity directly
        launchOverlayAlertActivity(context, prayer)
    }

    /**
     * Wakes the screen when the phone is locked or display is off.
     */
    private fun wakeScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isInteractive) {
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "qadha:PrayerAlertWakeLock"
                )
                wakeLock.acquire(12000) // 12 seconds to ensure user sees the prompt
            }
        } catch (e: Exception) {
            Log.e("PrayerAlarmReceiver", "Failed to acquire wake lock: ${e.message}")
        }
    }

    /**
     * Launches the PrayerAlertDialogActivity over other apps and on the lockscreen.
     */
    private fun launchOverlayAlertActivity(context: Context, prayer: PrayerType) {
        try {
            val alertIntent = Intent(context, PrayerAlertDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, prayer.id)
            }
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            Log.e("PrayerAlarmReceiver", "Failed to start PrayerAlertDialogActivity: ${e.message}")
        }
    }

    private fun showPrayerNotification(
        context: Context,
        prayer: PrayerType,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders and alerts for daily Salah and Qadha tracking"
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Full screen intent targeting the floating dialog activity (for lock screen & heads up display)
        val alertIntent = Intent(context, PrayerAlertDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, prayer.id)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            prayer.ordinal + 5000,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: YES (Prayed)
        val yesIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionHelper.ACTION_RECORD_YES
            putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, prayer.id)
        }
        val yesPendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal + 6000,
            yesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: NO (Add +1 Qadha)
        val noIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionHelper.ACTION_RECORD_NO
            putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, prayer.id)
        }
        val noPendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal + 7000,
            noIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: REMIND LATER (Quick 15 min snooze)
        val remindIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionHelper.ACTION_RECORD_REMIND_15M
            putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, prayer.id)
        }
        val remindPendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal + 8000,
            remindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_salah_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "YES (Prayed)", yesPendingIntent)
            .addAction(0, "NO (+1 Qadha)", noPendingIntent)
            .addAction(0, "REMIND 15M", remindPendingIntent)
            .build()

        try {
            notificationManager.notify(prayer.ordinal + 100, notification)
        } catch (e: SecurityException) {
            Log.e("PrayerAlarmReceiver", "Notification permission not granted: ${e.message}")
        }
    }
}

