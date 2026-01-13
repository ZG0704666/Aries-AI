package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.phoneagent.ui.UIAutomationProgressOverlay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class PhoneAgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: PhoneAgentAccessibilityService? = null
        
        // 截图压缩配置
        private const val SCREENSHOT_QUALITY = 85  // JPEG质量 (0-100)
        private const val SCREENSHOT_SCALE_PERCENT = 75  // 缩放百分比 (50-100)
        private const val USE_JPEG_COMPRESSION = true  // 是否使用JPEG压缩
        
        /**
         * 文本规范化：去除空白、繁简转换
         */
        private fun normalizeText(text: String): String {
            return text.trim()
                .replace(Regex("\\s+"), "")  // 移除所有空白字符
                .toSimplifiedChinese()  // 繁体转简体
                .lowercase()  // 转小写
        }
        
        /**
         * 繁体中文转简体，覆盖常见字符
         */
        private fun String.toSimplifiedChinese(): String {
            val traditionalToSimplified = mapOf(
                // 常见繁体字
                '錄' to '录', '應' to '应', '開' to '开', '關' to '关', '設' to '设',
                '節' to '节', '點' to '点', '選' to '选', '瀋' to '览', '頁' to '页',
                '鯒' to '龙', '青' to '香', '臺' to '台', '灣' to '湾', '國' to '国',
                '獨' to '小', '學' to '学', '會' to '会', '還' to '还', '當' to '当',
                '線' to '线', '購' to '购', '請' to '请', '讓' to '让', '說' to '说',
                '閱' to '阅', '文' to '件', '事' to '件', '工' to '作', '資' to '资',
                '訊' to '讯', '已' to '已', '未' to '未', '更' to '更', '呼' to '呼'
            )
            var result = this
            traditionalToSimplified.forEach { (trad, simp) ->
                result = result.replace(trad, simp)
            }
            return result
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var lastEventTimeMs: Long = 0L
    @Volatile private var lastWindowEventTimeMs: Long = 0L

        private enum class UiDetailLevel { MINIMAL, SUMMARY, FULL }

        private data class UiNodeSnapshot(
            val nodeId: String,
            val className: String,
            val packageName: String,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String,
            val clickable: Boolean,
            val enabled: Boolean,
            val focused: Boolean,
            val checkable: Boolean,
            val checked: Boolean,
            val selected: Boolean,
            val scrollable: Boolean,
            val longClickable: Boolean,
            val editable: Boolean,
            val children: List<UiNodeSnapshot>,
        )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventTimeMs = event.eventTime
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                lastWindowEventTimeMs = event.eventTime
            }
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    data class ScreenshotData(
            val width: Int,
            val height: Int,
            val base64Png: String,
    )

    fun currentAppPackage(): String {
        return rootInActiveWindow?.packageName?.toString().orEmpty()
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    suspend fun performTap(x: Float, y: Float): Boolean {
        return clickAwait(x, y)
    }

    suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
    ): Boolean {
        return swipeAwait(startX, startY, endX, endY, durationMs)
    }

    fun performTextInput(text: String): Boolean {
        return setTextOnFocused(text)
    }

    suspend fun tryCaptureScreenshotBase64(): ScreenshotData? {
        if (Build.VERSION.SDK_INT < 30) return null

        val progressOverlay = UIAutomationProgressOverlay.getInstance(this)
        val hideProgressOverlay = progressOverlay.isShowing()
        if (hideProgressOverlay) progressOverlay.setOverlayVisible(false)

        val hideAutomationOverlay = AutomationOverlay.isShowing()
        if (hideAutomationOverlay) AutomationOverlay.setOverlayVisible(false)

        try {
            if (hideProgressOverlay || hideAutomationOverlay) {
                delay(80)
            }
            return suspendCancellableCoroutine { cont ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    takeScreenshot(
                            0,
                            executor,
                            object : AccessibilityService.TakeScreenshotCallback {
                                override fun onSuccess(
                                        screenshot: AccessibilityService.ScreenshotResult
                                ) {
                                    try {
                                        val hw =
                                                Bitmap.wrapHardwareBuffer(
                                                        screenshot.hardwareBuffer,
                                                        screenshot.colorSpace
                                                )
                                        if (hw == null) {
                                            if (cont.isActive) cont.resume(null)
                                            return
                                        }

                                        val originalBmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                                        hw.recycle()
                                        
                                        val originalWidth = originalBmp.width
                                        val originalHeight = originalBmp.height
                                        
                                        // 按比例缩放截图以减少大小
                                        val scaleFactor = SCREENSHOT_SCALE_PERCENT / 100.0
                                        val bmpForCompress = if (scaleFactor < 1.0) {
                                            val newWidth = (originalWidth * scaleFactor).toInt().coerceAtLeast(1)
                                            val newHeight = (originalHeight * scaleFactor).toInt().coerceAtLeast(1)
                                            val scaled = Bitmap.createScaledBitmap(originalBmp, newWidth, newHeight, true)
                                            originalBmp.recycle()
                                            scaled
                                        } else {
                                            originalBmp
                                        }
                                        
                                        val out = ByteArrayOutputStream()
                                        // 使用JPEG压缩以大幅减少文件大小
                                        if (USE_JPEG_COMPRESSION) {
                                            bmpForCompress.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, out)
                                        } else {
                                            bmpForCompress.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        val bytes = out.toByteArray()
                                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        // 返回原始尺寸供坐标计算使用
                                        val result = ScreenshotData(originalWidth, originalHeight, base64)
                                        bmpForCompress.recycle()
                                        if (cont.isActive) cont.resume(result)
                                    } catch (_: Exception) {
                                        if (cont.isActive) cont.resume(null)
                                    } finally {
                                        runCatching {
                                                    screenshot.hardwareBuffer.close()
                                                }
                                        runCatching { executor.shutdown() }
                                    }
                                }

                                override fun onFailure(errorCode: Int) {
                                    runCatching { executor.shutdown() }
                                    if (cont.isActive) cont.resume(null)
                                }
                            }
                    )
                } catch (_: Exception) {
                    runCatching { executor.shutdown() }
                    if (cont.isActive) cont.resume(null)
                } finally {
                    cont.invokeOnCancellation { runCatching { executor.shutdownNow() } }
                }
            }
        } finally {
            if (hideAutomationOverlay) AutomationOverlay.setOverlayVisible(true)
            if (hideProgressOverlay) progressOverlay.setOverlayVisible(true)
        }
    }

    private fun normalizeDetailLevel(detail: String?): UiDetailLevel {
        return when (detail?.lowercase()) {
            "minimal" -> UiDetailLevel.MINIMAL
            "full" -> UiDetailLevel.FULL
            else -> UiDetailLevel.SUMMARY
        }
    }

    private fun sanitizeAttr(value: CharSequence?, maxLength: Int): String? {
        if (value.isNullOrBlank()) return null
        val cleaned =
                value
                        .toString()
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .trim()
        if (cleaned.isBlank()) return null
        return if (cleaned.length > maxLength) cleaned.take(maxLength) else cleaned
    }

    private fun Rect.toBoundsString(): String = toShortString()

    private fun buildNodeSnapshot(
            node: AccessibilityNodeInfo,
            detailLevel: UiDetailLevel,
            maxNodes: Int,
            counter: IntArray,
    ): UiNodeSnapshot? {
        if (counter[0] >= maxNodes) return null
        counter[0]++

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val maxAttrLen = when (detailLevel) {
            UiDetailLevel.MINIMAL -> 40
            UiDetailLevel.SUMMARY -> 80
            UiDetailLevel.FULL -> 140
        }

        val className = sanitizeAttr(node.className, maxAttrLen) ?: ""
        val packageName = sanitizeAttr(node.packageName, maxAttrLen) ?: ""
        val text = sanitizeAttr(node.text, maxAttrLen)
        val contentDesc = sanitizeAttr(node.contentDescription, maxAttrLen)
        val resourceId = sanitizeAttr(node.viewIdResourceName, maxAttrLen)
        val editable = node.isEditable || className.contains("edittext", ignoreCase = true)

        val children = mutableListOf<UiNodeSnapshot>()
        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (counter[0] >= maxNodes) break
            node.getChild(i)?.let { child ->
                buildNodeSnapshot(child, detailLevel, maxNodes, counter)?.let { children.add(it) }
            }
        }

        return UiNodeSnapshot(
                nodeId = bounds.toBoundsString(),
                className = className,
                packageName = packageName,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds.toBoundsString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                focused = node.isFocused,
                checkable = node.isCheckable,
                checked = node.isChecked,
                selected = node.isSelected,
                scrollable = node.isScrollable,
                longClickable = node.isLongClickable,
                editable = editable,
                children = children,
        )
    }

    private fun escapeXml(value: String): String {
        return buildString(value.length) {
            value.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
    }

    private fun appendNodeXml(
            sb: StringBuilder,
            node: UiNodeSnapshot,
            detailLevel: UiDetailLevel,
            depth: Int,
    ) {
        val compact = detailLevel == UiDetailLevel.MINIMAL
        val indent = if (!compact && detailLevel == UiDetailLevel.FULL) "  ".repeat(depth) else ""
        if (!compact) {
            sb.append("\n").append(indent)
        }
        sb.append("<node")

        val minimalAllowed = setOf("class", "text", "resource-id", "bounds", "clickable")
        fun appendAttr(key: String, value: String?) {
            if (value.isNullOrEmpty()) return
            if (compact && key !in minimalAllowed) return
            sb.append(' ').append(key).append("=\"").append(escapeXml(value)).append('"')
        }

        if (!compact) appendAttr("node_id", node.nodeId)
        appendAttr("class", node.className)
        if (!compact) appendAttr("package", node.packageName)
        appendAttr("text", node.text)
        appendAttr("content-desc", node.contentDesc)
        appendAttr("resource-id", node.resourceId)
        appendAttr("bounds", node.bounds)

        // summary/full detail flags
        if (detailLevel != UiDetailLevel.MINIMAL) {
            appendAttr("clickable", node.clickable.toString())
            appendAttr("focused", node.focused.toString())
            if (detailLevel == UiDetailLevel.FULL) {
                appendAttr("enabled", node.enabled.toString())
                appendAttr("checkable", node.checkable.toString())
                appendAttr("checked", node.checked.toString())
                appendAttr("selected", node.selected.toString())
                appendAttr("scrollable", node.scrollable.toString())
                appendAttr("long-clickable", node.longClickable.toString())
                appendAttr("editable", node.editable.toString())
            }
        } else {
            // 在minimal模式下仍保留clickable信息以保障自动化准确度
            appendAttr("clickable", node.clickable.toString())
        }

        if (node.children.isEmpty()) {
            sb.append("/>")
            return
        }

        sb.append(">")
        node.children.forEach { child -> appendNodeXml(sb, child, detailLevel, depth + 1) }
        if (!compact) {
            sb.append("\n").append(indent)
        }
        sb.append("</node>")
    }

    private fun nodeToJson(node: UiNodeSnapshot, detailLevel: UiDetailLevel): JSONObject {
        val obj = JSONObject()
        obj.put("node_id", node.nodeId)
        obj.put("class", node.className)
        obj.put("package", node.packageName)
        if (!node.text.isNullOrEmpty()) obj.put("text", node.text)
        if (!node.contentDesc.isNullOrEmpty()) obj.put("content_desc", node.contentDesc)
        if (!node.resourceId.isNullOrEmpty()) obj.put("resource_id", node.resourceId)
        obj.put("bounds", node.bounds)
        if (detailLevel != UiDetailLevel.MINIMAL) {
            obj.put("clickable", node.clickable)
            obj.put("focused", node.focused)
            if (detailLevel == UiDetailLevel.FULL) {
                obj.put("enabled", node.enabled)
                obj.put("checkable", node.checkable)
                obj.put("checked", node.checked)
                obj.put("selected", node.selected)
                obj.put("scrollable", node.scrollable)
                obj.put("long_clickable", node.longClickable)
                obj.put("editable", node.editable)
            }
        }

        val children = JSONArray()
        node.children.forEach { child -> children.put(nodeToJson(child, detailLevel)) }
        obj.put("children", children)
        return obj
    }

    fun dumpUiTreeXml(maxNodes: Int = 30, detail: String = "minimal"): String {
        val root = rootInActiveWindow ?: return "(no active window)"
        val detailLevel = normalizeDetailLevel(detail)
        val counter = intArrayOf(0)
        val snapshot = buildNodeSnapshot(root, detailLevel, maxNodes, counter) ?: return "(no active window)"

        val pkg = sanitizeAttr(root.packageName, 120).orEmpty()
        val activity = sanitizeAttr(root.className, 120)

        val sb = StringBuilder()
        sb.append("<ui_hierarchy")
        if (pkg.isNotBlank()) sb.append(" package=\"").append(escapeXml(pkg)).append('\"')
        if (!activity.isNullOrBlank()) sb.append(" activity=\"").append(escapeXml(activity)).append('\"')
        sb.append(">")
        appendNodeXml(sb, snapshot, detailLevel, 0)
        sb.append("\n</ui_hierarchy>")
        if (counter[0] >= maxNodes) {
            sb.append("<!-- truncated, maxNodes=").append(maxNodes).append(" -->")
        }
        
        val result = sb.toString()
        Log.d("UI_TREE", "XML格式已生成: 根元素=<ui_hierarchy>, package=$pkg, activity=$activity, 节点数=${counter[0]}, 长度=${result.length}")
        return result
    }

    fun dumpUiTreeJson(maxNodes: Int = 30, detail: String = "minimal"): String {
        val root = rootInActiveWindow ?: return "{}"
        val detailLevel = normalizeDetailLevel(detail)
        val counter = intArrayOf(0)
        val snapshot = buildNodeSnapshot(root, detailLevel, maxNodes, counter) ?: return "{}"

        val rootObj = JSONObject()
        sanitizeAttr(root.packageName, 120)?.let { rootObj.put("package", it) }
        sanitizeAttr(root.className, 120)?.let { rootObj.put("activity", it) }
        rootObj.put("tree", nodeToJson(snapshot, detailLevel))
        if (counter[0] >= maxNodes) {
            rootObj.put("truncated", true)
            rootObj.put("max_nodes", maxNodes)
        }
        return rootObj.toString()
    }

    fun getUiHierarchy(format: String = "xml", detail: String = "minimal", maxNodes: Int = 30): String {
        val result = when (format.lowercase()) {
            "json" -> dumpUiTreeJson(maxNodes, detail)
            else -> dumpUiTreeXml(maxNodes, detail)
        }
        Log.d("UI_TREE", "格式=$format, 详情=$detail, 节点≤$maxNodes, 长度=${result.length}")
        return result
    }

    /**
     * 带重试机制的 UI 树获取
    * getUIHierarchyWithRetry 策略
     */
    suspend fun dumpUiTreeWithRetry(maxNodes: Int = 30, maxRetries: Int = 3, retryDelayMs: Long = 300): String {
        repeat(maxRetries) { attempt ->
            val result = dumpUiTree(maxNodes)
            if (result != "(no active window)" && result.isNotBlank()) {
                return result
            }
            if (attempt < maxRetries - 1) {
                delay(retryDelayMs)
            }
        }
        return dumpUiTree(maxNodes) // 最后一次尝试
    }

    fun dumpUiTree(maxNodes: Int = 30): String {
        return dumpUiTreeXml(maxNodes = maxNodes, detail = "summary")
    }

    fun setTextOnFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused =
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        ?: findFirstEditableFocused(root) ?: return false

        if (!focused.isFocused) {
            focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditableFocused(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var guard = 0
        while (q.isNotEmpty() && guard < 600) {
            guard++
            val n = q.removeFirst()
            if (n.isEditable && (n.isFocused || n.isFocusable)) {
                return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }
    
    /**
     * 查找并点击第一个可编辑的输入框元素
     */
    suspend fun clickFirstEditableElement(): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findFirstEditableElement(root) ?: return false
        val bounds = android.graphics.Rect()
        editable.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        return clickAwait(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }
    
    private fun findFirstEditableElement(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var guard = 0
        while (q.isNotEmpty() && guard < 600) {
            guard++
            val n = q.removeFirst()
            if (n.isEditable) {
                return n
            }
            // 也检查 EditText 类名
            val className = n.className?.toString().orEmpty()
            if (className.contains("EditText", ignoreCase = true)) {
                return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    fun runDemo() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({ swipeUp() }, 450)
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1100)
    }

    fun click(x: Float, y: Float, durationMs: Long = 60L) {
        val p =
                Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    suspend fun clickAwait(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        val p =
                Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(gesture)
    }

    fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 300L,
    ) {
        val p =
                Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    suspend fun swipeAwait(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 300L,
    ): Boolean {
        val p =
                Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(gesture)
    }

    fun lastWindowEventTime(): Long = lastWindowEventTimeMs

    suspend fun awaitWindowEvent(afterTimeMs: Long, timeoutMs: Long = 1500L): Boolean {
        val start = android.os.SystemClock.uptimeMillis()
        while (android.os.SystemClock.uptimeMillis() - start < timeoutMs) {
            if (lastWindowEventTimeMs > afterTimeMs) return true
            delay(60L)
        }
        return false
    }

    // ============================================================================
    // Operit 同款工具接口 - TODO-007 wait_for_element
    // ============================================================================
    
    /**
     * 等待元素出现（轮询机制）
     * @param resourceId 资源ID（如 "com.example:id/button"）
     * @param text 文本内容（模糊匹配）
     * @param contentDesc 内容描述（模糊匹配）
     * @param className 类名（如 "Button" 或完整类名）
     * @param timeoutMs 超时时间（默认5000ms）
     * @param pollIntervalMs 轮询间隔（默认200ms）
     * @return 是否找到元素
     */
    suspend fun waitForElement(
        resourceId: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        className: String? = null,
        timeoutMs: Long = 5000L,
        pollIntervalMs: Long = 200L,
    ): Boolean {
        if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
            contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
            return false // 至少需要一个选择器
        }
        
        val startTime = android.os.SystemClock.uptimeMillis()
        while (android.os.SystemClock.uptimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                val found = findNode(root, resourceId, text, contentDesc, className, 0)
                if (found != null) {
                    Log.d("WAIT_ELEMENT", "元素已找到: resourceId=$resourceId, text=$text, 耗时=${android.os.SystemClock.uptimeMillis() - startTime}ms")
                    return true
                }
            }
            delay(pollIntervalMs)
        }
        Log.d("WAIT_ELEMENT", "等待超时: resourceId=$resourceId, text=$text, timeout=${timeoutMs}ms")
        return false
    }
    
    // ============================================================================
    // Operit 同款工具接口 - TODO-012 find_elements
    // ============================================================================
    
    /**
     * 查找所有匹配的元素
     * @return 匹配元素的信息列表 (JSON格式)
     */
    fun findElements(
        resourceId: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        className: String? = null,
        maxResults: Int = 10,
    ): String {
        val root = rootInActiveWindow ?: return "[]"
        val results = mutableListOf<JSONObject>()
        
        findAllMatchingNodes(root, resourceId, text, contentDesc, className, maxResults, results)
        
        val jsonArray = JSONArray()
        results.forEach { jsonArray.put(it) }
        
        Log.d("FIND_ELEMENTS", "找到 ${results.size} 个匹配元素: resourceId=$resourceId, text=$text")
        return jsonArray.toString()
    }
    
    private fun findAllMatchingNodes(
        root: AccessibilityNodeInfo,
        resourceId: String?,
        text: String?,
        contentDesc: String?,
        className: String?,
        maxResults: Int,
        results: MutableList<JSONObject>,
    ) {
        fun matches(node: AccessibilityNodeInfo): Boolean {
            val id = node.viewIdResourceName?.trim().orEmpty()
            val clsFull = node.className?.toString()?.trim().orEmpty()
            val clsShort = clsFull.substringAfterLast('.')
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()

            if (!resourceId.isNullOrBlank()) {
                if (id.isBlank()) return false
                if (!id.endsWith(resourceId) && !id.contains(resourceId)) return false
            }
            if (!className.isNullOrBlank()) {
                if (clsFull != className && clsShort != className && 
                    !clsFull.contains(className, ignoreCase = true)) return false
            }
            if (!text.isNullOrBlank()) {
                if (t.isBlank()) return false
                if (!t.contains(text, ignoreCase = true)) return false
            }
            if (!contentDesc.isNullOrBlank()) {
                if (d.isBlank()) return false
                if (!d.contains(contentDesc, ignoreCase = true)) return false
            }
            return true
        }
        
        fun nodeToJson(node: AccessibilityNodeInfo, index: Int): JSONObject {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            return JSONObject().apply {
                put("index", index)
                put("resource_id", node.viewIdResourceName ?: "")
                put("class", node.className?.toString() ?: "")
                put("text", node.text?.toString() ?: "")
                put("content_desc", node.contentDescription?.toString() ?: "")
                put("bounds", bounds.toShortString())
                put("clickable", node.isClickable)
                put("enabled", node.isEnabled)
                put("editable", node.isEditable)
            }
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var seen = 0
        var matchIndex = 0
        while (q.isNotEmpty() && seen < 2000 && results.size < maxResults) {
            seen++
            val n = q.removeFirst()
            if (matches(n)) {
                results.add(nodeToJson(n, matchIndex))
                matchIndex++
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
    }

    private fun parseBoundsCenter(boundsStr: String): Pair<Float, Float>? {
        // 格式: [left,top][right,bottom]
        return try {
            val regex = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
            val match = regex.find(boundsStr)
            if (match != null && match.groupValues.size == 5) {
                val l = match.groupValues[1].toFloat()
                val t = match.groupValues[2].toFloat()
                val r = match.groupValues[3].toFloat()
                val b = match.groupValues[4].toFloat()
                Pair((l + r) / 2f, (t + b) / 2f)
            } else null
        } catch (_: Exception) {
            null
        }
    }
    
    // ============================================================================
    // Operit 同款工具接口 - TODO-009 press_key
    // ============================================================================
    
    /**
     * 统一的按键接口
     * @param keyCode 按键码: HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG, LOCK_SCREEN
     * @return 是否成功
     */
    fun pressKey(keyCode: String): Boolean {
        val action = when (keyCode.uppercase().trim()) {
            "HOME" -> GLOBAL_ACTION_HOME
            "BACK" -> GLOBAL_ACTION_BACK
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "POWER_DIALOG" -> GLOBAL_ACTION_POWER_DIALOG
            "LOCK_SCREEN" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            else -> {
                Log.w("PRESS_KEY", "未知按键码: $keyCode")
                return false
            }
        }
        val result = performGlobalAction(action)
        Log.d("PRESS_KEY", "按键 $keyCode -> action=$action, result=$result")
        return result
    }
    
    // ============================================================================
    // Operit 同款工具接口 - TODO-010 get_current_app
    // ============================================================================
    
    /**
     * 获取当前应用详细信息
     * @return JSON格式的应用信息
     */
    // ============================================================================
    // Operit 同款工具接口 - scroll_to_element (优化新增)
    // ============================================================================
    
    /**
     * 滚动到目标元素，直到元素可见或达到最大滚动次数
     * @param resourceId 资源ID（如 "com.example:id/button"）
     * @param text 文本内容（模糊匹配）
     * @param contentDesc 内容描述（模糊匹配）
     * @param className 类名（如 "Button" 或完整类名）
     * @param direction 滚动方向: "up", "down", "left", "right"
     * @param maxScrolls 最大滚动次数（默认10次）
     * @param scrollDelayMs 每次滚动后的等待时间（默认500ms）
     * @return 是否成功找到元素
     */
    suspend fun scrollToElement(
        resourceId: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        className: String? = null,
        direction: String = "down",
        maxScrolls: Int = 10,
        scrollDelayMs: Long = 500L,
    ): Boolean {
        if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
            contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
            Log.w("SCROLL_TO_ELEMENT", "至少需要一个选择器参数")
            return false
        }
        
        Log.d("SCROLL_TO_ELEMENT", "开始滚动查找: resourceId=$resourceId, text=$text, direction=$direction")
        
        // 首先检查元素是否已经可见
        val root = rootInActiveWindow
        if (root != null) {
            val found = findNode(root, resourceId, text, contentDesc, className, 0)
            if (found != null && isElementVisible(found)) {
                Log.d("SCROLL_TO_ELEMENT", "元素已可见，无需滚动")
                return true
            }
        }
        
        // 执行滚动查找
        repeat(maxScrolls) { scrollCount ->
            Log.d("SCROLL_TO_ELEMENT", "滚动第 ${scrollCount + 1}/$maxScrolls 次")
            
            // 执行滚动
            performScroll(direction)
            
            // 等待界面稳定
            delay(scrollDelayMs)
            
            // 检查元素是否已出现
            val currentRoot = rootInActiveWindow
            if (currentRoot != null) {
                val target = findNode(currentRoot, resourceId, text, contentDesc, className, 0)
                if (target != null && isElementVisible(target)) {
                    Log.d("SCROLL_TO_ELEMENT", "滚动成功找到元素，滚动次数: ${scrollCount + 1}")
                    return true
                }
            }
        }
        
        Log.w("SCROLL_TO_ELEMENT", "达到最大滚动次数 $maxScrolls，未找到目标元素")
        return false
    }
    
    /**
     * 检查元素是否在屏幕可见区域内
     */
    private fun isElementVisible(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // 获取屏幕尺寸
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        
        // 检查元素是否在屏幕范围内
        return bounds.left >= 0 && bounds.top >= 0 && 
               bounds.right <= screenWidth && bounds.bottom <= screenHeight &&
               bounds.width() > 0 && bounds.height() > 0
    }
    
    /**
     * 执行滚动操作
     */
    private suspend fun performScroll(direction: String) {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        
        // 滚动区域设置为屏幕中央80%区域，避免触碰边缘控件
        val centerX = screenWidth * 0.5f
        val centerY = screenHeight * 0.5f
        val scrollDistance = minOf(screenWidth, screenHeight) * 0.3f // 滚动距离为屏幕30%
        
        val (startX, startY, endX, endY) = when (direction.lowercase().trim()) {
            "up" -> {
                val startY = centerY + scrollDistance
                val endY = centerY - scrollDistance
                arrayOf(centerX, startY, centerX, endY)
            }
            "down" -> {
                val startY = centerY - scrollDistance
                val endY = centerY + scrollDistance
                arrayOf(centerX, startY, centerX, endY)
            }
            "left" -> {
                val startX = centerX + scrollDistance
                val endX = centerX - scrollDistance
                arrayOf(startX, centerY, endX, centerY)
            }
            "right" -> {
                val startX = centerX - scrollDistance
                val endX = centerX + scrollDistance
                arrayOf(startX, centerY, endX, centerY)
            }
            else -> {
                Log.w("SCROLL_TO_ELEMENT", "未知滚动方向: $direction，使用默认向下滚动")
                val startY = centerY - scrollDistance
                val endY = centerY + scrollDistance
                arrayOf(centerX, startY, centerX, endY)
            }
        }
        
        // 执行滑动手势
        swipe(startX, startY, endX, endY, 400L)
    }

    fun getCurrentAppInfo(): String {
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString() ?: "unknown"
        val activityClass = root?.className?.toString() ?: "unknown"
        
        return JSONObject().apply {
            put("package_name", packageName)
            put("activity_class", activityClass)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    suspend fun clickElement(
            resourceId: String? = null,
            text: String? = null,
            contentDesc: String? = null,
            className: String? = null,
            index: Int = 0,
            bounds: String? = null,
            x: Float? = null,
            y: Float? = null,
    ): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            val target = findNode(root, resourceId, text, contentDesc, className, index)
            if (target != null) {
                val clickable = findClickableAncestor(target) ?: target
                if (clickable.isClickable && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d("CLICK_ELEMENT", "selector点击成功: res=$resourceId text=$text class=$className idx=$index")
                    return true
                }
                val r = Rect()
                clickable.getBoundsInScreen(r)
                if (!r.isEmpty) {
                    val ok = clickAwait(r.centerX().toFloat(), r.centerY().toFloat())
                    Log.d("CLICK_ELEMENT", "selector坐标兜底: bounds=${r.toShortString()} ok=$ok")
                    return ok
                }
            }
        }

        if (!bounds.isNullOrBlank()) {
            parseBoundsCenter(bounds)?.let { (cx, cy) ->
                val ok = clickAwait(cx, cy)
                Log.d("CLICK_ELEMENT", "bounds兜底: $bounds -> ($cx,$cy) ok=$ok")
                return ok
            }
        }

        if (x != null && y != null) {
            val ok = clickAwait(x, y)
            Log.d("CLICK_ELEMENT", "坐标兜底: ($x,$y) ok=$ok")
            return ok
        }

        Log.w("CLICK_ELEMENT", "未找到元素且无兜底坐标: res=$resourceId text=$text class=$className idx=$index")
        return false
    }

    suspend fun setTextOnElement(
            text: String,
            resourceId: String? = null,
            elementText: String? = null,
            contentDesc: String? = null,
            className: String? = null,
            index: Int = 0,
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val target =
                findNode(root, resourceId, elementText, contentDesc, className, index) ?: return false

        if (!target.isFocused) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) return true

        val r = Rect()
        target.getBoundsInScreen(r)
        if (!r.isEmpty) {
            clickAwait(r.centerX().toFloat(), r.centerY().toFloat())
            delay(120L)
        }
        if (!target.isFocused) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var guard = 0
        while (cur != null && guard < 10) {
            guard++
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return null
    }

    private fun findNode(
            root: AccessibilityNodeInfo,
            resourceId: String?,
            text: String?,
            contentDesc: String?,
            className: String?,
            index: Int,
    ): AccessibilityNodeInfo? {
        fun matches(node: AccessibilityNodeInfo): Boolean {
            val id = node.viewIdResourceName?.trim().orEmpty()
            val clsFull = node.className?.toString()?.trim().orEmpty()
            val clsShort = clsFull.substringAfterLast('.')
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()

            if (!resourceId.isNullOrBlank()) {
                if (id.isBlank()) return false
                if (!id.endsWith(resourceId)) return false
            }
            if (!className.isNullOrBlank()) {
                if (clsFull != className && clsShort != className) return false
            }
            if (!text.isNullOrBlank()) {
                if (t.isBlank()) return false
                // 增强匹配：原始 + 规范化
                val normalizedNodeText = normalizeText(t)
                val normalizedSearchText = normalizeText(text)
                if (!t.contains(text, ignoreCase = true) && 
                    !normalizedNodeText.contains(normalizedSearchText)) {
                    return false
                }
            }
            if (!contentDesc.isNullOrBlank()) {
                if (d.isBlank()) return false
                // 增强匹配：原始 + 规范化
                val normalizedNodeDesc = normalizeText(d)
                val normalizedSearchDesc = normalizeText(contentDesc)
                if (!d.contains(contentDesc, ignoreCase = true) && 
                    !normalizedNodeDesc.contains(normalizedSearchDesc)) {
                    return false
                }
            }
            return true
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var seen = 0
        var matched = 0
        while (q.isNotEmpty() && seen < 2000) {
            seen++
            val n = q.removeFirst()
            if (matches(n)) {
                if (matched == index.coerceAtLeast(0)) return n
                matched++
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { cont ->
            val accepted =
                    dispatchGesture(
                            gesture,
                            object : GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(false)
                                }
                            },
                            null
                    )
            if (!accepted) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val y1 = dm.heightPixels * 0.78f
        val y2 = dm.heightPixels * 0.32f
        swipe(x, y1, x, y2, 360L)
    }
}
