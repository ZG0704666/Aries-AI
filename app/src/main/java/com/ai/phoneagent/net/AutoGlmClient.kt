package com.ai.phoneagent.net

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.ai.phoneagent.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        private const val BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"
        private const val DEFAULT_MODEL = "glm-4-flash"
        const val PHONE_MODEL = "autoglm-phone"

        private const val DEFAULT_TEMPERATURE = 0.0f
        private const val DEFAULT_TOP_P = 0.85f
        private const val DEFAULT_FREQUENCY_PENALTY = 0.2f
        private const val DEFAULT_MAX_TOKENS = 3000

        private val service: AutoGlmService by lazy {
                Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(SharedHttpClient.instance)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(AutoGlmService::class.java)
        }

        suspend fun checkApi(apiKey: String, model: String = DEFAULT_MODEL): Boolean =
                runCatching {
                                val res =
                                        service.chat(
                                                auth = "Bearer $apiKey",
                                                request =
                                                        ChatRequest(
                                                                model = model,
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
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
        ): String? =
                sendChatResult(
                                apiKey = apiKey,
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
                model: String = DEFAULT_MODEL,
                temperature: Float? = DEFAULT_TEMPERATURE,
                maxTokens: Int? = DEFAULT_MAX_TOKENS,
                topP: Float? = DEFAULT_TOP_P,
                frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,
        ): Result<String> {
                return try {
                        val res =
                                service.chat(
                                        auth = "Bearer $apiKey",
                                        request =
                                                ChatRequest(
                                                        model = model,
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
