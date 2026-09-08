package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UserUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("UserUnlockReceiver", "Boot received (${intent.action}). Rescheduling prayer alarms.")
            // Re-schedule all custom clock triggers on boot safely
            PrayerAlarmScheduler.scheduleAllAlarms(context)
        }
    }
}
