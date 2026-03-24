package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.toolPermissionsDataStore by preferencesDataStore(name = "tool_permissions")

/**
 * Repository for tool_permissions SharedPreferences group.
 *
 * Keys:
 *  - "master_switch" → String (PermissionLevel enum name), default "CAUTION"
 *  - "tool_$toolName" → String (dynamic per-tool permission level)
 */
class ToolPermissionsRepository(
    private val context: Context,
) {
    private object Keys {
        val masterSwitch = stringPreferencesKey("master_switch")

        fun toolKey(toolName: String) = stringPreferencesKey("tool_$toolName")
    }

    val masterSwitchFlow: Flow<String> =
        context.toolPermissionsDataStore.data.map { prefs ->
            prefs[Keys.masterSwitch] ?: "CAUTION"
        }

    suspend fun getMasterSwitch(): String {
        val prefs = context.toolPermissionsDataStore.data.first()
        return prefs[Keys.masterSwitch] ?: "CAUTION"
    }

    suspend fun setMasterSwitch(value: String) {
        context.toolPermissionsDataStore.edit { prefs ->
            prefs[Keys.masterSwitch] = value
        }
    }

    suspend fun getToolPermission(toolName: String): String? {
        val prefs = context.toolPermissionsDataStore.data.first()
        return prefs[Keys.toolKey(toolName)]
    }

    fun getToolPermissionFlow(toolName: String): Flow<String?> =
        context.toolPermissionsDataStore.data.map { prefs ->
            prefs[Keys.toolKey(toolName)]
        }

    suspend fun setToolPermission(toolName: String, value: String) {
        context.toolPermissionsDataStore.edit { prefs ->
            prefs[Keys.toolKey(toolName)] = value
        }
    }

    suspend fun removeToolPermission(toolName: String) {
        context.toolPermissionsDataStore.edit { prefs ->
            prefs.remove(Keys.toolKey(toolName))
        }
    }

    fun getMasterSwitchBlocking(): String = runBlocking { getMasterSwitch() }
    fun setMasterSwitchBlocking(value: String) = runBlocking { setMasterSwitch(value) }
    fun getToolPermissionBlocking(toolName: String): String? = runBlocking { getToolPermission(toolName) }
    fun setToolPermissionBlocking(toolName: String, value: String) = runBlocking {
        setToolPermission(toolName, value)
    }
    fun removeToolPermissionBlocking(toolName: String) = runBlocking { removeToolPermission(toolName) }
}
