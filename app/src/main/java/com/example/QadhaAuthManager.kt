package com.example

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class QadhaUser(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val isGoogle: Boolean = false
)

object QadhaAuthManager {
    private const val PREFS_AUTH = "qadha_auth_session"
    private const val PREFS_ACCOUNTS = "qadha_user_accounts"
    private const val KEY_CURRENT_UID = "active_current_uid"
    private const val KEY_CURRENT_EMAIL = "active_current_email"
    private const val KEY_CURRENT_NAME = "active_current_display_name"
    private const val KEY_IS_GOOGLE = "active_is_google"

    fun getCurrentUser(context: Context): QadhaUser? {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_CURRENT_UID, null) ?: return null
        val email = prefs.getString(KEY_CURRENT_EMAIL, "") ?: ""
        val name = prefs.getString(KEY_CURRENT_NAME, "") ?: ""
        val isGoogle = prefs.getBoolean(KEY_IS_GOOGLE, false)
        return QadhaUser(uid = uid, email = email, displayName = name, isGoogle = isGoogle)
    }

    fun isAuthenticated(context: Context): Boolean {
        return getCurrentUser(context) != null
    }

    /**
     * Creates a new user account with Email & Password.
     * Assigns a unique, secure, persistent UID.
     */
    fun signUpWithEmail(context: Context, email: String, password: String): Result<QadhaUser> {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val accountsPrefs = context.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE)
        if (accountsPrefs.contains(cleanEmail)) {
            return Result.failure(IllegalStateException("An account with this email already exists. Please sign in."))
        }

        // Generate a unique Firebase-compatible UID
        val randomPart = UUID.randomUUID().toString().replace("-", "")
        val uid = "usr_${hashSha256(cleanEmail).take(12)}${randomPart.take(12)}"
        val passwordHash = hashSha256(password)
        val displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

        val json = JSONObject().apply {
            put("uid", uid)
            put("email", cleanEmail)
            put("password_hash", passwordHash)
            put("display_name", displayName)
            put("is_google", false)
        }
        accountsPrefs.edit().putString(cleanEmail, json.toString()).apply()

        val user = QadhaUser(uid = uid, email = cleanEmail, displayName = displayName, isGoogle = false)
        setCurrentUser(context, user)
        return Result.success(user)
    }

    /**
     * Signs in an existing user with Email & Password.
     */
    fun signInWithEmail(context: Context, email: String, password: String): Result<QadhaUser> {
        val cleanEmail = email.trim().lowercase()
        val accountsPrefs = context.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE)
        val accountData = accountsPrefs.getString(cleanEmail, null)
            ?: return Result.failure(IllegalArgumentException("No account found with this email. Please sign up."))

        val json = JSONObject(accountData)
        val expectedHash = json.optString("password_hash")
        val providedHash = hashSha256(password)
        if (expectedHash != providedHash) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }

        val uid = json.getString("uid")
        val displayName = json.optString("display_name", cleanEmail.substringBefore("@"))
        val user = QadhaUser(uid = uid, email = cleanEmail, displayName = displayName, isGoogle = false)
        setCurrentUser(context, user)
        return Result.success(user)
    }

    /**
     * Signs in or registers an authentic Google account.
     * Assigns a persistent unique UID isolated for this Google account.
     */
    fun signInWithGoogle(context: Context, email: String, googleId: String? = null, displayName: String? = null): QadhaUser {
        val cleanEmail = email.trim().lowercase()
        val accountsPrefs = context.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE)
        val existingData = accountsPrefs.getString(cleanEmail, null)

        val uid = if (existingData != null) {
            JSONObject(existingData).getString("uid")
        } else {
            val gid = googleId ?: UUID.randomUUID().toString().replace("-", "")
            "goog_${hashSha256(cleanEmail).take(12)}${gid.take(12)}"
        }

        val name = displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

        val json = JSONObject().apply {
            put("uid", uid)
            put("email", cleanEmail)
            put("display_name", name)
            put("is_google", true)
        }
        accountsPrefs.edit().putString(cleanEmail, json.toString()).apply()

        val user = QadhaUser(uid = uid, email = cleanEmail, displayName = name, isGoogle = true)
        setCurrentUser(context, user)
        return user
    }

    /**
     * Starts or resumes a private guest session.
     */
    fun continueAsGuest(context: Context): QadhaUser {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val guestUid = prefs.getString("guest_session_uid", null) ?: run {
            val newUid = "guest_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            prefs.edit().putString("guest_session_uid", newUid).apply()
            newUid
        }
        val guestUser = QadhaUser(uid = guestUid, email = "guest@local", displayName = "Guest User", isGoogle = false)
        setCurrentUser(context, guestUser)
        return guestUser
    }

    fun signOut(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun setCurrentUser(context: Context, user: QadhaUser) {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CURRENT_UID, user.uid)
            putString(KEY_CURRENT_EMAIL, user.email)
            putString(KEY_CURRENT_NAME, user.displayName)
            putBoolean(KEY_IS_GOOGLE, user.isGoogle)
            apply()
        }
    }

    private fun hashSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
