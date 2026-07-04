/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21B: AIToolHandler 单元测试。
 *
 * 测试目标：覆盖 AIToolHandler 的工具注册、查找、执行与危险操作检查。
 *
 * 注意：AIToolHandler 是 private 构造的单例（getInstance 模式）。
 * 测试通过反射重置 INSTANCE，再用 getInstance(mockContext) 获取新实例。
 * 任务原始描述中的 handleToolCall/getRegisteredTools/getToolSchema 在实际
 * 代码中对应为 executeTool/getAllToolNames/getOperationDescription，已按实际 API 测试。
 */
package com.ai.phoneagent.core.tools

import android.content.Context
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AIToolHandler] 单元测试。
 *
 * 覆盖：工具注册/注销、executeTool 分发与错误处理、危险操作检查、
 * 操作描述生成、已注册工具列表。
 */
class AIToolHandlerTest {

    private lateinit var mockContext: Context
    private lateinit var handler: AIToolHandler

    @Before
    fun setup() {
        resetAIToolHandlerSingleton()
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        handler = AIToolHandler.getInstance(mockContext)
    }

    // ─── 单例重置 ───────────────────────────────────────────────────────

    /**
     * 通过反射重置 AIToolHandler 的单例 INSTANCE，保证测试间隔离。
     *
     * 注意：Kotlin 将 companion object 中的 private var 编译为外层类的 private static
     * 字段（位于 AIToolHandler 类上，而非 AIToolHandler$Companion 类上），因此用
     * AIToolHandler::class.java.getDeclaredField("INSTANCE") + field.set(null, null)。
     */
    private fun resetAIToolHandlerSingleton() {
        val field = AIToolHandler::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    // ─── 辅助：构造 ToolExecutor ────────────────────────────────────────

    private fun successExecutor(data: String = "ok"): ToolExecutor =
        object : ToolExecutor {
            override suspend fun invoke(tool: AITool): ToolResult =
                ToolResult(tool.name, success = true, result = StringResultData(data))
        }

    private fun throwingExecutor(ex: Exception): ToolExecutor =
        object : ToolExecutor {
            override suspend fun invoke(tool: AITool): ToolResult { throw ex }
        }

    // ─── 测试用例 ───────────────────────────────────────────────────────

    @Test
    fun `executeTool_已知工具_分发到对应执行器`() = runBlocking {
        handler.registerTool(name = "file_read", executor = successExecutor("content"))

        val result = handler.executeTool(AITool("file_read"))

        assertTrue("已注册工具应执行成功", result.success)
        assertEquals("file_read", result.toolName)
        assertEquals("content", (result.result as StringResultData).data)
    }

    @Test
    fun `executeTool_未知工具_返回错误`() = runBlocking {
        val result = handler.executeTool(AITool("not_registered"))

        assertFalse("未知工具应返回失败", result.success)
        assertTrue("应包含工具未找到错误", result.error.contains("未找到"))
    }

    @Test
    fun `executeTool_工具名空_返回错误`() = runBlocking {
        val result = handler.executeTool(AITool(""))

        assertFalse("空工具名应返回失败", result.success)
        assertTrue("应包含工具未找到错误", result.error.contains("未找到"))
    }

    @Test
    fun `executeTool_空参数列表_正常执行`() = runBlocking {
        // AITool 使用结构化参数列表（非 JSON），空参数应正常执行
        handler.registerTool(name = "noop", executor = successExecutor("done"))

        val result = handler.executeTool(AITool(name = "noop", parameters = emptyList()))

        assertTrue("空参数列表应正常执行", result.success)
    }

    @Test
    fun `isDangerousOperation_危险工具_返回true`() = runBlocking {
        handler.registerTool(
            name = "delete_file",
            dangerCheck = { true },
            executor = successExecutor()
        )
        handler.registerTool(
            name = "read_file",
            dangerCheck = { false },
            executor = successExecutor()
        )

        assertTrue("delete_file 应为危险操作", handler.isDangerousOperation(AITool("delete_file")))
        assertFalse("read_file 应为非危险操作", handler.isDangerousOperation(AITool("read_file")))
    }

    @Test
    fun `unregisterTool_注销后_执行返回错误`() = runBlocking {
        handler.registerTool(name = "temp_tool", executor = successExecutor())
        assertTrue("注销前应可执行", handler.executeTool(AITool("temp_tool")).success)

        handler.unregisterTool("temp_tool")

        val result = handler.executeTool(AITool("temp_tool"))
        assertFalse("注销后应返回失败", result.success)
        assertTrue("应包含工具未找到错误", result.error.contains("未找到"))
    }

    @Test
    fun `executeTool_执行器抛异常_返回错误信息`() = runBlocking {
        handler.registerTool(
            name = "crash_tool",
            executor = throwingExecutor(RuntimeException("boom"))
        )

        val result = handler.executeTool(AITool("crash_tool"))

        assertFalse("执行器抛异常应返回失败", result.success)
        assertTrue("错误信息应包含异常消息", result.error.contains("boom"))
        assertTrue("错误信息应包含执行失败前缀", result.error.contains("执行失败"))
    }

    @Test
    fun `getAllToolNames_返回所有注册工具列表`() = runBlocking {
        handler.registerTool(name = "zebra_tool", executor = successExecutor())
        handler.registerTool(name = "alpha_tool", executor = successExecutor())
        handler.registerTool(name = "middle_tool", executor = successExecutor())

        val names = handler.getAllToolNames()

        assertEquals("应包含全部 3 个工具", 3, names.size)
        // 验证已排序
        assertEquals("alpha_tool", names[0])
        assertEquals("middle_tool", names[1])
        assertEquals("zebra_tool", names[2])
    }

    @Test
    fun `getOperationDescription_已知工具_返回描述`() = runBlocking {
        handler.registerTool(
            name = "send_sms",
            descriptionGenerator = { "发送短信给联系人" },
            executor = successExecutor()
        )

        val desc = handler.getOperationDescription(AITool("send_sms"))

        assertEquals("应返回自定义描述", "发送短信给联系人", desc)
    }

    @Test
    fun `getOperationDescription_未注册工具_返回默认值`() = runBlocking {
        val desc = handler.getOperationDescription(AITool("unknown_tool"))

        // 无 descriptionGenerator 时返回默认 "执行 ${tool.name}"
        assertEquals("应返回默认描述", "执行 unknown_tool", desc)
    }
}
