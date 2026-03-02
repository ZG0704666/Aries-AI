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
package com.ai.phoneagent.core.executor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.LaunchProxyActivity
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.ShizukuBridge
import com.ai.phoneagent.VirtualDisplayController
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.input.AppClipboardTransaction
import com.ai.phoneagent.core.tools.AppPackageManager
import com.ai.phoneagent.core.utils.ActionUtils
import kotlinx.coroutines.delay

/**
 * 自动化动作执行器。
 *
 * 负责将解析后的 Agent 动作分发到具体执行通道，并按环境选择：
 * 1. 虚拟屏通道
 * 2. Shizuku 通道
 * 3. 无障碍服务通道
 */
class ActionExecutor(
        private val context: Context,
        private val config: AgentConfiguration = AgentConfiguration.DEFAULT,
) {
    companion object {
        private const val TAG = "ActionExecutor"
    }

    private var shizukuAutoFocusConsumed = false

    private val editableFocusedNodeRegex =
            Regex("""<node[^>]*(editable="true"[^>]*focused="true"|focused="true"[^>]*editable="true")""")
    private val nodeTagRegex = Regex("""<node\b[^>]*>""")
    private val boundsAttrRegex = Regex("""\bbounds="([^"]+)"""")
    private val centerAttrRegex = Regex("""\bcenter="([^"]+)"""")
    private val classAttrRegex = Regex("""\bclass="([^"]+)"""")
    private val editableAttrRegex = Regex("""\beditable="true"""")

    // 执行通道相关辅助方法。

    /** 当前是否处于虚拟屏执行模式 */
    private fun isVirtualDisplayMode(): Boolean {
        return config.useBackgroundVirtualDisplay &&
                VirtualDisplayController.shouldUseVirtualDisplay &&
                VirtualDisplayController.isVirtualDisplayStarted()
    }

    /** 获取虚拟屏 displayId；若不可用返回 -1 */
    private fun getVirtualDisplayId(): Int {
        return VirtualDisplayController.getDisplayId() ?: -1
    }

    private fun shouldUseShizukuInteraction(): Boolean {
        return config.useShizukuInteraction && !isVirtualDisplayMode()
    }

    private fun isAsciiOnlyText(text: String): Boolean = text.all { it.code in 0..127 }

    private fun runShizukuTapCommand(
            x: Int,
            y: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val direct = ShizukuBridge.execResult("input tap $x $y")
        if (direct.exitCode == 0) return true

        val fallback = ShizukuBridge.execResult("input swipe $x $y $x $y 1")
        if (fallback.exitCode == 0) return true

        onLog("Shizuku 点击失败: exitCode=${direct.exitCode}/${fallback.exitCode}")
        return false
    }

    private fun runShizukuLongPressCommand(
            x: Int,
            y: Int,
            durationMs: Long,
            onLog: (String) -> Unit
    ): Boolean {
        val r = ShizukuBridge.execResult("input swipe $x $y $x $y ${durationMs.coerceAtLeast(1L)}")
        if (r.exitCode == 0) return true

        onLog("Shizuku 长按失败: exitCode=${r.exitCode}")
        return false
    }

        private fun runShizukuSwipeCommand(
            sx: Int,
            sy: Int,
            ex: Int,
            ey: Int,
            durationMs: Long,
            onLog: (String) -> Unit
    ): Boolean {
        val r = ShizukuBridge.execResult("input swipe $sx $sy $ex $ey ${durationMs.coerceAtLeast(1L)}")
        if (r.exitCode == 0) return true

        onLog("Shizuku 滑动失败: exitCode=${r.exitCode}")
        return false
    }

    private fun runShizukuKeyEventCommand(
            key: String,
            onLog: (String) -> Unit
    ): Boolean {
        val r = ShizukuBridge.execResult("input keyevent $key")
        if (r.exitCode == 0) return true

        onLog("Shizuku 按键事件($key)失败: exitCode=${r.exitCode}")
        return false
    }

    /** 预留：虚拟屏焦点准备逻辑。当前实现为 no-op。 */
    private fun ensureVdFocus() {
        // NO-OP
    }

    /**
     * 执行自动化动作前临时隐藏悬浮层，执行后恢复显示。
     * 避免悬浮层遮挡目标控件，影响点击、输入和滑动。
     */
    private suspend inline fun <T> withAutomationOverlayHidden(
            crossinline block: suspend () -> T
    ): T {
        AutomationOverlay.temporaryHide()
        return try {
            block()
        } finally {
            AutomationOverlay.restoreVisibility()
        }
    }

    private fun readField(fields: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val exact = fields[key]
            if (!exact.isNullOrBlank()) return exact

            val lower = fields[key.lowercase()]
            if (!lower.isNullOrBlank()) return lower
        }
        return null
    }

    internal fun resetSessionState() {
        shizukuAutoFocusConsumed = false
    }

    /** 执行单条解析动作，并路由到对应执行器。 */
    suspend fun execute(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            uiDump: String,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val rawName = action.actionName ?: return false
        val name = rawName.trim().trim('"', '\'', ' ').lowercase()
        val nameKey = name.replace(" ", "")

        return when (nameKey) {
            "launch",
            "open_app",
            "start_app" -> executeLaunch(action, service, onLog)
            "back" -> executeBack(service, onLog)
            "home" -> executeHome(service, onLog)
            "wait",
            "sleep" -> executeWait(action, onLog)
            "type",
            "input",
            "text",
            "type_name" -> executeType(action, service, uiDump, screenW, screenH, onLog)
            "tap",
            "click",
            "press" -> executeTap(action, service, uiDump, screenW, screenH, onLog)
            "longpress",
            "long_press",
            "long press" -> executeLongPress(action, service, screenW, screenH, onLog)
            "doubletap",
            "double_tap",
            "double tap" -> executeDoubleTap(action, service, screenW, screenH, onLog)
            "swipe",
            "scroll" -> executeSwipe(action, service, screenW, screenH, onLog)
            "take_over",
            "takeover" -> executeTakeOver(action, onLog)
            "finish" -> true
            else -> false
        }
    }

    private suspend fun executeLaunch(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            onLog: (String) -> Unit
    ): Boolean {
        val rawTarget =
                action.fields["package"]
                        ?: action.fields["package_name"] ?: action.fields["pkg"]
                                ?: action.fields["app"] ?: action.fields["app_name"] ?: ""
        val t = rawTarget.trim().trim('"', '\'', ' ')
        if (t.isBlank()) return false

        val pm = service?.packageManager ?: context.packageManager
        val beforeTime = service?.lastWindowEventTime()

        fun isInstalled(pkgName: String): Boolean {
            return runCatching {
                        @Suppress("DEPRECATION") pm.getPackageInfo(pkgName, 0)
                        true
                    }
                    .getOrDefault(false)
        }

        fun buildLaunchIntent(pkgName: String): android.content.Intent? {
            val direct = pm.getLaunchIntentForPackage(pkgName)
            if (direct != null) return direct

            val query =
                    android.content.Intent(android.content.Intent.ACTION_MAIN)
                            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val ri =
                    runCatching { pm.queryIntentActivities(query, 0) }.getOrNull()?.firstOrNull {
                        it.activityInfo?.packageName == pkgName
                    }
                            ?: return null

            val ai = ri.activityInfo ?: return null
            return android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setClassName(ai.packageName, ai.name)
        }

        AppPackageManager.initializeCache(context)
        val smartResolved = AppPackageManager.resolvePackageName(t)

        val candidates =
                buildList {
                    if (smartResolved != null) {
                        add(smartResolved)
                    }
                    if (t.contains('.') && t.count { it == '.' } >= 1) {
                        add(t)
                    }
                    service?.let { AppPackageManager.resolvePackageByLabel(it, t) }?.let { add(it) }
                }.distinct()

        val finalCandidates =
                if (candidates.isEmpty()) {
                    val allApps = AppPackageManager.getAllInstalledApps()
                    allApps
                            .filter { (_, appName) ->
                                appName.contains(t, ignoreCase = true) ||
                                        t.contains(appName, ignoreCase = true) ||
                                        isWordBoundaryMatch(t, appName)
                            }
                            .map { it.first }
                            .take(3)
                } else {
                    candidates
                }

        var pkgName = finalCandidates.firstOrNull().orEmpty().ifBlank { t }
        var intent: android.content.Intent? = null

        for (candidate in finalCandidates) {
            if (candidate.contains('.') && !isInstalled(candidate)) continue
            val i = buildLaunchIntent(candidate)
            if (i != null) {
                pkgName = candidate
                intent = i
                break
            }
        }

        onLog("执行操作: launch($pkgName)")
        if (intent == null) {
            onLog("启动失败: 未找到可启动入口 $pkgName (候选: ${candidates.joinToString()})")
            return false
        }

        intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        return try {
            if (isVirtualDisplayMode()) {
                val displayId = getVirtualDisplayId()
                onLog("在虚拟屏启动: displayId=$displayId")
                LaunchProxyActivity.launchOnDisplay(context, intent, displayId)
                if (displayId > 0) {
                    delay(config.launchActionDelayMs)
                    VirtualDisplayController.restoreFocusToDefaultDisplayNow()
                }
            } else {
                LaunchProxyActivity.launch(context, intent)
            }

            beforeTime?.let { t ->
                service?.awaitWindowEvent(
                        t,
                        timeoutMs = config.appLaunchWaitTimeoutMs
                )
            }
            true
        } catch (e: Exception) {
            onLog("启动异常: ${e.message.orEmpty()}")
            false
        }
    }

    private fun isWordBoundaryMatch(query: String, text: String): Boolean {
        val queryWords = query.lowercase().split(Regex("[\\s_\\-]")).filter { it.length >= 2 }
        val textWords = text.lowercase().split(Regex("[\\s_\\-]"))

        return queryWords.all { word ->
            textWords.any { textWord -> textWord.contains(word) || word.contains(textWord) }
        } &&
                textWords.any { textWord ->
                    queryWords.any { word ->
                        textWord.startsWith(word) || word.startsWith(textWord)
                    }
                }
    }

    private suspend fun executeBack(
            service: PhoneAgentAccessibilityService?,
            onLog: (String) -> Unit
    ): Boolean {
        onLog("执行操作: 返回")
        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            VirtualDisplayController.injectBackBestEffort(getVirtualDisplayId())
            delay(config.backAwaitWindowTimeoutMs)
            return true
        }

        if (shouldUseShizukuInteraction()) {
            val ok = runShizukuKeyEventCommand("KEYCODE_BACK", onLog)
            if (ok) delay(config.backAwaitWindowTimeoutMs)
            return ok
        }

        if (service != null) {
            val beforeTime = service.lastWindowEventTime()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            service.awaitWindowEvent(beforeTime, timeoutMs = config.backAwaitWindowTimeoutMs)
            return true
        }

        onLog("无法执行返回：无可用执行通道")
        return false
    }

    private suspend fun executeHome(
            service: PhoneAgentAccessibilityService?,
            onLog: (String) -> Unit
    ): Boolean {
        onLog("执行操作: 回到主页")
        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            VirtualDisplayController.injectHomeBestEffort(getVirtualDisplayId())
            delay(config.homeAwaitWindowTimeoutMs)
            return true
        }

        if (shouldUseShizukuInteraction()) {
            val ok = runShizukuKeyEventCommand("KEYCODE_HOME", onLog)
            if (ok) delay(config.homeAwaitWindowTimeoutMs)
            return ok
        }

        if (service != null) {
            val beforeTime = service.lastWindowEventTime()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            service.awaitWindowEvent(beforeTime, timeoutMs = config.homeAwaitWindowTimeoutMs)
            return true
        }

        onLog("无法执行回到主页：无可用执行通道")
        return false
    }

    /** 处理 take_over：提示需要人工接管，当前返回 false 交给上层处理。 */
    private fun executeTakeOver(action: ParsedAgentAction, onLog: (String) -> Unit): Boolean {
        val message = action.fields["message"].orEmpty().ifBlank { "User assistance required" }
        onLog("需要接管: $message")
        return false
    }

    private suspend fun executeWait(action: ParsedAgentAction, onLog: (String) -> Unit): Boolean {
        val raw = action.fields["duration"].orEmpty().trim()
        val d =
                when {
                    raw.endsWith("ms", ignoreCase = true) -> raw.dropLast(2).trim().toLongOrNull()
                    raw.endsWith("s", ignoreCase = true) ->
                            raw.dropLast(1).trim().toLongOrNull()?.times(1000)
                    raw.contains("second", ignoreCase = true) ->
                            Regex("""(\d+)""")
                                    .find(raw)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.toLongOrNull()
                                    ?.times(1000)
                    else -> {
                        val plain = raw.toLongOrNull()
                        when {
                            plain == null -> null
                            // 默认模型常输出 duration="3" 表示 3 秒；这里做秒级兜底。
                            plain in 1L..60L -> plain * 1000L
                            else -> plain
                        }
                    }
                }
                        ?: 600L

        onLog("执行操作: wait(${d}ms)")
        delay(d.coerceAtLeast(0L))
        return true
    }

        private suspend fun executeType(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            uiDump: String,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        if (ActionUtils.looksSensitive(uiDump, config.sensitiveKeywords)) {
            onLog("检测到敏感内容，已跳过输入操作")
            return false
        }

        val inputText = readField(action.fields, "text").orEmpty()
        val isAsciiInput = isAsciiOnlyText(inputText)
        val resourceId = readField(action.fields, "resourceId", "resource_id", "resourceid")
        val contentDesc = readField(action.fields, "contentDesc", "content_desc", "contentdesc")
        val className = readField(action.fields, "className", "class_name", "classname")
        val elementText =
                readField(
                        action.fields,
                        "elementText",
                        "element_text",
                        "elementtext",
                        "targetText",
                        "target_text",
                        "targettext"
                )
        val index = action.fields["index"]?.trim()?.toIntOrNull() ?: 0

        val element =
                ActionUtils.parsePoint(action.fields["element"])
                        ?: ActionUtils.parsePoint(action.fields["point"])
        val hasExplicitTapTarget = element != null
        if (element != null) {
            val (x, y) = ActionUtils.parsePointToScreen(element, screenW, screenH)
            onLog("输入前点击坐标: (${element.first},${element.second})")
            if (isVirtualDisplayMode()) {
                ensureVdFocus()
                VirtualDisplayController.injectTapBestEffort(
                        getVirtualDisplayId(),
                        x.toInt(),
                        y.toInt()
                )
            } else if (service != null) {
                val clicked = service.clickAwait(x, y)
                if (!clicked) {
                    onLog("无障碍点击失败")
                    return false
                } else {
                    delay(300)
                }
            } else if (shouldUseShizukuInteraction()) {
                val clicked = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                if (!clicked) {
                    onLog("Shizuku 点击失败")
                    return false
                }
            } else {
                onLog("输入前点击失败：无可用执行通道")
                return false
            }
            delay(300)
        }

        onLog("执行操作: 输入(${inputText.take(config.logInputTextTruncateLength)})")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            val displayId = getVirtualDisplayId()
            var ok = injectTextOnVirtualDisplay(displayId, inputText, onLog)
            if (!ok) {
                onLog("虚拟屏输入首轮失败，重试一次注入")
                delay(180)
                ok = injectTextOnVirtualDisplay(displayId, inputText, onLog)
            }
            if (!ok && service != null) {
                onLog("虚拟屏输入失败，尝试无障碍输入兜底")
                ok =
                        if (resourceId != null || contentDesc != null || className != null || elementText != null) {
                            service.setTextOnElement(
                                    text = inputText,
                                    resourceId = resourceId,
                                    elementText = elementText,
                                    contentDesc = contentDesc,
                                    className = className,
                                    index = index
                            )
                        } else {
                            service.setTextOnFocused(inputText)
                        }
                if (!ok) {
                    val inputClicked = service.clickFirstEditableElement()
                    if (inputClicked) {
                        delay(260)
                        ok = service.setTextOnFocused(inputText)
                    }
                }
                service.awaitWindowEvent(
                        service.lastWindowEventTime(),
                        timeoutMs = config.typeAwaitWindowTimeoutMs
                )
            }
            if (!ok && !isAsciiInput) {
                onLog("虚拟屏中文输入失败：下一步不要再输出Type，请改为Tap键盘逐字输入")
            }
            if (!ok) {
                onLog("虚拟屏输入失败")
            }
            delay(config.typeAwaitWindowTimeoutMs)
            return ok
        }

        if (service != null) {
            if (shouldUseShizukuInteraction()) {
                onLog("Shizuku 模式：输入动作改走无障碍服务直输")
            } else {
                onLog("无障碍模式：仅使用无障碍服务直输")
            }
            var ok =
                    if (resourceId != null || contentDesc != null || className != null || elementText != null) {
                        service.setTextOnElement(
                                text = inputText,
                                resourceId = resourceId,
                                elementText = elementText,
                                contentDesc = contentDesc,
                                className = className,
                                index = index
                        )
                    } else {
                        service.setTextOnFocused(inputText)
                    }

            if (!ok) {
                onLog("无障碍直输校验失败，尝试重新聚焦输入框")
                onLog("输入失败，尝试聚焦输入框后重试")
                val inputClicked = service.clickFirstEditableElement()
                if (inputClicked) {
                    delay(300)
                    ok = service.setTextOnFocused(inputText)
                }
            }

            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.typeAwaitWindowTimeoutMs
            )
            if (!ok && !isAsciiInput) {
                onLog("Type中文失败：下一步不要再输出Type，请直接Tap软键盘上的目标字键")
            }
            return ok
        }

        if (!shouldUseShizukuInteraction() && service == null) {
            onLog("无法执行输入：无可用执行通道")
            return false
        }

        val shizukuOnly = shouldUseShizukuInteraction() && service == null

        if (shouldUseShizukuInteraction() && !isVirtualDisplayMode() && !hasExplicitTapTarget) {
            prepareShizukuInputFocusIfNeeded(uiDump, onLog)
        }

        val ok = injectTextOnVirtualDisplay(-1, inputText, onLog)
        if (!ok) {
            onLog("Shizuku 输入失败")
            return false
        }
        if (!verifyShizukuTypeResult(service, inputText, onLog)) {
            val fixedByClipboard =
                    setClipboardAndPaste(-1, inputText, onLog) &&
                            verifyShizukuTypeResult(service, inputText, onLog)
            if (!fixedByClipboard) {
                if (!isAsciiInput && shizukuOnly) {
                    onLog("Shizuku-only 中文输入失败：请让模型改为 Tap 软键盘逐字输入")
                    return false
                }
                val retried = runDirectInputText(-1, inputText)
                if (!retried) {
                    onLog("Shizuku 直输兜底失败（校验不一致）")
                    return false
                }
                if (!verifyShizukuTypeResult(service, inputText, onLog)) {
                    onLog("Shizuku 输入重试后校验失败")
                    return false
                }
            }
        }
        delay(config.typeAwaitWindowTimeoutMs)
        return true
    }

        private suspend fun executeTap(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            uiDump: String,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        if (ActionUtils.looksSensitive(uiDump, config.sensitiveKeywords)) {
            onLog("检测到敏感内容，已跳过点击操作")
            return false
        }

        val resourceId = readField(action.fields, "resourceId", "resource_id", "resourceid")
        val contentDesc = readField(action.fields, "contentDesc", "content_desc", "contentdesc")
        val className = readField(action.fields, "className", "class_name", "classname")
        val elementText =
                readField(
                        action.fields,
                        "elementText",
                        "element_text",
                        "elementtext",
                        "label"
                )
        val index = action.fields["index"]?.trim()?.toIntOrNull() ?: 0

        val selectorOk =
                if (!isVirtualDisplayMode() &&
                                !shouldUseShizukuInteraction() &&
                                service != null &&
                                (resourceId != null ||
                                        contentDesc != null ||
                                        className != null ||
                                        elementText != null)
                ) {
                    onLog("执行操作: 点击(selector)")
                    withAutomationOverlayHidden {
                        service.clickElement(
                                resourceId = resourceId,
                                text = elementText,
                                contentDesc = contentDesc,
                                className = className,
                                index = index
                        )
                    }
                } else {
                    false
                }

        if (selectorOk) {
            if (service != null) {
                service.awaitWindowEvent(
                        service.lastWindowEventTime(),
                        timeoutMs = config.tapAwaitWindowTimeoutMs
                )
            }
            return true
        }

        val element =
                ActionUtils.parsePoint(action.fields["element"])
                        ?: ActionUtils.parsePoint(action.fields["point"])
                                ?: ActionUtils.parsePoint(action.fields["pos"])
        val xRel = ActionUtils.parseCoordinate(action.fields["x"]) ?: element?.first ?: return false
        val yRel = ActionUtils.parseCoordinate(action.fields["y"]) ?: element?.second ?: return false

        val (x, y) = ActionUtils.parsePointToScreen(xRel to yRel, screenW, screenH)
        onLog("执行操作: 点击($xRel,$yRel)")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            VirtualDisplayController.injectTapBestEffort(
                    getVirtualDisplayId(),
                    x.toInt(),
                    y.toInt()
            )
            delay(config.tapAwaitWindowTimeoutMs)
            return true
        }

        return withAutomationOverlayHidden {
            if (shouldUseShizukuInteraction()) {
                val ok = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                if (!ok) {
                    onLog("Shizuku 点击失败")
                    return@withAutomationOverlayHidden false
                }
                delay(config.tapAwaitWindowTimeoutMs)
                return@withAutomationOverlayHidden true
            }

            if (service != null) {
                service.clickAwait(x, y)
                service.awaitWindowEvent(
                        service.lastWindowEventTime(),
                        timeoutMs = config.tapAwaitWindowTimeoutMs
                )
                return@withAutomationOverlayHidden true
            }

            onLog("无法执行点击：无可用执行通道")
            false
        }
    }

        private suspend fun executeLongPress(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val element = ActionUtils.parsePoint(action.fields["element"]) ?: return false
        val (x, y) = ActionUtils.parsePointToScreen(element, screenW, screenH)

        onLog("执行操作: 长按(${element.first},${element.second})")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            val displayId = getVirtualDisplayId()
            VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
            delay(config.longPressDurationMs)
            VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
            delay(config.tapAwaitWindowTimeoutMs)
            return true
        }

        val useShizuku = shouldUseShizukuInteraction()
        val ok = withAutomationOverlayHidden {
            if (useShizuku) {
                val r = runShizukuLongPressCommand(x.toInt(), y.toInt(), config.longPressDurationMs, onLog)
                if (!r) onLog("Shizuku 长按失败")
                if (r) delay(config.tapAwaitWindowTimeoutMs)
                return@withAutomationOverlayHidden r
            }

            if (service != null) {
                service.clickAwait(x, y, durationMs = config.longPressDurationMs)
                return@withAutomationOverlayHidden true
            }

            onLog("无法执行长按：无可用执行通道")
            false
        }
        if (ok && !useShizuku && service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.tapAwaitWindowTimeoutMs
            )
        }
        return ok
    }

        private suspend fun executeDoubleTap(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val element = ActionUtils.parsePoint(action.fields["element"]) ?: return false
        val (x, y) = ActionUtils.parsePointToScreen(element, screenW, screenH)

        onLog("执行操作: 双击(${element.first},${element.second})")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            val displayId = getVirtualDisplayId()
            VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
            delay(config.doubleTapIntervalMs)
            VirtualDisplayController.injectTapBestEffort(displayId, x.toInt(), y.toInt())
            delay(config.tapAwaitWindowTimeoutMs)
            return true
        }

        val useShizuku = shouldUseShizukuInteraction()
        val ok = withAutomationOverlayHidden {
            if (useShizuku) {
                var ok1 = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                if (ok1) {
                    delay(config.doubleTapIntervalMs)
                    ok1 = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                } else {
                    onLog("Shizuku 双击首击失败")
                }
                return@withAutomationOverlayHidden ok1
            }

            if (service == null) {
                onLog("无法执行双击：无可用执行通道")
                return@withAutomationOverlayHidden false
            }

            val ok1 = service.clickAwait(x, y, durationMs = config.clickDurationMs)
            delay(config.doubleTapIntervalMs)
            val ok2 = service.clickAwait(x, y, durationMs = config.clickDurationMs)
            ok1 && ok2
        }
        if (ok && !useShizuku && service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.tapAwaitWindowTimeoutMs
            )
        }
        return ok
    }

    private suspend fun executeSwipe(
            action: ParsedAgentAction,
            service: PhoneAgentAccessibilityService?,
            screenW: Int,
            screenH: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val start = ActionUtils.parsePoint(action.fields["start"])
        val end = ActionUtils.parsePoint(action.fields["end"])

        val sxRel = ActionUtils.parseCoordinate(action.fields["start_x"]) ?: start?.first ?: return false
        val syRel = ActionUtils.parseCoordinate(action.fields["start_y"]) ?: start?.second ?: return false
        val exRel = ActionUtils.parseCoordinate(action.fields["end_x"]) ?: end?.first ?: return false
        val eyRel = ActionUtils.parseCoordinate(action.fields["end_y"]) ?: end?.second ?: return false

        val durRaw = action.fields["duration"].orEmpty().trim()
        val dur =
                when {
                    durRaw.endsWith("ms", ignoreCase = true) ->
                            durRaw.dropLast(2).trim().toLongOrNull()
                    durRaw.endsWith("s", ignoreCase = true) ->
                            durRaw.dropLast(1).trim().toLongOrNull()?.times(1000)
                    else -> durRaw.toLongOrNull()
                }
                        ?: config.scrollDurationMs

        val (sx, sy) = ActionUtils.parsePointToScreen(sxRel to syRel, screenW, screenH)
        val (ex, ey) = ActionUtils.parsePointToScreen(exRel to eyRel, screenW, screenH)

        onLog("执行操作: 滑动($sxRel,$syRel -> $exRel,$eyRel, ${dur}ms)")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            VirtualDisplayController.injectSwipeBestEffort(
                    getVirtualDisplayId(),
                    sx.toInt(),
                    sy.toInt(),
                    ex.toInt(),
                    ey.toInt(),
                    dur
            )
            delay(config.swipeAwaitWindowTimeoutMs)
            return true
        }

        val useShizuku = shouldUseShizukuInteraction()
        val ok = withAutomationOverlayHidden {
            if (useShizuku) {
                val r = runShizukuSwipeCommand(
                        sx.toInt(),
                        sy.toInt(),
                        ex.toInt(),
                        ey.toInt(),
                        dur,
                        onLog
                )
                if (!r) {
                    onLog("Shizuku 滑动失败")
                    return@withAutomationOverlayHidden false
                }
                delay(config.swipeAwaitWindowTimeoutMs)
                return@withAutomationOverlayHidden true
            }

            if (service != null) {
                service.swipeAwait(sx, sy, ex, ey, dur)
                return@withAutomationOverlayHidden true
            }

            onLog("无法执行滑动：无可用执行通道")
            false
        }
        if (ok && !useShizuku && service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.swipeAwaitWindowTimeoutMs
            )
        } else if (ok) {
            delay(config.swipeAwaitWindowTimeoutMs)
        }
        return ok
    }

    // 虚拟屏文本注入策略说明见下方方法注释。

    /**
     * 在虚拟屏场景注入文本。
     *
     * 策略：
     * 1. 先尝试“剪贴板写入 + 粘贴”。
     * 2. 失败后回退到 `input text` 直接输入。
     * 3. 若指定 displayId 注入失败，再回退前台输入兜底。
     */
    internal fun injectTextOnVirtualDisplay(
            displayId: Int,
            text: String,
            onLog: (String) -> Unit
    ): Boolean {
        if (text.isEmpty()) return false

        val hasDisplayId = displayId > 0
        val isAsciiOnly = text.all { it.code in 0..127 }
        logShizukuTypeStage(onLog, "mode", "start", if (hasDisplayId) "display=$displayId" else "foreground")
        logShizukuTypeStage(onLog, "mode", "clipboard_first", "policy=always")

        if (setClipboardAndPaste(displayId, text, onLog)) {
            logShizukuTypeStage(onLog, "final", "ok", "via=clipboard_paste")
            return true
        }
        logShizukuTypeStage(onLog, "clipboard_paste", "fail", "fallback=direct_input")

        if (runDirectInputText(if (hasDisplayId) displayId else -1, text)) {
            val via = if (isAsciiOnly) "direct_input" else "direct_input_fallback"
            logShizukuTypeStage(onLog, "final", "ok", "via=$via")
            return true
        }

        // Fallback to foreground direct input when display-targeted input fails.
        if (hasDisplayId && runDirectInputText(-1, text)) {
            val via = if (isAsciiOnly) "direct_input_fallback" else "direct_input_foreground_fallback"
            logShizukuTypeStage(onLog, "final", "ok", "via=$via")
            return true
        }
        logShizukuTypeStage(onLog, "final", "fail", if (isAsciiOnly) "ascii_all_failed" else "non_ascii_all_failed")
        return false
    }

    private fun runDirectInputText(displayId: Int, text: String): Boolean {
        val encoded = text.replace(" ", "%s")
        val args =
                mutableListOf<String>().apply {
                    add("input")
                    if (displayId > 0) {
                        add("-d")
                        add(displayId.toString())
                    }
                    add("text")
                    add(encoded)
                }
        return ShizukuBridge.execResultArgs(args).exitCode == 0
    }

    /** 通过剪贴板事务写入文本，并触发粘贴。 */
    private fun setClipboardAndPaste(
            displayId: Int,
            text: String,
            onLog: (String) -> Unit
    ): Boolean {
        val tx = AppClipboardTransaction(context)
        val result =
                tx.run(temporaryText = text) { verifyState ->
                    logShizukuTypeStage(onLog, "clipboard_tx", "start", "module=AppClipboardTransaction")

                    // Strict gate: clipboard must be verifiably matched before paste.
                    if (verifyState != AppClipboardTransaction.VerifyState.MATCHED) {
                        logShizukuTypeStage(
                                onLog,
                                "clipboard_set",
                                "fail",
                                "verify=${verifyState.name.lowercase()} strict=required"
                        )
                        return@run false
                    }

                    // Wait clipboard propagation before paste key event.
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {}

                    if (!triggerPaste(displayId)) {
                        logShizukuTypeStage(onLog, "paste", "fail")
                        return@run false
                    }
                    logShizukuTypeStage(onLog, "paste", "ok")

                    // Wait paste commit before restoring user clipboard snapshot.
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {}
                    true
                }

        val shouldFallbackToShizuku =
                !result.staged ||
                        result.verifyState != AppClipboardTransaction.VerifyState.MATCHED ||
                        !result.actionSucceeded
        if (shouldFallbackToShizuku) {
            logShizukuTypeStage(
                    onLog,
                    "clipboard_set",
                    "fail",
                    "verify=${result.verifyState.name.lowercase()}"
            )
            if (!result.restored) {
                logShizukuTypeStage(onLog, "clipboard_restore", "fail")
            }

            logShizukuTypeStage(
                    onLog,
                    "clipboard_tx",
                    "fallback",
                    "via=shizuku reason=${result.verifyState.name.lowercase()}"
            )
            if (setClipboardAndPasteViaShizuku(displayId, text, onLog)) {
                return true
            }
            return false
        }

        logShizukuTypeStage(
                onLog,
                "clipboard_set",
                "ok",
                "verify=${result.verifyState.name.lowercase()}"
        )
        if (!result.restored) {
            logShizukuTypeStage(onLog, "clipboard_restore", "fail")
        } else {
            logShizukuTypeStage(onLog, "clipboard_restore", "ok")
        }
        return result.actionSucceeded
    }

    private fun setClipboardAndPasteViaShizuku(
            displayId: Int,
            text: String,
            onLog: (String) -> Unit
    ): Boolean {
        val snapshot = readClipboardTextViaShizuku()
        if (snapshot == null) {
            logShizukuTypeStage(onLog, "clipboard_snapshot", "fail", "via=shizuku unreadable")
            return false
        }
        logShizukuTypeStage(onLog, "clipboard_snapshot", "ok", "via=shizuku")

        var shouldRestoreVerifyHighlight = false
        try {
            if (!forceSyncClipboardViaShizuku(text)) {
                logShizukuTypeStage(onLog, "clipboard_set", "fail", "via=shizuku write_failed")
                return false
            }

            when (val readBack = readClipboardTextViaShizuku()) {
                text -> {
                    logShizukuTypeStage(onLog, "clipboard_set", "ok", "via=shizuku verify=matched")
                    AutomationOverlay.setInputVerifyHighlight(true)
                    shouldRestoreVerifyHighlight = true
                }
                null -> {
                    logShizukuTypeStage(
                            onLog,
                            "clipboard_set",
                            "fail",
                            "via=shizuku verify=unreadable"
                    )
                    return false
                }
                else -> {
                    logShizukuTypeStage(
                            onLog,
                            "clipboard_set",
                            "fail",
                            "via=shizuku verify=mismatch"
                    )
                    return false
                }
            }

            // Wait clipboard propagation before paste key event.
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {}

            if (!triggerPaste(displayId)) {
                logShizukuTypeStage(onLog, "paste", "fail", "via=shizuku")
                return false
            }
            logShizukuTypeStage(onLog, "paste", "ok", "via=shizuku")

            // Wait paste commit before restoring user clipboard snapshot.
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {}
            return true
        } finally {
            val restored = forceSyncClipboardViaShizuku(snapshot)
            logShizukuTypeStage(
                    onLog,
                    "clipboard_restore",
                    if (restored) "ok" else "fail",
                    "via=shizuku"
            )
            if (shouldRestoreVerifyHighlight) {
                AutomationOverlay.setInputVerifyHighlight(false)
            }
        }
    }

    private fun forceSyncClipboardViaShizuku(text: String): Boolean {
        val candidates =
                listOf(
                        listOf("cmd", "clipboard", "set", "text", text),
                        listOf("cmd", "clipboard", "set-text", text),
                        listOf(
                                "service",
                                "call",
                                "clipboard",
                                "2",
                                "i32",
                                "1",
                                "i64",
                                "0",
                                "s16",
                                "com.android.shell",
                                "s16",
                                text,
                                "i32",
                                "0",
                                "i32",
                                "0",
                        ),
                )
        for (args in candidates) {
            val r = ShizukuBridge.execResultArgs(args)
            if (r.exitCode == 0 && !containsClipboardCommandFailure(r)) return true
        }
        return false
    }

    private fun readClipboardTextViaShizuku(): String? {
        val candidates =
                listOf(
                        listOf("cmd", "clipboard", "get", "text"),
                        listOf("cmd", "clipboard", "get-text")
                )
        for (args in candidates) {
            val r = ShizukuBridge.execResultArgs(args)
            if (r.exitCode != 0) continue
            if (containsClipboardCommandFailure(r)) continue
            val raw = r.stdoutText().replace("\u0000", "")
            return raw.trimEnd('\r', '\n')
        }
        return null
    }

    private fun containsClipboardCommandFailure(result: ShizukuBridge.ExecResult): Boolean {
        val merged = (result.stdoutText() + "\n" + result.stderrText()).lowercase()
        return merged.contains("no shell command implementation") ||
                merged.contains("unknown command") ||
                merged.contains("securityexception") ||
                merged.contains("permission denial") ||
                merged.contains("not found")
    }

    private fun triggerPaste(displayId: Int): Boolean {
        if (displayId > 0) {
            VirtualDisplayController.injectPasteBestEffort(displayId)
            return true
        }

        val pasteKeyEvent =
                ShizukuBridge.execResultArgs(listOf("input", "keyevent", "KEYCODE_PASTE"))
        if (pasteKeyEvent.exitCode == 0) return true

        val pasteKeyCode = ShizukuBridge.execResultArgs(listOf("input", "keyevent", "279"))
        return pasteKeyCode.exitCode == 0
    }

    private suspend fun prepareShizukuInputFocusIfNeeded(
            uiDump: String,
            onLog: (String) -> Unit
    ) {
        if (config.shizukuAutoFocusFirstTypeOnly && shizukuAutoFocusConsumed) {
            logShizukuTypeStage(onLog, "focus_prep", "skipped_once")
            return
        }
        if (config.shizukuAutoFocusFirstTypeOnly) {
            shizukuAutoFocusConsumed = true
        }

        if (editableFocusedNodeRegex.containsMatchIn(uiDump)) {
            logShizukuTypeStage(onLog, "focus_prep", "skipped_focused")
            return
        }

        val center = findFirstEditableCenter(uiDump)
        if (center == null) {
            logShizukuTypeStage(onLog, "focus_prep", "miss", "editable_not_found")
            return
        }

        val (x, y) = center
        val tapped = runShizukuTapCommand(x, y, onLog)
        if (tapped) {
            logShizukuTypeStage(onLog, "focus_prep", "hit", "tap=[$x,$y]")
            delay(220)
        } else {
            logShizukuTypeStage(onLog, "focus_prep", "fail", "tap=[$x,$y]")
        }
    }

    private fun findFirstEditableCenter(uiDump: String): Pair<Int, Int>? {
        for (match in nodeTagRegex.findAll(uiDump)) {
            val nodeTag = match.value
            val className =
                    classAttrRegex.find(nodeTag)?.groupValues?.getOrNull(1).orEmpty()
            val editable =
                    editableAttrRegex.containsMatchIn(nodeTag) ||
                            className.contains("EditText", ignoreCase = true) ||
                            className.contains("AutoCompleteTextView", ignoreCase = true)
            if (!editable) continue

            val centerAttr = centerAttrRegex.find(nodeTag)?.groupValues?.getOrNull(1).orEmpty()
            parseCenterPoint(centerAttr)?.let { return it }

            val bounds = boundsAttrRegex.find(nodeTag)?.groupValues?.getOrNull(1).orEmpty()
            parseCenterFromBounds(bounds)?.let { return it }
        }
        return null
    }

    private fun parseCenterPoint(raw: String): Pair<Int, Int>? {
        val match = Regex("""\[(\d+),(\d+)]""").find(raw) ?: return null
        val x = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        return x to y
    }

    private fun parseCenterFromBounds(bounds: String): Pair<Int, Int>? {
        val match = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""").find(bounds) ?: return null
        val l = match.groupValues[1].toIntOrNull() ?: return null
        val t = match.groupValues[2].toIntOrNull() ?: return null
        val r = match.groupValues[3].toIntOrNull() ?: return null
        val b = match.groupValues[4].toIntOrNull() ?: return null
        return ((l + r) / 2) to ((t + b) / 2)
    }

    private suspend fun verifyShizukuTypeResult(
            service: PhoneAgentAccessibilityService?,
            expected: String,
            onLog: (String) -> Unit,
            retries: Int = 6,
            intervalMs: Long = 90L
    ): Boolean {
        if (expected.isBlank()) return true
        val verifyResult = verifyFocusedInputText(service, expected, retries, intervalMs)
        return when (verifyResult) {
            true -> {
                logShizukuTypeStage(onLog, "verify", "ok")
                true
            }
            false -> {
                logShizukuTypeStage(onLog, "verify", "fail", "focused_text_mismatch")
                false
            }
            null -> {
                if (service == null) {
                    logShizukuTypeStage(onLog, "verify", "skip", "no_accessibility_service")
                    true
                } else {
                    // Service exists but focused text is unreadable; treat as failure to trigger fallback path.
                    logShizukuTypeStage(onLog, "verify", "fail", "focused_text_unreadable")
                    false
                }
            }
        }
    }

    private suspend fun verifyFocusedInputText(
            service: PhoneAgentAccessibilityService?,
            expected: String,
            retries: Int,
            intervalMs: Long
    ): Boolean? {
        val svc = service ?: return null
        val expectedNorm = normalizeTypeVerifyText(expected)
        if (expectedNorm.isEmpty()) return true

        var sawReadable = false
        repeat(retries) {
            val actual = svc.getFocusedInputText()
            if (actual != null) {
                sawReadable = true
                if (isTypeVerifyMatched(actual, expectedNorm)) return true
            }
            delay(intervalMs)
        }
        return if (sawReadable) false else null
    }

    private fun isTypeVerifyMatched(actualRaw: String, expectedNorm: String): Boolean {
        val actualNorm = normalizeTypeVerifyText(actualRaw)
        if (actualNorm == expectedNorm) return true
        if (actualNorm.contains(expectedNorm)) return true

        val actualNoWs = actualNorm.replace(Regex("""\s+"""), "")
        val expectedNoWs = expectedNorm.replace(Regex("""\s+"""), "")
        return expectedNoWs.isNotEmpty() && actualNoWs.contains(expectedNoWs)
    }

    private fun normalizeTypeVerifyText(value: String): String {
        return value.replace("\r\n", "\n").trim()
    }

    private fun logShizukuTypeStage(
            onLog: (String) -> Unit,
            stage: String,
            status: String,
            detail: String = ""
    ) {
        val suffix = if (detail.isBlank()) "" else " $detail"
        onLog("[Type][Shizuku] $stage=$status$suffix")
    }
}



