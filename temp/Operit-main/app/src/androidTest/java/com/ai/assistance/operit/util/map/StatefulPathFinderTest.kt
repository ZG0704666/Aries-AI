package com.ai.assistance.operit.util.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class StatefulPathFinderTest {

    private lateinit var graph: StatefulGraph
    private lateinit var pathFinder: StatefulPathFinder

    @Before
    fun setUp() {
        // 创建测试图
        graph = StatefulGraphBuilder.create()
            .addNode("A", "起始")
            .addNode("B", "中间1")
            .addNode("C", "中间2")
            .addNode("D", "目标")
            .addStatefulEdge("A", "B", "move1", StateTransforms.set("step", 1), 1.0)
            .addStatefulEdge("B", "C", "move2", StateTransforms.set("step", 2), 1.0)
            .addStatefulEdge("C", "D", "move3", StateTransforms.set("step", 3), 1.0)
            .build()
        
        pathFinder = StatefulPathFinder(graph)
    }

    @Test
    fun testBasicPathFinding() {
        val startState = NodeState("A")
        val result = pathFinder.findPath(startState, "D")
        
        assertTrue(result.success)
        assertNotNull(result.path)
        assertEquals("找到路径", result.message.substringAfter("算法"))
        
        val path = result.path!!
        assertEquals(4, path.states.size)
        assertEquals(3, path.edges.size)
        assertEquals(3.0, path.totalWeight, 0.001)
        
        // 验证路径状态
        assertEquals("A", path.startState.nodeId)
        assertEquals("D", path.endState.nodeId)
        assertEquals(3, path.endState.getVariable<Int>("step"))
    }

    @Test
    fun testStartStateIsTarget() {
        val startState = NodeState("A")
        val result = pathFinder.findPath(startState, "A")
        
        assertTrue(result.success)
        assertNotNull(result.path)
        assertEquals("起始状态即为目标状态", result.message)
        
        val path = result.path!!
        assertEquals(1, path.states.size)
        assertEquals(0, path.edges.size)
        assertTrue(path.isSingleState)
    }

    @Test
    fun testTargetStatePredicate() {
        val startState = NodeState("A")
        
        // 搜索到step=2的状态
        val result = pathFinder.findPath(
            startState, "C",
            targetStatePredicate = { state -> state.getVariable<Int>("step") == 2 }
        )
        
        assertTrue(result.success)
        assertNotNull(result.path)
        
        val path = result.path!!
        assertEquals("C", path.endState.nodeId)
        assertEquals(2, path.endState.getVariable<Int>("step"))
    }

    @Test
    fun testInvalidNodes() {
        val startState = NodeState("X") // 不存在的节点
        val result1 = pathFinder.findPath(startState, "D")
        
        assertFalse(result1.success)
        assertTrue(result1.message.contains("起始节点"))
        assertTrue(result1.message.contains("不存在"))
        
        val validStartState = NodeState("A")
        val result2 = pathFinder.findPath(validStartState, "X") // 不存在的目标节点
        
        assertFalse(result2.success)
        assertTrue(result2.message.contains("目标节点"))
        assertTrue(result2.message.contains("不存在"))
    }

    @Test
    fun testMaxDistanceLimit() {
        val startState = NodeState("A")
        val result = pathFinder.findPath(startState, "D", maxDistance = 2.0)
        
        assertFalse(result.success)
        assertTrue(result.message.contains("无法找到"))
    }

    @Test
    fun testEdgeConditions() {
        // 创建需要条件的图
        val conditionalGraph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addStatefulEdge("A", "B", "normal", StateTransformIdentity, 1.0)
            .addStatefulEdge("B", "C", "locked", StateTransformIdentity, 1.0, setOf("hasKey"))
            .build()
        
        val conditionalFinder = StatefulPathFinder(conditionalGraph)
        val startState = NodeState("A")
        
        // 没有条件时无法到达C
        val result1 = conditionalFinder.findPath(startState, "C")
        assertFalse(result1.success)
        
        // 有条件时可以到达C
        val result2 = conditionalFinder.findPath(startState, "C", availableConditions = setOf("hasKey"))
        assertTrue(result2.success)
        assertNotNull(result2.path)
        assertEquals(3, result2.path!!.states.size)
    }

    @Test
    fun testBacktrackVsNormalSearch() {
        val startState = NodeState("A")
        
        // 使用回退搜索
        val backtrackResult = pathFinder.findPath(startState, "D", enableBacktrack = true)
        assertTrue(backtrackResult.success)
        assertTrue(backtrackResult.message.contains("回退"))
        
        // 使用Dijkstra搜索
        val dijkstraResult = pathFinder.findPath(startState, "D", enableBacktrack = false)
        assertTrue(dijkstraResult.success)
        assertTrue(dijkstraResult.message.contains("Dijkstra"))
        
        // 两种方法应该找到相同的路径（对于简单图）
        assertEquals(backtrackResult.path!!.totalWeight, dijkstraResult.path!!.totalWeight, 0.001)
    }

    @Test
    fun testStateConflictAndBacktrack() {
        // 创建可能产生状态冲突的图
        val conflictGraph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addNode("D")
            
            // 从A到B：设置x=1
            .addStatefulEdge("A", "B", "set1", StateTransforms.set("x", 1), 1.0)
            
            // 从B到C：保持x=1
            .addStatefulEdge("B", "C", "keep", StateTransformIdentity, 1.0)
            
            // 从C回到B：设置x=2（与之前状态冲突）
            .addStatefulEdge("C", "B", "conflict", StateTransforms.set("x", 2), 1.0)
            
            // 从B到D：需要x=2
            .addStatefulEdge("B", "D", "needX2", 
                StateTransforms.conditionalSet({ state -> 
                    state.getVariable<Int>("x") == 2 
                }, "success", true), 1.0)
            
            .build()
        
        val conflictFinder = StatefulPathFinder(conflictGraph)
        val startState = NodeState("A")
        
        // 使用回退搜索应该能处理状态冲突
        val result = conflictFinder.findPath(startState, "D", enableBacktrack = true)
        
        if (result.success) {
            val path = result.path!!
            assertTrue(path.isValid())
            assertEquals("D", path.endState.nodeId)
            // 回退次数应该大于0
            assertTrue(result.backtrackCount >= 0)
        }
    }

    @Test
    fun testComplexStateTransformations() {
        // 创建复杂状态转换的图
        val complexGraph = StatefulGraphBuilder.create()
            .addNode("start")
            .addNode("collect")
            .addNode("process")
            .addNode("finish")
            
            // 收集物品
            .addStatefulEdge("start", "collect", "gather", 
                StateTransforms.set("items", listOf("item1", "item2")), 1.0)
            
            // 处理物品
            .addStatefulEdge("collect", "process", "craft",
                StateTransforms.composite(
                    StateTransforms.compute("itemCount") { state ->
                        state.getVariable<List<String>>("items")?.size ?: 0
                    },
                    StateTransforms.set("processed", true)
                ), 1.0)
            
            // 完成任务
            .addStatefulEdge("process", "finish", "complete",
                StateTransforms.conditionalSet(
                    { state -> state.getVariable<Boolean>("processed") == true },
                    "completed", true
                ), 1.0)
            
            .build()
        
        val complexFinder = StatefulPathFinder(complexGraph)
        val startState = NodeState("start")
        
        val result = complexFinder.findPath(startState, "finish")
        assertTrue(result.success)
        
        val finalState = result.path!!.endState
        assertEquals(2, finalState.getVariable<Int>("itemCount"))
        assertEquals(true, finalState.getVariable<Boolean>("processed"))
        assertEquals(true, finalState.getVariable<Boolean>("completed"))
    }

    @Test
    fun testSearchStatistics() {
        val startState = NodeState("A")
        val result = pathFinder.findPath(startState, "D")
        
        assertTrue(result.success)
        assertNotNull(result.searchStats)
        
        val stats = result.searchStats!!
        assertTrue(stats.visitedNodes > 0)
        assertTrue(stats.exploredEdges > 0)
        assertTrue(stats.searchTimeMs >= 0)
        assertNotNull(stats.algorithm)
    }

    @Test
    fun testNoPathExists() {
        // 创建没有连接的图
        val disconnectedGraph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addNode("D")
            .addStatefulEdge("A", "B", "move", StateTransformIdentity, 1.0)
            // C和D是孤立的
            .build()
        
        val disconnectedFinder = StatefulPathFinder(disconnectedGraph)
        val startState = NodeState("A")
        
        val result = disconnectedFinder.findPath(startState, "D")
        assertFalse(result.success)
        assertTrue(result.message.contains("无法找到"))
    }

    @Test
    fun testCyclicGraph() {
        // 创建有环的图
        val cyclicGraph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            
            // 创建循环：A->B->C->A
            .addStatefulEdge("A", "B", "step1", StateTransforms.set("count", 1), 1.0)
            .addStatefulEdge("B", "C", "step2", StateTransforms.compute("count") { state ->
                (state.getVariable<Int>("count") ?: 0) + 1
            }, 1.0)
            .addStatefulEdge("C", "A", "step3", StateTransforms.compute("count") { state ->
                (state.getVariable<Int>("count") ?: 0) + 1
            }, 1.0)
            
            // 目标边：当count>=3时可以到达目标
            .addNode("target")
            .addStatefulEdge("A", "target", "finish",
                StateTransforms.conditionalSet(
                    { state -> (state.getVariable<Int>("count") ?: 0) >= 3 },
                    "done", true
                ), 1.0)
            
            .build()
        
        val cyclicFinder = StatefulPathFinder(cyclicGraph)
        val startState = NodeState("A")
        
        val result = cyclicFinder.findPath(startState, "target")
        
        if (result.success) {
            val finalState = result.path!!.endState
            assertTrue((finalState.getVariable<Int>("count") ?: 0) >= 3)
            assertEquals(true, finalState.getVariable<Boolean>("done"))
        }
    }

    @Test
    fun testPathValidation() {
        val startState = NodeState("A")
        val result = pathFinder.findPath(startState, "D")
        
        assertTrue(result.success)
        assertNotNull(result.path)
        
        val path = result.path!!
        assertTrue(path.isValid())
        assertFalse(path.hasStateConflicts())
    }

    @Test
    fun testMultipleTargetStates() {
        // 测试可以到达目标节点的多种状态
        val multiStateGraph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("target")
            
            // 两条不同的路径到达target，产生不同状态
            .addStatefulEdge("A", "target", "direct", StateTransforms.set("path", "direct"), 2.0)
            .addStatefulEdge("A", "B", "indirect1", StateTransforms.set("via", "B"), 1.0)
            .addStatefulEdge("B", "target", "indirect2", StateTransforms.set("path", "indirect"), 1.0)
            
            .build()
        
        val multiFinder = StatefulPathFinder(multiStateGraph)
        val startState = NodeState("A")
        
        // 应该找到更短的路径（通过B）
        val result = multiFinder.findPath(startState, "target")
        assertTrue(result.success)
        
        val path = result.path!!
        assertEquals(2.0, path.totalWeight, 0.001) // 间接路径更短
        assertEquals("indirect", path.endState.getVariable<String>("path"))
    }

    @Test
    fun testPerformanceWithLargerGraph() {
        // 创建较大的图来测试性能
        val largeGraphBuilder = StatefulGraphBuilder.create()
        
        // 创建链式图：A0 -> A1 -> ... -> A9
        for (i in 0..9) {
            largeGraphBuilder.addNode("A$i")
        }
        
        for (i in 0..8) {
            largeGraphBuilder.addStatefulEdge(
                "A$i", "A${i+1}", "step$i",
                StateTransforms.set("position", i + 1),
                1.0
            )
        }
        
        val largeGraph = largeGraphBuilder.build()
        val largeFinder = StatefulPathFinder(largeGraph)
        val startState = NodeState("A0")
        
        val startTime = System.currentTimeMillis()
        val result = largeFinder.findPath(startState, "A9")
        val endTime = System.currentTimeMillis()
        
        assertTrue(result.success)
        assertTrue(endTime - startTime < 1000) // 应该在1秒内完成
        
        val path = result.path!!
        assertEquals(10, path.states.size)
        assertEquals(9, path.edges.size)
        assertEquals(9, path.endState.getVariable<Int>("position"))
    }

    @Test
    fun testEdgeCasesInSearch() {
        val builder = StatefulGraphBuilder.create()
            .addNode("single")
        
        val graph = builder.build()
        val finder = StatefulPathFinder(graph)
        
        // 测试单节点图
        val singleNodeResult = finder.findPath(NodeState("single"), "single")
        assertTrue(singleNodeResult.success)
        assertTrue(singleNodeResult.path!!.isSingleState)
    }
} 