package com.ai.phoneagent.core

import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.parser.ActionParser
import com.ai.phoneagent.core.templates.PromptTemplates
import com.ai.phoneagent.core.utils.ActionUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Agent 核心模块单元测试
 * 测试重构后的各组件功能
 */
class CoreModuleTest {

    // ========== 配置层测试 ==========
    
    @Test
    fun `AgentConfiguration 默认值测试`() {
        val config = AgentConfiguration.DEFAULT
        
        assertEquals(100, config.maxSteps)
        assertEquals(160L, config.stepDelayMs)
        assertEquals(3, config.maxModelRetries)
        assertEquals(20000, config.maxContextTokens)
        assertTrue(config.enableScreenshotCache)
        assertTrue(config.enableScreenshotThrottle)
    }
    
    @Test
    fun `AgentConfiguration getActionDelayMs 测试`() {
        val config = AgentConfiguration.DEFAULT
        
        assertEquals(1050L, config.getActionDelayMs("launch"))
        assertEquals(260L, config.getActionDelayMs("type"))
        assertEquals(320L, config.getActionDelayMs("tap"))
        assertEquals(420L, config.getActionDelayMs("swipe"))
        assertEquals(220L, config.getActionDelayMs("back"))
        assertEquals(240L, config.getActionDelayMs("unknown"))
    }
    
    @Test
    fun `AgentConfiguration getAwaitWindowTimeoutMs 测试`() {
        val config = AgentConfiguration.DEFAULT
        
        assertEquals(2200L, config.getAwaitWindowTimeoutMs("launch"))
        assertEquals(1400L, config.getAwaitWindowTimeoutMs("back"))
        assertEquals(1500L, config.getAwaitWindowTimeoutMs("unknown"))
    }
    
    @Test
    fun `AgentConfiguration isDangerousKeyword 测试`() {
        val config = AgentConfiguration.DEFAULT
        
        assertTrue(config.isDangerousKeyword("请输入支付密码"))
        assertTrue(config.isDangerousKeyword("请确认付款"))
        assertFalse(config.isDangerousKeyword("这是一个普通文本"))
    }
    
    // ========== 动作解析器测试 ==========
    
    @Test
    fun `ActionParser 解析 do 动作`() {
        val parser = ActionParser()
        val action = parser.parse("do(action=\"Tap\", element=[500,500])")
        
        assertEquals("do", action.metadata)
        assertEquals("Tap", action.actionName)
        assertEquals("500", action.fields["element"]?.split(",")?.first())
    }
    
    @Test
    fun `ActionParser 解析 finish 动作`() {
        val parser = ActionParser()
        val action = parser.parse("finish(message=\"任务完成\")")
        
        assertEquals("finish", action.metadata)
        assertEquals("任务完成", action.fields["message"])
    }
    
    @Test
    fun `ActionParser 解析带思考的回答`() {
        val parser = ActionParser()
        val (thinking, answer) = parser.parseWithThinking("""
            【思考开始】
            我需要点击这个按钮来完成操作
            【思考结束】
            
            【回答开始】
            do(action="Tap", element=[300,400])
            【回答结束】
        """.trimIndent())
        
        assertNotNull(thinking)
        assertTrue(thinking!!.contains("点击"))
        assertNotNull(answer)
        assertTrue(answer!!.contains("do(action="))
    }
    
    @Test
    fun `ActionParser 解析预估步骤数`() {
        val parser = ActionParser()
        
        // 显式数字
        val steps1 = parser.parseEstimatedSteps("我需要大约5步完成")
        assertEquals(5, steps1)
        
        // 编号步骤
        val steps2 = parser.parseEstimatedSteps("第一步...第二步...第三步")
        assertEquals(3, steps2)
    }
    
    // ========== 工具类测试 ==========
    // 注意：ActionUtils 是 object（单例），直接用 ActionUtils.method() 调用
    
    @Test
    fun `ActionUtils 解析坐标点`() {
        val point = ActionUtils.parsePoint("[500,600]")
        assertNotNull(point)
        assertEquals(500, point?.first)
        assertEquals(600, point?.second)

        val floatPoint = ActionUtils.parsePoint("[500.6, 200.4]")
        assertNotNull(floatPoint)
        assertEquals(501, floatPoint?.first)
        assertEquals(200, floatPoint?.second)

        val percentPoint = ActionUtils.parsePoint("[50%,25%]")
        assertNotNull(percentPoint)
        assertEquals(500, percentPoint?.first)
        assertEquals(250, percentPoint?.second)
    }
    
