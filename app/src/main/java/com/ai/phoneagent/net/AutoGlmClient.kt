package com.ai.phoneagent.net

import okhttp3.OkHttpClient
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

        private val service: AutoGlmService by lazy {
                val logger =
                        HttpLoggingInterceptor().apply {
                                level =
                                        if (BuildConfig.DEBUG)
                                                HttpLoggingInterceptor.Level.BASIC
                                        else
                                                HttpLoggingInterceptor.Level.NONE
                        }
                val client =
                        OkHttpClient.Builder()
                                .addInterceptor(logger)
                                .retryOnConnectionFailure(true)
                                .connectTimeout(20, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .writeTimeout(60, TimeUnit.SECONDS)
                                .callTimeout(90, TimeUnit.SECONDS)
                                .build()

                Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(client)
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
                temperature: Float? = null,
                maxTokens: Int? = null,
        ): String? =
                sendChatResult(
                                apiKey = apiKey,
                                messages = messages,
                                model = model,
                                temperature = temperature,
                                maxTokens = maxTokens
                        )
                        .getOrNull()

        suspend fun sendChatResult(
                apiKey: String,
                messages: List<ChatRequestMessage>,
                model: String = DEFAULT_MODEL,
                temperature: Float? = null,
                maxTokens: Int? = null,
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
                                                        max_tokens = maxTokens
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
