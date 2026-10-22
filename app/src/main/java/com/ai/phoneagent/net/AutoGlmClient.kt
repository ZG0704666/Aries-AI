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
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import com.ai.phoneagent.BuildConfig
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 共享 OkHttpClient 工厂。
 * 通过连接池复用连接，提高网络请求性能。
 */
private object SharedHttpClient {
        val instance: OkHttpClient by lazy {
                val logger =
                        HttpLoggingInterceptor().apply {
                                level =
                                        if (BuildConfig.DEBUG)
                                                HttpLoggingInterceptor.Level.BASIC
                                        else
                                                HttpLoggingInterceptor.Level.NONE
                        }
                OkHttpClient.Builder()
                        .addInterceptor(logger)
                        .retryOnConnectionFailure(true)
                        // 增加连接超时，适配慢速网络
                        .connectTimeout(60, TimeUnit.SECONDS)
                        // 读取超时设置更长，支持长时模型响应
                        .readTimeout(300, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS)
                        .callTimeout(360, TimeUnit.SECONDS)
                        // 使用连接池复用连接，提高性能
                        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                        // 支持 HTTP/2 协议
                        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                        .build()
        }

        /**
         * 自动化场景专用：使用更短超时，避免请求长时间卡住。
         * 注意：这不会让模型本身更快，但能让慢/异常连接更快失败并触发重试或降级。
         */
        val fastInstance: OkHttpClient by lazy {
                val logger =
                        HttpLoggingInterceptor().apply {
                                level =
                                        if (BuildConfig.DEBUG)
                                                HttpLoggingInterceptor.Level.BASIC
                                        else
                                                HttpLoggingInterceptor.Level.NONE
                        }
                OkHttpClient.Builder()
                        .addInterceptor(logger)
                        .retryOnConnectionFailure(true)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(25, TimeUnit.SECONDS)
                        .writeTimeout(25, TimeUnit.SECONDS)
                        .callTimeout(30, TimeUnit.SECONDS)
                        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                        .build()
        }
}

/** 简化版大模型客户端：用于单轮对话与 API 健康检查。 */
object AutoGlmClient {

        /** 当前正在进行的流式请求，用于支持外部取消。 */
        private val activeStreamCall = AtomicReference<Call?>(null)

        /** 取消正在进行的流式请求（若有）。 */
        fun cancelActiveStream() {
                activeStreamCall.getAndSet(null)?.cancel()
        }

        /** 携带 HTTP 状态码与响应体的 API 异常。 */
        class ApiException(
                val code: Int,
                val errorBody: String?,
                cause: Throwable? = null,
        ) : IOException(
                        buildString {
                            append("HTTP ")
                            append(code)
                            if (!errorBody.isNullOrBlank()) {
                                append(": ")
                                append(errorBody.take(400))
                            }
                        },
                        cause
                )

        /** API 连通性检查结果。 */
        data class ApiCheckResult(
                val ok: Boolean,
                val statusCode: Int? = null,
                val message: String? = null,
        )

        /** 流式响应中的单个 SSE 数据块。 */
        @Serializable
        private data class SseChunk(
                val choices: List<SseChoice>? = null,
        )

        /** SSE 数据块中的单个候选结果。 */
        @Serializable
        private data class SseChoice(
                val delta: SseDelta? = null,
                val message: SseMessage? = null,
        )

        /** 增量内容，区分思维链与正文。 */
        @Serializable
        private data class SseDelta(
                @SerialName("reasoning_content") val reasoningContent: String? = null,
                val reasoning: String? = null,
                val content: String? = null,
        )

        /** 非流式响应中的完整消息。 */
        @Serializable
        private data class SseMessage(
                val content: String? = null,
        )

        const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"
        const val DEFAULT_MODEL = "glm-4.6v-flash"

        /** 手机自动化场景使用的模型标识（保留上游标识以对接服务端协议）。 */
        const val PHONE_MODEL = "autoglm-phone"

        private const val DEFAULT_TEMPERATURE = 0.0f
        private const val DEFAULT_TOP_P = 0.85f
        private const val DEFAULT_FREQUENCY_PENALTY = 0.2f
        private const val DEFAULT_MAX_TOKENS = 4096

        /** 规范化 baseUrl：补全协议、校验 scheme/host，并以 `/` 结尾。 */
        private fun normalizeBaseUrl(baseUrl: String): String {
                val trimmed = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
                val withScheme =
                        if (
                                trimmed.startsWith("http://", ignoreCase = true) ||
                                        trimmed.startsWith("https://", ignoreCase = true)
                        ) {
                                trimmed
                        } else {
                                "https://$trimmed"
                        }

                val uri = runCatching { URI(withScheme) }.getOrNull()
                val scheme = uri?.scheme?.lowercase()
                val host = uri?.host

                require(!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
                        "Invalid baseUrl: $baseUrl"
                }
                require(scheme == "https" || scheme == "http") {
                        "Unsupported baseUrl scheme: $withScheme"
                }

                val canonical = "${uri.scheme}://${uri.authority}${uri.path.orEmpty()}"
                return if (canonical.endsWith("/")) canonical else "$canonical/"
        }

