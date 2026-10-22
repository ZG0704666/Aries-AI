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

/** 自动化结果独立 DataStore，存储最近一次自动化任务的运行状态与日志。 */
private val Context.automationResultsDataStore by preferencesDataStore(name = "automation_results")

/** 自动化结果仓库：持久化最近一次自动化执行的结果快照（成败、消息、步数、日志等）。 */
class AutomationResultsRepository(
    private val context: Context,
) {
    private object Keys {
        val lastResultSuccess = booleanPreferencesKey("last_result_success")
        val lastResultMessage = stringPreferencesKey("last_result_message")
        val lastResultSteps = intPreferencesKey("last_result_steps")
        val lastResultTime = longPreferencesKey("last_result_time")
        val runId = stringPreferencesKey("run_id")
        val goal = stringPreferencesKey("goal")
        val startedAt = longPreferencesKey("started_at")
        val status = stringPreferencesKey("status")
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

    /** 一次性写入完整结果快照；未显式传入状态时按成败推导为 SUCCEEDED/FAILED。 */
    suspend fun saveResult(
        success: Boolean,
        message: String,
        steps: Int,
        time: Long,
        log: String,
        runId: String = "",
        goal: String = "",
        startedAt: Long = 0L,
        status: String = if (success) "SUCCEEDED" else "FAILED",
    ) {
        context.automationResultsDataStore.edit { prefs ->
            prefs[Keys.runId] = runId
            prefs[Keys.goal] = goal
            prefs[Keys.startedAt] = startedAt
            prefs[Keys.status] = status
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

    // ─── 阻塞式快照读取 ────────────────────────────────────────────

    /** 最近一次自动化结果的快照数据。 */
    data class LastResult(
        val success: Boolean,
        val message: String,
        val steps: Int,
        val time: Long,
        val log: String,
        val runId: String = "",
        val goal: String = "",
        val startedAt: Long = 0L,
        val status: String = if (success) "SUCCEEDED" else "FAILED",
    ) {
        fun matchesRun(id: String): Boolean = id.isNotBlank() && runId == id
    }

    /** 从偏好项解码为 [LastResult]，缺失字段按默认值回填。 */
    private fun decode(prefs: androidx.datastore.preferences.core.Preferences) = LastResult(
        success = prefs[Keys.lastResultSuccess] ?: false,
        message = prefs[Keys.lastResultMessage].orEmpty(),
        steps = prefs[Keys.lastResultSteps] ?: 0,
        time = prefs[Keys.lastResultTime] ?: 0L,
        log = prefs[Keys.lastLog].orEmpty(),
        runId = prefs[Keys.runId].orEmpty(),
        goal = prefs[Keys.goal].orEmpty(),
        startedAt = prefs[Keys.startedAt] ?: 0L,
        status = prefs[Keys.status] ?: if (prefs[Keys.lastResultSuccess] == true) "SUCCEEDED" else "FAILED",
    )

    val resultFlow: Flow<LastResult> = context.automationResultsDataStore.data.map(::decode)
    fun getLastResultBlocking(): LastResult = runBlocking { resultFlow.first() }
}
