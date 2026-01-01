package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.MediaPlayer
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import android.media.AudioAttributes
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.net.URLEncoder

/**
 * 基于HTTP请求的TTS语音服务实现
 *
 * 此实现通过HTTP请求获取TTS音频数据，支持配置不同的TTS服务端点
 */
class HttpVoiceProvider(
    private val context: Context
) : VoiceService {

    private var httpConfig: SpeechServicesPreferences.TtsHttpConfig = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET

    companion object {
        private const val TAG = "HttpVoiceProvider"
        private const val DEFAULT_TIMEOUT = 10 // 10秒超时
    }

    // OkHttpClient实例
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .build()
    }

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

    // 媒体播放器实例
    private var mediaPlayer: MediaPlayer? = null
    
    // 缓存的音频文件映射表
    private val audioCache = ConcurrentHashMap<String, File>()
    
    // 当前语音参数
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceId: String? = null
    
    // 临时文件目录
    private val cacheDir by lazy { context.cacheDir }

    /**
     * 设置HTTP TTS服务的配置
     * @param config TTS HTTP配置
     */
    fun setConfiguration(config: SpeechServicesPreferences.TtsHttpConfig) {
        this.httpConfig = config
        this._isInitialized.value = false // 强制重新初始化
    }

    /** 初始化TTS引擎 */
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (_isInitialized.value) {
            return@withContext true
        }
        try {
            if (httpConfig.urlTemplate.isBlank()) {
                throw TtsException("HTTP TTS 服务配置错误: URL 模板不能为空。")
            }

            val isPost = httpConfig.httpMethod.uppercase() == "POST"
            val hasTextPlaceholder = if (isPost) {
                httpConfig.requestBody.contains("{text}", ignoreCase = true)
            } else {
                httpConfig.urlTemplate.contains("{text}", ignoreCase = true)
            }

            if (!hasTextPlaceholder) {
                val errorMessage = if (isPost) {
                    "HTTP TTS 服务配置错误: POST 请求的正文模板必须包含 {text} 占位符。"
                } else {
                    "HTTP TTS 服务配置错误: GET 请求的 URL 模板必须包含 {text} 占位符。"
                }
                throw TtsException(errorMessage)
            }

            if (!httpConfig.urlTemplate.startsWith("http://") && !httpConfig.urlTemplate.startsWith("https://")) {
                throw TtsException("HTTP TTS 服务配置错误: URL 模板必须以 http:// 或 https:// 开头。")
            }

            _isInitialized.value = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize HTTP TTS provider due to invalid configuration", e)
            _isInitialized.value = false
            // 重新抛出异常，以便UI层可以捕获并显示
            if (e is TtsException) throw e
            else throw TtsException("初始化HTTP TTS服务时发生意外错误", cause = e)
        }
        
        return@withContext _isInitialized.value
    }

    /**
     * 将文本转换为语音并播放
     *
     * @param text 要转换为语音的文本
     * @param interrupt 是否中断当前正在播放的语音
     * @param rate 语速
     * @param pitch 音调
     * @param extraParams 额外的请求参数
     * @return 操作是否成功
     */
    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float,
        pitch: Float,
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        // 检查初始化状态
        if (!isInitialized) {
            val initResult = initialize()
            if (!initResult) {
                return@withContext false
            }
        }
        
        // 如果需要中断当前播放，则停止
        if (interrupt && isSpeaking) {
            stop()
        }
        
        try {
            // 生成缓存键
            val cacheKey = generateCacheKey(text, rate, pitch, currentVoiceId, extraParams)
            var audioFile = audioCache[cacheKey]
            
            // 如果缓存中没有，则请求新的音频
            if (audioFile == null || !audioFile.exists()) {
                audioFile = fetchAudioFromServer(text, rate, pitch, currentVoiceId, extraParams)
                if (audioFile != null) {
                    audioCache[cacheKey] = audioFile
                } else {
                    return@withContext false
                }
            }
            
            // 播放音频文件
            playAudioFile(audioFile)
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "HTTP TTS播放失败", e)
            throw e
        }
    }

    /** 停止当前正在播放的语音 */
    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                _isSpeaking.value = false
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止HTTP TTS播放失败", e)
            return@withContext false
        }
    }

    /** 暂停当前正在播放的语音 */
    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isSpeaking.value = false
                    return@withContext true
                }
            }
            return@withContext false
        } catch (e: Exception) {
            AppLogger.e(TAG, "暂停HTTP TTS播放失败", e)
            return@withContext false
        }
    }

    /** 继续播放暂停的语音 */
    override suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _isSpeaking.value = true
                    return@withContext true
                }
            }
            return@withContext false
        } catch (e: Exception) {
            AppLogger.e(TAG, "恢复HTTP TTS播放失败", e)
            return@withContext false
        }
    }

    /** 释放TTS引擎资源 */
    override fun shutdown() {
        try {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "释放MediaPlayer失败", e)
                } finally {
                    mediaPlayer = null
                    _isInitialized.value = false
                    _isSpeaking.value = false
                }
            }
            
            // 清除缓存文件
            clearCache()
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭HTTP TTS引擎失败", e)
        }
    }

    /** 获取可用的语音列表 */
    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        AppLogger.w(TAG, "Listing available voices via HTTP is not supported by this generic provider. Returning an empty list. Set voice directly using setVoice().")
        return emptyList()
    }

    /** 设置当前使用的语音 */
    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        currentVoiceId = voiceId
        return@withContext true
    }
    
    /**
     * 从服务器获取音频数据
     *
     * @param text 要转换的文本
     * @param rate 语速
     * @param pitch 音调
     * @param voiceId 语音ID
     * @return 音频文件，如果失败则返回null
     */
    private suspend fun fetchAudioFromServer(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        extraParams: Map<String, String>
    ): File? = withContext(Dispatchers.IO) {
        if (httpConfig.urlTemplate.isBlank()) {
            AppLogger.e(TAG, "HTTP TTS URL template is not configured.")
            return@withContext null
        }

        try {
            // URL-encode parameters before replacing
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val encodedRate = rate.toString()
            val encodedPitch = pitch.toString()
            val encodedVoiceId = voiceId?.let { URLEncoder.encode(it, "UTF-8") } ?: ""

            val requestBuilder = Request.Builder()

            if (httpConfig.httpMethod.uppercase() == "POST") {
                // For POST requests, use the URL as base and put parameters in body
                val baseUrl = httpConfig.urlTemplate
                val httpUrl = baseUrl.toHttpUrlOrNull()
                if (httpUrl == null) {
                    AppLogger.e(TAG, "Base URL is invalid: $baseUrl")
                    return@withContext null
                }
                
                // Replace placeholders in the request body template
                var requestBody = httpConfig.requestBody
                    .replace("{text}", text, ignoreCase = true) // Don't encode for JSON body
                    .replace("{rate}", encodedRate, ignoreCase = true)
                    .replace("{pitch}", encodedPitch, ignoreCase = true)

                if (voiceId != null) {
                    requestBody = requestBody.replace("{voice}", voiceId, ignoreCase = true)
                }

                // Replace any extra parameters
                extraParams.forEach { (key, value) ->
                    requestBody = requestBody.replace("{$key}", value, ignoreCase = true)
                }

                val mediaType = httpConfig.contentType.toMediaType()
                val body = requestBody.toRequestBody(mediaType)
                
                requestBuilder
                    .url(httpUrl)
                    .post(body)
                    .addHeader("Content-Type", httpConfig.contentType)
            } else {
                // For GET requests, use the existing logic with URL parameters
            var finalUrl = httpConfig.urlTemplate
                .replace("{text}", encodedText, ignoreCase = true)
                .replace("{rate}", encodedRate, ignoreCase = true)
                .replace("{pitch}", encodedPitch, ignoreCase = true)

            if (voiceId != null) {
                finalUrl = finalUrl.replace("{voice}", encodedVoiceId, ignoreCase = true)
            }

            // Replace any extra parameters
            extraParams.forEach { (key, value) ->
                finalUrl = finalUrl.replace("{$key}", URLEncoder.encode(value, "UTF-8"), ignoreCase = true)
            }

            val httpUrl = finalUrl.toHttpUrlOrNull()
            if (httpUrl == null) {
                AppLogger.e(TAG, "Constructed URL is invalid: $finalUrl")
                return@withContext null
            }
            
                requestBuilder
                .url(httpUrl)
                .get()
            }
            
            // Add API key if present
            if (httpConfig.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${httpConfig.apiKey}")
            }

            // Add custom headers
            httpConfig.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            val request = requestBuilder.build()
            
            AppLogger.i(TAG, "Executing TTS Request: ${request.method} ${request.url}")
            AppLogger.i(TAG, "Request Headers:\n${request.headers}")

            val response = suspendCoroutine<Response> { continuation ->
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                })
            }
            
            if (response.isSuccessful) {
                // 创建临时文件
                val tempFile = File(cacheDir, "tts_${UUID.randomUUID()}.mp3")
                
                // 写入音频数据
                response.body?.let { responseBody ->
                    FileOutputStream(tempFile).use { output ->
                        responseBody.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    
                    return@withContext tempFile
                }
                // Should not happen if response is successful and body is present
                return@withContext null
            } else {
                val errorBody = response.body?.string()
                AppLogger.e(TAG, "HTTP TTS request failed. Code: ${response.code}, Body: $errorBody")
                response.close()
                throw TtsException(
                    message = "HTTP TTS request failed with code ${response.code}",
                    httpStatusCode = response.code,
                    errorBody = errorBody
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取HTTP TTS音频失败", e)
            if (e is TtsException) throw e
            throw TtsException("获取HTTP TTS音频失败", cause = e)
        }
    }
    
    /**
     * 播放音频文件
     *
     * @param audioFile 要播放的音频文件
     * @return 是否成功开始播放
     */
    private suspend fun playAudioFile(audioFile: File) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            AppLogger.e(TAG, "Audio file is invalid: ${audioFile.absolutePath}")
            return
        }

        var mediaPlayer: MediaPlayer? = null
        try {
            withContext(Dispatchers.IO) {
                FileInputStream(audioFile).use { fis ->
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(fis.fd)
                        prepare() // Synchronous preparation
                        start()
                    }
                }
            }

            // Wait for playback to complete
            mediaPlayer?.let {
                while (it.isPlaying) {
                    delay(100)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "播放HTTP TTS音频失败", e)
        } finally {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        }
    }
    
    /**
     * 生成缓存键
     *
     * @param text 文本内容
     * @param rate 语速
     * @param pitch 音调
     * @param voiceId 语音ID
     * @return 缓存键
     */
    private fun generateCacheKey(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        extraParams: Map<String, String>
    ): String {
        val paramsString = extraParams.entries.sortedBy { it.key }.joinToString()
        return "${text.hashCode()}_${rate}_${pitch}_${voiceId ?: "default"}_$paramsString"
    }
    
    /**
     * 清除缓存文件
     */
    private fun clearCache() {
        try {
            for (file in audioCache.values) {
                if (file.exists()) {
                    file.delete()
                }
            }
            audioCache.clear()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除HTTP TTS缓存失败", e)
        }
    }
} 