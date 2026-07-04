package com.ai.phoneagent.ui.helper

import android.content.Context
import android.net.Uri
import android.widget.Toast

/**
 * URL 校验相关 helper。
 *
 * 抽取自 MainActivity，仅做职责拆分，不改变原有逻辑。
 * - [normalizeBaseUrlInput] / [validateBaseUrlSecurity] 为纯函数。
 * - [maybeWarnInsecureHttpBaseUrl] 需要 [Context] 以展示 Toast 提示。
 */
object UrlValidator {

    fun normalizeBaseUrlInput(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun validateBaseUrlSecurity(baseUrl: String): String? {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return "API Base URL 格式错误，请检查后重试"
        }
        if (scheme != "https" && scheme != "http") {
            return "API Base URL 必须以 https:// 或 http:// 开头"
        }
        return null
    }

    fun maybeWarnInsecureHttpBaseUrl(context: Context, baseUrl: String) {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
        if (scheme == "http" && host !in localHosts) {
            Toast.makeText(context, "当前使用 http:// 地址，API Key 可能明文传输，请确认网络安全", Toast.LENGTH_LONG)
                    .show()
        }
    }
}
