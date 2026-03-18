package com.ai.phoneagent.data.local

import kotlinx.serialization.Serializable

@Serializable
data class StoredAttachmentRecord(
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val content: String = "",
)

@Serializable
data class StoredMessageRecord(
    val author: String,
    val content: String,
    val isUser: Boolean,
    val thinkingDurationMs: Long? = null,
    val attachments: List<StoredAttachmentRecord> = emptyList(),
)

data class ConversationRecord(
    val id: Long,
    val title: String,
    val messages: List<StoredMessageRecord>,
    val updatedAt: Long,
)
