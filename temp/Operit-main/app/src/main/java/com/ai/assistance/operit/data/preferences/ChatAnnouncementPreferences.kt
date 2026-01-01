package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Handles lightweight, one-off announcement flags so we can surface
 * "what's new" style dialogs without pulling in full DataStore flows.
 */
class ChatAnnouncementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowChatBindingAnnouncement(): Boolean {
        val storedVersion = prefs.getInt(KEY_CHAT_BINDING_ANNOUNCEMENT_VERSION, 0)
        return storedVersion < CURRENT_CHAT_BINDING_ANNOUNCEMENT_VERSION
    }

    fun setChatBindingAnnouncementAcknowledged() {
        prefs.edit()
            .putInt(
                KEY_CHAT_BINDING_ANNOUNCEMENT_VERSION,
                CURRENT_CHAT_BINDING_ANNOUNCEMENT_VERSION
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "chat_announcement_preferences"
        private const val KEY_CHAT_BINDING_ANNOUNCEMENT_VERSION =
            "chat_binding_announcement_version"
        private const val CURRENT_CHAT_BINDING_ANNOUNCEMENT_VERSION = 3
    }
}

