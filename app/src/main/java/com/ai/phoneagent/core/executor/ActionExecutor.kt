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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.LaunchProxyActivity
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.ShizukuBridge
import com.ai.phoneagent.VirtualDisplayController
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.tools.AppPackageManager
import com.ai.phoneagent.core.utils.ActionUtils
import kotlinx.coroutines.delay

/**
 * 动作执行器 - 单一职责
 *
 * 负责执行所有类型的Agent动作 原逻辑来自 UiAutomationAgent.kt 的 execute 方法
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

    // ─── 虚拟屏模式辅助方法 ───

    /** 判断当前是否应使用虚拟屏执行路径 */
    private fun isVirtualDisplayMode(): Boolean {
        return config.useBackgroundVirtualDisplay &&
                VirtualDisplayController.shouldUseVirtualDisplay &&
                VirtualDisplayController.isVirtualDisplayStarted()
    }

    /** 获取虚拟屏 displayId，不可用时返回 -1 */
    private fun getVirtualDisplayId(): Int {
        return VirtualDisplayController.getDisplayId() ?: -1
    }

    private fun shouldUseShizukuInteraction(): Boolean {
        return config.useShizukuInteraction && !isVirtualDisplayMode()
    }

    private fun runShizukuTapCommand(
            x: Int,
            y: Int,
            onLog: (String) -> Unit
    ): Boolean {
        val direct = ShizukuBridge.execResult("input tap $x $y")
        if (direct.exitCode == 0) return true

        val fallback = ShizukuBridge.execResult("input swipe $x $y $x $y 1")
        if (fallback.exitCode == 0) return true

        onLog("Shizuku tap 失败: exitCode=${direct.exitCode}/${fallback.exitCode}")
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

        onLog("Shizuku swipe failed: exitCode=${r.exitCode}")
        return false
    }

    private fun runShizukuKeyEventCommand(
            key: String,
            onLog: (String) -> Unit
    ): Boolean {
        val r = ShizukuBridge.execResult("input keyevent $key")
        if (r.exitCode == 0) return true

        onLog("Shizuku keyevent($key) failed: exitCode=${r.exitCode}")
        return false
    }

    /** 键盘焦点保持不切换：虚拟显示注入通过 displayId 指定 */
    private fun ensureVdFocus() {
        // NO-OP
    }

    /**
     * 临时隐藏自动化悬浮窗并保证在任意返回路径恢复，避免 Shizuku 路径中途异常导致悬浮窗消失。
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

    /** 执行动作 */
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

        onLog("执行：Launch($pkgName)")
        if (intent == null) {
            onLog("Launch 失败：未找到可启动入口：$pkgName（candidates=${candidates.joinToString()}）")
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
                onLog("Launch → 虚拟屏 displayId=$displayId")
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
            onLog("Launch 失败：${e.message.orEmpty()}")
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
        onLog("执行 back")
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

        onLog("无法执行 back：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
        return false
    }

    private suspend fun executeHome(
            service: PhoneAgentAccessibilityService?,
            onLog: (String) -> Unit
    ): Boolean {
        onLog("执行 home")
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

        onLog("无法执行 home：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
        return false
    }

    /** 执行 Take_over - 需要用户接管，不执行动作，仅返回失败，由上层处理 */
    private fun executeTakeOver(action: ParsedAgentAction, onLog: (String) -> Unit): Boolean {
        val message = action.fields["message"].orEmpty().ifBlank { "需要用户协助处理" }
        onLog("Take_over: $message")
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
                    else -> raw.toLongOrNull()
                }
                        ?: 600L

        onLog("执行：Wait(${d}ms)")
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
            onLog("检测到敏感内容，拒绝执行输入操作")
            return false
        }

        val inputText = readField(action.fields, "text").orEmpty()
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
            onLog("执行输入前先点击(${element.first},${element.second})")
            if (isVirtualDisplayMode()) {
                ensureVdFocus()
                VirtualDisplayController.injectTapBestEffort(
                        getVirtualDisplayId(),
                        x.toInt(),
                        y.toInt()
                )
            } else if (shouldUseShizukuInteraction()) {
                val clicked = withAutomationOverlayHidden {
                    runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                }
                if (!clicked) {
                    onLog("Shizuku 点击失败")
                    return false
                }
            } else if (service != null) {
                val clicked = service.clickAwait(x, y)
                if (!clicked) {
                    onLog("Accessibility 点击失败")
                    return false
                } else {
                    delay(300)
                }
            } else {
                onLog("输入前点击失败：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
                return false
            }
            delay(300)
        }

        onLog("执行 type(${inputText.take(config.logInputTextTruncateLength)})")

        if (isVirtualDisplayMode()) {
            ensureVdFocus()
            val displayId = getVirtualDisplayId()
            val ok = injectTextOnVirtualDisplay(displayId, inputText, onLog)
            if (!ok) {
                onLog("虚拟显示器文本输入失败")
            }
            delay(config.typeAwaitWindowTimeoutMs)
            return ok
        }

        if (service != null && !shouldUseShizukuInteraction()) {
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
                onLog("文本输入失败，尝试点击输入框后重试")
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
            return ok
        }

        if (!shouldUseShizukuInteraction() && service == null) {
            onLog("无法执行 type：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
            return false
        }

        if (shouldUseShizukuInteraction() && !isVirtualDisplayMode() && !hasExplicitTapTarget) {
            prepareShizukuInputFocusIfNeeded(uiDump, onLog)
        }

        val ok = injectTextOnVirtualDisplay(-1, inputText, onLog)
        if (!ok) {
            onLog("Shizuku 输入失败")
            return false
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
            onLog("检测到敏感内容，停止执行点击")
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
                    onLog("执行 tap(selector)")
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
        onLog("执行 tap($xRel,$yRel)")

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

            onLog("无法执行 tap：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
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

        onLog("执行 long press(${element.first},${element.second})")

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

            onLog("无法执行 long press：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
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

        onLog("执行 double tap(${element.first},${element.second})")

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
                    onLog("Shizuku 双击第一次点击失败")
                }
                return@withAutomationOverlayHidden ok1
            }

            if (service == null) {
                onLog("无法执行 double tap：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
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

        onLog("执行：Swipe($sxRel,$syRel -> $exRel,$eyRel, ${dur}ms)")

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

            onLog("无法执行 swipe：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
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

    // ─── 虚拟屏文本输入 ───

    /**
     * 在虚拟屏上输入文本（支持中文等非 ASCII 字符）
     *
     * 策略：
     * 1. 纯 ASCII 文本 → 直接使用 `input -d <displayId> text`
     * 2. 含非 ASCII 字符 → 先写入剪贴板，再注入 Ctrl+V 粘贴到虚拟屏
     * 3. 剪贴板方式失败 → 回退到逐字 `input text` 尝试
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

        if (!isAsciiOnly) {
            logShizukuTypeStage(onLog, "mode", "non_ascii", "clipboard_first=true")
            if (setClipboardAndPaste(displayId, text, onLog)) {
                logShizukuTypeStage(onLog, "final", "ok", "via=clipboard_paste")
                return true
            }
            logShizukuTypeStage(onLog, "clipboard_paste", "fail", "fallback=direct_input")
        }

        if (runDirectInputText(if (hasDisplayId) displayId else -1, text)) {
            val via = if (isAsciiOnly) "direct_input" else "direct_input_fallback"
            logShizukuTypeStage(onLog, "final", "ok", "via=$via")
            return true
        }

        // 对 displayId 定向失败时，尝试前台 direct input 兜底（仍不走粘贴）
        if (hasDisplayId && runDirectInputText(-1, text)) {
            val via = if (isAsciiOnly) "direct_input_fallback" else "direct_input_foreground_fallback"
            logShizukuTypeStage(onLog, "final", "ok", "via=$via")
            return true
        }

        if (isAsciiOnly && setClipboardAndPaste(displayId, text, onLog)) {
            logShizukuTypeStage(onLog, "final", "ok", "via=clipboard_paste_fallback")
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

    /** 通过剪贴板 + Ctrl+V 粘贴方式输入文本 */
    private fun setClipboardAndPaste(
            displayId: Int,
            text: String,
            onLog: (String) -> Unit
    ): Boolean {
        if (!setClipboardText(text)) {
            logShizukuTypeStage(onLog, "clipboard_set", "fail")
            return false
        }
        logShizukuTypeStage(onLog, "clipboard_set", "ok")

        // 等待剪贴板同步
        try {
            Thread.sleep(100)
        } catch (_: InterruptedException) {}

        if (!triggerPaste(displayId)) {
            logShizukuTypeStage(onLog, "paste", "fail")
            return false
        }
        logShizukuTypeStage(onLog, "paste", "ok")

        // 等待粘贴完成
        try {
            Thread.sleep(200)
        } catch (_: InterruptedException) {}

        return true
    }

    private fun setClipboardText(text: String): Boolean {
        // 方式 1: 优先使用应用侧 ClipboardManager（不依赖 ROM 的 shell clipboard 命令）
        val appClipboardOk =
                runCatching {
                    val cm =
                            context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager ?: return@runCatching false
                    cm.setPrimaryClip(ClipData.newPlainText("Aries", text))
                    true
                }
                        .getOrDefault(false)
        if (appClipboardOk) return true

        // 方式 2: 使用 shell clipboard 命令（部分 ROM 可能不实现 cmd clipboard）
        val clipCmds =
                listOf(
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

        for (cmd in clipCmds) {
            val r = ShizukuBridge.execResultArgs(cmd)
            if (r.exitCode == 0) return true
        }
        return false
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
        val tapped = withAutomationOverlayHidden { runShizukuTapCommand(x, y, onLog) }
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
