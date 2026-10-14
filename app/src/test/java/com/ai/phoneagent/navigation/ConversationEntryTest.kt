package com.ai.phoneagent.navigation

import com.ai.phoneagent.data.local.ConversationRecord
import com.ai.phoneagent.data.local.StoredMessageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationEntryTest {
    private val history = ConversationRecord(1, "History", listOf(StoredMessageRecord("User", "Hello", true)), 1)
    private val empty = ConversationRecord(2, "", emptyList(), 2)

    @Test fun freshLaunchDoesNotOpenPreviousHistory() {
        assertNull(initialConversationId(listOf(history), history.id, restoreActive = false))
    }

    @Test fun repeatedLaunchReusesEmptyConversation() {
        assertEquals(empty.id, initialConversationId(listOf(history, empty), history.id, restoreActive = false))
    }

    @Test fun recreationKeepsSelectedHistory() {
        assertEquals(history.id, initialConversationId(listOf(empty, history), history.id, restoreActive = true))
    }

    @Test fun missingRestoredSelectionFallsBackAndEmptyStorageStartsFresh() {
        assertEquals(history.id, initialConversationId(listOf(history), -1, restoreActive = true))
        assertNull(initialConversationId(emptyList(), -1, restoreActive = false))
    }
}
