/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent

import android.content.Context
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.cache.ScreenshotManager
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.executor.ActionExecutor
import com.ai.phoneagent.core.parser.ActionParser
import com.ai.phoneagent.core.templates.PromptTemplates
import com.ai.phoneagent.core.utils.ActionUtils
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ENABLE_SHIZUKU_UI_TREE = false

/**
 * UiAutomationAgent - 重构后的主Agent
 *
 * 使用清晰的分层架构：
 * - 配置层：AgentConfiguration
 * - 解析层：ActionParser
 * - 执行层：ActionExecutor
 * - 缓存层：ScreenshotManager
 * - 模板层：PromptTemplates
 *
 * 职责：
 * 1. 协调各组件完成Agent流程
 * 2. 管理对话历史和上下文
 * 3. 处理模型调用和重试
 * 4. 管理任务状态和进度
 */
class UiAutomationAgent(
        private val appContext: Context,
        private val config: AgentConfiguration = AgentConfiguration.DEFAULT,
) {
    // 组件实例化
    private val actionParser = ActionParser()
    private val actionExecutor = ActionExecutor(appContext, config)
    private var screenshotManager: ScreenshotManager? = null

    // Tap+Type 合并执行状态
    private var lastActionWasTap = false
    private var lastTapAction: ParsedAgentAction? = null

    private fun hasNonEmptyDesc(action: ParsedAgentAction): Boolean {
        val desc = action.fields["desc"] ?: action.fields["description"]
        return desc?.trim()?.isNotBlank() == true
    }

    private fun isValidDoAction(action: ParsedAgentAction, enforceDesc: Boolean): Boolean {
        return action.metadata == "do" && (!enforceDesc || hasNonEmptyDesc(action))
    }

    private fun resolveActionSubtitle(action: ParsedAgentAction): String {
        val actionName = action.actionName.orEmpty()
        val displayActionName = ActionUtils.getDisplayActionName(actionName, action.fields)
        val rawDesc = action.fields["desc"] ?: action.fields["description"] ?: action.fields["comment"]
        return (rawDesc?.trim().orEmpty().ifBlank { displayActionName }).take(config.subtitleMaxLength)
    }

    interface Control {
        fun isPaused(): Boolean
        suspend fun confirm(message: String): Boolean
    }

    private object NoopControl : Control {
        override fun isPaused(): Boolean = false
        override suspend fun confirm(message: String): Boolean = false
    }

    private class TakeOverException(message: String) : RuntimeException(message)

    data class AgentResult(
            val success: Boolean,
            val message: String,
            val steps: Int,
    )

    /** 运行Agent执行任务 */
    suspend fun run(
            apiKey: String,
            baseUrl: String,
            model: String,
            useThirdPartyApi: Boolean = false,
            task: String,
            service: PhoneAgentAccessibilityService?,
            control: Control = NoopControl,
            onLog: (String) -> Unit,
    ): AgentResult {
        val metrics = (service?.resources ?: appContext.resources).displayMetrics
        var screenW = metrics.widthPixels
        var screenH = metrics.heightPixels

        // 初始化截图管理器
        screenshotManager = ScreenshotManager(config)

        if (config.useShizukuInteraction && !ShizukuBridge.isShizukuAvailable()) {
            return AgentResult(false, "Shizuku 模式未授权，请先在设置中授权 Shizuku 后重试", 0)
        }

        // 如果启用虚拟屏模式，先准备虚拟屏
        if (config.useBackgroundVirtualDisplay) {
            onLog("【虚拟屏模式】正在准备后台虚拟屏...")
            val context = service ?: appContext
            val displayId = VirtualDisplayController.prepareForTask(context, "")
            if (displayId != null && displayId > 0) {
                onLog("【虚拟屏模式】虚拟屏已准备就绪，displayId=$displayId")
                val (vw, vh) = VirtualDisplayController.getContentSizeBestEffort(context)
                if (vw > 0 && vh > 0) {
                    screenW = vw
                    screenH = vh
                }
            } else {
                // 虚拟屏创建失败，直接报错退出，不降级到主屏幕
                onLog("【虚拟屏模式】虚拟屏准备失败：Shizuku 未授权或创建失败")
                return AgentResult(false, "虚拟屏模式启动失败：请确保已安装 Shizuku 并授予权限", 0)
            }
        }

        // try-finally 确保虚拟屏资源在任何退出路径下都被清理
        try {
            return runAgentLoop(
                    apiKey,
                    baseUrl,
                    model,
                    useThirdPartyApi,
                    task,
                    service,
                    control,
                    onLog,
                    screenW,
                    screenH
            )
        } finally {
            // 清理虚拟屏及预览悬浮窗
            cleanupVirtualDisplay(service)
        }
    }

    /** Agent 主循环（从 run 抽出以支持 try-finally 清理） */
    private suspend fun runAgentLoop(
            apiKey: String,
            baseUrl: String,
            model: String,
            useThirdPartyApi: Boolean,
            task: String,
            service: PhoneAgentAccessibilityService?,
            control: Control,
            onLog: (String) -> Unit,
            screenW: Int,
            screenH: Int,
    ): AgentResult {
        // 智能应用启动
        val smartLaunched = trySmartAppLaunch(task, service, onLog)
        if (smartLaunched) {
            onLog("✓ 应用已快速启动，继续后续操作...")
        }

        // 虚拟屏模式：启动预览悬浮窗
        if (config.useBackgroundVirtualDisplay && VirtualDisplayController.isVirtualDisplayStarted()
        ) {
            onLog("【虚拟屏模式】启动预览悬浮窗...")
            val ctx = service ?: appContext
            VirtualScreenPreviewOverlay.show(ctx)
        }

        // 构建初始消息
        val history = mutableListOf<ChatRequestMessage>()
        history +=
                ChatRequestMessage(
                        role = "system",
                        content =
                                PromptTemplates.buildSystemPrompt(
                                        screenW = screenW,
                                        screenH = screenH,
                                        config = null,
                                        enforceDesc = useThirdPartyApi
                                )
                )

        // 清理缓存
        screenshotManager?.clear()

        // 重置状态
        lastActionWasTap = false
        lastTapAction = null

        var step = 0
        var currentScreenW = screenW
        var currentScreenH = screenH

        while (step < config.maxSteps) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            awaitIfPaused(control)
            step++

            // 虚拟屏模式下刷新当前内容尺寸，适配动态分辨率变化
            if (config.useBackgroundVirtualDisplay &&
                            VirtualDisplayController.isVirtualDisplayStarted()
            ) {
                val (vw, vh) = VirtualDisplayController.getContentSizeBestEffort(service ?: appContext)
                if (vw > 0 && vh > 0) {
                    currentScreenW = vw
                    currentScreenH = vh
                }
            }

            // 更新进度
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0f,
                    maxSteps = config.maxSteps,
                    subtitle = "读取界面"
            )

            // 严格隔离模式：截图阶段不抢焦点，避免主屏返回键误作用到虚拟屏

            // 并行获取截图和UI树
            if (config.useShizukuInteraction && !ENABLE_SHIZUKU_UI_TREE) {
                onLog("[Step $step] Shizuku UI树采集已禁用，将仅靠截图解析")
            }
            val shizukuUiDump =
                    if (!config.useBackgroundVirtualDisplay && config.useShizukuInteraction && ENABLE_SHIZUKU_UI_TREE) {
                        ShizukuBridge.dumpUiHierarchyXml()
                    } else {
                        null
                    }
            val rawUiDump =
                    if (config.useBackgroundVirtualDisplay &&
                                    VirtualDisplayController.isVirtualDisplayStarted()
                    ) {
                        "[虚拟屏模式-纯视觉驱动]"
                    } else if (config.useShizukuInteraction) {
                        if (ENABLE_SHIZUKU_UI_TREE) {
                            shizukuUiDump ?: "[Shizuku UI层级不可用]"
                        } else {
                            "[Shizuku UI树采集已禁用，使用截图驱动]"
                        }
                    } else {
                        service?.dumpUiTreeWithRetry(maxNodes = config.uiTreeMaxNodes)
                                ?: throw IllegalStateException("无障碍服务未连接，无法读取 UI 树")
                    }
            val screenshot = screenshotManager?.getOptimizedScreenshot(service)
            if (config.useShizukuInteraction && ENABLE_SHIZUKU_UI_TREE && shizukuUiDump == null) {
                onLog("[Step $step] Shizuku UI 层级读取失败，降级为截图驱动")
            }
            if (config.useShizukuInteraction && screenshot == null) {
                onLog("[Step $step] Shizuku 模式未获取到截图，继续仅使用 UI 树分析")
            }
            if (config.useShizukuInteraction && screenshot == null && shizukuUiDump == null) {
                return AgentResult(false, "Shizuku 截图与UI层级均不可用，请先解锁屏幕并保持前台后重试", step)
            }

            // 更新进度
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.15f,
                    maxSteps = config.maxSteps,
                    subtitle = "解析界面"
            )

            // 截断UI树
            val uiDump = ActionUtils.truncateUiTree(rawUiDump, config.maxUiTreeChars)

            val currentApp = service?.currentAppPackage().orEmpty()
            val screenInfo = "{\"current_app\":\"${currentApp.replace("\"", "")}\"}"

            // 记录截图信息
            if (screenshot != null) {
                onLog("[Step $step] 截图：${screenshot.width}x${screenshot.height}")
            } else {
                onLog("[Step $step] 截图：不可用（将使用纯文本/无障碍树模式）")
            }

            // 构建用户消息
            val userMsg =
                    if (step == 1) {
                        "$task\n\n$screenInfo\n\nUI树：\n$uiDump"
                    } else {
                        "$screenInfo\n\nUI树：\n$uiDump"
                    }

            // 构建消息内容
            val userContent: Any =
                    if (screenshot != null) {
                        listOf(
                                mapOf(
                                        "type" to "image_url",
                                        "image_url" to
                                                mapOf(
                                                        "url" to
                                                                "data:${screenshot.mimeType};base64,${screenshot.base64Png}"
                                                )
                                ),
                                mapOf("type" to "text", "text" to userMsg)
                        )
                    } else {
                        userMsg
                    }

            // 修剪历史
            trimHistory(history)

            // 添加用户消息
            history += ChatRequestMessage(role = "user", content = userContent)
            val observationUserIndex = history.lastIndex

            // 更新进度
            onLog("[Step $step] 请求模型…")
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.25f,
                    maxSteps = config.maxSteps,
                    subtitle = "请求模型"
            )

            AutomationOverlay.startThinking()

            // 调用模型
            val replyResult =
                    requestModelWithRetry(
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            messages = history,
                            step = step,
                            purpose = "请求模型",
                            onLog = onLog,
                    )

            val finalReply = replyResult.getOrNull()?.trim().orEmpty()
            AutomationOverlay.stopThinking()

            // 模型调用失败
            if (finalReply.isBlank()) {
                val err = replyResult.exceptionOrNull()
                val msg = err?.message?.trim().orEmpty().ifBlank { "模型无回复或请求失败" }
                return AgentResult(false, "模型请求失败：${msg.take(320)}", step)
            }

            // 更新进度
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.55f,
                    maxSteps = config.maxSteps,
                    subtitle = "解析模型输出"
            )

            // 解析思考和回答
            val (thinking, answer) = actionParser.parseWithThinking(finalReply)
            if (!thinking.isNullOrBlank()) {
                onLog("[Step $step] 思考：${thinking.take(config.logThinkingTruncateLength)}")
                if (step == 1) {
                    val estimatedSteps = actionParser.parseEstimatedSteps(thinking)
                    if (estimatedSteps > 0) {
                        AutomationOverlay.updateEstimatedSteps(estimatedSteps)
                        onLog("[Step $step] 预估总步骤数：$estimatedSteps")
                    }
                }
            }
            onLog("[Step $step] 输出：${answer.take(config.logAnswerTruncateLength)}")

            // 添加助手消息到历史
            history += ChatRequestMessage(role = "assistant", content = finalReply)

            // 解析动作
            val action =
                    parseActionWithRepair(
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            enforceDesc = useThirdPartyApi,
                            history = history,
                            step = step,
                            answerText = answer,
                            onLog = onLog,
                    )

            // 检查是否完成
            if (action.metadata == "finish") {
                val msg = action.fields["message"].orEmpty().ifBlank { "已完成" }
                return AgentResult(true, msg, step)
            }

            if (action.metadata != "do") {
                return AgentResult(
                        false,
                        "无法解析动作：${action.raw.take(config.logStepTruncateLength)}",
                        step
                )
            }

            // 更新进度
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.68f,
                    maxSteps = config.maxSteps,
                    subtitle = "准备执行"
            )

            // 执行动作
            var currentAction = action
            var execOk = false
            var repairAttempt = 0

            while (true) {
                val actionName =
                        currentAction
                                .actionName
                                ?.trim()
                                ?.trim('"', '\'', ' ')
                                ?.lowercase()
                                .orEmpty()

                val overlayActionText = resolveActionSubtitle(currentAction)
                onLog("[Step $step] 当前动作：$overlayActionText")
                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.78f,
                        maxSteps = config.maxSteps,
                        subtitle = overlayActionText
                )

                // Take_over 处理
                if (actionName == "take_over" || actionName == "takeover") {
                    val msg = currentAction.fields["message"].orEmpty().ifBlank { "需要用户接管" }
                    return AgentResult(false, msg, step)
                }

                // Note/Call_api 处理
                if (actionName == "note" || actionName == "call_api" || actionName == "interact") {
                    return AgentResult(false, "需要用户交互/扩展能力：${currentAction.raw.take(180)}", step)
                }

                // Tap+Type 合并执行检测
                val isTypeAction =
                        actionName == "type" || actionName == "input" || actionName == "text"
                val wasPreviousTap = lastActionWasTap
                val shouldCombineTapAndType =
                        isTypeAction &&
                                wasPreviousTap &&
                                (config.useShizukuInteraction || config.useBackgroundVirtualDisplay)

                execOk =
                        try {
                            val result =
                                    if (shouldCombineTapAndType) {
                                        // 合并执行
                                        val previousTapAction = lastTapAction
                                        lastActionWasTap = false
                                        lastTapAction = null

                                        if (previousTapAction != null) {
                                            onLog("[合并执行] Tap + Type")
                                            executeTapAndTypeCombined(
                                                    service = service,
                                                    tapAction = previousTapAction,
                                                    typeAction = currentAction,
                                                    uiDump = uiDump,
                                                    screenW = currentScreenW,
                                                    screenH = currentScreenH,
                                                    onLog = onLog
                                            )
                                        } else {
                                            actionExecutor.execute(
                                                    currentAction,
                                                    service,
                                                    uiDump,
                                                    currentScreenW,
                                                    currentScreenH,
                                                    onLog
                                            )
                                        }
                                    } else {
                                        // 正常执行
                                        if (actionName == "tap" ||
                                                        actionName == "click" ||
                                                        actionName == "press"
                                        ) {
                                            lastActionWasTap = true
                                            lastTapAction = currentAction
                                        } else {
                                            lastActionWasTap = false
                                            lastTapAction = null
                                        }
                                        actionExecutor.execute(
                                                currentAction,
                                                service,
                                                uiDump,
                                                currentScreenW,
                                                currentScreenH,
                                                onLog
                                        )
                                    }

                            result
                        } catch (e: TakeOverException) {
                            val msg = e.message.orEmpty().ifBlank { "需要用户接管" }
                            return AgentResult(false, msg, step)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            onLog(
                                    "[Step $step] 动作执行异常：${e.message.orEmpty().take(config.logStepTruncateLength)}"
                            )
                            false
                        }

                if (execOk) break

                // 动作执行失败，尝试修复
                if (repairAttempt >= config.maxActionRepairs) {
                    return AgentResult(
                            false,
                            "动作执行失败：${currentAction.raw.take(config.logStepTruncateLength)}",
                            step
                    )
                }

                repairAttempt++
                onLog("[Step $step] 动作执行失败，尝试让模型修复（$repairAttempt/${config.maxActionRepairs})…")

                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.62f,
                        maxSteps = config.maxSteps,
                        subtitle = "动作失败，修复中"
                )

                // 构建修复消息
                val failMsg =
                        PromptTemplates.buildActionRepairPrompt(
                                currentAction.raw,
                                enforceDesc = useThirdPartyApi
                        )
                history += ChatRequestMessage(role = "user", content = failMsg)

                val fixResult =
                        requestModelWithRetry(
                                apiKey = apiKey,
                                baseUrl = baseUrl,
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
                    return AgentResult(false, "动作修复失败：${msg.take(320)}", step)
                }

                AutomationOverlay.updateProgress(
                        step = step,
                        phaseInStep = 0.72f,
                        maxSteps = config.maxSteps,
                        subtitle = "解析修复动作"
                )

                val (fixThinking, fixAnswer) = actionParser.parseWithThinking(fixFinal)
                if (!fixThinking.isNullOrBlank()) {
                    onLog("[Step $step] 修复思考：${fixThinking.take(config.logThinkingTruncateLength)}")
                }
                onLog("[Step $step] 修复输出：${fixAnswer.take(config.logAnswerTruncateLength)}")
                history += ChatRequestMessage(role = "assistant", content = fixFinal)

                currentAction =
                        parseActionWithRepair(
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                model = model,
                                enforceDesc = useThirdPartyApi,
                                history = history,
                                step = step,
                                answerText = fixAnswer,
                                onLog = onLog,
                        )

                if (currentAction.metadata == "finish") {
                    val msg = currentAction.fields["message"].orEmpty().ifBlank { "已完成" }
                    return AgentResult(true, msg, step)
                }
                if (currentAction.metadata != "do") {
                    return AgentResult(
                            false,
                            "无法解析动作：${currentAction.raw.take(config.logStepTruncateLength)}",
                            step
                    )
                }
            }

            // 计算延迟
            val extraDelayMs = config.getActionDelayMs(currentAction.actionName ?: "")

            // 更新进度
            AutomationOverlay.updateProgress(
                    step = step,
                    phaseInStep = 0.92f,
                    maxSteps = config.maxSteps,
                    subtitle = "等待界面稳定"
            )

            // 更新历史中的用户消息
            if (observationUserIndex in history.indices) {
                val obs = history[observationUserIndex]
                if (obs.content is List<*>) {
                    history[observationUserIndex] =
                            ChatRequestMessage(role = "user", content = userMsg)
                }
            }

            // 延迟等待
            delay((config.stepDelayMs + extraDelayMs).coerceAtLeast(0L))
        }

        return AgentResult(false, "达到最大步数限制（${config.maxSteps}）", config.maxSteps)
    } // end of run()

    /** 清理虚拟屏及预览悬浮窗资源 */
    private fun cleanupVirtualDisplay(service: PhoneAgentAccessibilityService?) {
        if (config.useBackgroundVirtualDisplay) {
            val ctx = service ?: appContext
            VirtualScreenPreviewOverlay.hide()
            VirtualDisplayController.cleanup(ctx)
        }
    }

    /** 检测用户任务中是否包含需要打开的应用，如果包含则自动启动 */
    private suspend fun trySmartAppLaunch(
            task: String,
            service: PhoneAgentAccessibilityService?,
            onLog: (String) -> Unit,
    ): Boolean {
        val launchPatterns =
                listOf(
                        Regex(
                                """(?:打开|启动|进入|帮我打开|用|去|切换到|跳转到|回到)\s*([^\s，。,\.！!？?；;]+?)(?:\s|，|。|,|\.|！|!|？|\?|；|;|$)"""
                        ),
                        Regex(
                                """(?:open|launch|start|switch\s+to|go\s+to)\s*(\S+)""",
                                RegexOption.IGNORE_CASE
                        ),
                )

        // 初始化应用包名管理器
        com.ai.phoneagent.core.tools.AppPackageManager.initializeCache(service ?: appContext)

        var resolvedPackage: String? = null
        var matchedAppName: String? = null

        for (pattern in launchPatterns) {
            val matchResult = pattern.find(task)
            if (matchResult != null) {
                val potentialApp = matchResult.groupValues.getOrNull(1)?.trim()
                if (!potentialApp.isNullOrBlank()) {
                    // 使用新的智能解析（包含防误匹配逻辑、高优先级关键词）
                    resolvedPackage =
                            com.ai.phoneagent.core.tools.AppPackageManager.resolvePackageName(
                                    potentialApp
                            )
                    if (resolvedPackage != null) {
                        matchedAppName = potentialApp
                        break
                    }
                }
            }
        }

        // 如果正则匹配失败，尝试全文搜索已安装应用
        if (resolvedPackage == null) {
            val allApps = com.ai.phoneagent.core.tools.AppPackageManager.getAllInstalledApps()
            for ((pkg, appName) in allApps) {
                if (task.contains(appName, ignoreCase = true)) {
                    resolvedPackage = pkg
                    matchedAppName = appName
                    break
                }
            }
        }

        if (resolvedPackage == null) {
            onLog("[⚡快速启动] 未在文本中识别到有效应用名称")
            return false
        }

        val currentApp = service?.currentAppPackage().orEmpty()
        if (currentApp.isNotBlank() && currentApp == resolvedPackage) {
            onLog("[⚡快速启动] ${matchedAppName} 已在前台，跳过启动（无需连接模型）")
            return true
        }

        try {
            if (service != null) {
                val pm = service.packageManager
                val intent = pm.getLaunchIntentForPackage(resolvedPackage)
                if (intent == null) {
                    onLog("[⚡快速启动] 未找到 ${matchedAppName}(${resolvedPackage}) 的启动入口")
                    return false
                }

                intent.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )

                val beforeTime = service.lastWindowEventTime()
                if (config.useBackgroundVirtualDisplay &&
                                VirtualDisplayController.isVirtualDisplayStarted()
                ) {
                    // 虚拟屏模式：启动到虚拟屏（不切换系统焦点）
                    val displayId = VirtualDisplayController.getDisplayId() ?: -1
                    LaunchProxyActivity.launchOnDisplay(service, intent, displayId)
                    onLog("[⚡快速启动] 虚拟屏启动 ${matchedAppName}（displayId=$displayId）")
                    delay(config.appLaunchExtraDelayMs + 500) // 虚拟屏启动需要稍长时间
                    // 启动完成后立即把系统焦点还给主屏（系统可能因 Activity 创建自动切焦）
                    VirtualDisplayController.restoreFocusToDefaultDisplayNow()
                } else {
                    // 前台模式
                    LaunchProxyActivity.launch(service, intent)
                    onLog("[⚡快速启动] 后台启动 ${matchedAppName}（无需连接模型，节省时间）")
                    service.awaitWindowEvent(beforeTime, timeoutMs = config.appLaunchWaitTimeoutMs)
                    delay(config.appLaunchExtraDelayMs)

                    // 验证应用是否真的启动了
                    val newApp = service.currentAppPackage()
                    if (newApp != resolvedPackage) {
                        onLog("[⚡快速启动] ${matchedAppName} 启动验证失败（当前：$newApp），将在后续步骤中处理")
                    } else {
                        onLog("[⚡快速启动] ${matchedAppName} 启动成功，继续后续操作...")
                    }
                }
            } else if (config.useShizukuInteraction) {
                val targetDisplayId =
                        if (config.useBackgroundVirtualDisplay &&
                                        VirtualDisplayController.isVirtualDisplayStarted()
                        ) {
                            VirtualDisplayController.getDisplayId() ?: -1
                        } else {
                            -1
                        }
                val launched = launchAppViaShizuku(resolvedPackage, targetDisplayId, onLog)
                if (!launched) return false
                if (targetDisplayId > 0) {
                    VirtualDisplayController.restoreFocusToDefaultDisplayNow()
                }
                onLog("[⚡快速启动] Shizuku 启动 ${matchedAppName} 成功")
                delay(config.appLaunchExtraDelayMs)
            } else {
                onLog("[⚡快速启动] 无可用启动通道（无障碍未连接，且未启用 Shizuku）")
                return false
            }

            // 清理截图缓存，确保获取最新的应用界面截图
            screenshotManager?.clear()
            return true
        } catch (e: Exception) {
            onLog("[⚡快速启动] 启动失败: ${e.message}")
            return false
        }
    }

    private fun launchAppViaShizuku(
            packageName: String,
            targetDisplayId: Int = -1,
            onLog: (String) -> Unit
    ): Boolean {
        if (!ShizukuBridge.isShizukuAvailable()) {
            onLog("[⚡快速启动] Shizuku 不可用，无法快速启动 $packageName")
            return false
        }
        if (!packageName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"))) {
            onLog("[⚡快速启动] 包名不合法，拒绝执行 Shizuku 启动：$packageName")
            return false
        }

        if (targetDisplayId > 0) {
            return launchAppViaShizukuOnDisplay(packageName, targetDisplayId, onLog)
        }

        val monkey = ShizukuBridge.execResultArgs(
                listOf(
                        "monkey",
                        "-p",
                        packageName,
                        "-c",
                        "android.intent.category.LAUNCHER",
                        "1"
                )
        )
        if (monkey.exitCode == 0) return true

        val resolve =
                ShizukuBridge.execResultArgs(
                        listOf("cmd", "package", "resolve-activity", "--brief", packageName)
                )
        val component =
                resolve.stdoutText().lineSequence().map { it.trim() }.firstOrNull {
                    it.contains("/") &&
                            !it.startsWith("priority=", ignoreCase = true) &&
                            !it.startsWith("No activity", ignoreCase = true)
                }
        if (!component.isNullOrBlank()) {
            val byComponent =
                    ShizukuBridge.execResultArgs(
                            listOf(
                                    "am",
                                    "start",
                                    "-n",
                                    component,
                                    "-a",
                                    "android.intent.action.MAIN",
                                    "-c",
                                    "android.intent.category.LAUNCHER"
                            )
                    )
            if (byComponent.exitCode == 0) return true
        }

        val byPackage =
                ShizukuBridge.execResultArgs(
                        listOf(
                                "am",
                                "start",
                                "-a",
                                "android.intent.action.MAIN",
                                "-c",
                                "android.intent.category.LAUNCHER",
                                "-p",
                                packageName
                        )
                )
        if (byPackage.exitCode == 0) return true

        onLog(
                "[⚡快速启动] Shizuku 启动失败：pkg=$packageName, exit=${monkey.exitCode}/${byPackage.exitCode}"
        )
        return false
    }

    /** 合并执行 Tap+Type 操作 */
    private fun launchAppViaShizukuOnDisplay(
            packageName: String,
            displayId: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val resolve =
                ShizukuBridge.execResultArgs(
                        listOf("cmd", "package", "resolve-activity", "--brief", packageName)
                )
        val component =
                resolve.stdoutText().lineSequence().map { it.trim() }.firstOrNull {
                    it.contains("/") &&
                            !it.startsWith("priority=", ignoreCase = true) &&
                            !it.startsWith("No activity", ignoreCase = true)
                }

        if (!component.isNullOrBlank()) {
            val byComponent = launchViaDisplayCandidates(component, displayId, usePackage = false)
            if (byComponent) return true
        }

        val byPackage = launchViaDisplayCandidates(packageName, displayId, usePackage = true)
        if (byPackage) return true

        onLog("[quick-launch] Shizuku launch failed on virtual display: pkg=$packageName, displayId=$displayId")
        return false
    }

    private fun launchViaDisplayCandidates(
            target: String,
            displayId: Int,
            usePackage: Boolean
    ): Boolean {
        val activityArgs =
                mutableListOf(
                        "-a",
                        "android.intent.action.MAIN",
                        "-c",
                        "android.intent.category.LAUNCHER"
                )
        if (usePackage) {
            activityArgs += listOf("-p", target)
        } else {
            activityArgs += listOf("-n", target)
        }

        val candidates =
                listOf(
                        listOf(
                                "cmd",
                                "activity",
                                "start-activity",
                                "--user",
                                "0",
                                "--display",
                                displayId.toString(),
                                "--windowingMode",
                                "1"
                        ) + activityArgs,
                        listOf(
                                "cmd",
                                "activity",
                                "start-activity",
                                "--user",
                                "0",
                                "--display",
                                displayId.toString()
                        ) + activityArgs,
                        listOf(
                                "am",
                                "start",
                                "--user",
                                "0",
                                "--display",
                                displayId.toString()
                        ) + activityArgs,
                        listOf("am", "start", "--display", displayId.toString()) + activityArgs
                )

        for (args in candidates) {
            val result = ShizukuBridge.execResultArgs(args)
            if (result.exitCode == 0) return true
        }
        return false
    }

    private suspend fun executeTapAndTypeCombined(
            service: PhoneAgentAccessibilityService?,
            tapAction: ParsedAgentAction,
            typeAction: ParsedAgentAction,
            uiDump: String,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit,
    ): Boolean {
        val inputText = typeAction.fields["text"].orEmpty()

        // 提取点击坐标
        val element =
                ActionUtils.parsePoint(tapAction.fields["element"])
                        ?: ActionUtils.parsePoint(tapAction.fields["point"])
                                ?: ActionUtils.parsePoint(tapAction.fields["pos"])

        if (element == null) {
            onLog("[合并执行] 无法获取点击坐标，回退到分别执行")
            val tapOk = actionExecutor.execute(tapAction, service, uiDump, screenW, screenH, onLog)
            if (!tapOk) return false
            return actionExecutor.execute(typeAction, service, uiDump, screenW, screenH, onLog)
        }

        val (x, y) = ActionUtils.parsePointToScreen(element, screenW, screenH)
        onLog(
                "[合并执行] Tap(${element.first},${element.second}) + Type(${inputText.take(config.logCombineInputTextTruncateLength)})"
        )

        val isVdMode =
                config.useBackgroundVirtualDisplay &&
                        VirtualDisplayController.shouldUseVirtualDisplay &&
                        VirtualDisplayController.isVirtualDisplayStarted()

        if (isVdMode) {
            // 虚拟屏模式：先注入点击，再输入文本（不切换系统焦点）
            val displayId = VirtualDisplayController.getDisplayId() ?: -1
            VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
            delay(config.tapTypeCombineKeyboardWaitMs)

            var result = actionExecutor.injectTextOnVirtualDisplay(displayId, inputText, onLog)
            if (!result) {
                onLog("[合并执行] 虚拟屏输入失败，补一次点击后重试")
                VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
                delay(config.tapTypeCombineSecondSetTextWaitMs)
                result = actionExecutor.injectTextOnVirtualDisplay(displayId, inputText, onLog)
            }
            if (!result && service != null) {
                onLog("[合并执行] 虚拟屏输入仍失败，回退到无障碍输入兜底")
                return actionExecutor.execute(typeAction, service, uiDump, screenW, screenH, onLog)
            }
            return result
        }

        if (service == null) {
            onLog("[合并执行] 无无障碍连接，回退到分别执行")
            val tapOk = actionExecutor.execute(tapAction, null, uiDump, screenW, screenH, onLog)
            if (!tapOk) return false
            return actionExecutor.execute(typeAction, null, uiDump, screenW, screenH, onLog)
        }

        // 前台模式：确保任意异常路径都恢复悬浮窗
        AutomationOverlay.temporaryHide()
        try {
            delay(30)

            // 执行点击
            val clickOk = service.clickAwait(x.toFloat(), y.toFloat())
            if (!clickOk) {
                return false
            }

            // 等待键盘弹出
            delay(config.tapTypeCombineKeyboardWaitMs)

            // 执行输入
            var ok = service.setTextOnFocused(inputText)

            // 如果失败，尝试查找可编辑元素
            if (!ok) {
                onLog("[合并执行] 直接输入失败，尝试查找输入框...")
                val inputClicked = service.clickFirstEditableElement()
                if (inputClicked) {
                    delay(200)
                    ok = service.setTextOnFocused(inputText)
                }
            }

            if (!ok) {
                onLog("[合并执行] 输入仍失败，回退到独立 Type 执行")
                return actionExecutor.execute(typeAction, service, uiDump, screenW, screenH, onLog)
            }

            return ok
        } finally {
            AutomationOverlay.restoreVisibility()
        }
    }

    /** 解析动作并修复 */
    private suspend fun parseActionWithRepair(
            apiKey: String,
            baseUrl: String,
            model: String,
            enforceDesc: Boolean,
            history: MutableList<ChatRequestMessage>,
            step: Int,
            answerText: String,
            onLog: (String) -> Unit,
    ): ParsedAgentAction {
        var action =
                actionParser.parse(ActionUtils.extractFirstActionSnippet(answerText) ?: answerText)

        if (action.metadata == "finish") {
            return action
        }
        if (isValidDoAction(action, enforceDesc)) {
            return action
        }
        if (enforceDesc && action.metadata == "do" && !hasNonEmptyDesc(action)) {
            onLog("[Step $step] 动作缺少 desc，尝试让模型补齐说明")
        }

        var attempt = 0
        while (attempt < config.maxParseRepairs &&
                !(action.metadata == "finish" || isValidDoAction(action, enforceDesc))) {
            attempt++
            onLog("[Step $step] 输出无法解析为动作，尝试修正（$attempt/${config.maxParseRepairs}）…")

            // 构建修复消息
            val repairHistory = mutableListOf<ChatRequestMessage>()
            history.firstOrNull { it.role == "system" }?.let { repairHistory.add(it) }
            history.lastOrNull { it.role == "user" }?.let { repairHistory.add(it) }
            repairHistory.add(
                    ChatRequestMessage(
                            role = "user",
                            content = PromptTemplates.buildRepairPrompt(enforceDesc = enforceDesc)
                    )
            )

            val repairResult =
                    requestModelWithRetry(
                            apiKey = apiKey,
                            baseUrl = baseUrl,
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

            val (_, repairAnswer) = actionParser.parseWithThinking(repairFinal)
            onLog("[Step $step] 修正输出：${repairAnswer.take(220)}")

            action =
                    actionParser.parse(
                            ActionUtils.extractFirstActionSnippet(repairAnswer) ?: repairAnswer
                    )
        }

        return action
    }

    /** 带重试的模型请求 */
    private suspend fun requestModelWithRetry(
            apiKey: String,
            baseUrl: String,
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
                                baseUrl = baseUrl,
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

            val retryable = ActionUtils.isRetryableModelError(err)
            if (!retryable || attempt >= maxAttempts - 1) break

            val waitMs = ActionUtils.computeModelRetryDelayMs(attempt, config.modelRetryBaseDelayMs)
            onLog(
                    "[Step $step] $purpose 失败：${err?.message.orEmpty().take(240)}（${attempt + 1}/$maxAttempts），${waitMs}ms 后重试…"
            )
            delay(waitMs)
        }

        return kotlin.Result.failure(lastErr ?: java.io.IOException("Unknown model error"))
    }

    /** 等待暂停状态恢复 */
    private suspend fun awaitIfPaused(control: Control) {
        while (control.isPaused()) {
            delay(config.pauseCheckIntervalMs)
        }
    }

    /** 修剪历史消息，保持上下文在限制内 */
    private fun trimHistory(history: MutableList<ChatRequestMessage>) {
        // 移除图片只保留文本
        for (i in history.indices) {
            val msg = history[i]
            if (msg.content is List<*>) {
                @Suppress("UNCHECKED_CAST") val content = msg.content as List<Map<String, Any>>
                val textOnly = content.filter { it["type"] == "text" }
                if (textOnly.isNotEmpty()) {
                    history[i] = ChatRequestMessage(role = msg.role, content = textOnly)
                }
            }
        }

        // 按token数量修剪
        while (history.size > 2 &&
                ActionUtils.estimateHistoryTokens(history) > config.maxContextTokens) {
            val removeIndex = history.indexOfFirst { it.role != "system" }
            if (removeIndex >= 0) {
                history.removeAt(removeIndex)
                if (removeIndex < history.size && history[removeIndex].role == "assistant") {
                    history.removeAt(removeIndex)
                }
            } else {
                break
            }
        }

        // 按对话轮数修剪
        var turns = 0
        var i = history.size - 1
        while (i >= 0 && turns < config.maxHistoryTurns * 2) {
            if (history[i].role != "system") turns++
            i--
        }
        val keepFrom = (i + 1).coerceAtLeast(if (history.any { it.role == "system" }) 1 else 0)
        while (history.size > keepFrom + turns) {
            val idx = history.indexOfFirst { it.role != "system" }
            if (idx < 0 || idx >= history.size - turns) break
            history.removeAt(idx)
        }
    }
}
