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
package com.ai.phoneagent

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.FloatingChatPreferencesRepository
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.ThemeColorStyle
import com.ai.phoneagent.core.designsystem.theme.ThemeMode
import com.ai.phoneagent.net.AriesApiClient
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import com.ai.phoneagent.net.LocalMnnInferenceEngine
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.viewmodel.AutomationViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

/** 悬浮聊天窗口服务 提供小窗模式的聊天界面和虚拟屏工具箱模式 */
class FloatingChatService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val appPrefsRepository by inject<AppPreferencesRepository>()
    private val floatingChatPrefs by inject<FloatingChatPreferencesRepository>()

    companion object {
        private const val TAG = "FloatingChatService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_chat_channel"
        private const val OPEN_APP_PI_REQUEST_CODE = 1002
        private const val LAUNCH_PROXY_EXTRA_TARGET_INTENT = "target_intent"
        private const val PREFS_NAME = "floating_chat_prefs"
        private const val PREF_KEY_FLOATING_MESSAGES = "floating_messages"
        private const val PREF_KEY_FLOATING_MESSAGES_UPDATED_AT = "floating_messages_updated_at"

        const val ACTION_FLOATING_RETURNED = "com.ai.phoneagent.action.FLOATING_RETURNED"

        // 工具箱模式启动 Intent Key
        const val EXTRA_TOOLBOX_MODE = "toolbox_mode"

        // 悬浮窗模式
        enum class FloatingMode {
            CHAT, // 聊天模式（原有）
            TOOLBOX // 工具箱模式（虚拟屏预览）
        }

        @Volatile private var instance: FloatingChatService? = null

        fun getInstance(): FloatingChatService? = instance

        fun isRunning(): Boolean = instance != null

        fun temporaryHideForScreenshot() {
            instance?.temporaryHideForScreenshotInternal()
        }

        fun restoreVisibilityAfterScreenshot() {
            instance?.restoreVisibilityAfterScreenshotInternal()
        }

        /** 检查是否有悬浮窗权限 */
        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun cacheMessagesForNextStart(context: Context, messages: List<String>) {
            runCatching {
                val repo = FloatingChatPreferencesRepository(context.applicationContext)
                val json = com.google.gson.Gson().toJson(messages)
                repo.setFloatingMessagesBlocking(json)
                repo.setFloatingMessagesUpdatedAtBlocking(System.currentTimeMillis())
            }
        }

        /** 启动悬浮窗服务 */
        fun start(
                context: Context,
                messages: ArrayList<String>? = null,
                fromX: Float = 100f,
                fromY: Float = 200f,
                fromWidth: Float = 320f,
                fromHeight: Float = 400f,
                showDelayMs: Long = 80L,
        ) {
            if (!hasOverlayPermission(context)) return
            if (!messages.isNullOrEmpty()) {
                cacheMessagesForNextStart(context, messages)
            }
            val intent =
                    Intent(context, FloatingChatService::class.java).apply {
                        // restore from prefs in onStartCommand to avoid large binder payload
                        putExtra("from_x", fromX)
                        putExtra("from_y", fromY)
                        putExtra("from_width", fromWidth)
                        putExtra("from_height", fromHeight)
                        putExtra("show_delay_ms", showDelayMs)
                    }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止悬浮窗服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingChatService::class.java))
        }

        /** 以工具箱模式启动悬浮窗服务（虚拟屏预览） */
        fun startAsToolbox(
                context: Context,
                fromX: Float = 100f,
                fromY: Float = 200f,
                fromWidth: Float = 240f,
                fromHeight: Float = 480f,
                showDelayMs: Long = 80L,
        ) {
            if (!hasOverlayPermission(context)) return
            val intent =
                    Intent(context, FloatingChatService::class.java).apply {
                        putExtra(EXTRA_TOOLBOX_MODE, true)
                        putExtra("from_x", fromX)
                        putExtra("from_y", fromY)
                        putExtra("from_width", fromWidth)
                        putExtra("from_height", fromHeight)
                        putExtra("show_delay_ms", showDelayMs)
                    }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // 工具箱进度更新回调
        @Volatile private var toolboxProgressListener: ((Int, Int, String) -> Unit)? = null

        fun setToolboxProgressListener(listener: ((Int, Int, String) -> Unit)?) {
            toolboxProgressListener = listener
        }

        fun updateToolboxProgress(step: Int, total: Int, description: String) {
            toolboxProgressListener?.invoke(step, total, description)
        }

        /** 消息同步监听器接口 */
        interface MessageSyncListener {
            fun onMessageAdded(message: String, isUser: Boolean)
            fun onMessagesCleared()
        }

        // 消息同步监听器（用于与 MainActivity 同步）
        private var messageSyncListener: MessageSyncListener? = null

        // 待同步消息队列（当主界面不在线时暂存）
        private val pendingSyncMessages = mutableListOf<Pair<String, Boolean>>()

        fun setMessageSyncListener(listener: MessageSyncListener?) {
            messageSyncListener = listener
            // 当设置新的监听器时，发送所有待同步的消息
            if (listener != null && pendingSyncMessages.isNotEmpty()) {
                pendingSyncMessages.forEach { (message, isUser) ->
                    listener.onMessageAdded(message, isUser)
                }
                pendingSyncMessages.clear()
            }
        }

        fun getMessageSyncListener(): MessageSyncListener? = messageSyncListener
    }

    private val binder = LocalBinder()
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var isViewAdded = false

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // 窗口状态
    private var windowX = 100
    private var windowY = 200
    private var windowWidth = 280 // dp - 更小的尺寸
    private var windowHeight = 360 // dp - 更小的尺寸

    private var targetX: Int = 100
    private var targetY: Int = 200
    private var showDelayMs: Long = 0L

    // 窗口参数
    private var layoutParams: WindowManager.LayoutParams? = null

    data class FloatingMessage(
            val text: String,
            val isUser: Boolean,
            val isStreaming: Boolean = false,
    )

    // 消息列表
    private val messages = mutableListOf<String>()
    private val chatHistory = mutableListOf<ChatRequestMessage>() // 用于 AI 对话上下文
    private val _floatingMessages = mutableStateListOf<FloatingMessage>()
    private var _streamingBuffer = mutableStateOf("")
    private var _isStreaming = mutableStateOf(false)
    private var streamingReasoningText: String = ""
    private var streamingAnswerText: String = ""

    // 工具箱模式相关
    private var isToolboxMode: Boolean = false
    private var toolboxView: View? = null
    private var currentToolboxStep: Int = 0
    private var totalToolboxSteps: Int = 0
    private var currentToolboxDescription: String = ""

    // 回调
    var onExpandToFullScreen: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    // 语音输入（系统 SpeechRecognizer）
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    private fun m3Color(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    private fun m3ColorWithAlpha(colorRes: Int, alpha: Int): Int =
            ColorUtils.setAlphaComponent(m3Color(colorRes), alpha.coerceIn(0, 255))

    private fun resolveApiConfig(): Triple<String, String, String> {
            val useAriesApi = appPrefsRepository.getUseAriesApiBlocking()
            val apiKey =
                    if (useAriesApi) {
                        appPrefsRepository.getActiveAriesApiKeyBlocking().trim()
                    } else {
                        appPrefsRepository.getApiKeyBlocking().trim()
                    }
            val useThirdParty = appPrefsRepository.getApiUseThirdPartyBlocking()
            val useLocalModel = appPrefsRepository.getApiUseLocalModelBlocking()
            val storedThirdPartyBaseUrl =
                    appPrefsRepository.getApiThirdPartyBaseUrlBlocking()
                        .trim()
                        .ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
            val baseUrl =
                    if (useLocalModel) {
                        AutoGlmClient.DEFAULT_BASE_URL
                    } else if (useAriesApi) {
                        AriesApiClient.ARIES_API_V1_BASE_URL
                    } else if (!useThirdParty) {
                        AutoGlmClient.DEFAULT_BASE_URL
                    } else {
                        storedThirdPartyBaseUrl
                    }
            val model =
                    if (useLocalModel) {
                        ModelScopeModelDownloader.QWEN35_MODEL_NAME
                    } else if (useAriesApi) {
                        AriesApiClient.ARIES_CHAT_MODEL
                    } else if (!useThirdParty) {
                        AutoGlmClient.DEFAULT_MODEL
                    } else {
                        appPrefsRepository.getApiThirdPartyModelBlocking()
                            .trim()
                            .ifBlank { AutoGlmClient.DEFAULT_MODEL }
                    }
            return Triple(apiKey, baseUrl, model)
    }

    private fun getStreamingPendingTitle(): String {
            val useLocalModel = appPrefsRepository.getApiUseLocalModelBlocking()
            return if (useLocalModel) "本地推理中，请等待" else "连接中"
    }

    private var awaitingReturnAck: Boolean = false

    private var lastOpenAppAttemptAt: Long = 0L

    private var overlayHiddenForReturn: Boolean = false

    private var overlayTouchBlockedForReturn: Boolean = false
    private val screenshotHideCounter = AtomicInteger(0)
    @Volatile private var wasFloatingVisibleBeforeScreenshot: Boolean = false
    @Volatile private var wasToolboxVisibleBeforeScreenshot: Boolean = false

    private val returnAckReceiver: BroadcastReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_FLOATING_RETURNED) return
                    if (!awaitingReturnAck) return
                    awaitingReturnAck = false
                    stopSelf()
                }
            }

    inner class LocalBinder : Binder() {
        fun getService(): FloatingChatService = this@FloatingChatService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        restoreWindowState()
        createNotificationChannel()

        val filter = IntentFilter(ACTION_FLOATING_RETURNED)
        ContextCompat.registerReceiver(
                this,
                returnAckReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        // 获取传入的消息
        intent?.getStringArrayListExtra("messages")?.let {
            messages.clear()
            messages.addAll(it)
            // 从消息中恢复聊天历史
            restoreChatHistory(it)
        }
                ?: run {
                    // 如果没有传入消息，从本地存储恢复
                    restoreMessagesFromPrefs()
                    if (messages.isNotEmpty()) {
                        restoreChatHistory(messages)
                    }
                }

        // 获取初始位置和尺寸
        intent?.let {
            val density = resources.displayMetrics.density

            // 读取工具箱模式标志
            isToolboxMode = it.getBooleanExtra(EXTRA_TOOLBOX_MODE, false)

            targetX = it.getFloatExtra("from_x", 100f).toInt()
            targetY = it.getFloatExtra("from_y", 200f).toInt()

            // 工具箱模式使用不同的默认尺寸
            if (isToolboxMode) {
                windowWidth = (it.getFloatExtra("from_width", 240f * density) / density).toInt()
                windowHeight = (it.getFloatExtra("from_height", 480f * density) / density).toInt()
            } else {
                windowWidth = (it.getFloatExtra("from_width", 320f * density) / density).toInt()
                windowHeight = (it.getFloatExtra("from_height", 400f * density) / density).toInt()
            }

            showDelayMs = it.getLongExtra("show_delay_ms", 0L)
        }

        // 显示悬浮窗（根据模式选择显示聊天或工具箱）
        if (showDelayMs > 0) {
            mainHandler.postDelayed({ showFloatingWindow() }, showDelayMs)
        } else {
            showFloatingWindow()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        runCatching { unregisterReceiver(returnAckReceiver) }
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        serviceScope.cancel() // 取消协程
        hideFloatingWindow()
        saveWindowState()
        saveMessagesToPrefs() // 保存消息到本地存储
    }

    private fun scheduleStopAfterReturnTimeout() {
        val self = this
        mainHandler.postDelayed(
                {
                    if (instance == self && awaitingReturnAck) {
                        awaitingReturnAck = false
                        restoreOverlayAfterFailedReturn()
                    }
                },
                3500L
        )
    }

    private fun scheduleRetryOpenAppWhileWaitingAck() {
        val self = this
        val delays = longArrayOf(900L, 1700L, 2600L)
        for (d in delays) {
            mainHandler.postDelayed(
                    {
                        if (instance == self && awaitingReturnAck) {
                            requestOpenApp(allowProxy = true)
                        }
                    },
                    d
            )
        }
    }

    private fun requestOpenApp(allowProxy: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastOpenAppAttemptAt < 700L) return
        lastOpenAppAttemptAt = now
        openAppFromFloating(allowProxy)
    }

    private fun fadeOutOverlayForReturn() {
        fadeOutOverlayForReturn(null)
    }

    private fun fadeOutOverlayForReturn(onEnd: (() -> Unit)?) {
        val view = floatingView ?: return
        if (overlayHiddenForReturn) return
        overlayHiddenForReturn = true
        view.animate().cancel()
        val exitInterpolator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    PathInterpolator(0.3f, 0f, 0.2f, 1f)
                } else {
                    DecelerateInterpolator()
                }
        view.animate()
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .setDuration(180)
                .setInterpolator(exitInterpolator)
                .withEndAction {
                    view.visibility = View.GONE
                    setTouchable(true)
                    onEnd?.invoke()
                }
                .start()
    }

    private fun prepareOverlayForReturn() {
        val view = floatingView ?: return
        if (overlayTouchBlockedForReturn) return
        overlayTouchBlockedForReturn = true
        setTouchable(false)
        setFocusable(false)
    }

    private fun restoreOverlayAfterFailedReturn() {
        val view = floatingView ?: return
        overlayHiddenForReturn = false
        overlayTouchBlockedForReturn = false
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        setTouchable(true)
        setFocusable(false)
    }

    private fun setTouchable(touchable: Boolean) {
        val lp = layoutParams ?: return
        lp.flags =
                if (touchable) {
                    lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
        if (isViewAdded && floatingView != null) {
            runCatching { windowManager.updateViewLayout(floatingView, lp) }
        }
    }

    private fun temporaryHideForScreenshotInternal() {
        val count = screenshotHideCounter.incrementAndGet()
        if (count != 1) return
        mainHandler.post {
            val chatView = floatingView
            val tbView = toolboxView
            wasFloatingVisibleBeforeScreenshot = chatView?.visibility == View.VISIBLE
            wasToolboxVisibleBeforeScreenshot = tbView?.visibility == View.VISIBLE
            chatView?.visibility = View.GONE
            tbView?.visibility = View.GONE
        }
    }

    private fun restoreVisibilityAfterScreenshotInternal() {
        val count = screenshotHideCounter.decrementAndGet()
        if (count > 0) return
        screenshotHideCounter.set(0)
        mainHandler.post {
            // If overlayHiddenForReturn is stale but we are no longer waiting ack, recover visibility.
            if (overlayHiddenForReturn && !awaitingReturnAck) {
                overlayHiddenForReturn = false
                overlayTouchBlockedForReturn = false
                setTouchable(true)
            }
            if (wasFloatingVisibleBeforeScreenshot && !overlayHiddenForReturn) {
                floatingView?.let {
                    it.visibility = View.VISIBLE
                    it.alpha = 1f
                    it.scaleX = 1f
                    it.scaleY = 1f
                }
            }
            if (wasToolboxVisibleBeforeScreenshot) {
                toolboxView?.let {
                    it.visibility = View.VISIBLE
                    it.alpha = 1f
                    it.scaleX = 1f
                    it.scaleY = 1f
                }
            }
        }
    }

    private fun noAnimOptions(): android.os.Bundle? {
        return runCatching { ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle() }
                .getOrNull()
    }

    /** 从消息列表恢复聊天历史 */
    private fun restoreChatHistory(messageList: List<String>) {
        chatHistory.clear()
        for (msg in messageList) {
            // 支持"我:"和"我: "两种格式
            val isUser = msg.startsWith("我:") || msg.startsWith("我: ")
            val content =
                    msg.removePrefix("我: ")
                            .removePrefix("我:")
                            .removePrefix("Aries: ")
                            .removePrefix("Aries:")
                            .removePrefix("AI: ")
                            .removePrefix("AI:")
                            .trim()
            if (content.isNotBlank()) {
                chatHistory.add(
                        ChatRequestMessage(
                                role = if (isUser) "user" else "assistant",
                                content = content
                        )
                )
            }
        }
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer? {
        // 部分系统会错误返回“不支持”，但实际仍可用；不要在这里直接拦截。
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        return speechRecognizer
    }

    private fun speechErrorToMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频采集失败（请检查麦克风权限/占用）"
            SpeechRecognizer.ERROR_CLIENT -> "语音服务异常（请重试）"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限（请在主界面授予）"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误（语音服务可能需要联网）"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时（请重试）"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容（再说一遍试试）"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务繁忙（稍后重试）"
            SpeechRecognizer.ERROR_SERVER -> "语音服务器错误（稍后重试）"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音（请靠近麦克风）"
            else -> "语音识别失败（错误码：$error）"
        }
    }

    private fun startVoiceInput(target: EditText) {
        val sr = ensureSpeechRecognizer() ?: return
        if (isListening) return
        isListening = true

        sr.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        // no-op
                    }

                    override fun onBeginningOfSpeech() {
                        // no-op
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // no-op
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // no-op
                    }

                    override fun onEndOfSpeech() {
                        // no-op
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.w(TAG, "SpeechRecognizer onError=$error")

                        // 部分机型会出现 ERROR_CLIENT/ERROR_RECOGNIZER_BUSY，重建 recognizer 更稳
                        val needsRebuild =
                                error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                        error == SpeechRecognizer.ERROR_SERVER
                        if (needsRebuild) {
                            runCatching { speechRecognizer?.destroy() }
                            speechRecognizer = null
                        }

                        // 特殊处理：如果系统语音不可用，提示用户
                        val msg =
                                if (!SpeechRecognizer.isRecognitionAvailable(
                                                this@FloatingChatService
                                        )
                                ) {
                                    "设备不支持系统语音识别，请使用主界面语音功能"
                                } else {
                                    speechErrorToMessage(error)
                                }
                        Toast.makeText(this@FloatingChatService, msg, Toast.LENGTH_SHORT).show()
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        isListening = false
                        val list =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.filterNotNull()
                                        .orEmpty()
                        val text = list.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            target.setText(text)
                            target.setSelection(target.text?.length ?: 0)
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val list =
                                partialResults
                                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.filterNotNull()
                                        .orEmpty()
                        val text = list.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            target.setText(text)
                            target.setSelection(target.text?.length ?: 0)
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                        // no-op
                    }
                }
        )

        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
        // 某些系统会在上一次会话未完全结束时 Busy；先 cancel 再启动更稳
        runCatching { sr.cancel() }
        runCatching { sr.startListening(intent) }.onFailure {
            isListening = false
            Toast.makeText(this, "语音输入启动失败（系统限制或识别服务不可用）", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceInput() {
        val sr = speechRecognizer ?: return
        // cancel 比 stopListening 更“硬”，能更快结束 busy 状态
        runCatching { sr.cancel() }
        isListening = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(CHANNEL_ID, "悬浮聊天窗口", NotificationManager.IMPORTANCE_LOW)
                            .apply {
                                description = "Aries AI 悬浮窗口服务"
                                setShowBadge(false)
                            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val targetIntent =
                Intent(this, MainActivity::class.java)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        .putExtra("from_floating", true)
        val proxyIntent =
                Intent(this, LaunchProxyActivity::class.java)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        .putExtra(LAUNCH_PROXY_EXTRA_TARGET_INTENT, targetIntent)

        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        proxyIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Aries AI")
                .setContentText("小窗模式运行中")
                .setSmallIcon(R.drawable.ic_floating_window_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
    }

    private fun showFloatingWindow() {
        if (isViewAdded) return
        if (!Settings.canDrawOverlays(this)) return

        val density = resources.displayMetrics.density
        val widthPx = (windowWidth * density).toInt()
        val heightPx = (windowHeight * density).toInt()

        val displayMetrics = resources.displayMetrics
        val startX = ((displayMetrics.widthPixels - widthPx) / 2f).toInt()
        val startY = ((displayMetrics.heightPixels - heightPx) / 2f).toInt()
        val endX = targetX
        val endY = targetY

        layoutParams =
                WindowManager.LayoutParams(
                                widthPx,
                                heightPx,
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.START
                            x = startX
                            y = startY
                        }

        windowX = startX
        windowY = startY

        // 根据模式选择显示聊天视图或工具箱视图
        if (isToolboxMode) {
            showToolboxWindow()
        } else {
            showChatWindow()
        }

        // 播放入场动画
        animateWindowEntry(startX, startY, endX, endY)
    }

    // 显示聊天窗口（原有逻辑）
    private fun showChatWindow() {
        updateMessagesUI()
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val themeModeStr by
                        appPrefsRepository.themeModeFlow.collectAsState(
                                initial = appPrefsRepository.getThemeModeBlocking()
                        )
                val themeColorStyleRaw by
                    appPrefsRepository.themeColorStyleFlow.collectAsState(
                    initial = appPrefsRepository.getThemeColorStyleBlocking()
                    )
                val amoledDark by
                        appPrefsRepository.amoledDarkEnabledFlow.collectAsState(
                                initial = appPrefsRepository.getAmoledDarkEnabledBlocking()
                        )
                val themeMode =
                        when (themeModeStr.lowercase()) {
                            "light" -> ThemeMode.LIGHT
                            "dark" -> ThemeMode.DARK
                            else -> ThemeMode.SYSTEM
                        }
                val themeColorStyle = ThemeColorStyle.fromStorage(themeColorStyleRaw)

                AriesMaterialTheme(
                        themeMode = themeMode,
                    themeColorStyle = themeColorStyle,
                        amoledDark = amoledDark,
                ) {
                    val listState = rememberLazyListState()
                    val messages = _floatingMessages

                    Column(
                            modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                    ) {
                        FloatingTitleBar(
                                onFullscreen = {
                                    setFocusable(false)
                                    expandToFullScreen()
                                },
                                onClose = { closeWindow() },
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                            onDrag = { change, dragAmount ->
                                                                change.consumeAllChanges()
                                                                val p =
                                                                        this@FloatingChatService
                                                                                .layoutParams
                                                                                ?: return@detectDragGestures
                                                                p.x += dragAmount.x.roundToInt()
                                                                p.y += dragAmount.y.roundToInt()
                                                                floatingView?.let {
                                                                    windowManager.updateViewLayout(it, p)
                                                                }
                                                                windowX = p.x
                                                                windowY = p.y
                                                            },
                                                            onDragEnd = { saveWindowState() }
                                                    )
                                                }
                        )

                        LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(messages) { msg ->
                                if (msg.isUser) FloatingUserBubble(msg.text)
                                else FloatingAiBubble(msg.text, msg.isStreaming)
                            }
                        }

                        LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        }

                        FloatingInputBar(
                                onSend = { text -> sendUserMessage(text) },
                                onFocused = { setFocusable(true) },
                                onUnfocused = { setFocusable(false) },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        floatingView = composeView
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        windowManager.addView(floatingView, layoutParams)
        isViewAdded = true
    }

    // 显示工具箱窗口（虚拟屏预览）
    private fun showToolboxWindow() {
        // 使用虚拟屏预览功能创建工具箱视图
        toolboxView = createToolboxView()
        windowManager.addView(toolboxView, layoutParams)
        isViewAdded = true
    }

    // 窗口入场动画
    private fun animateWindowEntry(startX: Int, startY: Int, endX: Int, endY: Int) {
        val view = (if (isToolboxMode) toolboxView else floatingView) ?: return
        view.alpha = 0f
        view.scaleX = 0.2f
        view.scaleY = 0.2f

        val enterInterpolator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    PathInterpolator(0.22f, 1f, 0.36f, 1f)
                } else {
                    OvershootInterpolator(0.8f)
                }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            interpolator = enterInterpolator
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val lp = layoutParams ?: return@addUpdateListener

                lp.x = (startX + (endX - startX) * t).toInt()
                lp.y = (startY + (endY - startY) * t).toInt()
                runCatching { windowManager.updateViewLayout(view, lp) }

                view.alpha = t
                val s = 0.2f + 0.8f * t
                view.scaleX = s
                view.scaleY = s

                windowX = lp.x
                windowY = lp.y
            }
            start()
        }
    }

    private fun openAppFromFloating(allowProxy: Boolean) {
        val baseIntent =
                Intent(this, MainActivity::class.java)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        .putExtra("from_floating", true)

        val targetIntentForService =
                Intent(baseIntent)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )

        val movedToFront =
                runCatching {
                            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            val task =
                                    am.appTasks.firstOrNull { t ->
                                        val info =
                                                runCatching { t.taskInfo }.getOrNull()
                                                        ?: return@firstOrNull false
                                        val mainName = MainActivity::class.java.name
                                        info.baseActivity?.className == mainName ||
                                                info.topActivity?.className == mainName
                                    }
                                            ?: am.appTasks.firstOrNull() ?: return@runCatching false
                            runCatching { task.moveToFront() }
                            task.startActivity(this, baseIntent, noAnimOptions())
                            true
                        }
                        .getOrDefault(false)
        if (movedToFront) return

        val directOk =
                runCatching { startActivity(targetIntentForService, noAnimOptions()) }.isSuccess
        if (directOk && !allowProxy) return

        val proxyIntent =
                Intent(this, LaunchProxyActivity::class.java)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        .putExtra(LAUNCH_PROXY_EXTRA_TARGET_INTENT, baseIntent)

        val pi =
                PendingIntent.getActivity(
                        this,
                        OPEN_APP_PI_REQUEST_CODE,
                        proxyIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

        val piSent =
                runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val options =
                                        ActivityOptions.makeBasic().apply {
                                            setPendingIntentBackgroundActivityStartMode(
                                                    ActivityOptions
                                                            .MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                            )
                                        }
                                pi.send(this, 0, null, null, null, null, options.toBundle())
                            } else {
                                pi.send()
                            }
                        }
                        .isSuccess
        if (piSent) return

        runCatching { startActivity(proxyIntent, noAnimOptions()) }.recoverCatching {
            startActivity(targetIntentForService, noAnimOptions())
        }
    }

    private fun animateDismissAndMaybeOpenApp(openApp: Boolean) {
        val view = floatingView

        if (view == null) {
            if (openApp) {
                awaitingReturnAck = true
                scheduleRetryOpenAppWhileWaitingAck()
                scheduleStopAfterReturnTimeout()
                requestOpenApp(allowProxy = false)
                return
            }
            stopSelf()
            return
        }

        // 放大恢复时保持悬浮窗可见，避免“消失无响应”的感知。
        if (openApp) {
            view.animate().cancel()
            view.visibility = View.VISIBLE
            view.alpha = 0.78f
            view.scaleX = 0.98f
            view.scaleY = 0.98f
            overlayHiddenForReturn = false
            overlayTouchBlockedForReturn = false
            prepareOverlayForReturn()

            awaitingReturnAck = true
            scheduleRetryOpenAppWhileWaitingAck()
            scheduleStopAfterReturnTimeout()

            // 仅在主界面确认回执后才 stopSelf；失败则超时自动恢复可操作状态。
            mainHandler.post { requestOpenApp(allowProxy = false) }
            return
        }

        // 普通关闭（不返回主界面）
        val exitInterpolator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    PathInterpolator(0.3f, 0f, 0.2f, 1f)
                } else {
                    DecelerateInterpolator()
                }

        view.animate()
                .alpha(0f)
                .scaleX(0.1f) // 缩小到更小，过渡更自然
                .scaleY(0.1f)
                .setStartDelay(0)
                .setDuration(220) // 稍微拉长一点时间，让消失更柔和
                .setInterpolator(exitInterpolator)
                .withEndAction {
                    // 关键修复：先移除再结束服务，确保 view 已经彻底消失
                    mainHandler.post {
                        hideFloatingWindow()
                        stopSelf()
                    }
                }
                .start()
    }

    /** 发送用户消息并获取 AI 回复 */
    private fun sendUserMessage(text: String) {
        // 添加用户消息
        addMessage("我: $text", isUser = true)

        // 已移除: addMessage("Aries: 思考中...", isUser = false, isThinking = true)

        // 发送 AI 请求
        requestAIResponse(text)
    }

    /** 请求 AI 回复 */
    private fun requestAIResponse(userText: String) {
        val (apiKey, baseUrl, model) = resolveApiConfig()
        val useLocalModel = appPrefsRepository.getApiUseLocalModelBlocking()
        if (useLocalModel && !ModelScopeModelDownloader.isQwen35ModelReady(this)) {
            addMessage("Aries: 本地模型未就绪，请先在主界面下载模型", isUser = false)
            return
        }
        if (!useLocalModel && apiKey.isBlank()) {
            addMessage("Aries: 请在主界面配置 API Key", isUser = false)
            return
        }

        if (chatHistory.firstOrNull()?.role != "system") {
            chatHistory.add(
                    0,
                    ChatRequestMessage(
                            role = "system",
                            content =
                                    """
                            你是 Aries AI。

                            要求：
                            1) 直接给出最终回答，使用 Markdown：标题/列表/代码块/表格等。
                            2) 代码块使用三反引号 ``` 并尽量保持语法完整。
                        """.trimIndent(),
                    ),
            )
        }

        chatHistory.add(ChatRequestMessage(role = "user", content = userText))

        serviceScope.launch {
            withContext(Dispatchers.Main) {
                beginExternalStreamAiReply()
            }

            val reasoningSb = StringBuilder()
            val contentSb = StringBuilder()

            var streamOk = false

            val result =
                if (useLocalModel) {
                    LocalMnnInferenceEngine.sendChatStreamResult(
                        context = this@FloatingChatService,
                        messages = chatHistory,
                        onReasoningDelta = { delta ->
                            if (delta.isNotBlank()) {
                                reasoningSb.append(delta)
                                Handler(Looper.getMainLooper()).post {
                                    appendExternalReasoningDelta(delta)
                                }
                            }
                        },
                        onContentDelta = { delta ->
                            if (delta.isNotEmpty()) {
                                contentSb.append(delta)
                                Handler(Looper.getMainLooper()).post {
                                    appendExternalContentDelta(delta)
                                }
                            }
                        }
                    )
                } else {
                    AutoGlmClient.sendChatStreamResult(
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        messages = chatHistory,
                        onReasoningDelta = { delta ->
                            if (delta.isNotBlank()) {
                                reasoningSb.append(delta)
                                Handler(Looper.getMainLooper()).post {
                                    appendExternalReasoningDelta(delta)
                                }
                            }
                        },
                        onContentDelta = { delta ->
                            if (delta.isNotEmpty()) {
                                contentSb.append(delta)
                                Handler(Looper.getMainLooper()).post {
                                    appendExternalContentDelta(delta)
                                }
                            }
                        }
                    )
                }

            streamOk = result.isSuccess

            // 结束后整理状态
            withContext(Dispatchers.Main) {
                val thinkingContent = reasoningSb.toString().trim()
                val renderedAnswer = extractDisplayAnswer(contentSb.toString()).trim()
                val fallbackAnswer = extractDisplayAnswer(contentSb.toString()).trim()
                val answerContent =
                        when {
                            renderedAnswer.isNotBlank() -> renderedAnswer
                            streamOk && fallbackAnswer.isNotBlank() -> fallbackAnswer
                            streamOk -> contentSb.toString()
                            else -> "请求失败"
                        }
                val persistText =
                        if (thinkingContent.isNotEmpty()) {
                            "<think>$thinkingContent</think>\n$answerContent"
                        } else {
                            answerContent
                        }

                finishExternalStreamAiReply(timeCost = 0, finalContent = persistText)

                chatHistory.add(ChatRequestMessage(role = "assistant", content = persistText))
            }
        }
    }

    private fun extractDisplayAnswer(raw: String): String {
        val source = raw.trim()
        if (source.isBlank()) return ""

        val thinkTagRegex = "<think>([\\s\\S]*?)</think>\\s*([\\s\\S]*)".toRegex()
        thinkTagRegex.find(source)?.let { match ->
            return match.groupValues.getOrNull(2)?.trim().orEmpty()
        }

        val answerStartTag = "【回答开始】"
        val answerEndTag = "【回答结束】"
        if (source.contains(answerStartTag)) {
            var answerPart = source.substring(source.indexOf(answerStartTag) + answerStartTag.length)
            val answerEndIdx = answerPart.indexOf(answerEndTag)
            if (answerEndIdx >= 0) {
                answerPart = answerPart.substring(0, answerEndIdx)
            }
            return answerPart.trim()
        }

        return source
    }

    // --- 外部同步接口 (供 MainActivity 调用) ---

    fun beginExternalStreamAiReply() {
        _isStreaming.value = true
        _streamingBuffer.value = ""
        streamingReasoningText = ""
        streamingAnswerText = ""
        _floatingMessages.add(FloatingMessage(text = "", isUser = false, isStreaming = true))
    }

    fun appendExternalReasoningDelta(delta: String) {
        if (!_isStreaming.value || delta.isEmpty()) return
        streamingReasoningText += delta
        updateStreamingMessageText()
    }

    fun appendExternalContentDelta(delta: String) {
        if (!_isStreaming.value || delta.isEmpty()) return
        streamingAnswerText += delta
        updateStreamingMessageText()
    }

    fun resetExternalStreamAiReply() {
        if (!_isStreaming.value) return
        _streamingBuffer.value = ""
        streamingReasoningText = ""
        streamingAnswerText = ""
        val streamIndex = _floatingMessages.indexOfLast { !it.isUser && it.isStreaming }
        if (streamIndex >= 0) {
            _floatingMessages[streamIndex] = _floatingMessages[streamIndex].copy(text = "")
        }
    }

    fun finishExternalStreamAiReply(timeCost: Int, finalContent: String) {
        if (!_isStreaming.value) return
        val persistText = if (finalContent.isNotBlank()) finalContent else _streamingBuffer.value
        val streamIndex = _floatingMessages.indexOfLast { !it.isUser && it.isStreaming }
        if (streamIndex >= 0) {
            _floatingMessages[streamIndex] =
                    _floatingMessages[streamIndex].copy(text = persistText, isStreaming = false)
        } else {
            _floatingMessages.add(FloatingMessage(text = persistText, isUser = false, isStreaming = false))
        }

        _isStreaming.value = false
        _streamingBuffer.value = persistText
        messages.add("Aries: $persistText")
        saveMessagesToPrefs()
    }

    /** 设置悬浮窗是否可聚焦（用于键盘输入） */
    private fun setFocusable(focusable: Boolean) {
        val params = layoutParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode =
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }
        if (isViewAdded) {
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun setupDragBehavior(dragHandle: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            val params =
                    floatingView?.layoutParams as? WindowManager.LayoutParams
                            ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    windowX = params.x
                    windowY = params.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveWindowState()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateMessagesUI() {
        _floatingMessages.clear()
        messages.forEach { appendFloatingMessageFromRaw(it) }
    }

    /**
     * 添加消息到悬浮窗（带同步功能）
     * @param message 消息内容
     * @param isUser 是否是用户消息
     * @param isThinking 是否是"思考中"消息（不同步）
     */
    fun addMessage(message: String, isUser: Boolean = false, isThinking: Boolean = false) {
        messages.add(message)
        appendFloatingMessageFromRaw(message)

        // 持久化消息到 SharedPreferences
        saveMessagesToPrefs()

        // 同步消息到主界面（不同步"思考中"消息）
        if (!isThinking) {
            val listener = messageSyncListener
            if (listener != null) {
                // 监听器存在时直接同步
                listener.onMessageAdded(message, isUser)
            } else {
                // 监听器不存在时加入待同步队列
                pendingSyncMessages.add(Pair(message, isUser))
            }
        }
    }

    private fun appendFloatingMessageFromRaw(rawMessage: String) {
        val isUser = rawMessage.startsWith("我:") || rawMessage.startsWith("我: ")
        val normalized = rawMessage.replace(" ", "")
        val isThinking = normalized == "AI:思考中..." || normalized == "Aries:思考中..."

        val content =
                if (isUser) {
                    rawMessage.removePrefix("我: ").removePrefix("我:").trimStart()
                } else {
                    rawMessage.removePrefix("AI: ")
                            .removePrefix("AI:")
                            .removePrefix("Aries: ")
                            .removePrefix("Aries:")
                            .trimStart()
                }

        val text = if (isThinking) "思考中..." else content
        _floatingMessages.add(FloatingMessage(text = text, isUser = isUser, isStreaming = false))
    }

    private fun updateStreamingMessageText() {
        val streamIndex = _floatingMessages.indexOfLast { !it.isUser && it.isStreaming }
        if (streamIndex < 0) return

        val composedText =
                when {
                    streamingReasoningText.isNotBlank() && streamingAnswerText.isNotBlank() ->
                        "<think>${streamingReasoningText}</think>\n$streamingAnswerText"
                    streamingReasoningText.isNotBlank() ->
                        "<think>${streamingReasoningText}</think>\n"
                    else -> streamingAnswerText
                }

        _streamingBuffer.value = composedText
        _floatingMessages[streamIndex] = _floatingMessages[streamIndex].copy(text = composedText)
    }

    /** 保存消息到本地存储 */
    private fun saveMessagesToPrefs() {
        try {
            val json = com.google.gson.Gson().toJson(messages)
            floatingChatPrefs.setFloatingMessagesBlocking(json)
            floatingChatPrefs.setFloatingMessagesUpdatedAtBlocking(System.currentTimeMillis())
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 从本地存储恢复消息 */
    private fun restoreMessagesFromPrefs() {
        try {
            val json = floatingChatPrefs.getFloatingMessagesBlocking() ?: return
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val list: List<String> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            messages.clear()
            messages.addAll(list)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 获取当前所有消息（用于展开到全屏时同步） */
    fun getMessages(): List<String> = messages.toList()

    /** 获取聊天历史（用于 AI 对话上下文） */
    fun getChatHistory(): List<ChatRequestMessage> = chatHistory.toList()

    /** 展开到全屏（返回主 Activity） */
    fun expandToFullScreen() {
        setFocusable(false)
        onExpandToFullScreen?.invoke()
        animateDismissAndMaybeOpenApp(openApp = true)
    }

    /** 关闭悬浮窗 */
    fun closeWindow() {
        onClose?.invoke()
        animateDismissAndMaybeOpenApp(openApp = false)
    }

    private fun hideFloatingWindow() {
        // 隐藏聊天视图
        if (isViewAdded && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                // ignore
            }
            floatingView = null
        }
        // 隐藏工具箱视图
        if (isViewAdded && toolboxView != null) {
            try {
                windowManager.removeView(toolboxView)
            } catch (e: Exception) {
                // ignore
            }
            toolboxView = null
        }
        isViewAdded = false
    }

    // ============================================================
    // 工具箱模式方法
    // ============================================================

    /** 创建工具箱视图（虚拟屏预览） */
    private fun createToolboxView(): View {
        val context = this
        val density = resources.displayMetrics.density

        // 计算尺寸
        val widthPx = (windowWidth * density).toInt()
        val headerHeight = (28 * density).toInt()
        val controlBarHeight = (40 * density).toInt()
        val previewHeight = widthPx * 16 / 9 // 16:9 比例

        // 根容器
        val container =
                FrameLayout(context).apply {
                    layoutParams =
                            ViewGroup.LayoutParams(
                                    widthPx,
                                    previewHeight + headerHeight + controlBarHeight
                            )
                }

        // 1. 标题栏
        val header =
                FrameLayout(context).apply {
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    headerHeight
                            )
                    setBackgroundColor(m3ColorWithAlpha(R.color.m3t_floating_header, 220))
                }

        // 标题栏：拖动手柄
        val handleIcon =
                TextView(context).apply {
                    text = "▼"
                    textSize = 10f
                    setTextColor(m3ColorWithAlpha(R.color.m3t_on_surface, 180))
                    gravity = Gravity.CENTER
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.WRAP_CONTENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply {
                                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                        marginStart = (8 * density).toInt()
                                    }
                }
        header.addView(handleIcon)

        // 标题栏：标题文字
        val titleText =
                TextView(context).apply {
                    text = "虚拟屏工具箱"
                    setTextColor(m3Color(R.color.m3t_on_surface))
                    textSize = 12f
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.WRAP_CONTENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply {
                                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                        marginStart = (24 * density).toInt()
                                    }
                }
        header.addView(titleText)

        // 标题栏：关闭按钮
        val closeBtn =
                TextView(context).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(m3ColorWithAlpha(R.color.m3t_on_surface, 220))
                    gravity = Gravity.CENTER
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            (28 * density).toInt(),
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
                    setOnClickListener {
                        // 关闭虚拟屏并停止服务
                        closeWindow()
                    }
                }
        header.addView(closeBtn)

        // 2. 预览区域（虚拟屏画面）
        val previewContainer =
                FrameLayout(context).apply {
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    previewHeight
                            )
                                    .apply { topMargin = headerHeight }
                    setBackgroundColor(m3Color(R.color.m3t_floating_surface))
                }

        // 添加虚拟屏 TextureView 占位（实际由 VirtualScreenPreviewOverlay 绑定）
        val previewPlaceholder =
                TextView(context).apply {
                    text = "📱 虚拟屏预览"
                    textSize = 14f
                    setTextColor(m3ColorWithAlpha(R.color.m3t_floating_text_secondary, 190))
                    gravity = Gravity.CENTER
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                            )
                }
        previewContainer.addView(previewPlaceholder)

        // 关键：完全隔离 - 禁止用户触摸注入虚拟屏
        // 预览区域拦截所有触摸事件，用户无法操作虚拟屏
        previewContainer.setOnTouchListener { _, _ ->
            // 拦截所有触摸事件，不传递到虚拟屏
            true
        }

        // 点击预览区域跳转到任务详情（代替触摸注入）
        previewContainer.setOnClickListener { navigateToTaskDetail() }

        // 3. 状态条（步骤进度）
        val statusBar =
                TextView(context).apply {
                    text = "📍 等待任务开始..."
                    setTextColor(m3ColorWithAlpha(R.color.m3t_floating_text_secondary, 220))
                    textSize = 11f
                    setBackgroundColor(m3ColorWithAlpha(R.color.m3t_surface_container_high, 200))
                    setPadding(
                            (8 * density).toInt(),
                            (4 * density).toInt(),
                            (8 * density).toInt(),
                            (4 * density).toInt()
                    )
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply {
                                        gravity = Gravity.BOTTOM
                                        bottomMargin = controlBarHeight
                                    }
                }
        previewContainer.addView(statusBar)

        // 4. 控制栏
        val controlBar =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(m3ColorWithAlpha(R.color.m3t_floating_header, 230))
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                            controlBarHeight
                                    )
                                    .apply { gravity = Gravity.BOTTOM }
                    setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }

        // 控制按钮辅助函数
        fun makeCtrlBtn(label: String, flex: Float = 1f, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                setTextColor(m3Color(R.color.m3t_on_surface))
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, flex)
                setOnClickListener { onClick() }
                isClickable = true
                isFocusable = true
            }
        }

        // 返回按钮
        controlBar.addView(
                makeCtrlBtn("◀ 返回") {
                    val did = VirtualDisplayController.getDisplayId() ?: return@makeCtrlBtn
                    VirtualDisplayController.injectBackBestEffort(did)
                }
        )

        // Home 按钮
        controlBar.addView(
                makeCtrlBtn("● Home") {
                    val did = VirtualDisplayController.getDisplayId() ?: return@makeCtrlBtn
                    VirtualDisplayController.injectHomeBestEffort(did)
                }
        )

        // 暂停/继续按钮
        controlBar.addView(
                makeCtrlBtn("⏸ 暂停") {
                    // TODO: 实现暂停/继续功能
                    Toast.makeText(context, "暂停功能", Toast.LENGTH_SHORT).show()
                }
        )

        // 停止按钮
        controlBar.addView(
                makeCtrlBtn("⏹ 停止") {
                    // 清理虚拟屏并关闭
                    VirtualDisplayController.cleanupAsync(context)
                    closeWindow()
                }
        )

        // 组装视图
        container.addView(header)
        container.addView(previewContainer)
        container.addView(controlBar)

        // 设置拖拽行为
        setupDragBehavior(header)

        // 点击标题栏跳转到任务详情
        header.setOnClickListener { navigateToTaskDetail() }

        return container
    }

    /** 更新工具箱步骤显示 */
    fun updateToolboxProgress(step: Int, total: Int, description: String) {
        currentToolboxStep = step
        totalToolboxSteps = total
        currentToolboxDescription = description

        // 查找状态条并更新
        toolboxView?.let { view ->
            val statusBar = view.findViewById<TextView>(android.R.id.text1)
            view.post { statusBar?.text = "📍 第 $step/$total 步：$description" }
        }
    }

    /** 跳转到任务详情页面 */
    private fun navigateToTaskDetail() {
        // 关闭悬浮窗
        closeWindow()

        // 跳转到 MainActivity（自动化路由）
        val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
                }
        startActivity(intent)
    }

    // ============================================================

    private fun saveWindowState() {
        floatingChatPrefs.setWindowXBlocking(windowX)
        floatingChatPrefs.setWindowYBlocking(windowY)
        floatingChatPrefs.setWindowWidthBlocking(windowWidth)
        floatingChatPrefs.setWindowHeightBlocking(windowHeight)
    }

    private fun restoreWindowState() {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val screenHeightDp = (displayMetrics.heightPixels / displayMetrics.density).toInt()

        // 机型自适配算法：
        // 1. 宽度取屏幕宽度的 80%，但不超过 360dp，且不小于 280dp
        // 2. 高度取屏幕高度的 50%，但不超过 480dp，且不小于 340dp
        val autoWidth = (screenWidthDp * 0.8).toInt().coerceIn(280, 360)
        val autoHeight = (screenHeightDp * 0.52).toInt().coerceIn(340, 480)

        windowWidth = floatingChatPrefs.getWindowWidthBlocking().takeIf { it > 0 } ?: autoWidth
        windowHeight = floatingChatPrefs.getWindowHeightBlocking().takeIf { it > 0 } ?: autoHeight

        // 检查之前保存的坐标是否超出当前屏幕（万一分辨率变了）
        windowX =
                floatingChatPrefs.getWindowXBlocking()
                        .coerceIn(
                                0,
                                maxOf(
                                        0,
                                        displayMetrics.widthPixels -
                                                (windowWidth * displayMetrics.density).toInt()
                                )
                        )
        windowY =
                floatingChatPrefs.getWindowYBlocking()
                        .coerceIn(
                                0,
                                maxOf(
                                        0,
                                        displayMetrics.heightPixels -
                                                (windowHeight * displayMetrics.density).toInt()
                                )
                        )
    }
}

