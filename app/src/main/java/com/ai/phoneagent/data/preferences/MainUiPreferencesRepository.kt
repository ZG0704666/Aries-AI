package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mainUiPreferencesDataStore by preferencesDataStore(name = "main_ui_preferences")

class MainUiPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val activeConversationId = longPreferencesKey("active_conversation_id")
        val thinkingExpandedByDefault = booleanPreferencesKey("thinking_expanded_by_default")
    }

    val thinkingExpandedByDefaultFlow: Flow<Boolean> =
        context.mainUiPreferencesDataStore.data.map { prefs ->
            prefs[Keys.thinkingExpandedByDefault] ?: false
        }

    suspend fun getActiveConversationId(): Long? {
        val prefs = context.mainUiPreferencesDataStore.data.first()
        return prefs[Keys.activeConversationId]
    }

    suspend fun setActiveConversationId(conversationId: Long?) {
        context.mainUiPreferencesDataStore.edit { prefs ->
            prefs.updateLong(Keys.activeConversationId, conversationId)
        }
    }

    suspend fun setThinkingExpandedByDefault(expanded: Boolean) {
        context.mainUiPreferencesDataStore.edit { prefs ->
            prefs[Keys.thinkingExpandedByDefault] = expanded
        }
    }
}

private fun MutablePreferences.updateLong(
    key: Preferences.Key<Long>,
    value: Long?,
) {
    if (value == null || value < 0L) {
        remove(key)
    } else {
        this[key] = value
    }
}
