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
package com.ai.phoneagent.core.common

/**
 * 日志脱敏工具
 * 对敏感信息（如 API Key）进行脱敏处理，防止日志泄露
 */
object LogMaskingUtil {

    /**
     * 对 API Key 进行脱敏处理
     * 保留前4位和后4位，中间用 **** 替代
     *
     * 示例：
     * - "sk-abcdefghijklmn" -> "sk-a****klmn"
     * - "short" -> "****"
     * - "" -> ""
     */
    fun maskApiKey(apiKey: String): String {
        if (apiKey.isBlank()) return ""

        val trimmed = apiKey.trim()

        // 移除 Bearer 前缀
        val key = if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed.substringAfter(" ", "").trim()
        } else {
            trimmed
        }

        if (key.length <= 8) return "****"

        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix****$suffix"
    }

    /**
     * 对 Authorization header 值进行脱敏
     * "Bearer sk-abcdefghijklmn" -> "Bearer sk-a****klmn"
     */
    fun maskAuthorizationHeader(headerValue: String): String {
        if (headerValue.isBlank()) return ""

        return if (headerValue.startsWith("Bearer ", ignoreCase = true)) {
            val key = headerValue.substringAfter(" ", "").trim()
            "Bearer ${maskApiKey(key)}"
        } else {
            maskApiKey(headerValue)
        }
    }

    /**
     * 对 URL 中的查询参数进行脱敏（如果包含 key/token 等敏感参数）
     */
    fun maskUrl(url: String): String {
        if (url.isBlank()) return ""

        // 简单替换 URL 中可能包含的 key 参数值
        return url.replace(
            Regex("(key|token|api_key|apikey|secret)=([^&]+)", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            val paramName = matchResult.groupValues[1]
            val paramValue = matchResult.groupValues[2]
            "$paramName=${maskApiKey(paramValue)}"
        }
    }

    /**
     * 对日志消息中的所有 API Key 模式进行脱敏
     * 匹配常见的 API Key 格式（sk-xxx, key-xxx 等）
     */
    fun maskSensitiveInMessage(message: String): String {
        if (message.isBlank()) return message

        var result = message
        // 匹配 Bearer token 模式
        result = result.replace(
            Regex("Bearer\\s+([A-Za-z0-9_\\-]{8,})", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            "Bearer ${maskApiKey(matchResult.groupValues[1])}"
        }

        // 匹配 sk- 开头的 API Key
        result = result.replace(
            Regex("(sk-[A-Za-z0-9_\\-]{8,})")
        ) { matchResult ->
            maskApiKey(matchResult.groupValues[1])
        }

        return result
    }
}
