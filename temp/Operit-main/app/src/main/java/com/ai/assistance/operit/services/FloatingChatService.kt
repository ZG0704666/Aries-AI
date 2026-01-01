package com.ai.assistance.operit.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.ai.assistance.operit.util.AppLogger
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.SerializableColorScheme
import com.ai.assistance.operit.data.model.SerializableTypography
import com.ai.assistance.operit.data.model.toComposeColorScheme
import com.ai.assistance.operit.data.model.toComposeTypography
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.floating.FloatingWindowCallback
import com.ai.assistance.operit.services.floating.FloatingWindowManager
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.services.floating.StatusIndicatorStyle
import com.ai.assistance.operit.ui.floating.FloatingMode
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingChatService : Service(), FloatingWindowCallback {
    private val TAG = "FloatingChatService"
    private val binder = LocalBinder()

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "floating_chat_channel"

    private val PREF_KEY_STATUS_INDICATOR_STYLE = "status_indicator_style"

    lateinit var windowState: FloatingWindowState
    private lateinit var windowManager: FloatingWindowManager
    private lateinit var prefs: SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    private val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    private val attachments = mutableStateOf<List<AttachmentInfo>>(emptyList())
    private val inputProcessingState = mutableStateOf<InputProcessingState>(InputProcessingState.Idle)

    // 聊天服务核心 - 整合所有业务逻辑
    private lateinit var chatCore: ChatServiceCore

    private var lastCrashTime = 0L
    private var crashCount = 0
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val customExceptionHandler =
            Thread.UncaughtExceptionHandler { thread, throwable ->
                handleServiceCrash(thread, throwable)
            }

    private val colorScheme = mutableStateOf<ColorScheme?>(null)
    private val typography = mutableStateOf<Typography?>(null)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        @Volatile
        private var instance: FloatingChatService? = null

        fun getInstance(): FloatingChatService? = instance
    }

    inner class LocalBinder : Binder() {
        private var closeCallback: (() -> Unit)? = null
        private var reloadCallback: (() -> Unit)? = null
        
        fun getService(): FloatingChatService = this@FloatingChatService
        fun getChatCore(): ChatServiceCore = chatCore
        
        fun setCloseCallback(callback: () -> Unit) {
            this.closeCallback = callback
        }
        
        fun notifyClose() {
            closeCallback?.invoke()
        }
        
        fun setReloadCallback(callback: () -> Unit) {
            this.reloadCallback = callback
        }
        
        fun notifyReload() {
            reloadCallback?.invoke()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun handleServiceCrash(thread: Thread, throwable: Throwable) {
        try {
            AppLogger.e(TAG, "Service crashed: ${throwable.message}", throwable)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCrashTime > 60000) {
                crashCount = 0
            }
            lastCrashTime = currentTime
            crashCount++

            if (crashCount > 3) {
                AppLogger.e(TAG, "Too many crashes in short time, stopping service")
                prefs.edit().putBoolean("service_disabled_due_to_crashes", true).apply()
                stopSelf()
                return
            }

            saveState()
            val intent = Intent(applicationContext, FloatingChatService::class.java)
            intent.setPackage(packageName)
            startService(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error handling crash", e)
        } finally {
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "onCreate")

        instance = this

        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler)

        prefs = getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            AppLogger.w(TAG, "Service was disabled due to frequent crashes")
            stopSelf()
            return
        }

        try {
            acquireWakeLock()
            
            // 初始化 ChatServiceCore
            chatCore = ChatServiceCore(context = this, coroutineScope = serviceScope)
            AppLogger.d(TAG, "ChatServiceCore 已初始化")
            
            // 设置额外的 onTurnComplete 回调，用于通知应用重新加载消息
            chatCore.setAdditionalOnTurnComplete {
                AppLogger.d(TAG, "流完成，通知应用重新加载消息")
                binder.notifyReload()
            }
            
            // 订阅聊天历史更新
            serviceScope.launch {
                chatCore.chatHistory.collect { messages ->
                    chatMessages.value = messages
                    AppLogger.d(TAG, "聊天历史已更新: ${messages.size} 条消息")
                }
            }
            
            // 订阅附件列表更新
            serviceScope.launch {
                chatCore.attachments.collect { newAttachments ->
                    attachments.value = newAttachments
                    AppLogger.d(TAG, "附件列表已更新: ${newAttachments.size} 个附件")
                }
            }

            // 订阅输入处理状态更新
            serviceScope.launch {
                chatCore.inputProcessingState.collect { state ->
                    inputProcessingState.value = state
                    AppLogger.d(TAG, "输入处理状态已更新: $state")
                }
            }
            
            // 设置 EnhancedAIService 就绪回调，以便监听输入处理状态
            chatCore.setOnEnhancedAiServiceReady { aiService ->
                AppLogger.d(TAG, "EnhancedAIService 已就绪，开始监听输入处理状态")
                serviceScope.launch {
                    try {
                        aiService.inputProcessingState.collect { state ->
                            chatCore.handleInputProcessingState(state)
                            AppLogger.d(TAG, "输入处理状态已更新: $state")
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "监听输入处理状态失败", e)
                    }
                }
            }
            
            // 订阅 Toast 事件
            serviceScope.launch {
                chatCore.getUiStateDelegate().toastEvent.collect { message ->
                    message?.let {
                        Toast.makeText(this@FloatingChatService, it, Toast.LENGTH_SHORT).show()
                        chatCore.getUiStateDelegate().clearToastEvent()
                    }
                }
            }
            
            lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            windowState = FloatingWindowState(this)
            windowManager =
                    FloatingWindowManager(
                            this,
                            windowState,
                            lifecycleOwner,
                            lifecycleOwner,
                            lifecycleOwner,
                            this
                    )
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                        powerManager.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "OperitApp:FloatingChatServiceWakeLock"
                        )
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
                AppLogger.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AI助手悬浮窗"
            val descriptionText = getString(R.string.floating_service_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                    NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        setShowBadge(false)
                    }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() =
            NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("AI助手悬浮窗")
                    .setContentText("AI助手正在后台运行")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(getPendingIntent())
                    .build()

    private fun getPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand")
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            acquireWakeLock()

            // Handle initial mode from intent
            intent?.getStringExtra("INITIAL_MODE")?.let { modeName ->
                try {
                    val mode = FloatingMode.valueOf(modeName)
                    windowState.currentMode.value = mode
                    AppLogger.d(TAG, "Set mode from intent: $mode")
                } catch (e: IllegalArgumentException) {
                    AppLogger.w(TAG, "Invalid mode name in intent: $modeName")
                }
            }

            if (intent?.hasExtra("CHAT_MESSAGES") == true) {
                @Suppress("DEPRECATION")
                val messagesArray = if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                    intent.getParcelableArrayExtra("CHAT_MESSAGES", ChatMessage::class.java)
                } else {
                    intent.getParcelableArrayExtra("CHAT_MESSAGES")
                }
                if (messagesArray != null) {
                    val messages = mutableListOf<ChatMessage>()
                    messagesArray.forEach { if (it is ChatMessage) messages.add(it) }
                    updateChatMessages(messages)
                }
            }
            if (intent?.hasExtra("COLOR_SCHEME") == true) {
                val serializableColorScheme =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                    "COLOR_SCHEME",
                                    SerializableColorScheme::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<SerializableColorScheme>("COLOR_SCHEME")
                        }
                serializableColorScheme?.let { colorScheme.value = it.toComposeColorScheme() }
            }
            if (intent?.hasExtra("TYPOGRAPHY") == true) {
                val serializableTypography =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                    "TYPOGRAPHY",
                                    SerializableTypography::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<SerializableTypography>("TYPOGRAPHY")
                        }
                serializableTypography?.let { typography.value = it.toComposeTypography() }
            }
            windowManager.show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        AppLogger.d(TAG, "onTaskRemoved")
        val restartServiceIntent =
                Intent(applicationContext, this.javaClass).apply { setPackage(packageName) }
        startService(restartServiceIntent)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLogger.d(TAG, "onLowMemory: 系统内存不足")
        saveState()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppLogger.d(TAG, "onTrimMemory: level=$level")
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        ) {
            saveState()
        }
    }

    private fun handleAttachmentRequest(request: String) {
        AppLogger.d(TAG, "Attachment request received: $request")
        serviceScope.launch {
            try {
                // 直接使用 chatCore 的 AttachmentDelegate 处理附件
                chatCore.handleAttachment(request)
                AppLogger.d(TAG, "附件已添加: $request")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling attachment request", e)
            }
        }
    }

    fun removeAttachment(filePath: String) {
        AppLogger.d(TAG, "移除附件: $filePath")
        // 直接使用 chatCore 的 AttachmentDelegate 移除附件
        chatCore.removeAttachment(filePath)
    }

    fun updateChatMessages(messages: List<ChatMessage>) {
        serviceScope.launch {
            AppLogger.d(
                    TAG,
                    "服务收到消息更新: ${messages.size} 条. 最后一条消息的 stream is null: ${messages.lastOrNull()?.contentStream == null}"
            )
            
            // 智能合并：通过 timestamp 匹配已存在的消息，保持原实例不变
            val currentMessages = chatMessages.value
            val currentMessageMap = currentMessages.associateBy { it.timestamp }
            
            val mergedMessages = messages.map { newMsg ->
                val existingMsg = currentMessageMap[newMsg.timestamp]
                if (existingMsg != null) {
                    // 消息已存在，保持原实例，但更新内容（如果内容有变化）
                    if (existingMsg.content != newMsg.content || existingMsg.roleName != newMsg.roleName) {
                        existingMsg.copy(content = newMsg.content, roleName = newMsg.roleName)
                    } else {
                        existingMsg
                    }
                } else {
                    // 新消息，直接添加
                    newMsg
                }
            }
            
            chatMessages.value = mergedMessages
            AppLogger.d(TAG, "智能合并完成: 当前 ${currentMessages.size} 条 -> 合并后 ${mergedMessages.size} 条")
        }
    }

    override fun onDestroy() {
        try {
            releaseWakeLock()
            
            serviceScope.cancel()
            saveState()
            super.onDestroy()
            AppLogger.d(TAG, "onDestroy")
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.destroy()
            Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)
            prefs.edit().putInt("view_creation_retry", 0).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in onDestroy", e)
        }
        instance = null
    }

    override fun onClose() {
        AppLogger.d(TAG, "Close request from window manager")
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                windowManager.prepareForExit()
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        windowManager.prepareForExit()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        binder.notifyClose()
        stopSelf()
    }

    override fun onSendMessage(message: String, promptType: PromptFunctionType) {
        AppLogger.d(TAG, "onSendMessage: $message, promptType: $promptType")
        
        // 直接使用 chatCore 发送消息，不再通过 SharedFlow
        serviceScope.launch {
            try {
                // 获取当前聊天ID，如果没有则创建新聊天
                var chatId = chatCore.currentChatId.value
                if (chatId == null) {
                    AppLogger.d(TAG, "当前没有活跃对话，自动创建新对话")
                    chatCore.createNewChat()
                    
                    // 等待对话ID更新
                    var waitCount = 0
                    while (chatCore.currentChatId.value == null && waitCount < 10) {
                        kotlinx.coroutines.delay(100)
                        waitCount++
                    }
                    
                    chatId = chatCore.currentChatId.value
                    if (chatId == null) {
                        AppLogger.e(TAG, "创建新对话超时，无法发送消息")
                        return@launch
                    }
                    AppLogger.d(TAG, "新对话创建完成，ID: $chatId")
                }
                
                // 设置消息文本
                chatCore.updateUserMessage(message)
                
                // 发送消息（包含总结逻辑）
                chatCore.sendUserMessage(promptType)
                
                AppLogger.d(TAG, "消息已通过 chatCore 发送")
            } catch (e: Exception) {
                AppLogger.e(TAG, "发送消息时出错", e)
            }
        }
    }

    override fun onCancelMessage() {
        AppLogger.d(TAG, "onCancelMessage")
        
        // 直接使用 chatCore 取消消息，不再通过 SharedFlow
        chatCore.cancelCurrentMessage()
    }

    override fun onAttachmentRequest(request: String) {
        handleAttachmentRequest(request)
    }

    override fun onRemoveAttachment(filePath: String) {
        removeAttachment(filePath)
    }

    override fun getMessages(): List<ChatMessage> = chatMessages.value

    override fun getAttachments(): List<AttachmentInfo> = attachments.value

    override fun getInputProcessingState(): State<InputProcessingState> = inputProcessingState

    override fun getColorScheme(): ColorScheme? = colorScheme.value

    override fun getTypography(): Typography? = typography.value

    override fun saveState() {
        windowState.saveState()
    }

    override fun getStatusIndicatorStyle(): StatusIndicatorStyle {
        val defaultStyleName = StatusIndicatorStyle.FULLSCREEN_RAINBOW.name
        val stored = prefs.getString(PREF_KEY_STATUS_INDICATOR_STYLE, defaultStyleName)
        return try {
            StatusIndicatorStyle.valueOf(stored ?: defaultStyleName)
        } catch (e: IllegalArgumentException) {
            AppLogger.e(TAG, "Invalid status indicator style in prefs: $stored, fallback to default", e)
            StatusIndicatorStyle.FULLSCREEN_RAINBOW
        }
    }

    fun setStatusIndicatorStyle(style: StatusIndicatorStyle) {
        prefs.edit().putString(PREF_KEY_STATUS_INDICATOR_STYLE, style.name).apply()
        AppLogger.d(TAG, "Status indicator style set to: $style")
    }

    /**
     * 获取悬浮窗的ComposeView实例，用于申请输入法焦点
     * @return ComposeView? 当前悬浮窗的ComposeView实例，如果未初始化则返回null
     */
    fun getComposeView(): View? {
        return if (::windowManager.isInitialized) {
            windowManager.getComposeView()
        } else {
            null
        }
    }

    fun switchToMode(mode: FloatingMode) {
        windowState.currentMode.value = mode
        AppLogger.d(TAG, "Switching to mode: $mode")
    }

    suspend fun setFloatingWindowVisible(visible: Boolean) {
        if (::windowManager.isInitialized) {
            withContext(Dispatchers.Main) {
                windowManager.setFloatingWindowVisible(visible)
                AppLogger.d(TAG, "Floating window visible set to: $visible")
            }
        } else {
            AppLogger.w(TAG, "WindowManager not initialized, cannot set floating window visibility.")
        }
    }

    suspend fun setFloatingWindowPersistentHidden(hidden: Boolean) {
        if (::windowManager.isInitialized) {
            withContext(Dispatchers.Main) {
                windowManager.setFloatingWindowPersistentHidden(hidden)
                AppLogger.d(TAG, "Floating window persistent hidden set to: $hidden")
            }
        } else {
            AppLogger.w(TAG, "WindowManager not initialized, cannot set floating window persistent hidden.")
        }
    }

    suspend fun setStatusIndicatorVisible(visible: Boolean) {
        if (::windowManager.isInitialized) {
            withContext(Dispatchers.Main) {
                windowManager.setStatusIndicatorVisible(visible)
                AppLogger.d(TAG, "Status indicator visible set to: $visible")
            }
        } else {
            AppLogger.w(TAG, "WindowManager not initialized, cannot set status indicator visibility.")
        }
    }

    suspend fun setStatusIndicatorPersistentVisible(visible: Boolean) {
        if (::windowManager.isInitialized) {
            withContext(Dispatchers.Main) {
                windowManager.setStatusIndicatorPersistentVisible(visible)
                AppLogger.d(TAG, "Status indicator persistent visible set to: $visible")
            }
        } else {
            AppLogger.w(TAG, "WindowManager not initialized, cannot set persistent status indicator visibility.")
        }
    }

    /**
     * 获取 ChatServiceCore 实例
     * @return ChatServiceCore 聊天服务核心实例
     */
    fun getChatCore(): ChatServiceCore = chatCore

    /**
     * 重新加载聊天消息（从数据库加载并智能合并）
     * 用于在流完成时同步消息，保持已存在消息的实例不变
     */
    fun reloadChatMessages() {
        serviceScope.launch {
            try {
                val chatId = chatCore.currentChatId.value
                if (chatId != null) {
                    AppLogger.d(TAG, "重新加载聊天消息，chatId: $chatId")
                    chatCore.reloadChatMessagesSmart(chatId)
                } else {
                    AppLogger.w(TAG, "当前没有活跃对话，无法重新加载消息")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "重新加载聊天消息失败", e)
            }
        }
    }
}
