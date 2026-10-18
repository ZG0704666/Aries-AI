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

/**
 * 主界面 UI 偏好的本地持久化（DataStore）。
 *
 * 记录消息展示模式、活跃会话 ID 与思维链默认展开状态。
 */
class MainUiPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val plainMessages = booleanPreferencesKey("plain_messages")
        val activeConversationId = longPreferencesKey("active_conversation_id")
        val thinkingExpandedByDefault = booleanPreferencesKey("thinking_expanded_by_default")
    }

/** 是否以纯文本展示消息。 */
    val plainMessagesFlow: Flow<Boolean> = context.mainUiPreferencesDataStore.data.map { it[Keys.plainMessages] ?: false }
    suspend fun setPlainMessages(value: Boolean) {
        context.mainUiPreferencesDataStore.edit { it[Keys.plainMessages] = value }
    }

    /** 思维链默认是否展开。 */
    val thinkingExpandedByDefaultFlow: Flow<Boolean> =
        context.mainUiPreferencesDataStore.data.map { prefs ->
            prefs[Keys.thinkingExpandedByDefault] ?: false
        }

/** 读取当前活跃会话 ID；无记录时返回 null。 */
    suspend fun getActiveConversationId(): Long? {
        val prefs = context.mainUiPreferencesDataStore.data.first()
        return prefs[Keys.activeConversationId]
    }

    /** 写入活跃会话 ID，null 或负数时移除记录。 */
    suspend fun setActiveConversationId(conversationId: Long?) {
        context.mainUiPreferencesDataStore.edit { prefs ->
            prefs.updateLong(Keys.activeConversationId, conversationId)
        }
    }

    /** 设置思维链默认展开状态。 */
    suspend fun setThinkingExpandedByDefault(expanded: Boolean) {
        context.mainUiPreferencesDataStore.edit { prefs ->
            prefs[Keys.thinkingExpandedByDefault] = expanded
        }
    }
}

/** 写入长整型偏好；null 或负数时删除该键。 */
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
