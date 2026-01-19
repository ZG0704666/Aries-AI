package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import com.ai.phoneagent.core.cache.ScreenshotCache
import com.ai.phoneagent.core.cache.ScreenshotThrottler
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class UiAutomationAgent(
        private val config: Config = Config(),
) {
    
    // 截图优化组件
    private val screenshotCache = ScreenshotCache(
        maxSize = 3,           // 最多缓墵3张截图
        ttlMs = 2000L          // 2秒TTL
    )
    private val screenshotThrottler = ScreenshotThrottler(
        minIntervalMs = 1100L  // 1.1秒最小间隔
    )

    interface Control {
        fun isPaused(): Boolean
        suspend fun confirm(message: String): Boolean
    }

    private object NoopControl : Control {
        override fun isPaused(): Boolean = false
        override suspend fun confirm(message: String): Boolean = false
    }

    private class TakeOverException(message: String) : RuntimeException(message)

    data class Config(
            val maxSteps: Int = 100,
            val stepDelayMs: Long = 160L,
            val maxModelRetries: Int = 2,
            val modelRetryBaseDelayMs: Long = 700L,
            val maxParseRepairs: Int = 2,
            val maxActionRepairs: Int = 1,
            val temperature: Float? = 0.0f,
            val topP: Float? = 0.85f,
            val frequencyPenalty: Float? = 0.2f,
            val maxTokens: Int? = 4500,
            // 上下文管理参数
            val maxContextTokens: Int = 24000,  // 留足够余量给输出
            val maxUiTreeChars: Int = 3000,     // 限制UI树大小
            val maxHistoryTurns: Int = 6,       // 最多保留几轮对话
            // 性能优化参数
            val useStreamingWithEarlyStop: Boolean = true,  // 使用流式API并在获取完整动作后早停
            val parallelScreenshotAndUi: Boolean = true,    // 并行获取截图和UI树
            val postActionDelayMs: Long = 120L,             // 执行动作后等待时间（原160ms）
            // 截图优化参数
            val enableScreenshotCache: Boolean = true,      // 启用截图缓存
            val enableScreenshotThrottle: Boolean = true,   // 启用截图节流
            val screenshotCompressionQuality: Int = 85,     // JPEG压缩质量 (0-100)
            val screenshotMaxSizeKB: Int = 150,             // 最大截图大小 (KB)
    )

    data class Result(
            val success: Boolean,
            val message: String,
            val steps: Int,
    )

    private data class ParsedAgentAction(
            val metadata: String,
            val actionName: String?,
            val fields: Map<String, String>,
            val raw: String,
    )

    /**
     * 获取用于显示的动作名称（中文友好）
     */
    private fun getDisplayActionName(actionName: String, action: ParsedAgentAction): String {
        val normalizedName = actionName.replace(" ", "").lowercase()
        return when (normalizedName) {
            "launch", "open_app", "start_app" -> {
                val app = action.fields["app"] ?: action.fields["package"] ?: ""
                if (app.isNotBlank()) "启动 $app" else "启动应用"
            }
            "tap", "click", "press" -> "点击"
            "doubletap", "double_tap" -> "双击"
            "longpress", "long_press" -> "长按"
            "swipe", "scroll" -> "滑动"
            "type", "input", "text" -> {
                val text = action.fields["text"]?.take(10) ?: ""
                if (text.isNotBlank()) "输入 \"$text\"" else "输入文本"
            }
            "type_name" -> "输入姓名"
            "back" -> "返回"
            "home" -> "回到桌面"
            "wait" -> "等待加载"
            "take_over", "takeover" -> "需要接管"
            else -> actionName.ifBlank { "操作" }
        }
    }

    private fun getActionDelayMs(actionName: String): Long {
        val normalized = actionName.replace(" ", "").lowercase()
        return when (normalized) {
            "launch", "open_app", "start_app" -> 1050L
            "type", "input", "text", "type_name" -> 260L
            "tap", "click", "press", "doubletap", "double_tap", "longpress", "long_press" -> 320L
            "swipe", "scroll" -> 420L
            "back" -> 220L
            "home" -> 420L
            "wait" -> 650L
            else -> 240L
        }
    }

    private fun extractFirstActionSnippet(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("do") || trimmed.startsWith("finish")) return trimmed

        val m =
                Regex("""(do\s*\(.*?\))|(finish\s*\(.*?\))""", RegexOption.DOT_MATCHES_ALL)
                        .find(trimmed)
        return m?.value?.trim()
    }

    private fun isRetryableModelError(t: Throwable?): Boolean {
        if (t == null) return false
        if (t is CancellationException) return false
        if (t is AutoGlmClient.ApiException) {
            if (t.code == 429) return true
            if (t.code in 500..599) return true
            return false
        }
        return t is IOException
    }

    private fun computeModelRetryDelayMs(attempt: Int): Long {
        val base = config.modelRetryBaseDelayMs.coerceAtLeast(0L)
        val mult = 1L shl attempt.coerceIn(0, 6)
        return (base * mult).coerceAtMost(6000L)
    }

    /**
     * 流式请求模型并支持早停优化
     * 当检测到完整的 do(...) 或 finish(...) 动作时，立即停止读取后续内容
     * 可节省 0.5-2 秒的"尾部等待"时间
     */
    /**
     * 优化的截图获取方法（集成缓存+节流+压缩）
     * 1. 检查节流器，防止频繁截图
     * 2. 检查缓存，避免重复截图
     * 3. 执行截图并压缩优化
     */
    private suspend fun getOptimizedScreenshot(
        service: PhoneAgentAccessibilityService
    ): PhoneAgentAccessibilityService.ScreenshotData? {
        // 检查节流器
        if (config.enableScreenshotThrottle && !screenshotThrottler.canTakeScreenshot()) {
            val remainingWait = screenshotThrottler.getRemainingWaitTime()
            if (remainingWait > 0) {
                // 尝试从缓存获取
                if (config.enableScreenshotCache) {
                    val currentApp = service.currentAppPackage()
                    val windowEventTime = service.lastWindowEventTime()
                    val cacheKey = screenshotCache.generateKey(currentApp, windowEventTime)
                    
                    val cachedScreenshot = screenshotCache.get(cacheKey)
                    if (cachedScreenshot is PhoneAgentAccessibilityService.ScreenshotData) {
                        return cachedScreenshot
                    }
                }
                
                // 无缓存且需要等待，返回null
                return null
            }
        }
        
        // 检查缓存
        if (config.enableScreenshotCache) {
            val currentApp = service.currentAppPackage()
            val windowEventTime = service.lastWindowEventTime()
            val cacheKey = screenshotCache.generateKey(currentApp, windowEventTime)
            
            val cachedScreenshot = screenshotCache.get(cacheKey)
            if (cachedScreenshot is PhoneAgentAccessibilityService.ScreenshotData) {
                return cachedScreenshot
            }
            
            // 缓存未命中，执行截图
            val screenshot = service.tryCaptureScreenshotBase64()
            if (screenshot != null) {
                // 存储到缓存
                screenshotCache.put(cacheKey, screenshot)
                
                // 清理过期缓存
                screenshotCache.evictExpired()
            }
            return screenshot
        } else {
            // 未启用缓存，直接截图
            return service.tryCaptureScreenshotBase64()
        }
    }
    
    /**
     * 清理截图缓存（在任务开始/结束时调用）
     */
    private fun clearScreenshotCache() {
        if (config.enableScreenshotCache) {
            screenshotCache.clear()
        }
        if (config.enableScreenshotThrottle) {
            screenshotThrottler.reset()
        }
    }

    private suspend fun requestModelStreamingWithEarlyStop(
            apiKey: String,
            model: String,
            messages: List<ChatRequestMessage>,
            step: Int,
            onLog: (String) -> Unit,
            onThinkingDelta: (String) -> Unit,
    ): kotlin.Result<String> {
        val thinkingBuilder = StringBuilder()
        val contentBuilder = StringBuilder()
        var hasCompleteAction = false
        var lastThinkingUpdateTime = System.currentTimeMillis()
        val thinkingTimeoutMs = 30_000L // 30秒无新思考内容则超时
        val maxTotalTimeMs = 60_000L // 最多等待60秒
        val startTime = System.currentTimeMillis()
        
        // 检测是否已获取完整动作（用于早停）
        fun checkCompleteAction(text: String): Boolean {
            // 检测完整的 do(...) 或 finish(...)
            val doMatch = Regex("""do\s*\([^)]*\)""").find(text)
            val finishMatch = Regex("""finish\s*\([^)]*\)""").find(text)
            return doMatch != null || finishMatch != null
        }
        
        val result = try {
            withTimeoutOrNull(maxTotalTimeMs) {
                withContext(Dispatchers.IO) {
                    AutoGlmClient.sendChatStreamResult(
                        apiKey = apiKey,
                        messages = messages,
                        model = model,
                        temperature = config.temperature,
                        maxTokens = config.maxTokens,
                        topP = config.topP,
                        frequencyPenalty = config.frequencyPenalty,
                        onReasoningDelta = { delta ->
                            thinkingBuilder.append(delta)
                            lastThinkingUpdateTime = System.currentTimeMillis()
                            onThinkingDelta(delta)
                        },
                        onContentDelta = { delta ->
                            contentBuilder.append(delta)
                            // 检测是否已获取完整动作
                            if (!hasCompleteAction && checkCompleteAction(contentBuilder.toString())) {
                                hasCompleteAction = true
                            }
                        },
                        shouldStop = { 
                            hasCompleteAction || 
                            (System.currentTimeMillis() - lastThinkingUpdateTime > thinkingTimeoutMs)
                        },
                        useFastTimeouts = true,
                    )
                }
            }
        } catch (e: CancellationException) {
            // 若被外部取消，但已经有部分响应，则允许后续使用部分内容
            onLog("[Step $step] 流式请求被取消（可能用户中止），将尝试使用已获取内容")
            null
        } catch (e: Exception) {
            onLog("[Step $step] 流式请求异常：${e.message.orEmpty().take(200)}")
            null
        }
        
        if (result == null) {
            // 超时或取消：若有部分内容则继续，否则失败
            if (contentBuilder.isNotEmpty() || thinkingBuilder.isNotEmpty()) {
                onLog("[Step $step] 模型请求超时/取消，使用已获取的部分内容")
            } else {
                return kotlin.Result.failure(IOException("Streaming timeout/cancel after ${System.currentTimeMillis() - startTime}ms"))
            }
        }

        if (result != null && result.isFailure) {
            return kotlin.Result.failure(result.exceptionOrNull() ?: IOException("Streaming failed"))
        }
        
        // 组合思考和内容
        val fullResponse = buildString {
            if (thinkingBuilder.isNotEmpty()) {
                append("【思考开始】")
                append(thinkingBuilder)
                append("【思考结束】")
            }
            if (contentBuilder.isNotEmpty()) {
                append("【回答开始】")
                append(contentBuilder)
                append("【回答结束】")
            }
        }
        
        return if (fullResponse.isNotBlank()) {
            kotlin.Result.success(fullResponse)
        } else {
            kotlin.Result.failure(IOException("Empty response"))
        }
    }

    private suspend fun requestModelWithRetry(
            apiKey: String,
            model: String,
            messages: List<ChatRequestMessage>,
            step: Int,
            purpose: String,
            onLog: (String) -> Unit,
    ): kotlin.Result<String> {
        val maxAttempts = (config.maxModelRetries + 1).coerceAtLeast(1)
        var lastErr: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            val result =
                    withContext(Dispatchers.IO) {
                        AutoGlmClient.sendChatResult(
                                apiKey = apiKey,
                                messages = messages,
                                model = model,
                                temperature = config.temperature,
                                maxTokens = config.maxTokens,
                                topP = config.topP,
                                frequencyPenalty = config.frequencyPenalty,
                        )
                    }

            if (result.isSuccess) return result

            val err = result.exceptionOrNull()
            if (err is CancellationException) throw err
            lastErr = err

            val retryable = isRetryableModelError(err)
            if (!retryable || attempt >= maxAttempts - 1) break

            val waitMs = computeModelRetryDelayMs(attempt)
            onLog(
                    "[Step $step] $purpose 失败：${err?.message.orEmpty().take(240)}（${attempt + 1}/$maxAttempts），${waitMs}ms 后重试…"
            )
            delay(waitMs)
        }

        return kotlin.Result.failure(lastErr ?: IOException("Unknown model error"))
    }

    private suspend fun parseActionWithRepair(
            apiKey: String,
            model: String,
            history: MutableList<ChatRequestMessage>,
            step: Int,
            answerText: String,
            onLog: (String) -> Unit,
    ): ParsedAgentAction {
        var action = parseAgentAction(extractFirstActionSnippet(answerText) ?: answerText)
        if (action.metadata == "do" || action.metadata == "finish") return action

        var attempt = 0
        while (attempt < config.maxParseRepairs && action.metadata != "do" && action.metadata != "finish") {
            attempt++
            onLog("[Step $step] 输出无法解析为动作，尝试修正（$attempt/${config.maxParseRepairs}）…")

            // 更直接的修复提示，强调只输出动作，不要思考
            val repairMsg = """你的上一条输出格式错误或被截断。请直接输出一个动作，不要输出任何思考内容：

do(action="Tap", element=[x,y])
或 do(action="Type", text="要输入的文字")
或 do(action="Swipe", start=[x1,y1], end=[x2,y2])
或 do(action="Back")
或 finish(message="完成原因")

只输出上述格式之一，不要输出其他任何文字。"""

                // 修复时回到简单模式：只用system + 最后一条user消息 + 修正指令
                val repairHistory = mutableListOf<ChatRequestMessage>()
                history.firstOrNull { it.role == "system" }?.let { repairHistory.add(it) }
                // 只保留最后一条用户消息
                history.lastOrNull { it.role == "user" }?.let { repairHistory.add(it) }
                repairHistory.add(ChatRequestMessage(role = "user", content = repairMsg))

            val repairResult =
                    requestModelWithRetry(
                            apiKey = apiKey,
                            model = model,
                            messages = repairHistory,
                            step = step,
                            purpose = "修正输出",
                            onLog = onLog,
                    )

            val repairFinal = repairResult.getOrNull()?.trim().orEmpty()
            if (repairFinal.isBlank()) {
                val err = repairResult.exceptionOrNull()
                onLog("[Step $step] 修正输出失败：${err?.message.orEmpty().take(240)}")
                continue
            }

            val (_, repairAnswer) = splitThinkingAndAnswer(repairFinal)
            onLog("[Step $step] 修正输出：${repairAnswer.take(220)}")
            // 不再将修复消息加入历史，避免污染上下文
            action = parseAgentAction(extractFirstActionSnippet(repairAnswer) ?: repairAnswer)
        }

        return action
    }

    /**
     * 检测用户任务中是否包含需要打开的应用，如果包含则自动启动（智能应用启动）
     * 支持的模式：
     * - "打开xxx" / "启动xxx" / "进入xxx" / "帮我打开xxx"
     * - "用xxx" / "去xxx" / "切换到xxx"
     * - "open xxx" / "launch xxx" / "start xxx"
     * - 直接在消息中提及应用名称
     * 
     * 此功能会在调用模型前直接执行，加快响应速度，节省API调用
     */
    private suspend fun trySmartAppLaunch(
            task: String,
            service: PhoneAgentAccessibilityService,
            onLog: (String) -> Unit,
    ): Boolean {
        // 检测"打开/启动/进入"等动作词后面的应用名
        // 支持中英文、模糊匹配、多种动作词
        val launchPatterns = listOf(
            // 中文动作词：打开、启动、进入、帮我打开、用、去、切换到、跳转到
            Regex("""(?:打开|启动|进入|帮我打开|用|去|切换到|跳转到|回到)\s*([^\s，。,\.！!？?；;]+?)(?:\s|，|。|,|\.|！|!|？|\?|；|;|$)"""),
            // 英文动作词：open、launch、start、switch to、go to
            Regex("""(?:open|launch|start|switch\s+to|go\s+to)\s+(\S+)""", RegexOption.IGNORE_CASE),
        )
        
        var appMatch: AppPackageMapping.Match? = null
        
        // 首先尝试动作词模式
        for (pattern in launchPatterns) {
            val matchResult = pattern.find(task)
            if (matchResult != null) {
                val potentialApp = matchResult.groupValues.getOrNull(1)?.trim()
                if (!potentialApp.isNullOrBlank()) {
                    val resolved = AppPackageMapping.resolve(potentialApp)
                    if (resolved != null) {
                        appMatch = AppPackageMapping.Match(
                            appLabel = potentialApp,
                            packageName = resolved,
                            start = matchResult.range.first,
                            end = matchResult.range.last
                        )
                        break
                    }
                }
            }
        }
        
        // 如果动作词模式没有找到，尝试直接在任务中查找应用名
        if (appMatch == null) {
            appMatch = AppPackageMapping.bestMatchInText(task)
        }
        
        if (appMatch == null) {
            return false
        }
        
        // 检查当前应用是否已经是目标应用
        val currentApp = service.currentAppPackage()
        if (currentApp == appMatch.packageName) {
            onLog("[⚡快速启动] ${appMatch.appLabel} 已在前台，跳过启动（无需连接模型）")
            return true
        }
        
        // 尝试启动应用
        val pm = service.packageManager
        val intent = pm.getLaunchIntentForPackage(appMatch.packageName)
        if (intent == null) {
            onLog("[⚡快速启动] 未找到 ${appMatch.appLabel}(${appMatch.packageName}) 的启动入口")
            return false
        }
        
        intent.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
            android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION or
            android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        
        try {
            val beforeTime = service.lastWindowEventTime()
            LaunchProxyActivity.launch(service, intent)
            onLog("[⚡快速启动] 后台启动 ${appMatch.appLabel}（无需连接模型，节省时间）")
            // 等待应用启动完成
            service.awaitWindowEvent(beforeTime, timeoutMs = 2200L)
            delay(500) // 额外等待应用界面稳定
            return true
        } catch (e: Exception) {
            onLog("[⚡快速启动] 启动失败: ${e.message}")
            return false
        }
    }

    suspend fun run(
            apiKey: String,
            model: String,
            task: String,
            service: PhoneAgentAccessibilityService,
            control: Control = NoopControl,
            onLog: (String) -> Unit,
    ): Result {
        val metrics = service.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        // ⚡ 智能应用启动：检测"打开XXX"等指令，直接后台启动，无需调用模型API
        // 支持：打开/启动/进入/用/去/切换到 + 应用名（中英文均可）
        // 效果：节省1-2秒模型调用时间，提升用户体验
        val smartLaunched = trySmartAppLaunch(task, service, onLog)
        if (smartLaunched) {
            onLog("✓ 应用已快速启动，继续后续操作...")
        }

        val history = mutableListOf<ChatRequestMessage>()
        history +=
                ChatRequestMessage(role = "system", content = buildSystemPrompt(screenW, screenH))

        // 清除截图缓存和限流器
        clearScreenshotCache()

        var step = 0
        while (step < config.maxSteps) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            awaitIfPaused(control)

            step++
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0f,
                    maxSteps = config.maxSteps,
                    subtitle = "读取界面"
            )
            
            // ⚡ 性能优化：并行获取截图和UI树
            val (screenshot, rawUiDump) = coroutineScope {
                val screenshotDeferred = async { getOptimizedScreenshot(service) }
                val uiDumpDeferred = async { service.dumpUiTreeWithRetry(maxNodes = 30) }
                Pair(screenshotDeferred.await(), uiDumpDeferred.await())
            }
            
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.15f,
                    maxSteps = config.maxSteps,
                    subtitle = "解析界面"
            )
            val uiDump = truncateUiTree(rawUiDump, config.maxUiTreeChars)

            val currentApp = service.currentAppPackage()
            val screenInfo = "{\"current_app\":\"${currentApp.replace("\"", "")}\"}"

            if (screenshot != null) {
                onLog("[Step $step] 截图：${screenshot.width}x${screenshot.height}")
            } else {
                onLog("[Step $step] 截图：不可用（将使用纯文本/无障碍树模式）")
            }

            // 简化用户消息，减少token消耗
            val userMsg =
                    if (step == 1) {
                        "$task\n\n$screenInfo\n\nUI树：\n$uiDump"
                    } else {
                        "$screenInfo\n\nUI树：\n$uiDump"
                    }

            val userContent: Any =
                    if (screenshot != null) {
                        listOf(
                                mapOf(
                                        "type" to "image_url",
                                        "image_url" to
                                                mapOf(
                                                        "url" to
                                                                "data:image/jpeg;base64,${screenshot.base64Png}"
                                                )
                                ),
                                mapOf("type" to "text", "text" to userMsg)
                        )
                    } else {
                        userMsg
                    }
            
            // 在添加新消息前，先裁剪历史以控制上下文大小
            trimHistory(history, config.maxContextTokens, config.maxHistoryTurns)
            
            history += ChatRequestMessage(role = "user", content = userContent)
            val observationUserIndex = history.lastIndex

            onLog("[Step $step] 请求模型…")
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.25f,
                    maxSteps = config.maxSteps,
                    subtitle = "请求模型"
            )
            
            // 启动流式思考显示
            AutomationOverlay.startThinking()
            
            // 【重要】禁用流式：之前的流式实现存在致命缺陷
            // 问题：早停条件导致内容不完整 → 触发回退 → 原流式协程仍继续运行 → 两个请求并行 → 协程状态混乱
            // 解决：直接用稳定的非流式请求，牺牲<1秒时间换取稳定性
            var streamedThinking = ""
            val replyResult = requestModelWithRetry(
                apiKey = apiKey,
                model = model,
                messages = history,
                step = step,
                purpose = "请求模型",
                onLog = onLog,
            )

            val finalReply = replyResult.getOrNull()?.trim().orEmpty()
            
            // 停止流式思考显示
            AutomationOverlay.stopThinking()
            
            if (finalReply.isBlank()) {
                val err = replyResult.exceptionOrNull()
                val msg = err?.message?.trim().orEmpty().ifBlank { "模型无回复或请求失败" }
                return Result(false, "模型请求失败：${msg.take(320)}", step)
            }

            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.55f,
                    maxSteps = config.maxSteps,
                    subtitle = "解析模型输出"
            )

            val (thinking, answer) = splitThinkingAndAnswer(finalReply)
            if (!thinking.isNullOrBlank()) {
                onLog("[Step $step] 思考：${thinking.take(180)}")
                // 从第一步的思考中解析预估步骤数
                if (step == 1) {
                    val estimatedSteps = parseEstimatedSteps(thinking)
                    if (estimatedSteps > 0) {
                        AutomationOverlay.updateEstimatedSteps(estimatedSteps)
                        onLog("[Step $step] 预估总步骤数：$estimatedSteps")
                    }
                }
            }
            onLog("[Step $step] 输出：${answer.take(220)}")

            history += ChatRequestMessage(role = "assistant", content = finalReply)

            val action =
                    parseActionWithRepair(
                            apiKey = apiKey,
                            model = model,
                            history = history,
                            step = step,
                            answerText = answer,
                            onLog = onLog,
                    )
            if (action.metadata == "finish") {
                val msg = action.fields["message"].orEmpty().ifBlank { "已完成" }
                return Result(true, msg, step)
            }
            if (action.metadata != "do") {
                return Result(false, "无法解析动作：${action.raw.take(240)}", step)
            }

            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.68f,
                    maxSteps = config.maxSteps,
                    subtitle = "准备执行"
            )

            var currentAction = action
            var actionName = ""
            var execOk = false
            var repairAttempt = 0

            while (true) {
                actionName =
                        currentAction.actionName
                                ?.trim()
                                ?.trim('"', '\'', ' ')
                                ?.lowercase()
                                .orEmpty()
                
                // 更新悬浮窗显示当前执行的动作
                val displayActionName = getDisplayActionName(actionName, currentAction)
                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.78f,
                        maxSteps = config.maxSteps,
                        subtitle = "执行 $displayActionName"
                )

                if (actionName == "take_over" || actionName == "takeover") {
                    val msg = currentAction.fields["message"].orEmpty().ifBlank { "需要用户接管" }
                    return Result(false, msg, step)
                }

                if (actionName == "note" || actionName == "call_api" || actionName == "interact") {
                    return Result(false, "需要用户交互/扩展能力：${currentAction.raw.take(180)}", step)
                }

                execOk =
                        try {
                            execute(service, currentAction, uiDump, screenW, screenH, control, onLog)
                        } catch (e: TakeOverException) {
                            val msg = e.message.orEmpty().ifBlank { "需要用户接管" }
                            return Result(false, msg, step)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            onLog("[Step $step] 动作执行异常：${e.message.orEmpty().take(240)}")
                            false
                        }

                if (execOk) break

                if (repairAttempt >= config.maxActionRepairs) {
                    return Result(false, "动作执行失败：${currentAction.raw.take(240)}", step)
                }
                repairAttempt++

                onLog("[Step $step] 动作执行失败，尝试让模型修复（$repairAttempt/${config.maxActionRepairs}）…")

                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.62f,
                        maxSteps = config.maxSteps,
                        subtitle = "动作失败，修复中"
                )

                val failMsg =
                        "上一步动作执行失败：${currentAction.raw.take(320)}\n" +
                                "请根据上一条屏幕信息重新给出下一步动作，优先使用 selector（resourceId/elementText/contentDesc/className/index）。\n" +
                                "严格只输出：\n【思考开始】...【思考结束】\n【回答开始】do(...)/finish(...)【回答结束】。"
                history += ChatRequestMessage(role = "user", content = failMsg)

                val fixResult =
                        requestModelWithRetry(
                                apiKey = apiKey,
                                model = model,
                                messages = history,
                                step = step,
                                purpose = "动作修复",
                                onLog = onLog,
                        )
                val fixFinal = fixResult.getOrNull()?.trim().orEmpty()
                if (fixFinal.isBlank()) {
                    val err = fixResult.exceptionOrNull()
                    val msg = err?.message?.trim().orEmpty().ifBlank { "模型无回复或请求失败" }
                    return Result(false, "动作修复失败：${msg.take(320)}", step)
                }

                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.72f,
                        maxSteps = config.maxSteps,
                        subtitle = "解析修复动作"
                )

                val (fixThinking, fixAnswer) = splitThinkingAndAnswer(fixFinal)
                if (!fixThinking.isNullOrBlank()) {
                    onLog("[Step $step] 修复思考：${fixThinking.take(180)}")
                }
                onLog("[Step $step] 修复输出：${fixAnswer.take(220)}")
                history += ChatRequestMessage(role = "assistant", content = fixFinal)

                currentAction =
                        parseActionWithRepair(
                                apiKey = apiKey,
                                model = model,
                                history = history,
                                step = step,
                                answerText = fixAnswer,
                                onLog = onLog,
                        )

                if (currentAction.metadata == "finish") {
                    val msg = currentAction.fields["message"].orEmpty().ifBlank { "已完成" }
                    return Result(true, msg, step)
                }
                if (currentAction.metadata != "do") {
                    return Result(false, "无法解析动作：${currentAction.raw.take(240)}", step)
                }
            }

            val extraDelayMs = getActionDelayMs(actionName)

            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.92f,
                    maxSteps = config.maxSteps,
                    subtitle = "等待界面稳定"
            )

            // 【历史瘦身】执行完动作后，从历史中移除图片数据，只保留文本
            // 参考 Aries AI 的 removeImagesFromLastUserMessage 策略
            if (observationUserIndex in history.indices) {
                val obs = history[observationUserIndex]
                if (obs.content is List<*>) {
                    // 只保留文本部分，移除图片以降低后续请求的 token 压力
                    history[observationUserIndex] =
                            ChatRequestMessage(role = "user", content = userMsg)
                }
            }

            delay((config.stepDelayMs + extraDelayMs).coerceAtLeast(0L))
        }

        return Result(false, "达到最大步数限制（${config.maxSteps}）", config.maxSteps)
    }

    private fun buildSystemPrompt(screenW: Int, screenH: Int): String {
        val today = LocalDate.now()
        val weekNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val formattedDate =
                today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) +
                        " " + weekNames[today.dayOfWeek.ordinal]

        return (
                """
今天的日期是: $formattedDate
你是 Aries AI。
你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。

你必须严格按以下结构输出（否则我的 Android 应用无法正确解析动作）：

【思考开始】
{think}
【思考结束】

【回答开始】
{action}
【回答结束】

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。你在 {action} 中只能输出 do(...) 或 finish(...)，不要输出多余解释文字。

操作指令及其作用如下：
- do(action="Launch", app="xxx")  
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])  
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")  
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")  
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
- do(action="Type_Name", text="xxx")  
    Type_Name是输入人名的操作，基本功能同Type。
- do(action="Interact")  
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])  
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="True")  
    记录当前页面内容以便后续总结。
- do(action="Call_API", instruction="xxx")  
    总结或评论当前页面或已记录的内容。
- do(action="Long Press", element=[x,y])  
    Long Press是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])  
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")  
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="Back")  
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home") 
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")  
    等待页面加载，x为需要等待多少秒。
- finish(message="xxx")  
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。

坐标系统：左上角(0,0) 到 右下角(999,999)，相对屏幕映射；当前屏幕像素：${screenW}x${screenH}。

必须遵循的规则：
0. 当页面出现支付/付款/密码/验证码/银行卡等验证或付款相关内容时，必须输出 do(action="Take_over", message="请你接管完成支付/验证")，不要继续执行 Tap/Type。
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载。
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
19. 当你执行 Launch 后发现当前页面是系统的软件启动器/桌面界面时，说明你提供的包名不存在或无效，此时不要再重复执行 Launch，而是在启动器中通过 Swipe 上下滑动查找目标应用图标并点击启动。
"""
                        .trimIndent()
        )
    }

    private fun splitThinkingAndAnswer(content: String): Pair<String?, String> {
        val full = content.trim()

        val (ariesThinking, ariesAnswer) = extractAriesThinkingAndAnswer(full)
        if (!ariesAnswer.isNullOrBlank()) {
            return ariesThinking to ariesAnswer
        }

        val thinkTag = extractTagContent(full, "think")
        val answerTag = extractTagContent(full, "answer")
        if (answerTag != null) {
            return thinkTag to answerTag
        }

        val finishMarker = "finish(message="
        val doMarker = "do(action="
        val finishIndex = full.indexOf(finishMarker)
        if (finishIndex >= 0) {
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null }
            val action = full.substring(finishIndex).trim()
            return thinking to action
        }
        val doIndex = full.indexOf(doMarker)
        if (doIndex >= 0) {
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null }
            val action = full.substring(doIndex).trim()
            return thinking to action
        }
        return null to full
    }

    private fun extractAriesThinkingAndAnswer(text: String): Pair<String?, String?> {
        val thinkStartTag = "【思考开始】"
        val thinkEndTag = "【思考结束】"
        val answerStartTags = listOf("【回答开始】", "【回答】")
        val answerEndTag = "【回答结束】"

        val thinkStartIdx = text.indexOf(thinkStartTag)
        val thinkEndIdx = text.indexOf(thinkEndTag)

        val answerStartMatch =
                answerStartTags
                        .mapNotNull { tag ->
                            val idx = text.indexOf(tag)
                            if (idx >= 0) idx to tag else null
                        }
                        .minByOrNull { it.first }
        val answerEndIdx = text.indexOf(answerEndTag)

        val thinking: String? =
                if (thinkStartIdx >= 0) {
                    val start = thinkStartIdx + thinkStartTag.length
                    val end =
                            when {
                                thinkEndIdx >= start -> thinkEndIdx
                                answerStartMatch != null && answerStartMatch.first >= start ->
                                        answerStartMatch.first
                                else -> text.length
                            }
                    text.substring(start, end).trim().ifBlank { null }
                } else {
                    null
                }

        val answer: String? =
                if (answerStartMatch != null) {
                    val start = answerStartMatch.first + answerStartMatch.second.length
                    val end = if (answerEndIdx >= start) answerEndIdx else text.length
                    text.substring(start, end).trim()
                } else {
                    null
                }

        return thinking to answer
    }
    
    /**
     * 从模型的"思考"内容中解析预估步骤数
     * 支持的格式：
     * - "我需要：1. xxx 2. xxx 3. xxx" 或 "第1步 第2步 第3步"
     * - "需要N步" / "大约N步" / "共N个步骤"
     */
    private fun parseEstimatedSteps(thinking: String): Int {
        // 尝试匹配明确的步骤数声明
        val explicitPatterns = listOf(
            Regex("""(?:需要|大约|共|总共|预计)\s*(\d+)\s*(?:步|个步骤|个操作)"""),
            Regex("""(\d+)\s*(?:步|个步骤|个操作)(?:完成|即可|就能)"""),
        )
        for (pattern in explicitPatterns) {
            val match = pattern.find(thinking)
            if (match != null) {
                val num = match.groupValues.getOrNull(1)?.toIntOrNull()
                if (num != null && num in 2..20) return num
            }
        }
        
        // 检测"我需要："后面的编号列表（最常见的格式）
        // 例如："我需要：\n1. 修改出发地\n2. 修改目的地\n3. 修改日期\n4. 查询车票\n5. 选择最便宜的"
        val needPattern = Regex("""我需要[：:]\s*([\s\S]*?)(?:首先|然后|接下来|现在|$)""")
        val needMatch = needPattern.find(thinking)
        if (needMatch != null) {
            val needContent = needMatch.groupValues.getOrNull(1).orEmpty()
            // 计算其中的编号数量
            val numbers = Regex("""(\d+)\s*[\.、）\)：:]""")
                .findAll(needContent)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
            if (numbers.isNotEmpty()) {
                val maxStep = numbers.maxOrNull() ?: 0
                if (maxStep in 2..20) return maxStep
            }
        }
        
        // 计算编号列表中的步骤数（1. 2. 3. 或 第1步 第2步）
        val numberedSteps = Regex("""(?:^|\n|\s|，|。|；)(\d+)\s*[\.、）\)：:]|第(\d+)步""")
            .findAll(thinking)
            .mapNotNull { 
                it.groupValues.getOrNull(1)?.toIntOrNull() 
                    ?: it.groupValues.getOrNull(2)?.toIntOrNull() 
            }
            .toList()
        
        if (numberedSteps.isNotEmpty()) {
            val maxStep = numberedSteps.maxOrNull() ?: 0
            if (maxStep in 2..20) return maxStep
        }
        
        // 计算动作动词出现次数作为粗略估计
        val actionKeywords = listOf("点击", "输入", "滑动", "打开", "选择", "返回", "等待", "启动", "查询", "修改")
        val actionCount = actionKeywords.sumOf { keyword -> 
            thinking.split(keyword).size - 1 
        }
        if (actionCount >= 2) {
            return actionCount.coerceIn(2, 15)
        }
        
        return 0 // 无法解析
    }

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()
        
        // 检查是否输出被截断（包含乱码字符或明显不完整）
        val hasTruncationSign = original.contains("\uFFFD") || // 替换字符
            original.endsWith("…") ||
            original.endsWith("...") ||
            (original.contains("我需要") && !original.contains("do(") && !original.contains("finish("))
        
        // 检查是否输出了无效内容（如重复的UI元素描述）
        if (original.contains("text=\"") && original.count { it == '=' } > 10 && 
            !original.contains("do(") && !original.contains("finish(")) {
            // 模型输出了UI元素列表而非动作，返回unknown让修复逻辑处理
            return ParsedAgentAction("unknown", null, emptyMap(), original.take(200))
        }
        
        // 检查是否只有思考内容没有动作（如 "我需要：1. 2. 3. " 被截断）
        if (hasTruncationSign && !original.contains("do(") && !original.contains("finish(")) {
            return ParsedAgentAction("unknown", null, emptyMap(), "输出被截断，未包含动作")
        }
        
        val finishIndex = original.lastIndexOf("finish(")
        val doIndex = original.lastIndexOf("do(")
        val startIndex =
                when {
                    finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex)
                    finishIndex >= 0 -> finishIndex
                    doIndex >= 0 -> doIndex
                    else -> -1
                }
        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original

        if (trimmed.startsWith("finish")) {
            val messageRegex =
                    Regex(
                            """finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""",
                            RegexOption.DOT_MATCHES_ALL
                    )
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction("finish", null, mapOf("message" to message), trimmed)
        }

        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction("unknown", null, emptyMap(), trimmed.take(200))
        }

        // 检查 do( 是否完整（有配对的右括号）
        val openParenIndex = trimmed.indexOf('(')
        if (openParenIndex < 0) {
            return ParsedAgentAction("unknown", null, emptyMap(), "do命令不完整")
        }
        
        // 寻找配对的右括号
        var parenCount = 0
        var closeParenIndex = -1
        for (i in openParenIndex until trimmed.length) {
            when (trimmed[i]) {
                '(' -> parenCount++
                ')' -> {
                    parenCount--
                    if (parenCount == 0) {
                        closeParenIndex = i
                        break
                    }
                }
            }
        }
        
        // 如果没有找到配对的右括号，尝试容错处理
        val inner = if (closeParenIndex > openParenIndex) {
            trimmed.substring(openParenIndex + 1, closeParenIndex).trim()
        } else {
            // 没有右括号，取到末尾并去除可能的尾部垃圾
            trimmed.substring(openParenIndex + 1).trim().trimEnd(')', ',', ' ')
        }
        
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""")
        regex.findAll(inner).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
        }
        
        // 如果解析出了action字段，即使格式不完整也尝试使用
        if (fields.containsKey("action")) {
            return ParsedAgentAction("do", fields["action"], fields, trimmed)
        }

        return ParsedAgentAction("unknown", null, emptyMap(), trimmed.take(200))
    }

    private suspend fun execute(
            service: PhoneAgentAccessibilityService,
            action: ParsedAgentAction,
            uiDump: String,
            screenW: Int,
            screenH: Int,
            control: Control,
            onLog: (String) -> Unit,
    ): Boolean {
        awaitIfPaused(control)

        val beforeWindowEventTime = service.lastWindowEventTime()

        val rawName = action.actionName ?: return false
        val name = rawName.trim().trim('"', '\'', ' ').lowercase()
        val nameKey = name.replace(" ", "")
        return when (nameKey) {
            "launch", "open_app", "start_app" -> {
                val rawTarget =
                        action.fields["package"]
                                ?: action.fields["package_name"] ?: action.fields["pkg"]
                                        ?: action.fields["app"] ?: ""
                val t = rawTarget.trim().trim('"', '\'', ' ')
                if (t.isBlank()) return false
                val pm = service.packageManager

                fun isInstalled(pkgName: String): Boolean {
                    return runCatching {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, 0)
                        true
                    }.getOrDefault(false)
                }

                fun buildLaunchIntent(pkgName: String): Intent? {
                    val direct = pm.getLaunchIntentForPackage(pkgName)
                    if (direct != null) return direct
                    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val ri =
                            runCatching { pm.queryIntentActivities(query, 0) }
                                    .getOrNull()
                                    ?.firstOrNull { it.activityInfo?.packageName == pkgName }
                                    ?: return null
                    val ai = ri.activityInfo ?: return null
                    return Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ai.packageName, ai.name)
                }

                val candidates =
                        buildList {
                                    if (t.contains('.')) add(t)
                                    AppPackageMapping.resolve(t)?.let { add(it) }
                                    resolvePackageByLabel(service, t)?.let { add(it) }
                                    if (!t.contains('.')) add(t)
                                }
                                .distinct()

                var pkgName = candidates.firstOrNull().orEmpty().ifBlank { t }
                var intent: Intent? = null
                for (candidate in candidates) {
                    if (candidate.contains('.') && !isInstalled(candidate)) continue
                    val i = buildLaunchIntent(candidate)
                    if (i != null) {
                        pkgName = candidate
                        intent = i
                        break
                    }
                }

                onLog("执行：Launch($pkgName)")
                if (intent == null) {
                    onLog("Launch 失败：未找到可启动入口：$pkgName（candidates=${candidates.joinToString()}）")
                    throw TakeOverException("暂未在手机中找到$t 应用")
                }
                // 添加标志，并通过透明跳板 Activity 前台拉起，减少系统确认弹窗
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                return try {
                    LaunchProxyActivity.launch(service, intent)
                    service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 2200L)
                    true
                } catch (e: Exception) {
                    onLog("Launch 失败：${e.message.orEmpty()}")
                    false
                }
            }
            "back" -> {
                onLog("执行：Back")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                true
            }
            "home" -> {
                onLog("执行：Home")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1800L)
                true
            }
            "wait", "sleep" -> {
                val raw = action.fields["duration"].orEmpty().trim()
                val d =
                        when {
                            raw.endsWith("ms", ignoreCase = true) ->
                                    raw.dropLast(2).trim().toLongOrNull()
                            raw.endsWith("s", ignoreCase = true) ->
                                    raw.dropLast(1).trim().toLongOrNull()?.times(1000)
                            raw.contains("second", ignoreCase = true) ->
                                    Regex("""(\d+)""")
                                            .find(raw)
                                            ?.groupValues
                                            ?.getOrNull(1)
                                            ?.toLongOrNull()
                                            ?.times(1000)
                            else -> raw.toLongOrNull()
                        }
                                ?: 600L
                onLog("执行：Wait(${d}ms)")
                delay(d.coerceAtLeast(0L))
                true
            }
            "type", "input", "text" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到支付/验证界面关键词，停止并要求用户接管")
                    throw TakeOverException("检测到支付/验证界面，需要用户接管")
                }

                val inputText = action.fields["text"].orEmpty()
                val resourceId = action.fields["resourceId"] ?: action.fields["resource_id"]
                val contentDesc = action.fields["contentDesc"] ?: action.fields["content_desc"]
                val className = action.fields["className"] ?: action.fields["class_name"]
                val elementText =
                        action.fields["elementText"]
                                ?: action.fields["element_text"]
                                        ?: action.fields["targetText"] ?: action.fields["target_text"]
                val index = action.fields["index"]?.trim()?.toIntOrNull() ?: 0
                
                // 如果提供了坐标，先点击该位置激活输入框
                val element = parsePoint(action.fields["element"])
                        ?: parsePoint(action.fields["point"])
                if (element != null) {
                    val x = (element.first / 1000.0f) * screenW
                    val y = (element.second / 1000.0f) * screenH
                    onLog("执行：先点击输入框(${element.first},${element.second})")
                    AutomationOverlay.temporaryHide()
                    delay(50)
                    service.clickAwait(x, y)
                    AutomationOverlay.restoreVisibility()
                    delay(300) // 等待输入框激活
                }

                onLog("执行：Type(${inputText.take(40)})")

                var ok =
                        if (resourceId != null || contentDesc != null || className != null || elementText != null) {
                            service.setTextOnElement(
                                    text = inputText,
                                    resourceId = resourceId,
                                    elementText = elementText,
                                    contentDesc = contentDesc,
                                    className = className,
                                    index = index,
                            )
                        } else {
                            service.setTextOnFocused(inputText)
                        }
                
                // 如果失败，尝试寻找并点击第一个可编辑的输入框
                if (!ok) {
                    onLog("输入失败，尝试查找并激活输入框…")
                    val inputClicked = service.clickFirstEditableElement()
                    if (inputClicked) {
                        delay(300)
                        ok = service.setTextOnFocused(inputText)
                    }
                }

                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1200L)
                ok
            }
            "tap", "click", "press" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到支付/验证界面关键词，停止并要求用户接管")
                    throw TakeOverException("检测到支付/验证界面，需要用户接管")
                }

                val confirmMsg = action.fields["message"].orEmpty().trim()
                if (confirmMsg.isNotBlank()) {
                    onLog("敏感操作需要确认：$confirmMsg")
                    val ok = control.confirm(confirmMsg)
                    if (!ok) {
                        throw TakeOverException(confirmMsg)
                    }
                }


                val resourceId = action.fields["resourceId"] ?: action.fields["resource_id"]
                val contentDesc = action.fields["contentDesc"] ?: action.fields["content_desc"]
                val className = action.fields["className"] ?: action.fields["class_name"]
                val elementText =
                        action.fields["elementText"]
                                ?: action.fields["element_text"]
                                        ?: action.fields["label"]
                val index = action.fields["index"]?.trim()?.toIntOrNull() ?: 0

                val selectorOk =
                        if (resourceId != null || contentDesc != null || className != null || elementText != null) {
                            onLog("执行：Tap(selector)")
                            service.clickElement(
                                    resourceId = resourceId,
                                    text = elementText,
                                    contentDesc = contentDesc,
                                    className = className,
                                    index = index,
                            )
                        } else {
                            false
                        }

                if (selectorOk) {
                    service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                    true
                } else {
                    val element =
                            parsePoint(action.fields["element"])
                                    ?: parsePoint(action.fields["point"])
                                            ?: parsePoint(action.fields["pos"])
                    val xRel =
                            action.fields["x"]?.trim()?.toIntOrNull() ?: element?.first ?: return false
                    val yRel =
                            action.fields["y"]?.trim()?.toIntOrNull() ?: element?.second ?: return false
                    val x = (xRel / 1000.0f) * screenW
                    val y = (yRel / 1000.0f) * screenH
                    onLog("执行：Tap($xRel,$yRel)")
                    // 临时隐藏悬浮窗，防止点击到悬浮窗
                    AutomationOverlay.temporaryHide()
                    delay(50) // 等待悬浮窗隐藏
                    val ok = service.clickAwait(x, y)
                    AutomationOverlay.restoreVisibility()
                    service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                    ok
                }
            }
            "longpress", "long_press" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到支付/验证界面关键词，停止并要求用户接管")
                    return false
                }
                val element = parsePoint(action.fields["element"]) ?: return false
                val x = (element.first / 1000.0f) * screenW
                val y = (element.second / 1000.0f) * screenH
                onLog("执行：Long Press(${element.first},${element.second})")
                // 临时隐藏悬浮窗
                AutomationOverlay.temporaryHide()
                delay(50)
                val ok = service.clickAwait(x, y, durationMs = 520L)
                AutomationOverlay.restoreVisibility()
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                ok
            }
            "doubletap", "double_tap" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到支付/验证界面关键词，停止并要求用户接管")
                    return false
                }
                val element = parsePoint(action.fields["element"]) ?: return false
                val x = (element.first / 1000.0f) * screenW
                val y = (element.second / 1000.0f) * screenH
                onLog("执行：Double Tap(${element.first},${element.second})")
                // 临时隐藏悬浮窗
                AutomationOverlay.temporaryHide()
                delay(50)
                val ok1 = service.clickAwait(x, y, durationMs = 60L)
                delay(90L)
                val ok2 = service.clickAwait(x, y, durationMs = 60L)
                AutomationOverlay.restoreVisibility()
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                ok1 && ok2
            }
            "swipe", "scroll" -> {
                val start = parsePoint(action.fields["start"])
                val end = parsePoint(action.fields["end"])
                val sxRel =
                        action.fields["start_x"]?.trim()?.toIntOrNull()
                                ?: start?.first ?: return false
                val syRel =
                        action.fields["start_y"]?.trim()?.toIntOrNull()
                                ?: start?.second ?: return false
                val exRel =
                        action.fields["end_x"]?.trim()?.toIntOrNull() ?: end?.first ?: return false
                val eyRel =
                        action.fields["end_y"]?.trim()?.toIntOrNull() ?: end?.second ?: return false

                val durRaw = action.fields["duration"].orEmpty().trim()
                val dur =
                        when {
                            durRaw.endsWith("ms", ignoreCase = true) ->
                                    durRaw.dropLast(2).trim().toLongOrNull()
                            durRaw.endsWith("s", ignoreCase = true) ->
                                    durRaw.dropLast(1).trim().toLongOrNull()?.times(1000)
                            else -> durRaw.toLongOrNull()
                        }
                                ?: 320L
                val sx = (sxRel / 1000.0f) * screenW
                val sy = (syRel / 1000.0f) * screenH
                val ex = (exRel / 1000.0f) * screenW
                val ey = (eyRel / 1000.0f) * screenH
                onLog("执行：Swipe($sxRel,$syRel -> $exRel,$eyRel, ${dur}ms)")
                // 临时隐藏悬浮窗
                AutomationOverlay.temporaryHide()
                delay(50)
                val ok = service.swipeAwait(sx, sy, ex, ey, dur)
                AutomationOverlay.restoreVisibility()
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1600L)
                ok
            }
            else -> false
        }
    }

    private suspend fun awaitIfPaused(control: Control) {
        while (control.isPaused()) {
            delay(220L)
        }
    }

    private fun parsePoint(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim().removeSurrounding("[", "]")
        val parts = v.split(',').map { it.trim() }
        if (parts.size < 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return x to y
    }

    private fun resolvePackageByLabel(
            service: PhoneAgentAccessibilityService,
            appName: String
    ): String? {
        val target = appName.trim()
        if (target.isBlank()) return null
        val pm = service.packageManager

        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ris = runCatching { pm.queryIntentActivities(query, 0) }.getOrNull().orEmpty()

        fun labelOf(ri: android.content.pm.ResolveInfo): String {
            return runCatching { ri.loadLabel(pm).toString() }.getOrDefault("")
        }

        val exact = ris.firstOrNull { labelOf(it).equals(target, ignoreCase = true) }
        if (exact?.activityInfo != null) return exact.activityInfo.packageName

        val contains = ris.firstOrNull { labelOf(it).contains(target, ignoreCase = true) }
        if (contains?.activityInfo != null) return contains.activityInfo.packageName

        val pkgContains =
                ris.firstOrNull {
                    it.activityInfo?.packageName?.contains(target, ignoreCase = true) == true
                }
        return pkgContains?.activityInfo?.packageName
    }

        private fun looksSensitive(uiDump: String): Boolean {
        // 降低敏感度：仅在真正付款/验证输入场景触发
        val highRisk = listOf(
            "支付密码", "银行卡", "信用卡", "卡号", "cvv", "安全码",
            "验证码", "短信验证码", "otp", "一次性密码", "动态口令",
            "输入密码", "请输入密码", "确认支付", "确认付款"
        )
        return highRisk.any { uiDump.contains(it, ignoreCase = true) }
        }

    /** 估算文本的token数量（中文约2字符=1token，英文约4字符=1token） */
    private fun estimateTokens(text: String): Int {
        var count = 0
        for (c in text) {
            count += if (c.code > 127) 1 else 1 // 简化：每个字符约0.5-1 token
        }
        return (count * 0.6).toInt().coerceAtLeast(1)
    }

    /** 估算消息列表的总token数 */
    private fun estimateHistoryTokens(history: List<ChatRequestMessage>): Int {
        var total = 0
        for (msg in history) {
            val content = msg.content
            when (content) {
                is String -> total += estimateTokens(content)
                is List<*> -> {
                    for (item in content) {
                        if (item is Map<*, *>) {
                            val type = item["type"]
                            if (type == "text") {
                                val text = item["text"] as? String ?: ""
                                total += estimateTokens(text)
                            } else if (type == "image_url") {
                                total += 1500 // 图片token估算
                            }
                        }
                    }
                }
            }
        }
        return total
    }

    /** 裁剪历史，保留system和最近几轮对话 */
    private fun trimHistory(history: MutableList<ChatRequestMessage>, maxTokens: Int, maxTurns: Int) {
        // 始终保留system消息
        if (history.isEmpty()) return
        val systemMsg = history.firstOrNull { it.role == "system" }
        
        // 移除所有历史消息中的图片，只保留文本
        for (i in history.indices) {
            val msg = history[i]
            if (msg.content is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val content = msg.content as List<Map<String, Any>>
                val textOnly = content.filter { it["type"] == "text" }
                if (textOnly.isNotEmpty()) {
                    history[i] = ChatRequestMessage(role = msg.role, content = textOnly)
                }
            }
        }

        // 如果仍然超出限制，删除最早的对话轮次
        while (history.size > 2 && estimateHistoryTokens(history) > maxTokens) {
            // 找到第一个非system的消息删除
            val removeIndex = history.indexOfFirst { it.role != "system" }
            if (removeIndex >= 0) {
                history.removeAt(removeIndex)
                // 如果下一条是assistant，也删除
                if (removeIndex < history.size && history[removeIndex].role == "assistant") {
                    history.removeAt(removeIndex)
                }
            } else {
                break
            }
        }

        // 限制对话轮次
        var turns = 0
        var i = history.size - 1
        while (i >= 0 && turns < maxTurns * 2) {
            if (history[i].role != "system") turns++
            i--
        }
        // 删除超出的历史
        val keepFrom = (i + 1).coerceAtLeast(if (systemMsg != null) 1 else 0)
        while (history.size > keepFrom + turns) {
            val idx = history.indexOfFirst { it.role != "system" }
            if (idx < 0 || idx >= history.size - turns) break
            history.removeAt(idx)
        }
    }

    /** 截断UI树，只保留关键信息 */
    private fun truncateUiTree(uiDump: String, maxChars: Int): String {
        if (uiDump.length <= maxChars) return uiDump
        
        // 保留开头和结尾
        val headSize = (maxChars * 0.6).toInt()
        val tailSize = maxChars - headSize - 50
        
        return uiDump.take(headSize) + 
               "\n... [UI树已截断，共${uiDump.length}字符] ...\n" + 
               uiDump.takeLast(tailSize.coerceAtLeast(100))
    }
}
