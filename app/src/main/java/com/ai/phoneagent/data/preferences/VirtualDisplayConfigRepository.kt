package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.virtualDisplayConfigDataStore by preferencesDataStore(name = "virtual_display_config")

class VirtualDisplayConfigRepository(
    private val context: Context,
) {
    private object Keys {
        val resolutionPreset = stringPreferencesKey("resolution_preset")
        val virtualDisplayDpi = intPreferencesKey("virtual_display_dpi")
        val virtualDisplayWidth = intPreferencesKey("virtual_display_width")
        val virtualDisplayHeight = intPreferencesKey("virtual_display_height")
        val useVirtualDisplay = booleanPreferencesKey("use_virtual_display")
        val useShizukuInteraction = booleanPreferencesKey("use_shizuku_interaction")
        val autoApproveAutomation = booleanPreferencesKey("auto_approve_automation")
    }

    val resolutionPresetFlow: Flow<String> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.resolutionPreset] ?: "1080P"
        }

    val virtualDisplayDpiFlow: Flow<Int> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.virtualDisplayDpi] ?: 480
        }

    val virtualDisplayWidthFlow: Flow<Int> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.virtualDisplayWidth] ?: 0
        }

    val virtualDisplayHeightFlow: Flow<Int> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.virtualDisplayHeight] ?: 0
        }

    val useVirtualDisplayFlow: Flow<Boolean> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.useVirtualDisplay] ?: false
        }

    val useShizukuInteractionFlow: Flow<Boolean> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.useShizukuInteraction] ?: false
        }

    val autoApproveAutomationFlow: Flow<Boolean> =
        context.virtualDisplayConfigDataStore.data.map { prefs ->
            prefs[Keys.autoApproveAutomation] ?: false
        }

    suspend fun getResolutionPreset(): String {
        val prefs = context.virtualDisplayConfigDataStore.data.first()
        return prefs[Keys.resolutionPreset] ?: "1080P"
    }

    suspend fun setResolutionPreset(value: String) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.resolutionPreset] = value
        }
    }

    suspend fun getVirtualDisplayDpi(): Int {
        val prefs = context.virtualDisplayConfigDataStore.data.first()
        return prefs[Keys.virtualDisplayDpi] ?: 480
    }

    suspend fun setVirtualDisplayDpi(value: Int) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.virtualDisplayDpi] = value
        }
    }

    suspend fun getVirtualDisplayWidth(): Int {
        val prefs = context.virtualDisplayConfigDataStore.data.first()
        return prefs[Keys.virtualDisplayWidth] ?: 0
    }

    suspend fun setVirtualDisplayWidth(value: Int) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.virtualDisplayWidth] = value
        }
    }

    suspend fun getVirtualDisplayHeight(): Int {
        val prefs = context.virtualDisplayConfigDataStore.data.first()
        return prefs[Keys.virtualDisplayHeight] ?: 0
    }

    suspend fun setVirtualDisplayHeight(value: Int) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.virtualDisplayHeight] = value
        }
    }

    suspend fun setUseVirtualDisplay(value: Boolean) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.useVirtualDisplay] = value
        }
    }

    suspend fun setUseShizukuInteraction(value: Boolean) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.useShizukuInteraction] = value
        }
    }

    suspend fun setAutoApproveAutomation(value: Boolean) {
        context.virtualDisplayConfigDataStore.edit { prefs ->
            prefs[Keys.autoApproveAutomation] = value
        }
    }
}
