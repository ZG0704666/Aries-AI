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
package com.ai.phoneagent.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.ncnn.ModelConfig
import com.k2fsa.sherpa.ncnn.RecognizerConfig
import com.k2fsa.sherpa.ncnn.SherpaNcnn
import com.k2fsa.sherpa.ncnn.getDecoderConfig
import com.k2fsa.sherpa.ncnn.getFeatureExtractorConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 模型缓存完整性清单（R-04）。
 *
 * 暂存目录完整拷贝并校验后，把“文件名 \t 长度 \t SHA-256”清单写入暂存目录并最后提交，
 * 再原子 rename 为正式缓存目录。启动期校验策略：
 * - 清单缺失或格式损坏 → 缓存不可信，整体重拷；
 * - 清单存在 → 校验所有必需文件存在、长度与清单一致（长度不一致即视为拷贝中断/损坏；
 *   完整哈希只在写入清单时计算一次，避免每次启动都对 ~141MiB 模型重新做 IO+CPU 全量哈希）。
 *
 * 解析/序列化是纯函数，独立成对象便于 JVM 单元测试覆盖。
 */
internal object ModelCacheManifest {

    /** 清单文件名；放在模型缓存目录内、最后一个提交。 */
    const val FILE_NAME = ".aries-model-manifest"

    /** 单文件条目：磁盘长度 + 拷贝完成时算出的 SHA-256(hex)。 */
    data class Entry(val length: Long, val sha256: String)

    /** 序列化：每行 "name \t length \t sha256"，行序稳定，便于 diff。 */
    fun format(entries: Map<String, Entry>): String {
        return entries.entries.joinToString("\n") { (name, entry) ->
            "$name\t${entry.length}\t${entry.sha256}"
        }
    }

    /**
     * 解析：任何一行不是 3 段、长度非正、哈希非法（非 64 位 hex）都导致整体返回 null。
     * 返回 null 表示清单不可信，调用方应重拷。
     */
    fun parse(text: String): Map<String, Entry>? {
        if (text.isBlank()) return null
        val entries = LinkedHashMap<String, Entry>()
        text.split('\n').forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split('\t')
            if (parts.size != 3) return null
            val name = parts[0].trim()
            val length = parts[1].trim().toLongOrNull()
            val sha256 = parts[2].trim().lowercase()
            // 文件名白名单式防御：拒绝路径分隔、点段与 URL 编码逃逸（防改名逃逸到缓存目录之外）
            val badName =
                    name.isEmpty() ||
                            name.contains('/') ||
                            name.contains('\\') ||
                            name.contains("..") ||
                            name.startsWith('.') ||
                            name.contains("%2f", ignoreCase = true) ||
                            name.contains("%5c", ignoreCase = true)
            if (badName) return null
            if (length == null || length <= 0L) return null
            if (sha256.length != 64 || sha256.any { it !in "0123456789abcdef" }) return null
            if (entries.containsKey(name)) return null
            entries[name] = Entry(length = length, sha256 = sha256)
        }
        if (entries.isEmpty()) return null
        return entries
    }
}

/**
 * 基于sherpa-ncnn的本地语音识别实现
 *
 * sherpa-ncnn是一个轻量级、高性能的语音识别引擎，比Vosk更适合移动端。
 * 参考: https://github.com/k2-fsa/sherpa-ncnn
 *
 * 并发模型（会话执行器，修复 R-02/R-03/R-05/R-06/R-08）：
 * - 所有识别相关工作（流重建、录音、解码、尾音排空、终态派发、资源释放）一律串行运行在单个
 *   "会话执行器"线程上，录音循环与收尾排空之间不再存在并发交错。
 * - 每个会话独占自己的 AudioRecord 和 listener：旧会话的 finally 只释放自己创建的 AudioRecord，
 *   不会误伤新会话的录音资源（修复跨会话竞态 R-03）。
 * - 会话 generation 贯穿所有回调：旧会话迟到的一切回调（partial/final/error/振幅）都被丢弃，
 *   不会污染新会话（状态不变量 4）。
 * - stopListening() 只发出"停止采集"请求；尾音排空顺序
 *   `inputFinished() → while (isReady()) decode() → 读取最终文本 → 恰好一次 onFinalResult`
 *   由本会话任务串行执行，不在调用线程与运行中的采样循环之间并发进行（R-02）。
 * - cancel() 不产出 final（不变量 3）。
 * - 按住说话是独立会话：会话开始时 reset(true) 重建流，不复用上一会话的 Stream/decoder/encoder
 *   状态（R-05）。
 * - native 识别器通过 [SherpaNcnn.close] 幂等释放，不再只依赖 finalize()（R-06）。
 * - 会话任务整体 try/catch：保证异常时资源释放、状态复位与恰好一次 error 回调（R-08）。
 */
