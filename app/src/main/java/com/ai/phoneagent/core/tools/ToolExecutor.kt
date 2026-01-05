package com.ai.phoneagent.core.tools

import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.ToolResult

/**
 * 工具执行器接口
 * 所有工具的执行逻辑都通过此接口实现
 */
fun interface ToolExecutor {
    /**
     * 执行工具
     * @param tool 要执行的工具及其参数
     * @return 工具执行结果
     */
    suspend fun invoke(tool: AITool): ToolResult
}
