package com.ai.phoneagent.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HorizontalPagerScrollConnectionTest {
    private val greedyDelegate = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource) = available
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
        override suspend fun onPreFling(available: Velocity) = available
        override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
    }
    private val connection = HorizontalPagerScrollConnection(greedyDelegate)

    @Test fun verticalDragRemainsAvailableWhilePagerSettles() {
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.UserInput))
        assertEquals(Offset(12f, 0f), connection.onPreScroll(Offset(12f, 80f), NestedScrollSource.UserInput))
        assertEquals(Offset(12f, 0f), connection.onPostScroll(Offset.Zero, Offset(12f, 80f), NestedScrollSource.UserInput))
    }

    @Test fun verticalFlingRemainsAvailableToSettings() = runBlocking {
        assertEquals(Velocity(12f, 0f), connection.onPreFling(Velocity(12f, 800f)))
        assertEquals(Velocity(12f, 0f), connection.onPostFling(Velocity.Zero, Velocity(12f, 800f)))
    }
}
