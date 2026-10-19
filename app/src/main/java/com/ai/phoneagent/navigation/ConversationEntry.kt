package com.ai.phoneagent.navigation

import com.ai.phoneagent.data.local.ConversationRecord

/**
 * 计算启动时应打开的会话 ID。
 *
 * 重建场景恢复上次选中的会话；全新启动则优先复用空会话，避免历史列表堆积。
 */
fun initialConversationId(
    conversations: List<ConversationRecord>,
    savedActiveId: Long,
    restoreActive: Boolean,
): Long? = if (restoreActive) {
    conversations.firstOrNull { it.id == savedActiveId }?.id ?: conversations.firstOrNull()?.id
} else {
    // 复用空会话，避免反复启动产生大量空历史记录。
    conversations.firstOrNull { it.messages.isEmpty() }?.id
}