    @Test
    fun `ActionUtils 敏感内容检测`() {
        assertTrue(ActionUtils.looksSensitive("请输入支付密码"))
        assertTrue(ActionUtils.looksSensitive("请确认银行卡号"))
        assertFalse(ActionUtils.looksSensitive("这是一个普通输入框"))
    }
    
    @Test
    fun `ActionUtils 截断 UI 树`() {
        val longTree = "A".repeat(5000)
        val truncated = ActionUtils.truncateUiTree(longTree, 1000)
        
        assertTrue(truncated.length < 5000)
        assertTrue(truncated.contains("已截断"))
    }
    
    @Test
    fun `ActionUtils 估算 token`() {
        // 英文
        val englishTokens = ActionUtils.estimateTokens("Hello World")
        assertTrue(englishTokens > 0)
        
        // 中文（token 估算不同）
        val chineseTokens = ActionUtils.estimateTokens("你好世界")
        assertTrue(chineseTokens > 0)
    }
    
    @Test
    fun `ActionUtils 计算模型重试延迟`() {
        assertEquals(700L, ActionUtils.computeModelRetryDelayMs(0, 700L))
        assertEquals(1400L, ActionUtils.computeModelRetryDelayMs(1, 700L))
        assertEquals(2800L, ActionUtils.computeModelRetryDelayMs(2, 700L))
    }
    
    // ========== 模板测试 ==========
    
    @Test
    fun `PromptTemplates 构建系统提示词`() {
        val prompt = PromptTemplates.buildSystemPrompt(1080, 1920, null)
        
        assertTrue(prompt.contains("手机 UI 自动化助手"))
        assertTrue(prompt.contains("1080"))
        assertTrue(prompt.contains("1920"))
        assertTrue(prompt.contains("Launch"))
        assertTrue(prompt.contains("Tap"))
        assertTrue(prompt.contains("Type"))
    }
    
    @Test
    fun `PromptTemplates 构建修复提示词`() {
        val repairPrompt = PromptTemplates.buildRepairPrompt()
        
        assertTrue(repairPrompt.contains("格式错误"))
        assertTrue(repairPrompt.contains("do(action="))
    }
    
    @Test
    fun `PromptTemplates 构建动作修复提示词`() {
        val actionRepairPrompt = PromptTemplates.buildActionRepairPrompt("Tap(500,500)")
        
        assertTrue(actionRepairPrompt.contains("Tap(500,500)"))
        assertTrue(actionRepairPrompt.contains("执行失败"))
    }
    
    // ========== 配置测试模式 ==========
    
    @Test
    fun `AgentConfiguration TEST 配置适用于测试`() {
        val testConfig = AgentConfiguration.TEST
        
        assertEquals(10, testConfig.maxSteps)
        assertEquals(50L, testConfig.stepDelayMs)
        assertEquals(1, testConfig.maxModelRetries)
    }
}

/**
 * 性能测试 - 确保新架构没有性能退化
 */
class PerformanceTest {

    @Test
    fun `ActionParser 大量解析性能`() {
        val parser = ActionParser()
        val actions = listOf(
            "do(action=\"Tap\", element=[500,500])",
            "do(action=\"Type\", text=\"测试文本\")",
            "do(action=\"Swipe\", start=[0,500], end=[0,1000])",
            "finish(message=\"任务完成\")"
        )
        
        val startTime = System.currentTimeMillis()
        repeat(1000) {
            actions.forEach { parser.parse(it) }
        }
        val elapsed = System.currentTimeMillis() - startTime
        
        // 1000 次解析应该在 1 秒内完成
        assertTrue("解析 1000 次动作耗时：${elapsed}ms", elapsed < 2000)
    }
    
    @Test
    fun `ActionUtils 截断大文本性能`() {
        val largeText = "A".repeat(100000)
        
        val startTime = System.currentTimeMillis()
        repeat(100) {
            ActionUtils.truncateUiTree(largeText, 3000)
        }
        val elapsed = System.currentTimeMillis() - startTime
        
        // 100 次截断应该在 500ms 内完成
        assertTrue("截断 100 次大文本耗时：${elapsed}ms", elapsed < 500)
    }
}
