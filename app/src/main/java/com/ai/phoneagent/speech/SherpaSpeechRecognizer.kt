package com.ai.phoneagent.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.ncnn.DecoderConfig
import com.k2fsa.sherpa.ncnn.FeatureExtractorConfig
import com.k2fsa.sherpa.ncnn.ModelConfig
import com.k2fsa.sherpa.ncnn.RecognizerConfig
import com.k2fsa.sherpa.ncnn.SherpaNcnn
import com.k2fsa.sherpa.ncnn.getDecoderConfig
import com.k2fsa.sherpa.ncnn.getFeatureExtractorConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于sherpa-ncnn的本地语音识别实现
 * sherpa-ncnn是一个轻量级、高性能的语音识别引擎，比Vosk更适合移动端
 * 参考: https://github.com/k2-fsa/sherpa-ncnn
 */
@SuppressLint("MissingPermission")
class SherpaSpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSpeechRecognizer"
        private const val SAMPLE_RATE = 16000
    }

    /** 识别结果回调 */
    interface RecognitionListener {
        /** 部分识别结果（实时中间结果） */
        fun onPartialResult(text: String)

        /** 最终识别结果 */
        fun onResult(text: String)

        /** 最终结果（识别结束） */
        fun onFinalResult(text: String)

        /** 识别出错 */
        fun onError(exception: Exception)

        /** 识别超时 */
        fun onTimeout()
    }

    private var recognizer: SherpaNcnn? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + scopeJob)

    private var listener: RecognitionListener? = null
    private var isInitialized = false
    private var isListening = false
    private val finalResultEmitted = AtomicBoolean(false)

    private fun releaseAudioRecord() {
        val ar = audioRecord ?: return
        audioRecord = null
        runCatching {
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                ar.stop()
            }
        }
        runCatching { ar.release() }
    }

    /**
     * 初始化语音识别引擎
     * @return true表示初始化成功
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true

        Log.d(TAG, "Initializing sherpa-ncnn...")
        return try {
            withContext(Dispatchers.IO) {
                val created = createRecognizer()
                if (!created || recognizer == null) {
                    Log.e(TAG, "Failed to create sherpa-ncnn recognizer")
                    return@withContext false
                }

                Log.d(TAG, "sherpa-ncnn initialized successfully")
                isInitialized = true
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sherpa-ncnn", e)
            false
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 检查是否正在录音识别
     */
    fun isListening(): Boolean = isListening

    @Throws(IOException::class)
    private fun copyAssetDirToCache(assetDir: String, cacheDir: File): File {
        val targetDir = File(cacheDir, assetDir.substringAfterLast('/'))
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model files already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }
        Log.d(TAG, "Copying model files from assets '$assetDir' to ${targetDir.absolutePath}")
        targetDir.mkdirs()

        val assetManager = context.assets
        val fileList = assetManager.list(assetDir)
        if (fileList.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        fileList.forEach { fileName ->
            val assetPath = "$assetDir/$fileName"
            val targetFile = File(targetDir, fileName)
            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return targetDir
    }

    private fun createRecognizer(): Boolean {
        val modelDirName = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"
        val assetModelDir = "sherpa-models/$modelDirName"

        val requiredFiles = listOf(
            "encoder_jit_trace-pnnx.ncnn.param",
            "encoder_jit_trace-pnnx.ncnn.bin",
            "decoder_jit_trace-pnnx.ncnn.param",
            "decoder_jit_trace-pnnx.ncnn.bin",
            "joiner_jit_trace-pnnx.ncnn.param",
            "joiner_jit_trace-pnnx.ncnn.bin",
            "tokens.txt",
        )

        val localModelDir = ensureModelDirReady(
            assetDir = assetModelDir,
            targetRootDir = context.filesDir,
            requiredFiles = requiredFiles
        ) ?: return false

        val featConfig = getFeatureExtractorConfig(sampleRate = SAMPLE_RATE.toFloat(), featureDim = 80)

        val modelConfig = ModelConfig(
            encoderParam = File(localModelDir, "encoder_jit_trace-pnnx.ncnn.param").absolutePath,
            encoderBin = File(localModelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
            decoderParam = File(localModelDir, "decoder_jit_trace-pnnx.ncnn.param").absolutePath,
            decoderBin = File(localModelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
            joinerParam = File(localModelDir, "joiner_jit_trace-pnnx.ncnn.param").absolutePath,
            joinerBin = File(localModelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
            tokens = File(localModelDir, "tokens.txt").absolutePath,
            numThreads = 2,
            useGPU = false
        )

        val decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4)

        val recognizerConfig = RecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decoderConfig = decoderConfig,
            enableEndpoint = true,
            rule1MinTrailingSilence = 2.4f,
            rule2MinTrailingSilence = 1.2f,
            rule3MinUtteranceLength = 20.0f,
            hotwordsFile = "",
            hotwordsScore = 1.5f
        )

        return try {
            recognizer = SherpaNcnn(
                config = recognizerConfig,
                assetManager = null // Force using newFromFile
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SherpaNcnn instance", e)
            recognizer = null
            false
        }
    }

    private fun ensureModelDirReady(
        assetDir: String,
        targetRootDir: File,
        requiredFiles: List<String>,
    ): File? {
        val dirName = assetDir.substringAfterLast('/')
        val targetDir = File(targetRootDir, dirName)

        fun isComplete(): Boolean {
            if (!targetDir.exists()) return false
            return requiredFiles.all { name ->
                val f = File(targetDir, name)
                f.exists() && f.isFile && f.length() > 0
            }
        }

        // 如果缓存目录存在但不完整（常见于首次运行中断/拷贝失败），需要删除并重新拷贝
        if (!isComplete()) {
            if (targetDir.exists()) {
                Log.w(TAG, "Model cache is incomplete. Re-copying model files: ${targetDir.absolutePath}")
                runCatching { targetDir.deleteRecursively() }
            }
            return try {
                copyAssetDirToCache(assetDir, targetRootDir)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy model assets.", e)
                null
            }
        }

        return targetDir
    }

    /**
     * 开始语音识别
     * @param listener 识别结果回调
     */
    fun startListening(listener: RecognitionListener) {
        if (!isInitialized) {
            listener.onError(IllegalStateException("Speech recognizer not initialized"))
            return
        }
        if (isListening) {
            return
        }

        this.listener = listener
        finalResultEmitted.set(false)
        recognizer?.reset(false)

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            listener.onError(IllegalStateException("AudioRecord.getMinBufferSize failed: $minBufferSize"))
            return
        }

        val ar =
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    channelConfig,
                    audioFormat,
                    minBufferSize * 2
                )
            } catch (e: Exception) {
                listener.onError(e)
                return
            }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { ar.release() }
            listener.onError(IllegalStateException("AudioRecord init failed, state=${ar.state}"))
            return
        }

        audioRecord = ar

        val started = runCatching {
            ar.startRecording()
            true
        }.getOrElse {
            listener.onError(it as? Exception ?: RuntimeException(it))
            false
        }

        if (!started) {
            releaseAudioRecord()
            return
        }

        isListening = true
        Log.d(TAG, "Started recording")

        recordingJob = scope.launch {
            try {
                val bufferSize = minBufferSize
                val audioBuffer = ShortArray(bufferSize)
                var lastText = ""

                while (isActive && isListening) {
                    val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (ret < 0) {
                        withContext(Dispatchers.Main) {
                            listener.onError(IOException("AudioRecord.read failed: $ret"))
                        }
                        isListening = false
                        break
                    }
                    if (ret > 0) {
                        val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
                        val currentRecognizer = recognizer ?: break

                        currentRecognizer.acceptSamples(samples)
                        while (currentRecognizer.isReady()) {
                            currentRecognizer.decode()
                        }

                        val isEndpoint = currentRecognizer.isEndpoint()
                        val text = currentRecognizer.text

                        if (text.isNotBlank() && lastText != text) {
                            lastText = text
                            withContext(Dispatchers.Main) {
                                if (isEndpoint) {
                                    listener.onResult(text)
                                } else {
                                    listener.onPartialResult(text)
                                }
                            }
                        }

                        if (isEndpoint) {
                            currentRecognizer.reset(false)
                            isListening = false
                            if (finalResultEmitted.compareAndSet(false, true)) {
                                withContext(Dispatchers.Main) {
                                    listener.onFinalResult(lastText)
                                }
                            }
                            break
                        }
                    }
                }

                Log.d(TAG, "Recording loop ended.")
            } finally {
                releaseAudioRecord()
            }
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) return

        Log.d(TAG, "Stopping recognition...")
        isListening = false
        recordingJob?.cancel()

        // Finalize recognition
        recognizer?.inputFinished()
        val text = recognizer?.text ?: ""
        if (finalResultEmitted.compareAndSet(false, true)) {
            listener?.onFinalResult(text)
        }

        releaseAudioRecord()
    }

    /**
     * 取消语音识别（不返回结果）
     */
    fun cancel() {
        isListening = false
        recordingJob?.cancel()
        finalResultEmitted.set(true)
        releaseAudioRecord()
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        cancel()
        scope.cancel()
        recognizer = null
        isInitialized = false
    }
}
