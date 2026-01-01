package com.ai.assistance.operit.util.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

@RunWith(AndroidJUnit4::class)
@MediumTest
class StatefulGraphBuilderTest {

    @Test
    fun testBasicGraphConstruction() {
        val graph = StatefulGraphBuilder.create()
            .addNode("A", "起始节点")
            .addNode("B", "目标节点")
            .addStatefulEdge("A", "B", "移动", StateTransformIdentity, 1.0)
            .build()
        
        assertTrue(graph.hasNode("A"))
        assertTrue(graph.hasNode("B"))
        assertTrue(graph.hasEdge("A", "B"))
        
        val stats = graph.getStats()
        assertEquals(2, stats.nodeCount)
        assertEquals(1, stats.edgeCount)
    }

    @Test
    fun testNodeCreationMethods() {
        val builder = StatefulGraphBuilder.create()
        
        // 测试各种添加节点的方法
        builder.addNode("A", "节点A", setOf("property1"), mapOf("meta" to "data"))
        
        val customNode = Node("B", "节点B", mapOf("custom" to true))
        builder.addNode(customNode)
        
        val nodeList = listOf(
            Node("C", "节点C"),
            Node("D", "节点D")
        )
        builder.addNodes(nodeList)
        
        val graph = builder.build()
        
        assertEquals(4, graph.getAllNodes().size)
        assertTrue(graph.hasNode("A"))
        assertTrue(graph.hasNode("B"))
        assertTrue(graph.hasNode("C"))
        assertTrue(graph.hasNode("D"))
    }

    @Test
    fun testVariableTransformEdges() {
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addNode("D")
            
            // 设置单个变量
            .addSetVariableEdge("A", "B", "设置x", "x", 10)
            
            // 设置多个变量
            .addSetVariablesEdge("B", "C", "设置多个", mapOf("y" to "hello", "z" to true))
            
            // 移除变量
            .addRemoveVariableEdge("C", "D", "移除x", "x")
            
            .build()
        
        val startState = NodeState("A")
        
        // 测试第一个边：设置x=10
        val edges1 = graph.getValidOutgoingEdges(startState)
        assertEquals(1, edges1.size)
        val state1 = edges1[0].applyTransform(startState)
        assertNotNull(state1)
        assertEquals(10, state1!!.getVariable<Int>("x"))
        
        // 测试第二个边：设置多个变量
        val edges2 = graph.getValidOutgoingEdges(state1)
        assertEquals(1, edges2.size)
        val state2 = edges2[0].applyTransform(state1)
        assertNotNull(state2)
        assertEquals(10, state2!!.getVariable<Int>("x"))
        assertEquals("hello", state2.getVariable<String>("y"))
        assertEquals(true, state2.getVariable<Boolean>("z"))
        
        // 测试第三个边：移除x
        val edges3 = graph.getValidOutgoingEdges(state2)
        assertEquals(1, edges3.size)
        val state3 = edges3[0].applyTransform(state2)
        assertNotNull(state3)
        assertNull(state3!!.getVariable<Int>("x"))
        assertEquals("hello", state3.getVariable<String>("y"))
        assertEquals(true, state3.getVariable<Boolean>("z"))
    }

    @Test
    fun testConditionalAndComputeEdges() {
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            
            // 条件设置边
            .addConditionalSetEdge(
                "A", "B", "条件设置",
                condition = { state -> (state.getVariable<Int>("x") ?: 0) > 5 },
                "result", "success"
            )
            
            // 计算变量边
            .addComputeVariableEdge(
                "B", "C", "计算总和",
                "sum",
                { state ->
                    val x = state.getVariable<Int>("x") ?: 0
                    val y = state.getVariable<Int>("y") ?: 0
                    x + y
                }
            )
            
            .build()
        
        // 测试条件不满足的情况
        val state1 = NodeState("A", mapOf("x" to 3))
        val edges1 = graph.getValidOutgoingEdges(state1)
        assertTrue(edges1.isEmpty()) // 条件不满足，不能通过
        
        // 测试条件满足的情况
        val state2 = NodeState("A", mapOf("x" to 10))
        val edges2 = graph.getValidOutgoingEdges(state2)
        assertEquals(1, edges2.size)
        val resultState = edges2[0].applyTransform(state2)
        assertNotNull(resultState)
        assertEquals("success", resultState!!.getVariable<String>("result"))
        
        // 测试计算边
        val state3 = NodeState("B", mapOf("x" to 5, "y" to 3, "result" to "success"))
        val edges3 = graph.getValidOutgoingEdges(state3)
        assertEquals(1, edges3.size)
        val finalState = edges3[0].applyTransform(state3)
        assertNotNull(finalState)
        assertEquals(8, finalState!!.getVariable<Int>("sum"))
    }

