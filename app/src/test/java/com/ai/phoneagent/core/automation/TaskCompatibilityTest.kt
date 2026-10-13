package com.ai.phoneagent.core.automation

import com.ai.phoneagent.data.local.StoredMessageRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TaskCompatibilityTest {
    @Test fun legacyMessageWithoutTaskIdentityStillLoads() {
        val message = Json.decodeFromString<StoredMessageRecord>("""{"author":"AI","content":"old log","isUser":false}""")
        assertNull(message.runId)
        assertEquals("old log", message.content)
    }

    @Test fun taskIdentitySurvivesPersistence() {
        val message = StoredMessageRecord("AI", "task", false, runId = "run-123")
        assertEquals(message, Json.decodeFromString<StoredMessageRecord>(Json.encodeToString(StoredMessageRecord.serializer(), message)))
    }

    @Test fun pauseRequestCannotBeMistakenForCompletion() {
        assertFalse(TaskStatus.PAUSE_REQUESTED.isTerminal)
        assertFalse(TaskStatus.PAUSED.isTerminal)
        assertFalse(TaskStatus.RUNNING.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertTrue(TaskStatus.SUCCEEDED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
    }

    @Test fun pauseRequiresAcknowledgementAndLateCallbacksPreserveTerminalState() {
        val running = TaskSnapshot("A", "goal", 1, steps = 4)
        val requested = running.requestPause(true)
        assertEquals(TaskStatus.PAUSE_REQUESTED, requested.status)
        val paused = requested.acknowledgePause(true)
        assertEquals(TaskStatus.PAUSED, paused.status)
        assertEquals(4, paused.steps)
        assertEquals(TaskStatus.RUNNING, paused.requestPause(false).status)
        val completed = paused.copy(status = TaskStatus.CANCELLED)
        assertEquals(completed, completed.acknowledgePause(false))
        assertEquals(completed, completed.requestPause(true))
    }
}
