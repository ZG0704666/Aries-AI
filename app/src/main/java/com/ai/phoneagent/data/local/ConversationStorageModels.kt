package com.ai.phoneagent.data.local

import kotlinx.serialization.Serializable

/** 消息附件的持久化记录。 */
@Serializable
data class StoredAttachmentRecord(
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val content: String = "",
)

/** 单条消息的持久化记录。 */
@Serializable
data class StoredMessageRecord(
    val author: String,
    val content: String,
    val isUser: Boolean,
    val runId: String? = null,
    val thinkingDurationMs: Long? = null,
    val attachments: List<StoredAttachmentRecord> = emptyList(),
)

/** 反序列化后的会话记录，供业务层直接使用。 */
data class ConversationRecord(
    val id: Long,
    val title: String,
    val messages: List<StoredMessageRecord>,
    val updatedAt: Long,
)
