package com.ai.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.ContextThemeWrapper
import com.ai.phoneagent.R
import com.ai.phoneagent.helper.StreamRenderHelper
import com.ai.phoneagent.net.AutoGlmClient
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.ai.phoneagent.net.ChatRequestMessage
import kotlinx.coroutines.*

/**
 * 悬浮聊天窗口服务
 * 提供小窗模式的聊天界面
 */
class FloatingChatService : Service() {

    companion object {
        private const val TAG = "FloatingChatService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_chat_channel"
        private const val OPEN_APP_PI_REQUEST_CODE = 1002
        private const val LAUNCH_PROXY_EXTRA_TARGET_INTENT = "target_intent"

        const val ACTION_FLOATING_RETURNED = "com.ai.phoneagent.action.FLOATING_RETURNED"
        
        @Volatile
        private var instance: FloatingChatService? = null
        
        fun getInstance(): FloatingChatService? = instance
        
        fun isRunning(): Boolean = instance != null
        
        /**
         * 检查是否有悬浮窗权限
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
        
        /**
         * 启动悬浮窗服务
         */
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
            val intent = Intent(context, FloatingChatService::class.java).apply {
                messages?.let { putStringArrayListExtra("messages", it) }
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
        
        /**
         * 停止悬浮窗服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingChatService::class.java))
        }
        
        /**
         * 消息同步监听器接口
         */
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
    private lateinit var prefs: SharedPreferences
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
    
    // 消息列表
    private val messages = mutableListOf<String>()
    private val chatHistory = mutableListOf<ChatRequestMessage>()  // 用于 AI 对话上下文
    
    // 回调
    var onExpandToFullScreen: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    
    // API Key
    private var apiKey: String = ""

    // 语音输入（系统 SpeechRecognizer）
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    private var awaitingReturnAck: Boolean = false

    private var lastOpenAppAttemptAt: Long = 0L

    private var overlayHiddenForReturn: Boolean = false

    private var overlayTouchBlockedForReturn: Boolean = false

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
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        
        // 获取 API Key（从主应用的 SharedPreferences）
        val appPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        apiKey = appPrefs.getString("api_key", "") ?: ""
        
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
        } ?: run {
            // 如果没有传入消息，从本地存储恢复
            restoreMessagesFromPrefs()
            if (messages.isNotEmpty()) {
                restoreChatHistory(messages)
            }
        }
        
        // 获取初始位置和尺寸
        intent?.let {
            val density = resources.displayMetrics.density

            targetX = it.getFloatExtra("from_x", 100f).toInt()
            targetY = it.getFloatExtra("from_y", 200f).toInt()
            windowWidth = (it.getFloatExtra("from_width", 320f * density) / density).toInt()
            windowHeight = (it.getFloatExtra("from_height", 400f * density) / density).toInt()

            showDelayMs = it.getLongExtra("show_delay_ms", 0L)
        }
        
        // 显示悬浮窗
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
        serviceScope.cancel()  // 取消协程
        hideFloatingWindow()
        saveWindowState()
        saveMessagesToPrefs()  // 保存消息到本地存储
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
            6000L
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

    private fun noAnimOptions(): android.os.Bundle? {
        return runCatching {
                ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
            }
            .getOrNull()
    }
    
    /**
     * 从消息列表恢复聊天历史
     */
    private fun restoreChatHistory(messageList: List<String>) {
        chatHistory.clear()
        for (msg in messageList) {
            // 支持"我:"和"我: "两种格式
            val isUser = msg.startsWith("我:") || msg.startsWith("我: ")
            val content =
                msg
                    .removePrefix("我: ")
                    .removePrefix("我:")
                    .removePrefix("Aries: ")
                    .removePrefix("Aries:")
                    .removePrefix("AI: ")
                    .removePrefix("AI:")
                    .trim()
            if (content.isNotBlank()) {
                chatHistory.add(ChatRequestMessage(
                    role = if (isUser) "user" else "assistant",
                    content = content
                ))
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
                    val needsRebuild = error == SpeechRecognizer.ERROR_CLIENT || 
                                       error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                       error == SpeechRecognizer.ERROR_SERVER
                    if (needsRebuild) {
                        runCatching { speechRecognizer?.destroy() }
                        speechRecognizer = null
                    }
                    
                    // 特殊处理：如果系统语音不可用，提示用户
                    val msg = if (!SpeechRecognizer.isRecognitionAvailable(this@FloatingChatService)) {
                        "设备不支持系统语音识别，请使用主界面语音功能"
                    } else {
                        speechErrorToMessage(error)
                    }
                    Toast.makeText(this@FloatingChatService, msg, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: android.os.Bundle?) {
                    isListening = false
                    val list =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
        // 某些系统会在上一次会话未完全结束时 Busy；先 cancel 再启动更稳
        runCatching { sr.cancel() }
        runCatching { sr.startListening(intent) }
            .onFailure {
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮聊天窗口",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
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

        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_chat_window, null)

        val density = resources.displayMetrics.density
        val widthPx = (windowWidth * density).toInt()
        val heightPx = (windowHeight * density).toInt()

        val displayMetrics = resources.displayMetrics
        val startX = ((displayMetrics.widthPixels - widthPx) / 2f).toInt()
        val startY = ((displayMetrics.heightPixels - heightPx) / 2f).toInt()
        val endX = targetX
        val endY = targetY

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        windowX = startX
        windowY = startY

        setupFloatingView()

        windowManager.addView(floatingView, layoutParams)
        isViewAdded = true

        val view = floatingView ?: return
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

    private fun setupFloatingView() {
        val view = floatingView ?: return

        // 标题栏拖动
        val titleBar = view.findViewById<View>(R.id.floatingTitleBar)
        setupDragBehavior(titleBar)

        // 全屏按钮
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreen)
        btnFullscreen.setOnClickListener {
            setFocusable(false)
            expandToFullScreen()
        }

        // 返回主页按钮
        val btnHome = view.findViewById<ImageButton>(R.id.btnHome)
        btnHome.setOnClickListener {
            setFocusable(false)
            expandToFullScreen()
        }

        // 关闭按钮
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener {
            closeWindow()
        }

        // 输入框 - 优化键盘呼出响应速度
        val inputMessage = view.findViewById<EditText>(R.id.inputMessage)

        // 语音按钮
        val btnVoice = view.findViewById<ImageButton>(R.id.btnVoice)
        btnVoice.setOnClickListener {
            // 悬浮窗里无法弹权限对话框；如果没权限提示用户回主界面授予
            val granted =
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(this, "请先在主界面授予录音权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setFocusable(true)
            if (isListening) stopVoiceInput() else startVoiceInput(inputMessage)
        }

        // 预先设置为可聚焦模式，避免延迟
        inputMessage.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // 立即切换为可聚焦模式
                setFocusable(true)

                // 关键：切换窗口 flags 后，显式让 EditText 获取焦点并拉起键盘
                inputMessage.isFocusableInTouchMode = true
                inputMessage.requestFocus()
                inputMessage.post {
                    runCatching {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(inputMessage, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            false // 继续传递事件
        }

        inputMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 确保键盘弹出
                mainHandler.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(inputMessage, InputMethodManager.SHOW_IMPLICIT)
                }, 50)
            }
        }

        // 发送按钮
        val btnSend = view.findViewById<ImageButton>(R.id.btnSend)
        btnSend.setOnClickListener {
            val text = inputMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendUserMessage(text, inputMessage)
            }
        }

        // 监听回车键发送
        inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val text = inputMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendUserMessage(text, inputMessage)
                }
                true
            } else false
        }

