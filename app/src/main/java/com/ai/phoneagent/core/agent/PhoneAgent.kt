package com.ai.phoneagent.core.agent

import android.content.Context
import android.util.Log
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import com.ai.phoneagent.data.model.ImageResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Phone Agent - AI驱动的手机自动化Agent
 * 完全对齐Operit的PhoneAgent实现
 */
class PhoneAgent(
    private val context: Context,
    private val config: AgentConfig = AgentConfig(),
    private val actionHandler: ActionHandler
) {
    companion object {
        private const val TAG = "PhoneAgent"
    }

    private var _stepCount = 0

    val stepCount: Int
        get() = _stepCount
    private val contextHistory = mutableListOf<Pair<String, String>>()

    /**
     * 运行Agent执行任务
     * @param task 任务描述
     * @param apiKey AutoGLM API Key
     * @param model 模型名称
     * @param systemPrompt 系统提示词
     * @param onStep 每步执行后的回调
     * @param isPausedFlow 暂停状态Flow
     * @return 最终消息
     */
    suspend fun run(
        task: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        onStep: (suspend (StepResult) -> Unit)? = null,
        isPausedFlow: StateFlow<Boolean>? = null
    ): String {
        Log.d(TAG, "Starting agent task: $task")
        _stepCount = 0
        contextHistory.clear()

        try {
            // 构建初始消息
            val messages = mutableListOf<ChatRequestMessage>()
            messages.add(ChatRequestMessage(role = "system", content = systemPrompt))
            messages.add(ChatRequestMessage(role = "user", content = "任务: $task"))

            // 执行步骤循环（无硬限制，由任务自动结束）
            var step = 1
            while (step <= config.maxSteps) {
                _stepCount = step

                // 检查暂停状态
                awaitIfPaused(isPausedFlow)

                // 执行单步
                val stepResult = executeStep(
                    step = step,
                    task = task,
                    apiKey = apiKey,
                    model = model,
                    messages = messages
                )

                // 回调
                onStep?.invoke(stepResult)

                // 检查是否完成
                if (stepResult.finished) {
                    Log.d(TAG, "Task finished at step $step")
                    return stepResult.message ?: "任务完成"
                }

                // 检查是否失败
                if (!stepResult.success) {
                    Log.e(TAG, "Step $step failed: ${stepResult.message}")
                    return stepResult.message ?: "执行失败"
                }

                // 添加延迟
                delay(config.stepDelayMs)
                step++
            }

            return "达到最大步数限制(${config.maxSteps}步)，任务可能未完成。建议提高maxSteps或检查Agent逻辑"

        } catch (e: CancellationException) {
            Log.d(TAG, "Agent cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent error", e)
            return "执行出错: ${e.message}"
        }
    }

    /**
     * 执行单步
     */
    private suspend fun executeStep(
        step: Int,
        task: String,
        apiKey: String,
        model: String,
        messages: MutableList<ChatRequestMessage>
    ): StepResult {
        try {
            Log.d(TAG, "Step $step: Taking screenshot and getting UI")

            // 1. 获取当前屏幕截图
            val screenshotResult = actionHandler.takeScreenshot()
            val screenshot = if (screenshotResult.success && screenshotResult.result is ImageResultData) {
                screenshotResult.result as ImageResultData
            } else {
                Log.w(TAG, "Screenshot failed, continuing without it")
                null
            }

            // 2. 获取UI层次结构
            val uiTree = actionHandler.getUIHierarchy(config.maxUiTreeChars)

            // 3. 构建当前观察消息
            val observationContent = buildObservationMessage(screenshot, uiTree, step)
            messages.add(ChatRequestMessage(role = "user", content = observationContent))

            // 4. 调用模型
            Log.d(TAG, "Step $step: Calling AI model")
            val response = requestModelWithRetry(
                apiKey = apiKey,
                model = model,
                messages = messages,
                step = step
            )

            if (response.isFailure) {
                val error = response.exceptionOrNull()
                return StepResult(
                    success = false,
                    finished = false,
                    action = null,
                    thinking = null,
                    message = "模型调用失败: ${error?.message}"
                )
            }

            val responseText = response.getOrNull() ?: ""
            Log.d(TAG, "Step $step: Got AI response: ${responseText.take(200)}")

            // 应用内容过滤 - 去除过度的敏感检查
            val filteredResponse = ContentFilter.sanitizeModelOutput(responseText)
            
            // 5. 解析动作
            val action = parseActionWithRepair(
                apiKey = apiKey,
                model = model,
                messages = messages,
                step = step,
                responseText = filteredResponse
            )

            if (action.metadata != "do" && action.metadata != "finish") {
                return StepResult(
                    success = false,
                    finished = false,
                    action = null,
                    thinking = null,
                    message = "无法解析AI响应为有效动作"
                )
            }

            // 6. 添加AI响应到历史
            messages.add(ChatRequestMessage(role = "assistant", content = responseText))

            // 7. 检查是否完成
            if (action.metadata == "finish") {
                val finishMessage = action.fields["message"] ?: "任务完成"
                return StepResult(
                    success = true,
                    finished = true,
                    action = action,
                    thinking = extractThinking(responseText),
                    message = finishMessage
                )
            }

            // 8. 执行动作
            Log.d(TAG, "Step $step: Executing action: ${action.actionName}")
            val actionResult = actionHandler.executeAction(action)

            // 9. 记录执行结果到历史
            val resultMessage = if (actionResult.success) {
                "执行成功: ${action.actionName}"
            } else {
                "执行失败: ${actionResult.error}"
            }
            contextHistory.add(action.raw to resultMessage)

            // 10. 管理上下文长度
            manageContextLength(messages)

            return StepResult(
                success = actionResult.success,
                finished = false,
                action = action,
                thinking = extractThinking(responseText),
                message = resultMessage
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in step $step", e)
            return StepResult(
                success = false,
                finished = false,
                action = null,
                thinking = null,
                message = "步骤执行出错: ${e.message}"
            )
        }
    }

    /**
     * 构建观察消息
     */
    private fun buildObservationMessage(
        screenshot: ImageResultData?,
        uiTree: String,
        step: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("【第 $step 步观察】")
        
        if (screenshot != null) {
            sb.appendLine("屏幕截图 (${screenshot.width}x${screenshot.height}):")
            sb.appendLine("data:image/png;base64,${screenshot.base64Data}")
        } else {
            sb.appendLine("(未能获取截图)")
        }
        
        sb.appendLine()
        sb.appendLine("UI层次结构:")
        sb.appendLine(uiTree)
        
        return sb.toString()
    }

    /**
     * 提取思考过程
     */
    private fun extractThinking(response: String): String? {
        // 尝试提取thinking内容
        val thinkingMatch = Regex("thinking:\\s*(.+?)(?=\\naction:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        return thinkingMatch?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * 解析动作并修复
     */
    private suspend fun parseActionWithRepair(
        apiKey: String,
        model: String,
        messages: MutableList<ChatRequestMessage>,
        step: Int,
        responseText: String
    ): ParsedAgentAction {
        var action = parseAgentAction(responseText)
        
        if (action.metadata == "do" || action.metadata == "finish") {
            return action
        }

        // 尝试修复
        repeat(config.maxParseRepairs) { attempt ->
            Log.d(TAG, "Step $step: Attempting parse repair ${attempt + 1}/${config.maxParseRepairs}")
            
            val repairPrompt = """
                你的输出格式不正确。请严格按照以下格式重新输出：
                
                thinking: [你的思考过程]
                action: do(动作名称, 参数1=值1, 参数2=值2)
                
                或者如果任务完成：
                action: finish(message=完成信息)
            """.trimIndent()
            
            messages.add(ChatRequestMessage(role = "user", content = repairPrompt))
            
            val repairResult = requestModelWithRetry(apiKey, model, messages, step)
            if (repairResult.isSuccess) {
                val repaired = repairResult.getOrNull() ?: ""
                messages.add(ChatRequestMessage(role = "assistant", content = repaired))
                
                action = parseAgentAction(repaired)
                if (action.metadata == "do" || action.metadata == "finish") {
                    return action
                }
            }
        }
        
        return action
    }

    /**
     * 解析Agent动作
     */
    private fun parseAgentAction(text: String): ParsedAgentAction {
        val trimmed = text.trim()
        
        // 提取action行
        val actionLine = Regex("action:\\s*(.+?)(?=\\n|$)", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.getOrNull(1)?.trim() ?: trimmed
        
        // 解析 do(...) 或 finish(...)
        val doMatch = Regex("""do\s*\(\s*([^,\)]+)\s*(?:,\s*(.+?))?\s*\)""", RegexOption.DOT_MATCHES_ALL)
            .find(actionLine)
        
        if (doMatch != null) {
            val actionName = doMatch.groupValues[1].trim()
            val paramsStr = doMatch.groupValues.getOrNull(2)?.trim() ?: ""
            val fields = parseParameters(paramsStr)
            
            return ParsedAgentAction(
                metadata = "do",
                actionName = actionName,
                fields = fields,
                raw = text
            )
        }
        
        val finishMatch = Regex("""finish\s*\(\s*(.+?)\s*\)""", RegexOption.DOT_MATCHES_ALL)
            .find(actionLine)
        
        if (finishMatch != null) {
            val paramsStr = finishMatch.groupValues[1].trim()
            val fields = parseParameters(paramsStr)
            
            return ParsedAgentAction(
                metadata = "finish",
                actionName = null,
                fields = fields,
                raw = text
            )
        }
        
        // 无法解析
        return ParsedAgentAction(
            metadata = "error",
            actionName = null,
            fields = emptyMap(),
            raw = text
        )
    }

    /**
     * 解析参数
     */
    private fun parseParameters(paramsStr: String): Map<String, String> {
        if (paramsStr.isEmpty()) return emptyMap()
        
        val params = mutableMapOf<String, String>()
        val parts = paramsStr.split(",")
        
        for (part in parts) {
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                val key = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                params[key] = value
            }
        }
        
        return params
    }

    /**
     * 带重试的模型请求
     */
    private suspend fun requestModelWithRetry(
        apiKey: String,
        model: String,
        messages: List<ChatRequestMessage>,
        step: Int
    ): Result<String> {
        val maxAttempts = (config.maxModelRetries + 1).coerceAtLeast(1)
        var lastErr: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            val result = withContext(Dispatchers.IO) {
                AutoGlmClient.sendChatResult(
                    apiKey = apiKey,
                    messages = messages,
                    model = model,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    topP = config.topP,
                    frequencyPenalty = config.frequencyPenalty
                )
            }

            if (result.isSuccess) return result

            val err = result.exceptionOrNull()
            if (err is CancellationException) throw err
            lastErr = err

            val retryable = isRetryableModelError(err)
            if (!retryable || attempt >= maxAttempts - 1) break

            val waitMs = computeModelRetryDelayMs(attempt)
            Log.d(TAG, "Step $step: Model call failed (${attempt + 1}/$maxAttempts), retrying in ${waitMs}ms...")
            delay(waitMs)
        }

        return Result.failure(lastErr ?: IOException("Unknown model error"))
    }

    /**
     * 判断是否可重试的错误
     */
    private fun isRetryableModelError(t: Throwable?): Boolean {
        if (t == null) return false
        if (t is CancellationException) return false
        if (t is IOException) return true
        
        // 检查HTTP错误码
        val message = t.message ?: ""
        if (message.contains("429") || message.contains("rate limit", ignoreCase = true)) return true
        if (message.contains("500") || message.contains("503")) return true
        
        return false
    }

    /**
     * 计算重试延迟
     */
    private fun computeModelRetryDelayMs(attempt: Int): Long {
        val base = config.modelRetryBaseDelayMs.coerceAtLeast(0L)
        val mult = 1L shl attempt.coerceIn(0, 6)
        return (base * mult).coerceAtMost(6000L)
    }

    /**
     * 管理上下文长度
     */
    private fun manageContextLength(messages: MutableList<ChatRequestMessage>) {
        // 简单实现：保留系统提示+最近N轮对话
        if (messages.size > config.maxHistoryTurns * 2 + 2) {
            // 保留第一条（system）和第二条（初始任务）
            val toKeep = messages.take(2).toMutableList()
            // 保留最近的N轮
            val recent = messages.takeLast(config.maxHistoryTurns * 2)
            toKeep.addAll(recent)
            
            messages.clear()
            messages.addAll(toKeep)
        }
    }

    /**
     * 等待暂停状态恢复
     */
    private suspend fun awaitIfPaused(isPausedFlow: StateFlow<Boolean>?) {
        if (isPausedFlow == null) return
        
        while (isPausedFlow.value) {
            delay(200)
        }
    }
}
