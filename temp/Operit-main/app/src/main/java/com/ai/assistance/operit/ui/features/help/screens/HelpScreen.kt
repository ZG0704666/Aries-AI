package com.ai.assistance.operit.ui.features.help.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val helpUrls = remember {
        listOf(
            "https://operit.aaswordsman.org",
            "https://operit.dev.tc",
            "https://aaswordman.github.io/OperitWeb/"
        )
    }
    var currentUrlIndex by remember { mutableStateOf(0) }
    val latencies = remember {
        mutableStateListOf<Long?>().apply {
            repeat(helpUrls.size) { add(null) }
        }
    }
    val pingCompleted = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(helpUrls.size) { add(false) }
        }
    }
    var showMirrorDialog by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val isCurrentScreen = LocalIsCurrentScreen.current
    
    // 创建WebView实例
    val webView = remember { 
        WebViewConfig.createWebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    isLoading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // 让WebView处理所有链接
                    return false
                }
            }
        }
    }
    // 确保WebView获取焦点，避免滑动时被父级拦截
    DisposableEffect(webView) {
        webView.post {
            webView.requestFocus()
            webView.requestFocusFromTouch()
        }
        onDispose { }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(helpUrls) {
        helpUrls.forEachIndexed { index, url ->
            launch {
                val latency = pingUrl(url)
                latencies[index] = latency
                pingCompleted[index] = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
        )
        
        // 加载指示器
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.help_loading_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (showMirrorDialog && isCurrentScreen) {
            AlertDialog(
                onDismissRequest = { /* 必须选择一个镜像，不允许直接关闭 */ },
                title = {
                    Text(text = stringResource(R.string.help_mirror_dialog_title))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        helpUrls.forEachIndexed { index, url ->
                            val label = when (index) {
                                0 -> stringResource(R.string.help_mirror_label_main)
                                1 -> stringResource(R.string.help_mirror_label_mirror1)
                                else -> stringResource(R.string.help_mirror_label_legacy)
                            }
                            val latency = latencies.getOrNull(index)
                            val completed = pingCompleted.getOrNull(index) ?: false
                            val latencyText = when {
                                latency != null -> stringResource(R.string.help_mirror_latency_ms, latency)
                                completed -> stringResource(R.string.help_mirror_latency_timeout)
                                else -> stringResource(R.string.help_mirror_latency_testing)
                            }

                            Button(
                                onClick = {
                                    if (currentUrlIndex != index) {
                                        currentUrlIndex = index
                                    }
                                    showMirrorDialog = false
                                    webView.loadUrl(url)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "$label ($latencyText)")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}

private suspend fun pingUrl(url: String, timeoutMs: Int = 3000): Long? {
    return withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..399) {
                System.currentTimeMillis() - start
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
