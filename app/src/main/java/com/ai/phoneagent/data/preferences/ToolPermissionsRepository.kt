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
 * 工具权限组的本地持久化（DataStore）。
 *
 * 键：
 *  - `master_switch` → 字符串（PermissionLevel 枚举名），默认 `CAUTION`
 *  - `tool_$toolName` → 字符串（各工具的动态权限级别）
 */
class ToolPermissionsRepository(
    private val context: Context,
) {
    private object Keys {
        val masterSwitch = stringPreferencesKey("master_switch")

        fun toolKey(toolName: String) = stringPreferencesKey("tool_$toolName")
    }

    /** 总开关级别的响应式流。 */
    val masterSwitchFlow: Flow<String> =
        context.toolPermissionsDataStore.data.map { prefs ->
            prefs[Keys.masterSwitch] ?: "CAUTION"
        }

    /** 读取总开关级别，未设置时默认 [CAUTION]。 */
    suspend fun getMasterSwitch(): String {
        val prefs = context.toolPermissionsDataStore.data.first()
        return prefs[Keys.masterSwitch] ?: "CAUTION"
    }

    /** 设置总开关级别。 */
    suspend fun setMasterSwitch(value: String) {
        context.toolPermissionsDataStore.edit { prefs ->
            prefs[Keys.masterSwitch] = value
        }
    }

    /** 读取指定工具的权限级别；未单独设置时返回 null（由上层按总开关决定）。 */
    suspend fun getToolPermission(toolName: String): String? {
        val prefs = context.toolPermissionsDataStore.data.first()
        return prefs[Keys.toolKey(toolName)]
    }

    /** 指定工具权限级别的响应式流。 */
    fun getToolPermissionFlow(toolName: String): Flow<String?> =
        context.toolPermissionsDataStore.data.map { prefs ->
            prefs[Keys.toolKey(toolName)]
        }

    /** 设置指定工具的权限级别。 */
    suspend fun setToolPermission(toolName: String, value: String) {
        context.toolPermissionsDataStore.edit { prefs ->
            prefs[Keys.toolKey(toolName)] = value
        }
    }

    /** 移除指定工具的权限级别（恢复跟随总开关）。 */
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
