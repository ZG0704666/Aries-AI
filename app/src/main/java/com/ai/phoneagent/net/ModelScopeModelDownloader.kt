package com.ai.phoneagent.net

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ai.phoneagent.core.common.AppJson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 本地推理模型下载器。
 *
 * 通过 ModelScope 仓库文件接口获取所需文件清单，并交由系统 DownloadManager 下载到应用外部目录。
 */
class ModelScopeModelDownloader {

    /** 仓库文件列表接口响应。 */
    @Serializable
    private data class ModelScopeRepoFilesResponse(
        @SerialName("Data") val data: ModelScopeRepoData? = null,
    )

    /** 响应中的 data 节点。 */
    @Serializable
    private data class ModelScopeRepoData(
        @SerialName("Files") val files: List<ModelScopeRepoFileEntry>? = null,
    )

    /** 单个仓库文件条目。 */
    @Serializable
    private data class ModelScopeRepoFileEntry(
        @SerialName("Path") val path: String? = null,
        @SerialName("Size") val size: Long? = null,
    )

    /** 所有模型都必须齐全的文件名集合。 */
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

    /** 特定模型额外必需的文件名集合。 */
    private val extraRequiredExactNamesByModelId =
        mapOf(
            "MNN/Qwen3.5-9B-MNN" to setOf("embeddings_bf16.bin"),
        )

    /** 下载入队结果。 */
    data class EnqueueResult(
        val enqueuedCount: Int,
        val skippedCount: Int,
        val totalBytes: Long,
        val targetDir: String,
        val downloadIds: List<Long>,
    )

    /** 归一化后的仓库文件信息。 */
    private data class RepoFile(
        val path: String,
        val fileName: String,
        val size: Long,
    )

    /** 拉取所需文件清单并入队下载。 */
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

    /** 返回模型在外部下载目录下的存放目录。 */
    fun getQwen35ModelDir(context: Context): File? {
        val externalDownloadDir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(externalDownloadDir, "$DOWNLOAD_ROOT_DIR/$QWEN35_MODEL_NAME")
    }

    /** 返回模型 config.json 的绝对路径；不存在时返回 null。 */
    fun getQwen35ConfigPath(context: Context): String? {
        val modelDir = getQwen35ModelDir(context) ?: return null
        val configFile = File(modelDir, "config.json")
        return if (configFile.exists() && configFile.isFile) configFile.absolutePath else null
    }

    /** 判断模型目录中所有必需文件是否齐全且非空。 */
    fun isQwen35ModelReady(context: Context): Boolean {
        val modelDir = getQwen35ModelDir(context) ?: return false
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        return requiredFileNamesForCurrentModel().all { name ->
            val file = File(modelDir, name)
            file.exists() && file.isFile && file.length() > 0L
        }
    }

    /** 从仓库文件列表接口筛选并归一化所需文件。 */
    private fun fetchRequiredFiles(modelId: String): List<RepoFile> {
        val endpoint = "$MODELSCOPE_API_BASE/$modelId/repo/files?Revision=master"
        val body = httpGet(endpoint)
        val root = AppJson.decodeFromString<ModelScopeRepoFilesResponse>(body)
        val files = root.data?.files.orEmpty()
        if (files.isEmpty()) return emptyList()

        val selected = linkedMapOf<String, RepoFile>()
        for (item in files) {
            val path = item.path.orEmpty().trim()
            val size = (item.size ?: 0L).coerceAtLeast(0L)
            val repoFile = normalizeRequiredRepoFile(path, size) ?: continue
            selected.putIfAbsent(repoFile.fileName, repoFile)
        }
        return selected.values.toList()
    }

    /** 将仓库路径归一为所需文件；不在白名单或路径非法时返回 null。 */
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

    /** 校验并拆分相对路径，拒绝绝对路径、协议前缀与目录穿越。 */
    private fun safePathSegments(path: String): List<String>? {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank()) return null
        if (normalized.startsWith('/')) return null
        if (normalized.contains("://") || normalized.contains(':')) return null

        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments
    }

    /** 当前模型所需文件名的全集（公共必需 + 该模型额外必需）。 */
    private fun requiredFileNamesForCurrentModel(): Set<String> {
        val extras = extraRequiredExactNamesByModelId[QWEN35_MODEL_ID].orEmpty()
        return requiredExactNames + extras
    }

    /**
     * 将文件逐个入队下载。
     *
     * 已存在且大小一致的文件会被跳过；目标路径经过越界校验防止写入目录之外。
     */
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

    /** 发起 GET 请求并返回响应文本；非 2xx 抛出异常。 */
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

    companion object {
        /** 本地推理模型包标识（含 llm.mnn / llm.mnn.weight / llm_config.json）。 */
        const val QWEN35_MODEL_ID = "MNN/Qwen3.5-9B-MNN"

        /** 本地推理模型显示名。 */
        const val QWEN35_MODEL_NAME = "Qwen3.5-9B"

        private const val MODELSCOPE_API_BASE = "https://www.modelscope.cn/api/v1/models"
        private const val MODELSCOPE_RESOLVE_BASE = "https://www.modelscope.cn/models"
        private const val USER_AGENT = "PhoneAgent"

        /** 模型下载的根目录名。 */
        private const val DOWNLOAD_ROOT_DIR = "AriesModels"

    }
}
