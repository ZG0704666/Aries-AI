package com.ai.phoneagent.net

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ModelScopeModelDownloader {

    // MNN local-inference package (contains llm.mnn / llm.mnn.weight / llm_config.json).
    const val QWEN35_MODEL_ID = "MNN/Qwen3.5-9B-MNN"
    const val QWEN35_MODEL_NAME = "Qwen3.5-9B"

    private const val MODELSCOPE_API_BASE = "https://www.modelscope.cn/api/v1/models"
    private const val MODELSCOPE_RESOLVE_BASE = "https://www.modelscope.cn/models"
    private const val USER_AGENT = "PhoneAgent"
    private const val DOWNLOAD_ROOT_DIR = "AriesModels"

    private val requiredExactNames =
        setOf(
            "config.json",
            "configuration.json",
            "llm.mnn",
            "llm.mnn.json",
            "llm.mnn.weight",
            "llm_config.json",
            "tokenizer.txt",
            "visual.mnn",
            "visual.mnn.weight",
        )

    private val extraRequiredExactNamesByModelId =
        mapOf(
            "MNN/Qwen3.5-9B-MNN" to setOf("embeddings_bf16.bin"),
        )

    data class EnqueueResult(
        val enqueuedCount: Int,
        val skippedCount: Int,
        val totalBytes: Long,
        val targetDir: String,
        val downloadIds: List<Long>,
    )

    private data class RepoFile(
        val path: String,
        val fileName: String,
        val size: Long,
    )

    suspend fun enqueueQwen35Downloads(context: Context): Result<EnqueueResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val modelFiles = fetchRequiredFiles(QWEN35_MODEL_ID)
                if (modelFiles.isEmpty()) {
                    throw IOException("No downloadable files returned for $QWEN35_MODEL_ID")
                }
                enqueueDownloads(context, QWEN35_MODEL_ID, QWEN35_MODEL_NAME, modelFiles)
            }
        }
    }

    fun getQwen35ModelDir(context: Context): File? {
        val externalDownloadDir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(externalDownloadDir, "$DOWNLOAD_ROOT_DIR/$QWEN35_MODEL_NAME")
    }

    fun getQwen35ConfigPath(context: Context): String? {
        val modelDir = getQwen35ModelDir(context) ?: return null
        val configFile = File(modelDir, "config.json")
        return if (configFile.exists() && configFile.isFile) configFile.absolutePath else null
    }

    fun isQwen35ModelReady(context: Context): Boolean {
        val modelDir = getQwen35ModelDir(context) ?: return false
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        return requiredFileNamesForCurrentModel().all { name ->
            val file = File(modelDir, name)
            file.exists() && file.isFile && file.length() > 0L
        }
    }

    private fun fetchRequiredFiles(modelId: String): List<RepoFile> {
        val endpoint = "$MODELSCOPE_API_BASE/$modelId/repo/files?Revision=master"
        val body = httpGet(endpoint)
        val root = JSONObject(body)
        val files = root.optJSONObject("Data")?.optJSONArray("Files")
        if (files == null || files.length() == 0) return emptyList()

        val selected = linkedMapOf<String, RepoFile>()
        for (i in 0 until files.length()) {
            val item = files.optJSONObject(i) ?: continue
            val path = item.optString("Path", "").trim()
            val size = item.optLong("Size", 0L).coerceAtLeast(0L)
            val repoFile = normalizeRequiredRepoFile(path, size) ?: continue
            selected.putIfAbsent(repoFile.fileName, repoFile)
        }
        return selected.values.toList()
    }

    private fun normalizeRequiredRepoFile(path: String, size: Long): RepoFile? {
        val segments = safePathSegments(path) ?: return null
        val fileName = segments.lastOrNull() ?: return null
        if (fileName !in requiredFileNamesForCurrentModel()) return null
        return RepoFile(
            path = segments.joinToString("/"),
            fileName = fileName,
            size = size,
        )
    }

    private fun safePathSegments(path: String): List<String>? {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank()) return null
        if (normalized.startsWith('/')) return null
        if (normalized.contains("://") || normalized.contains(':')) return null

        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments
    }

    private fun requiredFileNamesForCurrentModel(): Set<String> {
        val extras = extraRequiredExactNamesByModelId[QWEN35_MODEL_ID].orEmpty()
        return requiredExactNames + extras
    }

    private fun enqueueDownloads(
        context: Context,
        modelId: String,
        modelName: String,
        files: List<RepoFile>,
    ): EnqueueResult {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: throw IOException("DownloadManager unavailable")
        val externalDownloadDir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("External download directory unavailable")

        val relativeModelDir = "$DOWNLOAD_ROOT_DIR/$modelName"
        val modelDir = File(externalDownloadDir, relativeModelDir).canonicalFile
        var enqueuedCount = 0
        var skippedCount = 0
        var totalBytes = 0L
        val downloadIds = mutableListOf<Long>()

        files.forEach { file ->
            val existing = File(modelDir, file.fileName).canonicalFile
            if (!existing.path.startsWith(modelDir.path + File.separator) && existing != modelDir) {
                throw IOException("Refusing unsafe model destination: ${file.fileName}")
            }
            if (existing.exists() && file.size > 0L && existing.length() == file.size) {
                skippedCount++
                return@forEach
            }

            val encodedPath = file.path.split('/').joinToString("/") { Uri.encode(it) }
            val fileUrl =
                "$MODELSCOPE_RESOLVE_BASE/$modelId/resolve/master/$encodedPath?download=true"
            val destinationPath = "$relativeModelDir/${file.fileName}"

            val request =
                DownloadManager.Request(Uri.parse(fileUrl)).apply {
                    setTitle(modelName)
                    setDescription(file.fileName)
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    addRequestHeader("User-Agent", USER_AGENT)
                    setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        destinationPath,
                    )
                }

            val id = dm.enqueue(request)
            enqueuedCount++
            totalBytes += file.size
            downloadIds += id
        }

        return EnqueueResult(
            enqueuedCount = enqueuedCount,
            skippedCount = skippedCount,
            totalBytes = totalBytes,
            targetDir = modelDir.absolutePath,
            downloadIds = downloadIds,
        )
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("ModelScope request failed ($code): ${text.take(240)}")
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}