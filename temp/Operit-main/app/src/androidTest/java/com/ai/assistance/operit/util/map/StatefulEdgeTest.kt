package com.ai.assistance.operit.util.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class StatefulEdgeTest {

    @Test
    fun testBasicStatefulEdge() {
        val edge = StatefulEdge("A", "B", "move", 2.5)
        
        assertEquals("A", edge.from)
        assertEquals("B", edge.to)
        assertEquals("move", edge.action)
        assertEquals(2.5, edge.weight, 0.001)
        assertEquals(StateTransformIdentity, edge.stateTransform)
        assertTrue(edge.conditions.isEmpty())
    }

    @Test
    fun testEdgeWithConditions() {
        val conditions = setOf("hasKey", "isUnlocked")
        val edge = StatefulEdge("A", "B", "openDoor", 1.0, conditions)
        
        assertEquals(conditions, edge.conditions)
        
        val state = NodeState("A")
        
        // 没有满足条件时不能通过
        assertFalse(edge.canTraverse(state, emptySet()))
        assertFalse(edge.canTraverse(state, setOf("hasKey")))
        
        // 满足所有条件时可以通过
        assertTrue(edge.canTraverse(state, conditions))
        assertTrue(edge.canTraverse(state, setOf("hasKey", "isUnlocked", "extra")))
    }

    @Test
    fun testEdgeParameterAccess() {
        val parameters = mapOf("cost" to 10, "message" to "hello")
        val edge = StatefulEdge("A", "B", "action", parameters = parameters)
        
        assertEquals(10, edge.getParameter<Int>("cost"))
        assertEquals("hello", edge.getParameter<String>("message"))
        assertNull(edge.getParameter<String>("cost")) // 错误类型
        assertNull(edge.getParameter<Int>("missing")) // 不存在的参数
    }

    @Test
    fun testStateTransformIdentity() {
        val transform = StateTransformIdentity
        val state = NodeState("A", mapOf("x" to 1, "y" to "hello"))
        
        assertTrue(transform.canApply(state))
        assertEquals(state, transform.apply(state))
    }

    @Test
    fun testStateTransformSetVariable() {
        val transform = StateTransforms.set("x", 42)
        val state = NodeState("A", mapOf("y" to "hello"))
        
        assertTrue(transform.canApply(state))
        val newState = transform.apply(state)
        assertNotNull(newState)
        assertEquals(42, newState!!.getVariable<Int>("x"))
        assertEquals("hello", newState.getVariable<String>("y"))
    }

    @Test
    fun testStateTransformSetVariables() {
        val variables = mapOf("x" to 10, "z" to true)
        val transform = StateTransforms.setAll(variables)
        val state = NodeState("A", mapOf("y" to "hello"))
        
        assertTrue(transform.canApply(state))
        val newState = transform.apply(state)
        assertNotNull(newState)
        assertEquals(10, newState!!.getVariable<Int>("x"))
        assertEquals("hello", newState.getVariable<String>("y"))
        assertEquals(true, newState.getVariable<Boolean>("z"))
    }

    @Test
    fun testStateTransformRemoveVariable() {
        val transform = StateTransforms.remove("x")
        val state = NodeState("A", mapOf("x" to 1, "y" to "hello"))
        
        assertTrue(transform.canApply(state))
        val newState = transform.apply(state)
        assertNotNull(newState)
        assertNull(newState!!.getVariable<Int>("x"))
        assertEquals("hello", newState.getVariable<String>("y"))
    }

    @Test
    fun testStateTransformConditionalSet() {
        val condition: (NodeState) -> Boolean = { state ->
            (state.getVariable<Int>("x") ?: 0) > 5
        }
        val transform = StateTransforms.conditionalSet(condition, "result", "success")
        
        val state1 = NodeState("A", mapOf("x" to 10))
        val state2 = NodeState("A", mapOf("x" to 3))
        
        // 条件满足时
        assertTrue(transform.canApply(state1))
        val result1 = transform.apply(state1)
        assertNotNull(result1)
        assertEquals("success", result1!!.getVariable<String>("result"))
        
        // 条件不满足时
        assertFalse(transform.canApply(state2))
        val result2 = transform.apply(state2)
        assertNull(result2)
    }

    @Test
    fun testStateTransformComputeVariable() {
        val computation: (NodeState) -> Any? = { state ->
            val x = state.getVariable<Int>("x") ?: 0
            val y = state.getVariable<Int>("y") ?: 0
            x + y
        }
        val transform = StateTransforms.compute("sum", computation)
        val state = NodeState("A", mapOf("x" to 3, "y" to 7))
        
        assertTrue(transform.canApply(state))
        val newState = transform.apply(state)
        assertNotNull(newState)
        assertEquals(10, newState!!.getVariable<Int>("sum"))
    }

    @Test
    fun testStateTransformComposite() {
        val transforms = listOf(
            StateTransforms.set("x", 5),
            StateTransforms.set("y", 10),
            StateTransforms.compute("sum") { state ->
                (state.getVariable<Int>("x") ?: 0) + (state.getVariable<Int>("y") ?: 0)
            }
        )
        val compositeTransform = StateTransforms.composite(*transforms.toTypedArray())
        val state = NodeState("A")
        
        assertTrue(compositeTransform.canApply(state))
        val newState = compositeTransform.apply(state)
        assertNotNull(newState)
        assertEquals(5, newState!!.getVariable<Int>("x"))
        assertEquals(10, newState.getVariable<Int>("y"))
        assertEquals(15, newState.getVariable<Int>("sum"))
    }

    @Test
    fun testEdgeStateTransformation() {
        val transform = StateTransforms.set("visited", true)
        val edge = StatefulEdge("A", "B", "visit", stateTransform = transform)
        val startState = NodeState("A")
        
        assertTrue(edge.canTraverse(startState, emptySet()))
        val resultState = edge.applyTransform(startState)
        assertNotNull(resultState)
        assertEquals("B", resultState!!.nodeId)
        assertEquals(true, resultState.getVariable<Boolean>("visited"))
    }

    @Test
    fun testEdgeTraversalFailure() {
        val edge = StatefulEdge("A", "B", "move", conditions = setOf("key"))
        val state = NodeState("A")
        
        // 没有条件时不能通过
        assertFalse(edge.canTraverse(state, emptySet()))
        assertNull(edge.applyTransform(state, emptySet()))
        
        // 节点ID不匹配时不能通过
        val wrongState = NodeState("C")
        assertFalse(edge.canTraverse(wrongState, setOf("key")))
        assertNull(edge.applyTransform(wrongState, setOf("key")))
    }

    @Test
    fun testComplexStateTransform() {
        // 测试复杂的状态转换：累加器
        val incrementTransform = StateTransforms.compute("count") { state ->
            (state.getVariable<Int>("count") ?: 0) + 1
        }
        
        var state = NodeState("A")
        
        // 第一次转换
        state = incrementTransform.apply(state)!!
        assertEquals(1, state.getVariable<Int>("count"))
        
        // 第二次转换
        state = incrementTransform.apply(state)!!
        assertEquals(2, state.getVariable<Int>("count"))
        
        // 第三次转换
        state = incrementTransform.apply(state)!!
        assertEquals(3, state.getVariable<Int>("count"))
    }

    @Test
    fun testCompositeTransformFailure() {
        val failingTransform = StateTransforms.conditionalSet(
            { false }, // 永远失败的条件
            "key", "value"
        )
        val successTransform = StateTransforms.set("other", "success")
        
        val compositeTransform = StateTransforms.composite(successTransform, failingTransform)
        val state = NodeState("A")
        
        // 整个复合转换应该失败
        assertFalse(compositeTransform.canApply(state))
        assertNull(compositeTransform.apply(state))
    }

    @Test
    fun testTransformToString() {
        val identityTransform = StateTransformIdentity
        val setTransform = StateTransforms.set("x", 10)
        val removeTransform = StateTransforms.remove("y")
        val compositeTransform = StateTransforms.composite(setTransform, removeTransform)
        
        assertEquals("StateTransformIdentity", identityTransform.toString())
        assertEquals("StateTransform.Set(key=x, value=10)", setTransform.toString())
        assertEquals("StateTransform.Remove(key=y)", removeTransform.toString())
        assertTrue(compositeTransform.toString().contains("StateTransform.Composite"))
    }

    @Test
    fun testEdgeToString() {
        val edge = StatefulEdge("A", "B", "move", 2.5, setOf("key"))
        val edgeStr = edge.toString()
        
        assertTrue(edgeStr.contains("A"))
        assertTrue(edgeStr.contains("B"))
        assertTrue(edgeStr.contains("move"))
    }

    @Test
    fun testComputeVariableWithNullResult() {
        val failingComputation: (NodeState) -> Any? = { null }
        val transform = StateTransforms.compute("result", failingComputation)
        val state = NodeState("A")
        
        assertTrue(transform.canApply(state))
        val result = transform.apply(state)
        assertNull(result) // 应该返回null，因为计算结果为null
    }

    @Test
    fun testRealWorldScenario() {
        // 模拟游戏场景：捡起物品
        val pickupTransform = StateTransforms.composite(
            StateTransforms.set("hasItem", true),
            StateTransforms.compute("inventory") { state ->
                val current = state.getVariable<List<String>>("inventory") ?: emptyList()
                current + "sword"
            }
        )
        
        val edge = StatefulEdge(
            "room1", "room2", "pickup_sword", 
            stateTransform = pickupTransform,
            conditions = setOf("itemAvailable")
        )
        
        val playerState = NodeState("room1", mapOf(
            "inventory" to listOf("potion")
        ))
        
        assertTrue(edge.canTraverse(playerState, setOf("itemAvailable")))
        val newState = pickupTransform.apply(playerState)
        assertNotNull(newState)
        
        // assertEquals("room2", newState!!.nodeId) //
        assertEquals(true, newState!!.getVariable<Boolean>("hasItem"))
        val inventory = newState!!.getVariable<List<String>>("inventory")
        assertNotNull(inventory)
        assertEquals(listOf("potion", "sword"), inventory)
    }
} 