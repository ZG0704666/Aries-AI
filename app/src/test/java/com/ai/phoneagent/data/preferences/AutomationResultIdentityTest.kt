package com.ai.phoneagent.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
class AutomationResultIdentityTest {
    @Test fun replacementIsAtomicAndDoesNotMatchOlderTask() = runBlocking {
        val repository = AutomationResultsRepository(ApplicationProvider.getApplicationContext())
        repository.clearAll()
        repository.saveResult(true, "first", 3, 20, "log A", "A", "goal A", 10)
        val first = repository.resultFlow.first()
        assertTrue(first.matchesRun("A"))
        repository.saveResult(false, "stopped", 2, 40, "log B", "B", "goal B", 30, "CANCELLED")
        val latest = repository.resultFlow.first()
        assertFalse(latest.matchesRun("A"))
        assertTrue(latest.matchesRun("B"))
        assertEquals("CANCELLED", latest.status)
        assertEquals("goal B", latest.goal)
        assertEquals("log B", latest.log)
        assertEquals(2, latest.steps)
        assertEquals(30L, latest.startedAt)
        repository.clearAll()
        assertFalse(repository.resultFlow.first().matchesRun(""))
    }
}
