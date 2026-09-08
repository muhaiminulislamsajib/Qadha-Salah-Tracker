package com.example

import android.app.NotificationManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Centralized business logic helper for Salat Alert actions (YES, NO, REMIND ME LATER).
 * Ensures consistency across MainActivity, PrayerAlertDialogActivity, and background Notification actions.
 */
object PrayerActionHelper {
    const val ACTION_RECORD_YES = "com.example.ACTION_RECORD_YES"
    const val ACTION_RECORD_NO = "com.example.ACTION_RECORD_NO"
    const val ACTION_RECORD_REMIND_15M = "com.example.ACTION_RECORD_REMIND_15M"

    /**
     * Executes the YES action:
     * - Marks prayer as completed for today
     * - Increments active streak
     * - Increments total logged count
     * - Preserves historical records (updating unresolved REMIND_LATER/MISSED to PRAYED_LATER/EVENTUALLY_PRAYED)
     * - Cancels pending reminder alarm
     * - Dismisses the active notification
     */
    fun handleYes(context: Context, prayer: PrayerType) {
        // Cancel pending reminder alarm
        PrayerAlarmScheduler.cancelPrayerReminder(context, prayer)

        val storage = QadhaStorageHelper(context)
        val currentState = storage.loadState()
        val updatedToday = currentState.todayStatus.toMutableMap()
        val updatedBacklog = currentState.backlog.toMutableMap()
        var currentStreak = currentState.activeStreak
        var totalLogged = currentState.totalLoggedCount
        val updatedHistory = currentState.history.toMutableList()

        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        updatedToday[prayer] = TodayStatus.COMPLETED
        currentStreak++
        totalLogged++

        val unresolvedIdx = updatedHistory.indexOfFirst {
            it.prayerId.equals(prayer.id, ignoreCase = true) &&
            isEntryMatchingDate(it, dateStr, cal.timeInMillis) &&
            (it.actionType == "REMIND_LATER" || it.actionType == "MISSED") &&
            it.laterStatus == null
        }
        if (unresolvedIdx != -1) {
            val old = updatedHistory[unresolvedIdx]
            val nextStatus = if (old.actionType == "REMIND_LATER") "EVENTUALLY_PRAYED" else "PRAYED_LATER"
            updatedHistory[unresolvedIdx] = old.copy(
                laterStatus = nextStatus,
                laterTimestamp = System.currentTimeMillis()
            )
            if (old.actionType == "MISSED") {
                val currentBacklog = updatedBacklog[prayer] ?: 0
                updatedBacklog[prayer] = (currentBacklog - 1).coerceAtLeast(0)
            }
        } else {
            val entry = QadhaHistoryEntry(
                id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                prayerId = prayer.id,
                dateString = dateStr,
                dayOfWeek = dayStr,
                actionType = "PRAYED",
                timestamp = System.currentTimeMillis()
            )
            updatedHistory.add(0, entry)
        }

        val newState = currentState.copy(
            activeStreak = currentStreak,
            backlog = updatedBacklog,
            todayStatus = updatedToday,
            totalLoggedCount = totalLogged,
            history = updatedHistory
        )
        storage.saveState(newState)

        // Notify UI immediately
        notifyStateUpdated(context)

        // Dismiss notification
        dismissNotification(context, prayer)
    }

    /**
     * Executes the NO action:
     * - Marks prayer as missed for today
     * - Adds +1 Qadha to the specific prayer backlog
     * - Resets active streak to 0
     * - Preserves historical records (updating unresolved REMIND_LATER to MISSED)
     * - Cancels pending reminder alarm
     * - Dismisses the active notification
     */
    fun handleNo(context: Context, prayer: PrayerType) {
        // Cancel pending reminder alarm
        PrayerAlarmScheduler.cancelPrayerReminder(context, prayer)

        val storage = QadhaStorageHelper(context)
        val currentState = storage.loadState()
        val updatedToday = currentState.todayStatus.toMutableMap()
        val updatedBacklog = currentState.backlog.toMutableMap()
        val updatedHistory = currentState.history.toMutableList()

        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        updatedToday[prayer] = TodayStatus.MISSED
        val currentBacklog = updatedBacklog[prayer] ?: 0
        updatedBacklog[prayer] = currentBacklog + 1
        val currentStreak = 0

        val unresolvedIdx = updatedHistory.indexOfFirst {
            it.prayerId.equals(prayer.id, ignoreCase = true) &&
            isEntryMatchingDate(it, dateStr, cal.timeInMillis) &&
            it.actionType == "REMIND_LATER" &&
            it.laterStatus == null
        }
        if (unresolvedIdx != -1) {
            val old = updatedHistory[unresolvedIdx]
            updatedHistory[unresolvedIdx] = old.copy(
                laterStatus = "MISSED",
                laterTimestamp = System.currentTimeMillis()
            )
        } else {
            val entry = QadhaHistoryEntry(
                id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                prayerId = prayer.id,
                dateString = dateStr,
                dayOfWeek = dayStr,
                actionType = "MISSED",
                timestamp = System.currentTimeMillis()
            )
            updatedHistory.add(0, entry)
        }

        val newState = currentState.copy(
            activeStreak = currentStreak,
            backlog = updatedBacklog,
            todayStatus = updatedToday,
            history = updatedHistory
        )
        storage.saveState(newState)

        // Notify UI immediately
        notifyStateUpdated(context)

        // Dismiss notification
        dismissNotification(context, prayer)
    }

    /**
     * Executes the REMIND ME LATER action:
     * - Strictly ensures reminder cannot be scheduled before prayer start time
     * - Schedules exact reminder alarm
     * - Records REMIND_LATER in history book
     * - Does NOT add Qadha
     * - Does NOT reset active streak
     * - Dismisses the active notification
     */
    fun handleRemindLater(context: Context, prayer: PrayerType, reminderTimeMillis: Long) {
        val startTime = PrayerAlarmScheduler.getPrayerStartTimeForToday(context, prayer)
        val safeTriggerTime = if (reminderTimeMillis < startTime) startTime else reminderTimeMillis

        PrayerAlarmScheduler.schedulePrayerReminder(context, prayer, safeTriggerTime)

        val storage = QadhaStorageHelper(context)
        val history = storage.loadHistory().toMutableList()
        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(safeTriggerTime))

        val entry = QadhaHistoryEntry(
            id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
            prayerId = prayer.id,
            dateString = dateStr,
            dayOfWeek = dayStr,
            actionType = "REMIND_LATER",
            timestamp = System.currentTimeMillis(),
            note = "Snoozed until $timeFmt"
        )
        history.add(0, entry)
        storage.saveHistory(history)

        // Notify UI immediately
        notifyStateUpdated(context)

        // Dismiss notification
        dismissNotification(context, prayer)
    }

    private fun notifyStateUpdated(context: Context) {
        try {
            val intent = android.content.Intent("com.example.ACTION_STATE_UPDATED")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (ignored: Exception) {}
    }

    private fun dismissNotification(context: Context, prayer: PrayerType) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(prayer.ordinal + 100)
    }
}
