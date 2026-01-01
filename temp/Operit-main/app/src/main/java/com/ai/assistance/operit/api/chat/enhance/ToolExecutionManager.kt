package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.stream.StreamCollector
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/** Utility class for managing tool executions */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"

    /**
     * 从 AI 响应中提取工具调用。
     * @param response AI 的响应字符串。
     * @return 检测到的工具调用列表。
     */
    suspend fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val content = response

        val charStream = content.stream()
        val plugins = listOf(StreamXmlPlugin())

        charStream.splitBy(plugins).collect { group ->
            val chunkContent = StringBuilder()
            group.stream.collect { chunk -> chunkContent.append(chunk) }
            val chunkString = chunkContent.toString()

            if (chunkString.isEmpty()) return@collect

            if (group.tag is StreamXmlPlugin) {
                if (chunkString.startsWith("<tool") && chunkString.contains("</tool>")) {
                    val nameMatch = MessageContentParser.namePattern.find(chunkString)
                    val toolName = nameMatch?.groupValues?.get(1) ?: return@collect

                    val parameters = mutableListOf<ToolParameter>()
                    MessageContentParser.toolParamPattern.findAll(chunkString)
                        .forEach { paramMatch ->
                            val paramName = paramMatch.groupValues[1]
                            val paramValue = paramMatch.groupValues[2]
                            parameters.add(ToolParameter(paramName, unescapeXml(paramValue)))
                        }

                    val tool = AITool(name = toolName, parameters = parameters)
                    invocations.add(ToolInvocation(tool, chunkString, chunkString.indices))
                }
            }
        }

        AppLogger.d(
            TAG,
            "Found ${invocations.size} tool invocations: ${invocations.map { it.tool.name }}"
        )
        return invocations
    }

    /**
     * Unescapes XML special characters
     * @param input The XML escaped string
     * @return Unescaped string
     */
    private fun unescapeXml(input: String): String {
        var result = input

        // 处理 CDATA 标记
        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        // 即使没有完整的 CDATA 标记，也尝试清理末尾的 ]]> 和开头的 <![CDATA[
        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /**
     * Execute a tool safely, with parameter validation
     *
     * @param invocation The tool invocation to execute
     * @param executor The tool executor to use
     * @return The result of the tool execution
     */
    fun executeToolSafely(invocation: ToolInvocation, executor: ToolExecutor): Flow<ToolResult> {
        val validationResult = executor.validateParameters(invocation.tool)
        if (!validationResult.valid) {
            return flow {
                emit(
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "参数无效: ${validationResult.errorMessage}"
                    )
                )
            }
        }

        return executor.invokeAndStream(invocation.tool).catch { e ->
            AppLogger.e(TAG, "工具执行错误: ${invocation.tool.name}", e)
            emit(
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "工具执行错误: ${e.message}"
                )
            )
        }
    }

    /**
     * Check if a tool requires permission and verify if it has permission
     *
     * @param toolHandler The AIToolHandler instance to use for permission checks
     * @param invocation The tool invocation to check permissions for
     * @return A pair containing (has permission, error result if no permission)
     */
    suspend fun checkToolPermission(
        toolHandler: AIToolHandler,
        invocation: ToolInvocation
    ): Pair<Boolean, ToolResult?> {
        // 检查是否强制拒绝权限（deny_tool标记）
        val hasPromptForPermission = !invocation.rawText.contains("deny_tool")

        if (hasPromptForPermission) {
            // 检查权限，如果需要则弹出权限请求界面
            val toolPermissionSystem = toolHandler.getToolPermissionSystem()
            val hasPermission = toolPermissionSystem.checkToolPermission(invocation.tool)

            // 如果权限被拒绝，创建错误结果
            if (!hasPermission) {
                val errorResult =
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "User cancelled the tool execution."
                    )

                return Pair(false, errorResult)
            }
        }

        // 有权限
        return Pair(true, null)
    }

    /**
     *
     * 执行工具调用，包括权限检查、并行/串行执行和结果聚合。
     * @param invocations 要执行的工具调用列表。
     * @param toolHandler AIToolHandler 的实例。
     * @param packageManager PackageManager 的实例。
     * @param collector 用于实时输出结果的 StreamCollector。
     * @return 所有工具执行结果的列表。
     */
    suspend fun executeInvocations(
        invocations: List<ToolInvocation>,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        collector: StreamCollector<String>
    ): List<ToolResult> = coroutineScope {
        // 默认工具注册现在可能在启动阶段被延后；这里确保在真正执行工具前已完成注册
        // registerDefaultTools() 是幂等且线程安全的，可安全重复调用
        withContext(Dispatchers.Default) {
            toolHandler.registerDefaultTools()
        }

        // 1. 权限检查
        val permittedInvocations = mutableListOf<ToolInvocation>()
        val permissionDeniedResults = mutableListOf<ToolResult>()
        for (invocation in invocations) {
            val (hasPermission, errorResult) = checkToolPermission(toolHandler, invocation)
            if (hasPermission) {
                permittedInvocations.add(invocation)
            } else {
                errorResult?.let {
                    permissionDeniedResults.add(it)
                    val toolResultStatusContent =
                        ConversationMarkupManager.formatToolResultForMessage(it)
                    collector.emit(toolResultStatusContent)
                }
            }
        }

        // 2. 按并行/串行对工具进行分组
        val parallelizableToolNames = setOf(
            "list_files", "read_file", "read_file_part", "read_file_full", "file_exists",
            "find_files", "file_info", "grep_code", "query_memory", "calculate", "ffmpeg_info"
        )
        val (parallelInvocations, serialInvocations) = permittedInvocations.partition {
            parallelizableToolNames.contains(
                it.tool.name
            )
        }

        // 3. 执行工具并收集聚合结果
        val executionResults = ConcurrentHashMap<ToolInvocation, ToolResult>()

        // 启动并行工具
        val parallelJobs = parallelInvocations.map { invocation ->
            async {
                val result = executeAndEmitTool(invocation, toolHandler, packageManager, collector)
                executionResults[invocation] = result
            }
        }

        // 顺序执行串行工具
        for (invocation in serialInvocations) {
            val result = executeAndEmitTool(invocation, toolHandler, packageManager, collector)
            executionResults[invocation] = result
        }

        // 等待所有并行任务完成
        parallelJobs.awaitAll()

        // 4. 按原始顺序重新排序结果
        val orderedAggregated = permittedInvocations.mapNotNull { executionResults[it] }

        // 5. 组合所有结果并返回
        permissionDeniedResults + orderedAggregated
    }

    /**
     * 封装单个工具的执行、实时输出和结果聚合的辅助函数
     */
    private suspend fun executeAndEmitTool(
        invocation: ToolInvocation,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        collector: StreamCollector<String>
    ): ToolResult {
        var executor = toolHandler.getToolExecutor(invocation.tool.name)
        if (executor == null) {
            val toolName = invocation.tool.name
            // 尝试自动激活包
            val activated = tryActivatePackage(toolName, packageManager, toolHandler)
            if (activated) {
                // 激活后再次获取执行器
                executor = toolHandler.getToolExecutor(invocation.tool.name)
            }

            if (executor == null) {
                // 如果仍然为 null，则构建错误消息
                val errorMessage =
                    buildToolNotAvailableErrorMessage(toolName, packageManager, toolHandler)
                val notAvailableContent =
                    ConversationMarkupManager.createToolNotAvailableError(toolName, errorMessage)
                collector.emit(notAvailableContent)
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = errorMessage
                )
            }
        }

        val collectedResults = mutableListOf<ToolResult>()
        executeToolSafely(invocation, executor).collect { result ->
            collectedResults.add(result)
            // 实时输出每个结果
            val toolResultStatusContent =
                ConversationMarkupManager.formatToolResultForMessage(result)
            collector.emit(toolResultStatusContent)
        }

        // 为此调用聚合最终结果
        if (collectedResults.isEmpty()) {
            return ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = StringResultData(""),
                error = "工具执行后未返回任何结果。"
            )
        }

        val lastResult = collectedResults.last()
        val combinedResultString = collectedResults.joinToString("\n") { res ->
            (if (res.success) res.result.toString() else "步骤错误: ${res.error ?: "未知错误"}").trim()
        }.trim()

        return ToolResult(
            toolName = invocation.tool.name,
            success = lastResult.success,
            result = StringResultData(combinedResultString),
            error = lastResult.error
        )
    }

    /**
     * 构建工具不可用的错误信息，统一逻辑避免重复
     */
    private suspend fun buildToolNotAvailableErrorMessage(
        toolName: String,
        packageManager: PackageManager,
        toolHandler: AIToolHandler
    ): String {
        return when {
            toolName.contains('.') && !toolName.contains(':') -> {
                val parts = toolName.split('.', limit = 2)
                "工具调用语法错误: 对于工具包中的工具，应使用 'packName:toolName' 格式，而不是 '${toolName}'。您可能想调用 '${
                    parts.getOrNull(
                        0
                    )
                }:${parts.getOrNull(1)}'。"
            }

            toolName.contains(':') -> {
                val parts = toolName.split(':', limit = 2)
                val packName = parts[0]
                val toolNamePart = parts.getOrNull(1) ?: ""
                val isAvailable = packageManager.getAvailablePackages().containsKey(packName)

                if (!isAvailable) {
                    "工具包 '$packName' 不存在。"
                } else {
                    // 包存在，检查是否已激活（通过检查该包的任何工具是否已注册）
                    val packageTools =
                        packageManager.getPackageTools(packName)?.tools ?: emptyList()
                    val isPackageActivated = packageTools.any {
                        toolHandler.getToolExecutor("$packName:${it.name}") != null
                    }

                    if (isPackageActivated) {
                        // 包已激活但工具不存在
                        "工具 '$toolNamePart' 在工具包 '$packName' 中不存在。请使用 'use_package' 工具并指定包名 '$packName' 来查看该包的所有可用工具。"
                    } else {
                        // 包未激活
                        "工具包 '$packName' 未激活。已尝试自动激活但失败或工具 '$toolNamePart' 不存在。请使用 'use_package' 并指定包名 '$packName' 检查可用工具。"
                    }
                }
            }

            else -> {
                // 检查是否直接把包名当作工具名调用了
                val isPackageName = packageManager.getAvailablePackages().containsKey(toolName)
                if (isPackageName) {
                    "错误: '$toolName' 是一个工具包，不是工具。请先使用 'use_package' 工具并指定包名 '$toolName' 来激活这个工具包，然后才能使用其中的工具。"
                } else {
                    "工具 '${toolName}' 不可用或不存在。如果这是一个工具包中的工具，请使用 'packName:toolName' 格式调用。"
                }
            }
        }
    }

    /**
     * 尝试自动激活一个工具包，并验证激活结果
     */
    private fun tryActivatePackage(
        toolName: String,
        packageManager: PackageManager,
        toolHandler: AIToolHandler
    ): Boolean {
        if (!toolName.contains(':')) {
            return false
        }

        val parts = toolName.split(':', limit = 2)
        val packName = parts[0]

        // 已经有执行器就不需要再激活
        val isToolAlreadyRegistered = toolHandler.getToolExecutor(toolName) != null
        if (isToolAlreadyRegistered) {
            return true
        }

        // JS 工具包是否可用
        val isJsPackageAvailable = packageManager.getAvailablePackages().containsKey(packName)
        // MCP 服务器是否已注册
        val isMcpServerAvailable = packageManager.getAvailableServerPackages().containsKey(packName)

        if (isJsPackageAvailable || isMcpServerAvailable) {
            AppLogger.d(TAG, "尝试自动激活工具包或MCP服务器: $packName for tool $toolName")
            // 调用 usePackage 来加载和注册工具（对于 MCP 会转到 useMCPServer）
            packageManager.usePackage(packName)
            // 激活后，再次检查工具是否已注册
            return toolHandler.getToolExecutor(toolName) != null
        }

        return false
    }
}
