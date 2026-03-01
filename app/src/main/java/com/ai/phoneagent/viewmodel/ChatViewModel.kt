package com.ai.phoneagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.helper.AttachmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    
    /**
     * 构建包含附件的完整消息（多模态格式）
     * 
     * 对于多模态模型，返回包含文本和图片的内容数组
     * 对于普通模型，返回文本格式的附件引用
     */
    fun buildMessageWithAttachments(userMessage: String): Any {
        val currentAttachments = attachments.value
        if (currentAttachments.isEmpty()) {
            return userMessage
        }
        
        // 检查是否有图片附件
        val imageAttachments = currentAttachments.filter { 
            it.mimeType.startsWith("image/") 
        }
        
        if (imageAttachments.isNotEmpty()) {
            // 构建多模态内容数组（OpenAI格式）
            val contentArray = mutableListOf<Map<String, Any>>()
            
            // 添加文本内容
            if (userMessage.isNotBlank()) {
                contentArray.add(mapOf(
                    "type" to "text",
                    "text" to userMessage
                ))
            }
            
            // 添加图片内容
            imageAttachments.forEach { attachment ->
                // 读取图片并转换为base64
                val imageData = readImageAsBase64(attachment.filePath)
                if (imageData != null) {
                    contentArray.add(mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:${attachment.mimeType};base64,$imageData"
                        )
                    ))
                }
            }
            
            return contentArray
        }
        
        // 如果没有图片，使用文本格式的附件引用
        val messageBuilder = StringBuilder(userMessage)
        currentAttachments.forEach { attachment ->
            messageBuilder.append("\n\n")
            messageBuilder.append(createAttachmentReference(attachment))
        }
        
        return messageBuilder.toString()
    }
    
    /**
     * 读取图片文件并转换为base64
     */
    private fun readImageAsBase64(filePath: String): String? {
        return try {
            val context = getApplication<Application>()
            val uri = android.net.Uri.parse(filePath)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
