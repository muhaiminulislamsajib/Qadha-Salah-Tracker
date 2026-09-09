package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerAlarmScheduler {
    const val ACTION_PRAYER_ALARM = "com.example.ACTION_PRAYER_ALARM"
    const val ACTION_PRAYER_REMINDER = "com.example.ACTION_PRAYER_REMINDER"
    const val ACTION_END_OF_DAY_REVIEW = "com.example.ACTION_END_OF_DAY_REVIEW"
    const val EXTRA_PRAYER_ID = "prayer_id"
    const val EXTRA_OPEN_END_OF_DAY_REVIEW = "open_end_of_day_review"

    private const val DAILY_ALARM_REQUEST_BASE = 1000
    private const val REMINDER_ALARM_REQUEST_BASE = 2000
    private const val END_OF_DAY_ALARM_REQUEST = 3000

    fun getEndOfDayReviewTime(context: Context): String {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        return prefs.getString("end_of_day_review_time", "22:30") ?: "22:30"
    }

    fun setEndOfDayReviewTime(context: Context, timeStr: String) {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("end_of_day_review_time", timeStr).apply()
        scheduleEndOfDayReview(context)
    }

    fun scheduleEndOfDayReview(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        val timezoneId = prefs.getString("user_timezone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id
        val tz = TimeZone.getTimeZone(timezoneId)

        val customTime = getEndOfDayReviewTime(context)
        val timeParts = customTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 22
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 30

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_END_OF_DAY_REVIEW
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            END_OF_DAY_ALARM_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("PrayerAlarmScheduler", "Scheduled End of Day review alarm at ${cal.time}")
        } catch (e: SecurityException) {
            Log.e("PrayerAlarmScheduler", "SecurityException scheduling End of Day review: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        }
    }

    /**
     * Converts "13:30" into "1:30 PM", "05:00" into "5:00 AM", etc.
     */
    fun formatTime24to12(time24: String): String {
        return try {
            val inFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val outFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = inFormat.parse(time24)
            if (date != null) outFormat.format(date) else time24
        } catch (e: Exception) {
            time24
        }
    }

    /**
     * Returns the epoch milliseconds of when this prayer's Waqt starts for today.
     */
    fun getPrayerStartTimeForToday(context: Context, prayer: PrayerType): Long {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        val timezoneId = prefs.getString("user_timezone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id
        val tz = TimeZone.getTimeZone(timezoneId)
        val customTime = prefs.getString("custom_time_${prayer.id}", when (prayer) {
            PrayerType.FAJR -> "05:00"
            PrayerType.DHUHR -> "13:30"
            PrayerType.ASR -> "16:30"
            PrayerType.MAGHRIB -> "18:45"
            PrayerType.ISHA -> "20:30"
        }) ?: "12:00"

        val timeParts = customTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Checks if the prayer start time has already passed for today.
     * Enforces the strict Salah time requirement: Never ask before start time!
     */
    fun hasPrayerStartedToday(context: Context, prayer: PrayerType, nowMs: Long = System.currentTimeMillis()): Boolean {
        val startTime = getPrayerStartTimeForToday(context, prayer)
        return nowMs >= startTime
    }

    /**
     * Schedules a deferred "Remind Me Later" alarm for a specific prayer.
     * Prevents duplicate alarms by cancelling any previously pending reminder for this prayer.
     */
    fun schedulePrayerReminder(context: Context, prayer: PrayerType, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. Cancel existing reminder alarm to prevent duplicate notifications
        cancelPrayerReminder(context, prayer)

        // 2. Persist the pending reminder timestamp
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("pending_reminder_${prayer.id}", triggerAtMillis).apply()

        // 3. Create exact wakeup alarm
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_REMINDER
            putExtra(EXTRA_PRAYER_ID, prayer.id)
        }

        val requestCode = REMINDER_ALARM_REQUEST_BASE + prayer.ordinal
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d("PrayerAlarmScheduler", "Scheduled Remind Me Later for ${prayer.displayName} at ${Date(triggerAtMillis)}")
        } catch (e: SecurityException) {
            Log.e("PrayerAlarmScheduler", "SecurityException scheduling reminder: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancels any pending reminder alarm for this prayer.
     */
    fun cancelPrayerReminder(context: Context, prayer: PrayerType) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_REMINDER
            putExtra(EXTRA_PRAYER_ID, prayer.id)
        }
        val requestCode = REMINDER_ALARM_REQUEST_BASE + prayer.ordinal
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("pending_reminder_${prayer.id}").apply()
    }

    /**
     * Retrieves stored pending reminder timestamp in millis, or 0 if none.
     */
    fun getPendingReminder(context: Context, prayer: PrayerType): Long {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("pending_reminder_${prayer.id}", 0L)
    }

    /**
     * Clears stored pending reminder.
     */
    fun clearPendingReminder(context: Context, prayer: PrayerType) {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("pending_reminder_${prayer.id}").apply()
    }

    /**
     * Schedules the standard daily alarms at the start of each prayer time.
     */
    fun scheduleAllAlarms(context: Context) {
        val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
        val timezoneId = prefs.getString("user_timezone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id
        val tz = TimeZone.getTimeZone(timezoneId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()

        for (prayer in PrayerType.values()) {
            val customTime = prefs.getString("custom_time_${prayer.id}", when (prayer) {
                PrayerType.FAJR -> "05:00"
                PrayerType.DHUHR -> "13:30"
                PrayerType.ASR -> "16:30"
                PrayerType.MAGHRIB -> "18:45"
                PrayerType.ISHA -> "20:30"
            }) ?: "12:00"

            val timeParts = customTime.split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

            val cal = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If the time has already passed today, schedule for tomorrow
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_PRAYER_ALARM
                putExtra(EXTRA_PRAYER_ID, prayer.id)
            }

            val requestCode = DAILY_ALARM_REQUEST_BASE + prayer.ordinal
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                cal.timeInMillis,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                cal.timeInMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("PrayerAlarmScheduler", "Scheduled daily alarm for ${prayer.displayName} at ${cal.time}")
            } catch (e: SecurityException) {
                Log.e("PrayerAlarmScheduler", "SecurityException scheduling daily alarm: ${e.message}")
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
        }
        scheduleEndOfDayReview(context)
    }
}
