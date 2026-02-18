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

        val inputText = action.fields["text"].orEmpty()
        val resourceId = action.fields["resourceId"] ?: action.fields["resource_id"]
        val contentDesc = action.fields["contentDesc"] ?: action.fields["content_desc"]
        val className = action.fields["className"] ?: action.fields["class_name"]
        val elementText =
                action.fields["elementText"]
                        ?: action.fields["element_text"] ?: action.fields["targetText"]
                                ?: action.fields["target_text"]
        val index = action.fields["index"]?.trim()?.toIntOrNull() ?: 0

        val element =
                ActionUtils.parsePoint(action.fields["element"])
                        ?: ActionUtils.parsePoint(action.fields["point"])
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
                AutomationOverlay.temporaryHide()
                val clicked = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
                AutomationOverlay.restoreVisibility()
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

        val resourceId = action.fields["resourceId"] ?: action.fields["resource_id"]
        val contentDesc = action.fields["contentDesc"] ?: action.fields["content_desc"]
        val className = action.fields["className"] ?: action.fields["class_name"]
        val elementText =
                action.fields["elementText"]
                        ?: action.fields["element_text"] ?: action.fields["label"]
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
                    AutomationOverlay.temporaryHide()
                    val result =
                            service.clickElement(
                                    resourceId = resourceId,
                                    text = elementText,
                                    contentDesc = contentDesc,
                                    className = className,
                                    index = index
                            )
                    AutomationOverlay.restoreVisibility()
                    result
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

        AutomationOverlay.temporaryHide()
        if (shouldUseShizukuInteraction()) {
            val ok = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
            AutomationOverlay.restoreVisibility()
            if (!ok) {
                onLog("Shizuku 点击失败")
                return false
            }
            delay(config.tapAwaitWindowTimeoutMs)
            return true
        }

        if (service != null) {
            service.clickAwait(x, y)
        } else {
            onLog("无法执行 tap：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
            AutomationOverlay.restoreVisibility()
            return false
        }

        AutomationOverlay.restoreVisibility()
        if (service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.tapAwaitWindowTimeoutMs
            )
        }
        return true
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

        AutomationOverlay.temporaryHide()
        if (shouldUseShizukuInteraction()) {
            val ok = runShizukuLongPressCommand(x.toInt(), y.toInt(), config.longPressDurationMs, onLog)
            AutomationOverlay.restoreVisibility()
            if (!ok) onLog("Shizuku 长按失败")
            if (ok) delay(config.tapAwaitWindowTimeoutMs)
            return ok
        }

        if (service != null) {
            service.clickAwait(x, y, durationMs = config.longPressDurationMs)
        } else {
            onLog("无法执行 long press：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
            AutomationOverlay.restoreVisibility()
            return false
        }

        AutomationOverlay.restoreVisibility()
        if (service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.tapAwaitWindowTimeoutMs
            )
        }
        return true
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

        AutomationOverlay.temporaryHide()
        if (shouldUseShizukuInteraction()) {
            var ok1 = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
            if (ok1) {
                delay(config.doubleTapIntervalMs)
                ok1 = runShizukuTapCommand(x.toInt(), y.toInt(), onLog)
            } else {
                onLog("Shizuku 双击第一次点击失败")
            }
            AutomationOverlay.restoreVisibility()
            return ok1
        }

        if (service == null) {
            onLog("无法执行 double tap：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
            AutomationOverlay.restoreVisibility()
            return false
        }

        val ok1 = service.clickAwait(x, y, durationMs = config.clickDurationMs)
        delay(config.doubleTapIntervalMs)
        val ok2 = service.clickAwait(x, y, durationMs = config.clickDurationMs)

        AutomationOverlay.restoreVisibility()
        if (service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.tapAwaitWindowTimeoutMs
            )
        }
        return ok1 && ok2
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

        // 临时隐藏悬浮窗
        AutomationOverlay.temporaryHide()
        if (shouldUseShizukuInteraction()) {
            val ok = runShizukuSwipeCommand(
                    sx.toInt(),
                    sy.toInt(),
                    ex.toInt(),
                    ey.toInt(),
                    dur,
                    onLog
            )
            if (!ok) {
                onLog("Shizuku 滑动失败")
                AutomationOverlay.restoreVisibility()
                return false
            }
            delay(config.swipeAwaitWindowTimeoutMs)
            AutomationOverlay.restoreVisibility()
            return true
        }

        if (service != null) {
            service.swipeAwait(sx, sy, ex, ey, dur)
        } else {
            onLog("无法执行 swipe：Shizuku 模式已开启但不可用，且未允许 Accessibility 回退")
            AutomationOverlay.restoreVisibility()
            return false
        }
        AutomationOverlay.restoreVisibility()
        if (service != null) {
            service.awaitWindowEvent(
                    service.lastWindowEventTime(),
                    timeoutMs = config.swipeAwaitWindowTimeoutMs
            )
        } else {
            delay(config.swipeAwaitWindowTimeoutMs)
        }
        return true
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

        if (isAsciiOnly) {
            val escaped = text.replace(" ", "%s").replace("'", "\\'").replace("\"", "\\\"")
            val cmd = if (hasDisplayId) "input -d $displayId text '$escaped'" else "input text '$escaped'"
            val result = ShizukuBridge.execResult(cmd)
            if (result.exitCode == 0) return true

            if (hasDisplayId) {
                val cmd2 = "input text '$escaped'"
                val r2 = ShizukuBridge.execResult(cmd2)
                if (r2.exitCode == 0) return true
            }
            onLog("ASCII input 命令失败，尝试剪贴板方式...")
        }

        if (setClipboardAndPaste(displayId, text, onLog)) return true

        val broadcastResult =
                ShizukuBridge.execResult("am broadcast -a ADB_INPUT_TEXT --es msg '$text'")
        if (broadcastResult.exitCode == 0) {
            val output = broadcastResult.stdoutText()
            if (output.contains("result=0") || output.contains("result=-1")) {
                return true
            }
        }

        // 最后回退：不带 -d 的 input text（对某些设备可能有效）
        val escaped = text.replace(" ", "%s").replace("'", "\\'").replace("\"", "\\\"")
        val cmd = "input text '$escaped'"
        val result = ShizukuBridge.execResult(cmd)
        if (result.exitCode == 0) return true

        onLog("所有文本输入方式均失败")
        return false
    }

    /** 通过剪贴板 + Ctrl+V 粘贴方式输入文本 */
    private fun setClipboardAndPaste(
            displayId: Int,
            text: String,
            onLog: (String) -> Unit
    ): Boolean {
        if (displayId <= 0) return false

        // 方式 1: 使用 cmd clipboard（Android 12+ 可用）
        val escapedText = text.replace("'", "'\\''")
        val clipCmds =
                listOf(
                        "cmd clipboard set-text '$escapedText'",
                        "service call clipboard 2 i32 1 i64 0 s16 'com.android.shell' s16 '$escapedText' i32 0 i32 0",
                )

        var clipboardSet = false
        for (cmd in clipCmds) {
            val r = ShizukuBridge.execResult(cmd)
            if (r.exitCode == 0) {
                clipboardSet = true
                break
            }
        }

        if (!clipboardSet) {
            onLog("剪贴板设置失败，跳过粘贴方式")
            return false
        }

        // 等待剪贴板同步
        try {
            Thread.sleep(100)
        } catch (_: InterruptedException) {}

        // 在虚拟屏上注入 Ctrl+V（粘贴）
        VirtualDisplayController.injectPasteBestEffort(displayId)

        // 等待粘贴完成
        try {
            Thread.sleep(200)
        } catch (_: InterruptedException) {}

        return true
    }
}
