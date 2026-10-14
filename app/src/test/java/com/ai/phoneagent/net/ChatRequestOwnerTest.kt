package com.ai.phoneagent.net

import org.junit.Assert.*
import org.junit.Test

class ChatRequestOwnerTest {
    @Test fun anotherConversationCannotAcquireAnOngoingRequest() {
        val owner = ChatRequestOwner()
        assertTrue(owner.begin(101))
        assertFalse(owner.begin(202))
        assertEquals(101L, owner.conversationId)
        owner.finish(202)
        assertEquals(101L, owner.conversationId)
        owner.finish(101)
        assertNull(owner.conversationId)
        assertTrue(owner.begin(202))
        // A late cleanup from the previous request must not release the next request.
        owner.finish(101)
        assertEquals(202L, owner.conversationId)
    }
}
