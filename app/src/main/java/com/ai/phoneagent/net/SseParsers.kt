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
package com.ai.phoneagent.net

import com.ai.phoneagent.core.common.AppJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SSE 流式响应行的纯解析器。
 *
 * 这组解析器从 [AutoGlmClient] 与 [OpenAICompatibleProvider] 的内联解析逻辑中抽取，
 * 让生产代码与单元测试共用同一份实现，避免测试维护"逻辑副本"导致回归被掩盖。
 *
 * - AutoGlm 协议：行前缀为 `data:`（不要求尾随空格），兼容 `reasoning_content` / `reasoning` 双字段。
 * - OpenAI 协议：行前缀为 `data: `（强制尾随空格），支持普通 content 与 tool_calls 增量。
 *
 * 所有函数均为无副作用纯函数，输入单行 SSE 文本，输出一个事件。
 * 调用方（生产为流式回调、测试为列表归并）自行决定如何聚合事件。
 */

// ===================== AutoGlm =====================

internal sealed interface AutoGlmSseEvent {
    /** 解析出一条增量；reasoning / content 任一可能为空。 */
    data class Delta(val reasoning: String?, val content: String?) : AutoGlmSseEvent
    /** 收到 `[DONE]` 终止标记，调用方应结束流。 */
    object Done : AutoGlmSseEvent
    /** 空行、非 data 行或格式错误，调用方应跳过。 */
    object Skip : AutoGlmSseEvent
}

@Serializable
internal data class AutoGlmSseChunk(
    val choices: List<AutoGlmSseChoice>? = null,
)

@Serializable
internal data class AutoGlmSseChoice(
    val delta: AutoGlmSseDeltaDto? = null,
    val message: AutoGlmSseMessage? = null,
)

@Serializable
internal data class AutoGlmSseDeltaDto(
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    val content: String? = null,
)

@Serializable
internal data class AutoGlmSseMessage(
    val content: String? = null,
)

/**
 * 解析一行 AutoGlm 协议的 SSE 数据。
 *
 * 约定：
 * - 空白行 → [AutoGlmSseEvent.Skip]
 * - 非 `data:` 前缀 → [AutoGlmSseEvent.Skip]
 * - `data: [DONE]` → [AutoGlmSseEvent.Done]
 * - JSON 解析失败或 choices 为空 → [AutoGlmSseEvent.Skip]（与生产历史行为一致：忽略错误继续）
 * - delta 同时支持 `reasoning_content` 与 `reasoning` 字段（reasoning_content 优先）
 */
internal fun parseAutoGlmSseLine(line: String): AutoGlmSseEvent {
    if (line.isBlank()) return AutoGlmSseEvent.Skip
    if (!line.startsWith("data:")) return AutoGlmSseEvent.Skip

    val data = line.removePrefix("data:").trim()
    if (data == "[DONE]") return AutoGlmSseEvent.Done

    val chunk = runCatching { AppJson.decodeFromString<AutoGlmSseChunk>(data) }.getOrNull()
        ?: return AutoGlmSseEvent.Skip
    val choice0 = chunk.choices?.firstOrNull() ?: return AutoGlmSseEvent.Skip

    val delta = choice0.delta
    return if (delta != null) {
        AutoGlmSseEvent.Delta(
            reasoning = delta.reasoningContent ?: delta.reasoning,
            content = delta.content,
        )
    } else {
        AutoGlmSseEvent.Delta(
            reasoning = null,
            content = choice0.message?.content,
        )
    }
}

// ===================== OpenAI =====================

internal sealed interface OpenAiSseEvent {
    /** 解析出一条增量；content 与 toolCalls 任一可能为空。 */
    data class Delta(val content: String?, val toolCalls: List<OpenAiSseToolCall>?) : OpenAiSseEvent
    /** 收到 `[DONE]` 终止标记。 */
    object Done : OpenAiSseEvent
    /** 空行、非 data 行或格式错误。 */
    object Skip : OpenAiSseEvent
}

internal data class OpenAiSseToolCall(
    val name: String?,
    val arguments: String?,
)

@Serializable
internal data class OpenAiSseChunk(
    val choices: List<OpenAiSseChoice>? = null,
)

@Serializable
internal data class OpenAiSseChoice(
    val delta: OpenAiSseDeltaDto? = null,
)

@Serializable
internal data class OpenAiSseDeltaDto(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiSseToolCallDto>? = null,
)

@Serializable
internal data class OpenAiSseToolCallDto(
    val function: OpenAiSseToolFunction? = null,
)

@Serializable
internal data class OpenAiSseToolFunction(
    val name: String? = null,
    val arguments: String? = null,
)

/**
 * 解析一行 OpenAI 协议的 SSE 数据。
 *
 * 约定：
 * - 行前缀必须是 `data: `（含尾随空格），与生产实现一致；无空格的 `data:` 会被跳过。
 * - `data: [DONE]` → [OpenAiSseEvent.Done]
 * - JSON 解析失败或 choices 为空 → [OpenAiSseEvent.Skip]（与生产历史行为一致：忽略错误继续）
 * - 同时返回 content 与 tool_calls 增量，调用方根据 enableToolCall 决定是否消费 toolCalls
 */
internal fun parseOpenAiSseLine(line: String): OpenAiSseEvent {
    if (!line.startsWith("data: ")) return OpenAiSseEvent.Skip

    val data = line.substring(6).trim()
    if (data == "[DONE]") return OpenAiSseEvent.Done
    if (data.isBlank()) return OpenAiSseEvent.Skip

    val chunk = runCatching { AppJson.decodeFromString<OpenAiSseChunk>(data) }.getOrNull()
        ?: return OpenAiSseEvent.Skip
    val delta = chunk.choices?.firstOrNull()?.delta ?: return OpenAiSseEvent.Skip

    val toolCalls = delta.toolCalls?.mapNotNull { tc ->
        val fn = tc.function ?: return@mapNotNull null
        OpenAiSseToolCall(name = fn.name, arguments = fn.arguments)
    }

    return OpenAiSseEvent.Delta(
        content = delta.content,
        toolCalls = if (toolCalls.isNullOrEmpty()) null else toolCalls,
    )
}