@SuppressLint("MissingPermission")
class SherpaSpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSpeechRecognizer"
        private const val SAMPLE_RATE = 16000
        private val initializationMutex = Mutex()

        /**
         * 按住说话的最大录音时长（秒，R-09）。
         * 达到上限时按“用户松手”同一终态路径收尾：停止采集 → 尾音排空 → 恰好一次 final，
         * 不再无限增长内存，也不给用户悬挂的录音状态。
         */
        private const val MAX_RECORDING_SECONDS = 60

        /** 振幅回调最大派发频率（约 30Hz，R-09/8.3 P2 项）。 */
        private const val AMPLITUDE_DISPATCH_INTERVAL_MS = 33L
    }

    /** 识别结果回调 */
    interface RecognitionListener {
        /** 部分识别结果（实时中间结果） */
        fun onPartialResult(text: String)

        /** 最终识别结果 */
        fun onResult(text: String)

        /** 最终结果（识别结束） */
        fun onFinalResult(text: String)

        /** 音量振幅 (0.0 ~ 1.0) */
        fun onAmplitude(amplitude: Float) {}

        /** 识别出错 */
        fun onError(exception: Exception)

        /** 识别超时 */
        fun onTimeout()
    }

    /** native 识别器：创建发生在初始化线程（IO），访问与会话执行器线程、shutdown 线程之间需可见性保证。 */
    @Volatile
    private var recognizer: SherpaNcnn? = null

    /** 初始化是否完成：初始化线程写，UI/会话线程读。 */
    @Volatile
    private var isInitialized = false

    /** 当前是否处于录音会话占用状态：多个线程读写。 */
    @Volatile
    private var isListening = false

    /**
     * 单线程会话执行器：会话的全部工作串行化在此线程上，消除录音/停止/取消/重录之间的竞态。
     * 线程命名便于在 ANR/trace 中定位。
     */
    private val sessionExecutor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "sherpa-ncnn-session").apply { isDaemon = true }
            }

    /** 主线程回调派发器：所有 listener 回调统一投递到主线程。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 会话代数：startListening 时递增；旧会话的迟到回调按代数整组丢弃。 */
    private val sessionGeneration = AtomicLong(0)

    /** 当前在途会话（尚未 finish）；由会话执行器与公共入口共同维护。 */
    @Volatile
    private var activeSession: Session? = null

    /** 最近一次初始化失败的具体原因（成功后清空），供上层展示可定位的错误提示。 */
    @Volatile
    var lastInitializationError: String? = null
        private set

    /** 初始化是否正在进行（启动期或现场重试），用于上层避免重复排队。 */
    private val initializing = AtomicBoolean(false)

    fun isInitializing(): Boolean = initializing.get()

    /**
     * 初始化语音识别引擎
     * @return true表示初始化成功；false 表示本次未就绪（可能正在初始化或初始化失败，原因见 [lastInitializationError]）
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        // 已有初始化在跑时不重复排队；由正在进行的调用负责上报结果
        if (!initializing.compareAndSet(false, true)) return false
        try {
            return initializationMutex.withLock {
                if (isInitialized) return@withLock true

                Log.d(TAG, "Initializing sherpa-ncnn...")
                try {
                    withContext(Dispatchers.IO) {
                        if (!SherpaNcnn.isNativeLibraryReady()) {
                            val message =
                                    "native 库加载失败：${SherpaNcnn.nativeLibraryErrorMessage() ?: "未知错误"}"
                            Log.e(
                                    TAG,
                                    "Sherpa native library is unavailable: ${SherpaNcnn.nativeLibraryErrorMessage()}"
                            )
                            lastInitializationError = message
                            return@withContext false
                        }

                        val created = createRecognizer()
                        if (!created || recognizer == null) {
                            Log.e(TAG, "Failed to create sherpa-ncnn recognizer")
                            if (lastInitializationError == null) {
                                lastInitializationError = "模型文件缺失或加载失败"
                            }
                            return@withContext false
                        }

                        Log.d(TAG, "sherpa-ncnn initialized successfully")
                        isInitialized = true
                        lastInitializationError = null
                        true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize sherpa-ncnn", e)
                    lastInitializationError = e.message?.let { "初始化异常：$it" } ?: "初始化异常"
                    false
                }
            }
        } finally {
            initializing.set(false)
        }
    }

    /** 检查是否已初始化 */
    fun isReady(): Boolean = isInitialized

    /** 检查是否正在录音识别 */
    fun isListening(): Boolean = isListening

    @Throws(IOException::class)
    private fun copyAssetDirRecursive(
            assetDir: String,
            targetDir: File,
            overwrite: Boolean = false,
    ): File {
        if (overwrite && targetDir.exists()) {
            runCatching { targetDir.deleteRecursively() }
        } else if (!overwrite && targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model files already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Failed to create target directory: ${targetDir.absolutePath}")
        }

        val assetManager = context.assets
        val entries = assetManager.list(assetDir)
        if (entries.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        entries.forEach { entry ->
            val assetPath = "$assetDir/$entry"
            val outputFile = File(targetDir, entry)
            val children = assetManager.list(assetPath)
            if (!children.isNullOrEmpty()) {
                copyAssetDirRecursive(assetPath, outputFile, overwrite = true)
            } else {
                outputFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                }
                // 空目录与文件在 AssetManager.list() 下都表现为“无子项”。
                // 对空目录调用 open() 会抛异常；这里容错跳过，避免中断整个模型拷贝。
                runCatching {
                    assetManager.open(assetPath).use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "Skip non-file asset entry '$assetPath': ${e.message}")
                    runCatching { if (outputFile.exists() && outputFile.length() == 0L) outputFile.delete() }
                }
            }
        }

        return targetDir
    }

    private fun createRecognizer(): Boolean {
        val modelDirName = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"

        // 候选模型目录（按优先级依次尝试）：
        // 1. 文档约定的嵌套布局  sherpa-models/<modelDirName>/
        // 2. legacy 嵌套布局     sherpa-models/models/<modelDirName>/
        // 3. 平铺布局            sherpa-models/  （模型文件直接放在 sherpa-models/ 根目录下）
        val candidateAssetDirs =
                listOf(
                        "sherpa-models/$modelDirName",
                        "sherpa-models/models/$modelDirName",
                        "sherpa-models"
                )

        val requiredFiles =
                listOf(
                        "encoder_jit_trace-pnnx.ncnn.param",
                        "encoder_jit_trace-pnnx.ncnn.bin",
                        "decoder_jit_trace-pnnx.ncnn.param",
                        "decoder_jit_trace-pnnx.ncnn.bin",
                        "joiner_jit_trace-pnnx.ncnn.param",
                        "joiner_jit_trace-pnnx.ncnn.bin",
                        "tokens.txt",
                )

        val localModelDir =
                candidateAssetDirs.firstNotNullOfOrNull { assetDir ->
                    ensureModelDirReady(
                            assetDir = assetDir,
                            targetRootDir = context.filesDir,
                            requiredFiles = requiredFiles
                    )
                }
                        ?: return false

        val featConfig =
                getFeatureExtractorConfig(sampleRate = SAMPLE_RATE.toFloat(), featureDim = 80)

        val modelConfig =
                ModelConfig(
                        encoderParam =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        encoderBin =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        decoderParam =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        decoderBin =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        joinerParam =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        joinerBin =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
                        tokens = File(localModelDir, "tokens.txt").absolutePath,
                        numThreads = 4,
                        useGPU = false
                )

        val decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4)

        val recognizerConfig =
                RecognizerConfig(
                        featConfig = featConfig,
                        modelConfig = modelConfig,
                        decoderConfig = decoderConfig,
                        // 按住说话模式：用户按住按钮期间持续录音，
                        // 仅在显式调用 stopListening() 时定稿。
                        enableEndpoint = false,
                        rule1MinTrailingSilence = 2.4f,
                        rule2MinTrailingSilence = 1.2f,
                        rule3MinUtteranceLength = 20.0f,
                        hotwordsFile = "",
                        hotwordsScore = 1.5f
                )

        return try {
            recognizer =
                    SherpaNcnn(
                            config = recognizerConfig,
                            assetManager = null // Force using newFromFile
                    )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SherpaNcnn instance", e)
            lastInitializationError = "识别器创建失败：${e.message}"
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

        // R-04：以“清单 + 逐文件长度”校验缓存完整性；
        // 清单缺失（旧版本缓存/中断遗留）或长度不符 → 缓存不可信，整体原子重拷。
        if (isModelCacheComplete(targetDir, requiredFiles)) {
            Log.d(TAG, "Model cache is complete and verified: ${targetDir.absolutePath}")
            return targetDir
        }

        // 兼容布局是可选候选；不存在时直接尝试下一项，不创建空缓存或报加载错误。
        val assetEntries = context.assets.list(assetDir)?.toSet().orEmpty()
        if (!assetEntries.containsAll(requiredFiles)) {
            if (assetEntries.isNotEmpty()) {
                lastInitializationError = "assets 缺少模型文件：$assetDir"
            }
            return null
        }
        if (targetDir.exists()) {
            Log.w(
                    TAG,
                    "Model cache is incomplete. Re-staging model files: ${targetDir.absolutePath}"
            )
        }
        return try {
            stageCopyAndSwap(assetDir, targetRootDir, dirName, requiredFiles)
            if (isModelCacheComplete(targetDir, requiredFiles)) targetDir else {
                Log.e(TAG, "Model assets are incomplete after staging: $assetDir")
                lastInitializationError = "模型资产拷贝后不完整：$assetDir"
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model assets.", e)
            lastInitializationError = "模型资产拷贝失败：${e.message}"
            null
        }
    }

    /** 缓存完整性校验：清单可解析 + 必需文件存在且长度与清单一致。 */
    private fun isModelCacheComplete(targetDir: File, requiredFiles: List<String>): Boolean {
        val manifestFile = File(targetDir, ModelCacheManifest.FILE_NAME)
        if (!manifestFile.isFile) return false
        val manifest =
                runCatching { ModelCacheManifest.parse(manifestFile.readText()) }.getOrNull()
                        ?: return false

        return requiredFiles.all { name ->
            val f = File(targetDir, name)
            val entry = manifest[name]
            f.isFile && f.length() > 0 && entry != null && entry.length == f.length()
        }
    }

    /**
     * R-04 的核心交付路径：临时目录整份拷贝 → 校验 → 写清单 → 原子 rename 提交。
     *
     * 中断安全：任何一步失败都丢弃暂存目录；正式目录只有通过 rename 一步到位出现，
     * 不再存在“拷贝中断后长度大于 0 的残缺文件被当成完整模型”的路径，
     * 从源头规避部分 native 加载路径 exit(-1) 终止进程的风险。
     */
    @Throws(IOException::class)
    private fun stageCopyAndSwap(
            assetDir: String,
            targetRootDir: File,
            dirName: String,
            requiredFiles: List<String>,
    ) {
        val targetDir = File(targetRootDir, dirName)
        val stagingDir = File(targetRootDir, "$dirName.staging")

        runCatching { stagingDir.deleteRecursively() }
        if (stagingDir.exists()) {
            throw IOException("Failed to clean staging dir: ${stagingDir.absolutePath}")
        }

        try {
            copyAssetDirRecursive(assetDir, stagingDir, overwrite = true)

            // 校验必需文件并计算摘要（仅在拷贝结束后做一次全量哈希）
            val entries = LinkedHashMap<String, ModelCacheManifest.Entry>()
            for (name in requiredFiles) {
                val f = File(stagingDir, name)
                if (!f.isFile || f.length() <= 0) {
                    throw IOException("Staged model file missing or empty: $name")
                }
                entries[name] = ModelCacheManifest.Entry(length = f.length(), sha256 = sha256Hex(f))
            }

            // 清单最后写入并 fsync：只有完整拷贝+摘要都到位后才可能出现清单
            val manifestFile = File(stagingDir, ModelCacheManifest.FILE_NAME)
            FileOutputStream(manifestFile).use { out ->
                out.write(ModelCacheManifest.format(entries).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }

            // 原子提交：同一父目录内 rename 到位
            if (targetDir.exists()) {
                runCatching { targetDir.deleteRecursively() }
                if (targetDir.exists()) {
                    throw IOException("Failed to remove stale cache dir: ${targetDir.absolutePath}")
                }
            }
            if (!stagingDir.renameTo(targetDir)) {
                throw IOException(
                        "Atomic rename failed: ${stagingDir.absolutePath} -> ${targetDir.absolutePath}"
                )
            }
            Log.d(TAG, "Model cache staged and committed atomically: ${targetDir.absolutePath}")
        } catch (t: Throwable) {
            // 丢弃不完整的暂存目录，保持原缓存/无缓存状态
            runCatching { stagingDir.deleteRecursively() }
            throw t
        }
    }

    /** 流式计算文件 SHA-256（hex 小写）。 */
    @Throws(IOException::class)
    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val hex = StringBuilder(digest.digest().size * 2)
        for (b in digest.digest()) {
            val v = b.toInt() and 0xFF
            hex.append("0123456789abcdef"[v ushr 4])
            hex.append("0123456789abcdef"[v and 0x0F])
        }
        return hex.toString()
    }

    /**
     * 开始语音识别
     *
     * 会话在会话执行器上串行运行：上一会话的任务完全退出（含收尾排空）后，新会话任务才会开始，
     * 因此不存在旧任务触碰新 AudioRecord 或共享 native 识别器的交错窗口。
     *
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

        val session =
                Session(
                        generation = sessionGeneration.incrementAndGet(),
                        listener = listener
                )
        activeSession = session
        isListening = true

        sessionExecutor.execute { runSession(session) }
    }

    /**
     * 停止语音识别（松手发送路径）。
     *
     * 只发出“停止采集”请求，立即返回；尾音排空顺序
     * `inputFinished() → while (isReady()) decode() → 读取最终文本`
     * 由本会话任务在会话执行器线程上串行完成，并恰好派发一次 [RecognitionListener.onFinalResult]。
     * 调用线程不再直接读取 recognizer.text（R-02）。
     */
    fun stopListening() {
        val session = activeSession
        if (session == null || !isListening) {
            return
        }

        Log.d(TAG, "Stopping recognition...")
        session.requestStop()
    }

    /** 取消语音识别（不返回结果，不产出 final）。 */
    fun cancel() {
        val session = activeSession ?: return

        Log.d(TAG, "Cancelling recognition...")
        session.requestCancel()
        isListening = false
    }

    /** 释放识别器资源（幂等），并等待在途会话先退出。 */
    fun shutdown() {
        // 先取消在途会话：会话任务在下一轮读取前感知标记并退出，且只释放自己创建的 AudioRecord。
        cancel()
        // 捕获当前识别器引用后置空快状态，再把 native close 排入会话队列：
        // close 与仍在运行的会话任务串行执行，native 对象不会再被任何在途识别操作访问，
        // 释放因此具有确定性（不再只依赖 finalize()，R-06）。
        val rec = recognizer ?: return
        recognizer = null
        isInitialized = false
        sessionExecutor.execute {
            runCatching { rec.close() }
                    .onFailure { Log.w(TAG, "Failed to close sherpa native recognizer", it) }
        }
    }

    /**
     * 一次按住说话会话的全部状态。
     *
     * [audioRecord] 与 [listener] 由本会话独占：采样、排空、终态派发与 AudioRecord 生命周期
     * 只属于本会话任务线程；stop/cancel 请求可由任意线程写入 volatile 标记，
     * 并尽力解除阻塞中的 read()（见 [unblockRead]）。
     */
    private inner class Session(
            val generation: Long,
            val listener: RecognitionListener
    ) {
        @Volatile
        var stopRequested = false
            private set

        @Volatile
        var cancelRequested = false
            private set

        /** 本会话独占的 AudioRecord；只在本会话任务线程上创建与释放，unblockRead 仅 stop()。 */
        @Volatile
        private var audioRecord: AudioRecord? = null

        /** 终态恰好一次：final 与 error 二者其一。 */
        private val terminalEmitted = AtomicBoolean(false)

        /** 会话收尾幂等。 */
        private val finished = AtomicBoolean(false)

        fun attach(record: AudioRecord) {
            audioRecord = record
        }

        fun requestStop() {
            stopRequested = true
            unblockRead()
        }

        fun requestCancel() {
            cancelRequested = true
            unblockRead()
        }

        /** 恰好一次终态标记（final 或 error 其一），取消后禁止再产出 final。 */
        fun tryMarkTerminal(): Boolean = terminalEmitted.compareAndSet(false, true)

        /** 会话收尾（幂等）：释放本会话 AudioRecord；仅当仍是当前会话时复位全局状态。 */
        fun finish() {
            if (!finished.compareAndSet(false, true)) return

            releaseAudioRecord()

            if (sessionGeneration.get() == generation) {
                isListening = false
                if (activeSession === this) {
                    activeSession = null
                }
            }
        }

        /** 尽力让阻塞中的 read() 立即返回，加速 stop/cancel 请求被会话任务观察到；不负责释放。 */
        private fun unblockRead() {
            val ar = audioRecord ?: return
            runCatching {
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    ar.stop()
                }
            }
        }

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
    }

    /**
     * 会话任务主入口。整体在会话执行器线程上串行执行。
     *
     * 终态顺序：
     * 1. `reset(true)` 重建流（R-05：不复用上一会话 Stream/decoder/encoder 状态）
     * 2. 创建并启动本会话独占的 AudioRecord
     * 3. 采样循环（可随时被 stop/cancel/generation 失配打断）
     * 4. stop：`inputFinished() → while (isReady()) decode() → 最终文本 → 恰好一次 final`（R-02）
     *    cancel / generation 失配：不产出 final（不变量 3）
     * 5. finish()：释放本会话 AudioRecord，复位全局状态（幂等）
     */
    private fun runSession(session: Session) {
        try {
            if (sessionGeneration.get() != session.generation) {
                session.finish()
                return
            }

            val rec = recognizer
            if (rec == null) {
                session.finish()
                if (session.tryMarkTerminal()) {
                    dispatch(session) {
                        it.onError(IllegalStateException("Recognizer is not initialized"))
                    }
                }
                return
            }

            // R-05：按住说话是独立会话，重建流，不复用上一会话 decoder/encoder 状态。
            rec.reset(true)

            val ar = createAndStartAudioRecord()
            session.attach(ar)

            // 读取分片与创建 AudioRecord 时传入的缓冲大小一致（bufferSizeBytes = minBufferSize*2 → shorts = minBufferSize）。
            val chunkShorts =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    )

            val endpointText = runRecordingLoop(session, rec, ar, chunkShorts)
            Log.d(TAG, "Recording loop ended.")

            if (session.cancelRequested || sessionGeneration.get() != session.generation) {
                // 取消/被替换：不产出 final；先标记终态，杜绝任何迟到 final（不变量 3）。
                session.tryMarkTerminal()
                session.finish()
                return
            }

            if (endpointText != null) {
                // 端点分支（当前配置 enableEndpoint=false，不可达；保留与旧实现一致的语义）：
                // onResult 已在循环内派发，final 文本取端点处的识别文本。
                rec.reset(false)
                session.finish()
                if (session.tryMarkTerminal()) {
                    dispatch(session) { it.onFinalResult(endpointText) }
                }
                return
            }

            // R-02：尾音排空，与本会话采样同线程串行执行；
            // 若排空期间被取消或换代，同样禁止产出 final。
            runCatching {
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    ar.stop()
                }
            }
            rec.inputFinished()
            while (rec.isReady()) {
                rec.decode()
            }
            val finalText = rec.text

            if (session.cancelRequested || sessionGeneration.get() != session.generation) {
                session.tryMarkTerminal()
                session.finish()
                return
            }
            session.finish()
            if (session.tryMarkTerminal()) {
                dispatch(session) { it.onFinalResult(finalText) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Voice session failed", t)
            session.finish()
            if (session.tryMarkTerminal()) {
                dispatch(session) {
                    it.onError(t as? Exception ?: IllegalStateException(t.message, t))
                }
            }
        }
    }

    /**
     * 创建并启动本会话独占的 AudioRecord。任何失败抛出异常，由 [runSession]
     * 统一收敛为恰好一次 error 终态（R-08）。
     */
    @Throws(IOException::class, IllegalStateException::class)
    private fun createAndStartAudioRecord(): AudioRecord {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            throw IllegalStateException("AudioRecord.getMinBufferSize failed: $minBufferSize")
        }

        val ar =
                AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        channelConfig,
                        audioFormat,
                        minBufferSize * 2
                )

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { ar.release() }
            throw IllegalStateException("AudioRecord init failed, state=${ar.state}")
        }

        ar.startRecording()
        Log.d(TAG, "Started recording")
        return ar
    }

    /**
     * 采样循环。只读取会话独占的 AudioRecord；每轮迭代开头检查 stop/cancel/generation，
     * 保证停止与取消请求在一个读取周期内被观察到。返回非空表示端点已触发（定稿文本）。
     */
    private fun runRecordingLoop(
            session: Session,
            recognizer: SherpaNcnn,
            audioRecord: AudioRecord,
            chunkShorts: Int,
    ): String? {
        val audioBuffer = ShortArray(chunkShorts)
        var lastText = ""

        // R-09：录音时长累计（按实际采样数），达到上限后按与“松手”相同的终态路径收尾。
        var recordedShorts = 0L
        // R-09/8.3 P2：振幅回调限制在约 30Hz，避免每个音频块都把主线程唤醒一次。
        var lastAmplitudeDispatchMs = 0L

        while (sessionGeneration.get() == session.generation &&
                        !session.stopRequested &&
                        !session.cancelRequested
        ) {
            val ret = audioRecord.read(audioBuffer, 0, audioBuffer.size)
            if (ret < 0) {
                throw IOException("AudioRecord.read failed: $ret")
            }
            if (ret == 0) {
                // 麦克风刚启动或静音间隙：短暂退避，避免在会话线程上空转成紧密循环。
                runCatching { Thread.sleep(10) }
                continue
            }

            recordedShorts += ret

            // 计算振幅 (RMS)
            var sum = 0.0
            for (i in 0 until ret) {
                sum += audioBuffer[i] * audioBuffer[i]
            }
            val rms = Math.sqrt(sum / ret)
            // 简单的归一化处理 (32768 为最大值，但语音输入通常到不了那么大，这里取一个经验值 3000-5000)
            val amplitude = (rms / 3000.0).toFloat().coerceIn(0f, 1f)
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastAmplitudeDispatchMs >= AMPLITUDE_DISPATCH_INTERVAL_MS) {
                lastAmplitudeDispatchMs = nowMs
                dispatch(session) { it.onAmplitude(amplitude) }
            }

            val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
            recognizer.acceptSamples(samples)
            while (recognizer.isReady()) {
                recognizer.decode()
            }

            val isEndpoint = recognizer.isEndpoint()
            val text = recognizer.text

            if (text.isNotBlank() && lastText != text) {
                lastText = text
                dispatch(session) {
                    if (isEndpoint) {
                        it.onResult(text)
                    } else {
                        it.onPartialResult(text)
                    }
                }
            }

            if (isEndpoint) {
                return text
            }

            // R-09：录音达到最大时长上限 → 走与“松手”一致的终态路径（停止采集、尾音排空、恰好一次 final），
            // 不再无限录音增长内存，也不让用户停留在无上限的按住状态。
            if (recordedShorts >= SAMPLE_RATE.toLong() * MAX_RECORDING_SECONDS) {
                Log.w(
                        TAG,
                        "Recording reached max duration (${MAX_RECORDING_SECONDS}s); finalizing session"
                )
                return null
            }
        }

        return null
    }

    /**
     * 把回调投递到主线程；generation 失配（旧会话迟到回调）被整组丢弃，
     * 实现“旧会话不得污染新会话”（不变量 4）。
     */
    private fun dispatch(session: Session, block: (RecognitionListener) -> Unit) {
        if (sessionGeneration.get() != session.generation) return
        mainHandler.post {
            if (sessionGeneration.get() != session.generation) return@post
            block(session.listener)
        }
    }
}
