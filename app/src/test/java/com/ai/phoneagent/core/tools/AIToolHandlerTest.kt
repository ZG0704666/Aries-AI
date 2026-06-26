package com.ai.phoneagent.core.tools

import android.content.Context
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * AIToolHandler 单元测试
 *
 * 说明：AIToolHandler 使用 private constructor + getInstance(context) 单例模式，
 * 无法在纯 JVM 单元测试中通过 getInstance 创建实例（getInstance 需要真实 Android Context）。
 * 因此本测试通过反射调用 private constructor 直接构造实例，绕过单例缓存，
 * 每个测试方法都能获得一个拥有空注册表的新实例，保证测试之间相互隔离。
 *
 * 注意：AIToolHandler 内部使用了 android.util.Log。本项目在 app/build.gradle.kts 中
 * 配置了 testOptions { unitTests { isReturnDefaultValues = true } }，使 android.util.Log
 * 等框架方法在 JVM 单元测试中返回默认值（int 0）而非抛出 "not mocked" 异常，
 * 因此无需额外 Mock 即可正常执行被测代码中的 Log.d / Log.e 调用。
 */
class AIToolHandlerTest {

    private lateinit var handler: AIToolHandler

    @Before
    fun setUp() {
        // 通过反射调用 private constructor 创建新实例（传入 null 作为 Context，
        // 因为被测方法 registerTool/unregisterTool/getAllToolNames/isDangerousOperation/
        // getOperationDescription/executeTool 均不使用 context 字段）
        val constructor =
            AIToolHandler::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        handler = constructor.newInstance(null as Context?) as AIToolHandler
    }

    // ─── registerTool / getAllToolNames ───────────────────────────────────────

