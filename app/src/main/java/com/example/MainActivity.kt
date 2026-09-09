package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import android.os.Build
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ==========================================
// 1. DOMAIN MODELS & DATA DEFINITIONS
// ==========================================

enum class PrayerType(
    val id: String, 
    val displayName: String, 
    val subtitleName: String, 
    val rakah: Int
) {
    FAJR("fajr", "Fajr", "Dawn • Slanted Light", 2),
    DHUHR("dhuhr", "Dhuhr", "Noon • Solar Zenith", 4),
    ASR("asr", "Asr", "Afternoon • Shadow Lengthening", 4),
    MAGHRIB("maghrib", "Maghrib", "Sunset • Direct Dusk", 3),
    ISHA("isha", "Isha", "Night • Dominant Dark", 4);

    companion object {
        fun fromId(id: String): PrayerType = values().find { it.id == id } ?: FAJR
    }
}

enum class TodayStatus {
    UNTRACKED,
    COMPLETED,
    MISSED
}

data class BadgeMilestone(
    val id: String,
    val name: String,
    val description: String,
    val requiredStreak: Int,
    val hexColor: Long
)

// List of spiritually gamified Milestone Badges
val MILITARY_BADGES = listOf(
    BadgeMilestone("bronze", "Bronze Badge", "Achieved a sacred 3-prayer active logging streak.", 3, 0xFFCD7F32),
    BadgeMilestone("silver", "Silver Badge", "Steadfast in prayers with a solid 7-prayer active logging streak.", 7, 0xFFC0C0C0),
    BadgeMilestone("gold", "Gold Badge", "Devoted and disciplined! Logged 15 prayers consecutively.", 15, 0xFFFFD700),
    BadgeMilestone("platinum", "Platinum Badge", "Holy Guardian! Reached a milestone of 30+ consecutive prayers logged.", 30, 0xFFE5E4E2)
)

