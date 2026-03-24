package com.ai.phoneagent.net

import com.ai.phoneagent.data.model.ChatContent
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
    constructor(role: String, content: Any) : this(
        role,
        when (content) {
            is ChatContent -> content
            is String -> ChatContent.Text(content)
            else -> ChatContent.Text(content.toString())
        }
    )
}

@Serializable
data class ChatResponse(val choices: List<ChatChoice>?)

@Serializable
data class ChatChoice(val index: Int, val message: ChatResponseMessage?)

@Serializable
data class ChatResponseMessage(val role: String, val content: String)