@Composable
private fun FloatingTitleBar(
        onFullscreen: () -> Unit,
        onClose: () -> Unit,
        modifier: Modifier = Modifier,
) {
    Row(
            modifier = modifier.height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
                text = "Aries",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onFullscreen, modifier = Modifier.size(32.dp)) {
            Icon(
                    painter = painterResource(id = R.drawable.ic_fullscreen_24),
                    contentDescription = "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                    painter = painterResource(id = R.drawable.ic_close_24),
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FloatingInputBar(
        onSend: (String) -> Unit,
        onFocused: () -> Unit,
        onUnfocused: () -> Unit,
        modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Row(
            modifier =
                    modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).onFocusChanged { state ->
                    if (state.isFocused) onFocused() else onUnfocused()
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank()) {
                                onSend(text.trim())
                                text = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        }
                ),
                decorationBox = { innerTextField ->
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    color =
                                                            MaterialTheme
                                                                    .colorScheme
                                                                    .surfaceContainerHighest,
                                                    shape = MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                    text = "发消息...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
                textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                        ),
                singleLine = false,
                maxLines = 4,
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = text.isNotBlank(),
        ) {
            Icon(
                    painter = painterResource(id = R.drawable.ic_send_24),
                    contentDescription = "发送",
                    tint =
                            if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FloatingUserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 240.dp),
        ) {
            Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FloatingAiBubble(text: String, isStreaming: Boolean = false) {
    val thinkRegex = remember { "<think>([\\s\\S]*?)</think>([\\s\\S]*)".toRegex() }
    val match = remember(text) { thinkRegex.find(text) }
    val thinkContent = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val mainContent = match?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank {
        if (match == null) text else ""
    }
    val thinkExpandedState = remember(text) { mutableStateOf(false) }

    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 260.dp)) {
            if (thinkContent.isNotBlank()) {
                Row(
                        modifier =
                                Modifier.clickable {
                                            thinkExpandedState.value = !thinkExpandedState.value
                                        }
                                        .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                            text = "已思考",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                            text = if (thinkExpandedState.value) " ⌄" else " ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (thinkExpandedState.value) {
                    Text(
                            text = thinkContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                val displayText =
                        when {
                            isStreaming && mainContent.isEmpty() && thinkContent.isEmpty() -> "▌"
                            isStreaming && mainContent.isEmpty() -> "▌"
                            else -> mainContent
                        }

                Text(
                        text = displayText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
