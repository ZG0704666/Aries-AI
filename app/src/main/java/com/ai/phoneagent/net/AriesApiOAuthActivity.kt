 package com.ai.phoneagent.net

import android.app.Activity
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ai.phoneagent.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

class AriesApiOAuthActivity : Activity() {
    data class ApiLoginResult(
        val success: Boolean,
        val apiKey: String = "",
        val userAccessToken: String = "",
        val sessionCookieHeader: String = "",
        val userId: Int = 0,
        val displayName: String = "",
        val message: String = "",
    )

    data class PendingSession(
        val client: OkHttpClient,
        val callbackApiUrl: String,
        val expectedRedirectUri: String,
        val initialCookies: List<String>,
        val currentCookies: () -> List<String>,
        val completion: (ApiLoginResult) -> Unit,
    )

    companion object {
        private const val TAG = "AriesApiOAuth"
        const val EXTRA_AUTH_URL = "extra_auth_url"
        var pendingSession: PendingSession? = null
        private const val STORAGE_TOKEN_SCRIPT =
            """
            (function() {
              function entries(storage) {
                var out = {};
                for (var i = 0; i < storage.length; i++) {
                  var key = storage.key(i);
                  out[key] = storage.getItem(key);
                }
                return out;
              }
              function asObject(value) {
                if (!value || typeof value !== 'string') return null;
                try { return JSON.parse(value); } catch (e) { return null; }
              }
              function text(value) {
                return typeof value === 'string' ? value : '';
              }
              function looksLikeToken(value) {
                return typeof value === 'string' && value.length >= 16 && value.indexOf(' ') < 0;
              }
                            function emptyFound() {
                                return { token: '', userId: 0, displayName: '' };
                            }
                            function mergeProfile(found, value) {
                                if (!value || typeof value !== 'object') return found;
                                var userId = parseInt(value.id || value.user_id || value.userId || 0, 10) || 0;
                                var displayName = text(value.display_name || value.displayName || value.username || value.email || value.name);
                                if (!found.userId && userId) found.userId = userId;
                                if (!found.displayName && displayName) found.displayName = displayName;
                                return found;
                            }
                            function withToken(token, value) {
                                return mergeProfile({ token: token || '', userId: 0, displayName: '' }, value);
                            }
              function scan(value) {
                                var found = emptyFound();
                                if (!value) return found;
                if (typeof value === 'string') {
                  var parsed = asObject(value);
                                    if (parsed) return scan(parsed);
                                    return found;
                }
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length; i++) {
                                        var nested = scan(value[i]);
                                        if (!found.userId && nested.userId) found.userId = nested.userId;
                                        if (!found.displayName && nested.displayName) found.displayName = nested.displayName;
                                        if (nested.token) return nested;
                  }
                                    return found;
                }
                                if (typeof value !== 'object') return found;
                                found = mergeProfile(found, value);
                var preferred = ['access_token', 'accessToken', 'user_access_token', 'userAccessToken', 'token'];
                for (var p = 0; p < preferred.length; p++) {
                  var candidate = value[preferred[p]];
                  if (looksLikeToken(candidate)) {
                                        return withToken(candidate, value);
                  }
                }
                for (var key in value) {
                  if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
                  var lower = key.toLowerCase();
                  if (lower.indexOf('token') >= 0 && looksLikeToken(value[key])) {
                                        return withToken(value[key], value);
                  }
                }
                for (var nestedKey in value) {
                  if (!Object.prototype.hasOwnProperty.call(value, nestedKey)) continue;
                  var nested = scan(value[nestedKey]);
                                    if (!found.userId && nested.userId) found.userId = nested.userId;
                                    if (!found.displayName && nested.displayName) found.displayName = nested.displayName;
                                    if (nested.token) return mergeProfile(nested, value);
                }
                                return found;
              }
              var local = entries(window.localStorage);
              var session = entries(window.sessionStorage);
              var found = scan({ local: local, session: session });
              return JSON.stringify({
                token: found.token || '',
                userId: found.userId || 0,
                displayName: found.displayName || '',
                keys: Object.keys(local).concat(Object.keys(session)).join(','),
                href: window.location.href
              });
            })();
            """

                private const val WEB_TOKEN_PAGE_SNAPSHOT_SCRIPT =
                        """
                        (function() {
                            return JSON.stringify({
                                href: window.location.href || '',
                                text: ((document.body && document.body.innerText) || '').slice(0, 1500)
                            });
                        })();
                        """
    }

