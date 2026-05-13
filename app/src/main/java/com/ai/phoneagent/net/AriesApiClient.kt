package com.ai.phoneagent.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Aries API 管理接口客户端（基于 New API 协议）。
 *
 * 鉴权流程：
 *   1. POST /api/user/login  → Session Cookie
 *   2. POST /api/token/      → API Key (sk-...)
 *
 * 获取的 API Key 可直接用于 OpenAI 兼容接口。
 */
object AriesApiClient {
    const val BASE_URL = "https://api.aries.org.cn"
    const val ARIES_API_V1_BASE_URL = "https://api.aries.org.cn/v1"
    const val ARIES_CHAT_MODEL = "星环"
    const val ARIES_VISION_MODEL = "星环 Pro"
    const val ARIES_AUTOMATION_MODEL = "GUI"
    private const val ARIES_TOKEN_NAME = "Ariesphone"
    private val COOKIE_ATTRIBUTE_NAMES =
        setOf(
            "comment",
            "commenturl",
            "domain",
            "expires",
            "httponly",
            "max-age",
            "partitioned",
            "path",
            "priority",
            "samesite",
            "secure",
            "version",
        )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // ─── 数据模型 ────────────────────────────────────────────────────────────

    data class LoginResult(
        val success: Boolean,
        val userId: Int = 0,
        val message: String = "",
    )

    data class TokenResult(
        val success: Boolean,
        val apiKey: String = "",
        val tokenId: Int = 0,
        val message: String = "",
    )

    data class LoginAndTokenResult(
        val success: Boolean,
        val apiKey: String = "",
        val username: String = "",
        val message: String = "",
    )

    data class ModelInfo(
        val id: String,
        val ownedBy: String = "",
    )

    private data class TokenSummary(
        val id: Int,
        val name: String,
    )

    // ─── Session 管理 ────────────────────────────────────────────────────────

