package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UiAutomationAgent(
        private val config: Config = Config(),
) {

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
            val maxSteps: Int = 12,
            val stepDelayMs: Long = 160L,
            val maxModelRetries: Int = 2,
            val modelRetryBaseDelayMs: Long = 700L,
            val maxParseRepairs: Int = 2,
            val maxActionRepairs: Int = 1,
            val temperature: Float? = 0.0f,
            val maxTokens: Int? = 900,
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

            val repairMsg =
                    "你刚才的输出无法被解析为动作。\n" +
                            "请严格只输出：<think>...</think><answer>do(...)</answer> 或 <answer>finish(...)</answer>。\n" +
                            "不要输出任何其它文本。"

            val repairResult =
                    requestModelWithRetry(
                            apiKey = apiKey,
                            model = model,
                            messages =
                                    history +
                                            ChatRequestMessage(
                                                    role = "user",
                                                    content = repairMsg
                                            ),
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
            history += ChatRequestMessage(role = "assistant", content = repairFinal)
            action = parseAgentAction(extractFirstActionSnippet(repairAnswer) ?: repairAnswer)
        }

        return action
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

        val history = mutableListOf<ChatRequestMessage>()
        history +=
                ChatRequestMessage(role = "system", content = buildSystemPrompt(screenW, screenH))

        var step = 0
        while (step < config.maxSteps) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            awaitIfPaused(control)

            step++
            AutomationOverlay.updateStep(step = step, maxSteps = config.maxSteps)
            val uiDump = service.dumpUiTree(maxNodes = 240)

            val currentApp = service.currentAppPackage()
            val screenInfo = "{\"current_app\":\"${currentApp.replace("\"", "")}\"}"

            val screenshot = service.tryCaptureScreenshotBase64()
            if (screenshot != null) {
                onLog("[Step $step] 截图：${screenshot.width}x${screenshot.height}")
            } else {
                onLog("[Step $step] 截图：不可用（将使用纯文本/无障碍树模式）")
            }

            val userMsg =
                    if (step == 1) {
                        "$task\n\n$screenInfo\n\n屏幕信息（UI树摘要）：\n$uiDump"
                    } else {
                        "** Screen Info **\n\n$screenInfo\n\n屏幕信息（UI树摘要）：\n$uiDump"
                    }

            val userContent: Any =
                    if (screenshot != null) {
                        listOf(
                                mapOf(
                                        "type" to "image_url",
                                        "image_url" to
                                                mapOf(
                                                        "url" to
                                                                "data:image/png;base64,${screenshot.base64Png}"
                                                )
                                ),
                                mapOf("type" to "text", "text" to userMsg)
                        )
                    } else {
                        userMsg
                    }
            history += ChatRequestMessage(role = "user", content = userContent)
            val observationUserIndex = history.lastIndex

            onLog("[Step $step] 请求模型…")
            val replyResult =
                    requestModelWithRetry(
                            apiKey = apiKey,
                            model = model,
                            messages = history,
                            step = step,
                            purpose = "请求模型",
                            onLog = onLog,
                    )
            val finalReply = replyResult.getOrNull()?.trim().orEmpty()
            if (finalReply.isBlank()) {
                val err = replyResult.exceptionOrNull()
                val msg = err?.message?.trim().orEmpty().ifBlank { "模型无回复或请求失败" }
                return Result(false, "模型请求失败：${msg.take(320)}", step)
            }

            val (thinking, answer) = splitThinkingAndAnswer(finalReply)
            if (!thinking.isNullOrBlank()) {
                onLog("[Step $step] 思考：${thinking.take(180)}")
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

                val failMsg =
                        "上一步动作执行失败：${currentAction.raw.take(320)}\n" +
                                "请根据上一条屏幕信息重新给出下一步动作，优先使用 selector（resourceId/elementText/contentDesc/className/index）。\n" +
                                "严格只输出：<think>...</think><answer>do(...)</answer> 或 <answer>finish(...)</answer>。"
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

            val extraDelayMs =
                    when (actionName.replace(" ", "").lowercase()) {
                        "launch", "open_app", "start_app" -> 1050L
                        "type", "input", "text" -> 260L
                        "tap", "click", "press", "doubletap", "double_tap" -> 320L
                        "swipe", "scroll" -> 420L
                        else -> 240L
                    }

            if (observationUserIndex in history.indices) {
                val obs = history[observationUserIndex]
                if (obs.content is List<*>) {
                    val textOnly = listOf(mapOf("type" to "text", "text" to userMsg))
                    history[observationUserIndex] =
                            ChatRequestMessage(role = "user", content = textOnly)
                }
            }

            delay((config.stepDelayMs + extraDelayMs).coerceAtLeast(0L))
        }

        return Result(false, "达到最大步数限制（${config.maxSteps}）", config.maxSteps)
    }

    private fun buildSystemPrompt(screenW: Int, screenH: Int): String {
        return ("今天的日期是: (以系统为准)\n" +
                "你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。\n" +
                "你必须严格输出：<think>{think}</think><answer>{action}</answer>。\n" +
                "其中 {action} 必须是以下之一：\n" +
                "- do(action=\"Launch\", app=\"xxx\")\n" +
                "- do(action=\"Tap\", element=[x,y])\n" +
                "- do(action=\"Tap\", element=[x,y], message=\"重要操作\")\n" +
                "- do(action=\"Tap\", resourceId=\"id_suffix\", elementText=\"文本\", contentDesc=\"描述\", className=\"Button\", index=0)\n" +
                "- do(action=\"Swipe\", start=[x1,y1], end=[x2,y2], duration=\"320ms\")\n" +
                "- do(action=\"Type\", text=\"xxx\")\n" +
                "- do(action=\"Type\", text=\"xxx\", resourceId=\"id_suffix\", elementText=\"提示/当前文本\", contentDesc=\"描述\", className=\"EditText\", index=0)\n" +
                "- do(action=\"Back\") / do(action=\"Home\")\n" +
                "- do(action=\"Wait\", duration=\"1 seconds\")\n" +
                "- finish(message=\"xxx\")\n" +
                "坐标系统：左上角(0,0) 到 右下角(999,999)，相对屏幕映射。屏幕：${screenW}x${screenH}。\n" +
                "规则：每次只输出一步操作；遇到登录/验证码/支付/删除等敏感场景请输出 do(action=\"Take_over\", message=\"...\") 或 finish(message=\"需要用户接管确认\")。\n" +
                "不要输出其它解释文本。")
    }

    private fun splitThinkingAndAnswer(content: String): Pair<String?, String> {
        val full = content.trim()

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

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()
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
            return ParsedAgentAction("unknown", null, emptyMap(), trimmed)
        }

        val inner = trimmed.removePrefix("do").trim().removeSurrounding("(", ")")
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""")
        regex.findAll(inner).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
        }

        return ParsedAgentAction("do", fields["action"], fields, trimmed)
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
                    return false
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    service.startActivity(intent)
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
                    onLog("检测到敏感界面关键词，停止并要求用户接管")
                    throw TakeOverException("检测到敏感界面关键词，需要用户接管")
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

                onLog("执行：Type(${inputText.take(40)})")

                val ok =
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

                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1200L)
                ok
            }
            "tap", "click", "press" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到敏感界面关键词，停止并要求用户接管")
                    throw TakeOverException("检测到敏感界面关键词，需要用户接管")
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
                    val ok = service.clickAwait(x, y)
                    service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                    ok
                }
            }
            "longpress", "long_press" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到敏感界面关键词，停止并要求用户接管")
                    return false
                }
                val element = parsePoint(action.fields["element"]) ?: return false
                val x = (element.first / 1000.0f) * screenW
                val y = (element.second / 1000.0f) * screenH
                onLog("执行：Long Press(${element.first},${element.second})")
                val ok = service.clickAwait(x, y, durationMs = 520L)
                service.awaitWindowEvent(beforeWindowEventTime, timeoutMs = 1400L)
                ok
            }
            "doubletap", "double_tap" -> {
                if (looksSensitive(uiDump)) {
                    onLog("检测到敏感界面关键词，停止并要求用户接管")
                    return false
                }
                val element = parsePoint(action.fields["element"]) ?: return false
                val x = (element.first / 1000.0f) * screenW
                val y = (element.second / 1000.0f) * screenH
                onLog("执行：Double Tap(${element.first},${element.second})")
                val ok1 = service.clickAwait(x, y, durationMs = 60L)
                delay(90L)
                val ok2 = service.clickAwait(x, y, durationMs = 60L)
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
                val ok = service.swipeAwait(sx, sy, ex, ey, dur)
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
        val danger =
                listOf(
                        "支付",
                        "付款",
                        "转账",
                        "购买",
                        "下单",
                        "确认订单",
                        "删除",
                        "移除",
                        "清空",
                        "卸载",
                        "submit",
                        "pay",
                        "purchase",
                        "delete",
                        "remove"
                )
        return danger.any { uiDump.contains(it, ignoreCase = true) }
    }
}
