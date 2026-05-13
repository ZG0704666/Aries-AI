package com.ai.phoneagent.net

import com.ai.phoneagent.data.model.ChatContent
import com.ai.phoneagent.data.model.ContentPart
import com.ai.phoneagent.data.model.ImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: ChatContent,
) {
    /**
     * Secondary constructor for backward-compatibility with legacy call sites that pass
     * [String], [List], or other raw types as content. T9/T10 will migrate these call
     * sites to use typed [ChatContent] directly.
     */
    constructor(role: String, content: Any) : this(role, content.toChatContent())

    companion object {
        private fun Any.toChatContent(): ChatContent =
            when (this) {
                is ChatContent -> this
                is String -> ChatContent.Text(this)
                is List<*> -> toMultimodalContentOrNull() ?: ChatContent.Text(toString())
                is Map<*, *> -> toContentPartOrNull()?.let { ChatContent.Multimodal(listOf(it)) }
                    ?: ChatContent.Text(toString())
                else -> ChatContent.Text(toString())
            }

        private fun List<*>.toMultimodalContentOrNull(): ChatContent.Multimodal? {
            if (isEmpty()) return null
            val parts = mapNotNull { it.toContentPartOrNull() }
            return if (parts.size == size) ChatContent.Multimodal(parts) else null
        }

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

@Serializable
data class ChatResponse(val choices: List<ChatChoice>?)

@Serializable
data class ChatChoice(val index: Int, val message: ChatResponseMessage?)

@Serializable
data class ChatResponseMessage(val role: String, val content: String)