    @Test
    fun `registered tool is findable via getAllToolNames`() {
        handler.registerTool("screenshot") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("ok"))
        }
        assertTrue("screenshot" in handler.getAllToolNames())
    }

    @Test
    fun `getAllToolNames returns empty list when nothing registered`() {
        assertTrue(handler.getAllToolNames().isEmpty())
    }

    @Test
    fun `getAllToolNames returns all registered tool names`() {
        handler.registerTool("zebra") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }
        handler.registerTool("apple") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }
        handler.registerTool("mango") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }

        val names = handler.getAllToolNames()
        // getAllToolNames 返回排序后的列表
        assertEquals(listOf("apple", "mango", "zebra"), names)
        assertEquals(3, names.size)
    }

    // ─── unregisterTool ────────────────────────────────────────────────────────

    @Test
    fun `unregistered tool is no longer findable`() {
        handler.registerTool("screenshot") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("ok"))
        }
        assertTrue("screenshot" in handler.getAllToolNames())

        handler.unregisterTool("screenshot")
        assertFalse("screenshot" in handler.getAllToolNames())
    }

    @Test
    fun `unregisterTool on non-existent tool does not throw`() {
        // 注销一个从未注册的工具不应抛出异常
        handler.unregisterTool("never_registered")
        assertTrue(handler.getAllToolNames().isEmpty())
    }

    @Test
    fun `unregisterTool removes danger check and description generator`() {
        handler.registerTool(
            name = "delete_file",
            dangerCheck = { true },
            descriptionGenerator = { "删除文件" },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("deleted"))
        }

        val tool = AITool(name = "delete_file")
        // 注册后应为危险操作且有自定义描述
        assertTrue(handler.isDangerousOperation(tool))
        assertEquals("删除文件", handler.getOperationDescription(tool))

        handler.unregisterTool("delete_file")
        // 注销后应为非危险操作且使用默认描述
        assertFalse(handler.isDangerousOperation(tool))
        assertEquals("执行 delete_file", handler.getOperationDescription(tool))
    }

    // ─── isDangerousOperation ──────────────────────────────────────────────────

    @Test
    fun `isDangerousOperation returns false when no danger check registered`() {
        handler.registerTool("safe_tool") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("ok"))
        }
        val tool = AITool(name = "safe_tool")
        assertFalse(handler.isDangerousOperation(tool))
    }

    @Test
    fun `isDangerousOperation returns true when check returns true`() {
        handler.registerTool(
            name = "delete_file",
            dangerCheck = { true },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }
        val tool = AITool(name = "delete_file")
        assertTrue(handler.isDangerousOperation(tool))
    }

    @Test
    fun `isDangerousOperation returns false when check returns false`() {
        handler.registerTool(
            name = "read_file",
            dangerCheck = { false },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }
        val tool = AITool(name = "read_file")
        assertFalse(handler.isDangerousOperation(tool))
    }

    @Test
    fun `isDangerousOperation returns false for unregistered tool`() {
        val tool = AITool(name = "unknown_tool")
        // 未注册工具没有危险检查函数，应返回 false
        assertFalse(handler.isDangerousOperation(tool))
    }

    @Test
    fun `isDangerousOperation danger check receives tool with parameters`() {
        var receivedTool: AITool? = null
        handler.registerTool(
            name = "execute_sql",
            dangerCheck = { t ->
                receivedTool = t
                t.parameters.any { it.name == "sql" && it.value.contains("DROP") }
            },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }

        val tool = AITool(
            name = "execute_sql",
            parameters = listOf(
                com.ai.phoneagent.data.model.ToolParameter("sql", "DROP TABLE users;")
            )
        )
        assertTrue(handler.isDangerousOperation(tool))
        assertNotNull(receivedTool)
        assertEquals("execute_sql", receivedTool!!.name)
    }

    // ─── getOperationDescription ───────────────────────────────────────────────

    @Test
    fun `getOperationDescription returns default when no generator registered`() {
        handler.registerTool("screenshot") { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("ok"))
        }
        val tool = AITool(name = "screenshot")
        assertEquals("执行 screenshot", handler.getOperationDescription(tool))
    }

    @Test
    fun `getOperationDescription returns custom description when generator registered`() {
        handler.registerTool(
            name = "screenshot",
            descriptionGenerator = { "截取屏幕截图" },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData("ok"))
        }
        val tool = AITool(name = "screenshot")
        assertEquals("截取屏幕截图", handler.getOperationDescription(tool))
    }

    @Test
    fun `getOperationDescription returns default for unregistered tool`() {
        val tool = AITool(name = "unknown_tool")
        assertEquals("执行 unknown_tool", handler.getOperationDescription(tool))
    }

    @Test
    fun `getOperationDescription generator receives tool`() {
        var receivedTool: AITool? = null
        handler.registerTool(
            name = "tap",
            descriptionGenerator = { t ->
                receivedTool = t
                "点击坐标 (${t.parameters.joinToString { "${it.name}=${it.value}" }})"
            },
        ) { tool ->
            ToolResult(toolName = tool.name, success = true, result = StringResultData(""))
        }

        val tool = AITool(
            name = "tap",
            parameters = listOf(
                com.ai.phoneagent.data.model.ToolParameter("x", "500"),
                com.ai.phoneagent.data.model.ToolParameter("y", "600"),
            )
        )
        val desc = handler.getOperationDescription(tool)
        assertNotNull(receivedTool)
        assertEquals("tap", receivedTool!!.name)
        assertTrue(desc.contains("点击坐标"))
        assertTrue(desc.contains("500"))
        assertTrue(desc.contains("600"))
    }

    // ─── executeTool ───────────────────────────────────────────────────────────

    @Test
    fun `executeTool returns success result for registered tool`() = runBlocking {
        handler.registerTool("get_time") { tool ->
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("12:00:00")
            )
        }
        val tool = AITool(name = "get_time")
        val result = handler.executeTool(tool)

        assertEquals("get_time", result.toolName)
        assertTrue(result.success)
        // ToolResult.error 默认为空字符串，成功执行时应为空
        assertEquals("", result.error)
        assertTrue(result.result is StringResultData)
        assertEquals("12:00:00", (result.result as StringResultData).data)
    }

    @Test
    fun `executeTool returns error for unregistered tool`() = runBlocking {
        val tool = AITool(name = "non_existent_tool")
        val result = handler.executeTool(tool)

        assertEquals("non_existent_tool", result.toolName)
        assertFalse(result.success)
        assertTrue(result.error.contains("工具未找到"))
        assertTrue(result.error.contains("non_existent_tool"))
    }

    @Test
    fun `executeTool passes tool parameters to executor`() = runBlocking {
        var receivedTool: AITool? = null
        handler.registerTool("echo") { tool ->
            receivedTool = tool
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(tool.parameters.joinToString { it.value })
            )
        }

        val tool = AITool(
            name = "echo",
            parameters = listOf(
                com.ai.phoneagent.data.model.ToolParameter("msg", "hello"),
                com.ai.phoneagent.data.model.ToolParameter("lang", "zh"),
            )
        )
        val result = handler.executeTool(tool)

        assertNotNull(receivedTool)
        assertEquals("echo", receivedTool!!.name)
        assertEquals(2, receivedTool!!.parameters.size)
        assertTrue(result.success)
        val data = (result.result as StringResultData).data
        assertTrue(data.contains("hello"))
        assertTrue(data.contains("zh"))
    }

    @Test
    fun `executeTool returns error when executor throws exception`() = runBlocking {
        handler.registerTool("failing_tool") { _ ->
            throw RuntimeException("boom")
        }
        val tool = AITool(name = "failing_tool")
        val result = handler.executeTool(tool)

        assertEquals("failing_tool", result.toolName)
        assertFalse(result.success)
        assertTrue(result.error.contains("执行失败"))
        assertTrue(result.error.contains("boom"))
    }

    @Test
    fun `executeTool returns correct toolName in error for unregistered tool`() = runBlocking {
        val tool = AITool(name = "missing")
        val result = handler.executeTool(tool)

        assertEquals("missing", result.toolName)
        assertFalse(result.success)
    }
}
