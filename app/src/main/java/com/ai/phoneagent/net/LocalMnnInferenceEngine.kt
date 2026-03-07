package com.ai.phoneagent.net

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LocalMnnInferenceEngine {
    private const val TEMP_IMAGE_DIR = "local_mnn_images"
    private const val TEMP_IMAGE_TTL_MS = 24L * 60L * 60L * 1000L
    private const val LOCAL_MAX_NEW_TOKENS = 640
    private const val LOCAL_MAX_DURATION_MS = 75_000L
    private const val LOCAL_MAX_OUTPUT_CHARS = 3600
    private const val LOCAL_MAX_REPEAT_CHUNK_STREAK = 22
    private const val LOCAL_MAX_IMAGE_BYTES = 8L * 1024L * 1024L

    private val initMutex = Mutex()
    private val requestMutex = Mutex()
    private val tempImageSeq = AtomicLong(0L)

    @Volatile private var activeConfigPath: String? = null
    @Volatile private var activeSession: LlmSession? = null

    suspend fun sendChatStreamResult(
        context: Context,
        messages: List<ChatRequestMessage>,
        onReasoningDelta: (String) -> Unit,
        onContentDelta: (String) -> Unit,
        shouldStop: (() -> Boolean)? = null,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val session = ensureSession(context.applicationContext)
                val history = buildHistory(context.applicationContext, messages)
                if (history.isEmpty()) {
                    throw IOException("Empty local prompt")
                }

                requestMutex.withLock {
                    var emitted = false
                    var stopped = false
                    val guard = GenerationGuard()
                    val parser =
                        ThinkTagStreamParser(
                            onReasoning = { delta ->
                                if (delta.isNotEmpty()) {
                                    emitted = true
                                    onReasoningDelta(delta)
                                }
                            },
                            onContent = { delta ->
                                if (delta.isNotEmpty()) {
                                    emitted = true
                                    onContentDelta(delta)
                                }
                            }
                        )

                    session.submitFullHistory(
                        history = history,
                        progressListener =
                            object : GenerateProgressListener {
                                override fun onProgress(progress: String?): Boolean {
                                    if (!progress.isNullOrEmpty()) {
                                        guard.recordChunk(progress)
                                        parser.feed(progress)
                                    }
                                    val shouldInterrupt =
                                        shouldStop?.invoke() == true || guard.shouldInterrupt()
                                    if (shouldInterrupt) {
                                        stopped = true
                                    }
                                    return shouldInterrupt
                                }
                            }
                    )

                    parser.finish()

                    if (guard.hitGuard && emitted) {
                        onContentDelta("\n\n（本地推理已自动截断，疑似循环输出）")
                    }

                    if (!emitted && !stopped) {
                        throw IOException("Empty local model response")
                    }
                }
            }
        }
    }

    suspend fun sendChatResult(
        context: Context,
        messages: List<ChatRequestMessage>,
        shouldStop: (() -> Boolean)? = null,
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val session = ensureSession(context.applicationContext)
                val history = buildHistory(context.applicationContext, messages)
                if (history.isEmpty()) {
                    throw IOException("Empty local prompt")
                }

                requestMutex.withLock {
                    val raw = StringBuilder()
                    val reasoning = StringBuilder()
                    val content = StringBuilder()
                    val guard = GenerationGuard()
                    val parser =
                        ThinkTagStreamParser(
                            onReasoning = { delta -> reasoning.append(delta) },
                            onContent = { delta -> content.append(delta) }
                        )

                    session.submitFullHistory(
                        history = history,
                        progressListener =
                            object : GenerateProgressListener {
                                override fun onProgress(progress: String?): Boolean {
                                    if (!progress.isNullOrEmpty()) {
                                        guard.recordChunk(progress)
                                        raw.append(progress)
                                        parser.feed(progress)
                                    }
                                    return shouldStop?.invoke() == true || guard.shouldInterrupt()
                                }
                            }
                    )
                    parser.finish()

                    val answer = content.toString().trim()
                    if (answer.isNotBlank()) {
                        return@withLock answer
                    }

                    val rawText = raw.toString().trim()
                    if (rawText.isBlank()) {
                        throw IOException("Empty local model response")
                    }

                    val finalText = stripThinkTags(rawText).ifBlank { rawText }
                    if (guard.hitGuard) {
                        "$finalText\n\n（本地推理已自动截断，疑似循环输出）"
                    } else {
                        finalText
                    }
                }
            }
        }
    }

    suspend fun releaseSession() {
        initMutex.withLock {
            activeSession?.release()
            activeSession = null
            activeConfigPath = null
        }
    }

    private suspend fun ensureSession(context: Context): LlmSession {
        val configPath =
            ModelScopeModelDownloader.getQwen35ConfigPath(context)
                ?: throw IOException("Local model config missing, please download model first")

        return initMutex.withLock {
            val existing = activeSession
            if (existing != null && activeConfigPath == configPath) {
                return@withLock existing
            }

            existing?.release()

            val created = LlmSession(configPath = configPath, keepHistory = false)
            created.load()
            created.updateMaxNewTokens(LOCAL_MAX_NEW_TOKENS)
            activeSession = created
            activeConfigPath = configPath
            created
        }
    }

    private fun buildHistory(
        context: Context,
        messages: List<ChatRequestMessage>
    ): List<kotlin.Pair<String, String>> {
        val result = ArrayList<kotlin.Pair<String, String>>(messages.size)
        for (message in messages) {
            val role = message.role.trim().ifBlank { "user" }
            val content = toLocalPromptText(context, message.content).trim()
            if (content.isBlank()) continue
            result += role to content
        }
        return result
    }

    private fun toLocalPromptText(context: Context, content: Any): String {
        return when (content) {
            is String -> content
            is List<*> -> buildPromptFromMultimodalContent(context, content)
            else -> content.toString()
        }
    }

    private fun buildPromptFromMultimodalContent(context: Context, content: List<*>): String {
        val sb = StringBuilder()
        content.forEach { item ->
            val part = item as? Map<*, *> ?: return@forEach
            when (part["type"]?.toString()) {
                "text" -> {
                    val text = part["text"]?.toString().orEmpty()
                    if (text.isNotBlank()) {
                        sb.append(text)
                    }
                }
                "image_url" -> {
                    val imageUrl = extractImageUrl(part)
                    val localPath = imageUrl?.let { resolveImageUrlToPath(context, it) }
                    if (!localPath.isNullOrBlank()) {
                        sb.append("<img>").append(localPath).append("</img>")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun extractImageUrl(item: Map<*, *>): String? {
        val imageUrlValue = item["image_url"] ?: return null
        return when (imageUrlValue) {
            is String -> imageUrlValue
            is Map<*, *> -> imageUrlValue["url"]?.toString()
            else -> null
        }
    }

    private fun resolveImageUrlToPath(context: Context, url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null

        return when {
            trimmed.startsWith("data:image/", ignoreCase = true) -> {
                decodeDataUriToTempFile(context, trimmed)?.absolutePath
            }
            trimmed.startsWith("content://", ignoreCase = true) -> {
                copyUriToTempFile(context, Uri.parse(trimmed))?.absolutePath
            }
            trimmed.startsWith("file://", ignoreCase = true) -> {
                Uri.parse(trimmed).path?.let(::File)?.takeIf(::isLocalImageFileUsable)?.absolutePath
            }
            else -> File(trimmed).takeIf(::isLocalImageFileUsable)?.absolutePath
        }
    }

    private fun decodeDataUriToTempFile(context: Context, dataUri: String): File? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 0 || commaIndex >= dataUri.lastIndex) return null

        val header = dataUri.substring(0, commaIndex)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val cleanedBase64 = dataUri.substring(commaIndex + 1).filterNot(Char::isWhitespace)
        val estimatedBytes = estimateDecodedBase64Bytes(cleanedBase64) ?: return null
        if (estimatedBytes <= 0L || estimatedBytes > LOCAL_MAX_IMAGE_BYTES) return null

        val extension = extensionFromDataUriHeader(header)
        val target = createTempImageFile(context, extension)
        return runCatching {
                val bytes = Base64.decode(cleanedBase64, Base64.DEFAULT)
                if (bytes.isEmpty() || bytes.size.toLong() > LOCAL_MAX_IMAGE_BYTES) {
                    deleteQuietly(target)
                    return null
                }
                target.writeBytes(bytes)
                target
            }
            .getOrElse {
                deleteQuietly(target)
                null
            }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        val declaredSize =
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0L } ?: descriptor.declaredLength.takeIf { it > 0L } ?: 0L
                } ?: 0L
            }.getOrDefault(0L)
        if (declaredSize > LOCAL_MAX_IMAGE_BYTES) return null

        val extension = extensionFromUri(uri)
        val target = createTempImageFile(context, extension)
        return runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (!copyStreamToFileWithLimit(input, target, LOCAL_MAX_IMAGE_BYTES)) {
                        deleteQuietly(target)
                        return null
                    }
                } ?: run {
                    deleteQuietly(target)
                    return null
                }
                target
            }
            .getOrElse {
                deleteQuietly(target)
                null
            }
    }

    private fun isLocalImageFileUsable(file: File): Boolean {
        return file.exists() && file.isFile && file.length() in 1..LOCAL_MAX_IMAGE_BYTES
    }

    private fun estimateDecodedBase64Bytes(base64Data: String): Long? {
        if (base64Data.isEmpty()) return 0L
        val padding =
            when {
                base64Data.endsWith("==") -> 2L
                base64Data.endsWith("=") -> 1L
                else -> 0L
            }
        val decodedSize = (base64Data.length.toLong() * 3L) / 4L - padding
        return decodedSize.takeIf { it >= 0L }
    }

    private fun copyStreamToFileWithLimit(
        input: java.io.InputStream,
        target: File,
        maxBytes: Long,
    ): Boolean {
        var totalBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        target.outputStream().use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read.toLong()
                if (totalBytes > maxBytes) {
                    return false
                }
                output.write(buffer, 0, read)
            }
        }
        return totalBytes > 0L
    }

    private fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching { file.delete() }
    }

    private fun extensionFromDataUriHeader(header: String): String {
        val mime = header.substringAfter("data:", "").substringBefore(';').lowercase()
        return extensionFromMimeType(mime)
    }

    private fun extensionFromUri(uri: Uri): String {
        val path = uri.path.orEmpty().lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            path.endsWith(".bmp") -> "bmp"
            path.endsWith(".heic") || path.endsWith(".heif") -> "heic"
            else -> "jpg"
        }
    }

    private fun extensionFromMimeType(mime: String): String {
        return when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
    }

    private fun createTempImageFile(context: Context, extension: String): File {
        val dir = File(context.cacheDir, TEMP_IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        cleanupExpiredTempFiles(dir)
        val fileName = "img_${System.currentTimeMillis()}_${tempImageSeq.incrementAndGet()}.$extension"
        return File(dir, fileName)
    }

    private fun cleanupExpiredTempFiles(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && now - file.lastModified() > TEMP_IMAGE_TTL_MS) {
                runCatching { file.delete() }
            }
        }
    }

    private fun stripThinkTags(text: String): String {
        return text.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
    }

    private class ThinkTagStreamParser(
        private val onReasoning: (String) -> Unit,
        private val onContent: (String) -> Unit,
    ) {
        private val buffer = StringBuilder()
        private var inThink = false

        fun feed(chunk: String) {
            if (chunk.isEmpty()) return
            buffer.append(chunk)
            parseAvailable()
        }

        fun finish() {
            if (buffer.isEmpty()) return
            val remaining = buffer.toString()
            if (remaining.isNotEmpty()) {
                if (inThink) onReasoning(remaining) else onContent(remaining)
            }
            buffer.clear()
        }

        private fun parseAvailable() {
            while (buffer.isNotEmpty()) {
                if (inThink) {
                    val endIdx = buffer.indexOf(THINK_END_TAG)
                    if (endIdx >= 0) {
                        if (endIdx > 0) {
                            onReasoning(buffer.substring(0, endIdx))
                        }
                        buffer.delete(0, endIdx + THINK_END_TAG.length)
                        inThink = false
                        continue
                    }
                    emitKeepingPotentialTag(THINK_END_TAG, onReasoning)
                    break
                } else {
                    val startIdx = buffer.indexOf(THINK_START_TAG)
                    if (startIdx >= 0) {
                        if (startIdx > 0) {
                            onContent(buffer.substring(0, startIdx))
                        }
                        buffer.delete(0, startIdx + THINK_START_TAG.length)
                        inThink = true
                        continue
                    }
                    emitKeepingPotentialTag(THINK_START_TAG, onContent)
                    break
                }
            }
        }

        private fun emitKeepingPotentialTag(marker: String, emit: (String) -> Unit) {
            val keepTail = marker.length - 1
            val emitLen = (buffer.length - keepTail).coerceAtLeast(0)
            if (emitLen <= 0) return
            emit(buffer.substring(0, emitLen))
            buffer.delete(0, emitLen)
        }

        companion object {
            private const val THINK_START_TAG = "<think>"
            private const val THINK_END_TAG = "</think>"
        }
    }

    private class GenerationGuard {
        private val startedAt = System.currentTimeMillis()
        private val output = StringBuilder()
        private var lastChunk = ""
        private var repeatChunkStreak = 0
        var hitGuard: Boolean = false
            private set

        fun recordChunk(chunk: String) {
            if (chunk.isEmpty()) return
            output.append(chunk)
            repeatChunkStreak =
                if (chunk == lastChunk) repeatChunkStreak + 1 else 0
            lastChunk = chunk
        }

        fun shouldInterrupt(): Boolean {
            if (hitGuard) return true

            val now = System.currentTimeMillis()
            if (now - startedAt > LOCAL_MAX_DURATION_MS) {
                hitGuard = true
                return true
            }

            if (output.length > LOCAL_MAX_OUTPUT_CHARS) {
                hitGuard = true
                return true
            }

            if (repeatChunkStreak >= LOCAL_MAX_REPEAT_CHUNK_STREAK) {
                hitGuard = true
                return true
            }

            if (hasTailLoop(output)) {
                hitGuard = true
                return true
            }

            return false
        }

        private fun hasTailLoop(sb: StringBuilder): Boolean {
            val text = sb.toString()
            if (text.length < 96) return false
            val candidateLens = intArrayOf(12, 16, 20, 24, 32, 40, 48, 64)
            for (len in candidateLens) {
                val required = len * 4
                if (text.length < required) continue
                val suffix = text.takeLast(required)
                val unit = suffix.takeLast(len)
                val repeated = unit.repeat(4)
                if (suffix == repeated) return true
            }
            return false
        }
    }
}
