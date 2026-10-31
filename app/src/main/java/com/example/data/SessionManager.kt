package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.NotificationPreferences
import com.example.model.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "tontine_user_session"
        private const val KEY_USER_ID = "active_user_id"
        private const val KEY_FULL_NAME = "active_full_name"
        private const val KEY_PHONE = "active_phone"
        private const val KEY_AVATAR = "active_avatar"
        private const val KEY_LAST_NOTIF_TIMESTAMP = "last_notified_timestamp"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveUserSession(user: User) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_PHONE, user.phoneNumber)
            .putString(KEY_AVATAR, user.avatarUrl)
            .apply()
    }

    fun getActiveUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getActiveUser(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val fullName = prefs.getString(KEY_FULL_NAME, "") ?: ""
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val avatar = prefs.getString(KEY_AVATAR, "") ?: ""
        return User(
            id = id,
            fullName = fullName,
            phoneNumber = phone,
            avatarUrl = avatar,
            preferences = NotificationPreferences()
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun getLastNotifiedTimestamp(): Long {
        return prefs.getLong(KEY_LAST_NOTIF_TIMESTAMP, 0L)
    }

    fun setLastNotifiedTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_NOTIF_TIMESTAMP, ts).apply()
    }

    fun isNotificationDispatched(notifId: String): Boolean {
        val set = prefs.getStringSet("dispatched_notif_ids", emptySet()) ?: emptySet()
        return notifId in set
    }

    fun markNotificationDispatched(notifId: String) {
        val currentSet = prefs.getStringSet("dispatched_notif_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(notifId)
        // Keep set bounded to last 100 IDs to avoid unbounded growth
        val bounded = if (currentSet.size > 100) currentSet.toList().takeLast(80).toSet() else currentSet
        prefs.edit().putStringSet("dispatched_notif_ids", bounded).apply()
    }

    fun getAppliedVersion(): String {
        return prefs.getString("applied_app_version", "v1.5.0") ?: "v1.5.0"
    }

    fun setAppliedVersion(version: String) {
        prefs.edit().putString("applied_app_version", version).apply()
    }

    fun getSavedAccentTheme(): String? {
        return prefs.getString("saved_accent_theme", null)
    }

    fun setSavedAccentTheme(themeName: String) {
        prefs.edit().putString("saved_accent_theme", themeName).apply()
    }

    fun getSavedDisplayMode(): String? {
        return prefs.getString("saved_display_mode", null)
    }

    fun setSavedDisplayMode(mode: String) {
        prefs.edit().putString("saved_display_mode", mode).apply()
    }
}
