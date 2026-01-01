package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 硅基流动TTS语音服务实现
 */
class SiliconFlowVoiceProvider(
    private val context: Context,
    private val apiKey: String,
    initialVoiceId: String,
    private val initialModelName: String = ""
) : VoiceService {
    companion object {
        private const val TAG = "SiliconFlowVoiceProvider"
        private const val API_URL = "https://api.siliconflow.cn/v1/audio/speech"
        private const val RESPONSE_FORMAT = "mp3"
        private const val SAMPLE_RATE = 32000
        private const val SPEED = 1.0
        private const val GAIN = 0

        // 可用音色列表 - 根据硅基流动官方文档
        // 注意：现在只列出音色名称，模型在设置中独立配置
        val AVAILABLE_VOICES = listOf(
            VoiceService.Voice("alex", "Alex - 沉稳男声", "zh-CN", "MALE"),
            VoiceService.Voice("benjamin", "Benjamin - 低沉男声", "zh-CN", "MALE"),
            VoiceService.Voice("charles", "Charles - 磁性男声", "zh-CN", "MALE"),
            VoiceService.Voice("david", "David - 欢快男声", "zh-CN", "MALE"),
            VoiceService.Voice("anna", "Anna - 沉稳女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("bella", "Bella - 激情女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("claire", "Claire - 温柔女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("diana", "Diana - 欢快女声", "zh-CN", "FEMALE")
        )
        val DEFAULT_VOICE_ID = "charles"
    }

    // 当前音色
    private var voiceId: String = initialVoiceId.ifBlank { DEFAULT_VOICE_ID }


    // MediaPlayer用于播放音频
    private var mediaPlayer: MediaPlayer? = null

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    // 播放状态Flow
    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                throw TtsException("API密钥未设置，请在设置中填写。")
            }
            if (voiceId.isBlank()) {
                throw TtsException("音色ID未设置，请选择一个有效音色。")
            }
            
            _isInitialized.value = true
            AppLogger.i(TAG, "硅基流动TTS初始化成功")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "硅基流动TTS初始化失败", e)
            _isInitialized.value = false
            if (e is TtsException) throw e
            throw TtsException("初始化硅基流动TTS服务时发生意外错误", cause = e)
        }
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float,
        pitch: Float,
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            AppLogger.e(TAG, "TTS未初始化")
            return@withContext false
        }
        
        try {
            if (interrupt && isSpeaking) {
                stop()
            }

            _isSpeaking.value = true

            // 从 extraParams 获取自定义的 model 和 voice，如果没有则使用配置或默认值
            val customModel = extraParams["model"]
            val customVoice = extraParams["voice"]
            
            val (model, voice) = when {
                // 优先使用 extraParams 中的自定义值
                customModel != null && customVoice != null -> {
                    customModel to customVoice
                }
                customModel != null -> {
                    // 只指定了model，使用voiceId作为voice
                    customModel to voiceId
                }
                customVoice != null -> {
                    // 只指定了voice，使用配置的model或默认model
                    val modelName = initialModelName.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                    modelName to customVoice
                }
                else -> {
                    // 使用配置的model（或默认）和voiceId
                    val modelName = initialModelName.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                    modelName to voiceId
                }
            }

            // 清理 voice 字符串，去除可能的空白字符
            val cleanVoice = voice.trim()
            
            // 构建请求体
            val requestBody = buildString {
                append("{")
                append("\"model\":\"$model\",")
                append("\"input\":\"${text.replace("\"", "\\\"")}\",")
                
                // 根据官方文档：
                // 1. 系统预定义音色（如 alex, bella）需要格式为 "model:voice"
                // 2. 用户自定义音色（以 speech: 开头）直接使用完整的 voice ID
                // 两种情况都使用 voice 字段
                val voiceValue = if (cleanVoice.startsWith("speech:")) {
                    // 自定义音色，直接使用完整 ID
                    cleanVoice
                } else {
                    // 预置音色，需要加上模型前缀
                    if (cleanVoice.contains(":")) {
                        // 如果已经包含冒号，说明已经是完整格式
                        cleanVoice
                    } else {
                        // 否则添加模型前缀
                        "$model:$cleanVoice"
                    }
                }
                append("\"voice\":\"$voiceValue\",")
                
                append("\"response_format\":\"$RESPONSE_FORMAT\",")
                append("\"sample_rate\":$SAMPLE_RATE,")
                append("\"speed\":$SPEED,")
                append("\"gain\":$GAIN")
                append("}")
            }

            AppLogger.d(TAG, "TTS请求参数 - model: $model, voice: $voice")
            AppLogger.d(TAG, "TTS请求体: $requestBody")

            // 发送HTTP请求
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // 写入请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 将音频数据保存到临时文件
                val tempFile = File.createTempFile("siliconflow_tts", ".mp3", context.cacheDir)
                
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 播放音频文件
                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile)
                }
                
                return@withContext true
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()
                AppLogger.e(TAG, "TTS请求失败，响应码: $responseCode, Body: $errorBody")
                _isSpeaking.value = false
                throw TtsException(
                    message = "TTS request failed with code $responseCode",
                    httpStatusCode = responseCode,
                    errorBody = errorBody
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "TTS speak失败", e)
            _isSpeaking.value = false
            if (e is TtsException) throw e
            throw TtsException("TTS speak failed", cause = e)
        }
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    file.delete() // 清理临时文件
                }
                setOnErrorListener { _, what, extra ->
                    AppLogger.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    file.delete() // 清理临时文件
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "播放音频失败", e)
            _isSpeaking.value = false
            file.delete() // 清理临时文件
        }
    }

    override suspend fun stop(): Boolean {
        return try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            _isSpeaking.value = false
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止播放失败", e)
            false
        }
    }

    override suspend fun pause(): Boolean {
        return try {
            mediaPlayer?.pause()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "暂停播放失败", e)
            false
        }
    }

    override suspend fun resume(): Boolean {
        return try {
            mediaPlayer?.start()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "恢复播放失败", e)
            false
        }
    }

    override fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isSpeaking.value = false
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return AVAILABLE_VOICES
    }

    override suspend fun setVoice(voiceId: String): Boolean {
        // 支持系统预置音色和用户自定义音色（以speech:开头）
        if (AVAILABLE_VOICES.any { it.id == voiceId } || voiceId.startsWith("speech:")) {
            this.voiceId = voiceId
            AppLogger.d(TAG, "设置音色: $voiceId")
            return true
        }
        AppLogger.w(TAG, "不支持的音色ID: $voiceId")
        return false
    }
} 