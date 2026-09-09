package com.example

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class QadhaCloudBackupHelper(private val context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("qadha_tracker_prefs", Context.MODE_PRIVATE)

    // Fallback real Firebase Realtime Database endpoint for backup.
    private val databaseBaseUrl: String
        get() = "https://qadha-tracker-default-rtdb.firebaseio.com"

    fun isGoogleSignedIn(): Boolean {
        val user = QadhaAuthManager.getCurrentUser(context)
        return user != null && user.isGoogle
    }

    fun getGoogleUserEmail(): String? {
        return QadhaAuthManager.getCurrentUser(context)?.email
    }

    fun getGoogleUserId(): String? {
        return QadhaAuthManager.getCurrentUser(context)?.uid
    }

    fun isChoiceMade(): Boolean {
        return QadhaAuthManager.isAuthenticated(context)
    }

    fun setChoiceMade() {
        if (!QadhaAuthManager.isAuthenticated(context)) {
            QadhaAuthManager.continueAsGuest(context)
        }
    }

    fun setGoogleUser(userId: String?, email: String?, isGoogle: Boolean) {
        if (!email.isNullOrBlank()) {
            QadhaAuthManager.signInWithGoogle(context, email, userId)
        }
    }

    fun logout() {
        QadhaAuthManager.signOut(context)
    }

    /**
     * Uploads the full local state to Firebase Realtime Database.
     */
    fun backupToCloud(state: TrackerState, onComplete: (Boolean) -> Unit = {}) {
        val userId = getGoogleUserId() ?: QadhaAuthManager.getCurrentUser(context)?.uid
        if (userId.isNullOrEmpty()) {
            onComplete(false)
            return
        }

        Thread {
            try {
                val payload = JSONObject()
                payload.put("active_streak", state.activeStreak)
                payload.put("total_logged", state.totalLoggedCount)
                
                // Backlog object
                val backlogJson = JSONObject()
                state.backlog.forEach { (type, count) ->
                    backlogJson.put(type.id, count)
                }
                payload.put("backlog", backlogJson)

                // Today status
                val todayStatusJson = JSONObject()
                state.todayStatus.forEach { (type, status) ->
                    todayStatusJson.put(type.id, status.name)
                }
                payload.put("today_status", todayStatusJson)

                // History array
                val historyArray = JSONArray()
                state.history.forEach { entry ->
                    historyArray.put(entry.toSerialized())
                }
                payload.put("history", historyArray)
                payload.put("last_synced", System.currentTimeMillis())

                val jsonStr = payload.toString()
                val requestBody = jsonStr.toRequestBody("application/json; charset=utf-8".toMediaType())

                val url = "$databaseBaseUrl/users/$userId.json"
                val request = Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("CloudBackupHelper", "Successfully backed up data to cloud.")
                        onComplete(true)
                    } else {
                        Log.e("CloudBackupHelper", "Failed database write: ${response.code}")
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("CloudBackupHelper", "Error logging cloud backup", e)
                onComplete(false)
            }
        }.start()
    }

    /**
     * Fetches stored user state from the cloud database.
     */
    fun restoreFromCloud(userId: String, onResult: (TrackerState?) -> Unit) {
        Thread {
            try {
                val url = "$databaseBaseUrl/users/$userId.json"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onResult(null)
                        return@use
                    }
                    val bodyStr = response.body?.string()
                    if (bodyStr == null || bodyStr == "null" || bodyStr.isEmpty()) {
                        onResult(null)
                        return@use
                    }

                    val json = JSONObject(bodyStr)
                    val activeStreak = json.optInt("active_streak", 0)
                    val totalLogged = json.optInt("total_logged", 0)

                    // Backlog
                    val backlogMap = PrayerType.values().associateWith { type ->
                        val backlogJson = json.optJSONObject("backlog")
                        backlogJson?.optInt(type.id, 0) ?: 0
                    }

                    // Today status
                    val todayStatusMap = PrayerType.values().associateWith { type ->
                        val statusJson = json.optJSONObject("today_status")
                        val statusStr = statusJson?.optString(type.id, TodayStatus.UNTRACKED.name) ?: TodayStatus.UNTRACKED.name
                        try { TodayStatus.valueOf(statusStr) } catch (e: Exception) { TodayStatus.UNTRACKED }
                    }

                    // History
                    val historyList = mutableListOf<QadhaHistoryEntry>()
                    val historyArray = json.optJSONArray("history")
                    if (historyArray != null) {
                        for (i in 0 until historyArray.length()) {
                            val serialized = historyArray.getString(i)
                            val entry = QadhaHistoryEntry.fromSerialized(serialized)
                            if (entry != null) {
                                historyList.add(entry)
                            }
                        }
                    }

                    val restoredState = TrackerState(
                        activeStreak = activeStreak,
                        backlog = backlogMap,
                        todayStatus = todayStatusMap,
                        totalLoggedCount = totalLogged,
                        missedPrayers = emptyList(),
                        currentPrayerIndex = 0,
                        history = historyList
                    )
                    onResult(restoredState)
                }
            } catch (e: Exception) {
                Log.e("CloudBackupHelper", "Error restoring from cloud", e)
                onResult(null)
            }
        }.start()
    }
}