    /** 简单内存 CookieJar，仅用于同一次登录流程。 */
    private class InMemoryCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()
    }

    private fun buildClient(): OkHttpClient {
        val cookieJar = InMemoryCookieJar()
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    internal fun normalizeCookieHeader(cookieHeader: String): String {
        if (cookieHeader.isBlank()) return ""
        val normalizedCookies = linkedMapOf<String, Pair<String, String>>()
        cookieHeader.split(';').forEach { segment ->
            val token = segment.trim()
            if (token.isBlank()) return@forEach
            val separatorIndex = token.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            val name = token.substring(0, separatorIndex).trim()
            val lowerName = name.lowercase()
            if (lowerName in COOKIE_ATTRIBUTE_NAMES) return@forEach
            val value = token.substring(separatorIndex + 1).trim()
            if (value.isBlank()) return@forEach
            normalizedCookies[lowerName] = name to value
        }
        return normalizedCookies.values.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    private fun buildCookieHeaderClient(cookieHeader: String): OkHttpClient {
        val normalizedCookieHeader = normalizeCookieHeader(cookieHeader)
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val updatedRequest =
                    request.newBuilder()
                        .apply {
                            if (request.header("Cookie").isNullOrBlank() && normalizedCookieHeader.isNotBlank()) {
                                header("Cookie", normalizedCookieHeader)
                            }
                        }.build()
                chain.proceed(updatedRequest)
            }.build()
    }

    // ─── 接口实现 ────────────────────────────────────────────────────────────

    /**
     * 登录并自动创建 API Token，一步获取可用的 API Key。
     *
     * @param username 账号用户名
     * @param password 账号密码
     * @return [LoginAndTokenResult] 包含最终 API Key 或错误信息
     */
    suspend fun loginAndGetApiKey(username: String, password: String): LoginAndTokenResult =
        withContext(Dispatchers.IO) {
            // 同一个 client 实例共享 CookieJar，保证 session 在两步请求间传递
            val client = buildClient()

            val loginResult = login(client, username.trim(), password)
            if (!loginResult.success) {
                return@withContext LoginAndTokenResult(
                    success = false,
                    message = loginResult.message,
                )
            }

            val tokenResult = getOrCreateAriesToken(client, loginResult.userId)
            if (!tokenResult.success) {
                return@withContext LoginAndTokenResult(
                    success = false,
                    message = tokenResult.message,
                )
            }

            LoginAndTokenResult(
                success = true,
                apiKey = tokenResult.apiKey,
                username = username.trim(),
            )
        }

    suspend fun getOrCreateAriesTokenWithUserAccessToken(
        userAccessToken: String,
        userId: Int,
    ): TokenResult =
        withContext(Dispatchers.IO) {
            val token = userAccessToken.trim()
            if (token.isBlank()) {
                return@withContext TokenResult(success = false, message = "Aries API 用户登录 token 为空")
            }
            val resolvedUserId =
                if (userId > 0) {
                    userId
                } else {
                    fetchCurrentUserId(buildClient(), token).getOrDefault(0)
                }
            getOrCreateAriesToken(
                client = buildClient(),
                userId = resolvedUserId,
                userAccessToken = token,
            )
        }

    suspend fun getOrCreateAriesTokenWithAuthenticatedClient(
        client: OkHttpClient,
        userId: Int,
    ): TokenResult =
        withContext(Dispatchers.IO) {
            getOrCreateAriesToken(
                client = client,
                userId = userId,
            )
        }

    suspend fun getOrCreateAriesTokenWithSessionCookie(
        cookieHeader: String,
        userId: Int,
    ): TokenResult =
        withContext(Dispatchers.IO) {
            val normalizedCookieHeader = normalizeCookieHeader(cookieHeader)
            if (normalizedCookieHeader.isBlank()) {
                return@withContext TokenResult(success = false, message = "Aries API 登录会话 Cookie 为空")
            }
            val client = buildCookieHeaderClient(normalizedCookieHeader)
            val currentUserIdResult =
                if (userId > 0) {
                    Result.success(userId)
                } else {
                    fetchCurrentUserId(client)
                }
            val resolvedUserId = currentUserIdResult.getOrDefault(0)
            val tokenResult = getOrCreateAriesToken(
                client = client,
                userId = resolvedUserId,
            )
            val currentUserError = currentUserIdResult.exceptionOrNull()?.message?.trim().orEmpty()
            if (tokenResult.success) {
                return@withContext tokenResult
            }
            if (resolvedUserId <= 0 && currentUserError.isNotBlank()) {
                return@withContext tokenResult.copy(
                    message = "Aries API 登录会话存在，但无法解析当前用户: $currentUserError；${tokenResult.message}",
                )
            }
            tokenResult
        }

    // ─── 私有步骤 ────────────────────────────────────────────────────────────

    private fun login(client: OkHttpClient, username: String, password: String): LoginResult {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/api/user/login")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                parseLoginResponse(raw, response.code)
            }
        } catch (e: Exception) {
            LoginResult(success = false, message = e.message?.trim().orEmpty().ifBlank { "网络请求失败" })
        }
    }

    private fun parseLoginResponse(raw: String, code: Int): LoginResult {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val success = obj["success"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
                ?: (obj["success"]?.jsonPrimitive?.content == "true")
            val message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val dataObj = obj["data"]?.jsonObject
            val userId = dataObj?.get("id")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            if (success) {
                LoginResult(success = true, userId = userId, message = message)
            } else {
                LoginResult(success = false, message = message.ifBlank { "登录失败 (HTTP $code)" })
            }
        } catch (e: Exception) {
            LoginResult(success = false, message = "响应解析失败: ${e.message?.trim()}")
        }
    }

    private fun getOrCreateAriesToken(
        client: OkHttpClient,
        userId: Int,
        userAccessToken: String = "",
    ): TokenResult {
        val existingToken = findTokenByName(client, userId, ARIES_TOKEN_NAME, userAccessToken)
            .getOrElse { return TokenResult(success = false, message = it.message.orEmpty()) }
        existingToken?.let { return fetchTokenKeyById(client, userId, it.id, userAccessToken) }
        return createToken(client, userId, userAccessToken)
    }

    private fun fetchCurrentUserId(client: OkHttpClient, userAccessToken: String = ""): Result<Int> {
        val request = Request.Builder()
            .url("$BASE_URL/api/user/self")
            .get()
            .apply {
                if (userAccessToken.isNotBlank()) {
                    addHeader("Authorization", "Bearer $userAccessToken")
                }
            }.build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(raw).jsonObject
                if (!isSuccessResponse(obj)) {
                    return Result.failure(Exception(obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                }
                val id = obj["data"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                if (id > 0) {
                    Result.success(id)
                } else {
                    Result.failure(Exception("当前用户信息缺少 id (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("当前用户信息读取失败: ${e.message?.trim()}"))
        }
    }

    private fun findTokenByName(
        client: OkHttpClient,
        userId: Int,
        tokenName: String,
        userAccessToken: String = "",
    ): Result<TokenSummary?> {
        val request = Request.Builder()
            .url("$BASE_URL/api/token/?p=1&size=100&page_size=100")
            .get()
            .apply {
                if (userAccessToken.isNotBlank()) addHeader("Authorization", "Bearer $userAccessToken")
                if (userId > 0) addHeader("New-Api-User", userId.toString())
            }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                parseTokenListResponse(raw, response.code)
                    .map { tokens -> tokens.firstOrNull { it.name == tokenName } }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Token 列表查询失败: ${e.message?.trim()}"))
        }
    }

    private fun createToken(
        client: OkHttpClient,
        userId: Int,
        userAccessToken: String = "",
    ): TokenResult {
        val body = buildJsonObject {
            put("name", ARIES_TOKEN_NAME)
            put("remain_quota", 0)
            put("unlimited_quota", true)
            put("expired_time", -1)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/api/token/")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (userAccessToken.isNotBlank()) addHeader("Authorization", "Bearer $userAccessToken")
                if (userId > 0) addHeader("New-Api-User", userId.toString())
            }
            .build()

        return try {
            val raw: String
            val code: Int
            client.newCall(request).execute().use { response ->
                code = response.code
                raw = response.body?.string().orEmpty()
            }
            val result = parseTokenResponse(raw, code)
            when {
                result.success -> result
                result.tokenId > 0 -> fetchTokenKeyById(client, userId, result.tokenId, userAccessToken)
                isSuccessResponse(raw) -> fetchCreatedTokenKeyByName(client, userId, ARIES_TOKEN_NAME, userAccessToken)
                else -> result
            }
        } catch (e: Exception) {
            TokenResult(success = false, message = e.message?.trim().orEmpty().ifBlank { "Token 创建失败" })
        }
    }

    private fun fetchCreatedTokenKeyByName(
        client: OkHttpClient,
        userId: Int,
        tokenName: String,
        userAccessToken: String = "",
    ): TokenResult {
        val token = findTokenByName(client, userId, tokenName, userAccessToken)
            .getOrElse { return TokenResult(success = false, message = it.message.orEmpty()) }
        return if (token != null) {
            fetchTokenKeyById(client, userId, token.id, userAccessToken)
        } else {
            TokenResult(success = false, message = "Token 已创建，但未能在列表中找到对应 ID")
        }
    }

    private fun fetchTokenKeyById(
        client: OkHttpClient,
        userId: Int,
        tokenId: Int,
        userAccessToken: String = "",
    ): TokenResult {
        val request = Request.Builder()
            .url("$BASE_URL/api/token/$tokenId/key")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (userAccessToken.isNotBlank()) addHeader("Authorization", "Bearer $userAccessToken")
                if (userId > 0) addHeader("New-Api-User", userId.toString())
            }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                parseTokenKeyResponse(raw, response.code, tokenId)
            }
        } catch (e: Exception) {
            TokenResult(success = false, message = "Token Key 查询失败: ${e.message?.trim()}")
        }
    }

    private fun parseTokenListResponse(raw: String, code: Int): Result<List<TokenSummary>> {
        if (raw.isBlank()) return Result.failure(Exception("Token 列表响应为空 (HTTP $code)"))
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            if (!isSuccessResponse(obj)) {
                val message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                return Result.failure(Exception(message.ifBlank { "Token 列表查询失败 (HTTP $code)" }))
            }
            val dataObj = obj["data"]?.jsonObject
                ?: return Result.failure(Exception("Token 列表响应中无 data 字段"))
            val items = dataObj["items"]?.jsonArray
                ?: return Result.failure(Exception("Token 列表响应中无 items 字段"))
            val tokens = items.mapNotNull { element ->
                val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val id = item["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                TokenSummary(id = id, name = name)
            }
            Result.success(tokens)
        } catch (e: Exception) {
            Result.failure(Exception("Token 列表解析失败: ${e.message?.trim()} body=$raw"))
        }
    }

    private fun parseTokenKeyResponse(raw: String, code: Int, tokenId: Int): TokenResult {
        if (raw.isBlank()) return TokenResult(success = false, message = "Token Key 响应为空 (HTTP $code)")
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val dataObj = obj["data"]?.jsonObject
            val key = dataObj?.get("key")?.jsonPrimitive?.contentOrNull
                ?: obj["key"]?.jsonPrimitive?.contentOrNull
                ?: ""
            if (isSuccessResponse(obj) && key.isNotBlank()) {
                TokenResult(success = true, apiKey = key, tokenId = tokenId, message = message)
            } else {
                TokenResult(
                    success = false,
                    tokenId = tokenId,
                    message = message.ifBlank { "Token Key 查询失败 (HTTP $code) body=$raw" },
                )
            }
        } catch (e: Exception) {
            TokenResult(success = false, tokenId = tokenId, message = "Token Key 响应解析失败: ${e.message?.trim()} body=$raw")
        }
    }

    private fun parseTokenResponse(raw: String, code: Int): TokenResult {
        if (raw.isBlank()) return TokenResult(success = false, message = "响应为空 (HTTP $code)")
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            // 兼容 JSON boolean 和 JSON string 两种 success 格式
            val success = isSuccessResponse(obj)
            val message = obj["message"]?.jsonPrimitive?.content.orEmpty()
            // data 可能是 object（含 key 字段）或 integer（仅 token ID）
            val dataElement = obj["data"]
            val dataObj = runCatching { dataElement?.jsonObject }.getOrNull()
            val dataId = if (dataObj == null) {
                runCatching { dataElement?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull() ?: 0
            } else 0
            val key = dataObj?.get("key")?.jsonPrimitive?.content.orEmpty()
            val tokenId = dataObj?.get("id")?.jsonPrimitive?.content?.toIntOrNull() ?: dataId
            when {
                success && key.isNotBlank() ->
                    TokenResult(success = true, apiKey = key, tokenId = tokenId, message = message)
                success && tokenId > 0 ->
                    // data 是 integer ID，需上层补充 GET 拉取 key
                    TokenResult(success = false, apiKey = "", tokenId = tokenId, message = message)
                else ->
                    TokenResult(
                        success = false,
                        message = message.ifBlank { "Token 创建失败 (HTTP $code) body=$raw" },
                    )
            }
        } catch (e: Exception) {
            TokenResult(success = false, message = "响应解析失败: ${e.message?.trim()} body=$raw")
        }
    }

    private fun isSuccessResponse(raw: String): Boolean =
        runCatching { isSuccessResponse(json.parseToJsonElement(raw).jsonObject) }.getOrDefault(false)

    private fun isSuccessResponse(obj: JsonObject): Boolean {
        val successPrimitive = obj["success"]?.jsonPrimitive
        return successPrimitive?.booleanOrNull
            ?: successPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
            ?: false
    }

    // ─── 模型列表 ────────────────────────────────────────────────────────────

    /**
     * 使用已获取的 API Key 获取可用模型列表。
     *
     * @param apiKey Bearer token（sk-...）
     */
    suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/v1/models")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    parseModelsResponse(raw, response.code)
                }
            } catch (e: Exception) {
                Result.failure(Exception("获取模型列表失败: ${e.message?.trim()}"))
            }
        }

    private fun parseModelsResponse(raw: String, code: Int): Result<List<ModelInfo>> {
        if (raw.isBlank()) return Result.failure(Exception("模型列表响应为空 (HTTP $code)"))
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val dataArray = obj["data"]?.jsonArray
                ?: return Result.failure(Exception("响应中无 data 字段"))
            val models = dataArray.mapNotNull { element ->
                val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val ownedBy = o["owned_by"]?.jsonPrimitive?.contentOrNull.orEmpty()
                ModelInfo(id = id, ownedBy = ownedBy)
            }.sortedBy { it.id }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(Exception("模型列表解析失败: ${e.message?.trim()}"))
        }
    }
}
