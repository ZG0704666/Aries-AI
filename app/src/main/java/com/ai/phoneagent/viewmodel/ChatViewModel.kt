package com.ai.phoneagent.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Base64OutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.helper.AttachmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * ChatViewModel
 * 
 * 管理聊天相关的状态，包括附件管理
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        private const val MAX_BASE64_IMAGE_BYTES = 2L * 1024L * 1024L
        private const val MAX_RAW_FALLBACK_IMAGE_BYTES = 512L * 1024L
        private const val MAX_IMAGE_EDGE_PX = 1568
        private const val JPEG_QUALITY_START = 85
        private const val JPEG_QUALITY_MIN = 55
        private const val JPEG_QUALITY_STEP = 10
    }

    private data class EncodedImagePayload(
        val base64: String,
        val mimeType: String,
    )
    
    // 附件管理器
    private val attachmentManager = AttachmentManager(application)
    
    // 附件列表状态
    val attachments: StateFlow<List<AttachmentInfo>> = attachmentManager.attachments
    
    // Toast事件
    val toastEvent = attachmentManager.toastEvent
    
    // 附件选择器可见性
    private val _attachmentSelectorVisible = MutableStateFlow(false)
    val attachmentSelectorVisible: StateFlow<Boolean> = _attachmentSelectorVisible
    
    /**
     * 显示附件选择器
     */
    fun showAttachmentSelector() {
        _attachmentSelectorVisible.value = true
    }
    
    /**
     * 隐藏附件选择器
     */
    fun hideAttachmentSelector() {
        _attachmentSelectorVisible.value = false
    }
    
    /**
     * 切换附件选择器显示状态
     */
    fun toggleAttachmentSelector() {
        _attachmentSelectorVisible.value = !_attachmentSelectorVisible.value
    }
    
    /**
     * 添加附件
     */
    fun addAttachment(attachment: AttachmentInfo) {
        attachmentManager.addAttachment(attachment)
    }
    
    /**
     * 移除附件
     */
    fun removeAttachment(filePath: String) {
        attachmentManager.removeAttachment(filePath)
    }
    
    /**
     * 清空所有附件
     */
    fun clearAttachments() {
        attachmentManager.clearAttachments()
    }
    
    /**
     * 处理文件附件
     */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            attachmentManager.handleAttachment(filePath)
        }
    }
    
    /**
     * 处理拍照附件
     */
    fun handleTakenPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            attachmentManager.handleTakenPhoto(uri)
        }
    }
    
    /**
     * 捕获屏幕内容
     */
    fun captureScreenContent() {
        viewModelScope.launch {
            attachmentManager.captureScreenContent()
        }
    }
    
    /**
     * 捕获位置信息
     */
    fun captureLocation() {
        viewModelScope.launch {
            attachmentManager.captureLocation()
        }
    }
    
    /**
     * 创建附件引用
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        return attachmentManager.createAttachmentReference(attachment)
    }
    
    /**
     * 获取附件管理器（供UI组件使用）
     */
    fun getAttachmentManager(): AttachmentManager {
        return attachmentManager
    }
    
    private fun buildAttachmentAwareText(
        userMessage: String,
        nonImageAttachments: List<AttachmentInfo>
    ): String {
        val messageBuilder = StringBuilder(userMessage.trim())
        nonImageAttachments.forEachIndexed { index, attachment ->
            if (messageBuilder.isNotEmpty()) {
                messageBuilder.append("\n\n")
            }
            messageBuilder.append("附件").append(index + 1).append("：")
            messageBuilder.append(attachment.fileName.ifBlank { "未命名文件" })
            messageBuilder.append("（").append(attachment.mimeType).append("，")
            messageBuilder.append(attachment.fileSize).append("字节）")
            if (attachment.content.isNotBlank()) {
                messageBuilder.append("\n附件内容：\n")
                messageBuilder.append(attachment.content.trim())
            }
        }
        return messageBuilder.toString().trim()
    }

    private fun isImageAttachment(attachment: AttachmentInfo): Boolean {
        if (attachment.mimeType.startsWith("image/", ignoreCase = true)) return true
        val extension =
            attachment.fileName.substringAfterLast('.', "").ifBlank {
                attachment.filePath.substringAfterLast('.', "")
            }.lowercase()
        return extension in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
    }

    private fun resolveImageMimeType(attachment: AttachmentInfo): String {
        if (attachment.mimeType.startsWith("image/", ignoreCase = true)) {
            return attachment.mimeType
        }
        return when (
            attachment.fileName.substringAfterLast('.', "").ifBlank {
                attachment.filePath.substringAfterLast('.', "")
            }.lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    /**
     * 构建包含附件的完整消息（多模态格式）
     *
     * 对于图片附件，返回 OpenAI 兼容 content 数组；
     * 对于文本/文件附件，直接拼入可读文本内容传给模型。
     */
    fun buildMessageWithAttachments(
        userMessage: String,
        sourceAttachments: List<AttachmentInfo>? = null
    ): Any {
        val currentAttachments = sourceAttachments ?: attachments.value
        if (currentAttachments.isEmpty()) {
            return userMessage
        }
        
        // 检查是否有图片附件
        val imageAttachments = currentAttachments.filter { isImageAttachment(it) }
        
        val nonImageAttachments = currentAttachments.filterNot { isImageAttachment(it) }
        val textPayload = buildAttachmentAwareText(userMessage, nonImageAttachments)

        if (imageAttachments.isNotEmpty()) {
            // 构建多模态内容数组（OpenAI格式）
            val contentArray = mutableListOf<Map<String, Any>>()
             
            // 添加文本内容
            if (textPayload.isNotBlank()) {
                contentArray.add(mapOf(
                    "type" to "text",
                    "text" to textPayload
                ))
            }
            
            // 添加图片内容
            imageAttachments.forEach { attachment ->
                val imagePayload = encodeImagePayload(attachment)
                if (imagePayload != null) {
                    contentArray.add(mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:${imagePayload.mimeType};base64,${imagePayload.base64}"
                        )
                    ))
                }
            }
             
            return contentArray
        }

        return textPayload
    }
    
    /**
     * 读取图片文件并转换为base64
     */
    private fun encodeImagePayload(attachment: AttachmentInfo): EncodedImagePayload? {
        readCompressedImageAsBase64(attachment.filePath)?.let {
            return EncodedImagePayload(base64 = it, mimeType = "image/jpeg")
        }

        val rawBase64 = readImageAsBase64(attachment.filePath, MAX_RAW_FALLBACK_IMAGE_BYTES) ?: return null
        return EncodedImagePayload(base64 = rawBase64, mimeType = resolveImageMimeType(attachment))
    }

    private fun readCompressedImageAsBase64(filePath: String): String? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        openAttachmentInputStream(filePath)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap =
            openAttachmentInputStream(filePath)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

        val scaledBitmap = scaleBitmapIfNeeded(decodedBitmap)
        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        return try {
            compressBitmapAsBase64(scaledBitmap)
        } finally {
            scaledBitmap.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > MAX_IMAGE_EDGE_PX || currentHeight > MAX_IMAGE_EDGE_PX) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= MAX_IMAGE_EDGE_PX) {
            return bitmap
        }

        val scale = MAX_IMAGE_EDGE_PX.toFloat() / maxEdge.toFloat()
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    private fun compressBitmapAsBase64(bitmap: Bitmap): String? {
        var quality = JPEG_QUALITY_START
        while (quality >= JPEG_QUALITY_MIN) {
            val output = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            val bytes = output.toByteArray()
            if (compressed && bytes.isNotEmpty() && bytes.size.toLong() <= MAX_BASE64_IMAGE_BYTES) {
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            quality -= JPEG_QUALITY_STEP
        }
        return null
    }

    private fun readImageAsBase64(filePath: String, maxBytes: Long): String? {
        return try {
            if (!isWithinByteLimit(filePath, maxBytes)) {
                return null
            }
            openAttachmentInputStream(filePath)?.use { encodeStreamAsBase64(it, maxBytes) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isWithinByteLimit(filePath: String, maxBytes: Long): Boolean {
        val context = getApplication<Application>()
        return when {
            filePath.startsWith("content://") -> {
                val uri = Uri.parse(filePath)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    val declaredLength = descriptor.length.takeIf { it > 0L }
                        ?: descriptor.declaredLength.takeIf { it > 0L }
                    declaredLength == null || declaredLength in 1..maxBytes
                } ?: true
            }
            filePath.startsWith("file://") -> {
                Uri.parse(filePath).path
                    ?.let(::File)
                    ?.takeIf { it.exists() && it.isFile }
                    ?.length()
                    ?.let { it in 1..maxBytes }
                    ?: false
            }
            else -> {
                val file = File(filePath)
                file.exists() && file.isFile && file.length() in 1..maxBytes
            }
        }
    }

    private fun openAttachmentInputStream(filePath: String): InputStream? {
        val context = getApplication<Application>()
        return when {
            filePath.startsWith("content://") -> {
                context.contentResolver.openInputStream(Uri.parse(filePath))
            }
            filePath.startsWith("file://") -> {
                Uri.parse(filePath).path
                    ?.let(::File)
                    ?.takeIf { it.exists() && it.isFile }
                    ?.inputStream()
            }
            else -> {
                val file = File(filePath)
                if (file.exists() && file.isFile) {
                    file.inputStream()
                } else {
                    null
                }
            }
        }
    }

    private fun encodeStreamAsBase64(input: InputStream, maxBytes: Long): String? {
        val rawOutput = ByteArrayOutputStream()
        Base64OutputStream(rawOutput, android.util.Base64.NO_WRAP).use { base64Output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read.toLong()
                if (totalBytes > maxBytes) {
                    return null
                }
                base64Output.write(buffer, 0, read)
            }
            if (totalBytes <= 0L) {
                return null
            }
        }
        return rawOutput.toString(Charsets.UTF_8.name())
    }
}