data class QadhaHistoryEntry(
    val id: String,
    val prayerId: String,
    val dateString: String,
    val dayOfWeek: String,
    val actionType: String, // "MISSED" (added to backlog), "RECOVERED" (subtracted), "MANUAL_ADD", "PRAYED", "REMIND_LATER"
    val timestamp: Long,
    val laterStatus: String? = null, // e.g., "PRAYED_LATER", "EVENTUALLY_PRAYED", "MISSED"
    val laterTimestamp: Long? = null,
    val note: String? = null
) {
    fun toSerialized(): String {
        val ls = laterStatus ?: ""
        val lt = laterTimestamp?.toString() ?: ""
        val nt = note ?: ""
        return "$id|$prayerId|$dateString|$dayOfWeek|$actionType|$timestamp|$ls|$lt|$nt"
    }

    companion object {
        fun fromSerialized(str: String): QadhaHistoryEntry? {
            val parts = str.split("|")
            if (parts.size < 6) return null
            return QadhaHistoryEntry(
                id = parts[0],
                prayerId = parts[1],
                dateString = parts[2],
                dayOfWeek = parts[3],
                actionType = parts[4],
                timestamp = parts[5].toLongOrNull() ?: 0L,
                laterStatus = parts.getOrNull(6)?.takeIf { it.isNotEmpty() },
                laterTimestamp = parts.getOrNull(7)?.toLongOrNull(),
                note = parts.getOrNull(8)?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class TrackerState(
    val activeStreak: Int = 0,
    val backlog: Map<PrayerType, Int> = PrayerType.values().associateWith { 0 },
    val todayStatus: Map<PrayerType, TodayStatus> = PrayerType.values().associateWith { TodayStatus.UNTRACKED },
    val missedPrayers: List<PrayerType> = emptyList(),
    val currentPrayerIndex: Int = 0,
    val totalLoggedCount: Int = 0,
    val history: List<QadhaHistoryEntry> = emptyList(),
    val isGoogleUser: Boolean = false,
    val googleEmail: String? = null,
    val currentUserId: String? = null,
    val isSyncing: Boolean = false,
    val authChoiceMade: Boolean = false,
    val cloudRestoredPayload: TrackerState? = null,
    val showEndOfDayReview: Boolean = false
) {
    // Current prayer to confirm in the sequential dialog (displays only one dialog at a time)
    val currentSequentialPrayer: PrayerType?
        get() = if (currentPrayerIndex in missedPrayers.indices) missedPrayers[currentPrayerIndex] else null

    // Backwards compatibility queue property
    val dialogQueue: List<PrayerType>
        get() = if (currentPrayerIndex in missedPrayers.indices) missedPrayers.subList(currentPrayerIndex, missedPrayers.size) else emptyList()
}

// ==========================================
// 2. STATE PERSISTENCE FACTORY / OFFLINE LAYER
// ==========================================

data class PendingOverlaySlot(
    val prayerId: String,
    val dateString: String,
    val dayOfWeek: String,
    val timestamp: Long
) {
    fun toSerialized(): String = "$prayerId|$dateString|$dayOfWeek|$timestamp"
    
    companion object {
        fun fromSerialized(s: String): PendingOverlaySlot? {
            val parts = s.split("|")
            if (parts.size < 4) return null
            return PendingOverlaySlot(
                prayerId = parts[0],
                dateString = parts[1],
                dayOfWeek = parts[2],
                timestamp = parts[3].toLongOrNull() ?: 0L
            )
        }
    }
}

class QadhaStorageHelper(private val context: Context, private val customUserId: String? = null) {
    private val globalPrefs: SharedPreferences = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)

    val activeUserId: String
        get() = customUserId ?: QadhaAuthManager.getCurrentUser(context)?.uid ?: "unauthenticated"

    private val userPrefs: SharedPreferences
        get() {
            val safeUid = activeUserId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            return context.getSharedPreferences("qadha_user_$safeUid", Context.MODE_PRIVATE)
        }

    fun getTimeZoneId(): String {
        return globalPrefs.getString("user_timezone", java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id
    }

    fun setTimeZoneId(tzId: String) {
        globalPrefs.edit().putString("user_timezone", tzId).apply()
    }

    fun getPrayerCustomTime(prayer: PrayerType): String {
        val defaultTime = when (prayer) {
            PrayerType.FAJR -> "05:00"
            PrayerType.DHUHR -> "13:30"
            PrayerType.ASR -> "16:30"
            PrayerType.MAGHRIB -> "18:45"
            PrayerType.ISHA -> "20:30"
        }
        return globalPrefs.getString("custom_time_${prayer.id}", defaultTime) ?: defaultTime
    }

    fun setPrayerCustomTime(prayer: PrayerType, timeStr: String) {
        globalPrefs.edit().putString("custom_time_${prayer.id}", timeStr).apply()
    }

    companion object {
        fun calculateMissedSlots(
            context: Context,
            lastCheckTimeMs: Long,
            currentTimeMs: Long
        ): List<PendingOverlaySlot> {
            val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
            val timezoneId = prefs.getString("user_timezone", java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id
            val tz = java.util.TimeZone.getTimeZone(timezoneId)
            
            val list = mutableListOf<PendingOverlaySlot>()
            if (currentTimeMs <= lastCheckTimeMs) return list
            
            // Safety: clamp lookback to at most 7 days to prevent any blocking loop
            val maxLookbackMs = 7 * 24 * 60 * 60 * 1000L
            val safeStartMs = if (lastCheckTimeMs <= 0) currentTimeMs - (24 * 60 * 60 * 1000L) else maxOf(lastCheckTimeMs, currentTimeMs - maxLookbackMs)
            
            val sDay = java.util.Calendar.getInstance(tz).apply {
                timeInMillis = safeStartMs
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            
            val eDay = java.util.Calendar.getInstance(tz).apply {
                timeInMillis = currentTimeMs
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            
            val currentDayCal = java.util.Calendar.getInstance(tz).apply { timeInMillis = sDay.timeInMillis }
            var iterationCount = 0
            val maxIterations = 14
            
            while (!currentDayCal.after(eDay) && iterationCount < maxIterations) {
                iterationCount++
                val y = currentDayCal.get(java.util.Calendar.YEAR)
                val m = currentDayCal.get(java.util.Calendar.MONTH)
                val d = currentDayCal.get(java.util.Calendar.DAY_OF_MONTH)
                
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
                    
                    val prayerCal = java.util.Calendar.getInstance(tz).apply {
                        set(java.util.Calendar.YEAR, y)
                        set(java.util.Calendar.MONTH, m)
                        set(java.util.Calendar.DAY_OF_MONTH, d)
                        set(java.util.Calendar.HOUR_OF_DAY, hour)
                        set(java.util.Calendar.MINUTE, minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    
                    val prayerTimeMs = prayerCal.timeInMillis
                    
                    if (prayerTimeMs > safeStartMs && prayerTimeMs <= currentTimeMs) {
                        val dateFmt = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).apply { timeZone = tz }
                        val dayFmt = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).apply { timeZone = tz }
                        
                        list.add(
                            PendingOverlaySlot(
                                prayerId = prayer.id,
                                dateString = dateFmt.format(java.util.Date(prayerTimeMs)),
                                dayOfWeek = dayFmt.format(java.util.Date(prayerTimeMs)),
                                timestamp = prayerTimeMs
                            )
                        )
                    }
                }
                
                currentDayCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            
            return list.sortedBy { it.timestamp }
        }

        fun loadOverlayQueue(prefs: SharedPreferences): List<PendingOverlaySlot> {
            val serialized = prefs.getString("pending_overlay_queue", "") ?: ""
            if (serialized.isEmpty()) return emptyList()
            return serialized.split(";").mapNotNull { PendingOverlaySlot.fromSerialized(it) }
        }

        fun saveOverlayQueue(prefs: SharedPreferences, queue: List<PendingOverlaySlot>) {
            val serialized = queue.joinToString(";") { it.toSerialized() }
            prefs.edit().putString("pending_overlay_queue", serialized).apply()
        }
    }


    fun loadHistory(): List<QadhaHistoryEntry> {
        val set = userPrefs.getStringSet("qadha_history_list", null) ?: return emptyList()
        return set.mapNotNull { QadhaHistoryEntry.fromSerialized(it) }
            .sortedByDescending { it.timestamp }
    }

    fun saveHistory(list: List<QadhaHistoryEntry>) {
        val set = list.map { it.toSerialized() }.toSet()
        userPrefs.edit().putStringSet("qadha_history_list", HashSet(set)).apply()
    }

    fun addHistoryEntry(prayer: PrayerType, actionType: String, customDate: String? = null, customDay: String? = null, timestampOffsetMs: Long = 0) {
        val list = loadHistory().toMutableList()
        val currentTime = System.currentTimeMillis() + timestampOffsetMs
        val date = Date(currentTime)
        val dateString = customDate ?: SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
        val dayOfWeek = customDay ?: SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        val id = "${prayer.id}_${currentTime}_${(0..1000).random()}"
        
        val entry = QadhaHistoryEntry(
            id = id,
            prayerId = prayer.id,
            dateString = dateString,
            dayOfWeek = dayOfWeek,
            actionType = actionType,
            timestamp = currentTime
        )
        list.add(entry)
        saveHistory(list)
    }

    fun hasInitializedData(): Boolean {
        return userPrefs.contains("active_streak")
    }

    fun loadState(): TrackerState {
        val currentUser = QadhaAuthManager.getCurrentUser(context)
        val streak = userPrefs.getInt("active_streak", 0)
        val totalLogged = userPrefs.getInt("total_logged", 0)
        
        val backlog = PrayerType.values().associateWith { type ->
            userPrefs.getInt("backlog_${type.id}", 0)
        }
        
        val todayStatus = PrayerType.values().associateWith { type ->
            val statusStr = userPrefs.getString("today_status_${type.id}", TodayStatus.UNTRACKED.name)
            try {
                TodayStatus.valueOf(statusStr!!)
            } catch (e: Exception) {
                TodayStatus.UNTRACKED
            }
        }
        
        val history = loadHistory()
        val isAuth = currentUser != null
        val isGoogle = currentUser?.isGoogle ?: false
        val email = currentUser?.email
        
        return TrackerState(
            activeStreak = streak,
            backlog = backlog,
            todayStatus = todayStatus,
            totalLoggedCount = totalLogged,
            missedPrayers = emptyList(),
            currentPrayerIndex = 0,
            history = history,
            isGoogleUser = isGoogle,
            googleEmail = email,
            currentUserId = currentUser?.uid,
            authChoiceMade = isAuth
        )
    }

    fun saveState(state: TrackerState) {
        val editor = userPrefs.edit()
        editor.putInt("active_streak", state.activeStreak)
        editor.putInt("total_logged", state.totalLoggedCount)
        
        state.backlog.forEach { (type, count) ->
            editor.putInt("backlog_${type.id}", count)
        }
        
        state.todayStatus.forEach { (type, status) ->
            editor.putString("today_status_${type.id}", status.name)
        }
        
        editor.apply()
        saveHistory(state.history)
    }
    
    fun clearTodayStatusOnly() {
        val editor = userPrefs.edit()
        PrayerType.values().forEach { type ->
            editor.putString("today_status_${type.id}", TodayStatus.UNTRACKED.name)
        }
        editor.apply()
    }
}

// ==========================================
// 3. STATE REPRESENTATION (VIEWMODEL)
// ==========================================

class QadhaTrackerViewModel(
    context: Context,
    private val customStorage: QadhaStorageHelper? = null
) : ViewModel() {
    private val appContext = context.applicationContext
    private val storage: QadhaStorageHelper
        get() = customStorage ?: QadhaStorageHelper(appContext, _uiState.value.currentUserId)
    val cloudHelper = QadhaCloudBackupHelper(context)
    
    // Initial state with sequential prayer confirmation flow ready
    private val _uiState = MutableStateFlow(
        TrackerState(
            activeStreak = 0,
            missedPrayers = emptyList(),
            currentPrayerIndex = 0,
            authChoiceMade = false
        )
    )
    val uiState: StateFlow<TrackerState> = _uiState.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    // Helper: Returns list of prayers that have strictly started today, are untracked, and have no pending future reminder
    private fun getEligiblePromptPrayers(currentTodayStatus: Map<PrayerType, TodayStatus>): List<PrayerType> {
        val now = System.currentTimeMillis()
        return PrayerType.values().filter { prayer ->
            val hasStarted = PrayerAlarmScheduler.hasPrayerStartedToday(appContext, prayer, now)
            val isUntracked = (currentTodayStatus[prayer] ?: TodayStatus.UNTRACKED) == TodayStatus.UNTRACKED
            val pendingReminder = PrayerAlarmScheduler.getPendingReminder(appContext, prayer)
            val isReminderDue = (pendingReminder == 0L || pendingReminder <= now)
            hasStarted && isUntracked && isReminderDue
        }
    }

    init {
        if (customStorage != null) {
            _uiState.value = customStorage.loadState()
        } else {
            // Move initial database/storage loading off the main thread using Dispatchers.IO
            viewModelScope.launch(Dispatchers.IO) {
                val currentUser = QadhaAuthManager.getCurrentUser(appContext)
                if (currentUser != null) {
                    val userStorage = QadhaStorageHelper(appContext, currentUser.uid)
                    val loadedState = userStorage.loadState()
                    val isFirstBoot = !userStorage.hasInitializedData()
                    
                    val state = if (isFirstBoot) {
                        val initialBacklog = PrayerType.values().associateWith { 0 }
                        val initialToday = PrayerType.values().associateWith { TodayStatus.UNTRACKED }
                        val eligiblePrayers = getEligiblePromptPrayers(initialToday)

                        val stateWithQueue = TrackerState(
                            activeStreak = 0,
                            backlog = initialBacklog,
                            todayStatus = initialToday,
                            missedPrayers = eligiblePrayers,
                            currentPrayerIndex = 0,
                            totalLoggedCount = 0,
                            history = emptyList(),
                            isGoogleUser = currentUser.isGoogle,
                            googleEmail = currentUser.email,
                            currentUserId = currentUser.uid,
                            authChoiceMade = true
                        )
                        userStorage.saveState(stateWithQueue)
                        stateWithQueue
                    } else {
                        val eligiblePrayers = getEligiblePromptPrayers(loadedState.todayStatus)
                        loadedState.copy(
                            isGoogleUser = currentUser.isGoogle,
                            googleEmail = currentUser.email,
                            currentUserId = currentUser.uid,
                            authChoiceMade = true,
                            missedPrayers = eligiblePrayers,
                            currentPrayerIndex = 0
                        )
                    }
                    _uiState.value = state

                    if (currentUser.isGoogle) {
                        cloudHelper.setGoogleUser(currentUser.uid, currentUser.email ?: "", true)
                        triggerCloudBackup()
                    }
                } else {
                    // When no user is authenticated, show the proper Login/Sign Up screen!
                    _uiState.value = TrackerState(
                        activeStreak = 0,
                        backlog = PrayerType.values().associateWith { 0 },
                        todayStatus = PrayerType.values().associateWith { TodayStatus.UNTRACKED },
                        missedPrayers = emptyList(),
                        currentPrayerIndex = 0,
                        totalLoggedCount = 0,
                        history = emptyList(),
                        isGoogleUser = false,
                        googleEmail = null,
                        currentUserId = null,
                        authChoiceMade = false
                    )
                }
            }
        }
    }

    // Handles user answer from the sequential pop-up queue (YES / NO)
    fun handleSequentialResponse(prayer: PrayerType, completed: Boolean) {
        val nextIndex = _uiState.value.currentPrayerIndex + 1
        if (completed) {
            PrayerActionHelper.handleYes(appContext, prayer)
        } else {
            PrayerActionHelper.handleNo(appContext, prayer)
        }
        val loadedState = storage.loadState()
        val newState = loadedState.copy(currentPrayerIndex = nextIndex)
        _uiState.value = newState

        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    // Handles "REMIND ME LATER" choice
    // Rules:
    // - Do not add Qadha
    // - Do not reset streak
    // - Schedule reminder alarm
    // - Advance to next prayer in queue without duplicate reminders
    // - Record REMIND_LATER in history book
    fun handleRemindMeLater(prayer: PrayerType, reminderTimeMillis: Long) {
        PrayerActionHelper.handleRemindLater(appContext, prayer, reminderTimeMillis)
        val nextIndex = _uiState.value.currentPrayerIndex + 1
        val updatedHistory = storage.loadHistory()
        val newState = _uiState.value.copy(
            currentPrayerIndex = nextIndex,
            history = updatedHistory
        )
        _uiState.value = newState

        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    // Directly prompt a specific prayer (e.g. tapped from notification)
    fun promptSpecificPrayer(prayer: PrayerType) {
        _uiState.update { currentState ->
            currentState.copy(
                missedPrayers = listOf(prayer),
                currentPrayerIndex = 0
            )
        }
    }

    // Logs Qadha activity for a specific date (History Book tab requirement)
    fun logQadhaForSpecificDate(prayer: PrayerType, actionType: String, dateMillis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        val currentState = _uiState.value
        val updatedBacklog = currentState.backlog.toMutableMap()
        val updatedHistory = currentState.history.toMutableList()

        if (actionType == "MISSED" || actionType == "MANUAL_ADD") {
            val currentCount = updatedBacklog[prayer] ?: 0
            updatedBacklog[prayer] = currentCount + 1

            val entry = QadhaHistoryEntry(
                id = "${prayer.id}_${dateMillis}_${(0..10000).random()}",
                prayerId = prayer.id,
                dateString = dateStr,
                dayOfWeek = dayStr,
                actionType = actionType,
                timestamp = dateMillis
            )
            updatedHistory.add(0, entry)
        } else if (actionType == "RECOVERED") {
            val currentCount = updatedBacklog[prayer] ?: 0
            updatedBacklog[prayer] = (currentCount - 1).coerceAtLeast(0)

            // If there is an unresolved MISSED/MANUAL_ADD entry for this prayer on this date, update its laterStatus
            val unresolvedIdx = updatedHistory.indexOfFirst {
                it.prayerId.equals(prayer.id, ignoreCase = true) &&
                isEntryMatchingDate(it, dateStr, dateMillis) &&
                (it.actionType == "MISSED" || it.actionType == "MANUAL_ADD" || it.actionType == "REMIND_LATER") &&
                it.laterStatus == null
            }
            if (unresolvedIdx != -1) {
                val old = updatedHistory[unresolvedIdx]
                val nextStatus = if (old.actionType == "REMIND_LATER") "EVENTUALLY_PRAYED" else "PRAYED_LATER"
                updatedHistory[unresolvedIdx] = old.copy(
                    laterStatus = nextStatus,
                    laterTimestamp = System.currentTimeMillis()
                )
            } else {
                val entry = QadhaHistoryEntry(
                    id = "${prayer.id}_${dateMillis}_${(0..10000).random()}",
                    prayerId = prayer.id,
                    dateString = dateStr,
                    dayOfWeek = dayStr,
                    actionType = "PRAYED",
                    timestamp = dateMillis,
                    laterStatus = "PRAYED_LATER",
                    laterTimestamp = dateMillis
                )
                updatedHistory.add(0, entry)
            }
        }

        val newState = currentState.copy(
            backlog = updatedBacklog,
            history = updatedHistory
        )
        _uiState.value = newState

        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    // Adjust Backlog Counter Manually in Qadha Book Tracker
    fun modifyBacklogCount(prayer: PrayerType, increment: Boolean) {
        val currentState = _uiState.value
        val updatedBacklog = currentState.backlog.toMutableMap()
        val currentVal = updatedBacklog[prayer] ?: 0
        val newVal = if (increment) currentVal + 1 else (currentVal - 1).coerceAtLeast(0)
        updatedBacklog[prayer] = newVal

        val updatedHistory = currentState.history.toMutableList()
        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        if (increment) {
            val entry = QadhaHistoryEntry(
                id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                prayerId = prayer.id,
                dateString = dateStr,
                dayOfWeek = dayStr,
                actionType = "MANUAL_ADD",
                timestamp = System.currentTimeMillis()
            )
            updatedHistory.add(0, entry)
        } else {
            val unresolvedIdx = updatedHistory.indexOfFirst {
                it.prayerId.equals(prayer.id, ignoreCase = true) &&
                (it.actionType == "MISSED" || it.actionType == "MANUAL_ADD") &&
                it.laterStatus == null
            }
            if (unresolvedIdx != -1) {
                val old = updatedHistory[unresolvedIdx]
                updatedHistory[unresolvedIdx] = old.copy(
                    laterStatus = "PRAYED_LATER",
                    laterTimestamp = System.currentTimeMillis()
                )
            } else {
                val entry = QadhaHistoryEntry(
                    id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                    prayerId = prayer.id,
                    dateString = dateStr,
                    dayOfWeek = dayStr,
                    actionType = "RECOVERED",
                    timestamp = System.currentTimeMillis(),
                    laterStatus = "PRAYED_LATER",
                    laterTimestamp = System.currentTimeMillis()
                )
                updatedHistory.add(0, entry)
            }
        }

        val newState = currentState.copy(backlog = updatedBacklog, history = updatedHistory)
        _uiState.value = newState
        
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    // Toggle today's prayer status manually from the Dashboard
    fun toggleTodayStatus(prayer: PrayerType) {
        val currentState = _uiState.value
        val updatedToday = currentState.todayStatus.toMutableMap()
        val currentStatus = updatedToday[prayer] ?: TodayStatus.UNTRACKED
        val updatedBacklog = currentState.backlog.toMutableMap()
        var currentStreak = currentState.activeStreak
        var totalLogged = currentState.totalLoggedCount
        val updatedHistory = currentState.history.toMutableList()

        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        val nextStatus = when (currentStatus) {
            TodayStatus.UNTRACKED -> {
                currentStreak++
                totalLogged++
                val entry = QadhaHistoryEntry(
                    id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                    prayerId = prayer.id,
                    dateString = dateStr,
                    dayOfWeek = dayStr,
                    actionType = "PRAYED",
                    timestamp = System.currentTimeMillis()
                )
                updatedHistory.add(0, entry)
                TodayStatus.COMPLETED
            }
            TodayStatus.COMPLETED -> {
                // Turn to Missed -> backlog + 1, and reset streak
                val backlogVal = updatedBacklog[prayer] ?: 0
                updatedBacklog[prayer] = backlogVal + 1
                currentStreak = 0

                val entry = QadhaHistoryEntry(
                    id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                    prayerId = prayer.id,
                    dateString = dateStr,
                    dayOfWeek = dayStr,
                    actionType = "MISSED",
                    timestamp = System.currentTimeMillis()
                )
                updatedHistory.add(0, entry)

                TodayStatus.MISSED
            }
            TodayStatus.MISSED -> {
                val backlogVal = updatedBacklog[prayer] ?: 0
                updatedBacklog[prayer] = (backlogVal - 1).coerceAtLeast(0)

                val unresolvedIdx = updatedHistory.indexOfFirst {
                    it.prayerId.equals(prayer.id, ignoreCase = true) &&
                    isEntryMatchingDate(it, dateStr, cal.timeInMillis) &&
                    it.actionType == "MISSED" &&
                    it.laterStatus == null
                }
                if (unresolvedIdx != -1) {
                    val old = updatedHistory[unresolvedIdx]
                    updatedHistory[unresolvedIdx] = old.copy(
                        laterStatus = "PRAYED_LATER",
                        laterTimestamp = System.currentTimeMillis()
                    )
                } else {
                    val entry = QadhaHistoryEntry(
                        id = "${prayer.id}_${System.currentTimeMillis()}_${(0..1000).random()}",
                        prayerId = prayer.id,
                        dateString = dateStr,
                        dayOfWeek = dayStr,
                        actionType = "RECOVERED",
                        timestamp = System.currentTimeMillis(),
                        laterStatus = "PRAYED_LATER",
                        laterTimestamp = System.currentTimeMillis()
                    )
                    updatedHistory.add(0, entry)
                }

                TodayStatus.UNTRACKED
            }
        }

        updatedToday[prayer] = nextStatus

        val newState = currentState.copy(
            activeStreak = currentStreak,
            backlog = updatedBacklog,
            todayStatus = updatedToday,
            totalLoggedCount = totalLogged,
            history = updatedHistory
        )
        _uiState.value = newState
        
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    // Manual trigger to evaluate today status and prompt sequential queue
    fun triggerSimulatedQueue() {
        val eligible = PrayerType.values().filter { PrayerAlarmScheduler.hasPrayerStartedToday(appContext, it) }
        val prayersToPrompt = if (eligible.isNotEmpty()) eligible else listOf(PrayerType.FAJR)
        val resetToday = _uiState.value.todayStatus.toMutableMap()
        prayersToPrompt.forEach { resetToday[it] = TodayStatus.UNTRACKED }
        
        val newState = _uiState.value.copy(
            todayStatus = resetToday,
            missedPrayers = prayersToPrompt,
            currentPrayerIndex = 0
        )
        _uiState.value = newState
        
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearTodayStatusOnly()
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    fun reloadState() {
        val loadedState = storage.loadState()
        _uiState.update { currentState ->
            currentState.copy(
                activeStreak = loadedState.activeStreak,
                backlog = loadedState.backlog,
                todayStatus = loadedState.todayStatus,
                totalLoggedCount = loadedState.totalLoggedCount,
                history = loadedState.history
            )
        }
    }

    fun resolveMissedHistory(entry: QadhaHistoryEntry) {
        val currentState = _uiState.value
        val updatedHistory = currentState.history.toMutableList()
        val index = updatedHistory.indexOfFirst { it.id == entry.id }
        val updatedBacklog = currentState.backlog.toMutableMap()
        
        if (index != -1) {
            val foundEntry = updatedHistory[index]
            // CRITICAL REQUIREMENT:
            // The History Book must preserve historical records.
            // When Fajr was initially missed, and later marked as prayed,
            // Fajr MUST still appear in the historical record, showing:
            // "Missed → Prayed Later".
            // It must NOT disappear from the History Book simply because its current status changed!
            val laterStatusName = if (foundEntry.actionType == "REMIND_LATER") "EVENTUALLY_PRAYED" else "PRAYED_LATER"
            updatedHistory[index] = foundEntry.copy(
                laterStatus = laterStatusName,
                laterTimestamp = System.currentTimeMillis()
            )
            
            val prayerEnum = PrayerType.values().find { it.id.equals(foundEntry.prayerId, ignoreCase = true) }
            if (prayerEnum != null) {
                val currentCount = updatedBacklog[prayerEnum] ?: 0
                updatedBacklog[prayerEnum] = (currentCount - 1).coerceAtLeast(0)
            }
        }
        
        val newState = currentState.copy(
            history = updatedHistory,
            backlog = updatedBacklog
        )
        _uiState.value = newState
        
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, pass: String) {
        val result = QadhaAuthManager.signUpWithEmail(appContext, name, email, pass)
        if (result.isSuccess) {
            _authErrorMessage.value = null
            loadAuthenticatedUser(result.getOrNull()!!)
        } else {
            _authErrorMessage.value = result.exceptionOrNull()?.message ?: "Sign up failed"
        }
    }

    fun signUpWithEmail(email: String, pass: String) {
        signUpWithEmail("", email, pass)
    }

    fun signInWithEmail(email: String, pass: String) {
        val result = QadhaAuthManager.signInWithEmail(appContext, email, pass)
        if (result.isSuccess) {
            _authErrorMessage.value = null
            loadAuthenticatedUser(result.getOrNull()!!)
        } else {
            _authErrorMessage.value = result.exceptionOrNull()?.message ?: "Sign in failed"
        }
    }

    fun resetPassword(email: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        val result = QadhaAuthManager.resetPassword(appContext, email, newPass)
        if (result.isSuccess) {
            onResult(true, "Password updated successfully. You can now sign in.")
        } else {
            onResult(false, result.exceptionOrNull()?.message ?: "Failed to reset password")
        }
    }

    fun signInWithGoogle(email: String, googleId: String? = null, displayName: String? = null) {
        val user = QadhaAuthManager.signInWithGoogle(appContext, email, googleId, displayName)
        _authErrorMessage.value = null
        loadAuthenticatedUser(user)
    }

    fun continueAsGuest() {
        val guest = QadhaAuthManager.continueAsGuest(appContext)
        _authErrorMessage.value = null
        loadAuthenticatedUser(guest)
    }

    private fun loadAuthenticatedUser(user: QadhaUser) {
        viewModelScope.launch(Dispatchers.IO) {
            val userStorage = QadhaStorageHelper(appContext, user.uid)
            val loadedState = userStorage.loadState()
            val eligiblePrayers = getEligiblePromptPrayers(loadedState.todayStatus)
            val state = loadedState.copy(
                isGoogleUser = user.isGoogle,
                googleEmail = user.email,
                currentUserId = user.uid,
                authChoiceMade = true,
                missedPrayers = eligiblePrayers,
                currentPrayerIndex = 0
            )
            _uiState.value = state

            if (user.isGoogle) {
                cloudHelper.setGoogleUser(user.uid, user.email ?: "", true)
                cloudHelper.restoreFromCloud(user.uid) { restoredState ->
                    if (restoredState != null && restoredState.totalLoggedCount > 0) {
                        _uiState.update { it.copy(cloudRestoredPayload = restoredState) }
                    } else {
                        cloudHelper.backupToCloud(state)
                    }
                }
            }
        }
    }

    fun signOut() {
        QadhaAuthManager.signOut(appContext)
        cloudHelper.logout()
        _uiState.value = TrackerState(
            activeStreak = 0,
            backlog = PrayerType.values().associateWith { 0 },
            todayStatus = PrayerType.values().associateWith { TodayStatus.UNTRACKED },
            missedPrayers = emptyList(),
            currentPrayerIndex = 0,
            totalLoggedCount = 0,
            history = emptyList(),
            isGoogleUser = false,
            googleEmail = null,
            currentUserId = null,
            authChoiceMade = false,
            cloudRestoredPayload = null,
            showEndOfDayReview = false
        )
    }

    fun setEndOfDayReviewVisible(visible: Boolean) {
        _uiState.update { it.copy(showEndOfDayReview = visible) }
    }

    fun reconcileEndOfDayPrayer(prayer: PrayerType, choice: EndOfDayChoice, dateMillis: Long = System.currentTimeMillis()) {
        if (choice == EndOfDayChoice.SKIP) return

        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
        val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)

        val currentState = _uiState.value
        val updatedToday = currentState.todayStatus.toMutableMap()
        val updatedBacklog = currentState.backlog.toMutableMap()
        val updatedHistory = currentState.history.toMutableList()
        var currentStreak = currentState.activeStreak
        var totalLogged = currentState.totalLoggedCount

        val priorStatus = updatedToday[prayer] ?: TodayStatus.UNTRACKED

        when (choice) {
            EndOfDayChoice.MISSED -> {
                if (priorStatus == TodayStatus.MISSED) {
                    // Already counted as missed earlier today -> do not double-count!
                } else if (priorStatus == TodayStatus.COMPLETED) {
                    // Corrected from Prayed to Missed
                    val currentVal = updatedBacklog[prayer] ?: 0
                    updatedBacklog[prayer] = currentVal + 1
                    updatedToday[prayer] = TodayStatus.MISSED
                    currentStreak = 0

                    val entry = QadhaHistoryEntry(
                        id = "${prayer.id}_${dateMillis}_${(0..1000).random()}",
                        prayerId = prayer.id,
                        dateString = dateStr,
                        dayOfWeek = dayStr,
                        actionType = "MISSED",
                        timestamp = dateMillis,
                        note = "End-of-Day Review: Missed"
                    )
                    updatedHistory.add(0, entry)
                } else {
                    // Was UNTRACKED: add exactly 1 missed entry
                    val currentVal = updatedBacklog[prayer] ?: 0
                    updatedBacklog[prayer] = currentVal + 1
                    updatedToday[prayer] = TodayStatus.MISSED
                    currentStreak = 0

                    val entry = QadhaHistoryEntry(
                        id = "${prayer.id}_${dateMillis}_${(0..1000).random()}",
                        prayerId = prayer.id,
                        dateString = dateStr,
                        dayOfWeek = dayStr,
                        actionType = "MISSED",
                        timestamp = dateMillis,
                        note = "End-of-Day Review: Missed"
                    )
                    updatedHistory.add(0, entry)
                }
            }
            EndOfDayChoice.PRAYED -> {
                if (priorStatus == TodayStatus.COMPLETED) {
                    // Already marked prayed earlier today -> no duplicate addition!
                } else if (priorStatus == TodayStatus.MISSED) {
                    // Was marked Missed earlier, now confirmed Prayed:
                    val currentVal = updatedBacklog[prayer] ?: 0
                    updatedBacklog[prayer] = (currentVal - 1).coerceAtLeast(0)
                    updatedToday[prayer] = TodayStatus.COMPLETED

                    val unresolvedIdx = updatedHistory.indexOfFirst {
                        it.prayerId.equals(prayer.id, ignoreCase = true) &&
                        isEntryMatchingDate(it, dateStr, dateMillis) &&
                        it.actionType == "MISSED" &&
                        it.laterStatus == null
                    }
                    if (unresolvedIdx != -1) {
                        val old = updatedHistory[unresolvedIdx]
                        updatedHistory[unresolvedIdx] = old.copy(
                            laterStatus = "PRAYED_LATER",
                            laterTimestamp = dateMillis
                        )
                    } else {
                        val entry = QadhaHistoryEntry(
                            id = "${prayer.id}_${dateMillis}_${(0..1000).random()}",
                            prayerId = prayer.id,
                            dateString = dateStr,
                            dayOfWeek = dayStr,
                            actionType = "PRAYED",
                            timestamp = dateMillis,
                            laterStatus = "PRAYED_LATER",
                            laterTimestamp = dateMillis,
                            note = "End-of-Day Review: Prayed Later"
                        )
                        updatedHistory.add(0, entry)
                    }
                } else {
                    // Was UNTRACKED
                    updatedToday[prayer] = TodayStatus.COMPLETED
                    currentStreak++
                    totalLogged++

                    val entry = QadhaHistoryEntry(
                        id = "${prayer.id}_${dateMillis}_${(0..1000).random()}",
                        prayerId = prayer.id,
                        dateString = dateStr,
                        dayOfWeek = dayStr,
                        actionType = "PRAYED",
                        timestamp = dateMillis,
                        note = "End-of-Day Review: Prayed"
                    )
                    updatedHistory.add(0, entry)
                }
            }
            EndOfDayChoice.SKIP -> {}
        }

        val newState = currentState.copy(
            activeStreak = currentStreak,
            backlog = updatedBacklog,
            todayStatus = updatedToday,
            totalLoggedCount = totalLogged,
            history = updatedHistory
        )
        _uiState.value = newState

        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(newState)
            if (cloudHelper.isGoogleSignedIn()) {
                cloudHelper.backupToCloud(newState)
            }
        }
    }

    fun restoreCloudData() {
        val payload = _uiState.value.cloudRestoredPayload ?: return
        val updatedState = _uiState.value.copy(
            activeStreak = payload.activeStreak,
            backlog = payload.backlog,
            todayStatus = payload.todayStatus,
            totalLoggedCount = payload.totalLoggedCount,
            history = payload.history,
            cloudRestoredPayload = null,
            isSyncing = false
        )
        _uiState.value = updatedState
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(updatedState)
        }
    }

    fun overwriteCloudData() {
        val cleanState = _uiState.value.copy(cloudRestoredPayload = null, isSyncing = true)
        _uiState.value = cleanState
        viewModelScope.launch(Dispatchers.IO) {
            cloudHelper.backupToCloud(cleanState) {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun triggerCloudBackup() {
        if (!cloudHelper.isGoogleSignedIn()) return
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            cloudHelper.backupToCloud(_uiState.value) { success ->
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun signOutGoogle() = signOut()
}

// ==========================================
// 4. MAIN CONTAINER ACTIVITY
// ==========================================

fun getDynamicHijriString(context: Context? = null): String {
    return HijriCalendarHelper.getFormattedHijriString(context)
}

class MainActivity : ComponentActivity() {
    private var pendingPrayerPrompt by mutableStateOf<String?>(null)
    private var pendingEndOfDayReview by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        pendingPrayerPrompt = intent?.getStringExtra("extra_prompt_prayer")
        pendingEndOfDayReview = intent?.getBooleanExtra(PrayerAlarmScheduler.EXTRA_OPEN_END_OF_DAY_REVIEW, false) ?: false
        PrayerAlarmScheduler.scheduleAllAlarms(this)

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    pendingPrayerPrompt = pendingPrayerPrompt,
                    pendingEndOfDayReview = pendingEndOfDayReview
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val prayerId = intent.getStringExtra("extra_prompt_prayer")
        if (prayerId != null) {
            pendingPrayerPrompt = prayerId
        }
        if (intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_OPEN_END_OF_DAY_REVIEW, false)) {
            pendingEndOfDayReview = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    pendingPrayerPrompt: String? = null,
    pendingEndOfDayReview: Boolean = false
) {
    val context = LocalContext.current
    // Scaffold architecture with state representation
    val viewModel: QadhaTrackerViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return QadhaTrackerViewModel(context.applicationContext) as T
            }
        }
    )

    // Request notification permission on Android 13+ (Tiramisu)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }
        LaunchedEffect(Unit) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Handle deep-link / notification intent for specific prayer prompt
    LaunchedEffect(pendingPrayerPrompt) {
        if (pendingPrayerPrompt != null) {
            val prayer = PrayerType.fromId(pendingPrayerPrompt)
            viewModel.promptSpecificPrayer(prayer)
        }
    }

    // Handle deep-link / notification intent for end of day review
    LaunchedEffect(pendingEndOfDayReview) {
        if (pendingEndOfDayReview) {
            viewModel.setEndOfDayReviewVisible(true)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authError by viewModel.authErrorMessage.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Synchronize UI instantly context when background overlays update the ledger
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                viewModel.reloadState()
            }
        }
        val filter = android.content.IntentFilter("com.example.ACTION_STATE_UPDATED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    if (!uiState.authChoiceMade) {
        QadhaAuthScreen(
            onEmailSignIn = { email, pass -> viewModel.signInWithEmail(email, pass) },
            onEmailSignUp = { name, email, pass -> viewModel.signUpWithEmail(name, email, pass) },
            onResetPassword = { email, newPass, callback -> viewModel.resetPassword(email, newPass, callback) },
            onGoogleSignIn = { email, id, name -> viewModel.signInWithGoogle(email, id, name) },
            onContinueAsGuest = { viewModel.continueAsGuest() },
            errorMessage = authError,
            onClearError = { viewModel.clearAuthError() }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Qadha Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Text(
                                text = "${getDynamicHijriString(context)} • AHLAN WA SAHLAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon),
                            contentDescription = "Qadha logo",
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    actions = {
                        IconButton(
                            onClick = { viewModel.triggerSimulatedQueue() },
                            modifier = Modifier.testTag("app_bar_trigger_queue")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Simulate missed prayers queue",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Profile & Cloud Synchronizer Dialog Trigger
                        var showProfileDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier.testTag("app_bar_profile_sync")
                        ) {
                            Icon(
                                imageVector = if (uiState.isGoogleUser) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = "Sync Options & Profile",
                                tint = if (uiState.isGoogleUser) Color(0xFF27AE60) else MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (showProfileDialog) {
                            ProfileSyncControlDialog(
                                uiState = uiState,
                                onDismiss = { showProfileDialog = false },
                                onSyncNow = { viewModel.triggerCloudBackup() },
                                onSignOut = {
                                    showProfileDialog = false
                                    viewModel.signOutGoogle()
                                },
                                onSignInGoogle = {
                                    showProfileDialog = false
                                    viewModel.signOutGoogle()
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.Dashboard else Icons.Outlined.Dashboard,
                                contentDescription = "Dashboard"
                            )
                        },
                        label = { Text("Dashboard", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("nav_tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Default.Book else Icons.Outlined.Book,
                                contentDescription = "Qadha Book Ledger"
                            )
                        },
                        label = { Text("Qadha Book", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("nav_tab_qadha_book")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Default.EmojiEvents else Icons.Outlined.EmojiEvents,
                                contentDescription = "Achievements Grid"
                            )
                        },
                        label = { Text("Achievements", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("nav_tab_achievements")
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Standard tab router page layouts
                when (selectedTab) {
                    0 -> DashboardScreen(
                        uiState = uiState, 
                        onCompletedToggle = { viewModel.toggleTodayStatus(it) }, 
                        onTriggerQueue = { viewModel.triggerSimulatedQueue() },
                        onOpenEndOfDayReview = { viewModel.setEndOfDayReviewVisible(true) }
                    )
                    1 -> QadhaBookScreen(
                        uiState = uiState, 
                        onModifyCount = { type, inc -> viewModel.modifyBacklogCount(type, inc) },
                        onResolveHistory = { viewModel.resolveMissedHistory(it) },
                        onLogQadhaForDate = { prayer, action, dateMs -> viewModel.logQadhaForSpecificDate(prayer, action, dateMs) }
                    )
                    2 -> AchievementsScreen(uiState = uiState)
                }

                // End-of-Day Salah Review Dialog
                if (uiState.showEndOfDayReview) {
                    EndOfDaySalahReviewDialog(
                        uiState = uiState,
                        onDismiss = { viewModel.setEndOfDayReviewVisible(false) },
                        onReconcile = { prayer, choice ->
                            viewModel.reconcileEndOfDayPrayer(prayer, choice)
                        }
                    )
                }

                // CRUCIAL FEATURE: The Sequential Pop-up Queue Dialog
                // Displays only one dialog at a time, sequentially advancing after Yes/No/Remind Me Later
                val currentPrayer = uiState.currentSequentialPrayer
                if (currentPrayer != null) {
                    SequentialQueueDialog(
                        prayerType = currentPrayer,
                        onResponse = { answeredYes ->
                            viewModel.handleSequentialResponse(currentPrayer, answeredYes)
                        },
                        onRemindLater = { reminderTimeMillis ->
                            viewModel.handleRemindMeLater(currentPrayer, reminderTimeMillis)
                        }
                    )
                }

                // Cloud Restore Found Dialog
                if (uiState.cloudRestoredPayload != null) {
                    AlertDialog(
                        onDismissRequest = { /* Force response */ },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Cloud Backup Available",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Cloud Backup Found!",
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            val payload = uiState.cloudRestoredPayload!!
                            Text(
                                text = "We found an existing cloud backup for ${uiState.googleEmail}.\n\n" +
                                        "• Active Streak: ${payload.activeStreak} days\n" +
                                        "• Total Logged: ${payload.totalLoggedCount}\n" +
                                        "• Historical Logs: ${payload.history.size}\n\n" +
                                        "Would you like to RESTORE this online backup, or OVERWRITE it using this phone's current local records?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.restoreCloudData() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("dialog_restore_confirm")
                            ) {
                                Text("Restore from Cloud")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.overwriteCloudData() },
                                modifier = Modifier.testTag("dialog_restore_overwrite")
                            ) {
                                Text("Overwrite Cloud", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QadhaAuthChoiceScreen(
    onGoogleSignIn: (String, String) -> Unit,
    onContinueAsGuest: () -> Unit
) {
    var showAccountChooser by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant Islamic decorative element/badge
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0x22FFFFFF), shape = CircleShape)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Qadha logo",
                    tint = Color(0xFFD4AF37), // Metallic Gold
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Qadha Tracker",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Ultimate Spiritual Ledger & Backlog Companion",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Premium styled card containing buttons
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Secure Your Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Choose Google Sign-In to back up your records forever. If you reinstall the app, resurrect your Qadha book instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Real "Sign in with Google" Button
                    Button(
                        onClick = { showAccountChooser = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_login_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF2C3E50),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Not Now / Continue as Guest"
                    OutlinedButton(
                        onClick = { onContinueAsGuest() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("guest_login_button")
                    ) {
                        Text(
                            text = "Not Now / Continue as Guest",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Offered data stored privately & safely.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }

    // Google Account Picker Dialog representing the real OAuth choices
    if (showAccountChooser) {
        Dialog(onDismissRequest = { showAccountChooser = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Choose an account to continue to Qadha Tracker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Account list
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            // User Email from additional metadata
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAccountChooser = false
                                        onGoogleSignIn("muhaiminulislamsajib@gmail.com", "user_google_sajib_123456")
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "M",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Muhaiminul Islam Sajib",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "muhaiminulislamsajib@gmail.com",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Generic Backup Account
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAccountChooser = false
                                        onGoogleSignIn("qadha.backup@gmail.com", "user_google_generic_987654")
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Q",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Qadha Backup Sync",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "qadha.backup@gmail.com",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showAccountChooser = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSyncControlDialog(
    uiState: TrackerState,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isGoogleUser) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    contentDescription = null,
                    tint = if (uiState.isGoogleUser) Color(0xFF27AE60) else Color(0xFFE74C3C),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (uiState.isGoogleUser) "Cloud Backup Active" else "Local Guest Mode",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.isGoogleUser) {
                    Text(
                        text = "You are currently backing up your ledger to:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.googleEmail?.firstOrNull()?.uppercase() ?: "G",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.googleEmail ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        text = "✓ Automatically backing up to safe cloud.\n" +
                                "✓ Reinstall protection: Active.\n" +
                                "✓ Cross-device sync: Enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (uiState.isSyncing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Synchronizing data online...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text(
                        text = "Your Qadha ledger data is stored only offline on this phone. If you uninstall the app or clear space, everything will be wiped out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "⚠️ No Reinstall Protection\n" +
                                "⚠️ Offline Backup: None",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC0392B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.isGoogleUser) {
                Button(
                    onClick = {
                        onSyncNow()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("btn_trigger_sync_manual")
                ) {
                    Text("Sync Now")
                }
            } else {
                Button(
                    onClick = {
                        onSignInGoogle()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("btn_connect_google_manual")
                ) {
                    Text("Connect Google")
                }
            }
        },
        dismissButton = {
            if (uiState.isGoogleUser) {
                TextButton(
                    onClick = {
                        onSignOut()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("btn_logout_manual")
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

// ==========================================
// 5. SCREEN 1: THE DASHBOARD SCREEN
// ==========================================

@Composable
fun DashboardScreen(
    uiState: TrackerState,
    onCompletedToggle: (PrayerType) -> Unit,
    onTriggerQueue: () -> Unit,
    onOpenEndOfDayReview: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Decorative Hero Banner (using generated image)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shadow(4.dp)
            ) {
                // Background image generated via Dall-E / Imagen inside image-generation skill
                Image(
                    painter = painterResource(id = R.drawable.img_islamic_header),
                    contentDescription = "Spiritual Islamic Banner Decor",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Minimal translucent overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC072E31))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Peace Be Upon You",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Keep Steadfast in Your Salah",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Streak Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1B4D3E), Color(0xFF0F352B))
                            )
                        )
                        .padding(20.dp)
                ) {
                    // Decorative Arabic Pattern Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 16.dp, y = 16.dp)
                    ) {
                        Text(
                            text = "ﷲ",
                            color = Color.White.copy(alpha = 0.08f),
                            fontSize = 110.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "ACTIVE STREAK",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFFC5A059),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                            
                            val activeMilestone = when {
                                uiState.activeStreak >= 30 -> "PLATINUM"
                                uiState.activeStreak >= 15 -> "GOLD"
                                uiState.activeStreak >= 7 -> "SILVER"
                                uiState.activeStreak >= 3 -> "BRONZE"
                                else -> "KEEPER"
                            }
                            
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFC5A059), shape = RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$activeMilestone BADGE",
                                    color = Color(0xFF1B4D3E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${uiState.activeStreak}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color.White,
                                    letterSpacing = (-1).sp
                                )
                            )
                            Text(
                                text = "Prayers",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Milestone indicators progress info helper
                        val badge = getNextMilestoneBadge(uiState.activeStreak)
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Today's Checkbox Checklist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Prayers Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap to toggle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        items(PrayerType.values()) { prayer ->
            val status = uiState.todayStatus[prayer] ?: TodayStatus.UNTRACKED
            PrayerStatusRow(
                prayer = prayer,
                status = status,
                onClick = { onCompletedToggle(prayer) }
            )
        }

        // End of Day Salah Review Trigger Card
        item {
            EndOfDayReviewCard(
                onOpenReview = onOpenEndOfDayReview
            )
        }

        // Beautiful Interactive Screen Wake Overlay Control Card
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            var overlayEnabled by remember {
                mutableStateOf(context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE).getBoolean("overlay_enabled", true))
            }
            var hasPermission by remember {
                mutableStateOf(android.provider.Settings.canDrawOverlays(context))
            }
            
            // Re-evaluate drawer permission status on lifecycle resume
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        hasPermission = android.provider.Settings.canDrawOverlays(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_setup_panel_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SCREEN WAKE OVERLAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFC5A059),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatic Queue Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { isChecked ->
                                overlayEnabled = isChecked
                                context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("overlay_enabled", isChecked)
                                    .apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1B4D3E),
                                checkedTrackColor = Color(0xFFE0ECE3),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Launches your daily tracked outstanding prayer checklists directly on your screen when you unlock or wake up your phone to keep you beautifully, continuously accountable.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF5C635E),
                            lineHeight = 15.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Permission indicator and action button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (hasPermission) Color(0xFFE0ECE3) else Color(0xFFFBEBEB),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (hasPermission) Color(0xFF1B4D3E) else Color(0xFFC0392B),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (hasPermission) "Overlay Service: Active" else "Overlay Service: Permission Required",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPermission) Color(0xFF1B4D3E) else Color(0xFFC0392B)
                            )
                        }
                        
                        if (!hasPermission) {
                            TextButton(
                                onClick = {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFF1B4D3E)
                                )
                            ) {
                                Text(
                                    text = "Enable in Settings",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B4D3E),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val storage = remember { QadhaStorageHelper(context) }
            
            var timezoneId by remember { mutableStateOf(storage.getTimeZoneId()) }
            var showTzDialog by remember { mutableStateOf(false) }
            
            var fajrTime by remember { mutableStateOf(storage.getPrayerCustomTime(PrayerType.FAJR)) }
            var dhuhrTime by remember { mutableStateOf(storage.getPrayerCustomTime(PrayerType.DHUHR)) }
            var asrTime by remember { mutableStateOf(storage.getPrayerCustomTime(PrayerType.ASR)) }
            var maghribTime by remember { mutableStateOf(storage.getPrayerCustomTime(PrayerType.MAGHRIB)) }
            var ishaTime by remember { mutableStateOf(storage.getPrayerCustomTime(PrayerType.ISHA)) }

            val popularTimezones = listOf(
                "Asia/Dhaka", "Asia/Karachi", "Asia/Riyadh", "Asia/Dubai",
                "Europe/London", "America/New_York", "America/Los_Angeles", "UTC"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prayer_alarm_settings_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "CLOCK TIMINGS & TIMEZONE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFC5A059),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set Custom Waqt Times",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Custom clocks trigger local precise alarms. The screen overlay triggers if you were away past these times.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF5C635E),
                            lineHeight = 15.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { showTzDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF1B4D3E),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Active Timezone",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5C635E)
                                )
                                Text(
                                    text = timezoneId,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B4D3E)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B4D3E), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Change",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Custom Alarm Clocks",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B4D3E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        Triple(PrayerType.FAJR, fajrTime) { t: String -> fajrTime = t },
                        Triple(PrayerType.DHUHR, dhuhrTime) { t: String -> dhuhrTime = t },
                        Triple(PrayerType.ASR, asrTime) { t: String -> asrTime = t },
                        Triple(PrayerType.MAGHRIB, maghribTime) { t: String -> maghribTime = t },
                        Triple(PrayerType.ISHA, ishaTime) { t: String -> ishaTime = t }
                    ).forEach { (prayer, timeStr, setTime) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = prayer.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Current: $timeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF1B4D3E),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val timeParts = timeStr.split(":")
                                    var h = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
                                    var m = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                    
                                    fun saveUpdatedTime(newH: Int, newM: Int) {
                                        val paddedH = newH.toString().padStart(2, '0')
                                        val paddedM = newM.toString().padStart(2, '0')
                                        val finalT = "$paddedH:$paddedM"
                                        storage.setPrayerCustomTime(prayer, finalT)
                                        setTime(finalT)
                                        PrayerAlarmScheduler.scheduleAllAlarms(context)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                h = if (h == 0) 23 else h - 1
                                                saveUpdatedTime(h, m)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                        }
                                        Text(
                                            text = "${h.toString().padStart(2, '0')}h",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                h = if (h == 23) 0 else h + 1
                                                saveUpdatedTime(h, m)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1B4D3E))
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                m = (m - 5 + 60) % 60
                                                saveUpdatedTime(h, m)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                        }
                                        Text(
                                            text = "${m.toString().padStart(2, '0')}m",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                m = (m + 5) % 60
                                                saveUpdatedTime(h, m)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1B4D3E))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showTzDialog) {
                AlertDialog(
                    onDismissRequest = { showTzDialog = false },
                    title = { Text("Select Timezone") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(popularTimezones) { tzId ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            storage.setTimeZoneId(tzId)
                                            timezoneId = tzId
                                            showTzDialog = false
                                            PrayerAlarmScheduler.scheduleAllAlarms(context)
                                        }
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (tzId == timezoneId) Color(0xFFE0ECE3) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = tzId,
                                        modifier = Modifier.padding(16.dp),
                                        fontWeight = if (tzId == timezoneId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (tzId == timezoneId) Color(0xFF1B4D3E) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTzDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // Manual simulator testing trigger button helper
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onTriggerQueue() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("trigger_simulated_queue_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulate Missed Prayers Queue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Creative signature/credit
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ideation_credit_dashboard"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ideation by Muhaiminul Islam Sajib",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PrayerStatusRow(
    prayer: PrayerType,
    status: TodayStatus,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when (status) {
            TodayStatus.COMPLETED -> Color(0xFFE0ECE3)
            TodayStatus.MISSED -> Color(0xFFFBEBEB)
            TodayStatus.UNTRACKED -> Color(0xFFFFFFFF)
        }, label = "rowColor"
    )

    val contentColor = when (status) {
        TodayStatus.COMPLETED -> Color(0xFF1B4D3E)
        TodayStatus.MISSED -> Color(0xFFC0392B)
        TodayStatus.UNTRACKED -> Color(0xFF191C1B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("prayer_row_${prayer.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when (status) {
                TodayStatus.COMPLETED -> Color(0xFF1B4D3E).copy(alpha = 0.1f)
                TodayStatus.MISSED -> Color(0xFFF9D6D6)
                TodayStatus.UNTRACKED -> Color(0xFFD1D9D4)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = prayer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = prayer.subtitleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        TodayStatus.UNTRACKED -> Color(0xFF5C635E)
                        else -> contentColor.copy(alpha = 0.7f)
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = when (status) {
                                TodayStatus.COMPLETED -> Color(0xFF1B4D3E)
                                TodayStatus.MISSED -> Color(0xFFC0392B)
                                TodayStatus.UNTRACKED -> Color(0xFFF0F3F4)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (status) {
                            TodayStatus.COMPLETED -> Icons.Default.Check
                            TodayStatus.MISSED -> Icons.Default.Remove
                            TodayStatus.UNTRACKED -> Icons.Default.Forward
                        },
                        contentDescription = "Toggle status symbol",
                        tint = if (status == TodayStatus.UNTRACKED) Color(0xFF7F8C8D) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when (status) {
                        TodayStatus.COMPLETED -> "Prayed"
                        TodayStatus.MISSED -> "Backlog (+1)"
                        TodayStatus.UNTRACKED -> "Pending"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.width(100.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

fun getNextMilestoneBadge(streak: Int): String {
    return when {
        streak < 3 -> "Next Achievement: Bronze Badge at 3 streak (Needs ${3 - streak} more)"
        streak < 7 -> "Bronze Unlocked! Silver Badge at 7 streak (Needs ${7 - streak} more)"
        streak < 15 -> "Silver Unlocked! Gold Badge at 15 streak (Needs ${15 - streak} more)"
        streak < 30 -> "Gold Unlocked! Platinum Badge at 30 streak (Needs ${30 - streak} more)"
        else -> "Sacred Platinum Achievement Unlocked! Absolute Master!"
    }
}

// ==========================================
// 6. SCREEN 2: QADHA BOOK BACKLOG LEDGER
// ==========================================

// ==========================================
// 6. SCREEN 2: QADHA BOOK BACKLOG LEDGER
// ==========================================

// Helper for date matching between Qadha history records and selected date
fun isEntryMatchingDate(entry: QadhaHistoryEntry, dateStr: String, dateMillis: Long): Boolean {
    if (entry.dateString.isNotBlank() && entry.dateString.trim().equals(dateStr.trim(), ignoreCase = true)) return true
    if (!entry.note.isNullOrBlank() && entry.note.contains(dateStr.trim(), ignoreCase = true)) return true
    if (entry.timestamp > 0 && dateMillis > 0) {
        val c1 = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
        val c2 = Calendar.getInstance().apply { timeInMillis = dateMillis }
        if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)) {
            return true
        }
    }
    return false
}

@Composable
fun PrayerCalendarView(
    uiState: TrackerState,
    onResolveHistory: (QadhaHistoryEntry) -> Unit,
    onLogQadhaForDate: (PrayerType, String, Long) -> Unit
) {
    val context = LocalContext.current
    var calendarMonth by remember { mutableStateOf(Calendar.getInstance()) }
    val todayFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    val todayCal = remember { Calendar.getInstance() }
    val todayStr = remember { todayFormatter.format(todayCal.time) }
    
    var selectedDateCal by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var showDateHistoryModal by remember { mutableStateOf(false) }
    var modalDateCal by remember { mutableStateOf(Calendar.getInstance()) }
    var modalDateStr by remember { mutableStateOf(todayStr) }

    // First day of current displayed month
    val firstDayCal = remember(calendarMonth) {
        Calendar.getInstance().apply {
            timeInMillis = calendarMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    val currentMonthYearString = remember(calendarMonth) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdf.format(calendarMonth.time)
    }

    val maxDays = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) // 1 (Sunday) to 7 (Saturday)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prayer_history_calendar_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Month switcher header with Date Picker Dialog jump
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = calendarMonth.timeInMillis
                            add(Calendar.MONTH, -1)
                        }
                        calendarMonth = newCal
                    },
                    modifier = Modifier.testTag("calendar_prev_month_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Month"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        val dpd = DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val newSelected = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                selectedDateCal = newSelected
                                selectedDateStr = todayFormatter.format(newSelected.time)
                                calendarMonth = Calendar.getInstance().apply { timeInMillis = newSelected.timeInMillis }
                            },
                            selectedDateCal.get(Calendar.YEAR),
                            selectedDateCal.get(Calendar.MONTH),
                            selectedDateCal.get(Calendar.DAY_OF_MONTH)
                        )
                        dpd.show()
                    }
                ) {
                    Text(
                        text = currentMonthYearString.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("calendar_month_year_title")
                    )
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Pick Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = calendarMonth.timeInMillis
                            add(Calendar.MONTH, 1)
                        }
                        calendarMonth = newCal
                    },
                    modifier = Modifier.testTag("calendar_next_month_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Week days row
            val weekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid calculation & rendering
            val totalCells = (firstDayOfWeek - 1) + maxDays
            val rows = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (c in 0..6) {
                            val cellIndex = r * 7 + c
                            val dayNumber = cellIndex - (firstDayOfWeek - 1) + 1

                            if (cellIndex < (firstDayOfWeek - 1) || dayNumber > maxDays) {
                                Box(modifier = Modifier.weight(1f))
                            } else {
                                val cellCal = Calendar.getInstance().apply {
                                    timeInMillis = firstDayCal.timeInMillis
                                    set(Calendar.DAY_OF_MONTH, dayNumber)
                                }
                                val cellDateStr = todayFormatter.format(cellCal.time)
                                val isSelected = (cellDateStr == selectedDateStr)
                                val isCellToday = (cellDateStr == todayStr)

                                val dayEntries = uiState.history.filter {
                                    isEntryMatchingDate(it, cellDateStr, cellCal.timeInMillis)
                                }
                                
                                val hasMissed = dayEntries.any { 
                                    it.actionType == "MISSED" || it.actionType == "MANUAL_ADD" 
                                }
                                
                                val todayHasMissed = if (isCellToday) {
                                    uiState.todayStatus.values.any { it == TodayStatus.MISSED }
                                } else false
                                
                                val isRed = hasMissed || todayHasMissed
                                val isGreen = if (isCellToday) {
                                    uiState.todayStatus.values.all { it == TodayStatus.COMPLETED }
                                } else {
                                    dayEntries.isNotEmpty() && !isRed
                                }

                                val cellBgColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    isRed -> Color(0xFFFCE4E4)
                                    isGreen -> Color(0xFFE8F8F5)
                                    else -> Color.Transparent
                                }

                                val cellTextColor = when {
                                    isRed -> Color(0xFFC0392B)
                                    isGreen -> Color(0xFF27AE60)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                val borderStroke = if (isSelected) {
                                    BorderStroke(2.dp, Color(0xFFC5A059))
                                } else if (isCellToday) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                } else {
                                    null
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clickable {
                                            selectedDateCal = cellCal
                                            selectedDateStr = cellDateStr
                                            modalDateCal = cellCal
                                            modalDateStr = cellDateStr
                                            showDateHistoryModal = true
                                        }
                                        .testTag("calendar_day_cell_$dayNumber"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = cellBgColor),
                                    border = borderStroke
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = if (isCellToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = cellTextColor
                                                )
                                            )
                                            if (isRed) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(Color(0xFFC0392B), CircleShape)
                                                )
                                            } else if (isGreen) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(Color(0xFF27AE60), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // ==============================================================
            // SPECIFIC DATE QADHA RECORD DETAILS (User Requested Requirement)
            // ==============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DATE QADHA RECORDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC5A059),
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = if (selectedDateStr == todayStr) "Today, $selectedDateStr" else fullDateFormatter.format(selectedDateCal.time),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("selected_day_title")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            modalDateCal = selectedDateCal
                            modalDateStr = selectedDateStr
                            showDateHistoryModal = true
                        },
                        modifier = Modifier.testTag("open_history_modal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Date Details Modal",
                            tint = Color(0xFFC5A059),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (selectedDateStr != todayStr) {
                        FilledTonalButton(
                            onClick = {
                                selectedDateCal = Calendar.getInstance()
                                selectedDateStr = todayStr
                                calendarMonth = Calendar.getInstance()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Jump to Today", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. FILTERED LIST OF PERSISTENT QADHA ACTIVITY ENTRIES ON THIS SPECIFIC DATE
            val dateHistoryEntries = remember(uiState.history, selectedDateStr) {
                uiState.history.filter {
                    isEntryMatchingDate(it, selectedDateStr, selectedDateCal.timeInMillis)
                }
            }

            val missedCountOnDate = dateHistoryEntries.count { it.actionType == "MISSED" || it.actionType == "MANUAL_ADD" }
            val recoveredCountOnDate = dateHistoryEntries.count { it.actionType == "RECOVERED" }

            // Summary Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (missedCountOnDate > 0) Color(0xFFFCE4E4) else Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Missed Logged", fontSize = 10.sp, color = Color(0xFF7F8C8D))
                        Text("+$missedCountOnDate Qadha", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (missedCountOnDate > 0) Color(0xFFC0392B) else Color(0xFF2C3E50))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (recoveredCountOnDate > 0) Color(0xFFE8F8F5) else Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Prayed / Recovered", fontSize = 10.sp, color = Color(0xFF7F8C8D))
                        Text("-$recoveredCountOnDate Qadha", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (recoveredCountOnDate > 0) Color(0xFF27AE60) else Color(0xFF2C3E50))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recorded Qadha Activity (${dateHistoryEntries.size} entries)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (dateHistoryEntries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dateHistoryEntries.forEach { entry ->
                        val prayer = PrayerType.fromId(entry.prayerId)
                        val isMissed = (entry.actionType == "MISSED" || entry.actionType == "MANUAL_ADD")
                        val isReminded = (entry.actionType == "REMIND_LATER")
                        val isPrayedLater = (entry.laterStatus == "PRAYED_LATER" || entry.laterStatus == "EVENTUALLY_PRAYED" || entry.actionType == "RECOVERED")

                        val badgeColor = when {
                            isPrayedLater || entry.actionType == "PRAYED" -> Color(0xFF27AE60)
                            isReminded -> Color(0xFFE67E22)
                            isMissed -> Color(0xFFC0392B)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val badgeBg = when {
                            isPrayedLater || entry.actionType == "PRAYED" -> Color(0xFFE8F8F5)
                            isReminded -> Color(0xFFFDF2E9)
                            isMissed -> Color(0xFFFCE4E4)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        }
                        val actionLabel = when {
                            entry.actionType == "MISSED" && isPrayedLater -> "Missed → Prayed Later"
                            entry.actionType == "MANUAL_ADD" && isPrayedLater -> "Added to Qadha → Prayed Later"
                            entry.actionType == "REMIND_LATER" && entry.laterStatus == "EVENTUALLY_PRAYED" -> "Reminded → Eventually Prayed"
                            entry.actionType == "REMIND_LATER" && entry.laterStatus == "MISSED" -> "Reminded → Missed"
                            entry.actionType == "REMIND_LATER" -> "Reminded for Later"
                            entry.actionType == "MISSED" -> "Missed Waqt (+1 Qadha)"
                            entry.actionType == "MANUAL_ADD" -> "Manual Qadha Added (+1)"
                            entry.actionType == "RECOVERED" -> "Marked as Prayed Later"
                            entry.actionType == "PRAYED" -> "Prayed"
                            else -> entry.actionType
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_qadha_record_${entry.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = prayer.displayName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "(${prayer.subtitleName})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(badgeBg, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = actionLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = badgeColor
                                            )
                                        }
                                        if (entry.timestamp > 0) {
                                            Text(
                                                text = timeFormatter.format(Date(entry.timestamp)),
                                                fontSize = 11.sp,
                                                color = Color(0xFF7F8C8D)
                                            )
                                        }
                                        if (entry.laterTimestamp != null && entry.laterTimestamp > 0) {
                                            Text(
                                                text = "Prayed: ${timeFormatter.format(Date(entry.laterTimestamp))}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF27AE60)
                                            )
                                        }
                                    }
                                }

                                if (isMissed && !isPrayedLater) {
                                    IconButton(
                                        onClick = { onResolveHistory(entry) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF2ECC71).copy(alpha = 0.15f), CircleShape)
                                            .testTag("resolve_record_${entry.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Mark as Prayed Later",
                                            tint = Color(0xFF27AE60),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No prayer activity recorded for this date",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "You can record missed prayers or mark prayers completed below.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF95A5A6),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. DAILY 5 WAQTS BREAKDOWN & QUICK RECORDING FOR THIS DATE
            Text(
                text = "5 Daily Waqts for $selectedDateStr",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrayerType.values().forEach { prayer ->
                    val isClickedToday = (selectedDateStr == todayStr)
                    
                    val prayerStatus = if (isClickedToday) {
                        when (uiState.todayStatus[prayer]) {
                            TodayStatus.COMPLETED -> "Performed"
                            TodayStatus.MISSED -> "Missed"
                            else -> "Untracked"
                        }
                    } else {
                        val entry = dateHistoryEntries.find { 
                            it.prayerId.equals(prayer.id, ignoreCase = true)
                        }
                        when (entry?.actionType) {
                            "RECOVERED" -> "Performed"
                            "MISSED", "MANUAL_ADD" -> "Missed"
                            else -> "Untracked"
                        }
                    }

                    val rowBgColor = when (prayerStatus) {
                        "Performed" -> Color(0xFFE8F8F5).copy(alpha = 0.5f)
                        "Missed" -> Color(0xFFFCE4E4).copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val statusColor = when (prayerStatus) {
                        "Performed" -> Color(0xFF27AE60)
                        "Missed" -> Color(0xFFC0392B)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calendar_breakdown_row_${prayer.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = rowBgColor),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = prayer.displayName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = prayer.subtitleName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = prayerStatus.uppercase(),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = statusColor
                                    )
                                }

                                // Quick Log Buttons for this specific date
                                OutlinedButton(
                                    onClick = { onLogQadhaForDate(prayer, "MISSED", selectedDateCal.timeInMillis) },
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("log_missed_${prayer.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    border = BorderStroke(1.dp, Color(0xFFC0392B).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+ Missed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                }

                                Button(
                                    onClick = { onLogQadhaForDate(prayer, "RECOVERED", selectedDateCal.timeInMillis) },
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("log_prayed_${prayer.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("✓ Prayed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup showing complete prayer/waqt history for selected date
    if (showDateHistoryModal) {
        DatePrayerHistoryModal(
            dateCal = modalDateCal,
            dateStr = modalDateStr,
            todayStr = todayStr,
            historyEntries = uiState.history,
            todayStatus = uiState.todayStatus,
            onDismiss = { showDateHistoryModal = false },
            onResolveHistory = onResolveHistory,
            onLogQadhaForDate = onLogQadhaForDate
        )
    }
}

@Composable
fun DatePrayerHistoryModal(
    dateCal: Calendar,
    dateStr: String,
    todayStr: String,
    historyEntries: List<QadhaHistoryEntry>,
    todayStatus: Map<PrayerType, TodayStatus>,
    onDismiss: () -> Unit,
    onResolveHistory: (QadhaHistoryEntry) -> Unit,
    onLogQadhaForDate: (PrayerType, String, Long) -> Unit
) {
    val fullDateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Filter historical records specifically matching this date
    val dayRecords = remember(historyEntries, dateStr, dateCal) {
        historyEntries.filter {
            isEntryMatchingDate(it, dateStr, dateCal.timeInMillis)
        }
    }

    val missedCount = dayRecords.count { (it.actionType == "MISSED" || it.actionType == "MANUAL_ADD") && it.laterStatus == null }
    val prayedLaterCount = dayRecords.count { it.laterStatus == "PRAYED_LATER" || it.laterStatus == "EVENTUALLY_PRAYED" || it.actionType == "RECOVERED" }
    val remindedCount = dayRecords.count { it.actionType == "REMIND_LATER" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
                .testTag("date_prayer_history_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Date, Title, and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFC5A059).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFFC5A059),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "HISTORY BOOK ARCHIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC5A059),
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = if (dateStr == todayStr) "Today, $dateStr" else fullDateFormatter.format(dateCal.time),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("modal_date_title")
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .testTag("modal_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Modal",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metric Summary Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (missedCount > 0) Color(0xFFFCE4E4) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Missed", fontSize = 10.sp, color = Color(0xFF7F8C8D))
                            Text(
                                text = "$missedCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (missedCount > 0) Color(0xFFC0392B) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (prayedLaterCount > 0) Color(0xFFE8F8F5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Prayed Later", fontSize = 10.sp, color = Color(0xFF7F8C8D))
                            Text(
                                text = "$prayedLaterCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (prayedLaterCount > 0) Color(0xFF27AE60) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (remindedCount > 0) Color(0xFFFDF2E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Reminded", fontSize = 10.sp, color = Color(0xFF7F8C8D))
                            Text(
                                text = "$remindedCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (remindedCount > 0) Color(0xFFE67E22) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: Historical Log Records
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HISTORICAL PRAYER LOG",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "${dayRecords.size} records",
                                fontSize = 11.sp,
                                color = Color(0xFF7F8C8D)
                            )
                        }

                        if (dayRecords.isEmpty()) {
                            // User constraint: Clicking a date with no prayer activity should show a clear
                            // "No prayer activity recorded for this date" message.
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("modal_no_activity_message"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = Color(0xFF95A5A6),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No prayer activity recorded for this date",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No prayers were marked missed, reminded, or recovered on $dateStr.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF7F8C8D),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            dayRecords.forEach { record ->
                                val prayer = PrayerType.fromId(record.prayerId)
                                val isMissed = (record.actionType == "MISSED" || record.actionType == "MANUAL_ADD")
                                val isReminded = (record.actionType == "REMIND_LATER")
                                val isPrayedLater = (record.laterStatus == "PRAYED_LATER" || record.laterStatus == "EVENTUALLY_PRAYED" || record.actionType == "RECOVERED")

                                val displayStatus = when {
                                    record.actionType == "MISSED" && isPrayedLater -> "Missed → Prayed Later"
                                    record.actionType == "MANUAL_ADD" && isPrayedLater -> "Added to Qadha → Prayed Later"
                                    record.actionType == "REMIND_LATER" && record.laterStatus == "EVENTUALLY_PRAYED" -> "Reminded → Eventually Prayed"
                                    record.actionType == "REMIND_LATER" && record.laterStatus == "MISSED" -> "Reminded → Missed"
                                    record.actionType == "REMIND_LATER" -> "Reminded for Later"
                                    record.actionType == "MISSED" -> "Missed"
                                    record.actionType == "MANUAL_ADD" -> "Added to Qadha"
                                    record.actionType == "RECOVERED" -> "Marked as Prayed Later"
                                    record.actionType == "PRAYED" -> "Prayed"
                                    else -> record.actionType
                                }

                                val statusBadgeColor = when {
                                    isPrayedLater || record.actionType == "PRAYED" -> Color(0xFF27AE60)
                                    isReminded -> Color(0xFFE67E22)
                                    isMissed -> Color(0xFFC0392B)
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                val statusBadgeBg = when {
                                    isPrayedLater || record.actionType == "PRAYED" -> Color(0xFFE8F8F5)
                                    isReminded -> Color(0xFFFDF2E9)
                                    isMissed -> Color(0xFFFCE4E4)
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("modal_history_entry_${record.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = prayer.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "(${prayer.subtitleName})",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }

                                            // Status pill showing complete transition (e.g., Missed -> Prayed Later)
                                            Box(
                                                modifier = Modifier
                                                    .background(statusBadgeBg, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = displayStatus,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = statusBadgeColor
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Associated details: Timestamps, Notes
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                if (record.timestamp > 0) {
                                                    Text(
                                                        text = "Logged: ${timeFormatter.format(Date(record.timestamp))}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF7F8C8D)
                                                    )
                                                }
                                                if (record.laterTimestamp != null && record.laterTimestamp > 0) {
                                                    Text(
                                                        text = "Prayed: ${timeFormatter.format(Date(record.laterTimestamp))}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF27AE60)
                                                    )
                                                }
                                                if (!record.note.isNullOrBlank()) {
                                                    Text(
                                                        text = record.note,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFE67E22)
                                                    )
                                                }
                                            }

                                            // Action if missed and not yet resolved
                                            if (isMissed && !isPrayedLater) {
                                                FilledTonalButton(
                                                    onClick = { onResolveHistory(record) },
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .testTag("modal_resolve_button_${record.id}"),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = Color(0xFFE8F8F5),
                                                        contentColor = Color(0xFF27AE60)
                                                    ),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Mark Prayed Later", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // SECTION 2: 5 Daily Waqts Overview & Quick Log on Date
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "5 DAILY WAQTS FOR THIS DATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        )

                        PrayerType.values().forEach { prayer ->
                            val isClickedToday = (dateStr == todayStr)
                            val prayerStatus = if (isClickedToday) {
                                when (todayStatus[prayer]) {
                                    TodayStatus.COMPLETED -> "Performed"
                                    TodayStatus.MISSED -> "Missed"
                                    else -> "Untracked"
                                }
                            } else {
                                val entry = dayRecords.find { it.prayerId.equals(prayer.id, ignoreCase = true) }
                                when {
                                    entry?.laterStatus == "PRAYED_LATER" || entry?.laterStatus == "EVENTUALLY_PRAYED" || entry?.actionType == "RECOVERED" -> "Prayed Later"
                                    entry?.actionType == "MISSED" || entry?.actionType == "MANUAL_ADD" -> "Missed"
                                    entry?.actionType == "REMIND_LATER" -> "Reminded"
                                    entry?.actionType == "PRAYED" -> "Performed"
                                    else -> "Untracked"
                                }
                            }

                            val rowStatusColor = when (prayerStatus) {
                                "Performed", "Prayed Later" -> Color(0xFF27AE60)
                                "Missed" -> Color(0xFFC0392B)
                                "Reminded" -> Color(0xFFE67E22)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("modal_waqt_row_${prayer.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = prayer.displayName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = prayer.subtitleName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(rowStatusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = prayerStatus.uppercase(),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = rowStatusColor
                                            )
                                        }

                                        // Quick Log Buttons
                                        OutlinedButton(
                                            onClick = { onLogQadhaForDate(prayer, "MISSED", dateCal.timeInMillis) },
                                            modifier = Modifier
                                                .height(30.dp)
                                                .testTag("modal_log_missed_${prayer.id}"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            border = BorderStroke(1.dp, Color(0xFFC0392B).copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("+ Missed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                        }

                                        Button(
                                            onClick = { onLogQadhaForDate(prayer, "RECOVERED", dateCal.timeInMillis) },
                                            modifier = Modifier
                                                .height(30.dp)
                                                .testTag("modal_log_prayed_${prayer.id}"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("✓ Prayed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Done / Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("modal_dismiss_bottom_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close History", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QadhaBookScreen(
    uiState: TrackerState,
    onModifyCount: (PrayerType, Boolean) -> Unit,
    onResolveHistory: (QadhaHistoryEntry) -> Unit,
    onLogQadhaForDate: (PrayerType, String, Long) -> Unit
) {
    var selectedQadhaTab by remember { mutableStateOf(0) }
    var selectedPrayerForHistory by remember { mutableStateOf<PrayerType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Book Header Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LIFETIME RECOVERY BACKLOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val totalBacklog = uiState.backlog.values.sum()
                Text(
                    text = "$totalBacklog Prayers Due",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reduce your backlog counts as you offer your Qadha prayers. The ultimate spiritual ledger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD5F3EE),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // M3 Tab Row for switching between Ledger Fine-Tuner and History view
        TabRow(
            selectedTabIndex = selectedQadhaTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            indicator = { tabPositions ->
                if (selectedQadhaTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedQadhaTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedQadhaTab == 0,
                onClick = { selectedQadhaTab = 0 },
                text = {
                    Text(
                        text = "Ledger Counters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            Tab(
                selected = selectedQadhaTab == 1,
                onClick = { selectedQadhaTab = 1 },
                text = {
                    Text(
                        text = "History Book",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedQadhaTab == 0) {
            Text(
                text = "Fine-Tune Individual Ledgers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap on a prayer to view its specific missed/added dates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PrayerType.values()) { prayer ->
                    val count = uiState.backlog[prayer] ?: 0
                    QadhaBookLedgerRow(
                        prayer = prayer,
                        count = count,
                        onIncrease = { onModifyCount(prayer, true) },
                        onDecrease = { onModifyCount(prayer, false) },
                        onClick = { selectedPrayerForHistory = prayer }
                    )
                }

                // Creative signature/credit
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ideation_credit_qadha_book"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ideation by Muhaiminul Islam Sajib",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                letterSpacing = 0.5.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        } else {
            Text(
                text = "Missed Prayer History Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Track your performances and resolve Missed / Qadha prayers visually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PrayerCalendarView(
                        uiState = uiState,
                        onResolveHistory = onResolveHistory,
                        onLogQadhaForDate = onLogQadhaForDate
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Lifetime Backlog Details by Salat",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                items(PrayerType.values()) { prayer ->
                    val missedEntries = uiState.history.filter {
                        it.prayerId.equals(prayer.id, ignoreCase = true) && 
                        (it.actionType == "MISSED" || it.actionType == "MANUAL_ADD")
                    }
                    val count = missedEntries.size
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPrayerForHistory = prayer }
                            .testTag("history_salat_row_${prayer.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (count > 0) Color(0xFFF9E7E7) else Color(0xFFE8F8F5),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = if (count > 0) Color(0xFFC0392B) else Color(0xFF27AE60),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = prayer.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = prayer.subtitleName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE74C3C), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "$count Missed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2ECC71), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "Clean",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Forward,
                                    contentDescription = "View dates",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ideation_credit_qadha_history"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ideation by Muhaiminul Islam Sajib",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                letterSpacing = 0.5.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    val selectedPrayerHistoryList = selectedPrayerForHistory?.let { prayer ->
        uiState.history.filter {
            it.prayerId.equals(prayer.id, ignoreCase = true) && 
            (it.actionType == "MISSED" || it.actionType == "MANUAL_ADD")
        }
    } ?: emptyList()

    if (selectedPrayerForHistory != null) {
        QadhaMissedDatesDialog(
            prayer = selectedPrayerForHistory!!,
            entries = selectedPrayerHistoryList,
            onDismiss = { selectedPrayerForHistory = null },
            onResolve = { entry ->
                onResolveHistory(entry)
            }
        )
    }
}

@Composable
fun QadhaMissedDatesDialog(
    prayer: PrayerType,
    entries: List<QadhaHistoryEntry>,
    onDismiss: () -> Unit,
    onResolve: (QadhaHistoryEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Missed ${prayer.displayName} Dates",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Check historical dates when you registered of missing or manual increase of ${prayer.displayName}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Clean ledger",
                                tint = Color(0xFF27AE60),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Zero missed dates!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your ${prayer.displayName} backlog is completely clean.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries) { entry ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (entry.actionType == "MISSED") Icons.Default.EventBusy else Icons.Default.AddCircle,
                                            contentDescription = null,
                                            tint = if (entry.actionType == "MISSED") Color(0xFFE74C3C) else Color(0xFFE67E22),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "${entry.dayOfWeek}, ${entry.dateString}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (entry.actionType == "MISSED") "Missed checkpoint entry" else "Manual backlog count adjustment",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { onResolve(entry) },
                                        modifier = Modifier.size(36.dp).testTag("resolve_missed_date_button_${entry.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Offer Salat",
                                            tint = Color(0xFF27AE60)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun QadhaHistoryItemRow(entry: QadhaHistoryEntry) {
    val prayerType = PrayerType.values().find { it.id == entry.prayerId }
    val prayerName = prayerType?.displayName ?: entry.prayerId.replaceFirstChar { it.uppercase() }
    
    val badgeColor = when (entry.actionType) {
        "MISSED" -> Color(0xFFFADBD8) // Soft Red
        "RECOVERED" -> Color(0xFFD4EFDF) // Soft Green
        else -> Color(0xFFFDEBD0) // Soft Amber
    }
    val textBadgeColor = when (entry.actionType) {
        "MISSED" -> Color(0xFFC0392B)
        "RECOVERED" -> Color(0xFF27AE60)
        else -> Color(0xFFD35400)
    }
    val badgeLabel = when (entry.actionType) {
        "MISSED" -> "Missed"
        "RECOVERED" -> "Offered"
        else -> "Adjusted"
    }
    val actionIcon = when (entry.actionType) {
        "MISSED" -> Icons.Default.Remove
        "RECOVERED" -> Icons.Default.Check
        else -> Icons.Default.Add
    }
    val actionIconColor = when (entry.actionType) {
        "MISSED" -> Color(0xFFC0392B)
        "RECOVERED" -> Color(0xFF27AE60)
        else -> Color(0xFFD35400)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = badgeLabel,
                    tint = actionIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prayerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Box(
                        modifier = Modifier
                            .background(badgeColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textBadgeColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${entry.dayOfWeek}, ${entry.dateString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun QadhaBookLedgerRow(
    prayer: PrayerType,
    count: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("ledger_row_${prayer.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prayer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unit: ${prayer.rakah} Rak'ah • Backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minus Button
                FilledIconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_decrease_${prayer.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (count > 0) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF2F4F4),
                        contentColor = if (count > 0) MaterialTheme.colorScheme.primary else Color(0xFFBDC3C7)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease backlog count",
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Count Display
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }

                // Plus Button
                FilledIconButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_increase_${prayer.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase backlog count",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 7. SCREEN 3: ACHIEVEMENTS & SPIRITUAL REWARDS
// ==========================================

@Composable
fun AchievementsScreen(uiState: TrackerState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Sacred Achievements",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Gamified Badges Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val unlockedCount = MILITARY_BADGES.count { uiState.activeStreak >= it.requiredStreak }
                    Text(
                        text = "Unlocked: $unlockedCount of ${MILITARY_BADGES.size} Milestones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Gamification Journey",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MILITARY_BADGES) { badge ->
                val isUnlocked = uiState.activeStreak >= badge.requiredStreak
                BadgeGridCard(badge = badge, isUnlocked = isUnlocked, userStreak = uiState.activeStreak)
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ideation_credit_achievements"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ideation by Muhaiminul Islam Sajib",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            letterSpacing = 0.5.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun BadgeGridCard(
    badge: BadgeMilestone,
    isUnlocked: Boolean,
    userStreak: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .testTag("badge_card_${badge.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color.White else Color(0xFFF2F4F4)
        ),
        border = BorderStroke(
            width = if (isUnlocked) 2.dp else 1.dp,
            color = if (isUnlocked) Color(badge.hexColor) else Color(0xFFDEDFE0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = if (isUnlocked) Color(badge.hexColor).copy(alpha = 0.15f) else Color(0x1A7F8C8D),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Badge Unlocked",
                        tint = Color(badge.hexColor),
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Badge Locked",
                        tint = Color(0xFF95A5A6),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = badge.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else Color(0xFF7F8C8D),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isUnlocked) MaterialTheme.colorScheme.secondary else Color(0xFF95A5A6),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }

            // Streak Progress bar indicator inside grid card
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { (userStreak.toFloat() / badge.requiredStreak.toFloat()).coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                        .clip(CircleShape),
                    color = if (isUnlocked) Color(badge.hexColor) else Color(0xFFBDC3C7),
                    trackColor = if (isUnlocked) Color(badge.hexColor).copy(alpha = 0.2f) else Color(0xFFE5E7E9)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${userStreak.coerceAtMost(badge.requiredStreak)}/${badge.requiredStreak} Streak",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isUnlocked) Color(badge.hexColor) else Color(0xFF95A5A6),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// 8. THE SEQUENTIAL DIALOG POP-UP QUEUE
// ==========================================

@Composable
fun SequentialQueueDialog(
    prayerType: PrayerType,
    onResponse: (Boolean) -> Unit,
    onRemindLater: (Long) -> Unit
) {
    val context = LocalContext.current
    var showReminderView by remember(prayerType) { mutableStateOf(false) }
    var selectedOption by remember(prayerType) { mutableStateOf("15m") } // "15m", "30m", "1h", "custom"

    val now = Calendar.getInstance()
    val defaultHour = (now.get(Calendar.HOUR_OF_DAY) % 12).let { if (it == 0) 12 else it }
    val defaultMinute = ((now.get(Calendar.MINUTE) + 15) / 5 * 5) % 60
    val defaultAmPm = if (now.get(Calendar.HOUR_OF_DAY) >= 12) "PM" else "AM"

    var customHour by remember(prayerType) { mutableIntStateOf(defaultHour) }
    var customMinute by remember(prayerType) { mutableIntStateOf(defaultMinute) }
    var customAmPm by remember(prayerType) { mutableStateOf(defaultAmPm) }
    var validationError by remember(prayerType) { mutableStateOf<String?>(null) }

    val prayerStartTime = remember(prayerType) {
        PrayerAlarmScheduler.getPrayerStartTimeForToday(context, prayerType)
    }
    val prayerStartTimeFormatted = remember(prayerType) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(prayerStartTime))
    }

    Dialog(
        onDismissRequest = { /* Force explicit interaction based on rules */ }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("queue_alert_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B4D3E))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon),
                            contentDescription = "Qadha logo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (showReminderView) "Set Reminder" else "Assalamu Alaikum",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (showReminderView) "REMIND ME LATER FOR ${prayerType.displayName.uppercase()}" else "DAILY SALAH CONFIRMATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                if (!showReminderView) {
                    // Standard YES / NO / REMIND ME LATER View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Have you performed your",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF5C635E),
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = "${prayerType.displayName} Salah?",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B4D3E),
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = "YES: Mark prayed & build streak\nNO: Add +1 Qadha to ${prayerType.displayName} & reset streak\nREMIND: Snooze without penalty",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF7F8C8D),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier
                                .background(Color(0xFFF7F9F9), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    // Action Buttons: YES | NO | REMIND ME LATER
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // YES Button
                        Button(
                            onClick = { onResponse(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("dialog_btn_yes"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B4D3E)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YES (Prayed)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        // NO Button
                        OutlinedButton(
                            onClick = { onResponse(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("dialog_btn_no"),
                            border = BorderStroke(1.5.dp, Color(0xFFC0392B)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFC0392B)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NO (Add +1 Qadha)",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC0392B),
                                fontSize = 15.sp
                            )
                        }

                        // REMIND ME LATER Button
                        FilledTonalButton(
                            onClick = { showReminderView = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("dialog_btn_remind_later"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFFF3CD),
                                contentColor = Color(0xFF856404)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REMIND ME LATER",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF856404),
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    // Reminder Options Screen (15m, 30m, 1h, Custom)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "When should we remind you for ${prayerType.displayName}?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2C3E50)
                        )

                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        val currentTimeMs = System.currentTimeMillis()

                        // Preset 1: 15 minutes
                        val t15 = currentTimeMs + 15 * 60 * 1000L
                        ReminderOptionTile(
                            title = "15 minutes",
                            subtitle = "At ${timeFormat.format(Date(t15))}",
                            isSelected = selectedOption == "15m",
                            onClick = {
                                selectedOption = "15m"
                                validationError = null
                            },
                            testTag = "reminder_opt_15m"
                        )

                        // Preset 2: 30 minutes
                        val t30 = currentTimeMs + 30 * 60 * 1000L
                        ReminderOptionTile(
                            title = "30 minutes",
                            subtitle = "At ${timeFormat.format(Date(t30))}",
                            isSelected = selectedOption == "30m",
                            onClick = {
                                selectedOption = "30m"
                                validationError = null
                            },
                            testTag = "reminder_opt_30m"
                        )

                        // Preset 3: 1 hour
                        val t60 = currentTimeMs + 60 * 60 * 1000L
                        ReminderOptionTile(
                            title = "1 hour",
                            subtitle = "At ${timeFormat.format(Date(t60))}",
                            isSelected = selectedOption == "1h",
                            onClick = {
                                selectedOption = "1h"
                                validationError = null
                            },
                            testTag = "reminder_opt_1h"
                        )

                        // Preset 4: Custom time
                        ReminderOptionTile(
                            title = "Custom time",
                            subtitle = "Pick specific time today (after $prayerStartTimeFormatted)",
                            isSelected = selectedOption == "custom",
                            onClick = {
                                selectedOption = "custom"
                            },
                            testTag = "reminder_opt_custom"
                        )

                        // Custom time picker UI when selected
                        if (selectedOption == "custom") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Hour stepper
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    customHour = if (customHour <= 1) 12 else customHour - 1
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Hour down", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%02d", customHour),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    customHour = if (customHour >= 12) 1 else customHour + 1
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Hour up", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                                        // Minute stepper (5-min steps)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    customMinute = if (customMinute <= 0) 55 else (customMinute - 5) / 5 * 5
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Minute down", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%02d", customMinute),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    customMinute = if (customMinute >= 55) 0 else (customMinute + 5) / 5 * 5
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Minute up", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // AM/PM toggle
                                        Button(
                                            onClick = {
                                                customAmPm = if (customAmPm == "AM") "PM" else "AM"
                                                validationError = null
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1B4D3E)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(customAmPm, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Validation error message if any
                        if (validationError != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4E4)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = validationError ?: "",
                                    color = Color(0xFFC0392B),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Confirm & Back buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showReminderView = false
                                    validationError = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dialog_btn_reminder_back"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    val targetMillis = when (selectedOption) {
                                        "15m" -> System.currentTimeMillis() + 15 * 60 * 1000L
                                        "30m" -> System.currentTimeMillis() + 30 * 60 * 1000L
                                        "1h" -> System.currentTimeMillis() + 60 * 60 * 1000L
                                        "custom" -> {
                                            val hour24 = when {
                                                customAmPm == "PM" && customHour < 12 -> customHour + 12
                                                customAmPm == "AM" && customHour == 12 -> 0
                                                else -> customHour
                                            }
                                            val cal = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, hour24)
                                                set(Calendar.MINUTE, customMinute)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            cal.timeInMillis
                                        }
                                        else -> System.currentTimeMillis() + 15 * 60 * 1000L
                                    }

                                    // Strict Salah Time Validation:
                                    // Custom reminders cannot be scheduled before the relevant Salah time.
                                    if (targetMillis < prayerStartTime) {
                                        validationError = "Cannot schedule reminder before ${prayerType.displayName} starts ($prayerStartTimeFormatted)."
                                        return@Button
                                    }
                                    if (targetMillis <= System.currentTimeMillis()) {
                                        validationError = "Reminder time must be later than the current time."
                                        return@Button
                                    }

                                    // Valid: Schedule reminder, advance queue, do not add Qadha, do not reset streak!
                                    onRemindLater(targetMillis)
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp)
                                    .testTag("dialog_btn_confirm_reminder"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4D3E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Set Reminder", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderOptionTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8F8F5) else Color(0xFFF8F9FA)
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF1B4D3E) else Color(0xFFE0E0E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF1B4D3E) else Color(0xFF2C3E50)
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSelected) Color(0xFF27AE60) else Color(0xFF7F8C8D),
                        fontSize = 11.sp
                    )
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1B4D3E))
            )
        }
    }
}
