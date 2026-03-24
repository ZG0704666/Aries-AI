package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.floatingChatPreferencesDataStore by preferencesDataStore(name = "floating_chat_prefs")

class FloatingChatPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val floatingMessages = stringPreferencesKey("floating_messages")
        val floatingMessagesUpdatedAt = longPreferencesKey("floating_messages_updated_at")
        val windowX = intPreferencesKey("window_x")
        val windowY = intPreferencesKey("window_y")
        val windowWidth = intPreferencesKey("window_width")
        val windowHeight = intPreferencesKey("window_height")
    }

    val floatingMessagesFlow: Flow<String?> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.floatingMessages]
        }

    val floatingMessagesUpdatedAtFlow: Flow<Long> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.floatingMessagesUpdatedAt] ?: 0L
        }

    val windowXFlow: Flow<Int> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.windowX] ?: 100
        }

    val windowYFlow: Flow<Int> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.windowY] ?: 200
        }

    val windowWidthFlow: Flow<Int> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.windowWidth] ?: 0
        }

    val windowHeightFlow: Flow<Int> =
        context.floatingChatPreferencesDataStore.data.map { prefs ->
            prefs[Keys.windowHeight] ?: 0
        }

    suspend fun getFloatingMessages(): String? {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.floatingMessages]
    }

    suspend fun setFloatingMessages(value: String?) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.floatingMessages)
            } else {
                prefs[Keys.floatingMessages] = value
            }
        }
    }

    suspend fun setFloatingMessagesUpdatedAt(value: Long) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs[Keys.floatingMessagesUpdatedAt] = value
        }
    }

    suspend fun getFloatingMessagesUpdatedAt(): Long {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.floatingMessagesUpdatedAt] ?: 0L
    }

    suspend fun getWindowX(): Int {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.windowX] ?: 100
    }

    suspend fun setWindowX(value: Int) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs[Keys.windowX] = value
        }
    }

    suspend fun getWindowY(): Int {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.windowY] ?: 200
    }

    suspend fun setWindowY(value: Int) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs[Keys.windowY] = value
        }
    }

    suspend fun getWindowWidth(): Int {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.windowWidth] ?: 0
    }

    suspend fun setWindowWidth(value: Int) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs[Keys.windowWidth] = value
        }
    }

    suspend fun getWindowHeight(): Int {
        val prefs = context.floatingChatPreferencesDataStore.data.first()
        return prefs[Keys.windowHeight] ?: 0
    }

    suspend fun setWindowHeight(value: Int) {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs[Keys.windowHeight] = value
        }
    }

    suspend fun clearFloatingMessages() {
        context.floatingChatPreferencesDataStore.edit { prefs ->
            prefs.remove(Keys.floatingMessages)
            prefs.remove(Keys.floatingMessagesUpdatedAt)
        }
    }

    fun getFloatingMessagesBlocking(): String? = runBlocking { getFloatingMessages() }
    fun setFloatingMessagesBlocking(value: String?) = runBlocking { setFloatingMessages(value) }
    fun getFloatingMessagesUpdatedAtBlocking(): Long = runBlocking { getFloatingMessagesUpdatedAt() }
    fun setFloatingMessagesUpdatedAtBlocking(value: Long) = runBlocking { setFloatingMessagesUpdatedAt(value) }
    fun clearFloatingMessagesBlocking() = runBlocking { clearFloatingMessages() }
    fun getWindowXBlocking(): Int = runBlocking { getWindowX() }
    fun setWindowXBlocking(value: Int) = runBlocking { setWindowX(value) }
    fun getWindowYBlocking(): Int = runBlocking { getWindowY() }
    fun setWindowYBlocking(value: Int) = runBlocking { setWindowY(value) }
    fun getWindowWidthBlocking(): Int = runBlocking { getWindowWidth() }
    fun setWindowWidthBlocking(value: Int) = runBlocking { setWindowWidth(value) }
    fun getWindowHeightBlocking(): Int = runBlocking { getWindowHeight() }
    fun setWindowHeightBlocking(value: Int) = runBlocking { setWindowHeight(value) }
}