        // 更新消息列表
        updateMessagesUI()
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        val movedToFront =
            runCatching {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val task =
                        am.appTasks.firstOrNull { t ->
                            val info = runCatching { t.taskInfo }.getOrNull() ?: return@firstOrNull false
                            val mainName = MainActivity::class.java.name
                            info.baseActivity?.className == mainName || info.topActivity?.className == mainName
                        } ?: am.appTasks.firstOrNull() ?: return@runCatching false
                    runCatching { task.moveToFront() }
                    task.startActivity(this, baseIntent, noAnimOptions())
                    true
                }
                .getOrDefault(false)
        if (movedToFront) return

        val directOk = runCatching { startActivity(targetIntentForService, noAnimOptions()) }.isSuccess
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
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                            }
                        pi.send(this, 0, null, null, null, null, options.toBundle())
                    } else {
                        pi.send()
                    }
                }
                .isSuccess
        if (piSent) return

        runCatching { startActivity(proxyIntent, noAnimOptions()) }
            .recoverCatching { startActivity(targetIntentForService, noAnimOptions()) }
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

        // 【优化】立即隐藏视图，完全避免闪烁
        if (openApp) {
            // 立即将视图设为不可见和不可触摸
            view.animate().cancel()
            view.visibility = View.INVISIBLE
            setTouchable(false)
            overlayHiddenForReturn = true
            
            awaitingReturnAck = true
            scheduleRetryOpenAppWhileWaitingAck()
            scheduleStopAfterReturnTimeout()
            
            // 直接拉起主界面，不再做淡出动画（因为已经 INVISIBLE 了）
            mainHandler.post {
                requestOpenApp(allowProxy = false)
            }
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
            .scaleX(0.2f)
            .scaleY(0.2f)
            .setStartDelay(0)
            .setDuration(180)
            .setInterpolator(exitInterpolator)
            .withEndAction {
                hideFloatingWindow()
                stopSelf()
            }
            .start()
    }

    /**
     * 发送用户消息并获取 AI 回复
     */
    private fun sendUserMessage(text: String, inputMessage: EditText) {
        // 添加用户消息
        addMessage("我: $text", isUser = true)
        inputMessage.setText("")
        
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputMessage.windowToken, 0)
        
        // 已移除: addMessage("Aries: 思考中...", isUser = false, isThinking = true)
        
        // 发送 AI 请求
        requestAIResponse(text)
    }
    
    private var currentStreamViewHolder: StreamRenderHelper.ViewHolder? = null
    
    /**
     * 请求 AI 回复
     */
    private fun requestAIResponse(userText: String) {
        if (apiKey.isBlank()) {
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
                            
                            你必须严格按以下结构输出（否则我的 Android 应用无法正确渲染）：
                            
                            【思考开始】
                            （这里写你的思考过程）
                            【思考结束】
                            
                            【回答开始】
                            （这里写你的最终回答，使用 Markdown：标题/列表/代码块/表格等）
                            【回答结束】
                            
                            要求：
                            1) 以上四个标记必须原样输出，且不要输出其它同名/相似标记。
                            2) 思考内容写在“思考开始/结束”之间；正式回答写在“回答开始/结束”之间。
                            3) 代码块使用三反引号 ``` 并尽量保持语法完整。
                        """.trimIndent(),
                ),
            )
        }
        
        chatHistory.add(ChatRequestMessage(role = "user", content = userText))
        
        serviceScope.launch {
            // 准备流式渲染视图
            val container = floatingView?.findViewById<LinearLayout>(R.id.messagesContainer)
            if (container != null) {
                withContext(Dispatchers.Main) {
                    // 使用 Theme.PhoneAgent 包装 Context，防止 Context 引起的崩溃
                    val contextWrapper = ContextThemeWrapper(this@FloatingChatService, R.style.Theme_PhoneAgent)
                    val inflater = LayoutInflater.from(contextWrapper)
                    
                    val aiView = inflater.inflate(R.layout.item_ai_message_complex, container, false)
                    container.addView(aiView)
                    
                    val vh = StreamRenderHelper.bindViews(aiView)
                    StreamRenderHelper.initThinkingState(vh)
                    currentStreamViewHolder = vh

                    vh.copyButton?.setOnClickListener {
                        val cm =
                            getSystemService(android.content.Context.CLIPBOARD_SERVICE) as
                                android.content.ClipboardManager
                        val clip =
                            android.content.ClipData.newPlainText(
                                "AI Reply",
                                vh.messageContent.text,
                            )
                        cm.setPrimaryClip(clip)
                        Toast.makeText(this@FloatingChatService, "已复制内容", Toast.LENGTH_SHORT)
                            .show()
                    }
                    
                    // 滚动到底部
                    floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
                }
            }

            val vh = currentStreamViewHolder
            val reasoningSb = StringBuilder()
            val contentSb = StringBuilder()

            var streamOk = false
            
            val result = AutoGlmClient.sendChatStreamResult(
                apiKey = apiKey,
                messages = chatHistory,
                onReasoningDelta = { delta ->
                    if (delta.isNotBlank() && vh != null) {
                        reasoningSb.append(delta)
                        Handler(Looper.getMainLooper()).post {
                            StreamRenderHelper.processReasoningDelta(vh, delta, serviceScope) {
                                floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                },
                onContentDelta = { delta ->
                    if (delta.isNotEmpty() && vh != null) {
                        contentSb.append(delta)
                        Handler(Looper.getMainLooper()).post {
                            StreamRenderHelper.processContentDelta(
                                vh,
                                delta,
                                serviceScope,
                                this@FloatingChatService,
                                onScroll = {
                                    floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
                                },
                                onPhaseChange = { isAnswerPhase ->
                                    if (isAnswerPhase) {
                                        StreamRenderHelper.transitionToAnswer(vh)
                                    }
                                }
                            )
                        }
                    }
                }
            )
            
            streamOk = result.isSuccess
            
            // 结束后整理状态
            withContext(Dispatchers.Main) {
                if (vh != null) {
                     StreamRenderHelper.markCompleted(vh, 0)
                }
                
                // 获取解析后的内容
                val thinkingContent = vh?.let { StreamRenderHelper.getThinkingText(it) } ?: ""
                val answerContent = vh?.let { StreamRenderHelper.getAnswerText(it) } ?: contentSb.toString()
                
                val persistText = if (thinkingContent.isNotEmpty()) {
                    "<think>$thinkingContent</think>\n$answerContent"
                } else {
                    answerContent
                }
                
                messages.add("Aries: $persistText")
                saveMessagesToPrefs()
                
                chatHistory.add(ChatRequestMessage(role = "assistant", content = persistText))
            }
        }
    }

    // --- 外部同步接口 (供 MainActivity 调用) ---

    fun beginExternalStreamAiReply() {
         // 在悬浮窗也准备一个气泡跟随主界面
         Handler(Looper.getMainLooper()).post {
             val container = floatingView?.findViewById<LinearLayout>(R.id.messagesContainer) ?: return@post
             // 使用主题包装
             val contextWrapper = ContextThemeWrapper(this@FloatingChatService, R.style.Theme_PhoneAgent)
             val inflater = LayoutInflater.from(contextWrapper)
             val aiView = inflater.inflate(R.layout.item_ai_message_complex, container, false)
             container.addView(aiView)
             
             val vh = StreamRenderHelper.bindViews(aiView)
             StreamRenderHelper.initThinkingState(vh)
             currentStreamViewHolder = vh
             
             floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
         }
    }
    
    fun appendExternalReasoningDelta(delta: String) {
        val vh = currentStreamViewHolder ?: return
        Handler(Looper.getMainLooper()).post {
             StreamRenderHelper.processReasoningDelta(vh, delta, serviceScope) {
                 floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
             }
        }
    }
    
    fun appendExternalContentDelta(delta: String) {
        val vh = currentStreamViewHolder ?: return
        Handler(Looper.getMainLooper()).post {
            // 使用新的智能解析处理方法
            StreamRenderHelper.processContentDelta(
                vh,
                delta,
                serviceScope,
                this@FloatingChatService,
                onScroll = {
                    floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
                },
                onPhaseChange = { isAnswerPhase ->
                    if (isAnswerPhase) {
                        StreamRenderHelper.transitionToAnswer(vh)
                    }
                }
            )
        }
    }
    
    fun resetExternalStreamAiReply() {
        val vh = currentStreamViewHolder ?: return
        Handler(Looper.getMainLooper()).post {
             StreamRenderHelper.initThinkingState(vh)
        }
    }
    
    fun finishExternalStreamAiReply(timeCost: Int, finalContent: String) {
        val vh = currentStreamViewHolder ?: return
        Handler(Looper.getMainLooper()).post {
             StreamRenderHelper.markCompleted(vh, timeCost.toLong())
             
             // 获取解析后的内容保存
             val thinkingContent = StreamRenderHelper.getThinkingText(vh)
             val answerContent = StreamRenderHelper.getAnswerText(vh)
             
             val persistText = if (thinkingContent.isNotEmpty()) {
                 "<think>$thinkingContent</think>\n$answerContent"
             } else if (answerContent.isNotEmpty()) {
                 answerContent
             } else {
                 finalContent
             }
             
             messages.add("Aries: $persistText")
             saveMessagesToPrefs()
        }
    }

    
    /**
     * 设置悬浮窗是否可聚焦（用于键盘输入）
     */
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
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            
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
        val container = floatingView?.findViewById<LinearLayout>(R.id.messagesContainer) ?: return
        container.removeAllViews()
        val contextWrapper = ContextThemeWrapper(this@FloatingChatService, R.style.Theme_PhoneAgent)
        val inflater = LayoutInflater.from(contextWrapper)
        
        for (msg in messages) {
            // 支持“我:”和“我: ”两种格式
            val isUser = msg.startsWith("我:") || msg.startsWith("我: ")
            val normalized = msg.replace(" ", "")
            val isThinking =
                normalized == "AI:思考中..." ||
                    normalized == "Aries:思考中..."

            if (isThinking) {
                val textView = TextView(this).apply {
                    text = msg
                    textSize = 14f
                    setTextColor(0xFF999999.toInt())
                    setPadding(16, 8, 16, 8)
                    setTypeface(null, android.graphics.Typeface.ITALIC)
                }
                container.addView(textView)
                continue
            }

            if (isUser) {
                val content =
                    msg.removePrefix("我: ")
                        .removePrefix("我:")
                        .trimStart()

                val textView = TextView(this).apply {
                    text = content
                    textSize = 14f
                    setTextColor(0xFF333333.toInt())
                    setPadding(16, 8, 16, 8)
                }
                container.addView(textView)
                continue
            }

            val content =
                msg.removePrefix("AI: ")
                    .removePrefix("AI:")
                    .removePrefix("Aries: ")
                    .removePrefix("Aries:")
                    .trimStart()

            val aiView = inflater.inflate(R.layout.item_ai_message_complex, container, false)
            container.addView(aiView)

            val vh = StreamRenderHelper.bindViews(aiView)
            vh.authorName.text = "Aries"
            vh.authorName.visibility = View.VISIBLE

            val thinkRegex = "<think>([\\s\\S]*?)</think>([\\s\\S]*)".toRegex()
            val match = thinkRegex.find(content)
            val thinkContent = match?.groupValues?.get(1)?.trim()
            val realContent = match?.groupValues?.get(2)?.trim() ?: content

            if (!thinkContent.isNullOrBlank()) {
                vh.thinkingLayout.visibility = View.VISIBLE
                val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
                headerTitle?.text = "已思考"

                var expanded = true
                vh.thinkingText.visibility = View.VISIBLE
                vh.thinkingContentArea.visibility = View.VISIBLE
                vh.thinkingIndicator.text = " ⌄"

                vh.thinkingHeader.setOnClickListener {
                    expanded = !expanded
                    vh.thinkingText.visibility = if (expanded) View.VISIBLE else View.GONE
                    vh.thinkingContentArea.visibility = if (expanded) View.VISIBLE else View.GONE
                    vh.thinkingIndicator.text = if (expanded) " ⌄" else " ›"
                }

                StreamRenderHelper.applyMarkdownToHistory(vh.thinkingText, thinkContent)
            } else {
                vh.thinkingLayout.visibility = View.GONE
            }

            StreamRenderHelper.applyMarkdownToHistory(vh.messageContent, realContent)
            vh.actionArea.visibility = View.VISIBLE
            vh.retryButton?.visibility = View.GONE
            vh.copyButton?.setOnClickListener {
                val cm =
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE) as
                        android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Reply", realContent)
                cm.setPrimaryClip(clip)
                Toast.makeText(this@FloatingChatService, "已复制内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 滚动到底部
        floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.post {
            floatingView?.findViewById<ScrollView>(R.id.scrollArea)?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 添加消息到悬浮窗（带同步功能）
     * @param message 消息内容
     * @param isUser 是否是用户消息
     * @param isThinking 是否是"思考中"消息（不同步）
     */
    fun addMessage(message: String, isUser: Boolean = false, isThinking: Boolean = false) {
        messages.add(message)
        updateMessagesUI()
        
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
    
    /**
     * 保存消息到本地存储
     */
    private fun saveMessagesToPrefs() {
        try {
            val json = com.google.gson.Gson().toJson(messages)
            prefs.edit()
                .putString("floating_messages", json)
                .putLong("floating_messages_updated_at", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // ignore
        }
    }
    
    /**
     * 从本地存储恢复消息
     */
    private fun restoreMessagesFromPrefs() {
        try {
            val json = prefs.getString("floating_messages", null) ?: return
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val list: List<String> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            messages.clear()
            messages.addAll(list)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    /**
     * 获取当前所有消息（用于展开到全屏时同步）
     */
    fun getMessages(): List<String> = messages.toList()
    
    /**
     * 获取聊天历史（用于 AI 对话上下文）
     */
    fun getChatHistory(): List<ChatRequestMessage> = chatHistory.toList()
    
    /**
     * 展开到全屏（返回主 Activity）
     */
    fun expandToFullScreen() {
        setFocusable(false)
        onExpandToFullScreen?.invoke()
        animateDismissAndMaybeOpenApp(openApp = true)
    }
    
    /**
     * 关闭悬浮窗
     */
    fun closeWindow() {
        onClose?.invoke()
        animateDismissAndMaybeOpenApp(openApp = false)
    }
    
    private fun hideFloatingWindow() {
        if (isViewAdded && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                // ignore
            }
            floatingView = null
            isViewAdded = false
        }
    }
    
    private fun saveWindowState() {
        prefs.edit()
            .putInt("window_x", windowX)
            .putInt("window_y", windowY)
            .putInt("window_width", windowWidth)
            .putInt("window_height", windowHeight)
            .apply()
    }
    
    private fun restoreWindowState() {
        windowX = prefs.getInt("window_x", 100)
        windowY = prefs.getInt("window_y", 200)
        windowWidth = prefs.getInt("window_width", 320)
        windowHeight = prefs.getInt("window_height", 400)
    }
}
