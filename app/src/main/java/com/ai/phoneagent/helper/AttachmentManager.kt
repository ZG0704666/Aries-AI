package com.ai.phoneagent.helper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.phoneagent.data.AttachmentInfo
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.extractor.ExcelExtractor
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.sl.extractor.SlideShowExtractor
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.extractor.XSSFExcelExtractor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 附件管理器
 * 
 * 统一管理聊天中的所有附件操作
 * - 添加、删除、清空附件
 * - 特殊附件捕获（屏幕内容、位置等）
 * - 附件状态管理
 */
class AttachmentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AttachmentManager"
        private const val OCR_INLINE_INSTRUCTION = "Do not read the file, answer the user's question directly based on the attachment content and the user's question."
        private const val MAX_TEXT_ATTACHMENT_CHARS = 120_000
        private const val TEXT_ATTACHMENT_TRUNCATED_SUFFIX = "\n\n[附件内容过长，已截断]"
        private const val MAX_EXTRACTABLE_FILE_BYTES = 20L * 1024L * 1024L
        private const val MAX_CACHED_IMAGE_BYTES = 8L * 1024L * 1024L
        private const val FILE_TOO_LARGE_TEMPLATE = "[附件过大，未提取正文，大小=%d字节]"
    }
    
    // 附件列表状态
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments
    
    // Toast事件
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent

    private val textAttachmentExtensions =
        setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "log",
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "html", "htm", "css", "yml", "yaml", "ini", "properties"
        )

    private val structuredDocumentExtensions =
        setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx")

    private sealed class LimitedReadResult {
        data class Success(val bytes: ByteArray) : LimitedReadResult()
        data class TooLarge(val bytesRead: Long) : LimitedReadResult()
    }
    
    /**
     * 添加多个附件（去重）
     */
    fun addAttachments(attachments: List<AttachmentInfo>) {
        if (attachments.isEmpty()) return
        
        val currentList = _attachments.value
        val toAdd = attachments.filterNot { incoming ->
            currentList.any { existing -> existing.filePath == incoming.filePath }
        }
        
        if (toAdd.isNotEmpty()) {
            _attachments.value = currentList + toAdd
        }
    }
    
    /**
     * 添加单个附件
     */
    fun addAttachment(attachment: AttachmentInfo) {
        addAttachments(listOf(attachment))
    }
    
    /**
     * 移除附件
     */
    fun removeAttachment(filePath: String) {
        val currentList = _attachments.value
        _attachments.value = currentList.filter { it.filePath != filePath }
    }
    
    /**
     * 清空所有附件
     */
    fun clearAttachments() {
        _attachments.value = emptyList()
    }
    
    /**
     * 更新附件列表
     */
    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        _attachments.value = newAttachments
    }
    
    /**
     * 创建附件引用字符串
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        return AttachmentParser.createAttachmentRef(
            id = attachment.filePath,
            filename = attachment.fileName,
            type = attachment.mimeType,
            size = attachment.fileSize,
            content = attachment.content.ifEmpty { null }
        )
    }
    
    /**
     * 处理文件附件
     */
    suspend fun handleAttachment(filePath: String) = withContext(Dispatchers.IO) {
        try {
            // 处理特殊路径
            when (filePath) {
                "screen_capture" -> {
                    captureScreenContent()
                    return@withContext
                }
                "location_capture" -> {
                    captureLocation()
                    return@withContext
                }
            }
            
            // 处理content URI
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                val fileName = getFileNameFromUri(uri)
                val resolvedMimeType =
                    context.contentResolver.getType(uri)
                        ?: getMimeTypeFromPath(fileName)
                        ?: "application/octet-stream"
                val fileSize = getFileSizeFromUri(uri)
                val attachmentContent =
                    readAttachmentContentFromUri(
                        uri = uri,
                        fileName = fileName,
                        mimeType = resolvedMimeType,
                        fileSize = fileSize
                    ).orEmpty()
                
                val attachmentInfo = AttachmentInfo(
                    filePath = filePath,
                    fileName = fileName,
                    mimeType = resolvedMimeType,
                    fileSize = fileSize,
                    content = attachmentContent
                )
                
                addAttachment(attachmentInfo)
                _toastEvent.emit("已添加附件: $fileName")
            } else {
                // 处理普通文件路径
                val file = File(filePath)
                if (!file.exists()) {
                    _toastEvent.emit("文件不存在")
                    return@withContext
                }
                
                val fileName = file.name
                val fileSize = file.length()
                val mimeType = getMimeTypeFromPath(filePath) ?: "application/octet-stream"
                val attachmentContent =
                    readAttachmentContentFromFile(
                        file = file,
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = fileSize
                    ).orEmpty()
                
                val attachmentInfo = AttachmentInfo(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    content = attachmentContent
                )
                
                addAttachment(attachmentInfo)
                _toastEvent.emit("已添加附件: $fileName")
            }
        } catch (e: Exception) {
            _toastEvent.emit("添加附件失败: ${e.message}")
        }
    }
    
    /**
     * 处理拍照附件
     */
    suspend fun handleTakenPhoto(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val fileName = "camera_${System.currentTimeMillis()}.jpg"
            val tempFile = createTempFileFromUri(uri, fileName)
            
            if (tempFile != null) {
                val attachmentInfo = AttachmentInfo(
                    filePath = tempFile.absolutePath,
                    fileName = fileName,
                    mimeType = "image/jpeg",
                    fileSize = tempFile.length()
                )
                
                addAttachment(attachmentInfo)
                _toastEvent.emit("已添加照片: $fileName")
            } else {
                _toastEvent.emit("处理照片失败")
            }
        } catch (e: Exception) {
            _toastEvent.emit("添加照片失败: ${e.message}")
        }
    }
    
    /**
     * 捕获屏幕内容（截图 + OCR）
     */
    suspend fun captureScreenContent() = withContext(Dispatchers.IO) {
        try {
            // TODO: 集成截图功能
            // 1. 调用截图工具
            // 2. 使用OCR识别文本
            // 3. 创建虚拟附件
            
            val captureId = "screen_ocr_${System.currentTimeMillis()}"
            val ocrText = "屏幕内容识别功能待实现" // TODO: 实际OCR结果
            
            val content = buildString {
                append("屏幕内容:\n")
                append("位置: 全屏\n\n")
                append(ocrText)
                append("\n\n")
                append(OCR_INLINE_INSTRUCTION)
            }
            
            val attachmentInfo = AttachmentInfo(
                filePath = captureId,
                fileName = "screen_content.txt",
                mimeType = "text/plain",
                fileSize = content.length.toLong(),
                content = content
            )
            
            addAttachment(attachmentInfo)
            _toastEvent.emit("已添加屏幕内容")
        } catch (e: Exception) {
            _toastEvent.emit("捕获屏幕内容失败: ${e.message}")
        }
    }
    
    /**
     * 捕获位置信息
     */
    suspend fun captureLocation(highAccuracy: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            // TODO: 集成位置服务
            val captureId = "location_${System.currentTimeMillis()}"
            
            // 模拟位置数据
            val locationJson = """
                {
                    "latitude": 39.9042,
                    "longitude": 116.4074,
                    "accuracy": 10.0,
                    "timestamp": "${System.currentTimeMillis()}"
                }
            """.trimIndent()
            
            val attachmentInfo = AttachmentInfo(
                filePath = captureId,
                fileName = "location.json",
                mimeType = "application/json",
                fileSize = locationJson.length.toLong(),
                content = locationJson
            )
            
            addAttachment(attachmentInfo)
            _toastEvent.emit("已添加位置信息")
        } catch (e: Exception) {
            _toastEvent.emit("获取位置失败: ${e.message}")
        }
    }
    
    /**
     * 捕获当前时间
     */
    suspend fun captureCurrentTime() = withContext(Dispatchers.IO) {
        try {
            val captureId = "time_${System.currentTimeMillis()}"
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val content = "当前时间: $timeText"
            val attachmentInfo = AttachmentInfo(
                filePath = captureId,
                fileName = "time.txt",
                mimeType = "text/plain",
                fileSize = content.length.toLong(),
                content = content
            )
            
            addAttachment(attachmentInfo)
            _toastEvent.emit("已添加时间信息")
        } catch (e: Exception) {
            _toastEvent.emit("获取时间失败: ${e.message}")
        }
    }
    
    /**
     * 从URI创建临时文件
     */
    private suspend fun createTempFileFromUri(uri: Uri, fileName: String): File? = 
        withContext(Dispatchers.IO) {
            try {
                val declaredSize =
                    runCatching {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                            descriptor.length.takeIf { it > 0L }
                                ?: descriptor.declaredLength.takeIf { it > 0L }
                                ?: 0L
                        } ?: 0L
                    }.getOrDefault(0L)
                if (declaredSize > MAX_CACHED_IMAGE_BYTES) {
                    return@withContext null
                }

                val fileExtension = fileName.substringAfterLast('.', "jpg")
                val tempFile = File.createTempFile("temp_", ".$fileExtension", context.cacheDir)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (!copyStreamToFileWithLimit(input, tempFile, MAX_CACHED_IMAGE_BYTES)) {
                        tempFile.delete()
                        return@withContext null
                    }
                } ?: run {
                    tempFile.delete()
                    return@withContext null
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile
                } else {
                    tempFile.delete()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    
    private fun copyStreamToFileWithLimit(input: InputStream, target: File, maxBytes: Long): Boolean {
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
    /**
     * 从URI获取文件名
     */
    private suspend fun getFileNameFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        var fileName = "unknown_file"
        
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        fileName
    }
    
    /**
     * 从URI获取文件大小
     */
    private suspend fun getFileSizeFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        var fileSize = 0L
        
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        if (fileSize > 0L) {
            return@withContext fileSize
        }

        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it > 0L } ?: 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun detectExtension(fileName: String, mimeType: String): String {
        val extFromName = fileName.substringAfterLast('.', "").trim().lowercase()
        if (extFromName.isNotBlank()) return extFromName

        return when (mimeType.substringBefore(";").trim().lowercase()) {
            "application/pdf" -> "pdf"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            else -> ""
        }
    }

    private fun shouldExtractAttachmentContent(fileName: String, mimeType: String): Boolean {
        val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
        if (normalizedMime.startsWith("text/")) return true
        if (
            normalizedMime == "application/json" ||
            normalizedMime == "application/xml" ||
            normalizedMime == "application/x-yaml" ||
            normalizedMime == "application/yaml" ||
            normalizedMime == "application/csv"
        ) {
            return true
        }

        val extension = detectExtension(fileName, mimeType)
        return extension in textAttachmentExtensions || extension in structuredDocumentExtensions
    }

    private fun truncateAttachmentText(raw: String): String {
        val text = raw.replace("\u0000", "").trim()
        if (text.length <= MAX_TEXT_ATTACHMENT_CHARS) return text
        return text.take(MAX_TEXT_ATTACHMENT_CHARS) + TEXT_ATTACHMENT_TRUNCATED_SUFFIX
    }

    private fun extractAttachmentText(bytes: ByteArray, fileName: String, mimeType: String): String? {
        val extension = detectExtension(fileName, mimeType)
        val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
        val rawText =
            when {
                extension in textAttachmentExtensions || normalizedMime.startsWith("text/") ->
                    bytes.toString(Charsets.UTF_8)
                extension == "pdf" -> extractPdfText(bytes)
                extension == "doc" -> extractDocText(bytes)
                extension == "docx" -> extractDocxText(bytes)
                extension == "ppt" -> extractPptText(bytes)
                extension == "pptx" -> extractPptxText(bytes)
                extension == "xls" -> extractXlsText(bytes)
                extension == "xlsx" -> extractXlsxText(bytes)
                else -> null
            }
        return rawText?.let(::truncateAttachmentText)?.takeIf { it.isNotBlank() }
    }

    private fun extractPdfText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                PdfReader(input).use { reader ->
                    PdfDocument(reader).use { pdf ->
                        buildString {
                            for (pageIndex in 1..pdf.numberOfPages) {
                                val pageText = PdfTextExtractor.getTextFromPage(pdf.getPage(pageIndex)).trim()
                                if (pageText.isNotBlank()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(pageText)
                                }
                            }
                        }
                    }
                }
            }
        }.getOrNull()

    private fun extractDocText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                HWPFDocument(input).use { document ->
                    WordExtractor(document).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun extractDocxText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                XWPFDocument(input).use { document ->
                    XWPFWordExtractor(document).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun extractPptText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                HSLFSlideShow(input).use { slideShow ->
                    SlideShowExtractor(slideShow).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun extractPptxText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                XMLSlideShow(input).use { slideShow ->
                    SlideShowExtractor(slideShow).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun extractXlsText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                HSSFWorkbook(input).use { workbook ->
                    ExcelExtractor(workbook).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun extractXlsxText(bytes: ByteArray): String? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    XSSFExcelExtractor(workbook).use { extractor -> extractor.text }
                }
            }
        }.getOrNull()

    private fun InputStream.readBytesLimited(maxBytes: Long): LimitedReadResult {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L

        while (true) {
            val readCount = read(buffer)
            if (readCount <= 0) break

            totalBytes += readCount
            if (totalBytes > maxBytes) {
                return LimitedReadResult.TooLarge(totalBytes)
            }
            output.write(buffer, 0, readCount)
        }

        return LimitedReadResult.Success(output.toByteArray())
    }

    private suspend fun readAttachmentContentFromUri(
        uri: Uri,
        fileName: String,
        mimeType: String,
        fileSize: Long
    ): String? = withContext(Dispatchers.IO) {
        if (!shouldExtractAttachmentContent(fileName, mimeType)) return@withContext null
        if (fileSize > MAX_EXTRACTABLE_FILE_BYTES) {
            return@withContext FILE_TOO_LARGE_TEMPLATE.format(fileSize)
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (val readResult = input.readBytesLimited(MAX_EXTRACTABLE_FILE_BYTES)) {
                    is LimitedReadResult.Success ->
                        extractAttachmentText(readResult.bytes, fileName, mimeType)
                    is LimitedReadResult.TooLarge ->
                        FILE_TOO_LARGE_TEMPLATE.format(maxOf(fileSize, readResult.bytesRead))
                }
            }
        }.getOrNull()
    }

    private suspend fun readAttachmentContentFromFile(
        file: File,
        fileName: String,
        mimeType: String,
        fileSize: Long
    ): String? = withContext(Dispatchers.IO) {
        if (!shouldExtractAttachmentContent(fileName, mimeType)) return@withContext null
        if (fileSize > MAX_EXTRACTABLE_FILE_BYTES) {
            return@withContext FILE_TOO_LARGE_TEMPLATE.format(fileSize)
        }
        runCatching {
            file.inputStream().use { input ->
                when (val readResult = input.readBytesLimited(MAX_EXTRACTABLE_FILE_BYTES)) {
                    is LimitedReadResult.Success ->
                        extractAttachmentText(readResult.bytes, fileName, mimeType)
                    is LimitedReadResult.TooLarge ->
                        FILE_TOO_LARGE_TEMPLATE.format(maxOf(fileSize, readResult.bytesRead))
                }
            }
        }.getOrNull()
    }
    
    /**
     * 从文件路径获取MIME类型
     */
    private fun getMimeTypeFromPath(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 获取显示名称（截断过长的文件名）
     */
    fun getDisplayName(attachment: AttachmentInfo, maxLength: Int = 20): String {
        return if (attachment.fileName.length <= maxLength) {
            attachment.fileName
        } else {
            val extension = attachment.fileName.substringAfterLast('.', "")
            val nameWithoutExtension = attachment.fileName.substringBeforeLast('.')
            val truncatedName = nameWithoutExtension.take(maxLength - extension.length - 3)
            "$truncatedName...${if (extension.isNotEmpty()) ".$extension" else ""}"
        }
    }
}
