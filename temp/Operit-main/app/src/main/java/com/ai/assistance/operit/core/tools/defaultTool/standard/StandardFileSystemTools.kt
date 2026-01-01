package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.GrepResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.util.SyntaxCheckUtil
import com.ai.assistance.operit.util.PathMapper
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.HttpMultiPartDownloader
import com.ai.assistance.operit.util.FFmpegUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.services.OnnxEmbeddingService
import com.ai.assistance.operit.core.tools.ToolProgressBus

/**
 * Collection of file system operation tools for the AI assistant These tools use Java File APIs for
 * file operations
 */
open class StandardFileSystemTools(protected val context: Context) {
    companion object {
        protected const val TAG = "FileSystemTools"

        // 特殊文件类型扩展名列表（需要特殊处理提取文本的文件类型）
        protected val SPECIAL_FILE_EXTENSIONS = listOf(
            "doc", "docx",      // Word documents
            "pdf",              // PDF documents
            "jpg", "jpeg",      // Image files
            "png", "gif", "bmp",
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus",
            "mp4", "mkv", "mov", "webm", "avi", "m4v"
        )
    }

    // ApiPreferences 实例，用于动态获取配置
    protected val apiPreferences: ApiPreferences by lazy {
        ApiPreferences.getInstance(context)
    }

    // SSH文件管理器（单例，懒加载）
    private val sshFileManager by lazy {
        SSHFileConnectionManager.getInstance(context)
    }

    // TerminalManager（单例，懒加载）
    private val terminalManager by lazy {
        TerminalManager.getInstance(context)
    }

    // Linux文件系统提供者，优先使用SSH连接，否则从TerminalManager获取
    protected fun getLinuxFileSystem(): FileSystemProvider {
        // 先尝试获取SSH连接的文件系统
        val sshProvider = sshFileManager.getFileSystemProvider()
        
        // 如果SSH已登录，使用SSH文件系统
        if (sshProvider != null) {
            AppLogger.d(TAG, "Using SSH file system provider")
            return sshProvider
        }
        
        // 否则使用本地Terminal的文件系统
        AppLogger.d(TAG, "Using local terminal file system provider")
        return terminalManager.getFileSystemProvider()
    }

    // Linux文件系统工具实例
    protected val linuxTools: LinuxFileSystemTools by lazy {
        LinuxFileSystemTools(context)
    }

    /** 检查是否是Linux环境 */
    protected fun isLinuxEnvironment(environment: String?): Boolean {
        return environment?.lowercase() == "linux"
    }

    /** Adds line numbers to a string of content. */
    protected fun addLineNumbers(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits = lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    /** Adds line numbers to a string of content, starting from a specific line number. */
    protected fun addLineNumbers(content: String, startLine: Int, totalLines: Int): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits =
            if (totalLines > 0) totalLines.toString().length else lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(startLine + index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    /** 判断是否为需要特殊处理的文件类型 */
    protected fun isSpecialFileType(fileExtension: String): Boolean {
        return fileExtension.lowercase() in SPECIAL_FILE_EXTENSIONS
    }

    /** 统计文件总行数 */
    protected fun countFileLines(file: File): Int {
        var totalLines = 0
        file.bufferedReader().use { reader ->
            while (reader.readLine() != null) {
                totalLines++
            }
        }
        return totalLines
    }

    /** 从文件中读取指定范围的行 */
    private fun readLinesFromFile(file: File, startLine: Int, endLine: Int): String {
        val partContent = StringBuilder()
        var currentLine = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (currentLine >= endLine) return@useLines
                if (currentLine >= startLine) {
                    partContent.append(line).append('\n')
                }
                currentLine++
            }
        }
        // Remove last newline if content is not empty
        if (partContent.isNotEmpty()) {
            partContent.setLength(partContent.length - 1)
        }
        return partContent.toString()
    }


    /** List files in a directory */
    open suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.listFiles(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            if (!directory.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Directory does not exist: $path"
                )
            }

