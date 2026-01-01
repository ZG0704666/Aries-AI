package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/** 委托类，负责管理token统计相关功能 */
class TokenStatisticsDelegate(
    private val coroutineScope: CoroutineScope,
    private val getEnhancedAiService: () -> EnhancedAIService?
) {
    companion object {
        private const val TAG = "TokenStatisticsDelegate"
    }

    // --- UI State Flows ---
    private val _cumulativeInputTokens = MutableStateFlow(0)
    val cumulativeInputTokensFlow: StateFlow<Int> = _cumulativeInputTokens.asStateFlow()

    private val _cumulativeOutputTokens = MutableStateFlow(0)
    val cumulativeOutputTokensFlow: StateFlow<Int> = _cumulativeOutputTokens.asStateFlow()

    private val _currentWindowSize = MutableStateFlow(0)
    val currentWindowSizeFlow: StateFlow<Int> = _currentWindowSize.asStateFlow()

    private val _perRequestTokenCount = MutableStateFlow<Pair<Int, Int>?>(null)
    val perRequestTokenCountFlow: StateFlow<Pair<Int, Int>?> = _perRequestTokenCount.asStateFlow()

    // --- Internal State ---
    private var lastCurrentWindowSize = 0
    private var tokenCollectorJob: Job? = null


    fun setupCollectors() {
        tokenCollectorJob?.cancel() // Cancel previous collector if any
        val service = getEnhancedAiService() ?: return // Service not ready
        tokenCollectorJob = coroutineScope.launch(Dispatchers.IO) {
            service.perRequestTokenCounts.collect { counts ->
                _perRequestTokenCount.value = counts
                counts?.let {
                    _currentWindowSize.value = it.first
                    lastCurrentWindowSize = it.first
                }
            }
        }
    }

    /** 重置token统计 */
    fun resetTokenStatistics() {
        _cumulativeInputTokens.value = 0
        _cumulativeOutputTokens.value = 0
        _currentWindowSize.value = 0
        _perRequestTokenCount.value = null
        lastCurrentWindowSize = 0

        // 同时重置服务中的token计数
        getEnhancedAiService()?.resetTokenCounters()
        AppLogger.d(TAG, "token统计已重置")
    }

    /** 更新累计的token统计信息 */
    fun updateCumulativeStatistics() {
        val service = getEnhancedAiService()
        service?.let {
            try {
                // 从AI服务获取最新的token统计
                val currentInputTokens = it.getCurrentInputTokenCount()
                val currentOutputTokens = it.getCurrentOutputTokenCount()

                // 更新累计token数
                _cumulativeInputTokens.value += currentInputTokens
                _cumulativeOutputTokens.value += currentOutputTokens

                AppLogger.d(
                        TAG,
                    "Cumulative token stats updated - " +
                            "Input: ${_cumulativeInputTokens.value}, Output: ${_cumulativeOutputTokens.value}"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取累计token计数时出错: ${e.message}", e)
            }
        }
    }

    /** 设置累计token计数 */
    fun setTokenCounts(inputTokens: Int, outputTokens: Int, windowSize: Int) {
        _cumulativeInputTokens.value = inputTokens
        _cumulativeOutputTokens.value = outputTokens
        _currentWindowSize.value = windowSize
        lastCurrentWindowSize = windowSize
    }

    /** 获取当前累计token计数 */
    fun getCumulativeTokenCounts(): Pair<Int, Int> {
        return Pair(_cumulativeInputTokens.value, _cumulativeOutputTokens.value)
    }

    /** 获取最近一次的实际上下文窗口大小 */
    fun getLastCurrentWindowSize(): Int {
        return lastCurrentWindowSize
    }
}
