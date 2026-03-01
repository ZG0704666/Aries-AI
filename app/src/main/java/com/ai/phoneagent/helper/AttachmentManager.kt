package com.ai.phoneagent.helper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.phoneagent.data.AttachmentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
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
    }
    
    // 附件列表状态
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments
    
    // Toast事件
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent
    
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
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileSize = getFileSizeFromUri(uri)
                
                val attachmentInfo = AttachmentInfo(
                    filePath = filePath,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize
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
                
                val attachmentInfo = AttachmentInfo(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize
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
                val fileExtension = fileName.substringAfterLast('.', "jpg")
                val tempFile = File.createTempFile("temp_", ".$fileExtension", context.cacheDir)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
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
        
        fileSize
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
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
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
