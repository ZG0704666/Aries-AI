package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.automationResultsDataStore by preferencesDataStore(name = "automation_results")

class AutomationResultsRepository(
    private val context: Context,
) {
    private object Keys {
        val lastResultSuccess = booleanPreferencesKey("last_result_success")
        val lastResultMessage = stringPreferencesKey("last_result_message")
        val lastResultSteps = intPreferencesKey("last_result_steps")
        val lastResultTime = longPreferencesKey("last_result_time")
        val lastLog = stringPreferencesKey("last_log")
    }

    val lastResultSuccessFlow: Flow<Boolean> =
        context.automationResultsDataStore.data.map { prefs ->
            prefs[Keys.lastResultSuccess] ?: false
        }

    val lastResultMessageFlow: Flow<String> =
        context.automationResultsDataStore.data.map { prefs ->
            prefs[Keys.lastResultMessage] ?: ""
        }

    val lastResultStepsFlow: Flow<Int> =
        context.automationResultsDataStore.data.map { prefs ->
            prefs[Keys.lastResultSteps] ?: 0
        }

    val lastResultTimeFlow: Flow<Long> =
        context.automationResultsDataStore.data.map { prefs ->
            prefs[Keys.lastResultTime] ?: 0L
        }

    val lastLogFlow: Flow<String> =
        context.automationResultsDataStore.data.map { prefs ->
            prefs[Keys.lastLog] ?: ""
        }

    suspend fun getLastResultSuccess(): Boolean {
        val prefs = context.automationResultsDataStore.data.first()
        return prefs[Keys.lastResultSuccess] ?: false
    }

    suspend fun setLastResultSuccess(value: Boolean) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastResultSuccess] = value
        }
    }

    suspend fun getLastResultMessage(): String {
        val prefs = context.automationResultsDataStore.data.first()
        return prefs[Keys.lastResultMessage] ?: ""
    }

    suspend fun setLastResultMessage(value: String) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastResultMessage] = value
        }
    }

    suspend fun getLastResultSteps(): Int {
        val prefs = context.automationResultsDataStore.data.first()
        return prefs[Keys.lastResultSteps] ?: 0
    }

    suspend fun setLastResultSteps(value: Int) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastResultSteps] = value
        }
    }

    suspend fun getLastResultTime(): Long {
        val prefs = context.automationResultsDataStore.data.first()
        return prefs[Keys.lastResultTime] ?: 0L
    }

    suspend fun setLastResultTime(value: Long) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastResultTime] = value
        }
    }

    suspend fun getLastLog(): String {
        val prefs = context.automationResultsDataStore.data.first()
        return prefs[Keys.lastLog] ?: ""
    }

    suspend fun setLastLog(value: String) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastLog] = value
        }
    }

    suspend fun saveResult(
        success: Boolean,
        message: String,
        steps: Int,
        time: Long,
        log: String,
    ) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.lastResultSuccess] = success
            prefs[Keys.lastResultMessage] = message
            prefs[Keys.lastResultSteps] = steps
            prefs[Keys.lastResultTime] = time
            prefs[Keys.lastLog] = log
        }
    }

    suspend fun clearAll() {
        context.automationResultsDataStore.edit { prefs -> prefs.clear() }
    }

    // ─── Blocking snapshot helpers ────────────────────────────────────────────

    data class LastResult(
        val success: Boolean,
        val message: String,
        val steps: Int,
        val time: Long,
        val log: String,
    )

    fun getLastResultBlocking(): LastResult = runBlocking {
        val prefs = context.automationResultsDataStore.data.first()
        LastResult(
            success = prefs[Keys.lastResultSuccess] ?: false,
            message = prefs[Keys.lastResultMessage] ?: "",
            steps = prefs[Keys.lastResultSteps] ?: 0,
            time = prefs[Keys.lastResultTime] ?: 0L,
            log = prefs[Keys.lastLog] ?: "",
        )
    }
}