        /** 解析模型名，空白时回退到默认模型。 */
        private fun resolveModel(model: String?): String =
                model?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        /** 去除 API Key 中可能存在的 `Bearer ` 前缀。 */
        private fun normalizeApiKey(apiKey: String): String {
                val trimmed = apiKey.trim()
                return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                        trimmed.substringAfter(" ", "").trim()
                } else {
                        trimmed
                }
        }

        /**
         * 发送流式对话请求，逐片段回调思维链与正文内容。
         *
         * @param onReasoningDelta 思维链增量回调。
         * @param onContentDelta 正文增量回调。
         * @param shouldStop 返回 true 时主动中断流。
         * @param useFastTimeouts 是否使用更短的超时客户端。
         * @return 成功时返回 [Unit]；未收到任何增量或请求失败时返回失败。
         */
        suspend fun sendChatStreamResult(
                apiKey: String,
                messages: List<ChatRequestMessage>,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
                onReasoningDelta: (String) -> Unit,
                onContentDelta: (String) -> Unit,
                shouldStop: (() -> Boolean)? = null,
                useFastTimeouts: Boolean = false,
        ): Result<Unit> {
                return withContext(Dispatchers.IO) {
                        try {
                                val normalizedApiKey = normalizeApiKey(apiKey)
                                val reqObj =
                                        ChatRequest(
                                                model = resolveModel(model),
                                                messages = messages,
                                                stream = true,
                                                temperature = temperature,
                                                max_tokens = maxTokens,
                                                top_p = topP,
                                                frequency_penalty = frequencyPenalty,
                                        )
                                val bodyJson = AppJson.encodeToString(reqObj)
                                val requestBuilder =
                                        Request.Builder()
                                                .url(normalizeBaseUrl(baseUrl) + "chat/completions")
                                                .addHeader("Content-Type", "application/json")
                                if (normalizedApiKey.isNotBlank()) {
                                        requestBuilder.addHeader(
                                                "Authorization",
                                                "Bearer $normalizedApiKey"
                                        )
                                }
                                val request =
                                        requestBuilder
                                                .post(
                                                        bodyJson.toRequestBody(
                                                                "application/json; charset=utf-8".toMediaType()
                                                        )
                                                )
                                                .build()

                                var receivedAnyDelta = false
                                val client =
                                        if (useFastTimeouts) SharedHttpClient.fastInstance
                                        else SharedHttpClient.instance
                                val call = client.newCall(request)

                                activeStreamCall.set(call)

                                try {
                                        call.execute().use { resp ->
                                                if (!resp.isSuccessful) {
                                                        val errBody =
                                                                runCatching { resp.body?.string() }.getOrNull()
                                                        return@withContext Result.failure(
                                                                ApiException(resp.code, errBody, null)
                                                        )
                                                }

                                                val responseBody =
                                                        resp.body
                                                                ?: return@withContext Result.failure(
                                                                        IOException("Empty response body")
                                                                )
                                                val source = responseBody.source()

                                                while (!source.exhausted()) {
                                                        if (shouldStop?.invoke() == true) {
                                                                call.cancel()
                                                                break
                                                        }

                                                        val line = source.readUtf8Line() ?: break
                                                        if (line.isBlank()) continue
                                                        if (!line.startsWith("data:")) continue

                                                        val data = line.removePrefix("data:").trim()
                                                        if (data == "[DONE]") break

                                                        val chunk =
                                                                runCatching {
                                                                                AppJson.decodeFromString<SseChunk>(
                                                                                        data
                                                                                )
                                                                        }
                                                                        .getOrNull() ?: continue

                                                        val choice0 =
                                                                chunk.choices
                                                                        ?.firstOrNull()
                                                                        ?: continue

                                                        val delta = choice0.delta

                                                        if (delta != null) {
                                                                val reasoning =
                                                                        delta.reasoningContent
                                                                                ?: delta.reasoning
                                                                val content = delta.content

                                                                if (!reasoning.isNullOrEmpty())
                                                                        onReasoningDelta(reasoning)
                                                                if (!content.isNullOrEmpty())
                                                                        onContentDelta(content)
                                                                if (!reasoning.isNullOrEmpty() ||
                                                                                !content.isNullOrEmpty()
                                                                ) {
                                                                        receivedAnyDelta = true
                                                                }
                                                        } else {
                                                                val content = choice0.message?.content
                                                                if (!content.isNullOrEmpty())
                                                                        onContentDelta(content)
                                                                if (!content.isNullOrEmpty()) {
                                                                        receivedAnyDelta = true
                                                                }
                                                        }
                                                }
                                        }
                                } finally {
                                        activeStreamCall.compareAndSet(call, null)
                                }

                                if (!receivedAnyDelta) {
                                        Result.failure(IOException("Empty stream response"))
                                } else {
                                        Result.success(Unit)
                                }
                        } catch (e: Exception) {
                                Result.failure(e)
                        }
                }
        }

        /** 发送一次最小请求验证 API 是否可用。 */
        suspend fun checkApi(
                apiKey: String,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
        ): Boolean =
                withContext(Dispatchers.IO) {
                        checkApiDetailed(
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        model = model,
                                )
                                .ok
                }

        /** 发送最小请求验证 API 可用性，返回状态码与可读错误信息。 */
        suspend fun checkApiDetailed(
                apiKey: String,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
        ): ApiCheckResult =
                withContext(Dispatchers.IO) {
                        runCatching {
                                        val normalizedApiKey = normalizeApiKey(apiKey)
                                        val requestBodyJson =
                                                AppJson.encodeToString(
                                                        ChatRequest(
                                                                model = resolveModel(model),
                                                                messages =
                                                                        listOf(
                                                                                ChatRequestMessage(
                                                                                        role = "user",
                                                                                        content = "Reply with OK."
                                                                                )
                                                                        ),
                                                                stream = false,
                                                                max_tokens = 32,
                                                        )
                                                )
                                        val requestBuilder =
                                                Request.Builder()
                                                        .url(normalizeBaseUrl(baseUrl) + "chat/completions")
                                                        .addHeader("Content-Type", "application/json")
                                        if (normalizedApiKey.isNotBlank()) {
                                                requestBuilder.addHeader(
                                                        "Authorization",
                                                        "Bearer $normalizedApiKey"
                                                )
                                        }
                                        val request =
                                                requestBuilder
                                                        .post(
                                                                requestBodyJson.toRequestBody(
                                                                        "application/json; charset=utf-8"
                                                                                .toMediaType()
                                                                )
                                                        )
                                                        .build()

                                        SharedHttpClient.fastInstance.newCall(request).execute().use { response ->
                                                val code = response.code
                                                val body = response.body?.string().orEmpty()

                                                if (!response.isSuccessful) {
                                                        val reason =
                                                                extractApiErrorMessage(body)
                                                                        .ifBlank { "HTTP $code" }
                                                        return@use ApiCheckResult(
                                                                ok = false,
                                                                statusCode = code,
                                                                message = reason
                                                        )
                                                }

                                                if (body.isBlank()) {
                                                        return@use ApiCheckResult(
                                                                ok = false,
                                                                statusCode = code,
                                                                message = "API response body is empty"
                                                        )
                                                }

                                                val parsed = parseApiCheckSuccessResponse(body)
                                                if (parsed != null) {
                                                        return@use parsed.copy(statusCode = code)
                                                }

                                                ApiCheckResult(
                                                        ok = false,
                                                        statusCode = code,
                                                        message = "Unexpected API response format"
                                                )
                                        }
                                }
                                .getOrElse { e ->
                                        ApiCheckResult(
                                                ok = false,
                                                message =
                                                        e.message
                                                                ?.replace('\n', ' ')
                                                                ?.trim()
                                                                ?.take(180)
                                                                .orEmpty()
                                                                .ifBlank { "Network request failed" }
                                        )
                                }
                }

        /** 判断健康检查响应是否为合法的成功结构；无法识别时返回 null。 */
        private fun parseApiCheckSuccessResponse(body: String): ApiCheckResult? {
                val obj = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
                if (obj["error"] !is JsonNull && obj.containsKey("error")) {
                        val reason = extractApiErrorMessage(body)
                        return ApiCheckResult(ok = false, message = reason.ifBlank { "API returned an error" })
                }

                val choices = obj["choices"]?.asJsonArrayOrNull() ?: return null
                if (choices.isEmpty()) return null
                val choice0 = choices.firstOrNull()?.asJsonObjectOrNull() ?: return null
                val messageObj = choice0["message"]?.asJsonObjectOrNull() ?: return ApiCheckResult(ok = true)
                val contentEl = messageObj["content"] ?: return ApiCheckResult(ok = true)

                // 健康检查只验证连通性与响应结构合法，不校验正文内容。
                if (contentEl is JsonNull) return ApiCheckResult(ok = true)
                extractMessageContentText(contentEl)
                return ApiCheckResult(ok = true)
        }

        /** 从响应体中提取错误信息，依次尝试 `error.message`、`message`，最后回退原文。 */
        private fun extractApiErrorMessage(body: String): String {
                val fallback = body.replace('\n', ' ').replace('\r', ' ').trim().take(220)
                val obj = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return fallback
                val errorObj = obj["error"]?.asJsonObjectOrNull()
                val fromError =
                        errorObj?.get("message")
                                ?.asStringOrNull()
                                ?.trim()
                                .orEmpty()
                if (fromError.isNotBlank()) return fromError.take(220)

                val fromMessage =
                        obj["message"]
                                ?.asStringOrNull()
                                ?.trim()
                                .orEmpty()
                return if (fromMessage.isNotBlank()) fromMessage.take(220) else fallback
        }

        /** 将消息 content 字段（字符串或分段数组）归一为纯文本。 */
        private fun extractMessageContentText(contentEl: JsonElement): String? {
                if (contentEl is JsonNull) return null
                if (contentEl is JsonPrimitive) {
                        return contentEl.contentOrNull
                }
                if (contentEl is JsonArray) {
                        val parts =
                                contentEl.mapNotNull { part ->
                                        when {
                                                part is JsonNull -> null
                                                part is JsonPrimitive -> part.contentOrNull
                                                part is JsonObject -> {
                                                        part["text"]?.asStringOrNull()
                                                }
                                                else -> null
                                        }
                                }
                        return if (parts.isEmpty()) null else parts.joinToString("\n")
                }
                return null
        }

        /** 安全转换为 [JsonObject]，类型不符时返回 null。 */
        private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
                this as? JsonObject

        /** 安全转换为 [JsonArray]，类型不符时返回 null。 */
        private fun JsonElement.asJsonArrayOrNull(): JsonArray? =
                this as? JsonArray

        /** 安全提取字符串原始值，非字符串返回 null。 */
        private fun JsonElement.asStringOrNull(): String? =
                (this as? JsonPrimitive)?.contentOrNull

        /** 发送非流式对话，成功返回正文，失败返回 null。 */
        suspend fun sendChat(
                apiKey: String,
                messages: List<ChatRequestMessage>,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
        ): String? =
                sendChatResult(
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                messages = messages,
                                model = model,
                                temperature = temperature,
                                maxTokens = maxTokens,
                                topP = topP,
                                frequencyPenalty = frequencyPenalty,
                        )
                        .getOrNull()

        /** 发送非流式对话并以 [Result] 返回正文或错误。 */
        suspend fun sendChatResult(
                apiKey: String,
                messages: List<ChatRequestMessage>,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
                /** 自动化场景可启用更短超时，避免长时间卡住 */
                useFastTimeouts: Boolean = false,
        ): Result<String> {
                return withContext(Dispatchers.IO) {
                        try {
                                val normalizedApiKey = normalizeApiKey(apiKey)
                                val reqObj =
                                        ChatRequest(
                                                model = resolveModel(model),
                                                messages = messages,
                                                stream = false,
                                                temperature = temperature,
                                                max_tokens = maxTokens,
                                                top_p = topP,
                                                frequency_penalty = frequencyPenalty,
                                        )
                                val bodyJson = AppJson.encodeToString(reqObj)
                                val requestBuilder =
                                        Request.Builder()
                                                .url(normalizeBaseUrl(baseUrl) + "chat/completions")
                                                .addHeader("Content-Type", "application/json")
                                if (normalizedApiKey.isNotBlank()) {
                                        requestBuilder.addHeader(
                                                "Authorization",
                                                "Bearer $normalizedApiKey"
                                        )
                                }
                                val request =
                                        requestBuilder
                                                .post(
                                                        bodyJson.toRequestBody(
                                                                "application/json; charset=utf-8".toMediaType()
                                                        )
                                                )
                                                .build()
                                val client =
                                        if (useFastTimeouts) SharedHttpClient.fastInstance
                                        else SharedHttpClient.instance
                                client.newCall(request).execute().use { response ->
                                        if (!response.isSuccessful) {
                                                val errBody =
                                                        runCatching { response.body?.string() }.getOrNull()
                                                return@withContext Result.failure(
                                                        ApiException(response.code, errBody, null)
                                                )
                                        }
                                        val body = response.body?.string()
                                        if (body.isNullOrBlank()) {
                                                return@withContext Result.failure(
                                                        IOException("Empty response body")
                                                )
                                        }
                                        val res = AppJson.decodeFromString<ChatResponse>(body)
                                        val content = res.choices?.firstOrNull()?.message?.content
                                        if (content.isNullOrBlank()) {
                                                Result.failure(IOException("Empty model response"))
                                        } else {
                                                Result.success(content)
                                        }
                                }
                        } catch (e: Exception) {
                                Result.failure(e)
                        }
                }
        }
}
