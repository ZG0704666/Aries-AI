package com.ai.phoneagent.core.utils

import java.util.concurrent.ConcurrentHashMap

/** 动作工具类 - 动作名称转换、延迟计算等 */
object ActionUtils {

    private val numberRegex = Regex("""[-+]?\d+(?:\.\d+)?""")

    /** 获取用于显示的动作名称（中文友好） */
    fun getDisplayActionName(actionName: String, fields: Map<String, String>): String {
        val normalizedName = actionName.replace(" ", "").lowercase()
        return when (normalizedName) {
            "launch", "open_app", "start_app" -> {
                val app = fields["app"] ?: fields["package"] ?: ""
                if (app.isNotBlank()) "启动 $app" else "启动应用"
            }
            "tap", "click", "press" -> "点击"
            "doubletap", "double_tap" -> "双击"
            "longpress", "long_press" -> "长按"
            "swipe", "scroll" -> "滑动"
            "type", "input", "text" -> {
                val text = fields["text"]?.take(10) ?: ""
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

    /** 计算模型重试延迟 */
    fun computeModelRetryDelayMs(attempt: Int, baseDelayMs: Long): Long {
        val base = baseDelayMs.coerceAtLeast(0L)
        val mult = 1L shl attempt.coerceIn(0, 6)
        return (base * mult).coerceAtMost(6000L)
    }

    /** 估算文本token数量 */
    fun estimateTokens(text: String): Int {
        var count = 0
        for (c in text) {
            count += if (c.code > 127) 1 else 1
        }
        return (count * 0.6).toInt().coerceAtLeast(1)
    }

    /** 估算历史消息的token数量 */
    fun estimateHistoryTokens(
            messages: List<com.ai.phoneagent.net.ChatRequestMessage>,
            imageTokenEstimate: Int = 1500
    ): Int {
        var total = 0
        for (msg in messages) {
            val content = msg.content
            when (content) {
                is String -> total += estimateTokens(content)
                is List<*> -> {
                    for (item in content) {
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST") val map = item as Map<String, Any>
                            val type = map["type"]
                            if (type == "text") {
                                val text = map["text"] as? String ?: ""
                                total += estimateTokens(text)
                            } else if (type == "image_url") {
                                total += imageTokenEstimate
                            }
                        }
                    }
                }
            }
        }
        return total
    }

    /**
     * 解析单个坐标值（兼容整数 / 浮点 / 百分比）
     *
     * 例如：
     * - `500` -> 500
     * - `500.7` -> 501
     * - `50%` -> 500（按 0-1000 相对坐标系转换）
     */
    fun parseCoordinate(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().trim('"', '\'', ' ')
        if (cleaned.isEmpty()) return null

        if (cleaned.endsWith("%")) {
            val percent = cleaned.dropLast(1).trim().toFloatOrNull() ?: return null
            return ((percent / 100f) * 1000f).toInt().coerceIn(0, 1000)
        }

        cleaned.toIntOrNull()?.let { return it }

        cleaned.toFloatOrNull()?.let { return kotlin.math.round(it).toInt() }

        // 兼容 "x=500.4" / "500px" 这类噪声格式
        val number = numberRegex.find(cleaned)?.value?.toFloatOrNull() ?: return null
        return kotlin.math.round(number).toInt()
    }

    /** 解析坐标点 */
    fun parsePoint(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim().removeSurrounding("[", "]")
        val parts = v.split(',').map { it.trim() }
        if (parts.size < 2) return null
        val x = parseCoordinate(parts[0]) ?: return null
        val y = parseCoordinate(parts[1]) ?: return null
        return x to y
    }

    /** 检查是否为可重试的模型错误 */
    fun isRetryableModelError(t: Throwable?): Boolean {
        if (t == null) return false
        if (t is kotlinx.coroutines.CancellationException) return false
        if (t is com.ai.phoneagent.net.AutoGlmClient.ApiException) {
            if (t.code == 429) return true
            if (t.code in 500..599) return true
            return false
        }
        return t is java.io.IOException
    }

    /** 敏感内容检测 */
    fun looksSensitive(
            uiDump: String,
            keywords: List<String> =
                    listOf(
                            "支付密码",
                            "银行卡",
                            "信用卡",
                            "卡号",
                            "cvv",
                            "安全码",
                            "验证码",
                            "短信验证码",
                            "otp",
                            "一次性密码",
                            "动态口令",
                            "输入密码",
                            "请输入密码",
                            "确认支付",
                            "确认付款"
                    )
    ): Boolean {
        return keywords.any { uiDump.contains(it, ignoreCase = true) }
    }

    /** 截断UI树 */
    fun truncateUiTree(
            uiDump: String,
            maxChars: Int,
            headRatio: Float = 0.6f,
            minTailLength: Int = 100
    ): String {
        if (uiDump.length <= maxChars) return uiDump

        val headSize = (maxChars * headRatio).toInt()
        val tailSize = maxChars - headSize - 50

        return uiDump.take(headSize) +
                "\n... [UI树已截断，共${uiDump.length}字符] ...\n" +
                uiDump.takeLast(tailSize.coerceAtLeast(minTailLength))
    }

    /** 提取第一步动作片段 */
    fun extractFirstActionSnippet(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("do") || trimmed.startsWith("finish")) return trimmed

        val m =
                Regex("""(do\s*\(.*?\))|(finish\s*\(.*?\))""", RegexOption.DOT_MATCHES_ALL)
                        .find(trimmed)
        return m?.value?.trim()
    }

    /** 解析坐标点为屏幕坐标（含边界校验 + roundToInt 精度优化） 坐标系：0-1000 归一化 → 像素 */
    fun parsePointToScreen(point: Pair<Int, Int>, screenW: Int, screenH: Int): Pair<Float, Float> {
        val relX = point.first.coerceIn(0, 1000)
        val relY = point.second.coerceIn(0, 1000)
        val x = (relX / 1000.0f) * screenW
        val y = (relY / 1000.0f) * screenH
        return x to y
    }
}

/** 文本工具类 */
object TextUtils {

    /** 简化日志行 */
    fun simplifyLogLine(line: String): String {
        val raw = line.trim()
        val m = Regex("""\[Step\s+\d+\]\s*""").find(raw)
        return if (m != null && m.range.first == 0) {
            raw.substring(m.range.last + 1).trimStart()
        } else {
            raw
        }
    }

    /** 截断文本 */
    fun truncate(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.take(maxLength) + "..." else text
    }

    /** 截断错误消息 */
    fun truncateErrorMessage(msg: String?, maxLength: Int = 320): String {
        return msg?.trim()?.ifBlank { "未知错误" }?.take(maxLength) ?: "未知错误"
    }
}

/** 正则表达式缓存 */
object RegexCache {
    private val cache = ConcurrentHashMap<String, Regex>()

    fun get(pattern: String, ignoreCase: Boolean = false, multiline: Boolean = false): Regex {
        val cacheKey = "$pattern|$ignoreCase|$multiline"
        return cache.getOrPut(cacheKey) {
            val options = buildSet {
                if (ignoreCase) add(kotlin.text.RegexOption.IGNORE_CASE)
                if (multiline) add(kotlin.text.RegexOption.MULTILINE)
            }
            if (options.isEmpty()) {
                Regex(pattern)
            } else {
                Regex(pattern, options)
            }
        }
    }
}