            if (!directory.isDirectory) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a directory: $path"
                )
            }

            val entries = mutableListOf<DirectoryListingData.FileEntry>()
            val files = directory.listFiles() ?: emptyArray()

            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

            for (file in files) {
                if (file.name != "." && file.name != "..") {
                    entries.add(
                        DirectoryListingData.FileEntry(
                            name = file.name,
                            isDirectory = file.isDirectory,
                            size = file.length(),
                            permissions = getFilePermissions(file),
                            lastModified =
                            dateFormat.format(
                                Date(file.lastModified())
                            )
                        )
                    )
                }
            }

            AppLogger.d(TAG, "Listed ${entries.size} entries in directory $path")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = DirectoryListingData(path, entries),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** Get file permissions as a string like "rwxr-xr-x" */
    protected fun getFilePermissions(file: File): String {
        // Java has limited capabilities for getting Unix-style file permissions
        // This is a simplified version that checks basic permissions
        val canRead = if (file.canRead()) 'r' else '-'
        val canWrite = if (file.canWrite()) 'w' else '-'
        val canExecute = if (file.canExecute()) 'x' else '-'

        // For simplicity, we'll use the same permissions for user, group, and others
        return "$canRead$canWrite$canExecute$canRead-$canExecute$canRead-$canExecute"
    }

    /**
     * Handles reading special file types that require conversion or OCR. Returns a ToolResult
     * if the file type is special, otherwise null.
     */
    protected open suspend fun handleSpecialFileRead(
        tool: AITool,
        path: String,
        fileExt: String
    ): ToolResult? {
        return when (fileExt) {
            "doc", "docx" -> {
                AppLogger.d(
                    TAG,
                    "Detected Word document, attempting to extract text"
                )
                val tempFilePath =
                    "${path}_converted_${System.currentTimeMillis()}.txt"
                try {
                    val sourceFile = File(path)
                    val tempFile = File(tempFilePath)
                    val success = com.ai.assistance.operit.util.DocumentConversionUtil
                        .extractTextFromWord(sourceFile, tempFile, fileExt)

                    if (success && tempFile.exists()) {
                        AppLogger.d(
                            TAG,
                            "Successfully extracted text from Word document"
                        )
                        val content = tempFile.readText()
                        tempFile.delete() // Clean up
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content = content,
                                size = content.length.toLong(),
                            ),
                            error = ""
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "Word text extraction failed, returning error"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to extract text from Word document"
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during Word document text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error extracting text from Word document: ${e.message}"
                    )
                }
            }

            "pdf" -> {
                AppLogger.d(
                    TAG,
                    "Detected PDF document, attempting to extract text"
                )
                val tempFilePath =
                    "${path}_converted_${System.currentTimeMillis()}.txt"
                try {
                    val sourceFile = File(path)
                    val tempFile = File(tempFilePath)
                    val success = com.ai.assistance.operit.util.DocumentConversionUtil
                        .extractTextFromPdf(context, sourceFile, tempFile)

                    if (success && tempFile.exists()) {
                        AppLogger.d(
                            TAG,
                            "Successfully extracted text from PDF document"
                        )
                        val content = tempFile.readText()
                        tempFile.delete() // Clean up
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content = content,
                                size = content.length.toLong(),
                            ),
                            error = ""
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "PDF text extraction failed, returning error"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to extract text from PDF document"
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during PDF document text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error extracting text from PDF document: ${e.message}"
                    )
                }
            }

            "jpg", "jpeg", "png", "gif", "bmp" -> {
                // 获取可选的intent参数和direct_image参数
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directImage = tool.parameters.find { it.name == "direct_image" }?.value?.toBoolean() ?: false

                AppLogger.d(
                    TAG,
                    "Detected image file, intent=${intent ?: "无"}, direct_image=$directImage"
                )

                // 情况1：direct_image 为 true，直接返回图片链接，供支持识图的聊天模型自己查看
                if (directImage) {
                    try {
                        val imageId = ImagePoolManager.addImage(path)
                        if (imageId == "error") {
                            AppLogger.e(TAG, "Failed to register image for direct_image, falling back to intent/OCR: $path")
                        } else {
                            val link = "<link type=\"image\" id=\"$imageId\"></link>"
                            AppLogger.d(TAG, "Generated image link for direct_image: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct image link, falling back to intent/OCR", e)
                    }
                    // 如果生成图片链接失败，则继续走下面的 intent/OCR 逻辑
                }

                // 情况2：提供了 intent，使用后端识图模型
                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService =
                            com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeImageWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "识图模型调用失败，回退到OCR", e)
                        // 回退到默认OCR处理
                    }
                }

                // 情况3：默认OCR处理
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(path)
                    }
                    if (bitmap != null) {
                        val ocrText =
                            kotlinx.coroutines.runBlocking {
                                com.ai.assistance.operit.util
                                    .OCRUtils.recognizeText(
                                        context,
                                        bitmap
                                    )
                            }
                        if (ocrText.isNotBlank()) {
                            AppLogger.d(
                                TAG,
                                "Successfully extracted text from image using OCR"
                            )
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                FileContentData(
                                    path = path,
                                    content = ocrText,
                                    size =
                                    ocrText.length
                                        .toLong()
                                ),
                                error = ""
                            )
                        } else {
                            AppLogger.w(
                                TAG,
                                "OCR extraction returned empty text, returning no text detected message"
                            )
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                FileContentData(
                                    path = path,
                                    content =
                                    "No text detected in image.",
                                    size =
                                    "No text detected in image."
                                        .length
                                        .toLong()
                                ),
                                error = ""
                            )
                        }
                    } else {
                        AppLogger.w(
                            TAG,
                            "Failed to decode image file, returning error message"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content =
                                "Failed to decode image file.",
                                size =
                                "Failed to decode image file."
                                    .length
                                    .toLong()
                            ),
                            error = ""
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during OCR text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileContentData(
                            path = path,
                            content =
                            "Error extracting text from image: ${e.message}",
                            size =
                            "Error extracting text from image: ${e.message}"
                                .length.toLong()
                        ),
                        error = ""
                    )
                }
            }

            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus" -> {
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directAudio = tool.parameters.find { it.name == "direct_audio" }?.value?.toBoolean() ?: false

                AppLogger.d(TAG, "Detected audio file, intent=${intent ?: "无"}, direct_audio=$directAudio")

                if (directAudio) {
                    try {
                        val derivedMimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt)
                                ?: "audio/*"
                        val audioId = MediaPoolManager.addMedia(path, derivedMimeType)
                        if (audioId == "error") {
                            AppLogger.e(TAG, "Failed to register audio for direct_audio, falling back to intent/info: $path")
                        } else {
                            val link = "<link type=\"audio\" id=\"$audioId\"></link>"
                            AppLogger.d(TAG, "Generated audio link for direct_audio: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct audio link, falling back to intent/info", e)
                    }
                }

                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService = com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeAudioWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "音频识别模型调用失败，回退到媒体信息", e)
                    }
                }

                val file = File(path)
                val mediaInfo = FFmpegUtil.getMediaInfo(path)
                val infoText = buildString {
                    appendLine("Audio file info:")
                    appendLine("- path: $path")
                    appendLine("- size_bytes: ${file.length()}")
                    appendLine("- extension: $fileExt")
                    if (mediaInfo != null) {
                        appendLine("- format: ${mediaInfo.format}")
                        appendLine("- duration: ${mediaInfo.duration}")
                        appendLine("- bitrate: ${mediaInfo.bitrate}")
                        val streams = mediaInfo.streams
                        if (!streams.isNullOrEmpty()) {
                            val audioStreams = streams.filter { it.type.equals("audio", ignoreCase = true) }
                            appendLine("- audio_streams: ${audioStreams.size}")
                        }
                    }
                }.trim()

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = infoText,
                        size = infoText.length.toLong()
                    ),
                    error = ""
                )
            }

            "mp4", "mkv", "mov", "webm", "avi", "m4v" -> {
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directVideo = tool.parameters.find { it.name == "direct_video" }?.value?.toBoolean() ?: false

                AppLogger.d(TAG, "Detected video file, intent=${intent ?: "无"}, direct_video=$directVideo")

                if (directVideo) {
                    try {
                        val derivedMimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt)
                                ?: "video/*"
                        val videoId = MediaPoolManager.addMedia(path, derivedMimeType)
                        if (videoId == "error") {
                            AppLogger.e(TAG, "Failed to register video for direct_video, falling back to intent/info: $path")
                        } else {
                            val link = "<link type=\"video\" id=\"$videoId\"></link>"
                            AppLogger.d(TAG, "Generated video link for direct_video: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct video link, falling back to intent/info", e)
                    }
                }

                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService = com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeVideoWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "视频识别模型调用失败，回退到媒体信息", e)
                    }
                }

                val file = File(path)
                val mediaInfo = FFmpegUtil.getMediaInfo(path)
                val infoText = buildString {
                    appendLine("Video file info:")
                    appendLine("- path: $path")
                    appendLine("- size_bytes: ${file.length()}")
                    appendLine("- extension: $fileExt")
                    if (mediaInfo != null) {
                        appendLine("- format: ${mediaInfo.format}")
                        appendLine("- duration: ${mediaInfo.duration}")
                        appendLine("- bitrate: ${mediaInfo.bitrate}")
                        val streams = mediaInfo.streams
                        if (!streams.isNullOrEmpty()) {
                            val videoStreams = streams.filter { it.type.equals("video", ignoreCase = true) }
                            val audioStreams = streams.filter { it.type.equals("audio", ignoreCase = true) }
                            appendLine("- video_streams: ${videoStreams.size}")
                            appendLine("- audio_streams: ${audioStreams.size}")
                        }
                    }
                }.trim()

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = infoText,
                        size = infoText.length.toLong()
                    ),
                    error = ""
                )
            }

            else -> null
        }
    }

    /**
     * Reads the full content of a file as a new tool, handling different file types. This
     * function does not enforce a size limit.
     */
    open suspend fun readFileFull(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val textOnly = tool.parameters.find { it.name == "text_only" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFileFull(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!file.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = file.extension.lowercase()
            
            // 如果启用了 text_only 模式，检查文件是否为文本
            if (textOnly) {
                // 读取文件前 512 字节进行判断
                if (!FileUtils.isTextLike(file, 512)) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Skipped non-text file: $path"
                    )
                }
            }

            // 尝试特殊文件处理
            if (isSpecialFileType(fileExt)) {
                val specialReadResult = handleSpecialFileRead(tool, path, fileExt)
                if (specialReadResult != null) {
                    return specialReadResult
                }
            }

            // Check if file is text-like by analyzing content
            if (FileUtils.isTextLike(file)) {
                val content = file.readText()
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileContentData(
                        path = path,
                        content = content,
                        size = file.length()
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not appear to be a text file. Use specialized tools for binary files."
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file (full)", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** Read binary file content and return base64-encoded data */
    open suspend fun readFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFileBinary(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist or is not a regular file: $path"
                    )
                }

                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BinaryFileContentData(
                        path = path,
                        contentBase64 = base64,
                        size = file.length()
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reading binary file", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading binary file: ${e.message}"
                )
            }
        }
    }

    /** Read file content, truncated to configured max size */
    open suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            // 从配置中获取最大文件大小
            val maxFileSizeBytes = apiPreferences.getMaxFileSizeBytes()

            val file = File(path)
            if (!file.exists() || !file.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = file.extension.lowercase()

            // For special types, full read then truncate text is the only way.
            if (isSpecialFileType(fileExt)) {
                val fullResult = readFileFull(tool)
                if (!fullResult.success) return fullResult

                val contentData = fullResult.result as FileContentData
                var content = contentData.content
                val isTruncated = content.length > maxFileSizeBytes
                if (isTruncated) {
                    content = content.substring(0, maxFileSizeBytes)
                }

                var contentWithLineNumbers = addLineNumbers(content)
                if (isTruncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileContentData(
                        path = path,
                        content = contentWithLineNumbers,
                        size = contentWithLineNumbers.length.toLong()
                    ),
                    error = ""
                )
            }

            // For text-based files, read only the beginning.
            // Check if file is text-like by analyzing content
            if (!FileUtils.isTextLike(file)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                    "File does not appear to be a text file. Use readFileFull tool for special file types."
                )
            }

            val content =
                file.bufferedReader().use {
                    val buffer = CharArray(maxFileSizeBytes)
                    val charsRead = it.read(buffer, 0, maxFileSizeBytes)
                    if (charsRead > 0) String(buffer, 0, charsRead) else ""
                }

            val truncated = file.length() > maxFileSizeBytes
            var finalContent = addLineNumbers(content)
            if (truncated) {
                finalContent += "\n\n... (file content truncated) ..."
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileContentData(
                    path = path,
                    content = finalContent,
                    size = finalContent.length.toLong()
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 按行号范围读取文件内容（行号从1开始，包括开始行和结束行） */
    open suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val startLineParam = tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull() ?: 1
        val endLineParam = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFilePart(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist or is not a regular file: $path"
                    )
                }

                // 1. 准备数据源 & 获取总行数
                // 如果是特殊文件，先提取所有文本到内存；如果是普通文件，只统计行数
                val inMemoryLines: List<String>?
                val totalLines: Int

                if (isSpecialFileType(file.extension)) {
                    val specialResult = handleSpecialFileRead(tool, path, file.extension.lowercase())
                    if (specialResult != null && !specialResult.success) return@withContext specialResult

                    if (specialResult != null) {
                        val content = (specialResult.result as FileContentData).content
                        inMemoryLines = content.lines()
                        totalLines = inMemoryLines.size
                    } else {
                        // Fallback if handleSpecialFileRead returns null (shouldn't happen given check)
                        inMemoryLines = null
                        totalLines = 0
                    }
                } else {
                    inMemoryLines = null
                    totalLines = countFileLines(file)
                }

                // 2. 计算实际的行号范围（行号从1开始，转换为0-based索引）
                val startLine = maxOf(1, startLineParam).coerceIn(1, maxOf(1, totalLines))
                val endLine = (endLineParam ?: (startLine + 99)).coerceIn(startLine, maxOf(1, totalLines))
                
                // 转换为0-based索引
                val startIndex = startLine - 1
                val endIndex = endLine // endLine 本身就是最后一行的1-based行号，转成exclusive的end需要不减1

                // 3. 获取分段内容
                val partContent = if (inMemoryLines != null) {
                    // 内存模式：直接切片
                    if (totalLines > 0 && startIndex < totalLines) {
                        inMemoryLines.subList(startIndex, minOf(endIndex, totalLines)).joinToString("\n")
                    } else ""
                } else {
                    // 磁盘流模式：读取指定行
                    if (totalLines > 0) readLinesFromFile(file, startIndex, endIndex) else ""
                }

                // 4. 封装返回结果
                val contentWithLineNumbers = addLineNumbers(partContent, startIndex, totalLines)

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FilePartContentData(
                        path = path,
                        content = contentWithLineNumbers,
                        partIndex = 0, // 保留兼容性，但不再使用
                        totalParts = 1, // 保留兼容性，但不再使用
                        startLine = startIndex,
                        endLine = minOf(endIndex, totalLines),
                        totalLines = totalLines
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reading file part", e)
                return@withContext ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading file part: ${e.message}"
                )
            }
        }
    }

    /** Write content to a file */
    open suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append =
            tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.writeFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)

                // Create parent directories if needed
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        AppLogger.w(
                            TAG,
                            "Failed to create parent directory: ${parentDir.absolutePath}"
                        )
                    }
                }

                // Write content to file
                if (append && file.exists()) {
                    file.appendText(content)
                } else {
                    file.writeText(content)
                }

                // Verify write was successful
                if (!file.exists()) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation =
                            if (append) "append" else "write",
                            path = path,
                            successful = false,
                            details =
                            "Write completed but file does not exist. Possible permission issue."
                        ),
                        error =
                        "Write completed but file does not exist. Possible permission issue."
                    )
                }

                if (file.length() == 0L && content.isNotEmpty()) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation =
                            if (append) "append" else "write",
                            path = path,
                            successful = false,
                            details =
                            "File was created but appears to be empty. Possible write failure."
                        ),
                        error =
                        "File was created but appears to be empty. Possible write failure."
                    )
                }

                val operation = if (append) "append" else "write"
                val details =
                    if (append) "Content appended to $path"
                    else "Content written to $path"

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = operation,
                        path = path,
                        successful = true,
                        details = details
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error writing to file", e)

                val errorMessage =
                    when {
                        e is IOException ->
                            "File I/O error: ${e.message}. Please check if the path has write permissions."

                        e.message?.contains("permission", ignoreCase = true) ==
                                true ->
                            "Permission denied, cannot write to file: ${e.message}. Please check if the app has proper permissions."

                        else -> "Error writing to file: ${e.message}"
                    }

                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = if (append) "append" else "write",
                        path = path,
                        successful = false,
                        details = errorMessage
                    ),
                    error = errorMessage
                )
            }
        }
    }

    /** Write base64 encoded content to a binary file */
    open suspend fun writeFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.writeFileBinary(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        if (base64Content.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = false,
                    details = "base64Content parameter is required"
                ),
                error = "base64Content parameter is required"
            )
        }

        return try {
            val file = File(path)

            // Create parent directories if needed
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    AppLogger.w(
                        TAG,
                        "Failed to create parent directory: ${parentDir.absolutePath}"
                    )
                }
            }

            // Decode base64 and write bytes
            val decodedBytes =
                android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            file.writeBytes(decodedBytes)

            // Verify write was successful
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "write_binary",
                        path = path,
                        successful = false,
                        details =
                        "Write completed but file does not exist. Possible permission issue."
                    ),
                    error =
                    "Write completed but file does not exist. Possible permission issue."
                )
            }

            if (file.length() == 0L && decodedBytes.isNotEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "write_binary",
                        path = path,
                        successful = false,
                        details =
                        "File was created but appears to be empty. Possible write failure."
                    ),
                    error =
                    "File was created but appears to be empty. Possible write failure."
                )
            }

            val details = "Binary content written to $path"

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = true,
                    details = details
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing binary file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = false,
                    details = "Error writing binary file: ${e.message}"
                ),
                error = "Error writing binary file: ${e.message}"
            )
        }
    }

    /** Delete a file or directory */
    open suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val recursive =
            tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.deleteFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "delete",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }


        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details =
                        "File or directory does not exist: $path"
                    ),
                    error = "File or directory does not exist: $path"
                )
            }

            var success = false

            if (file.isDirectory) {
                if (recursive) {
                    success = file.deleteRecursively()
                } else {
                    // Only delete if directory is empty
                    val files = file.listFiles() ?: emptyArray()
                    if (files.isEmpty()) {
                        success = file.delete()
                    } else {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                            FileOperationData(
                                operation = "delete",
                                path = path,
                                successful = false,
                                details =
                                "Directory is not empty and recursive flag is not set"
                            ),
                            error =
                            "Directory is not empty and recursive flag is not set"
                        )
                    }
                }
            } else {
                success = file.delete()
            }

            if (success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = true,
                        details = "Successfully deleted $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details =
                        "Failed to delete: permission denied or file in use"
                    ),
                    error = "Failed to delete: permission denied or file in use"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting file/directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = false,
                    details =
                    "Error deleting file/directory: ${e.message}"
                ),
                error = "Error deleting file/directory: ${e.message}"
            )
        }
    }

    /** Check if a file or directory exists */
    open suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.fileExists(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            val exists = file.exists()

            if (!exists) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileExistsData(path = path, exists = false),
                    error = ""
                )
            }

            val isDirectory = file.isDirectory
            val size = file.length()

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileExistsData(
                    path = path,
                    exists = true,
                    isDirectory = isDirectory,
                    size = size
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking file existence", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileExistsData(
                    path = path,
                    exists = false,
                    isDirectory = false,
                    size = 0
                ),
                error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** Move or rename a file or directory */
    open suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.moveFile(tool)
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }


        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Source file does not exist: $sourcePath"
                    ),
                    error = "Source file does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            // Perform move operation
            if (sourceFile.renameTo(destFile)) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = true,
                        details =
                        "Successfully moved $sourcePath to $destPath"
                    ),
                    error = ""
                )
            } else {
                // If simple rename fails, try copy and delete (could be across
                // filesystems)
                if (sourceFile.isDirectory) {
                    // For directories, use directory copy utility
                    val copySuccess = copyDirectory(sourceFile, destFile)
                    if (copySuccess && sourceFile.deleteRecursively()) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "move",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                            ),
                            error = ""
                        )
                    }
                } else {
                    // For files, copy the content then delete original
                    sourceFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (destFile.exists() &&
                        destFile.length() == sourceFile.length() &&
                        sourceFile.delete()
                    ) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "move",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                            ),
                            error = ""
                        )
                    }
                }

                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Failed to move file: possibly a permissions issue or destination already exists"
                    ),
                    error =
                    "Failed to move file: possibly a permissions issue or destination already exists"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error moving file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Error moving file: ${e.message}"
                ),
                error = "Error moving file: ${e.message}"
            )
        }
    }

    /** Helper method to recursively copy a directory */
    private fun copyDirectory(sourceDir: File, destDir: File): Boolean {
        try {
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            sourceDir.listFiles()?.forEach { file ->
                val destFile = File(destDir, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    file.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying directory", e)
            return false
        }
    }

    /**
     * 跨环境复制文件或目录
     * 支持 Android <-> Linux 之间的文件复制
     */
    private suspend fun copyFileCrossEnvironment(
        toolName: String,
        sourcePath: String,
        destPath: String,
        sourceEnvironment: String,
        destEnvironment: String,
        recursive: Boolean
    ): ToolResult {
        // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
        val finalDestPath = destPath

        return try {
            AppLogger.d(
                TAG,
                "Cross-environment copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath"
            )

            // 1. 检查源文件是否存在
            val sourceExists = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().exists(sourcePath)
            } else {
                File(sourcePath).exists()
            }

            if (!sourceExists) {
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to read source file"
                    ),
                    error = "Failed to read source file"
                )
            }

            // 2. 检查是否是目录
            val isDirectory = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().isDirectory(sourcePath)
            } else {
                File(sourcePath).isDirectory
            }

            if (isDirectory) {
                if (!recursive) {
                    return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details = "Cannot copy directory without recursive flag"
                        ),
                        error = "Cannot copy directory without recursive flag"
                    )
                }

                // 目录复制：递归复制所有文件
                return copyDirectoryCrossEnvironment(
                    toolName,
                    sourcePath,
                    finalDestPath,
                    sourceEnvironment,
                    destEnvironment
                )
            }

            // 3. 获取文件大小
            val fileSize = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().getFileSize(sourcePath)
            } else {
                File(sourcePath).length()
            }

            // 4. 统一分块传输（10MB 缓冲）
            val BUFFER_SIZE = 10 * 1024 * 1024
            var totalBytes = 0L

            if (isLinuxEnvironment(sourceEnvironment)) {
                // 从 Linux 读取并写入
                val content = getLinuxFileSystem().readFile(sourcePath) ?: return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to read source file"
                    ),
                    error = "Failed to read source file"
                )
                val bytes = content.toByteArray(Charsets.UTF_8)

                if (isLinuxEnvironment(destEnvironment)) {
                    val result = getLinuxFileSystem().writeFileBytes(finalDestPath, bytes)
                    if (!result.success) {
                        return ToolResult(
                            toolName = toolName,
                            success = false,
                            result = FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = false,
                                details = result.message
                            ),
                            error = result.message
                        )
                    }
                } else {
                    File(finalDestPath).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                }
                totalBytes = bytes.size.toLong()
            } else {
                // 从 Android 读取并写入
                val sourceFile = File(sourcePath)
                sourceFile.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    val outputStream = if (isLinuxEnvironment(destEnvironment)) {
                        java.io.ByteArrayOutputStream()
                    } else {
                        File(finalDestPath).apply { parentFile?.mkdirs() }.outputStream()
                    }

                    outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                    }

                    if (isLinuxEnvironment(destEnvironment)) {
                        val bytes = (outputStream as java.io.ByteArrayOutputStream).toByteArray()
                        val result = getLinuxFileSystem().writeFileBytes(finalDestPath, bytes)
                        if (!result.success) {
                            return ToolResult(
                                toolName = toolName,
                                success = false,
                                result = FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = result.message
                                ),
                                error = result.message
                            )
                        }
                    }
                }
            }

            // 5. 验证成功
            AppLogger.d(TAG, "Successfully copied file cross-environment: $totalBytes bytes")
            return ToolResult(
                toolName = toolName,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = true,
                    details = "Successfully copied file from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($totalBytes bytes)"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file cross-environment", e)
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Error copying file cross-environment: ${e.message}"
                ),
                error = "Error copying file cross-environment: ${e.message}"
            )
        }
    }

    /**
     * 跨环境递归复制目录
     */
    private suspend fun copyDirectoryCrossEnvironment(
        toolName: String,
        sourcePath: String,
        destPath: String,
        sourceEnvironment: String,
        destEnvironment: String
    ): ToolResult {
        // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
        val finalDestPath = destPath

        return try {
            AppLogger.d(
                TAG,
                "Cross-environment directory copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath"
            )

            // 1. 创建目标目录
            if (isLinuxEnvironment(destEnvironment)) {
                val result = getLinuxFileSystem().createDirectory(finalDestPath, createParents = true)
                if (!result.success) {
                    return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details = "Failed to create destination directory: ${result.message}"
                        ),
                        error = "Failed to create destination directory: ${result.message}"
                    )
                }
            } else {
                val destDir = File(finalDestPath)
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
            }

            // 2. 列出源目录内容
            val entries = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().listDirectory(sourcePath)?.map { fileInfo ->
                    Pair(fileInfo.name, fileInfo.isDirectory)
                } ?: emptyList()
            } else {
                File(sourcePath).listFiles()?.map { file ->
                    Pair(file.name, file.isDirectory)
                } ?: emptyList()
            }

            // 3. 递归复制每个条目
            var copiedFiles = 0
            var copiedDirs = 0
            for ((name, isDir) in entries) {
                val srcFullPath =
                    if (sourcePath.endsWith("/")) "$sourcePath$name" else "$sourcePath/$name"
                val dstFullPath =
                    if (finalDestPath.endsWith("/")) "$finalDestPath$name" else "$finalDestPath/$name"

                if (isDir) {
                    val result = copyDirectoryCrossEnvironment(
                        toolName,
                        srcFullPath,
                        dstFullPath,
                        sourceEnvironment,
                        destEnvironment
                    )
                    if (result.success) {
                        copiedDirs++
                    } else {
                        AppLogger.w(TAG, "Failed to copy directory: $srcFullPath")
                    }
                } else {
                    val result = copyFileCrossEnvironment(
                        toolName,
                        srcFullPath,
                        dstFullPath,
                        sourceEnvironment,
                        destEnvironment,
                        recursive = false
                    )
                    if (result.success) {
                        copiedFiles++
                    } else {
                        AppLogger.w(TAG, "Failed to copy file: $srcFullPath")
                    }
                }
            }

            AppLogger.d(
                TAG,
                "Successfully copied directory: $copiedFiles files, $copiedDirs subdirectories"
            )
            return ToolResult(
                toolName = toolName,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = true,
                    details = "Successfully copied directory from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($copiedFiles files, $copiedDirs subdirectories)"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying directory cross-environment", e)
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Error copying directory cross-environment: ${e.message}"
                ),
                error = "Error copying directory cross-environment: ${e.message}"
            )
        }
    }

    /** Copy a file or directory */
    open suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val sourceEnvironment = tool.parameters.find { it.name == "source_environment" }?.value
        val destEnvironment = tool.parameters.find { it.name == "dest_environment" }?.value
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val recursive =
            tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }

        // 确定源和目标环境
        val srcEnv = sourceEnvironment ?: environment ?: "android"
        val dstEnv = destEnvironment ?: environment ?: "android"

        // 检查是否是跨环境复制
        val isCrossEnvironment = srcEnv.lowercase() != dstEnv.lowercase()

        // 如果是跨环境复制，使用特殊处理
        if (isCrossEnvironment) {
            return copyFileCrossEnvironment(
                toolName = tool.name,
                sourcePath = sourcePath,
                destPath = destPath,
                sourceEnvironment = srcEnv,
                destEnvironment = dstEnv,
                recursive = recursive
            )
        }

        // 同环境复制 - 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(srcEnv)) {
            return linuxTools.copyFile(
                AITool(
                    name = tool.name,
                    parameters = listOf(
                        ToolParameter("source", sourcePath),
                        ToolParameter("destination", destPath),
                        ToolParameter("recursive", recursive.toString())
                    )
                )
            )
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        // Android环境内复制
        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Source path does not exist: $sourcePath"
                    ),
                    error = "Source path does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            if (sourceFile.isDirectory) {
                if (recursive) {
                    val success = copyDirectory(sourceFile, destFile)
                    if (success) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully copied directory $sourcePath to $destPath"
                            ),
                            error = ""
                        )
                    } else {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                            FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = false,
                                details =
                                "Failed to copy directory: possible permission issue"
                            ),
                            error =
                            "Failed to copy directory: possible permission issue"
                        )
                    }
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details =
                            "Cannot copy directory without recursive flag"
                        ),
                        error =
                        "Cannot copy directory without recursive flag"
                    )
                }
            } else {
                // Copy file
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify copy was successful
                if (destFile.exists() && destFile.length() == sourceFile.length()) {
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = true,
                            details =
                            "Successfully copied file $sourcePath to $destPath"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details =
                            "Copy operation completed but verification failed"
                        ),
                        error =
                        "Copy operation completed but verification failed"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file/directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Error copying file/directory: ${e.message}"
                ),
                error = "Error copying file/directory: ${e.message}"
            )
        }
    }

    /** Create a directory */
    open suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val createParents =
            tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean()
                ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.makeDirectory(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "mkdir",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            // Check if directory already exists
            if (directory.exists()) {
                if (directory.isDirectory) {
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "mkdir",
                            path = path,
                            successful = true,
                            details =
                            "Directory already exists: $path"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "mkdir",
                            path = path,
                            successful = false,
                            details =
                            "Path exists but is not a directory: $path"
                        ),
                        error = "Path exists but is not a directory: $path"
                    )
                }
            }

            // Create directory
            val success =
                if (createParents) {
                    directory.mkdirs()
                } else {
                    directory.mkdir()
                }

            if (success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = true,
                        details =
                        "Successfully created directory $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = false,
                        details =
                        "Failed to create directory: parent directory may not exist or permission denied"
                    ),
                    error =
                    "Failed to create directory: parent directory may not exist or permission denied"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "mkdir",
                    path = path,
                    successful = false,
                    details = "Error creating directory: ${e.message}"
                ),
                error = "Error creating directory: ${e.message}"
            )
        }
    }

    /** Search for files matching a pattern */
    open suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.findFiles(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList(),
                    env = "android"
                ),
                error = "Path and pattern parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, 0f, "Searching...")
            val rootDir = File(path)

            if (!rootDir.exists() || !rootDir.isDirectory) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FindFilesResultData(
                        path = path,
                        pattern = pattern,
                        files = emptyList(),
                        env = "android"
                    ),
                    error = "Path does not exist or is not a directory: $path"
                )
            }

            // Get search options
            val usePathPattern =
                tool.parameters
                    .find { it.name == "use_path_pattern" }
                    ?.value
                    ?.toBoolean()
                    ?: false
            val caseInsensitive =
                tool.parameters
                    .find { it.name == "case_insensitive" }
                    ?.value
                    ?.toBoolean()
                    ?: false
            val maxDepth =
                tool.parameters
                    .find { it.name == "max_depth" }
                    ?.value
                    ?.toIntOrNull()
                    ?: -1

            // Convert glob pattern to regex
            val regex = globToRegex(pattern, caseInsensitive)

            // Recursively find matching files
            val matchingFiles = mutableListOf<String>()
            val progress = FindFilesProgressState(lastPath = rootDir.absolutePath)
            findMatchingFiles(
                rootDir,
                regex,
                matchingFiles,
                usePathPattern,
                maxDepth,
                0,
                rootDir.absolutePath,
                tool.name,
                progress
            )

            ToolProgressBus.update(tool.name, 1f, "Search completed, found ${matchingFiles.size}")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = matchingFiles,
                    env = "android"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error searching for files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList(),
                    env = "android"
                ),
                error = "Error searching for files: ${e.message}"
            )
        }
    }

    /** Helper method to convert glob pattern to regex */
    private fun globToRegex(glob: String, caseInsensitive: Boolean): Regex {
        val regex = StringBuilder("^")

        for (i in glob.indices) {
            val c = glob[i]
            when (c) {
                '*' -> regex.append(".*")
                '?' -> regex.append(".")
                '.' -> regex.append("\\.")
                '\\' -> regex.append("\\\\")
                '[' -> regex.append("[")
                ']' -> regex.append("]")
                '(' -> regex.append("\\(")
                ')' -> regex.append("\\)")
                '{' -> regex.append("(")
                '}' -> regex.append(")")
                ',' -> regex.append("|")
                else -> regex.append(c)
            }
        }

        regex.append("$")

        return if (caseInsensitive) {
            Regex(regex.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(regex.toString())
        }
    }

    private data class FindFilesProgressState(
        var visited: Int = 0,
        var discovered: Int = 1,
        var matched: Int = 0,
        var progressFloor: Float = 0f,
        var lastUpdateTimeMs: Long = 0L,
        var lastPath: String = ""
    )

    /** Helper method to recursively find files matching a pattern */
    private fun findMatchingFiles(
        dir: File,
        regex: Regex,
        results: MutableList<String>,
        usePathPattern: Boolean,
        maxDepth: Int,
        currentDepth: Int,
        rootPath: String,
        toolName: String,
        progress: FindFilesProgressState
    ) {
        if (maxDepth >= 0 && currentDepth > maxDepth) {
            return
        }

        val files = dir.listFiles() ?: return

        progress.visited++
        progress.discovered += files.size
        progress.lastPath = dir.absolutePath

        val now = System.currentTimeMillis()
        if (progress.lastUpdateTimeMs == 0L) {
            progress.lastUpdateTimeMs = now
        }
        if (now - progress.lastUpdateTimeMs >= 500L || progress.visited % 200 == 0) {
            val candidate =
                if (progress.discovered > 0) {
                    (progress.visited.toFloat() / progress.discovered.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            if (candidate > progress.progressFloor) {
                progress.progressFloor = candidate
            }
            val rel =
                if (progress.lastPath.startsWith(rootPath)) {
                    progress.lastPath.removePrefix(rootPath).trimStart(File.separatorChar).ifBlank { "." }
                } else {
                    progress.lastPath
                }
            ToolProgressBus.update(
                toolName,
                progress.progressFloor,
                "Searching... scanned ${progress.visited}, found ${progress.matched} (at $rel)"
            )
            progress.lastUpdateTimeMs = now
        }

        for (file in files) {
            val relativePath = file.absolutePath.substring(rootPath.length + 1)

            val testString = if (usePathPattern) relativePath else file.name

            if (regex.matches(testString)) {
                results.add(file.absolutePath)
                progress.matched++
            }

            if (file.isDirectory) {
                if (maxDepth >= 0 && currentDepth + 1 > maxDepth) {
                    continue
                }
                findMatchingFiles(
                    file,
                    regex,
                    results,
                    usePathPattern,
                    maxDepth,
                    currentDepth + 1,
                    rootPath,
                    toolName,
                    progress
                )
            } else {
                progress.visited++
                progress.lastPath = file.absolutePath
                val nowFile = System.currentTimeMillis()
                if (nowFile - progress.lastUpdateTimeMs >= 500L || progress.visited % 200 == 0) {
                    val candidate =
                        if (progress.discovered > 0) {
                            (progress.visited.toFloat() / progress.discovered.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    if (candidate > progress.progressFloor) {
                        progress.progressFloor = candidate
                    }
                    val rel =
                        if (progress.lastPath.startsWith(rootPath)) {
                            progress.lastPath.removePrefix(rootPath).trimStart(File.separatorChar)
                        } else {
                            progress.lastPath
                        }
                    ToolProgressBus.update(
                        toolName,
                        progress.progressFloor,
                        "Searching... scanned ${progress.visited}, found ${progress.matched} (at $rel)"
                    )
                    progress.lastUpdateTimeMs = nowFile
                }
            }
        }
    }

    /** Get file information */
    open suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.fileInfo(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileInfoData(
                    path = "",
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "android"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileInfoData(
                        path = path,
                        exists = false,
                        fileType = "",
                        size = 0,
                        permissions = "",
                        owner = "",
                        group = "",
                        lastModified = "",
                        rawStatOutput = "",
                        env = "android"
                    ),
                    error = "File or directory does not exist: $path"
                )
            }

            // Get file type
            val fileType =
                when {
                    file.isDirectory -> "directory"
                    file.isFile -> "file"
                    else -> "other"
                }

            // Get permissions
            val permissions = getFilePermissions(file)

            // Owner and group info are not easily available in Java
            val owner = System.getProperty("user.name") ?: ""
            val group = ""

            // Last modified time
            val lastModified =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(Date(file.lastModified()))

            // Size
            val size = if (file.isFile) file.length() else 0

            // Collect all file info into a raw string
            val rawInfo = StringBuilder()
            rawInfo.append("File: $path\n")
            rawInfo.append("Size: $size bytes\n")
            rawInfo.append("Type: $fileType\n")
            rawInfo.append("Permissions: $permissions\n")
            rawInfo.append("Last Modified: $lastModified\n")
            rawInfo.append("Owner: $owner\n")
            if (file.canRead()) rawInfo.append("Access: Readable\n")
            if (file.canWrite()) rawInfo.append("Access: Writable\n")
            if (file.canExecute()) rawInfo.append("Access: Executable\n")
            if (file.isHidden()) rawInfo.append("Hidden: Yes\n")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileInfoData(
                    path = path,
                    exists = true,
                    fileType = fileType,
                    size = size,
                    permissions = permissions,
                    owner = owner,
                    group = group,
                    lastModified = lastModified,
                    rawStatOutput = rawInfo.toString(),
                    env = "android"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file information", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileInfoData(
                    path = path,
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "android"
                ),
                error = "Error getting file information: ${e.message}"
            )
        }
    }

    /** Zip files or directories */
    open suspend fun zipFiles(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(zipPath, tool.name, "destination")?.let { return it }

        val actualSourcePath = PathMapper.resolvePath(context, sourcePath, environment)
        val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)

        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }

        return try {
            val sourceFile = File(actualSourcePath)
            val destZipFile = File(actualZipPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                    "Source file or directory does not exist: $sourcePath"
                )
            }

            // Create parent directory for zip file if needed
            val zipDir = destZipFile.parentFile
            if (zipDir != null && !zipDir.exists()) {
                zipDir.mkdirs()
            }

            // Initialize buffer for file operations
            val buffer = ByteArray(1024)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zos ->
                if (sourceFile.isDirectory) {
                    // For directories, add all files recursively
                    addDirectoryToZip(sourceFile, sourceFile.name, zos)
                } else {
                    // For a single file, add it directly
                    val entryName = sourceFile.name
                    zos.putNextEntry(ZipEntry(entryName))

                    FileInputStream(sourceFile).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } >
                                0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            if (destZipFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "zip",
                        path = sourcePath,
                        successful = true,
                        details =
                        "Successfully compressed $sourcePath to $zipPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to create zip file"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error compressing files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error compressing files: ${e.message}"
            )
        }
    }

    /** Helper method to add directory contents to zip */
    private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
        val buffer = ByteArray(1024)
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryToZip(file, "$baseName/${file.name}", zos)
                continue
            }

            val entryName = "$baseName/${file.name}"
            zos.putNextEntry(ZipEntry(entryName))

            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }

            zos.closeEntry()
        }
    }

    /** Unzip a zip file */
    open suspend fun unzipFiles(tool: AITool): ToolResult {
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        PathValidator.validateAndroidPath(zipPath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)
        val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, -1f, "Preparing to unzip...")
            val zipFile = File(actualZipPath)
            val destDir = File(actualDestPath)

            if (!zipFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Zip file does not exist: $zipPath"
                )
            }

            if (!zipFile.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source path is not a file: $zipPath"
                )
            }

            // Create destination directory if needed
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val totalEntries = try {
                ZipFile(zipFile).use { it.size() }
            } catch (_: Exception) {
                null
            }
            var processedEntries = 0

            val destDirCanonical = destDir.canonicalPath + File.separator
            val buffer = ByteArray(64 * 1024)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry

                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    val newFile = File(destDir, fileName)

                    val newFileCanonical = newFile.canonicalPath
                    if (!newFileCanonical.startsWith(destDirCanonical)) {
                        throw SecurityException("Zip entry is outside of the target dir: $fileName")
                    }

                    // Create parent directories if needed
                    val parentDir = newFile.parentFile
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs()
                    }

                    if (zipEntry.isDirectory) {
                        // Create directory if it doesn't exist
                        if (!newFile.exists()) {
                            newFile.mkdirs()
                        }
                    } else {
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also {
                                        len = it
                                    } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                    processedEntries++
                    val progress =
                        if (totalEntries != null && totalEntries > 0) {
                            (processedEntries.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                    val msg =
                        if (totalEntries != null && totalEntries > 0) {
                            "Unzipping... ($processedEntries/$totalEntries)"
                        } else {
                            "Unzipping..."
                        }
                    ToolProgressBus.update(tool.name, progress, msg)
                    zipEntry = zis.nextEntry
                }
            }

            ToolProgressBus.update(tool.name, 1f, "Unzip completed")
            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "unzip",
                    path = zipPath,
                    successful = true,
                    details =
                    "Successfully extracted $zipPath to $destPath"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error extracting zip file: ${e.message}"
            )
        } finally {
            ToolProgressBus.clear()
        }
    }

    /**
     * 智能应用文件绑定，将AI生成的代码与原始文件内容智能合并 该工具会读取原始文件内容，应用AI生成的代码（通常包含//existing code标记）， 然后将合并后的内容写回文件
     */
    open fun applyFile(tool: AITool): Flow<ToolResult> = flow {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val aiGeneratedCode = tool.parameters.find { it.name == "content" }?.value ?: ""
        ToolProgressBus.update(tool.name, 0f, "Preparing...")
        try {
            PathValidator.validateAndroidPath(path, tool.name)?.let {
                emit(it)
                return@flow
            }

            if (path.isBlank()) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = "",
                            successful = false,
                            details = "Path parameter is required"
                        ),
                        error = "Path parameter is required"
                    )
                )
                return@flow
            }

            if (aiGeneratedCode.isBlank()) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = false,
                            details = "Content parameter is required"
                        ),
                        error = "Content parameter is required"
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.05f, "Checking file...")
            val fileExistsResult =
                fileExists(
                    AITool(
                        name = "file_exists", parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!fileExistsResult.success ||
                !(fileExistsResult.result as FileExistsData).exists
            ) {
                ToolProgressBus.update(tool.name, 0.4f, "Creating file...")
                AppLogger.d(TAG, "File does not exist. Creating new file '$path'...")

                val writeResult = writeFile(
                    AITool(
                        name = "write_file",
                        parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("content", aiGeneratedCode),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

                val diffContent = FileBindingService(context).generateUnifiedDiff("", aiGeneratedCode)

                if (writeResult.success) {
                    ToolProgressBus.update(tool.name, 1f, "Completed")
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileApplyResultData(
                                operation = FileOperationData(
                                    operation = "create",
                                    path = path,
                                    successful = true,
                                    details = "Successfully created new file: $path"
                                ),
                                aiDiffInstructions = "",
                                syntaxCheckResult = null,
                                diffContent = diffContent
                            )
                        )
                    )
                } else {
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(
                                operation = "create",
                                path = path,
                                successful = false,
                                details = "Failed to create new file: ${writeResult.error}"
                            ),
                            error = "Failed to create new file: ${writeResult.error}"
                        )
                    )
                }
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.15f, "Reading file...")
            val readResult =
                readFileFull(
                    AITool(
                        name = "read_file_full", parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!readResult.success) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = false,
                            details =
                            "Failed to read original file: ${readResult.error}"
                        ),
                        error = "Failed to read original file: ${readResult.error}"
                    )
                )
                return@flow
            }

            val originalContent = (readResult.result as? FileContentData)?.content ?: ""

            if (!aiGeneratedCode.contains("[START-")) {
                val errorMsg =
                    "如果你想覆盖这个文件，请删除文件后再写入;如果你想修改文件，请严格使用OLD/NEW的格式进行替换或者使用DELETE进行删除部分。" +
                    "所有补丁内容都必须写在 apply_file 的 content 参数里，并使用 [START-REPLACE]/[START-DELETE] + [OLD]/[NEW] 这样的结构化块，而不是直接输出整个文件的新内容进行覆盖。"
                AppLogger.w(
                    TAG,
                    "apply_file requested full overwrite without structured edit blocks for existing file: $path. $errorMsg"
                )
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = false,
                            details = errorMsg
                        ),
                        error = errorMsg
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.2f, "Applying patch...")
            val lastEmitMs = java.util.concurrent.atomic.AtomicLong(0L)
            val bindingResult =
                EnhancedAIService.applyFileBinding(context, originalContent, aiGeneratedCode) { p, msg ->
                    val now = System.currentTimeMillis()
                    val last = lastEmitMs.get()
                    if (now - last < 200L) return@applyFileBinding
                    if (!lastEmitMs.compareAndSet(last, now)) return@applyFileBinding

                    val mapped = (0.2f + 0.6f * p).coerceIn(0f, 0.99f)
                    ToolProgressBus.update(tool.name, mapped, msg)
                }
            val mergedContent = bindingResult.first
            val aiInstructions = bindingResult.second

            if (aiInstructions.startsWith("Error", ignoreCase = true)) {
                AppLogger.e(TAG, "File binding failed: $aiInstructions")
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = false,
                            details = "File binding failed: $aiInstructions"
                        ),
                        error = aiInstructions
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.85f, "Writing file...")
            val writeResult =
                writeFile(
                    AITool(
                        name = "write_file",
                        parameters =
                        listOf(
                            ToolParameter("path", path),
                            ToolParameter("content", mergedContent),
                            ToolParameter("append", "false"),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!writeResult.success) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = false,
                            details =
                            "Failed to write merged content: ${writeResult.error}"
                        ),
                        error = "Failed to write merged content: ${writeResult.error}"
                    )
                )
                return@flow
            }

            val operationData =
                FileOperationData(
                    operation = "apply",
                    path = path,
                    successful = true,
                    details = "Successfully applied AI code to file: $path"
                )

            ToolProgressBus.update(tool.name, 0.92f, "Checking syntax...")
            val syntaxCheckResult = performSyntaxCheck(path, mergedContent)
            ToolProgressBus.update(tool.name, 0.96f, "Generating diff...")
            val diffContent =
                FileBindingService(context).generateUnifiedDiff(originalContent, mergedContent)
            ToolProgressBus.update(tool.name, 1f, "Completed")

            emit(
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileApplyResultData(
                        operation = operationData,
                        aiDiffInstructions = aiInstructions,
                        syntaxCheckResult = syntaxCheckResult,
                        diffContent = diffContent
                    ),
                    error = ""
                )
            )
        } finally {
            ToolProgressBus.clear()
        }
    }
        .catch { e ->
            AppLogger.e(TAG, "Error applying file binding", e)
            val path = tool.parameters.find { it.name == "path" }?.value ?: "unknown"
            emit(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "apply",
                        path = path,
                        successful = false,
                        details = "Error applying file binding: ${e.message}"
                    ),
                    error = "Error applying file binding: ${e.message}"
                )
            )
        }

    /** Download file from URL */
    open suspend fun downloadFile(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

        if (url.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details =
                    "URL and destination parameters are required"
                ),
                error = "URL and destination parameters are required"
            )
        }

        // Validate URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details = "URL must start with http:// or https://"
                ),
                error = "URL must start with http:// or https://"
            )
        }

        return try {
            val destFile = File(actualDestPath)

            fun formatSize(bytes: Long): String {
                return when {
                    bytes > 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                    bytes > 1024 -> String.format("%.2f KB", bytes / 1024.0)
                    else -> "$bytes bytes"
                }
            }

            ToolProgressBus.update(tool.name, 0f, "Connecting...")
            try {
                val destParent = destFile.parentFile
                if (destParent != null && !destParent.exists()) {
                    destParent.mkdirs()
                }

                val lastEmitMs = java.util.concurrent.atomic.AtomicLong(0L)
                HttpMultiPartDownloader.download(url, destFile, threadCount = 4) { downloaded, total ->
                    val now = System.currentTimeMillis()
                    val last = lastEmitMs.get()
                    if (now - last < 200L) return@download
                    if (!lastEmitMs.compareAndSet(last, now)) return@download

                    val p = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else -1f
                    val msg =
                        if (total > 0L) {
                            val percent = ((downloaded.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                            "Downloading... $percent% (${formatSize(downloaded)}/${formatSize(total)})"
                        } else {
                            "Downloading... ${formatSize(downloaded)}"
                        }
                    ToolProgressBus.update(tool.name, p, msg)
                }
                ToolProgressBus.update(tool.name, 1f, "Completed")

                if (destFile.exists()) {
                    val fileSize = destFile.length()
                    val formattedSize = formatSize(fileSize)
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "download",
                            path = destPath,
                            successful = true,
                            details =
                            "File downloaded successfully: $url -> $destPath (file size: $formattedSize)"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "download",
                            path = destPath,
                            successful = false,
                            details =
                            "Download completed but file was not created"
                        ),
                        error = "Download completed but file was not created"
                    )
                }
            } finally {
                ToolProgressBus.clear()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error downloading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details = "Error downloading file: ${e.message}"
                ),
                error = "Error downloading file: ${e.message}"
            )
        }
    }

    /** Open file with system default app */
    open suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.openFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "open",
                        env = "android",
                        path = path,
                        successful = false,
                        details = "File does not exist: $path"
                    ),
                    error = "File does not exist: $path"
                )
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "*/*"

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            context.startActivity(intent)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = true,
                    details = "Request to open file sent to system: $path"
                ),
                error = ""
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(TAG, "No activity found to handle opening file: $path", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "No application found to open this file type."
                ),
                error = "No application found to open this file type."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error opening file", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "Error opening file: ${e.message}"
                ),
                error = "Error opening file: ${e.message}"
            )
        }
    }

    /**
     * Grep代码搜索工具 - 在指定目录中搜索包含指定模式的代码
     * 依赖 findFiles 和 readFileFull 函数，不直接使用 File 类
     */
    open suspend fun grepCode(tool: AITool): ToolResult {
        val startTime = System.currentTimeMillis()
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val caseInsensitive =
            tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
        val contextLines =
            tool.parameters.find { it.name == "context_lines" }?.value?.toIntOrNull() ?: 3
        val maxResults =
            tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 100

        AppLogger.d(TAG, "grep_code: Starting search - path=$path, pattern=\"$pattern\", file_pattern=$filePattern, max_results=$maxResults")

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.grepCode(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Pattern parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                // 1. 使用 findFiles 查找所有匹配的文件
                val findFilesResult = findFiles(
                    AITool(
                        name = "find_files",
                        parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("pattern", filePattern),
                            ToolParameter("use_path_pattern", "false"),
                            ToolParameter("case_insensitive", "false"),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

                if (!findFilesResult.success) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to find files: ${findFilesResult.error}"
                    )
                }

                val foundFiles = (findFilesResult.result as FindFilesResultData).files
                AppLogger.d(TAG, "grep_code: Found ${foundFiles.size} files to search")

                if (foundFiles.isEmpty()) {
                    AppLogger.d(TAG, "grep_code: No files found matching pattern")
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = GrepResultData(
                            searchPath = path,
                            pattern = pattern,
                            matches = emptyList(),
                            totalMatches = 0,
                            filesSearched = 0
                        ),
                        error = ""
                    )
                }

                // 2. 创建正则表达式用于匹配
                val regex = if (caseInsensitive) {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                } else {
                    Regex(pattern)
                }

                // 3. 遍历每个文件，搜索匹配的行
                val fileMatches = mutableListOf<GrepResultData.FileMatch>()
                var totalMatches = 0
                var filesSearched = 0

                for (filePath in foundFiles) {
                    if (totalMatches >= maxResults) {
                        AppLogger.d(TAG, "grep_code: Reached max results limit ($maxResults), stopping search")
                        break
                    }

                    filesSearched++
                    val fileStartTime = System.currentTimeMillis()

                    // 读取文件内容（启用 text_only 模式，跳过图片等非文本文件）
                    val readResult = readFileFull(
                        AITool(
                            name = "read_file_full",
                            parameters = listOf(
                                ToolParameter("path", filePath),
                                ToolParameter("text_only", "true")
                            )
                        )
                    )

                    if (!readResult.success) {
                        // 如果读取失败（可能是二进制文件或权限问题），跳过该文件
                        AppLogger.d(TAG, "grep_code: Skipped file $filePath (${readResult.error})")
                        continue
                    }

                    val fileContent = (readResult.result as FileContentData).content
                    val lines = fileContent.lines()
                    
                    // 使用 regex.findAll 在整个文件内容上一次性找到所有匹配，比逐行遍历更高效
                    // 对于大文件，考虑限制处理大小或分块处理
                    val matches = if (fileContent.length > 10_000_000) { // 10MB limit
                        // 对于超大文件，只处理前10MB以避免ANR
                        val limitedContent = fileContent.take(10_000_000)
                        regex.findAll(limitedContent)
                    } else {
                        regex.findAll(fileContent)
                    }
                val matchedLineNumbers = matches
                    .map { match ->
                        // 通过计算匹配位置之前的换行符数量得到行号
                        fileContent.substring(0, match.range.first).count { it == '\n' }
                    }
                    .distinct()
                    .sorted()
                    .toList()

                if (matchedLineNumbers.isEmpty()) {
                    continue
                }
                
                val fileElapsed = System.currentTimeMillis() - fileStartTime
                AppLogger.d(TAG, "grep_code: File $filesSearched/${foundFiles.size} - Found ${matchedLineNumbers.size} matching lines in ${File(filePath).name} (${fileElapsed}ms)")

                // 合并相近的匹配（根据上下文窗口大小）
                val mergedMatches = mergeNearbyMatches(matchedLineNumbers, contextLines)
                
                val lineMatches = mutableListOf<GrepResultData.LineMatch>()
                
                // 为每个合并后的匹配组创建一个 LineMatch
                for (matchGroup in mergedMatches) {
                    if (totalMatches >= maxResults) {
                        break
                    }
                    
                    val firstLine = matchGroup.first()
                    val lastLine = matchGroup.last()
                    
                    // 获取合并后的上下文
                    val startIdx = maxOf(0, firstLine - contextLines)
                    val endIdx = minOf(lines.size - 1, lastLine + contextLines)
                    val context = lines.subList(startIdx, endIdx + 1).joinToString("\n")
                    
                    // 收集这个区间内所有匹配的行内容
                    val matchedLinesContent = matchGroup.map { lines[it].trim() }.joinToString(" | ")
                    
                    lineMatches.add(
                        GrepResultData.LineMatch(
                            lineNumber = firstLine + 1, // 第一个匹配的行号
                            lineContent = if (matchGroup.size == 1) {
                                lines[firstLine].trim()
                            } else {
                                "${matchGroup.size} matches: ${matchedLinesContent.take(200)}..." // 限制长度
                            },
                            matchContext = context
                        )
                    )
                    
                    totalMatches++
                }

                // 如果该文件有匹配，添加到结果中
                if (lineMatches.isNotEmpty()) {
                    fileMatches.add(
                        GrepResultData.FileMatch(
                            filePath = filePath,
                            lineMatches = lineMatches
                        )
                    )
                }
            }

                // 4. 返回结果
                val totalElapsed = System.currentTimeMillis() - startTime
                AppLogger.d(TAG, "grep_code: Completed - Found $totalMatches matches in ${fileMatches.size} files (searched $filesSearched/${foundFiles.size} files, ${totalElapsed}ms total)")
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = GrepResultData(
                        searchPath = path,
                        pattern = pattern,
                        matches = fileMatches.take(20), // 最多显示20个文件
                        totalMatches = totalMatches,
                        filesSearched = filesSearched
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                val totalElapsed = System.currentTimeMillis() - startTime
                AppLogger.e(TAG, "grep_code: Error after ${totalElapsed}ms - ${e.message}", e)
                return@withContext ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing grep search: ${e.message}"
                )
            }
        }
    }

    /**
     * Grep上下文搜索工具 - 基于意图字符串查找相关文件或文件内的相关代码段
     * 使用语义相关性评分来匹配最相关的内容
     * 
     * 支持两种模式：
     * 1. 目录模式：path 是目录，搜索最相关的文件
     * 2. 文件模式：path 是文件，搜索文件内最相关的代码段
     */
    open suspend fun grepContext(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 10

        // 检查 OnnxEmbeddingService 是否初始化
        if (!OnnxEmbeddingService.isInitialized()) {
            try {
                OnnxEmbeddingService.initialize(context)
            } catch (e: Exception) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to initialize embedding service: ${e.message}"
                )
            }
        }

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.grepContext(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (intent.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Intent parameter is required"
            )
        }

        return try {
            val file = File(path)
            
            // 检查是文件还是目录
            if (file.isFile) {
                // 文件模式：搜索文件内的相关代码段
                return grepContextInFile(path, intent, maxResults, tool.name)
            }
            
            // 目录模式：搜索相关文件
            // 1. 查找所有匹配的文件
            val findFilesResult = findFiles(
                AITool(
                    name = "find_files",
                    parameters = listOf(
                        ToolParameter("path", path),
                        ToolParameter("pattern", filePattern),
                        ToolParameter("use_path_pattern", "false"),
                        ToolParameter("case_insensitive", "false"),
                        ToolParameter("environment", environment ?: "")
                    )
                )
            )

            if (!findFilesResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to find files: ${findFilesResult.error}"
                )
            }

            val foundFiles = (findFilesResult.result as FindFilesResultData).files

            if (foundFiles.isEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = GrepResultData(
                        searchPath = path,
                        pattern = intent,
                        matches = emptyList(),
                        totalMatches = 0,
                        filesSearched = 0
                    )
                )
            }

            // 2. 生成意图的向量
            val intentEmbedding = OnnxEmbeddingService.generateEmbedding(intent)
            if (intentEmbedding == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to generate embedding for intent"
                )
            }

            // 3. 为每个文件计算相关性评分（基于向量相似度）
            data class FileScore(val path: String, val score: Double)
            val fileScores = mutableListOf<FileScore>()

            // 限制最多处理的文件数，避免生成向量过慢
            val filesToProcess = foundFiles.take(50) 

            for (filePath in filesToProcess) {
                val currentFile = File(filePath)
                
                // 跳过目录，只处理文件
                if (!currentFile.isFile) {
                    continue
                }
                
                val fileName = currentFile.name
                val fileExt = currentFile.extension.lowercase()
                
                // 只读取前20行（使用 readFilePart，虽然返回内容带行号但不影响向量生成）
                val readResult = readFilePart(
                    AITool(
                        name = "read_file_part",
                        parameters = listOf(
                            ToolParameter("path", filePath),
                            ToolParameter("start_line", "1"),
                            ToolParameter("end_line", "20")
                        )
                    )
                )

                val fileContent = if (readResult.success) {
                    (readResult.result as FilePartContentData).content
                } else {
                    ""
                }
                
                // 组合文件名和内容作为代表性文本
                // 给予文件名更高的权重（通过重复文件名）
                val representativeText = "File: $fileName. $fileName. Content: ${fileContent.take(500)}"
                
                val fileEmbedding = OnnxEmbeddingService.generateEmbedding(representativeText)
                if (fileEmbedding != null) {
                    var similarity = OnnxEmbeddingService.cosineSimilarity(intentEmbedding, fileEmbedding)
                    
                    // 对文档类文件降低权重，避免其在代码搜索中占据优势
                    val docExtensions = setOf("md", "txt", "doc", "docx", "pdf", "rst", "adoc")
                    if (fileExt in docExtensions) {
                        similarity *= 0.7f // 降低30%权重
                    }
                    
                    if (similarity > 0.2) { // 设定一个最小相似度阈值
                        fileScores.add(FileScore(filePath, similarity.toDouble()))
                    }
                }
            }

            // 4. 按评分排序并取top N
            val topFiles = fileScores.sortedByDescending { it.score }.take(maxResults)

            // 5. 构建返回结果（目录模式：只显示文件路径和相关度，不显示内容预览）
            val fileMatches = topFiles.map { fileScore ->
                GrepResultData.FileMatch(
                    filePath = fileScore.path,
                    lineMatches = listOf(
                        GrepResultData.LineMatch(
                            lineNumber = 0,
                            lineContent = "语义相关度: ${String.format("%.4f", fileScore.score)}",
                            matchContext = null // 不显示文件内容预览
                        )
                    )
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = GrepResultData(
                    searchPath = path,
                    pattern = intent,
                    matches = fileMatches,
                    totalMatches = fileMatches.size,
                    filesSearched = foundFiles.size
                )
            )

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing context search", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error performing context search: ${e.message}"
            )
        }
    }

    /**
     * 在单个文件内搜索与意图最相关的代码段
     */
    protected suspend fun grepContextInFile(
        path: String,
        intent: String,
        maxResults: Int,
        toolName: String
    ): ToolResult {
        return try {
            // 1. 读取文件内容
            val readResult = readFileFull(
                AITool(
                    name = "read_file_full",
                    parameters = listOf(ToolParameter("path", path))
                )
            )

            if (!readResult.success) {
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to read file: ${readResult.error}"
                )
            }

            val fileContent = (readResult.result as FileContentData).content
            val lines = fileContent.lines()
            
            if (lines.isEmpty()) {
                return ToolResult(
                    toolName = toolName,
                    success = true,
                    result = GrepResultData(
                        searchPath = path,
                        pattern = intent,
                        matches = emptyList(),
                        totalMatches = 0,
                        filesSearched = 1
                    )
                )
            }

            // 2. 生成意图向量
            val intentEmbedding = OnnxEmbeddingService.generateEmbedding(intent)
            if (intentEmbedding == null) {
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to generate embedding for intent"
                )
            }

            // 3. 将文件分段（按固定行数或代码块）
            val segments = segmentFileContent(lines)

            // 4. 为每个代码段评分
            data class SegmentScore(
                val startLine: Int,
                val endLine: Int,
                val score: Double,
                val content: String
            )

            val segmentScores = mutableListOf<SegmentScore>()

            for (segment in segments) {
                val segmentContent = lines.subList(segment.first, segment.second + 1).joinToString("\n")
                val segmentEmbedding = OnnxEmbeddingService.generateEmbedding(segmentContent)
                
                if (segmentEmbedding != null) {
                    val similarity = OnnxEmbeddingService.cosineSimilarity(intentEmbedding, segmentEmbedding)
                    
                    if (similarity > 0.2) {
                        segmentScores.add(
                            SegmentScore(
                                startLine = segment.first,
                                endLine = segment.second,
                                score = similarity.toDouble(),
                                content = segmentContent
                            )
                        )
                    }
                }
            }

            // 5. 按评分排序并取 top N
            val topSegments = segmentScores.sortedByDescending { it.score }.take(maxResults)

            // 6. 构建返回结果
            val fileMatches = listOf(
                GrepResultData.FileMatch(
                    filePath = path,
                    lineMatches = topSegments.map { segment ->
                        // 取前3行作为预览
                        val previewLines = segment.content.lines().take(3)
                        val preview = previewLines.joinToString(" | ")
                        val hasMore = segment.content.lines().size > 3
                        
                        GrepResultData.LineMatch(
                            lineNumber = segment.startLine + 1, // 1-indexed
                            lineContent = "相关度: ${String.format("%.4f", segment.score)} | ${preview}${if (hasMore) "..." else ""}",
                            matchContext = null // 不使用 matchContext，避免行号重复显示
                        )
                    }
                )
            )

            ToolResult(
                toolName = toolName,
                success = true,
                result = GrepResultData(
                    searchPath = path,
                    pattern = intent,
                    matches = fileMatches,
                    totalMatches = topSegments.size,
                    filesSearched = 1
                )
            )

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing context search in file", e)
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Error performing context search in file: ${e.message}"
            )
        }
    }

    /**
     * 将文件内容分段
     * 返回段落的起始和结束行索引对 (startLine, endLine)
     */
    protected fun segmentFileContent(lines: List<String>): List<Pair<Int, Int>> {
        if (lines.isEmpty()) return emptyList()
        
        val segments = mutableListOf<Pair<Int, Int>>()
        val segmentSize = 30 // 每段30行
        
        var currentStart = 0
        while (currentStart < lines.size) {
            val currentEnd = minOf(currentStart + segmentSize - 1, lines.size - 1)
            
            // 尝试在代码块边界处分段（寻找空行或大括号）
            var adjustedEnd = currentEnd
            if (currentEnd < lines.size - 1) {
                // 向后查找最多5行，寻找更好的分段点
                for (i in currentEnd until minOf(currentEnd + 5, lines.size)) {
                    val line = lines[i].trim()
                    if (line.isEmpty() || line == "}" || line == "})" || line.endsWith("{")) {
                        adjustedEnd = i
                        break
                    }
                }
            }
            
            segments.add(Pair(currentStart, adjustedEnd))
            currentStart = adjustedEnd + 1
        }
        
        return segments
    }

    /**
     * 合并相近的匹配行
     * 如果两个匹配行之间的距离小于等于 2 * contextLines，则合并它们
     * @param matchedLines 所有匹配的行号列表（已排序）
     * @param contextLines 上下文行数
     * @return 合并后的匹配组列表
     */
    private fun mergeNearbyMatches(matchedLines: List<Int>, contextLines: Int): List<List<Int>> {
        if (matchedLines.isEmpty()) return emptyList()
        
        val sorted = matchedLines.sorted()
        val merged = mutableListOf<MutableList<Int>>()
        var currentGroup = mutableListOf(sorted[0])
        
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            
            // 如果两个匹配的上下文窗口会重叠或相邻，则合并
            // 窗口重叠条件：curr - prev <= 2 * contextLines + 1
            if (curr - prev <= 2 * contextLines + 1) {
                currentGroup.add(curr)
            } else {
                // 开始新的组
                merged.add(currentGroup)
                currentGroup = mutableListOf(curr)
            }
        }
        
        // 添加最后一组
        if (currentGroup.isNotEmpty()) {
            merged.add(currentGroup)
        }
        
        return merged
    }

    /**
     * 执行语法检查
     * @param filePath 文件路径
     * @param content 文件内容
     * @return 语法检查结果的字符串表示，如果不支持该文件类型则返回null
     */
    protected fun performSyntaxCheck(filePath: String, content: String): String? {
        return try {
            val result = SyntaxCheckUtil.checkSyntax(filePath, content)
            result?.toString()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing syntax check", e)
            "Syntax check failed: ${e.message}"
        }
    }

    /** Share file via system share dialog */
    open suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.shareFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "share",
                        env = "android",
                        path = path,
                        successful = false,
                        details = "File does not exist: $path"
                    ),
                    error = "File does not exist: $path"
                )
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "*/*"

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val chooser =
                Intent.createChooser(intent, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = true,
                    details = "Share dialog for file opened: $path"
                ),
                error = ""
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(TAG, "No activity found to handle sharing file: $path", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "No application found to share this file type."
                ),
                error = "No application found to share this file type."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sharing file", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "Error sharing file: ${e.message}"
                ),
                error = "Error sharing file: ${e.message}"
            )
        }
    }
}
