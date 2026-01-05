package com.ai.phoneagent.core.tools

import android.content.Context
import android.util.Log
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.ToolResult
import com.ai.phoneagent.data.model.StringResultData
import java.util.concurrent.ConcurrentHashMap

/**
 * AI工具处理器
 * 负责工具的注册、查找和执行
 */
class AIToolHandler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AIToolHandler"

        @Volatile
        private var INSTANCE: AIToolHandler? = null

        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 工具注册表
    private val availableTools = ConcurrentHashMap<String, ToolExecutor>()

    // 危险操作检查注册表
    private val dangerousOperationsRegistry = ConcurrentHashMap<String, (AITool) -> Boolean>()

    // 操作描述生成器注册表
    private val operationDescriptionRegistry = ConcurrentHashMap<String, (AITool) -> String>()

    /**
     * 注册工具
     * @param name 工具名称
     * @param dangerCheck 危险操作检查函数（可选）
     * @param descriptionGenerator 操作描述生成器（可选）
     * @param executor 工具执行器
     */
    fun registerTool(
        name: String,
        dangerCheck: ((AITool) -> Boolean)? = null,
        descriptionGenerator: ((AITool) -> String)? = null,
        executor: ToolExecutor
    ) {
        availableTools[name] = executor

        // 注册危险操作检查
        if (dangerCheck != null) {
            dangerousOperationsRegistry[name] = dangerCheck
        }

        // 注册描述生成器
        if (descriptionGenerator != null) {
            operationDescriptionRegistry[name] = descriptionGenerator
        }

        Log.d(TAG, "Registered tool: $name")
    }

    /**
     * 注销工具
     */
    fun unregisterTool(toolName: String) {
        availableTools.remove(toolName)
        dangerousOperationsRegistry.remove(toolName)
        operationDescriptionRegistry.remove(toolName)
    }

    /**
     * 获取所有已注册的工具名称
     */
    fun getAllToolNames(): List<String> {
        return availableTools.keys.toList().sorted()
    }

    /**
     * 检查工具是否为危险操作
     */
    fun isDangerousOperation(tool: AITool): Boolean {
        val check = dangerousOperationsRegistry[tool.name] ?: return false
        return check(tool)
    }

    /**
     * 生成操作描述
     */
    fun getOperationDescription(tool: AITool): String {
        val generator = operationDescriptionRegistry[tool.name]
        return generator?.invoke(tool) ?: "执行 ${tool.name}"
    }

    /**
     * 执行工具
     */
    suspend fun executeTool(tool: AITool): ToolResult {
        val executor = availableTools[tool.name]
        if (executor == null) {
            Log.e(TAG, "Tool not found: ${tool.name}")
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "工具未找到: ${tool.name}"
            )
        }

        return try {
            Log.d(TAG, "Executing tool: ${tool.name}")
            executor.invoke(tool)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool: ${tool.name}", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "执行失败: ${e.message}"
            )
        }
    }
}
