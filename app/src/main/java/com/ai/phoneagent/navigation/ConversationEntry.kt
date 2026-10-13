package com.ai.phoneagent.navigation

import com.ai.phoneagent.data.local.ConversationRecord

/** A fresh launch opens an empty conversation; recreation preserves the user's selection. */
fun initialConversationId(
    conversations: List<ConversationRecord>,
    savedActiveId: Long,
    restoreActive: Boolean,
): Long? = if (restoreActive) {
    conversations.firstOrNull { it.id == savedActiveId }?.id ?: conversations.firstOrNull()?.id
} else {
    // Reuse an empty conversation so repeated app launches do not clutter history.
    conversations.firstOrNull { it.messages.isEmpty() }?.id
}
