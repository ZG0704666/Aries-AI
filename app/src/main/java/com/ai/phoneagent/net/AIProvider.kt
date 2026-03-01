package com.ai.phoneagent.net

/**
 * AI Provider 接口
 * 
 * 定义与不同AI提供商进行交互的标准方法
 * 支持多模态输入和Tool Call功能
 */
interface AIProvider {
    
    /**
     * 供应商名称
     */
    val providerName: String
    
    /**
     * 模型名称
     */
    val modelName: String
    
    /**
     * 是否支持视觉输入（图片）
     */
    val supportsVision: Boolean
    
    /**
     * 是否支持音频输入
     */
    val supportsAudio: Boolean
    
    /**
     * 是否支持视频输入
     */
    val supportsVideo: Boolean
    
    /**
     * 是否启用Tool Call API
     */
    val enableToolCall: Boolean
    
    /**
     * 发送消息（流式）
     * 
     * @param messages 消息历史
     * @param onChunk 接收到文本片段时的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    suspend fun sendMessageStream(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    )
    
    /**
     * 构建多模态内容
     * 
     * @param text 文本内容
     * @param attachments 附件列表
     * @return 处理后的消息内容
     */
    fun buildContentWithAttachments(
        text: String,
        attachments: List<com.ai.phoneagent.data.AttachmentInfo>
    ): Any
    
    /**
     * 解析XML格式的Tool Call
     * 
     * @param content 包含XML标签的内容
     * @return Pair<纯文本内容, Tool Call数据>
     */
    fun parseXmlToolCalls(content: String): Pair<String, Any?>
    
    /**
     * 解析XML格式的Tool Result
     * 
     * @param content 包含XML标签的内容
     * @return Pair<纯文本内容, Tool Result数据>
     */
    fun parseXmlToolResults(content: String): Pair<String, Any?>
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val role: String,      // "user", "assistant", "system"
    val content: String,   // 消息内容
    val attachments: List<com.ai.phoneagent.data.AttachmentInfo> = emptyList()
)