    @Test
    fun testCompositeTransformEdge() {
        val transforms = listOf(
            StateTransforms.set("step", 1),
            StateTransforms.compute("doubled") { state ->
                (state.getVariable<Int>("step") ?: 0) * 2
            },
            StateTransforms.set("complete", true)
        )
        
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addCompositeTransformEdge("A", "B", "复合操作", transforms)
            .build()
        
        val startState = NodeState("A")
        val edges = graph.getValidOutgoingEdges(startState)
        assertEquals(1, edges.size)
        
        val resultState = edges[0].applyTransform(startState)
        assertNotNull(resultState)
        assertEquals(1, resultState!!.getVariable<Int>("step"))
        assertEquals(2, resultState.getVariable<Int>("doubled"))
        assertEquals(true, resultState.getVariable<Boolean>("complete"))
    }

    @Test
    fun testBidirectionalEdges() {
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addBidirectionalStatefulEdge(
                "A", "B",
                "A到B", "B到A",
                StateTransforms.set("direction", "forward"),
                StateTransforms.set("direction", "backward")
            )
            .build()
        
        assertTrue(graph.hasEdge("A", "B"))
        assertTrue(graph.hasEdge("B", "A"))
        
        val stateA = NodeState("A")
        val stateB = NodeState("B")
        
        val edgesFromA = graph.getValidOutgoingEdges(stateA)
        assertEquals(1, edgesFromA.size)
        val resultFromA = edgesFromA[0].applyTransform(stateA)
        assertEquals("forward", resultFromA!!.getVariable<String>("direction"))
        
        val edgesFromB = graph.getValidOutgoingEdges(stateB)
        assertEquals(1, edgesFromB.size)
        val resultFromB = edgesFromB[0].applyTransform(stateB)
        assertEquals("backward", resultFromB!!.getVariable<String>("direction"))
    }

    @Test
    fun testVariableChain() {
        val nodeIds = listOf("A", "B", "C", "D")
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addNode("D")
            .createVariableChain(nodeIds, "传递", "token", "secret123")
            .build()
        
        // 验证链中的每个转换
        var currentState = NodeState("A")
        
        for (i in 0 until nodeIds.size - 1) {
            val edges = graph.getValidOutgoingEdges(currentState)
            assertEquals(1, edges.size)
            
            currentState = edges[0].applyTransform(currentState)!!
            assertEquals("secret123", currentState.getVariable<String>("token"))
        }
        
        assertEquals("D", currentState.nodeId)
    }

    @Test
    fun testAccumulatorChain() {
        val nodeIds = listOf("Start", "Step1", "Step2", "End")
        val graph = StatefulGraphBuilder.create()
            .addNode("Start")
            .addNode("Step1")
            .addNode("Step2")
            .addNode("End")
            .createAccumulatorChain(nodeIds, "累加", "count", 2)
            .build()
        
        var currentState = NodeState("Start", mapOf("count" to 0))
        
        // 第一步：0 + 2 = 2
        val edges1 = graph.getValidOutgoingEdges(currentState)
        currentState = edges1[0].applyTransform(currentState)!!
        assertEquals(2, currentState.getVariable<Int>("count"))
        assertEquals("Step1", currentState.nodeId)
        
        // 第二步：2 + 2 = 4
        val edges2 = graph.getValidOutgoingEdges(currentState)
        currentState = edges2[0].applyTransform(currentState)!!
        assertEquals(4, currentState.getVariable<Int>("count"))
        assertEquals("Step2", currentState.nodeId)
        
        // 第三步：4 + 2 = 6
        val edges3 = graph.getValidOutgoingEdges(currentState)
        currentState = edges3[0].applyTransform(currentState)!!
        assertEquals(6, currentState.getVariable<Int>("count"))
        assertEquals("End", currentState.nodeId)
    }

    @Test
    fun testStatefulGraphSearcher() {
        val searcher = StatefulGraphBuilder.create()
            .addNode("A", "起始")
            .addNode("B", "中间")
            .addNode("C", "目标")
            .addSetVariableEdge("A", "B", "步骤1", "step", 1)
            .addSetVariableEdge("B", "C", "步骤2", "step", 2)
            .buildWithFinder()
        
        // 测试简单路径搜索
        val result1 = searcher.findPathTo("A", "C")
        assertTrue(result1.success)
        assertNotNull(result1.path)
        assertEquals(3, result1.path!!.states.size)
        
        // 测试带变量状态的搜索
        val result2 = searcher.findPathToVariableState(
            "A", "C", "step", 2, emptyMap()
        )
        assertTrue(result2.success)
        assertNotNull(result2.path)
        assertEquals(2, result2.path!!.endState.getVariable<Int>("step"))
        
        // 测试统计信息
        val stats = searcher.getStats()
        assertEquals(3, stats.nodeCount)
        assertEquals(2, stats.edgeCount)
    }

