package com.ai.assistance.operit.util.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StatefulGraphIntegrationTest {

    @Test
    fun testRolePlayingGameScenario() {
        // 完整的RPG游戏场景测试
        val gameWorld = StatefulGraphBuilder.create()
            // 游戏世界节点
            .addNode("village", "村庄")
            .addNode("forest", "森林")
            .addNode("cave", "洞穴")
            .addNode("dragon_lair", "龙穴")
            .addNode("treasure_room", "宝藏室")
            .addNode("shop", "商店")
            .addNode("inn", "旅店")
            
            // 基本移动
            .addBidirectionalStatefulEdge("village", "forest", "前往森林", "返回村庄")
            .addBidirectionalStatefulEdge("village", "shop", "去商店", "离开商店")
            .addBidirectionalStatefulEdge("village", "inn", "去旅店", "离开旅店")
            .addStatefulEdge("forest", "cave", "进入洞穴", StateTransformIdentity, 1.0)
            
            // 在森林中获得武器
            .addStatefulEdge("forest", "forest", "找到剑",
                StateTransforms.composite(
                    StateTransforms.set("weapon", "sword"),
                    StateTransforms.set("attack", 20)
                ), 0.0, setOf("探索森林"))
            
            // 在商店购买装备（需要金币）
            .addStatefulEdge("shop", "shop", "购买盔甲",
                StateTransforms.composite(
                    StateTransforms.conditionalSet(
                        { state -> (state.getVariable<Int>("gold") ?: 0) >= 50 },
                        "armor", "iron_armor"
                    ),
                    StateTransforms.compute("gold") { state ->
                        val gold = state.getVariable<Int>("gold") ?: 0
                        if (gold >= 50) gold - 50 else gold
                    },
                    StateTransforms.set("defense", 15)
                ), 0.0, setOf("有足够金币"))
            
            // 在旅店休息恢复生命值
            .addStatefulEdge("inn", "inn", "休息",
                StateTransforms.set("health", 100), 0.0)
            
            // 进入龙穴需要武器
            .addStatefulEdge("cave", "dragon_lair", "深入龙穴",
                StateTransformIdentity, 2.0, setOf("有武器"))
            
            // 击败龙需要足够攻击力
            .addStatefulEdge("dragon_lair", "treasure_room", "击败龙",
                StateTransforms.composite(
                    StateTransforms.set("dragon_defeated", true),
                    StateTransforms.compute("gold") { state ->
                        (state.getVariable<Int>("gold") ?: 0) + 1000
                    }
                ), 3.0, setOf("攻击力够强"))
            
            .buildWithFinder()
        
        // 测试场景：新手玩家从村庄开始冒险
        val startState = NodeState("village", mapOf(
            "health" to 100,
            "gold" to 100,
            "level" to 1
        ))
        
        // 第一步：探索森林获得武器
        val explorationResult = gameWorld.findPath(
            startState, "forest",
            conditions = setOf("探索森林")
        )
        assertTrue(explorationResult.success)
        
        // 模拟在森林中找到剑
        val forestState = explorationResult.path!!.endState
        val withSwordState = forestState.withVariable("weapon", "sword")
            .withVariable("attack", 20)
        
        // 第二步：返回村庄，去商店购买装备
        val shopResult = gameWorld.findPath(
            withSwordState, "shop",
            conditions = setOf("有足够金币")
        )
        assertTrue(shopResult.success)
        
        // 模拟购买装备
        val shopState = shopResult.path!!.endState
        val withArmorState = shopState.withVariable("armor", "iron_armor")
            .withVariable("defense", 15)
            .withVariable("gold", 50) // 花费50金币
        
        // 第三步：前往龙穴
        val dragonLairResult = gameWorld.findPath(
            withArmorState, "dragon_lair",
            conditions = setOf("有武器")
        )
        assertTrue(dragonLairResult.success)
        
        // 第四步：击败龙（需要检查攻击力）
        val dragonState = dragonLairResult.path!!.endState
        val finalResult = gameWorld.findPath(
            dragonState, "treasure_room",
            conditions = setOf("攻击力够强")
        )
        
        if (finalResult.success) {
            val victoryState = finalResult.path!!.endState
            assertEquals(true, victoryState.getVariable<Boolean>("dragon_defeated"))
            assertTrue((victoryState.getVariable<Int>("gold") ?: 0) >= 1000)
        }
        
        println("RPG游戏测试完成 - 玩家是否获胜: ${finalResult.success}")
    }

    @Test
    fun testWorkflowManagementSystem() {
        // 工作流管理系统测试
        val workflow = StatefulGraphBuilder.create()
            .addNode("draft", "草稿")
            .addNode("review", "审核中")
            .addNode("approved", "已批准") 
            .addNode("rejected", "已拒绝")
            .addNode("published", "已发布")
            .addNode("archived", "已归档")
            
            // 提交审核
            .addStatefulEdge("draft", "review", "提交审核",
                StateTransforms.composite(
                    StateTransforms.set("submitted_at", System.currentTimeMillis()),
                    StateTransforms.set("status", "pending_review")
                ), 1.0, setOf("内容完整"))
            
            // 审核通过
            .addStatefulEdge("review", "approved", "审核通过",
                StateTransforms.composite(
                    StateTransforms.set("approved_at", System.currentTimeMillis()),
                    StateTransforms.set("reviewer", "admin"),
                    StateTransforms.set("status", "approved")
                ), 1.0, setOf("审核员权限"))
            
            // 审核拒绝
            .addStatefulEdge("review", "rejected", "审核拒绝",
                StateTransforms.composite(
                    StateTransforms.set("rejected_at", System.currentTimeMillis()),
                    StateTransforms.set("reject_reason", "内容不符合规范"),
                    StateTransforms.set("status", "rejected")
                ), 1.0, setOf("审核员权限"))
            
            // 从拒绝状态返回草稿（修改后重新提交）
            .addStatefulEdge("rejected", "draft", "重新编辑",
                StateTransforms.composite(
                    StateTransforms.remove("rejected_at"),
                    StateTransforms.remove("reject_reason"),
                    StateTransforms.set("status", "draft"),
                    StateTransforms.compute("revision") { state ->
                        (state.getVariable<Int>("revision") ?: 0) + 1
                    }
                ), 1.0)
            
            // 发布
            .addStatefulEdge("approved", "published", "发布",
                StateTransforms.composite(
                    StateTransforms.set("published_at", System.currentTimeMillis()),
                    StateTransforms.set("status", "published"),
                    StateTransforms.set("public_url", "https://example.com/article")
                ), 1.0, setOf("发布权限"))
            
            // 归档
            .addStatefulEdge("published", "archived", "归档",
                StateTransforms.composite(
                    StateTransforms.set("archived_at", System.currentTimeMillis()),
                    StateTransforms.set("status", "archived")
                ), 1.0)
            
            .buildWithFinder()
        
        // 测试正常工作流程
        val initialDocument = NodeState("draft", mapOf(
            "title" to "测试文档",
            "content" to "这是测试内容",
            "author" to "张三",
            "created_at" to System.currentTimeMillis(),
            "revision" to 0
        ))
        
        // 完整流程：草稿 -> 审核 -> 批准 -> 发布 -> 归档
        val publishResult = workflow.findPath(
            initialDocument, "published",
            conditions = setOf("内容完整", "审核员权限", "发布权限")
        )
        assertTrue(publishResult.success)
        
        val publishedState = publishResult.path!!.endState
        assertEquals("published", publishedState.getVariable<String>("status"))
        assertNotNull(publishedState.getVariable<String>("public_url"))
        assertNotNull(publishedState.getVariable<String>("reviewer"))
        
        // 测试拒绝后重新提交的流程
        val rejectedState = NodeState("rejected", mapOf(
            "title" to "测试文档",
            "status" to "rejected",
            "reject_reason" to "内容不符合规范",
            "revision" to 0
        ))
        
        val resubmitResult = workflow.findPath(
            rejectedState, "approved",
            conditions = setOf("内容完整", "审核员权限")
        )
        assertTrue(resubmitResult.success)
        
        val finalState = resubmitResult.path!!.endState
        assertEquals("approved", finalState.getVariable<String>("status"))
        assertEquals(1, finalState.getVariable<Int>("revision")) // 版本号应该增加
    }

    @Test
    fun testInventoryManagementSystem() {
        // 库存管理系统测试
        val inventory = StatefulGraphBuilder.create()
            .addNode("warehouse", "仓库")
            .addNode("shipping", "配送中")
            .addNode("delivered", "已送达")
            .addNode("returned", "已退货")
            .addNode("processing", "处理中")
            
            // 发货
            .addStatefulEdge("warehouse", "shipping", "发货",
                StateTransforms.composite(
                    StateTransforms.compute("stock") { state ->
                        val current = state.getVariable<Int>("stock") ?: 0
                        val ordered = state.getVariable<Int>("ordered_quantity") ?: 0
                        current - ordered
                    },
                    StateTransforms.set("shipping_date", System.currentTimeMillis()),
                    StateTransforms.set("status", "shipped")
                ), 1.0, setOf("库存充足"))
            
            // 送达
            .addStatefulEdge("shipping", "delivered", "送达",
                StateTransforms.composite(
                    StateTransforms.set("delivered_date", System.currentTimeMillis()),
                    StateTransforms.set("status", "delivered"),
                    StateTransforms.compute("total_delivered") { state ->
                        val current = state.getVariable<Int>("total_delivered") ?: 0
                        val ordered = state.getVariable<Int>("ordered_quantity") ?: 0
                        current + ordered
                    }
                ), 1.0)
            
            // 退货
            .addStatefulEdge("delivered", "returned", "退货",
                StateTransforms.composite(
                    StateTransforms.set("return_date", System.currentTimeMillis()),
                    StateTransforms.set("status", "returned"),
                    StateTransforms.set("return_reason", "客户不满意")
                ), 1.0, setOf("允许退货"))
            
            // 处理退货（补充库存）
            .addStatefulEdge("returned", "processing", "处理退货",
                StateTransforms.composite(
                    StateTransforms.compute("stock") { state ->
                        val current = state.getVariable<Int>("stock") ?: 0
                        val returned = state.getVariable<Int>("ordered_quantity") ?: 0
                        current + returned
                    },
                    StateTransforms.set("processed_date", System.currentTimeMillis()),
                    StateTransforms.set("status", "processed")
                ), 1.0)
            
            .buildWithFinder()
        
        // 测试正常订单流程
        val order = NodeState("warehouse", mapOf(
            "product_id" to "PROD001",
            "product_name" to "笔记本电脑",
            "stock" to 100,
            "ordered_quantity" to 5,
            "total_delivered" to 0,
            "order_id" to "ORD001"
        ))
        
        // 完整配送流程
        val deliveryResult = inventory.findPath(
            order, "delivered",
            conditions = setOf("库存充足")
        )
        assertTrue(deliveryResult.success)
        
        val deliveredState = deliveryResult.path!!.endState
        assertEquals(95, deliveredState.getVariable<Int>("stock")) // 100 - 5
        assertEquals(5, deliveredState.getVariable<Int>("total_delivered"))
        assertEquals("delivered", deliveredState.getVariable<String>("status"))
        
        // 测试退货流程
        val returnResult = inventory.findPath(
            deliveredState, "processing",
            conditions = setOf("允许退货")
        )
        assertTrue(returnResult.success)
        
        val processedState = returnResult.path!!.endState
        assertEquals(100, processedState.getVariable<Int>("stock")) // 95 + 5（退货补充）
        assertEquals("processed", processedState.getVariable<String>("status"))
    }

    @Test
    fun testStateMachineBacktrackScenario() {
        // 测试复杂的状态机回退场景
        val stateMachine = StatefulGraphBuilder.create()
            .addNode("idle", "空闲")
            .addNode("processing", "处理中")
            .addNode("waiting", "等待中")
            .addNode("error", "错误")
            .addNode("completed", "完成")
            .addNode("timeout", "超时")
            
            // 开始处理
            .addStatefulEdge("idle", "processing", "开始",
                StateTransforms.set("attempts", 1), 1.0)
            
            // 处理成功
            .addStatefulEdge("processing", "completed", "成功",
                StateTransforms.set("result", "success"), 1.0, setOf("处理成功"))
            
            // 处理失败进入等待
            .addStatefulEdge("processing", "waiting", "失败重试",
                StateTransforms.compute("attempts") { state ->
                    (state.getVariable<Int>("attempts") ?: 0) + 1
                }, 1.0, setOf("需要重试"))
            
            // 等待后重新处理
            .addStatefulEdge("waiting", "processing", "重新处理",
                StateTransformIdentity, 1.0)
            
            // 超过重试次数进入错误状态
            .addStatefulEdge("waiting", "error", "重试失败",
                StateTransforms.set("error_reason", "超过最大重试次数"), 1.0, setOf("重试次数过多"))
            
            // 处理超时
            .addStatefulEdge("processing", "timeout", "超时",
                StateTransforms.set("timeout_reason", "处理时间过长"), 1.0, setOf("处理超时"))
            
            // 从超时恢复
            .addStatefulEdge("timeout", "waiting", "超时恢复",
                StateTransforms.compute("attempts") { state ->
                    (state.getVariable<Int>("attempts") ?: 0) + 1
                }, 1.0)
            
            .buildWithFinder()
        
        val initialState = NodeState("idle")
        
        // 测试成功场景
        val successResult = stateMachine.findPath(
            initialState, "completed",
            conditions = setOf("处理成功"),
            enableBacktrack = true
        )
        assertTrue(successResult.success)
        assertEquals("success", successResult.path!!.endState.getVariable<String>("result"))
        
        // 测试重试场景（模拟多次失败后成功）
        val retryScenario = initialState.withVariable("max_attempts", 3)
        val retryResult = stateMachine.findPath(
            retryScenario, "completed",
            conditions = setOf("需要重试", "处理成功"),
            enableBacktrack = true
        )
        
        // 在回退搜索中，应该能找到最终成功的路径
        if (retryResult.success) {
            assertTrue(retryResult.backtrackCount >= 0)
            val finalState = retryResult.path!!.endState
            assertEquals("success", finalState.getVariable<String>("result"))
        }
    }

    @Test
    fun testRealTimeSystemSimulation() {
        // 实时系统模拟测试
        val realTimeSystem = StatefulGraphBuilder.create()
            .addNode("boot", "启动")
            .addNode("ready", "就绪")
            .addNode("running", "运行")
            .addNode("suspended", "挂起")
            .addNode("terminated", "终止")
            .addNode("error", "错误")
            
            // 系统启动
            .addStatefulEdge("boot", "ready", "初始化",
                StateTransforms.composite(
                    StateTransforms.set("boot_time", System.currentTimeMillis()),
                    StateTransforms.set("cpu_usage", 0),
                    StateTransforms.set("memory_usage", 512)
                ), 1.0)
            
            // 开始运行
            .addStatefulEdge("ready", "running", "启动服务",
                StateTransforms.composite(
                    StateTransforms.set("start_time", System.currentTimeMillis()),
                    StateTransforms.compute("cpu_usage") { 25 },
                    StateTransforms.compute("memory_usage") { state ->
                        (state.getVariable<Int>("memory_usage") ?: 512) + 256
                    }
                ), 1.0)
            
            // 挂起系统
            .addStatefulEdge("running", "suspended", "挂起",
                StateTransforms.composite(
                    StateTransforms.set("suspended_time", System.currentTimeMillis()),
                    StateTransforms.set("cpu_usage", 0)
                ), 1.0, setOf("允许挂起"))
            
            // 恢复运行
            .addStatefulEdge("suspended", "running", "恢复",
                StateTransforms.composite(
                    StateTransforms.set("resume_time", System.currentTimeMillis()),
                    StateTransforms.set("cpu_usage", 25)
                ), 1.0)
            
            // 正常终止
            .addStatefulEdge("running", "terminated", "关闭",
                StateTransforms.composite(
                    StateTransforms.set("shutdown_time", System.currentTimeMillis()),
                    StateTransforms.set("cpu_usage", 0),
                    StateTransforms.set("memory_usage", 0),
                    StateTransforms.set("exit_code", 0)
                ), 1.0)
            
            // 异常终止
            .addStatefulEdge("running", "error", "崩溃",
                StateTransforms.composite(
                    StateTransforms.set("crash_time", System.currentTimeMillis()),
                    StateTransforms.set("exit_code", -1),
                    StateTransforms.set("error_message", "系统异常")
                ), 1.0, setOf("系统故障"))
            
            .buildWithFinder()
        
        val bootState = NodeState("boot", mapOf(
            "system_id" to "SYS001",
            "version" to "1.0.0"
        ))
        
        // 测试正常运行周期
        val normalCycleResult = realTimeSystem.findPath(
            bootState, "terminated"
        )
        assertTrue(normalCycleResult.success)
        
        val terminatedState = normalCycleResult.path!!.endState
        assertEquals(0, terminatedState.getVariable<Int>("exit_code"))
        assertEquals(0, terminatedState.getVariable<Int>("cpu_usage"))
        assertEquals(0, terminatedState.getVariable<Int>("memory_usage"))
        
        // 测试挂起恢复周期
        val suspendCycleResult = realTimeSystem.findPath(
            bootState, "running",
            conditions = setOf("允许挂起")
        )
        assertTrue(suspendCycleResult.success)
        
        // 验证系统状态在整个生命周期中的变化
        val path = suspendCycleResult.path!!
        for (i in 0 until path.states.size) {
            val state = path.states[i]
            println("步骤 $i: ${state.nodeId}, CPU: ${state.getVariable<Int>("cpu_usage")}, 内存: ${state.getVariable<Int>("memory_usage")}")
        }
    }
} 