package com.ai.assistance.operit.util.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class StatefulPathTest {

    @Test
    fun testSingleStatePath() {
        val state = NodeState("A")
        val path = StatefulPath(listOf(state), emptyList(), 0.0)
        
        assertEquals(1, path.states.size)
        assertTrue(path.edges.isEmpty())
        assertEquals(state, path.startState)
        assertEquals(state, path.endState)
        assertTrue(path.isSingleState)
        assertEquals(listOf("A"), path.nodeIds)
        assertTrue(path.isValid())
    }

    @Test
    fun testBasicPathCreation() {
        val stateA = NodeState("A")
        val stateB = NodeState("B")
        val edgeAB = StatefulEdge("A", "B", "move", 1.0)
        val path = StatefulPath(listOf(stateA, stateB), listOf(edgeAB), 1.0)
        
        assertEquals(2, path.states.size)
        assertEquals(1, path.edges.size)
        assertEquals(stateA, path.startState)
        assertEquals(stateB, path.endState)
        assertFalse(path.isSingleState)
        assertEquals(1.0, path.totalWeight, 0.0)
        
        // isValid will fail if stateB is not the result of applying the transform on stateA
        // Since the default transform is identity, the new state should be NodeState("B"), which matches.
        assertTrue(path.isValid())
    }

    @Test
    fun testLongerPathCreation() {
        val stateA = NodeState("A")
        val stateB = NodeState("B")
        val stateC = NodeState("C")
        val edgeAB = StatefulEdge("A", "B", "move1", 1.0)
        val edgeBC = StatefulEdge("B", "C", "move2", 2.0)
        
        val path = StatefulPath(listOf(stateA, stateB, stateC), listOf(edgeAB, edgeBC), 3.0)
        
        assertEquals(3, path.states.size)
        assertEquals(2, path.edges.size)
        assertEquals(stateA, path.startState)
        assertEquals(stateC, path.endState)
        assertEquals(3.0, path.totalWeight, 0.0)
        assertEquals(listOf("A", "B", "C"), path.nodeIds)
        assertTrue(path.isValid())
    }

    @Test
    fun testPathValidation_InvalidStateSequence() {
        val stateA = NodeState("A")
        val stateC = NodeState("C") // Mismatch with edge's destination
        val edgeAB = StatefulEdge("A", "B", "move")
        val path = StatefulPath(listOf(stateA, stateC), listOf(edgeAB), 1.0)
        
        // The path is invalid because applying edgeAB on stateA results in a state for node "B", not "C".
        assertFalse(path.isValid())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPathValidation_InvalidEdgeCount() {
        val stateA = NodeState("A")
        val stateB = NodeState("B")
        val stateC = NodeState("C")
        val edgeAC = StatefulEdge("A", "C", "wrong_move")

        // This should throw IllegalArgumentException because edges.size (1) is not states.size (3) - 1
        StatefulPath(listOf(stateA, stateB, stateC), listOf(edgeAC), 1.0)
    }

    @Test
    fun testHasStateConflicts() {
        val stateA1 = NodeState("A", mapOf("x" to 1))
        val stateB = NodeState("B")
        val stateA2 = NodeState("A", mapOf("x" to 2)) // Same node ID, different state
        
        val edgeAB = StatefulEdge("A", "B", "move")
        val edgeBA = StatefulEdge("B", "A", "return")
        
        val pathWithConflict = StatefulPath(listOf(stateA1, stateB, stateA2), listOf(edgeAB, edgeBA), 2.0)
        assertTrue(pathWithConflict.hasStateConflicts())
        
        val pathWithoutConflict = StatefulPath(listOf(stateA1, stateB), listOf(edgeAB), 1.0)
        assertFalse(pathWithoutConflict.hasStateConflicts())
    }

    @Test
    fun testPathToStringAndProperties() {
        val stateA = NodeState("A")
        val stateB = NodeState("B", mapOf("var" to "val"))
        val edgeAB = StatefulEdge("A", "B", "move", 2.5)
        val path = StatefulPath(listOf(stateA, stateB), listOf(edgeAB), 2.5)
        
        assertEquals("StatefulPath(A -> B, weight=2.5)", path.toString())
        
        // Verify properties directly instead of a non-existent detailed string method
        assertEquals(stateA, path.states[0])
        assertEquals(stateB, path.states[1])
        assertEquals(edgeAB, path.edges[0])
    }
} 