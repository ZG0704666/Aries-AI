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

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.ai.phoneagent.BuildConfig
import java.io.IOException
import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 共享 OkHttpClient 工厂
 * 使用连接池复用连接，提高性能
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
                        // 增加连接超时以适应慢速网络
                        .connectTimeout(60, TimeUnit.SECONDS)
                        // 设置较长的读写超时以支持长时间模型响应
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
         * 自动化场景专用：更短的超时，避免“请求模型”阶段长时间卡住。
         * 注意：这不会让模型本身更快，但能让慢/异常连接更快失败并触发重试/降级。
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

/** 简化版 AutoGLM 客户端：仅用于单轮对话与 API 健康检查。 默认 BASE_URL 指向智谱官方 OpenAI 兼容接口，可根据需要调整。 */
object AutoGlmClient {

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

        // 如需替换其他网关，可修改此处
        const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"
        const val DEFAULT_MODEL = "glm-4-flash"
        const val PHONE_MODEL = "autoglm-phone"

        private const val DEFAULT_TEMPERATURE = 0.0f
        private const val DEFAULT_TOP_P = 0.85f
        private const val DEFAULT_FREQUENCY_PENALTY = 0.2f
        private const val DEFAULT_MAX_TOKENS = 4096

        private const val SERVICE_CACHE_MAX_ENTRIES = 8

        private val serviceCacheLock = Any()
        private val fastServiceCacheLock = Any()

        private val serviceCache =
                object : LinkedHashMap<String, AutoGlmService>(16, 0.75f, true) {
                        override fun removeEldestEntry(
                                eldest: MutableMap.MutableEntry<String, AutoGlmService>?
                        ): Boolean {
                                return size > SERVICE_CACHE_MAX_ENTRIES
                        }
                }
        private val fastServiceCache =
                object : LinkedHashMap<String, AutoGlmService>(16, 0.75f, true) {
                        override fun removeEldestEntry(
                                eldest: MutableMap.MutableEntry<String, AutoGlmService>?
                        ): Boolean {
                                return size > SERVICE_CACHE_MAX_ENTRIES
                        }
                }

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

