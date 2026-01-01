package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.AppLogger
import android.view.Gravity
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.VisitWebResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient

/** Tool for web page visiting and content extraction */
class StandardWebVisitTool(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "WebVisitTool"
        private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
        
        // Cache to store visit results
        private val visitCache = ConcurrentHashMap<String, VisitWebResultData>()
    }

    // 创建OkHttpClient实例，配置超时
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    // 存储WebView引用，用于在不同方法间共享
    private var webViewReference: WebView? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var overlayLifecycleOwner: ServiceLifecycleOwner? = null

    private var indicatorComposeView: ComposeView? = null
    private var indicatorLayoutParams: WindowManager.LayoutParams? = null

    private val isExpandedState = mutableStateOf(false)
    private val isExpandedByUserState = mutableStateOf(false)
    private val isCaptchaDetectedState = mutableStateOf(false)

    override fun invoke(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value
        val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
        val linkNumberStr = tool.parameters.find { it.name == "link_number" }?.value

        val targetUrl = when {
            !visitKey.isNullOrBlank() && !linkNumberStr.isNullOrBlank() -> {
                val linkNumber = linkNumberStr.toIntOrNull()
                if (linkNumber == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Invalid link number.")
                }
                
                val cachedVisit = visitCache[visitKey]
                if (cachedVisit == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Invalid visit key.")
                }

                val link = cachedVisit.links.getOrNull(linkNumber - 1)
                if (link == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Link number out of bounds.")
                }
                link.url
            }
            !url.isNullOrBlank() -> url
            else -> {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Either 'url' or both 'visit_key' and 'link_number' must be provided."
                )
            }
        }

        return try {
            val pageContent = visitWebPage(targetUrl)
            ToolResult(toolName = tool.name, success = true, result = pageContent, error = null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error visiting web page", e)

            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error visiting web page: ${e.message}"
            )
        }
    }

    @Composable
    private fun SearchingIndicator(
            onToggleFullscreen: () -> Unit,
            onDragBy: (dx: Int, dy: Int) -> Unit
    ) {
        val transition = rememberInfiniteTransition(label = "indicator")
        val primaryColor = MaterialTheme.colorScheme.primary

        val bobbingDp =
                transition.animateFloat(
                        initialValue = -2f,
                        targetValue = 2f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 900, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "bobbing"
                )

        val wiggleDeg =
                transition.animateFloat(
                        initialValue = -8f,
                        targetValue = 8f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "wiggle"
                )

        val pulse =
                transition.animateFloat(
                        initialValue = 0.96f,
                        targetValue = 1.04f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "pulse"
                )

        Surface(
                shape = CircleShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier =
                        Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                                    }
                                }
                                .clickable { onToggleFullscreen() }
        ) {
            Box(
                    modifier =
                            Modifier
                                    .fillMaxSize()
                                    .scale(pulse.value)
                                    .drawBehind {
                                        val radius = size.minDimension / 2f
                                        drawCircle(
                                                brush =
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.White.copy(alpha = 0.40f),
                                                                                primaryColor.copy(alpha = 0.16f),
                                                                                Color.Transparent
                                                                        ),
                                                                center = Offset(size.width * 0.30f, size.height * 0.28f),
                                                                radius = radius * 1.15f
                                                        ),
                                                radius = radius
                                        )

                                        drawCircle(
                                                color = Color.White.copy(alpha = 0.20f),
                                                radius = radius * 0.22f,
                                                center = Offset(size.width * 0.28f, size.height * 0.28f)
                                        )

                                        drawCircle(
                                                color = Color.White.copy(alpha = 0.08f),
                                                radius = radius * 0.90f,
                                                center = Offset(size.width * 0.55f, size.height * 0.62f)
                                        )
                                    }
                                    .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier =
                                Modifier
                                        .size(22.dp)
                                        .offset(y = bobbingDp.value.dp)
                                        .rotate(wiggleDeg.value)
                )
            }
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val url = tool.parameters.find { it.name == "url" }?.value
        val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
        val linkNumber = tool.parameters.find { it.name == "link_number" }?.value

        val isUrlVisit = !url.isNullOrBlank()
        val isKeyVisit = !visitKey.isNullOrBlank() && !linkNumber.isNullOrBlank()

        return if (isUrlVisit || isKeyVisit) {
            ToolValidationResult(valid = true)
        } else {
            ToolValidationResult(valid = false, errorMessage = "Either 'url' or both 'visit_key' and 'link_number' must be provided.")
        }
    }

    /** Visit web page and extract content */
    private fun visitWebPage(url: String): VisitWebResultData {
        // Use WebView to visit the page and extract content
        val extractedJson = runBlocking { loadWebPageAndExtractContent(url) }

        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val result = json.decodeFromString<ExtractedWebData>(extractedJson)

            // 将内容解析为结构化数据
            val lines = result.content.split("\n")
            var title = result.title
            val metadata = mutableMapOf<String, String>()
            var contentStartIndex = 0

            // 寻找标题，假设格式是"# 标题"
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("# ")) {
                    title = line.substring(2).trim()
                    contentStartIndex = i + 1
                    break
                }
            }

            // 寻找元数据部分
            var inMetadata = false
            var metadataEndIndex = contentStartIndex

            for (i in contentStartIndex until lines.size) {
                val line = lines[i].trim()

                if (line == "---METADATA---") {
                    inMetadata = true
                    metadataEndIndex = i + 1
                    continue
                }

                if (inMetadata) {
                    if (line == "---CONTENT---") {
                        metadataEndIndex = i + 1
                        break
                    }

                    // 解析元数据，格式为"key: value"
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            metadata[key] = value
                        }
                    }
                }
            }

            // 提取实际内容
            val content =
                    if (metadataEndIndex < lines.size) {
                        lines.subList(metadataEndIndex, lines.size).joinToString("\n")
                    } else {
                        // 如果没有找到元数据/内容分隔符，使用整个内容
                        result.content
                    }

            val visitKey = UUID.randomUUID().toString()
            val resultData = VisitWebResultData(
                    url = url,
                    title = title,
                    content = content,
                    metadata = metadata,
                    links = result.links.map { VisitWebResultData.LinkData(it.url, it.text) },
                    visitKey = visitKey
            )
            visitCache[visitKey] = resultData
            resultData
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing extracted web content", e)
            // Fallback for old format or error
            VisitWebResultData(url = url, title = "Error", content = extractedJson)
        }
    }

    /** 使用WebView加载页面并提取内容 */
    private suspend fun loadWebPageAndExtractContent(url: String): String {
        return suspendCancellableCoroutine { continuation ->
            AppLogger.d(TAG, "Starting to load web page: $url")

            // 确保UI操作在主线程上执行
            Handler(Looper.getMainLooper()).post {
                try {
                    val windowManager =
                            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    overlayWindowManager = windowManager

                    // 创建生命周期管理器
                    val lifecycleOwner =
                            ServiceLifecycleOwner().apply {
                                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                                handleLifecycleEvent(Lifecycle.Event.ON_START)
                                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                            }

                    overlayLifecycleOwner = lifecycleOwner
                    isExpandedState.value = false
                    isExpandedByUserState.value = false
                    isCaptchaDetectedState.value = false

                    if (indicatorLayoutParams == null) {
                        indicatorLayoutParams = createIndicatorLayoutParams()
                    }

                    val params = setupWindowParams()
                    overlayLayoutParams = params

                    val composeView =
                            ComposeView(context).apply {
                                // 设置视图组合策略
                                setViewCompositionStrategy(
                                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                                )

                                // 使用自定义的生命周期所有者
                                setViewTreeLifecycleOwner(lifecycleOwner)
                                setViewTreeViewModelStoreOwner(lifecycleOwner)
                                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                            }
                    overlayComposeView = composeView

                    composeView.setContent {
                        WebVisitUI(
                                url = url,
                                onWebViewCreated = { webView ->
                                    // 存储WebView引用以便清理
                                    webViewReference = webView
                                },
                                onContentExtracted = { content ->
                                    AppLogger.d(TAG, "Content extracted, length: ${content.length}")

                                    // 在完成内容提取后进行资源清理
                                    try {
                                        // 清理生命周期资源
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_PAUSE
                                        )
                                        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_DESTROY
                                        )

                                        // 清理WebView
                                        cleanupWebView(webViewReference)

                                        removeIndicatorWindow()

                                        // 清理Compose View并移除窗口
                                        CoroutineScope(Dispatchers.Main).launch {
                                            // 首先设置内容为空，释放组合资源
                                            composeView.setContent {}

                                            // 使用try-catch以确保即使移除窗口失败也能继续
                                            try {
                                                windowManager.removeView(composeView)
                                                AppLogger.d(TAG, "ComposeView removed from window")
                                            } catch (e: Exception) {
                                                AppLogger.e(
                                                        TAG,
                                                        "Error removing ComposeView: ${e.message}"
                                                )
                                            }

                                            if (overlayComposeView === composeView) {
                                                overlayComposeView = null
                                                overlayWindowManager = null
                                                overlayLayoutParams = null
                                                overlayLifecycleOwner = null
                                            }

                                            // 恢复协程
                                            if (continuation.isActive) {
                                                continuation.resume(content)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "Error cleaning up resources: ${e.message}")
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                    "Error extracting content: ${e.message}"
                                            )
                                        }
                                    }
                                },
                                onCaptchaStateChanged = { isCaptcha ->
                                    updateOverlayWindowLayout(isCaptcha)
                                },
                                isExpanded = isExpandedState,
                                onMinimizeRequested = {
                                    if (!isCaptchaDetectedState.value) {
                                        setExpanded(false)
                                    }
                                }
                        )
                    }

                    try {
                        windowManager.addView(composeView, params)
                        AppLogger.d(TAG, "Web browser window added")

                        showIndicatorWindow()

                        // 添加取消回调以处理协程取消
                        continuation.invokeOnCancellation {
                            // 确保在主线程上执行UI操作
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    // 清理生命周期资源
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                                    // 清理WebView资源
                                    cleanupWebView(webViewReference)

                                    removeIndicatorWindow()

                                    // 移除视图
                                    composeView.setContent {}
                                    windowManager.removeView(composeView)
                                    AppLogger.d(TAG, "Web browser window removed on cancellation")
                                    if (overlayComposeView === composeView) {
                                        overlayComposeView = null
                                        overlayWindowManager = null
                                        overlayLayoutParams = null
                                        overlayLifecycleOwner = null
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e(TAG, "Error removing view on cancellation: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error showing web browser: ${e.message}")
                        continuation.resume("Error displaying web browser: ${e.message}")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error in loadWebPageAndExtractContent: ${e.message}")
                    continuation.resume("General error: ${e.message}")
                }
            }
        }
    }

    /** Setup window parameters for the WebView dialog */
    private fun setupWindowParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics

        return WindowManager.LayoutParams().apply {
            // Set window type
            type =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

            // Important: Do not set FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
            // as they would prevent interaction with the WebView
            flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            // Make window transparent to properly display the Compose UI
            format = PixelFormat.TRANSLUCENT

            val compactSizePx = (displayMetrics.density * 1f).toInt().coerceAtLeast(1)
            width = compactSizePx
            height = compactSizePx

            gravity = Gravity.TOP or Gravity.START

            val indicatorParams = indicatorLayoutParams
            if (indicatorParams != null) {
                x = indicatorParams.x
                y = indicatorParams.y
            }
        }
    }

    private fun createIndicatorLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val sizePx = (density * 40f).roundToInt().coerceAtLeast(1)

        return WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val safeMargin = (density * 16f).roundToInt()
            x = safeMargin
            y = safeMargin
        }
    }

    private fun showIndicatorWindow() {
        val windowManager = overlayWindowManager ?: return
        val lifecycleOwner = overlayLifecycleOwner ?: return
        if (indicatorComposeView != null) return

        val params = indicatorLayoutParams ?: createIndicatorLayoutParams().also { indicatorLayoutParams = it }

        val view =
                ComposeView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        MaterialTheme {
                            SearchingIndicator(
                                    onToggleFullscreen = { setExpanded(true) },
                                    onDragBy = { dx, dy -> moveIndicatorBy(dx, dy) }
                            )
                        }
                    }
                }

        indicatorComposeView = view
        windowManager.addView(view, params)
    }

    private fun hideIndicatorWindow() {
        val windowManager = overlayWindowManager
        val view = indicatorComposeView
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing indicator view: ${e.message}")
            }
        }
        indicatorComposeView = null
    }

    private fun removeIndicatorWindow() {
        val windowManager = overlayWindowManager
        val view = indicatorComposeView
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing indicator view: ${e.message}")
            }
        }
        indicatorComposeView = null
        indicatorLayoutParams = null
    }

    private fun moveIndicatorBy(dx: Int, dy: Int) {
        val windowManager = overlayWindowManager ?: return
        val view = indicatorComposeView ?: return
        val params = indicatorLayoutParams ?: return

        val displayMetrics = context.resources.displayMetrics
        val maxX = (displayMetrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)

        indicatorLayoutParams = params
        windowManager.updateViewLayout(view, params)
        syncWebOverlayToIndicator()
    }

    private fun syncWebOverlayToIndicator() {
        if (isExpandedState.value) return
        val windowManager = overlayWindowManager ?: return
        val view = overlayComposeView ?: return
        val overlayParams = overlayLayoutParams ?: return
        val indicatorParams = indicatorLayoutParams ?: return

        overlayParams.x = indicatorParams.x
        overlayParams.y = indicatorParams.y
        overlayLayoutParams = overlayParams
        if (view.windowToken != null) {
            windowManager.updateViewLayout(view, overlayParams)
        }
    }

    private fun setExpanded(expanded: Boolean) {
        isExpandedState.value = expanded
        isExpandedByUserState.value = expanded
        updateOverlayWindowLayout()
    }

    private fun updateOverlayWindowLayout(isCaptchaVerification: Boolean) {
        isCaptchaDetectedState.value = isCaptchaVerification
        if (isCaptchaVerification) {
            isExpandedState.value = true
        } else if (!isExpandedByUserState.value) {
            isExpandedState.value = false
        }
        updateOverlayWindowLayout()
    }

    private fun updateOverlayWindowLayout() {
        val windowManager = overlayWindowManager ?: return
        val view = overlayComposeView ?: return
        val params = (overlayLayoutParams ?: (view.layoutParams as? WindowManager.LayoutParams)) ?: return

        val displayMetrics = context.resources.displayMetrics
        val expanded = isExpandedState.value

        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            hideIndicatorWindow()
        } else {
            val compactSizePx = (displayMetrics.density * 1f).toInt().coerceAtLeast(1)
            params.width = compactSizePx
            params.height = compactSizePx
            params.gravity = Gravity.TOP or Gravity.START
            params.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            if (indicatorComposeView == null) {
                showIndicatorWindow()
            }

            val indicatorParams = indicatorLayoutParams
            if (indicatorParams != null) {
                params.x = indicatorParams.x
                params.y = indicatorParams.y
            }
        }

        overlayLayoutParams = params
        if (view.windowToken != null) {
            windowManager.updateViewLayout(view, params)
        }
    }

    /** 清理WebView资源的辅助方法 */
    private fun cleanupWebView(webView: WebView?) {
        webView?.apply {
            // 先停止加载
            stopLoading()

            // 清理WebView状态
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()

            // 清理JavaScript状态
            evaluateJavascript("javascript:void(0);", null)

            // 加载空白页以释放资源
            loadUrl("about:blank")

            // 暂停WebView
            onPause()

            // 移除回调和消息
            handler?.removeCallbacksAndMessages(null)

            // 销毁WebView
            destroy()
        }

        // 请求GC回收资源
        System.gc()
    }

    /** Compose UI for the web browser */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WebVisitUI(
            url: String,
            onWebViewCreated: (WebView) -> Unit,
            onContentExtracted: (String) -> Unit,
            onCaptchaStateChanged: (Boolean) -> Unit,
            isExpanded: State<Boolean>,
            onMinimizeRequested: () -> Unit
    ) {
        // 页面状态
        val isLoading = remember { mutableStateOf(true) } // 页面是否正在加载
        val pageLoaded = remember { mutableStateOf(false) } // 页面是否已加载完成
        val currentUrl = remember { mutableStateOf(url) } // 当前URL
        val pageTitle = remember { mutableStateOf("") } // 页面标题

        // 内容状态
        val pageContent = remember { mutableStateOf("") } // 提取的页面内容
        val hasExtractedContent = remember { mutableStateOf(false) } // 是否已提取内容

        // 自动模式状态
        val autoModeEnabled = remember { mutableStateOf(true) } // 是否启用自动模式
        val autoCountdownActive = remember { mutableStateOf(false) } // 倒计时是否激活
        val autoCountdownSeconds = remember { mutableStateOf(5) } // 倒计时秒数
        val isCaptchaVerification = remember { mutableStateOf(false) } // 是否需要人机验证

        LaunchedEffect(isCaptchaVerification.value) {
            onCaptchaStateChanged(isCaptchaVerification.value)
        }

        // 修改LaunchedEffect部分，使滚动和倒计时同时进行
        LaunchedEffect(autoCountdownActive.value, isCaptchaVerification.value) {
            if (autoCountdownActive.value) {
                val countdownDuration = if (isCaptchaVerification.value) 60 else 5
                autoCountdownSeconds.value = countdownDuration

                for (i in countdownDuration downTo 1) {
                    autoCountdownSeconds.value = i
                    delay(1000)

                    if (!autoCountdownActive.value) {
                        break
                    }
                }

                if (autoCountdownActive.value) {
                    autoCountdownActive.value = false
                    if (pageContent.value.isNotEmpty() && !isCaptchaVerification.value) {
                        onContentExtracted(pageContent.value)
                    }
                }
            }
        }

        MaterialTheme {
            Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
            ) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                        shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "网页访问中",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                            )
                        }

                        // URL info
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                        text = "访问网址:",
                                        fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = currentUrl.value,
                                        fontSize = 14.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 状态文本根据当前状态动态变化
                                val statusText =
                                        when {
                                            !pageLoaded.value -> "正在加载页面..."
                                            isLoading.value -> "等待页面完全加载..."
                                            isCaptchaVerification.value -> "请在${autoCountdownSeconds.value}秒内完成人机验证..."
                                            autoCountdownActive.value -> "正在提取内容，${autoCountdownSeconds.value}秒后自动继续..."
                                            hasExtractedContent.value -> "内容已提取，等待手动继续..."
                                            else -> "页面已加载，等待提取内容..."
                                        }

                                Text(
                                        text = statusText,
                                        fontSize = 16.sp
                                )

                                if (pageTitle.value.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text = "页面标题: ${pageTitle.value}",
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // WebView
                        AndroidView(
                                factory = { context ->
                                    createOptimizedWebView(context, url) { webView ->
                                        // Register created WebView instance for later cleanup
                                        onWebViewCreated(webView)

                                        // Configure WebViewClient
                                        webView.webViewClient =
                                                object : WebViewClient() {
                                                    // 追踪上一个URL，用于检测重定向
                                                    private var lastLoadedUrl: String = ""

                                                    override fun onPageFinished(
                                                            view: WebView,
                                                            loadedUrl: String
                                                    ) {
                                                        super.onPageFinished(view, loadedUrl)
                                                        AppLogger.d(TAG, "Loaded URL: $loadedUrl")

                                                        // 检查是否是重定向或新页面
                                                        val isNewPage = lastLoadedUrl != loadedUrl
                                                        lastLoadedUrl = loadedUrl

                                                        currentUrl.value = loadedUrl
                                                        pageTitle.value = view.title ?: ""
                                                        pageLoaded.value = true

                                                        // 如果是重定向或新页面，重置提取状态
                                                        if (isNewPage) {
                                                            AppLogger.d(TAG, "页面发生重定向或加载了新页面，重置提取状态")
                                                            isLoading.value = true
                                                            hasExtractedContent.value = false
                                                            pageContent.value = ""
                                                            autoCountdownActive.value = false
                                                            autoModeEnabled.value = true
                                                            isCaptchaVerification.value = false
                                                        }

                                                        // 检查是否是Google人机验证页面
                                                        if (loadedUrl.contains("google.com/sorry/index")) {
                                                            AppLogger.d(TAG, "Google CAPTCHA page detected, returning error.")
                                                            onContentExtracted("{\"error\":\"Google CAPTCHA detected. Please try again later or solve it in a browser.\"}")
                                                            return@onPageFinished
                                                        }

                                                        // 检查是否需要人机验证
                                                        view.evaluateJavascript("(function() { return document.body.innerText.includes('人机验证') || document.body.innerHTML.includes('captcha'); })();") { result ->
                                                            val isCaptcha = result?.toBoolean() ?: false
                                                            if (isCaptcha && !isCaptchaVerification.value) {
                                                                isCaptchaVerification.value = true
                                                                autoModeEnabled.value = false // 需要手动验证，禁用自动模式
                                                                autoCountdownActive.value = true // 开始60秒倒计时
                                                            } else if (!isCaptcha && isCaptchaVerification.value) {
                                                                // 用户完成了验证
                                                                isCaptchaVerification.value = false
                                                                autoModeEnabled.value = true
                                                                // 重新触发内容提取和5秒倒计时
                                                                triggerContentExtraction(view)
                                                            }
                                                        }

                                                        // 页面加载完成后，如果不是人机验证模式，则延迟自动提取内容
                                                        if (autoModeEnabled.value && !isCaptchaVerification.value) {
                                                            triggerContentExtraction(view)
                                                        }
                                                    }

                                                    private fun triggerContentExtraction(view: WebView) {
                                                        Handler(Looper.getMainLooper()).postDelayed({
                                                            if (!view.isAttachedToWindow) {
                                                                AppLogger.d(TAG, "WebView不再附加到窗口，跳过提取")
                                                                return@postDelayed
                                                            }

                                                            if (!hasExtractedContent.value && autoModeEnabled.value) {
                                                                isLoading.value = true
                                                                extractPageContent(view) { content ->
                                                                    isLoading.value = false
                                                                    hasExtractedContent.value = true
                                                                    pageContent.value = content

                                                                    if (autoModeEnabled.value) {
                                                                        autoCountdownActive.value = true
                                                                        autoScrollToBottom(view) {
                                                                            AppLogger.d(TAG, "页面滚动完成")
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }, 800)
                                                    }

                                                    override fun shouldOverrideUrlLoading(
                                                            view: WebView,
                                                            request: WebResourceRequest
                                                    ): Boolean {
                                                        // 记录URL变化，但允许所有导航
                                                        val newUrl = request.url.toString()
                                                        AppLogger.d(TAG, "URL变化: $newUrl")
                                                        currentUrl.value = newUrl

                                                        // 设置页面正在加载状态
                                                        isLoading.value = true
                                                        pageLoaded.value = false

                                                        return false // 允许WebView处理URL加载
                                                    }

                                                    override fun onReceivedError(
                                                            view: WebView,
                                                            errorCode: Int,
                                                            description: String,
                                                            failingUrl: String
                                                    ) {
                                                        AppLogger.e(
                                                                TAG,
                                                                "WebView error: $errorCode - $description"
                                                        )
                                                        super.onReceivedError(
                                                                view,
                                                                errorCode,
                                                                description,
                                                                failingUrl
                                                        )
                                                    }
                                                }

                                        return@createOptimizedWebView webView
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                update = { webView ->
                                    // 存储最新的WebView引用
                                    webViewReference = webView
                                    // 只更新重要属性以避免重新创建
                                    webView.requestFocus()
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 按钮区域 - 根据当前状态显示不同按钮
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 左侧按钮 - 根据状态变化
                            OutlinedButton(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，则取消倒计时
                                            autoCountdownActive.value = false
                                        } else if (pageLoaded.value &&
                                                        !hasExtractedContent.value &&
                                                        autoModeEnabled.value
                                        ) {
                                            // 如果页面已加载但未提取内容，且处于自动模式，则切换到手动模式
                                            autoModeEnabled.value = false
                                        } else {
                                            // 其他情况为取消操作
                                            onContentExtracted("操作已取消")
                                        }
                                    }
                            ,
                                    modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value -> "取消倒计时"
                                            pageLoaded.value &&
                                                    !hasExtractedContent.value &&
                                                    autoModeEnabled.value -> "取消自动"
                                            else -> "取消"
                                        }
                                )
                            }

                            if (isExpanded.value && !isCaptchaVerification.value) {
                                OutlinedButton(
                                        onClick = onMinimizeRequested,
                                        modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("缩小")
                                }
                            }

                            // 右侧按钮 - 根据状态变化
                            Button(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，立即继续
                                            autoCountdownActive.value = false
                                            if (pageContent.value.isNotEmpty()) {
                                                onContentExtracted(pageContent.value)
                                            }
                                        } else if (hasExtractedContent.value) {
                                            // 如果已提取内容，直接继续
                                            onContentExtracted(pageContent.value)
                                        } else {
                                            // 否则提取内容
                                            webViewReference?.let { webView ->
                                                // 显示正在提取提示
                                                isLoading.value = true

                                                AppLogger.d(TAG, "触发内容提取，当前页面: ${currentUrl.value}")

                                                extractPageContent(webView) { content ->
                                                    isLoading.value = false
                                                    hasExtractedContent.value = true
                                                    pageContent.value = content

                                                    // 如果是自动模式，开始倒计时
                                                    if (autoModeEnabled.value) {
                                                        autoCountdownActive.value = true
                                                    }
                                                }
                                            }
                                                    ?: onContentExtracted("WebView不可用，无法提取内容。")
                                        }
                                    },
                                    enabled = pageLoaded.value,
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                    modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value ->
                                                    "立即继续 (${autoCountdownSeconds.value}s)"
                                            hasExtractedContent.value -> "继续处理"
                                            !autoModeEnabled.value -> "手动提取"
                                            else -> "提取内容" // 移除"(自动)"后缀，因为这是默认行为
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 帮助文本
                        Text(
                                text =
                                        when {
                                            !pageLoaded.value -> "正在加载页面，请稍候..."
                                            autoCountdownActive.value -> "正在自动倒计时，点击\"取消倒计时\"可停止自动处理"
                                            !hasExtractedContent.value && autoModeEnabled.value ->
                                                    "点击\"取消自动\"可切换到手动模式"
                                            !hasExtractedContent.value && !autoModeEnabled.value ->
                                                    "已切换到手动模式，点击\"手动提取\"继续"
                                            else -> "内容已提取完成，点击\"继续处理\"继续"
                                        },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    /** 创建优化的WebView实例，配置所有必要的设置 */
    private fun createOptimizedWebView(
            context: Context,
            url: String,
            configure: (WebView) -> WebView
    ): WebView {
        return WebView(context).apply {
            layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                    )

            // 配置WebView设置
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = USER_AGENT

                // 启用App Cache
//                try {
//                    val enableMethod = this::class.java.getMethod("setAppCacheEnabled", Boolean::class.javaPrimitiveType)
//                    enableMethod.invoke(this, true)
//                } catch (_: Exception) {
//                    // AppCache API removed on newer Android versions
//                }
//                try {
//                    val pathMethod = this::class.java.getMethod("setAppCachePath", String::class.java)
//                    pathMethod.invoke(this, context.cacheDir.absolutePath)
//                } catch (_: Exception) {
//                    // AppCache API removed on newer Android versions
//                }
                allowFileAccess = true

                // 设置默认缓存模式，而不是完全禁用
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // 减少自动媒体加载
                mediaPlaybackRequiresUserGesture = true

                // 在Android 5.0及以上版本，默认阻止混合内容，这里明确设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }

            // 启用交互
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            // 配置Cookie策略，确保第三方Cookie也被接受
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }

            // 改善内存管理
            setWillNotDraw(false)

            // 加载URL
            loadUrl(url)

            // 应用额外配置
            configure(this)
        }
    }

    /** 从加载的WebView提取页面内容 */
    private fun extractPageContent(webView: WebView, callback: (String) -> Unit) {
        // 检查WebView是否有效
        if (!webView.isAttachedToWindow) {
            AppLogger.e(TAG, "WebView is not attached to window, cannot extract content")
            callback("Error: WebView is not attached to window")
            return
        }

        // 记录提取开始
        AppLogger.d(TAG, "Starting to extract content from web page")

        // 用于提取页面内容的JavaScript
        val extractionScript =
                """
            (function() {
                try {
                    console.log("Starting content extraction script");

                    // 提取链接
                    var links = [];
                    var linkNodes = document.querySelectorAll('a');
                    for (var i = 0; i < linkNodes.length; i++) {
                        var node = linkNodes[i];
                        var href = node.href;
                        var text = node.innerText.trim();
                        if (href && text) {
                            links.push({url: href, text: text});
                        }
                    }
                    
                    // 页面基本信息
                    var result = {
                        title: document.title || "No Title",
                        url: window.location.href,
                        content: "",
                        links: links
                    };
                    
                    // 直接获取整个文档的HTML和文本内容
                    var fullHtml = document.documentElement.outerHTML;
                    var fullText = document.body.innerText;
                    
                    // 添加元数据
                    var metadata = {};
                    
                    // 提取所有meta标签的信息
                    var metaTags = document.querySelectorAll('meta');
                    for (var i = 0; i < metaTags.length; i++) {
                        var name = metaTags[i].getAttribute('name') || 
                                   metaTags[i].getAttribute('property') || 
                                   metaTags[i].getAttribute('itemprop');
                        var content = metaTags[i].getAttribute('content');
                        
                        if (name && content) {
                            metadata[name] = content;
                        }
                    }
                    
                    // 添加特别关注的元数据
                    var importantMetadata = ['description', 'keywords', 'author', 'og:title', 'og:description'];
                    var metaStr = "---METADATA---\n";
                    importantMetadata.forEach(function(key) {
                        if (metadata[key]) {
                            metaStr += key + ": " + metadata[key] + "\n";
                        }
                    });
                    
                    // 组合最终结果
                    result.content = "# " + result.title + "\n\n" +
                                      metaStr + "\n" +
                                      "---CONTENT---\n" +
                                      fullText;
                                      
                    console.log("Content extraction complete");
                    return JSON.stringify(result);
                } catch(e) {
                    return JSON.stringify({error: "Error extracting content: " + e.toString()});
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(extractionScript) { resultContent ->
                try {
                    AppLogger.d(
                            TAG,
                            "Content extraction result received. Length: ${resultContent.length}"
                    )

                    // 处理JavaScript结果，去掉引号包装
                    val processedContent =
                            if (resultContent.startsWith("\"") && resultContent.endsWith("\"")) {
                                // 解码JavaScript字符串到Kotlin字符串
                                resultContent
                                        .substring(1, resultContent.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                        .replace("\\n", "\n")
                            } else {
                                resultContent
                            }

                    callback(processedContent)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error processing extracted content", e)
                    callback("Error processing content: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during JavaScript evaluation", e)
            callback("Error evaluating JavaScript: ${e.message}")
        }
    }

    /** 自动滚动页面到底部以触发可能的懒加载内容 */
    private fun autoScrollToBottom(webView: WebView, onScrollComplete: () -> Unit) {
        val scrollScript =
                """
            (function() {
                try {
                    // 初始位置
                    var initialHeight = document.body.scrollHeight;
                    var scrollAttempts = 0;
                    var maxScrollAttempts = 3;
                    var scrollInterval;
                    var lastScrollHeight = 0;
                    
                    function smoothScroll() {
                        // 检查是否到达尝试次数上限
                        if (scrollAttempts >= maxScrollAttempts) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成 - 达到最大尝试次数');
                            return true; // 滚动完成
                        }
                        
                        // 获取当前文档高度
                        var currentHeight = document.body.scrollHeight;
                        
                        // 如果两次滚动后高度没有变化，认为已经滚动到底部
                        if (currentHeight === lastScrollHeight && scrollAttempts > 0) {
                            scrollAttempts++;
                            console.log('内容高度未变化，尝试次数: ' + scrollAttempts);
                        } else {
                            // 更新上次高度
                            lastScrollHeight = currentHeight;
                        }
                        
                        // 执行滚动
                        var currentPosition = window.pageYOffset || document.documentElement.scrollTop;
                        var targetPosition = currentHeight - window.innerHeight;
                        var distance = targetPosition - currentPosition;
                        
                        if (Math.abs(distance) < 10) {
                            // 已接近底部，增加尝试次数
                            scrollAttempts++;
                            console.log('已接近底部，尝试次数: ' + scrollAttempts);
                            
                            // 额外触发一次滚动确保触发所有加载
                            window.scrollTo(0, targetPosition + 1);
                            
                            // 短暂等待后滚回正常位置
                            setTimeout(function() {
                                window.scrollTo(0, targetPosition);
                            }, 100);
                        } else {
                            // 平滑滚动到目标位置
                            window.scrollTo({
                                top: targetPosition,
                                behavior: 'smooth'
                            });
                            console.log('滚动到位置: ' + targetPosition + '，总高度: ' + currentHeight);
                        }
                        
                        return scrollAttempts >= maxScrollAttempts;
                    }
                    
                    // 立即执行一次初始滚动
                    var isComplete = smoothScroll();
                    if (isComplete) {
                        return 'scroll-complete';
                    }
                    
                    // 设置定时滚动
                    scrollInterval = setInterval(function() {
                        var isComplete = smoothScroll();
                        if (isComplete) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成');
                            return 'scroll-complete';
                        }
                    }, 1000); // 每秒滚动一次
                    
                    // 无论如何，最多滚动8秒
                    setTimeout(function() {
                        clearInterval(scrollInterval);
                        console.log('自动滚动超时完成');
                    }, 8000);
                    
                    return 'scrolling-started';
                } catch(e) {
                    console.error('滚动过程出错: ' + e);
                    return 'scroll-error: ' + e;
                }
            })();
        """.trimIndent()

        try {
            AppLogger.d(TAG, "开始执行自动滚动脚本")
            webView.evaluateJavascript(scrollScript) { result -> AppLogger.d(TAG, "滚动脚本初始执行结果: $result") }

            // 等待滚动完成后执行回调
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                AppLogger.d(TAG, "自动滚动等待完成，继续处理")
                                onScrollComplete()
                            },
                            5000
                    ) // 给页面5秒钟的滚动时间
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行滚动脚本时出错", e)
            // 出错时也要继续后续流程
            onScrollComplete()
        }
    }
}

@kotlinx.serialization.Serializable
private data class ExtractedWebData(
    val title: String,
    val url: String,
    val content: String,
    val links: List<LinkInfo> = emptyList(),
    val error: String? = null
) {
    @kotlinx.serialization.Serializable
    data class LinkInfo(val url: String, val text: String)
}

/** 服务生命周期所有者 - 为Compose UI提供生命周期支持 必须在主线程上初始化 */
private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        // 确保在主线程上初始化
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 在主线程上，直接初始化
            savedStateRegistryController.performRestore(null)
        } else {
            // 如果不在主线程上，则记录警告（我们应该确保在主线程上创建实例）
            AppLogger.w(
                    "ServiceLifecycleOwner",
                    "Initializing not on main thread. This may cause issues."
            )
            // 在实际使用时应该避免这种情况，代码已经通过 Handler(Looper.getMainLooper()).post 确保在主线程上创建
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        // 确保生命周期事件在主线程上处理
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            // 如果不在主线程上，使用Handler将调用转到主线程
            Handler(Looper.getMainLooper()).post { lifecycleRegistry.handleLifecycleEvent(event) }
        }
    }
}