    @Test
    fun testEdgeConditions() {
        val graph = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addStatefulEdge("A", "B", "需要钥匙", StateTransformIdentity, 1.0, setOf("hasKey"))
            .addStatefulEdge("B", "C", "不需要条件", StateTransformIdentity, 1.0)
            .build()
        
        val state = NodeState("A")
        
        // 没有条件时无法通过第一条边
        val edges1 = graph.getValidOutgoingEdges(state, emptySet())
        assertTrue(edges1.isEmpty())
        
        // 有条件时可以通过
        val edges2 = graph.getValidOutgoingEdges(state, setOf("hasKey"))
        assertEquals(1, edges2.size)
    }

    @Test
    fun testComplexGameScenario() {
        // 构建一个复杂的游戏场景
        val searcher = StatefulGraphBuilder.create()
            // 添加房间节点
            .addNode("entrance", "入口")
            .addNode("hallway", "走廊")
            .addNode("keyRoom", "钥匙房间")
            .addNode("treasureRoom", "宝藏房间")
            .addNode("exit", "出口")
            
            // 入口到走廊
            .addSetVariableEdge("entrance", "hallway", "进入", "location", "hallway")
            
            // 走廊到钥匙房间
            .addCompositeTransformEdge("hallway", "keyRoom", "探索", listOf(
                StateTransforms.set("location", "keyRoom"),
                StateTransforms.set("hasKey", true)
            ))
            
            // 钥匙房间回到走廊
            .addSetVariableEdge("keyRoom", "hallway", "返回", "location", "hallway")
            
            // 走廊到宝藏房间（需要钥匙）
            .addStatefulEdge("hallway", "treasureRoom", "开门", 
                StateTransforms.composite(
                    StateTransforms.set("location", "treasureRoom"),
                    StateTransforms.set("hasTreasure", true)
                ),
                1.0, setOf("hasKey")
            )
            
            // 宝藏房间到出口
            .addSetVariableEdge("treasureRoom", "exit", "离开", "location", "exit")
            
            .buildWithFinder()
        
        // 打印图信息
        Log.d("TestGameScenario", "Graph built: ${searcher.getStats()}")
        searcher.graph.getAllEdges().forEach { edge ->
            Log.d("TestGameScenario", "Edge: ${edge.from} -> ${edge.to}, action: ${edge.action}, conditions: ${edge.conditions}")
        }
        
        // 测试完整的游戏流程
        val result = searcher.findPathToVariableState(
            "entrance", "exit", "hasTreasure", true
        )
        
        // 打印搜索结果
        Log.d("TestGameScenario", "Search result: success=${result.success}, path=${result.path}")
        result.path?.states?.forEachIndexed { index, state ->
            Log.d("TestGameScenario", "Path[$index]: ${state.nodeId} with vars ${state.variables}")
        }
        
        assertTrue(result.success)
        assertNotNull(result.path)
        
        val finalState = result.path!!.endState
        assertEquals("exit", finalState.nodeId)
        assertEquals(true, finalState.getVariable<Boolean>("hasKey"))
        assertEquals(true, finalState.getVariable<Boolean>("hasTreasure"))
        assertEquals("exit", finalState.getVariable<String>("location"))
        
        // 验证路径经过的节点
        val nodeIds = result.path!!.nodeIds
        assertTrue(nodeIds.contains("entrance"))
        assertTrue(nodeIds.contains("keyRoom"))
        assertTrue(nodeIds.contains("treasureRoom"))
        assertTrue(nodeIds.contains("exit"))
    }

    @Test
    fun testBuilderChaining() {
        // 测试构建器的流式API
        val result = StatefulGraphBuilder
            .create()
            .addNode("A")
            .addNode("B")
            .addNode("C")
            .addSetVariableEdge("A", "B", "step1", "x", 1)
            .addSetVariableEdge("B", "C", "step2", "y", 2)
            .buildWithFinder()
            .findPathTo("A", "C")
        
        assertTrue(result.success)
        assertEquals(3, result.path!!.states.size)
    }

    @Test
    fun testGraphModification() {
        val builder = StatefulGraphBuilder.create()
            .addNode("A")
            .addNode("B")
        
        // 添加多条边
        val edges = listOf(
            StatefulEdge("A", "B", "move1", 1.0, stateTransform = StateTransformIdentity),
            StatefulEdge("B", "A", "move2", 2.0, stateTransform = StateTransformIdentity)
        )
        builder.addStatefulEdges(edges)
        
        val graph = builder.build()
        
        assertTrue(graph.hasEdge("A", "B"))
        assertTrue(graph.hasEdge("B", "A"))
        assertEquals(2, graph.getStats().edgeCount)
    }

    @Test
    fun testErrorHandling() {
        val builder = StatefulGraphBuilder.create()
            .addNode("A")
        
        // 尝试添加指向不存在节点的边
        try {
            builder.addStatefulEdge("A", "NonExistent", "invalid")
            builder.build()
            fail("应该抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不存在"))
        }
    }
} 