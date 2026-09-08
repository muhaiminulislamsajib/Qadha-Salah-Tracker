package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Background receiver for direct notification action buttons:
 * - YES (Prayed)
 * - NO (Missed)
 * - REMIND ME LATER (15m quick snooze)
 * All actions execute directly in the background without opening the main app.
 */
class PrayerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prayerId = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID) ?: ""
        val prayer = PrayerType.fromId(prayerId)

        Log.d("PrayerActionReceiver", "Action received: $action for prayer: ${prayer.displayName}")

        when (action) {
            PrayerActionHelper.ACTION_RECORD_YES -> {
                PrayerActionHelper.handleYes(context, prayer)
                showToast(context, "Alhamdulillah! ${prayer.displayName} marked as prayed.")
            }
            PrayerActionHelper.ACTION_RECORD_NO -> {
                PrayerActionHelper.handleNo(context, prayer)
                showToast(context, "${prayer.displayName} added to Qadha Book (+1).")
            }
            PrayerActionHelper.ACTION_RECORD_REMIND_15M -> {
                val t15 = System.currentTimeMillis() + 15 * 60 * 1000L
                PrayerActionHelper.handleRemindLater(context, prayer, t15)
                showToast(context, "Reminder set for ${prayer.displayName} in 15 minutes.")
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
