package com.ai.phoneagent.net

/**
 * 一次请求独占一个会话，直至结束，与 UI 当前选择无关。
 */
class ChatRequestOwner {
    /** 当前被占用的会话 ID；无请求时为 null。 */
    var conversationId: Long? = null
        private set

    /**
     * 尝试占用指定会话。
     *
     * @return 若已有会话被占用返回 false，否则占用并返回 true。
     */
    @Synchronized fun begin(id: Long): Boolean {
        if (conversationId != null) return false
        conversationId = id
        return true
    }

    /** 结束占用；仅当 ID 匹配时才释放。 */
    @Synchronized fun finish(id: Long) {
        if (conversationId == id) conversationId = null
    }
}
