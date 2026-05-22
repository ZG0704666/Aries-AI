package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.telemetryPreferencesDataStore by preferencesDataStore(name = "telemetry_prefs")

class TelemetryPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val installId = stringPreferencesKey("install_id")
        val lastHeartbeatAtMs = longPreferencesKey("last_heartbeat_at_ms")
    }

    suspend fun getOrCreateInstallId(): String {
        val existing = context.telemetryPreferencesDataStore.data.first()[Keys.installId].orEmpty()
        if (existing.isNotBlank()) return existing

        val generated = UUID.randomUUID().toString()
        context.telemetryPreferencesDataStore.edit { prefs ->
            if (prefs[Keys.installId].orEmpty().isBlank()) {
                prefs[Keys.installId] = generated
            }
        }
        return context.telemetryPreferencesDataStore.data.first()[Keys.installId] ?: generated
    }

    suspend fun getLastHeartbeatAtMs(): Long =
        context.telemetryPreferencesDataStore.data.first()[Keys.lastHeartbeatAtMs] ?: 0L

    suspend fun setLastHeartbeatAtMs(value: Long) {
        context.telemetryPreferencesDataStore.edit { prefs ->
            prefs[Keys.lastHeartbeatAtMs] = value
        }
    }
}