        private fun resolveModel(model: String?): String =
                model?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        private fun normalizeApiKey(apiKey: String): String {
                val trimmed = apiKey.trim()
                return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                        trimmed.substringAfter(" ", "").trim()
                } else {
                        trimmed
                }
        }

        private fun getService(baseUrl: String): AutoGlmService {
                val normalized = normalizeBaseUrl(baseUrl)
                synchronized(serviceCacheLock) {
                        serviceCache[normalized]?.let { return it }
                        val created =
                                Retrofit.Builder()
                                        .baseUrl(normalized)
                                        .client(SharedHttpClient.instance)
                                        .addConverterFactory(GsonConverterFactory.create())
                                        .build()
                                        .create(AutoGlmService::class.java)
                        serviceCache[normalized] = created
                        return created
                }
        }

        private fun getFastService(baseUrl: String): AutoGlmService {
                val normalized = normalizeBaseUrl(baseUrl)
                synchronized(fastServiceCacheLock) {
                        fastServiceCache[normalized]?.let { return it }
                        val created =
                                Retrofit.Builder()
                                        .baseUrl(normalized)
                                        .client(SharedHttpClient.fastInstance)
                                        .addConverterFactory(GsonConverterFactory.create())
                                        .build()
                                        .create(AutoGlmService::class.java)
                        fastServiceCache[normalized] = created
                        return created
                }
        }

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
                /**
                 * 自动化早停：当上层已经拿到可解析的动作时可主动停止流式读取，缩短尾部等待。
                 * 返回值仍按 success 处理（只要已收到过任意 delta）。
                 */
                shouldStop: (() -> Boolean)? = null,
                /** 自动化场景可启用更短超时，避免长卡住 */
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
                                val bodyJson = Gson().toJson(reqObj)
                                val request =
                                        Request.Builder()
                                                .url(normalizeBaseUrl(baseUrl) + "chat/completions")
                                                .addHeader("Authorization", "Bearer $normalizedApiKey")
                                                .addHeader("Content-Type", "application/json")
                                                .post(
                                                        bodyJson.toRequestBody(
                                                                "application/json; charset=utf-8".toMediaType()
                                                        )
                                                )
                                                .build()

                                var receivedAnyDelta = false

                                val client = if (useFastTimeouts) SharedHttpClient.fastInstance else SharedHttpClient.instance
                                val call = client.newCall(request)

                                call.execute().use { resp ->
                                        if (!resp.isSuccessful) {
                                                val errBody =
                                                        runCatching { resp.body?.string() }.getOrNull()
                                                return@withContext Result.failure(
                                                        ApiException(resp.code, errBody, null)
                                                )
                                        }

                                        val responseBody = resp.body
                                                ?: return@withContext Result.failure(
                                                        IOException("Empty response body")
                                                )

                                        val source = responseBody.source()

                                        while (!source.exhausted()) {
                                                // 尽早响应取消/早停，避免阻塞“请求模型”阶段
                                                if (shouldStop?.invoke() == true) {
                                                        call.cancel()
                                                        break
                                                }

                                                val line = source.readUtf8Line() ?: break
                                                if (line.isBlank()) continue
                                                if (!line.startsWith("data:")) continue

                                                val data = line.removePrefix("data:").trim()
                                                if (data == "[DONE]") break

                                                val obj =
                                                        runCatching {
                                                                        JsonParser.parseString(data).asJsonObject
                                                                }
                                                                .getOrNull() ?: continue

                                                val choices = obj.getAsJsonArray("choices") ?: continue
                                                if (choices.size() == 0) continue
                                                val choice0 = choices[0].asJsonObject

                                                val deltaEl = choice0.get("delta")
                                                val delta = if (deltaEl != null && deltaEl.isJsonObject) deltaEl.asJsonObject else null
                                                if (delta != null) {
                                                        val reasoningEl =
                                                                delta.get("reasoning_content")
                                                                        ?: delta.get("reasoning")
                                                        val contentEl = delta.get("content")
                                                        val reasoning =
                                                                if (reasoningEl != null && !reasoningEl.isJsonNull)
                                                                        reasoningEl.asString
                                                                else null
                                                        val content =
                                                                if (contentEl != null && !contentEl.isJsonNull)
                                                                        contentEl.asString
                                                                else null

                                                        if (!reasoning.isNullOrEmpty()) onReasoningDelta(reasoning)
                                                        if (!content.isNullOrEmpty()) onContentDelta(content)

                                                        if (!reasoning.isNullOrEmpty() || !content.isNullOrEmpty()) {
                                                                receivedAnyDelta = true
                                                        }
                                                } else {
                                                        val messageEl = choice0.get("message")
                                                        val message =
                                                                if (messageEl != null && messageEl.isJsonObject)
                                                                        messageEl.asJsonObject
                                                                else null
                                                        val contentEl = message?.get("content")
                                                        val content =
                                                                if (contentEl != null && !contentEl.isJsonNull)
                                                                        contentEl.asString
                                                                else null
                                                        if (!content.isNullOrEmpty()) onContentDelta(content)

                                                        if (!content.isNullOrEmpty()) {
                                                                receivedAnyDelta = true
                                                        }
                                                }
                                        }
                                }

                                if (!receivedAnyDelta) {
                                        Result.failure(IOException("Empty stream response"))
                                } else {
                                        Result.success(Unit)
                                }
                        } catch (e: HttpException) {
                                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                                Result.failure(ApiException(e.code(), body, e))
                        } catch (e: Exception) {
                                Result.failure(e)
                        }
                }
        }

        suspend fun checkApi(
                apiKey: String,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
        ): Boolean =
                runCatching {
                                val normalizedApiKey = normalizeApiKey(apiKey)
                                val res =
                                        getService(baseUrl).chat(
                                                auth = "Bearer $normalizedApiKey",
                                                request =
                                                        ChatRequest(
                                                                model = resolveModel(model),
                                                                messages =
                                                                        listOf(
                                                                                ChatRequestMessage(
                                                                                        role =
                                                                                                "user",
                                                                                        content =
                                                                                                "ping"
                                                                                )
                                                                        ),
                                                                stream = false
                                                        )
                                        )
                                !res.choices.isNullOrEmpty()
                        }
                        .getOrDefault(false)

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

        suspend fun sendChatResult(
                apiKey: String,
                messages: List<ChatRequestMessage>,
                baseUrl: String = DEFAULT_BASE_URL,
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
                /** 自动化场景可启用更短超时，避免长卡住 */
                useFastTimeouts: Boolean = false,
        ): Result<String> {
                return try {
                        val normalizedApiKey = normalizeApiKey(apiKey)
                        val svc = if (useFastTimeouts) getFastService(baseUrl) else getService(baseUrl)
                        val res =
                                svc.chat(
                                        auth = "Bearer $normalizedApiKey",
                                        request =
                                                ChatRequest(
                                                        model = resolveModel(model),
                                                        messages = messages,
                                                        stream = false,
                                                        temperature = temperature,
                                                        max_tokens = maxTokens,
                                                        top_p = topP,
                                                        frequency_penalty = frequencyPenalty,
                                                )
                                )
                        val content = res.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                                Result.failure(IOException("Empty model response"))
                        } else {
                                Result.success(content)
                        }
                } catch (e: HttpException) {
                        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                        Result.failure(ApiException(e.code(), body, e))
                } catch (e: Exception) {
                        Result.failure(e)
                }
        }
}

interface AutoGlmService {
        @POST("chat/completions")
        suspend fun chat(
                @Header("Authorization") auth: String,
                @Body request: ChatRequest
        ): ChatResponse
}
