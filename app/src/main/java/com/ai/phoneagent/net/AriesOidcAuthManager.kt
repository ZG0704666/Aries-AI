package com.ai.phoneagent.net

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Aries 通行证 -> Aries API 的两段式 OAuth 登录。
 *
 * Aries 通行证只负责 OIDC 身份；`api.aries.org.cn` 作为 New API 站点完成
 * OIDC callback、创建站点用户 session，并返回站点用户 access token。
 */
class AriesOidcAuthManager(
    @Suppress("UNUSED_PARAMETER") application: Application,
) {
    data class AuthResult(
        val success: Boolean,
        val accessToken: String = "",
        val displayName: String = "",
        val message: String = "",
    )

    private data class ApiOAuthConfig(
        val state: String,
        val clientId: String,
        val authorizationEndpoint: String,
        val redirectUri: String,
    )

    private class InMemoryCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path }
            }
            val now = System.currentTimeMillis()
            store.addAll(cookies.filter { it.expiresAt > now })
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }

        fun loadForUrl(url: String): List<Cookie> =
            loadForRequest(url.toHttpUrl())
    }

    suspend fun signIn(activity: Activity): AuthResult {
        val cookieJar = InMemoryCookieJar()
        val client =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        val config =
            withContext(Dispatchers.IO) {
                fetchApiOAuthConfig(client)
            }.getOrElse { error ->
                return AuthResult(
                    success = false,
                    message = error.message?.trim().orEmpty().ifBlank { "获取 Aries API 登录配置失败" },
                )
            }

        val authUrl = buildAuthorizationUrl(config)
        val apiLoginResult =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    AriesApiOAuthActivity.pendingSession =
                        AriesApiOAuthActivity.PendingSession(
                            client = client,
                            callbackApiUrl = "${AriesApiClient.BASE_URL}/api/oauth/oidc",
                            expectedRedirectUri = config.redirectUri,
                            initialCookies = cookieJar.loadForUrl(AriesApiClient.BASE_URL).map { it.toString() },
                            currentCookies = {
                                cookieJar.loadForUrl(AriesApiClient.BASE_URL).map { "${it.name}=${it.value}" }
                            },
                            completion = { result ->
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            },
                        )
                    activity.startActivity(
                        Intent(activity, AriesApiOAuthActivity::class.java)
                            .putExtra(AriesApiOAuthActivity.EXTRA_AUTH_URL, authUrl),
                    )
                }
            }
        if (!apiLoginResult.success) {
            return AuthResult(success = false, message = apiLoginResult.message)
        }
        if (apiLoginResult.apiKey.isNotBlank()) {
            return AuthResult(
                success = true,
                accessToken = apiLoginResult.apiKey,
                displayName = apiLoginResult.displayName.ifBlank { "Aries API" },
            )
        }

        val tokenResult =
            withContext(Dispatchers.IO) {
                if (apiLoginResult.userAccessToken.isNotBlank()) {
                    AriesApiClient.getOrCreateAriesTokenWithUserAccessToken(
                        userAccessToken = apiLoginResult.userAccessToken,
                        userId = apiLoginResult.userId,
                    )
                } else if (apiLoginResult.sessionCookieHeader.isNotBlank()) {
                    AriesApiClient.getOrCreateAriesTokenWithSessionCookie(
                        cookieHeader = apiLoginResult.sessionCookieHeader,
                        userId = apiLoginResult.userId,
                    )
                } else {
                    AriesApiClient.getOrCreateAriesTokenWithAuthenticatedClient(
                        client = client,
                        userId = apiLoginResult.userId,
                    )
                }
            }
        return if (tokenResult.success) {
            AuthResult(
                success = true,
                accessToken = tokenResult.apiKey,
                displayName = apiLoginResult.displayName.ifBlank { "Aries API" },
            )
        } else {
            AuthResult(success = false, message = tokenResult.message.ifBlank { "Aries API Token 获取失败" })
        }
    }

    suspend fun signOut(): String? = null

    suspend fun getApiAccessToken(): AuthResult =
        AuthResult(success = false, message = "Aries API 登录已过期，请重新登录")

    private fun fetchApiOAuthConfig(client: OkHttpClient): Result<ApiOAuthConfig> {
        val statusRequest = Request.Builder()
            .url("${AriesApiClient.BASE_URL}/api/status")
            .get()
            .build()
        val statusRaw =
            client.newCall(statusRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("站点状态读取失败 HTTP ${response.code}"))
                }
                response.body?.string().orEmpty()
            }
        val statusObj = Json.parseToJsonElement(statusRaw).jsonObject
        val dataObj = statusObj["data"]?.jsonObject
            ?: return Result.failure(Exception("站点状态响应缺少 data"))
        val oidcEnabled = dataObj["oidc_enabled"]?.jsonPrimitive?.contentOrNull == "true"
        if (!oidcEnabled) return Result.failure(Exception("Aries API 未启用 OIDC 登录"))
        val clientId = dataObj["oidc_client_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val authorizationEndpoint = dataObj["oidc_authorization_endpoint"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val serverAddress =
            dataObj["server_address"]?.jsonPrimitive?.contentOrNull
                ?.trimEnd('/')
                ?.ifBlank { AriesApiClient.BASE_URL }
                ?: AriesApiClient.BASE_URL
        if (clientId.isBlank() || authorizationEndpoint.isBlank()) {
            return Result.failure(Exception("Aries API OIDC 配置不完整"))
        }

        val stateRequest = Request.Builder()
            .url("${AriesApiClient.BASE_URL}/api/oauth/state")
            .get()
            .build()
        val stateRaw =
            client.newCall(stateRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("OAuth state 获取失败 HTTP ${response.code}"))
                }
                response.body?.string().orEmpty()
            }
        val stateObj = Json.parseToJsonElement(stateRaw).jsonObject
        val state = stateObj["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (state.isBlank()) return Result.failure(Exception("OAuth state 为空"))

        return Result.success(
            ApiOAuthConfig(
                state = state,
                clientId = clientId,
                authorizationEndpoint = authorizationEndpoint,
                redirectUri = "$serverAddress/oauth/oidc",
            ),
        )
    }

    private fun buildAuthorizationUrl(config: ApiOAuthConfig): String =
        Uri.parse(config.authorizationEndpoint)
            .buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("state", config.state)
            .build()
            .toString()
}
