package com.ai.phoneagent.navigation

import com.ai.phoneagent.core.automation.TaskSnapshot
import com.ai.phoneagent.core.automation.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskNavigationTest {
    private val run = TaskSnapshot("run-1", "Test task", 1L)

    @Test fun enteringOrReturningToControlDoesNotReopenExistingRun() {
        for (status in TaskStatus.entries) {
            assertFalse(shouldOpenNewTask(run.runId, run.copy(status = status)))
        }
    }

    @Test fun newlyStartedRunOpensDetailsOnce() {
        assertTrue(shouldOpenNewTask(null, run))
        assertTrue(shouldOpenNewTask("older-run", run))
        assertFalse(shouldOpenNewTask(run.runId, run))
    }

    @Test fun missingOrCompletedRunDoesNotOpenDetails() {
        assertFalse(shouldOpenNewTask(run.runId, null))
        for (status in TaskStatus.entries.filter { it.isTerminal }) {
            assertFalse(shouldOpenNewTask(null, run.copy(status = status)))
        }
    }
}
