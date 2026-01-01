package com.ai.phoneagent.net

data class ChatRequest(
        val model: String,
        val messages: List<ChatRequestMessage>,
        val stream: Boolean = false,
        val temperature: Float? = null,
        val max_tokens: Int? = null
)

data class ChatRequestMessage(val role: String, val content: Any)

data class ChatResponse(val choices: List<ChatChoice>?)

data class ChatChoice(val index: Int, val message: ChatResponseMessage?)

data class ChatResponseMessage(val role: String, val content: String)
