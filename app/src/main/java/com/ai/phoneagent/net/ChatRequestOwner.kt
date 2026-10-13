package com.ai.phoneagent.net

/** One request owns one conversation until it finishes, independent of UI selection. */
class ChatRequestOwner {
    var conversationId: Long? = null
        private set

    @Synchronized fun begin(id: Long): Boolean {
        if (conversationId != null) return false
        conversationId = id
        return true
    }

    @Synchronized fun finish(id: Long) {
        if (conversationId == id) conversationId = null
    }
}
