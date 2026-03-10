package com.ai.phoneagent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val updatedAt: Long,
    val messagesJson: String,
)
