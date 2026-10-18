package com.ai.phoneagent.net

import com.ai.phoneagent.data.model.ChatContent
import com.ai.phoneagent.data.model.ContentPart
import com.ai.phoneagent.data.model.ImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 对话补全请求体。 */
@Serializable
data class ChatRequest(
        val model: String,
        val messages: List<ChatRequestMessage>,
        val stream: Boolean = false,
        val temperature: Float? = null,
        @SerialName("max_tokens") val max_tokens: Int? = null,
        @SerialName("top_p") val top_p: Float? = null,
        @SerialName("frequency_penalty") val frequency_penalty: Float? = null,
)

/**
 * 对话请求中的单条消息。
 *
 * @property role 角色（user / assistant / system）。
 * @property content 结构化内容。
 */
@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: ChatContent,
) {
    /**
     * 兼容旧调用点的次构造函数：接受 [String]、[List] 等原始类型内容并归一为 [ChatContent]。
     *
     * 新代码应直接传入类型化的 [ChatContent]。
     */
    constructor(role: String, content: Any) : this(role, content.toChatContent())

    companion object {
        /** 将任意原始内容归一为 [ChatContent]。 */
        private fun Any.toChatContent(): ChatContent =
            when (this) {
                is ChatContent -> this
                is String -> ChatContent.Text(this)
                is List<*> -> toMultimodalContentOrNull() ?: ChatContent.Text(toString())
                is Map<*, *> -> toContentPartOrNull()?.let { ChatContent.Multimodal(listOf(it)) }
                    ?: ChatContent.Text(toString())
                else -> ChatContent.Text(toString())
            }

        /** 当列表内每一项都能解析为内容分段时，构造多模态内容；否则返回 null。 */
        private fun List<*>.toMultimodalContentOrNull(): ChatContent.Multimodal? {
            if (isEmpty()) return null
            val parts = mapNotNull { it.toContentPartOrNull() }
            return if (parts.size == size) ChatContent.Multimodal(parts) else null
        }

        /** 将原始对象解析为内容分段（文本或图片），无法识别时返回 null。 */
        private fun Any?.toContentPartOrNull(): ContentPart? =
            when (this) {
                is ContentPart -> this
                is Map<*, *> -> {
                    when (this["type"] as? String) {
                        "text" -> (this["text"] as? String)?.let(ContentPart::TextPart)
                        "image_url" -> extractImageUrl(this)?.let {
                            ContentPart.ImageUrlPart(ImageUrl(it))
                        }
                        else -> null
                    }
                }
                else -> null
            }

        /** 从图片分段中提取 URL，兼容对象/字符串/嵌套 Map 三种形态。 */
        private fun extractImageUrl(part: Map<*, *>): String? {
            val imageUrl = part["image_url"]
            return when (imageUrl) {
                is ImageUrl -> imageUrl.url
                is String -> imageUrl
                is Map<*, *> -> imageUrl["url"] as? String
                else -> null
            }
        }
    }
}

/** 对话补全响应体。 */
@Serializable
data class ChatResponse(val choices: List<ChatChoice>?)

/** 响应中的单个候选结果。 */
@Serializable
data class ChatChoice(val index: Int, val message: ChatResponseMessage?)

/** 响应中的完整消息。 */
@Serializable
data class ChatResponseMessage(val role: String, val content: String)
