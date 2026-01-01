package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import android.content.Context
import androidx.compose.runtime.*
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.fullscreen.XmlTextProcessor
import com.ai.assistance.operit.ui.floating.voice.SpeechInteractionManager
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val TAG = "FloatingFullscreenViewModel"

class FloatingFullscreenModeViewModel(
    private val context: Context,
    private val floatContext: FloatContext,
    private val coroutineScope: CoroutineScope
) {
    // ===== 状态定义 =====
    var aiMessage by mutableStateOf("长按下方麦克风开始说话")
    
    // UI状态
    var isWaveActive by mutableStateOf(false)
    var showBottomControls by mutableStateOf(true)
    var isEditMode by mutableStateOf(false)
    var editableText by mutableStateOf("")
    var inputText by mutableStateOf("")
    var showDragHints by mutableStateOf(false)

    var attachScreenContent by mutableStateOf(false)
    var attachNotifications by mutableStateOf(false)
    var hasOcrSelection by mutableStateOf(false)
    
    val isInitialLoad = mutableStateOf(true)

     private var aiStreamJob: Job? = null
     private var activeAiStreamIdentity: Int? = null
     private var activeAiMessageTimestamp: Long? = null
    
    // ===== 语音交互管理器 =====
    val speechManager = SpeechInteractionManager(
        context = context,
        coroutineScope = coroutineScope,
        onSpeechResult = { text, _ -> 
            // 收到最终语音结果后直接发送，不再写入底部输入框
            val finalText = text.trim()
            if (finalText.isNotEmpty()) {
                floatContext.onSendMessage?.invoke(finalText, PromptFunctionType.VOICE)
                aiMessage = "思考中..."
            }
        },
        onStateChange = { msg -> aiMessage = msg }
    )
    
    // 代理属性，方便 UI 访问
    val isRecording: Boolean get() = speechManager.isRecording
    val isProcessingSpeech: Boolean get() = speechManager.isProcessingSpeech
    val userMessage: String get() = speechManager.userMessage
    val hasFocus: Boolean get() = speechManager.hasFocus
    // UI 用到 volumeFlow 和 recognitionResultFlow
    val speechService get() = speechManager.speechService
    val volumeLevelFlow get() = speechManager.volumeLevelFlow
    val recognitionResultFlow get() = speechManager.recognitionResultFlow

    // ===== 业务逻辑 =====

    fun processAndSpeakAiMessage(lastMessage: ChatMessage?, ttsCleanerRegexs: List<String>) {
        val message = lastMessage ?: return

         // If we are switching to a new message, stop any previous stream collector.
         // This avoids duplicate collectors (and duplicated replay) when the upstream SharedStream replays history.
         if (activeAiMessageTimestamp != null && activeAiMessageTimestamp != message.timestamp) {
             aiStreamJob?.cancel()
             aiStreamJob = null
             activeAiStreamIdentity = null
         }
         activeAiMessageTimestamp = message.timestamp
        
        if (isInitialLoad.value) {
            isInitialLoad.value = false
            if (message.sender == "ai") aiMessage = message.content
            return
        }
        
        coroutineScope.launch { speechManager.voiceService.stop() }
        
        when (message.sender) {
            "think" -> {
                aiStreamJob?.cancel()
                aiStreamJob = null
                activeAiStreamIdentity = null
                aiMessage = "思考中..."
            }
            "ai" -> {
                val stream = message.contentStream
                if (stream != null) {
                    val streamIdentity = System.identityHashCode(stream)
                    if (aiStreamJob?.isActive == true && activeAiStreamIdentity == streamIdentity) {
                        return
                    }
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = streamIdentity

                    // 不要立即清空，等待流内容到达
                    aiStreamJob = coroutineScope.launch {
                        handleStreamResponse(stream, ttsCleanerRegexs)
                    }
                } else {
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = null
                    handleStaticResponse(message.content, ttsCleanerRegexs)
                }
            }
        }
    }

    private suspend fun handleStreamResponse(stream: Stream<String>, cleaners: List<String>) {
        val sb = StringBuilder()
        var isFirstSentence = true
        var isFirstChar = true
        val endChars = ".,!?;:，。！？；：\n"
        
        XmlTextProcessor.processStreamToText(stream).collect { char ->
            if (isFirstChar) {
                aiMessage = "" // 收到第一个字符时才清空等待提示
                isFirstChar = false
            }
            aiMessage += char
            sb.append(char)
            
            if (char in endChars || sb.length >= 50) {
                if (trySpeak(sb.toString(), isFirstSentence, cleaners)) {
                    isFirstSentence = false
                    sb.clear()
                }
            }
        }
        trySpeak(sb.toString(), isFirstSentence, cleaners)
    }

    private fun handleStaticResponse(content: String, cleaners: List<String>) {
        aiMessage = content
        trySpeak(content, false, cleaners)
    }

    private fun trySpeak(text: String, interrupt: Boolean, cleaners: List<String>): Boolean {
        val cleanText = speechManager.cleanTextForTts(text.trim(), cleaners)
        if (cleanText.isNotEmpty()) {
            speechManager.speak(cleanText, interrupt)
            return true
        }
        return false
    }

    // ===== 语音交互 =====

    fun startVoiceCapture() {
        // 如果AI正在生成，尝试取消
        val lastMessage = floatContext.messages.lastOrNull()
        val isAiWorking = lastMessage?.sender == "think" || 
                          (lastMessage?.sender == "ai" && lastMessage.contentStream != null)
        
        if (isAiWorking) {
            floatContext.onCancelMessage?.invoke()
        }
        
        speechManager.startListening { errorMsg ->
            aiMessage = errorMsg
        }
    }

    fun stopVoiceCapture(isCancel: Boolean) {
        speechManager.stopListening(isCancel)
    }

    fun enterWaveMode() {
        startVoiceCapture()
        if (speechManager.isRecording) {
            isWaveActive = true
        }
    }
    
    fun exitWaveMode() {
        stopVoiceCapture(true)
        isWaveActive = false
        showBottomControls = true
    }

    fun handleRecognitionResult(resultText: String, isFinal: Boolean) {
        // 委托给 Manager 处理，波浪模式下启用自动静默发送
        speechManager.handleRecognitionResult(resultText, isFinal, autoSendSilence = isWaveActive)
    }

    // ===== 初始化与清理 =====

    suspend fun initialize() {
        speechManager.initialize()
        isInitialLoad.value = true
        isWaveActive = false
        showBottomControls = true
        exitEditMode()

        // 获取焦点
        val view = floatContext.chatService?.getComposeView()
        if (!speechManager.requestFocus(view)) {
            aiMessage = "无法获取输入法服务"
        } else {
            aiMessage = "长按下方麦克风开始说话"
        }
    }

    fun cleanup() {
        val view = floatContext.chatService?.getComposeView()
        speechManager.releaseFocus(view)
        speechManager.cleanup()

         aiStreamJob?.cancel()
         aiStreamJob = null
         activeAiStreamIdentity = null
    }

    // ===== 编辑模式 =====

    fun enterEditMode(text: String) {
        coroutineScope.launch { speechManager.stopListening(isCancel = true) }
        editableText = text
        isEditMode = true
        aiMessage = "编辑您的消息"
    }
    
    fun exitEditMode() {
        isEditMode = false
        editableText = ""
        aiMessage = "长按下方麦克风开始说话"
    }
    
    fun sendEditedMessage() {
        if (editableText.isNotBlank()) {
            floatContext.onSendMessage?.invoke(editableText, PromptFunctionType.VOICE)
            isEditMode = false
            editableText = ""
            aiMessage = "思考中..."
        }
    }
    
    fun sendInputMessage() {
        val text = inputText.trim()
        if (text.isEmpty() && !attachScreenContent && !attachNotifications && !hasOcrSelection) return

        // 立即清理UI状态，不等待协程
        val shouldCaptureScreen = attachScreenContent
        val shouldCaptureNotifications = attachNotifications
        
        inputText = ""
        attachScreenContent = false
        attachNotifications = false
        hasOcrSelection = false
        aiMessage = "思考中..."

        coroutineScope.launch {
            try {
                val attachmentDelegate = floatContext.chatService?.getChatCore()?.getAttachmentDelegate()
                if (shouldCaptureScreen) {
                    attachmentDelegate?.captureScreenContent()
                }
                if (shouldCaptureNotifications) {
                    attachmentDelegate?.captureNotifications()
                }
                // hasOcrSelection 的附件已经在 FloatingScreenOcrScreen 中添加了
            } catch (_: Exception) {
            }

            floatContext.onSendMessage?.invoke(text, PromptFunctionType.VOICE)
        }
    }
}

@Composable
fun rememberFloatingFullscreenModeViewModel(
    context: Context,
    floatContext: FloatContext,
    coroutineScope: CoroutineScope
) = remember(context) {
    FloatingFullscreenModeViewModel(context, floatContext, coroutineScope)
}
