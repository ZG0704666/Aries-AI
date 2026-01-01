package com.ai.assistance.operit.core.tools.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.FileProvider
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.UIAutomationProgressOverlay
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Configuration for the PhoneAgent. */
data class AgentConfig(
    val maxSteps: Int = 20
)

/** Result of a single agent step. */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: ParsedAgentAction?,
    val thinking: String?,
    val message: String? = null
)

/** Parsed action from the model's response. */
data class ParsedAgentAction(
    val metadata: String,
    val actionName: String?,
    val fields: Map<String, String>
)

/**
 * AI-powered agent for automating Android phone interactions.
 *
 * The agent uses a vision-language model to understand screen content
 * and decide on actions to complete user tasks.
 */
class PhoneAgent(
    private val context: Context,
    private val config: AgentConfig,
    private val uiService: AIService,
    private val actionHandler: ActionHandler,
    val agentId: String = "default",
    private val cleanupOnFinish: Boolean = (agentId != "default"),
) {
    private var _stepCount = 0
    val stepCount: Int
        get() = _stepCount

    private val _contextHistory = mutableListOf<Pair<String, String>>()
    val contextHistory: List<Pair<String, String>>
        get() = _contextHistory.toList()

    private var pauseFlow: StateFlow<Boolean>? = null

    init {
        actionHandler.setAgentId(agentId)
    }

    private suspend fun awaitIfPaused() {
        val flow = pauseFlow ?: return
        if (!flow.value) {
            return
        }
        AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: entering pause loop, delay starting")
        try {
            while (flow.value) {
                delay(200)
            }
        } finally {
            AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: exiting pause loop")
        }
    }


    /**
     * Run the agent to complete a task.
     *
     * @param task Natural language description of the task.
     * @param systemPrompt System prompt for the UI automation agent.
     * @param onStep Optional callback invoked after each step with the StepResult.
     * @return Final message from the agent.
     */
    suspend fun run(
        task: String,
        systemPrompt: String,
        onStep: (suspend (StepResult) -> Unit)? = null,
        isPausedFlow: StateFlow<Boolean>? = null
    ): String {
        val floatingService = FloatingChatService.getInstance()
        val job = currentCoroutineContext()[Job]

        if (job != null) {
            PhoneAgentJobRegistry.register(agentId, job)
        } else {
            AppLogger.w("PhoneAgent", "[$agentId] run: no Job in coroutineContext, registry disabled")
        }

        val hasShowerDisplayAtStart = try {
            ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] Error checking Shower virtual display state", e)
            false
        }

        var useShowerUi = hasShowerDisplayAtStart
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        var showerOverlay: VirtualDisplayOverlay? = if (useShowerUi) try {
            VirtualDisplayOverlay.getInstance(context, agentId)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance", e)
            null
        } else null

        val pausedMutable = isPausedFlow as? MutableStateFlow<Boolean>

        try {
            // Setup UI for agent run: hide window, then choose indicator based on whether Shower virtual display is active
            floatingService?.setFloatingWindowVisible(false)
            if (useShowerUi) {
                useShowerIndicatorForAgent(context, agentId)
            } else {
                useFullscreenStatusIndicatorForAgent()
            }
            if (useShowerUi) {
                showerOverlay?.showAutomationControls(
                    totalSteps = config.maxSteps,
                    initialStatus = "思考中...",
                    onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                    onExit = {
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                        job?.cancel(CancellationException("User cancelled UI automation"))
                    }
                )
            } else {
                progressOverlay.show(
                    config.maxSteps,
                    "Thinking...",
                    onCancel = {
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                        job?.cancel(CancellationException("User cancelled UI automation"))
                    },
                    onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                )
            }

            reset()
            _contextHistory.add("system" to systemPrompt)
            pauseFlow = isPausedFlow

            // First step with user prompt
            AppLogger.d("PhoneAgent", "[$agentId] run: starting first step for task='$task', hasShowerDisplayAtStart=$hasShowerDisplayAtStart")
            awaitIfPaused()
            var result = _executeStep(task, isFirst = true)
            val firstAction = result.action
            val firstStatusText = when {
                result.finished -> result.message ?: "已完成"
                firstAction != null && firstAction.metadata == "do" -> {
                    val actionName = firstAction.actionName ?: ""
                    if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..."
                }
                else -> "思考中..."
            }

            if (!useShowerUi) {
                val hasShowerNow = try {
                    ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null
                } catch (e: Exception) {
                    AppLogger.e("PhoneAgent", "[$agentId] Error re-checking Shower virtual display state after first step", e)
                    false
                }

                if (hasShowerNow) {
                    useShowerUi = true
                    try {
                        progressOverlay.hide()
                    } catch (_: Exception) {
                    }

                    try {
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId)
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance when switching (first step)", e)
                        showerOverlay = null
                    }

                    if (showerOverlay != null) {
                        useShowerIndicatorForAgent(context, agentId)
                        showerOverlay?.showAutomationControls(
                            totalSteps = config.maxSteps,
                            initialStatus = firstStatusText,
                            onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                            onExit = {
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                job?.cancel(CancellationException("User cancelled UI automation"))
                            }
                        )
                        showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
                    } else {
                        progressOverlay.show(
                            config.maxSteps,
                            "Thinking...",
                            onCancel = {
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                job?.cancel(CancellationException("User cancelled UI automation"))
                            },
                            onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                        )
                        progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                        useShowerUi = false
                    }
                } else {
                    progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                }
            } else {
                showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
            }

            onStep?.invoke(result)

            if (result.finished) {
                return result.message ?: "Task completed"
            }

            // Continue until finished or max steps reached
            while (_stepCount < config.maxSteps) {
                awaitIfPaused()
                result = _executeStep(null, isFirst = false)
                val action = result.action
                val statusText = when {
                    result.finished -> result.message ?: "已完成"
                    action != null && action.metadata == "do" -> {
                        val actionName = action.actionName ?: ""
                        if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..."
                    }
                    else -> "思考中..."
                }

                if (!useShowerUi) {
                    val hasShowerNow = try {
                        ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "[$agentId] Error re-checking Shower state in loop", e)
                        false
                    }

                    if (hasShowerNow) {
                        useShowerUi = true
                        progressOverlay.hide()
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId)
                        if (showerOverlay != null) {
                            useShowerIndicatorForAgent(context, agentId)
                            showerOverlay?.showAutomationControls(
                                totalSteps = config.maxSteps,
                                initialStatus = statusText,
                                onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                                onExit = {
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                    job?.cancel(CancellationException("User cancelled UI automation"))
                                }
                            )
                            showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                        } else {
                            progressOverlay.show(
                                config.maxSteps,
                                "Thinking...",
                                onCancel = {
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                    job?.cancel(CancellationException("User cancelled UI automation"))
                                },
                                onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                            )
                            progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                            useShowerUi = false
                        }
                    } else {
                        progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                    }
                } else {
                    showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                }

                onStep?.invoke(result)

                if (result.finished) {
                    return result.message ?: "Task completed"
                }
            }

            return "Max steps reached"
        } finally {
            AppLogger.d("PhoneAgent", "[$agentId] run: finishing, restoring UI")
            pauseFlow = null
            floatingService?.setFloatingWindowVisible(true)
            clearAgentIndicators(context, agentId)
            if (useShowerUi) {
                showerOverlay?.hideAutomationControls()
            } else {
                progressOverlay.hide()
            }
            if (cleanupOnFinish) {
                AppLogger.d("PhoneAgent", "[$agentId] run: cleaning up agent session")
                try {
                    VirtualDisplayOverlay.hide(agentId)
                } catch (_: Exception) {
                }
                try {
                    ShowerController.shutdown(agentId)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Reset the agent state for a new task. */
    fun reset() {
        _contextHistory.clear()
        _stepCount = 0
    }

    /** Execute a single step of the agent loop. */
    private suspend fun _executeStep(userPrompt: String?, isFirst: Boolean): StepResult {
        _stepCount++
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: begin, step=$_stepCount")

        val screenshotLink = actionHandler.captureScreenshotForAgent()
        val screenInfo = buildString {
            if (screenshotLink != null) {
                appendLine("[SCREENSHOT] Below is the latest screen image:")
                appendLine(screenshotLink)
            } else {
                appendLine("No screenshot available for this step.")
            }
        }.trim()

        val userMessage = if (isFirst) {
            "$userPrompt\n\n$screenInfo"
        } else {
            "** Screen Info **\n\n$screenInfo"
        }

        _contextHistory.add("user" to userMessage)

        val responseStream = uiService.sendMessage(
            message = userMessage,
            chatHistory = _contextHistory.toList(),
            enableThinking = false,
            stream = true,
            preserveThinkInHistory = true
        )

        val contentBuilder = StringBuilder()
        responseStream.collect { chunk -> contentBuilder.append(chunk) }
        val fullResponse = contentBuilder.toString().trim()
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: AI response collected, length=${fullResponse.length}")

        val (thinking, answer) = parseThinkingAndAction(fullResponse)
        val historyEntry = "<think>$thinking</think><answer>$answer</answer>"
        _contextHistory.add("assistant" to historyEntry)

        val parsedAction = parseAgentAction(answer)
        actionHandler.removeImagesFromLastUserMessage(_contextHistory)

        if (parsedAction.metadata == "finish") {
            val message = parsedAction.fields["message"] ?: "Task finished."
            return StepResult(success = true, finished = true, action = parsedAction, thinking = thinking, message = message)
        }

        if (parsedAction.metadata == "do") {
            awaitIfPaused()
            val execResult = actionHandler.executeAgentAction(parsedAction)
            if (execResult.shouldFinish) {
                 return StepResult(success = execResult.success, finished = true, action = parsedAction, thinking = thinking, message = execResult.message)
            }
            return StepResult(success = execResult.success, finished = false, action = parsedAction, thinking = thinking, message = execResult.message)
        }

        val errorMessage = "Unknown action format: ${parsedAction.metadata}"
        return StepResult(success = false, finished = true, action = parsedAction, thinking = thinking, message = errorMessage)
    }

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseThinkingAndAction(content: String): Pair<String?, String> {
        val full = content.trim()
        val finishMarker = "finish(message="
        val finishIndex = full.indexOf(finishMarker)
        if (finishIndex >= 0) {
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null }
            val action = full.substring(finishIndex).trim()
            return thinking to action
        }
        val doMarker = "do(action="
        val doIndex = full.indexOf(doMarker)
        if (doIndex >= 0) {
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null }
            val action = full.substring(doIndex).trim()
            return thinking to action
        }
        val thinkTag = extractTagContent(full, "think")
        val answerTag = extractTagContent(full, "answer")
        if (thinkTag != null || answerTag != null) {
            return thinkTag to (answerTag ?: full)
        }
        return null to full
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()
        val finishIndex = original.lastIndexOf("finish(")
        val doIndex = original.lastIndexOf("do(")
        val startIndex = when {
            finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex)
            finishIndex >= 0 -> finishIndex
            doIndex >= 0 -> doIndex
            else -> -1
        }

        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original

        if (trimmed.startsWith("finish")) {
            val messageRegex = Regex("""finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""", RegexOption.DOT_MATCHES_ALL)
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction(metadata = "finish", actionName = null, fields = mapOf("message" to message))
        }

        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap())
        }

        val inner = trimmed.removePrefix("do").trim().removeSurrounding("(", ")")
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""")
        regex.findAll(inner).forEach { matchResult ->
            val key = matchResult.groupValues[1]
            val value = matchResult.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
        }

        return ParsedAgentAction(metadata = "do", actionName = fields["action"], fields = fields)
    }
}

private suspend fun useFullscreenStatusIndicatorForAgent() {
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(true)
}

private suspend fun useShowerIndicatorForAgent(context: Context, agentId: String) {
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId)
        overlay.setShowerBorderVisible(true)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "[$agentId] Error enabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

private suspend fun clearAgentIndicators(context: Context, agentId: String) {
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId)
        overlay.setShowerBorderVisible(false)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "[$agentId] Error disabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

/** Handles the execution of parsed actions. */
class ActionHandler(
    private val context: Context,
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val toolImplementations: ToolImplementations
) {
    private var agentId: String = "default"

    fun setAgentId(id: String) {
        agentId = id
    }

    data class ActionExecResult(
        val success: Boolean,
        val shouldFinish: Boolean,
        val message: String?
    )

    companion object {
        private const val POST_LAUNCH_DELAY_MS = 1000L
        private const val POST_NON_WAIT_ACTION_DELAY_MS = 500L
    }

    private data class ShowerUsageContext(
        val isAdbOrHigher: Boolean,
        val showerDisplayId: Int?
    ) {
        val hasShowerDisplay: Boolean get() = showerDisplayId != null
        val canUseShowerForInput: Boolean get() = isAdbOrHigher && showerDisplayId != null
    }

    private fun resolveShowerUsageContext(): ShowerUsageContext {
        val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
        var isAdbOrHigher = when (level) {
            AndroidPermissionLevel.DEBUGGER,
            AndroidPermissionLevel.ADMIN,
            AndroidPermissionLevel.ROOT -> true
            else -> false
        }

        if (isAdbOrHigher) {
            val experimentalEnabled = try {
                DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
            } catch (e: Exception) {
                AppLogger.e("ActionHandler", "[$agentId] Error reading experimental virtual display flag", e)
                true
            }
            if (!experimentalEnabled) {
                isAdbOrHigher = false
            }
        }
        val showerId = try {
            ShowerController.getDisplayId(agentId)
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Error getting Shower display id", e)
            null
        }
        return ShowerUsageContext(isAdbOrHigher = isAdbOrHigher, showerDisplayId = showerId)
    }

    suspend fun captureScreenshotForAgent(): String? {
        val showerCtx = resolveShowerUsageContext()
        val floatingService = FloatingChatService.getInstance()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)

        var screenshotLink: String? = null
        var dimensions: Pair<Int, Int>? = null

        if (showerCtx.canUseShowerForInput) {
            val (link, dims) = captureScreenshotViaShower()
            screenshotLink = link
            dimensions = dims
        }

        if (screenshotLink == null) {
            try {
                floatingService?.setStatusIndicatorVisible(false)
                progressOverlay.setOverlayVisible(false)
                delay(200)

                val screenshotTool = buildScreenshotTool()
                val (filePath, fallbackDims) = toolImplementations.captureScreenshot(screenshotTool)
                
                if (filePath != null) {
                    val bitmap = BitmapFactory.decodeFile(filePath)
                    if (bitmap != null) {
                        val (compressedLink, _) = saveCompressedScreenshotFromBitmap(bitmap)
                        screenshotLink = compressedLink
                        dimensions = fallbackDims
                        bitmap.recycle()
                    }
                }
            } finally {
                val hasShowerDisplayNow = try {
                    ShowerController.getDisplayId(agentId) != null
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "[$agentId] Error checking Shower display state in finally", e)
                    false
                }
                if (!hasShowerDisplayNow) {
                    floatingService?.setStatusIndicatorVisible(true)
                }
                progressOverlay.setOverlayVisible(true)
            }
        }

        if (dimensions != null) {
            screenWidth = dimensions.first
            screenHeight = dimensions.second
        }
        return screenshotLink
    }

    private fun buildScreenshotTool(): AITool {
        return AITool(
            name = "capture_screenshot",
            parameters = emptyList()
        )
    }

    private suspend fun captureScreenshotViaShower(): Pair<String?, Pair<Int, Int>?> {
        return try {
            val pngBytes = VirtualDisplayOverlay.getInstance(context, agentId).captureCurrentFramePng()
            if (pngBytes == null || pngBytes.isEmpty()) {
                AppLogger.w("ActionHandler", "[$agentId] Shower WS screenshot returned no data")
                Pair(null, null)
            } else {
                val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                if (bitmap == null) {
                    AppLogger.e("ActionHandler", "[$agentId] Shower screenshot: failed to decode bytes")
                    Pair(null, null)
                } else {
                    val result = saveCompressedScreenshotFromBitmap(bitmap)
                    bitmap.recycle()
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ActionHandler", "[$agentId] Shower screenshot failed", e)
            Pair(null, null)
        }
    }

    private fun saveCompressedScreenshotFromBitmap(bitmap: Bitmap): Pair<String?, Pair<Int, Int>?> {
        return try {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            val prefs = DisplayPreferencesManager.getInstance(context)
            val format = prefs.getScreenshotFormat().uppercase(Locale.getDefault())
            val quality = prefs.getScreenshotQuality().coerceIn(50, 100)
            val scalePercent = prefs.getScreenshotScalePercent().coerceIn(50, 100)

            val screenshotDir = File("/sdcard/Download/Operit/cleanOnExit")
            if (!screenshotDir.exists()) screenshotDir.mkdirs()

            val shortName = System.currentTimeMillis().toString().takeLast(4)
            val (compressFormat, fileExt, effectiveQuality) = when (format) {
                "JPG", "JPEG" -> Triple(Bitmap.CompressFormat.JPEG, "jpg", quality)
                else -> Triple(Bitmap.CompressFormat.PNG, "png", 100)
            }

            val scaleFactor = scalePercent / 100.0
            val bitmapForSave = if (scaleFactor in 0.0..0.999) {
                val newWidth = (originalWidth * scaleFactor).toInt().coerceAtLeast(1)
                val newHeight = (originalHeight * scaleFactor).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val file = File(screenshotDir, "$shortName.$fileExt")
            try {
                FileOutputStream(file).use { outputStream ->
                    bitmapForSave.compress(compressFormat, effectiveQuality, outputStream)
                }
            } finally {
                if (bitmapForSave !== bitmap) bitmapForSave.recycle()
            }

            val imageId = ImagePoolManager.addImage(file.absolutePath)
            if (imageId == "error") {
                Pair(null, null)
            } else {
                Pair("<link type=\"image\" id=\"$imageId\"></link>", Pair(originalWidth, originalHeight))
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Error saving compressed screenshot", e)
            Pair(null, null)
        }
    }

    fun removeImagesFromLastUserMessage(history: MutableList<Pair<String, String>>) {
        val lastUserMessageIndex = history.indexOfLast { it.first == "user" }
        if (lastUserMessageIndex != -1) {
            val (role, content) = history[lastUserMessageIndex]
            if (content.contains("<link type=\"image\"")) {
                val stripped = content.replace(Regex("""<link type=\"image\".*?</link>"""), "").trim()
                history[lastUserMessageIndex] = role to stripped
            }
        }
    }

    suspend fun executeAgentAction(parsed: ParsedAgentAction): ActionExecResult {
        val actionName = parsed.actionName ?: return fail(message = "Missing action name")
        val fields = parsed.fields

        val showerCtx = resolveShowerUsageContext()
        return when (actionName) {
            "Launch" -> {
                val app = fields["app"]?.takeIf { it.isNotBlank() } ?: return fail(message = "No app name specified for Launch")
                val packageName = resolveAppPackageName(app)
                try {
                    val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
                        ?: AndroidPermissionLevel.STANDARD
                    val experimentalEnabled = try {
                        DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
                    } catch (e: Exception) { true }

                    if (preferredLevel == AndroidPermissionLevel.DEBUGGER && experimentalEnabled) {
                        val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
                        val hasShizukuPermission = if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false
                        if (!isShizukuRunning || !hasShizukuPermission) {
                            return fail(shouldFinish = true, message = "Shizuku 不可用，无法启动虚拟屏幕。")
                        }
                    }

                    if (showerCtx.isAdbOrHigher) {
                        val pm = context.packageManager
                        val hasLaunchableTarget = pm.getLaunchIntentForPackage(packageName) != null
                        ensureVirtualDisplayIfAdbOrHigher()

                        val metrics = context.resources.displayMetrics
                        val width = metrics.widthPixels
                        val height = metrics.heightPixels
                        val dpi = metrics.densityDpi
                        val bitrateKbps = try {
                            DisplayPreferencesManager.getInstance(context).getVirtualDisplayBitrateKbps()
                        } catch (e: Exception) { 3000 }

                        val created = ShowerController.ensureDisplay(agentId, context, width, height, dpi, bitrateKbps = bitrateKbps)
                        val launched = if (created && hasLaunchableTarget) ShowerController.launchApp(agentId, packageName) else false

                        if (created && launched) {
                            try {
                                VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(packageName)
                            } catch (_: Exception) {}
                            useShowerIndicatorForAgent(context, agentId)
                            delay(POST_LAUNCH_DELAY_MS)
                            ok()
                        } else {
                            val desktopPackage = "com.ai.assistance.operit.desktop"
                            val desktopLaunched = ShowerController.launchApp(agentId, desktopPackage)
                            if (desktopLaunched) {
                                try {
                                    VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(desktopPackage)
                                } catch (_: Exception) {}
                                useShowerIndicatorForAgent(context, agentId)
                                delay(POST_LAUNCH_DELAY_MS)
                                ok()
                            } else {
                                fail(message = "Failed to launch on Shower virtual display")
                            }
                        }
                    } else {
                        val systemTools = ToolGetter.getSystemOperationTools(context)
                        val result = systemTools.startApp(AITool("start_app", listOf(ToolParameter("package_name", packageName))))
                        if (result.success) {
                            delay(POST_LAUNCH_DELAY_MS)
                            ok()
                        } else {
                            fail(message = result.error ?: "Failed to launch app: $packageName")
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    fail(message = "Exception while launching app: ${e.message}")
                }
            }
            "Tap" -> {
                val element = fields["element"] ?: return fail(message = "No element for Tap")
                val (x, y) = parseRelativePoint(element) ?: return fail(message = "Invalid coordinates for Tap: $element")
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okTap = ShowerController.tap(agentId, x, y)
                        if (okTap) ok() else fail(message = "Shower TAP failed at ($x,$y)")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("x", x.toString()), ToolParameter("y", y.toString())))
                        val result = toolImplementations.tap(AITool("tap", params))
                        if (result.success) ok() else fail(message = result.error ?: "Tap failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Type" -> {
                val text = fields["text"] ?: ""
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        try {
                            val cleared = ShowerController.key(agentId, KeyEvent.KEYCODE_CLEAR)
                            if (!cleared) return@withAgentUiHiddenForAction fail(message = "Shower CLEAR failed")
                            delay(300)
                            if (text.isEmpty()) return@withAgentUiHiddenForAction ok()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                ?: return@withAgentUiHiddenForAction fail(message = "Clipboard unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text))
                            delay(100)
                            val pasted = ShowerController.key(agentId, KeyEvent.KEYCODE_PASTE)
                            if (pasted) ok() else fail(message = "Shower PASTE failed")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            fail(message = "Error typing via Shower: ${e.message}")
                        }
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("text", text)))
                        val result = toolImplementations.setInputText(AITool("set_input_text", params))
                        if (result.success) ok() else fail(message = result.error ?: "Type failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Swipe" -> {
                val start = fields["start"] ?: return fail(message = "Missing swipe start")
                val end = fields["end"] ?: return fail(message = "Missing swipe end")
                val (sx, sy) = parseRelativePoint(start) ?: return fail(message = "Invalid swipe start")
                val (ex, ey) = parseRelativePoint(end) ?: return fail(message = "Invalid swipe end")
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okSwipe = ShowerController.swipe(agentId, sx, sy, ex, ey)
                        if (okSwipe) ok() else fail(message = "Shower SWIPE failed")
                    } else {
                        val params = withDisplayParam(listOf(
                            ToolParameter("start_x", sx.toString()), ToolParameter("start_y", sy.toString()),
                            ToolParameter("end_x", ex.toString()), ToolParameter("end_y", ey.toString())
                        ))
                        val result = toolImplementations.swipe(AITool("swipe", params))
                        if (result.success) ok() else fail(message = result.error ?: "Swipe failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Back" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_BACK)
                        if (okKey) ok() else fail(message = "Shower BACK failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_BACK")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Back failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Home" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_HOME)
                        if (okKey) ok() else fail(message = "Shower HOME failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_HOME")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Home failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Wait" -> {
                val seconds = fields["duration"]?.replace("seconds", "")?.trim()?.toDoubleOrNull() ?: 1.0
                delay((seconds * 1000).toLong().coerceAtLeast(0L))
                ok()
            }
            "Take_over" -> ok(shouldFinish = true, message = fields["message"] ?: "User takeover required")
            else -> fail(message = "Unknown action: $actionName")
        }
    }

    private suspend fun withAgentUiHiddenForAction(
        showerCtx: ShowerUsageContext,
        block: suspend () -> ActionExecResult
    ): ActionExecResult {
        if (showerCtx.canUseShowerForInput) return block()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        try {
            progressOverlay.setOverlayVisible(false)
            delay(200)
            return block()
        } finally {
            progressOverlay.setOverlayVisible(true)
        }
    }

    private suspend fun ensureVirtualDisplayIfAdbOrHigher() {
        try {
            val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
            val isAdbOrHigher = when (level) {
                AndroidPermissionLevel.DEBUGGER, AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.ROOT -> true
                else -> false
            }
            if (!isAdbOrHigher) return

            val ok = ShowerServerManager.ensureServerStarted(context)
            if (ok) {
                try {
                    VirtualDisplayOverlay.getInstance(context, agentId).show(0)
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "[$agentId] Error showing Shower overlay", e)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ActionHandler", "[$agentId] Error ensuring Shower", e)
        }
    }

    private fun withDisplayParam(params: List<ToolParameter>): List<ToolParameter> {
        return try {
            val showerId = ShowerController.getDisplayId(agentId)
            if (showerId != null) {
                params + ToolParameter("display", showerId.toString())
            } else {
                val id = VirtualDisplayManager.getInstance(context).getDisplayId()
                if (id != null) params + ToolParameter("display", id.toString()) else params
            }
        } catch (e: Exception) {
            params
        }
    }

    private fun ok(shouldFinish: Boolean = false, message: String? = null) = ActionExecResult(true, shouldFinish, message)
    private fun fail(shouldFinish: Boolean = false, message: String) = ActionExecResult(false, shouldFinish, message)

    private fun parseRelativePoint(value: String): Pair<Int, Int>? {
        val parts = value.trim().removeSurrounding("[", "]").split(",").map { it.trim() }
        if (parts.size < 2) return null
        val relX = parts[0].toIntOrNull() ?: return null
        val relY = parts[1].toIntOrNull() ?: return null
        return (relX / 1000.0 * screenWidth).toInt() to (relY / 1000.0 * screenHeight).toInt()
    }

    private suspend fun resolveAppPackageName(app: String): String {
        val trimmed = app.trim()
        val lowered = trimmed.lowercase(Locale.getDefault())
        fun lookup(): String? = StandardUITools.APP_PACKAGES[app] ?: StandardUITools.APP_PACKAGES[trimmed] ?: StandardUITools.APP_PACKAGES[lowered]
        val directHit = lookup()
        if (directHit != null) return directHit
        withContext(Dispatchers.IO) { StandardUITools.scanAndAddInstalledApps(context) }
        return lookup() ?: trimmed
    }
}

/** Interface for providing tool implementations to the ActionHandler. */
interface ToolImplementations {
    suspend fun tap(tool: AITool): ToolResult
    suspend fun longPress(tool: AITool): ToolResult
    suspend fun setInputText(tool: AITool): ToolResult
    suspend fun swipe(tool: AITool): ToolResult
    suspend fun pressKey(tool: AITool): ToolResult
    suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?>
}
