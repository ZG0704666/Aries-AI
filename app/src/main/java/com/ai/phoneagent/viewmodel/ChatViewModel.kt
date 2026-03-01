package com.ai.phoneagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.helper.AttachmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ChatViewModel
 * 
 * 管理聊天相关的状态，包括附件管理
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
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
                // 读取图片并转换为base64
                val imageData = readImageAsBase64(attachment.filePath)
                if (imageData != null) {
                    val imageMimeType = resolveImageMimeType(attachment)
                    contentArray.add(mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:${imageMimeType};base64,$imageData"
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
    private fun readImageAsBase64(filePath: String): String? {
        return try {
            val context = getApplication<Application>()
            val inputStream =
                when {
                    filePath.startsWith("content://") || filePath.startsWith("file://") -> {
                        val uri = android.net.Uri.parse(filePath)
                        context.contentResolver.openInputStream(uri)
                    }
                    else -> {
                        val file = File(filePath)
                        if (file.exists() && file.isFile) file.inputStream() else null
                    }
                }
            val bytes = inputStream?.use { it.readBytes() }
            bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