    private var completed = false
    private var finalizing = false
    private var pendingCallbackUrl: String? = null
    private lateinit var progressOverlay: LinearLayout
    private lateinit var progressScrollView: ScrollView
    private lateinit var progressLogView: TextView
    private val progressMessages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = pendingSession
        val authUrl = intent.getStringExtra(EXTRA_AUTH_URL).orEmpty()
        if (session == null || authUrl.isBlank()) {
            finishWith(ApiLoginResult(success = false, message = "Aries API 登录会话已失效"))
            return
        }

        val webView = WebView(this)
        val spacingLg = resources.getDimensionPixelSize(R.dimen.m3t_spacing_lg)
        val spacingMd = resources.getDimensionPixelSize(R.dimen.m3t_spacing_md)
        val lineSpacing = resources.getDimension(R.dimen.m3t_automation_log_line_spacing)
        val statusBarHeight =
            resources.getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }
                ?.let { resources.getDimensionPixelSize(it) }
                ?: 0
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        session.initialCookies.forEach { cookie ->
            cookieManager.setCookie(AriesApiClient.BASE_URL, cookie)
        }
        cookieManager.flush()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(request.url.toString(), session)

                @Suppress("OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    handleUrl(url, session)

                override fun onPageFinished(view: WebView, url: String) {
                    val callbackUrl = pendingCallbackUrl ?: url.takeIf { isApiCallbackUrl(url, session) }
                    if (callbackUrl != null) {
                        completeLoginWithBackendCallback(view, callbackUrl, session)
                    }
                }
            }

        progressLogView =
            TextView(this).apply {
                setTextColor(resources.getColor(R.color.m3t_on_surface_variant, theme))
                typeface = Typeface.MONOSPACE
                setLineSpacing(lineSpacing, 1f)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        progressScrollView =
            ScrollView(this).apply {
                setPadding(0, spacingMd, 0, 0)
                addView(progressLogView)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            }
        progressOverlay =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                isClickable = true
                setBackgroundColor(resources.getColor(R.color.m3t_surface, theme))
                setPadding(spacingLg, spacingLg + statusBarHeight, spacingLg, spacingLg)
                addView(
                    TextView(this@AriesApiOAuthActivity).apply {
                        text = "正在完成 Aries 登录"
                        setTextColor(resources.getColor(R.color.m3t_primary, theme))
                        setTypeface(typeface, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    },
                )
                addView(
                    TextView(this@AriesApiOAuthActivity).apply {
                        text = ""
                        setTextColor(resources.getColor(R.color.m3t_on_surface_variant, theme))
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    },
                )
                addView(progressScrollView)
            }

        setContentView(
            FrameLayout(this).apply {
                addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(progressOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )
        appendProgress("正在打开 Aries 登录页")
        webView.loadUrl(authUrl)
    }

    override fun onDestroy() {
        if (!completed && !finalizing) {
            finishWith(ApiLoginResult(success = false, message = "Aries API 登录已取消"))
        }
        super.onDestroy()
    }

    private fun handleUrl(url: String, session: PendingSession): Boolean {
        if (!isApiCallbackUrl(url, session)) return false
        val callbackUri = Uri.parse(url)
        val error = callbackUri.getQueryParameter("error").orEmpty()
        val errorDescription = callbackUri.getQueryParameter("error_description").orEmpty()
        if (error.isNotBlank()) {
            showProgressOverlay("OIDC 登录返回错误：${errorDescription.ifBlank { error }}")
            finishWith(ApiLoginResult(success = false, message = errorDescription.ifBlank { error }))
            return true
        }
        pendingCallbackUrl = url
        showProgressOverlay("已收到 OIDC 回调，正在继续 Aries 登录")
        return false
    }

    private fun isApiCallbackUrl(url: String, session: PendingSession): Boolean =
        url.startsWith(session.expectedRedirectUri)

    private fun completeLoginWithBackendCallback(webView: WebView?, callbackUrl: String, session: PendingSession) {
        if (completed || finalizing) return
        finalizing = true
        pendingCallbackUrl = null
        CoroutineScope(Dispatchers.Main).launch {
            appendProgress("回调页已加载，开始整理 Aries 登录结果")
            appendProgress("正在读取 WebView 存储中的登录态")
            val storageResult =
                if (webView != null) {
                    fetchUserAccessTokenFromWebStorage(webView)
                } else {
                    ApiLoginResult(success = false, message = "已拦截 OIDC 回调，未加载 WebView 回调页")
                }
            if (storageResult.success) {
                appendProgress("已从 WebView 存储获取用户令牌，准备返回应用")
                finishWith(storageResult)
                return@launch
            }
            appendProgress("未直接拿到用户令牌，正在从 Aries 控制台读取 API Key")
            val webConsoleResult =
                if (webView != null) {
                    fetchApiKeyFromWebConsole(webView)
                } else {
                    ApiLoginResult(success = false, message = "WebView 不可用，无法读取 Aries API Key")
                }
            if (webConsoleResult.success) {
                appendProgress("已从 Aries 控制台读取 API Key，准备返回应用")
                finishWith(webConsoleResult)
                return@launch
            }
            appendProgress("控制台直读失败，正在校验 Aries Cookie 会话")
            val cookieResult = withContext(Dispatchers.IO) { fetchUserAccessTokenFromCookieSession(session) }
            if (cookieResult.success) {
                appendProgress("Cookie 会话可用，准备生成 Aries Token")
                finishWith(cookieResult)
                return@launch
            }
            appendProgress("Cookie 会话不可用，正在请求后端 OIDC 回调结果")
            val backendResult = withContext(Dispatchers.IO) { fetchUserAccessTokenFromBackendCallback(callbackUrl, session) }
            val cookieSessionResult = buildCookieSessionResult(session, storageResult, backendResult)
            if (cookieSessionResult != null) {
                appendProgress("已拿到站点登录会话，准备回退到 Cookie 登录")
                finishWith(cookieSessionResult)
                return@launch
            }
            val result =
                if (backendResult.success) {
                    appendProgress("后端已返回 Aries 用户令牌，准备返回应用")
                    backendResult
                } else {
                    ApiLoginResult(
                        success = false,
                        message = "Aries API 已完成 OIDC 回调，但无法生成 access token: ${storageResult.message}; ${webConsoleResult.message}; ${cookieResult.message}; ${backendResult.message}",
                    )
                }
            finishWith(result)
        }
    }

    private fun fetchUserAccessTokenFromBackendCallback(callbackUrl: String, session: PendingSession): ApiLoginResult {
        return try {
            val callbackUri = Uri.parse(callbackUrl)
            val code = callbackUri.getQueryParameter("code").orEmpty()
            val state = callbackUri.getQueryParameter("state").orEmpty()
            if (code.isBlank() || state.isBlank()) {
                return ApiLoginResult(success = false, message = "OIDC 回调缺少 code 或 state")
            }
            val apiUrl =
                Uri.parse(session.callbackApiUrl)
                    .buildUpon()
                    .appendQueryParameter("code", code)
                    .appendQueryParameter("state", state)
                    .build()
                    .toString()
            val callbackRequest = Request.Builder().url(apiUrl).get().build()
            var callbackResult = ApiLoginResult(success = false, message = "Aries API 回调未执行")
            session.client.newCall(callbackRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val setCookieNames = response.headers("Set-Cookie").map { it.substringBefore("=") }
                Log.e(
                    TAG,
                    "callback code=${response.code} setCookie=$setCookieNames body=${raw.take(300)}",
                )
                callbackResult = parseApiLoginResponse(raw, response.code, requireToken = false)
                if (callbackResult.success && callbackResult.userAccessToken.isNotBlank()) {
                    return callbackResult
                }
                if (!callbackResult.success) {
                    return callbackResult
                }
            }
            val tokenRequest = Request.Builder()
                .url("${AriesApiClient.BASE_URL}/api/user/token")
                .get()
                .apply {
                    val cookieHeader = AriesApiClient.normalizeCookieHeader(session.currentCookies().joinToString("; "))
                    if (cookieHeader.isNotBlank()) {
                        addHeader("Cookie", cookieHeader)
                    }
                    if (callbackResult.userId > 0) {
                        addHeader("New-Api-User", callbackResult.userId.toString())
                    }
                }
                .build()
            val requestCookieNames =
                tokenRequest.header("Cookie")
                    .orEmpty()
                    .split(";")
                    .mapNotNull { cookie -> cookie.trim().takeIf { it.isNotBlank() }?.substringBefore("=") }
            Log.e(TAG, "user-token request userId=${callbackResult.userId} cookies=$requestCookieNames")
            session.client.newCall(tokenRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                Log.e(TAG, "user-token code=${response.code} body=${raw.take(300)}")
                val tokenResult = parseApiLoginResponse(raw, response.code)
                if (tokenResult.success) {
                    val displayName = tokenResult.displayName.ifBlank { callbackResult.displayName }
                    tokenResult.copy(
                        userId = tokenResult.userId.takeIf { it > 0 } ?: callbackResult.userId,
                        displayName = displayName,
                    )
                } else {
                    ApiLoginResult(
                        success = false,
                        userId = callbackResult.userId,
                        displayName = callbackResult.displayName,
                        message = "后端回调已登录用户 ${callbackResult.userId}，但生成 user token 失败: ${tokenResult.message}",
                    )
                }
            }
        } catch (error: Exception) {
            ApiLoginResult(
                success = false,
                message = error.message?.trim().orEmpty().ifBlank { "后端回调换取 user token 失败" },
            )
        }
    }

    private fun fetchUserAccessTokenFromWebSession(webView: WebView, session: PendingSession) {
        if (completed || finalizing) return
        finalizing = true
        CoroutineScope(Dispatchers.Main).launch {
            val storageResult = fetchUserAccessTokenFromWebStorage(webView)
            val cookieResult = withContext(Dispatchers.IO) { fetchUserAccessTokenFromCookieSession(session) }
            val result =
                if (cookieResult.success) {
                    cookieResult
                } else {
                    ApiLoginResult(
                        success = false,
                        message = "Aries API 已完成 OIDC 回调，但无法生成 access token: ${storageResult.message}; ${cookieResult.message}",
                    )
                }
            finishWith(result)
        }
    }

    private suspend fun fetchUserAccessTokenFromWebStorage(webView: WebView): ApiLoginResult {
        var storageFailure = "WebView 存储中未发现 access token"
        repeat(8) {
            delay(700)
            val snapshot = evaluateStorageSnapshot(webView)
            val parsed = parseStorageLoginSnapshot(snapshot)
            if (parsed.success) return parsed
            storageFailure = parsed.message
        }
        return ApiLoginResult(success = false, message = storageFailure)
    }

    private suspend fun evaluateStorageSnapshot(webView: WebView): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(STORAGE_TOKEN_SCRIPT) { value ->
                continuation.resume(value.orEmpty())
            }
        }

    private suspend fun evaluateJavascriptValue(
        webView: WebView,
        script: String,
    ): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { value ->
                continuation.resume(value.orEmpty())
            }
        }

    private fun decodeJavascriptString(raw: String): String =
        runCatching { Json.parseToJsonElement(raw).jsonPrimitive.contentOrNull.orEmpty() }
            .getOrDefault(raw)

        private fun buildWebTokenFetchScript(userId: Int): String {
                val userHeader = if (userId > 0) "'New-Api-User': '$userId'," else ""
                return """
                        (function() {
                            if (window.__ariesTokenProbeRunning) return 'running';
                            window.__ariesTokenProbeRunning = true;
                            window.__ariesTokenProbeResult = '';
                            (async function() {
                                function finish(payload) {
                                    window.__ariesTokenProbeResult = JSON.stringify(payload || {});
                                    window.__ariesTokenProbeRunning = false;
                                }
                                try {
                                    var listResponse = await fetch('/api/token/?p=1&size=100&page_size=100', {
                                        credentials: 'include',
                                        headers: { 'Accept': 'application/json', $userHeader }
                                    });
                                    var listText = await listResponse.text();
                                    var listObj = {};
                                    try { listObj = JSON.parse(listText); } catch (e) {}
                                    var items = listObj && listObj.data && Array.isArray(listObj.data.items) ? listObj.data.items : [];
                                    var picked = items.find(function(item) { return item && item.name === 'Ariesphone'; }) || items[0] || null;
                                    if (!picked || !picked.id) {
                                        finish({
                                            success: false,
                                            stage: 'list',
                                            status: listResponse.status,
                                            message: (listObj && listObj.message) || '',
                                            body: (listText || '').slice(0, 300),
                                            href: window.location.href,
                                            userId: $userId
                                        });
                                        return;
                                    }

                                    var keyResponse = await fetch('/api/token/' + picked.id + '/key', {
                                        method: 'POST',
                                        credentials: 'include',
                                        headers: {
                                            'Accept': 'application/json',
                                            'Content-Type': 'application/json',
                                            $userHeader
                                        },
                                        body: '{}'
                                    });
                                    var keyText = await keyResponse.text();
                                    var keyObj = {};
                                    try { keyObj = JSON.parse(keyText); } catch (e) {}
                                    var data = keyObj && keyObj.data && typeof keyObj.data === 'object' ? keyObj.data : null;
                                    var key = (data && (data.key || data.access_token || data.token)) || keyObj.key || keyObj.access_token || keyObj.token || '';
                                    if (key) {
                                        finish({
                                            success: true,
                                            apiKey: key,
                                            tokenId: picked.id || 0,
                                            tokenName: picked.name || '',
                                            href: window.location.href,
                                            userId: $userId
                                        });
                                        return;
                                    }

                                    finish({
                                        success: false,
                                        stage: 'key',
                                        status: keyResponse.status,
                                        message: (keyObj && keyObj.message) || '',
                                        body: (keyText || '').slice(0, 300),
                                        tokenId: picked.id || 0,
                                        tokenName: picked.name || '',
                                        href: window.location.href,
                                        userId: $userId
                                    });
                                } catch (error) {
                                    finish({
                                        success: false,
                                        stage: 'exception',
                                        message: String(error),
                                        href: window.location.href,
                                        userId: $userId
                                    });
                                }
                            })();
                            return 'started';
                        })();
                """.trimIndent()
        }

    private suspend fun fetchApiKeyFromWebConsole(webView: WebView): ApiLoginResult {
        var lastFailure = "WebView 令牌页中未发现 Aries API Key"
        var waitingLogged = false
        var readyLogged = false
        repeat(24) { attempt ->
            delay(1000)
            val snapshotRaw = evaluateJavascriptValue(webView, WEB_TOKEN_PAGE_SNAPSHOT_SCRIPT)
            val snapshot = decodeJavascriptString(snapshotRaw)
            val snapshotObj = runCatching { Json.parseToJsonElement(snapshot).jsonObject }.getOrNull()
            val href = snapshotObj?.let { textAt(it, "href") }.orEmpty()
            val text = snapshotObj?.let { textAt(it, "text") }.orEmpty()
            val tokenPageReady = href.contains("/token") || text.contains("令牌管理") || text.contains("Ariesphone")
            if (!tokenPageReady) {
                if (!waitingLogged) {
                    appendProgress("正在等待 Aries 控制台令牌页就绪")
                    waitingLogged = true
                }
                lastFailure = "WebView 尚未进入令牌页 href=$href text=${text.take(120)}"
                return@repeat
            }
            if (!readyLogged) {
                appendProgress("令牌页已就绪，正在读取 Aries API Key")
                readyLogged = true
            }

            val storageSnapshot = evaluateStorageSnapshot(webView)
            val storageResult = parseStorageLoginSnapshot(storageSnapshot)
            val storageUserId = storageResult.userId.takeIf { it > 0 } ?: 0
            evaluateJavascriptValue(webView, buildWebTokenFetchScript(storageUserId))
            repeat(8) {
                delay(500)
                val raw = evaluateJavascriptValue(webView, "window.__ariesTokenProbeResult || ''")
                val decoded = decodeJavascriptString(raw)
                if (decoded.isBlank()) return@repeat
                val parsed = parseWebConsoleTokenResult(decoded)
                if (parsed.success) {
                    return parsed
                }
                lastFailure = parsed.message
                evaluateJavascriptValue(webView, "window.__ariesTokenProbeResult = ''; window.__ariesTokenProbeRunning = false; ''")
                return@repeat
            }
        }
        return ApiLoginResult(success = false, message = lastFailure)
    }

    private fun collectSessionCookieHeader(session: PendingSession): String =
        AriesApiClient.normalizeCookieHeader(
            buildString {
                val webViewCookies = CookieManager.getInstance().getCookie(AriesApiClient.BASE_URL).orEmpty().trim()
                val clientCookies = session.currentCookies().joinToString("; ").trim()
                if (webViewCookies.isNotBlank()) {
                    append(webViewCookies)
                }
                if (clientCookies.isNotBlank()) {
                    if (isNotEmpty()) append("; ")
                    append(clientCookies)
                }
            },
        )

    private fun parseWebConsoleTokenResult(raw: String): ApiLoginResult =
        runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val success = obj["success"]?.jsonPrimitive?.booleanOrNull == true
            val apiKey = textAt(obj, "apiKey")
            if (success && apiKey.isNotBlank()) {
                ApiLoginResult(
                    success = true,
                    apiKey = apiKey,
                )
            } else {
                val stage = textAt(obj, "stage")
                val message = textAt(obj, "message")
                val body = textAt(obj, "body")
                val href = textAt(obj, "href")
                ApiLoginResult(
                    success = false,
                    message = "WebView 令牌页读取失败 stage=$stage message=$message href=$href body=$body",
                )
            }
        }.getOrElse { error ->
            ApiLoginResult(success = false, message = "WebView 令牌页结果解析失败: ${error.message.orEmpty()} body=${raw.take(300)}")
        }

    private fun buildCookieSessionResult(
        session: PendingSession,
        storageResult: ApiLoginResult,
        backendResult: ApiLoginResult,
    ): ApiLoginResult? {
        val cookieHeader = collectSessionCookieHeader(session)
        if (cookieHeader.isBlank()) return null
        val userId =
            storageResult.userId.takeIf { it > 0 }
                ?: backendResult.userId.takeIf { it > 0 }
                ?: 0
        val displayName = storageResult.displayName.ifBlank { backendResult.displayName }
        return ApiLoginResult(
            success = true,
            sessionCookieHeader = cookieHeader,
            userId = userId,
            displayName = displayName,
        )
    }

    private fun fetchUserAccessTokenFromCookieSession(session: PendingSession): ApiLoginResult =
        runCatching {
            var lastFailure = ApiLoginResult(success = false, message = "Aries API 登录态尚未写入")
            repeat(5) {
                Thread.sleep(800)
                val cookieHeader = collectSessionCookieHeader(session)
                if (cookieHeader.isBlank()) {
                    lastFailure = ApiLoginResult(success = false, message = "Aries API 登录态 Cookie 为空")
                    return@repeat
                }
                val request =
                    Request.Builder()
                        .url("${AriesApiClient.BASE_URL}/api/user/token")
                        .get()
                        .addHeader("Cookie", cookieHeader)
                        .build()
                session.client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val parsed = parseApiLoginResponse(raw, response.code)
                    if (parsed.success) return@runCatching parsed
                    lastFailure = parsed
                }
            }
            lastFailure
        }.getOrElse { error ->
            ApiLoginResult(
                success = false,
                message = error.message?.trim().orEmpty().ifBlank { "Aries API 登录失败" },
            )
        }

    private fun parseApiLoginResponse(raw: String, code: Int, requireToken: Boolean = true): ApiLoginResult {
        if (raw.isBlank()) return ApiLoginResult(success = false, message = "Aries API 登录响应为空 HTTP $code")
        val obj =
            runCatching { Json.parseToJsonElement(raw).jsonObject }
                .getOrElse { error ->
                    return ApiLoginResult(
                        success = false,
                        message = "Aries API 登录响应解析失败 HTTP $code: ${error.message.orEmpty()} body=${raw.take(300)}",
                    )
                }
        val success = isSuccessResponse(obj)
        val message = textAt(obj, "message")
        val dataElement = obj["data"]
        val dataObj = dataElement?.asJsonObjectOrNull()
        val userObj = dataObj?.get("user")?.asJsonObjectOrNull()
        val token =
            firstText(
                dataObj?.let { textAt(it, "token") },
                dataObj?.let { textAt(it, "access_token") },
                dataObj?.let { textAt(it, "accessToken") },
                dataObj?.let { textAt(it, "user_access_token") },
                dataObj?.let { textAt(it, "userAccessToken") },
                userObj?.let { textAt(it, "token") },
                userObj?.let { textAt(it, "access_token") },
                userObj?.let { textAt(it, "accessToken") },
                textAt(obj, "token"),
                textAt(obj, "access_token"),
                textAt(obj, "accessToken"),
                dataElement?.jsonPrimitiveOrNullContent(),
            )
        val userId =
            userObj?.get("id")?.jsonPrimitive?.intOrNull
                ?: dataObj?.get("id")?.jsonPrimitive?.intOrNull
                ?: 0
        val displayName =
            firstText(
                userObj?.let { textAt(it, "display_name") },
                userObj?.let { textAt(it, "displayName") },
                userObj?.let { textAt(it, "username") },
                userObj?.let { textAt(it, "email") },
                dataObj?.let { textAt(it, "display_name") },
                dataObj?.let { textAt(it, "displayName") },
                dataObj?.let { textAt(it, "username") },
                dataObj?.let { textAt(it, "email") },
            )
        return if (success && (!requireToken || token.isNotBlank())) {
            ApiLoginResult(
                success = true,
                userAccessToken = token,
                userId = userId,
                displayName = displayName,
            )
        } else if (success) {
            ApiLoginResult(
                success = false,
                message = "Aries API 已完成 OIDC 回调，但没有返回 access token: ${raw.take(300)}",
            )
        } else {
            ApiLoginResult(success = false, message = message.ifBlank { "Aries API 登录失败 HTTP $code: ${raw.take(300)}" })
        }
    }

    private fun parseStorageLoginSnapshot(snapshot: String): ApiLoginResult {
        val raw =
            runCatching { Json.parseToJsonElement(snapshot).jsonPrimitive.contentOrNull.orEmpty() }
                .getOrDefault(snapshot)
        if (raw.isBlank()) return ApiLoginResult(success = false, message = "WebView 存储读取为空")
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val token = textAt(obj, "token")
            val userId = obj["userId"]?.jsonPrimitive?.intOrNull ?: 0
            val displayName = textAt(obj, "displayName")
            if (token.isNotBlank()) {
                ApiLoginResult(
                    success = true,
                    userAccessToken = token,
                    userId = userId,
                    displayName = displayName,
                )
            } else {
                val keys = textAt(obj, "keys")
                val href = textAt(obj, "href")
                ApiLoginResult(
                    success = false,
                    userId = userId,
                    displayName = displayName,
                    message = "WebView 存储中未发现 token keys=$keys href=$href",
                )
            }
        }.getOrElse { error ->
            ApiLoginResult(success = false, message = "WebView 存储解析失败: ${error.message.orEmpty()}")
        }
    }

    private fun JsonElement.asJsonObjectOrNull() =
        runCatching { jsonObject }.getOrNull()

    private fun JsonElement.jsonPrimitiveOrNullContent(): String =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull().orEmpty()

    private fun isSuccessResponse(obj: JsonObject): Boolean {
        val success = obj["success"]?.jsonPrimitive
        val codeValue = obj["code"]?.jsonPrimitive
        return success?.booleanOrNull
            ?: success?.contentOrNull?.equals("true", ignoreCase = true)
            ?: codeValue?.booleanOrNull
            ?: codeValue?.contentOrNull?.equals("true", ignoreCase = true)
            ?: false
    }

    private fun textAt(obj: JsonObject, key: String): String =
        obj[key]?.jsonPrimitiveOrNullContent().orEmpty()

    private fun firstText(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    private fun finishWith(result: ApiLoginResult) {
        if (completed) return
        completed = true
        finalizing = false
        val session = pendingSession
        pendingSession = null
        if (result.success) {
            appendProgress("Aries 登录完成，正在返回设置页")
        } else {
            showProgressOverlay("Aries 登录失败：${result.message}")
        }
        if (!result.success) {
            Log.e(TAG, result.message)
        }
        session?.completion?.invoke(result)
        finish()
    }

    private fun showProgressOverlay(message: String) {
        runOnUiThread {
            if (!::progressOverlay.isInitialized) return@runOnUiThread
            progressOverlay.visibility = View.VISIBLE
            progressOverlay.bringToFront()
            appendProgressLine(message)
        }
    }

    private fun appendProgress(message: String) {
        runOnUiThread {
            if (!::progressLogView.isInitialized) return@runOnUiThread
            appendProgressLine(message)
        }
    }

    private fun appendProgressLine(message: String) {
        if (progressMessages.lastOrNull()?.endsWith(message) == true) return
        val timestamp = DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()
        progressMessages += "[$timestamp] $message"
        progressLogView.text = progressMessages.joinToString("\n")
        progressScrollView.post { progressScrollView.fullScroll(View.FOCUS_DOWN) }
    }

}
