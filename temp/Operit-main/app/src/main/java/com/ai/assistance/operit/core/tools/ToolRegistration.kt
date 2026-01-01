package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.services.FloatingChatService
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import com.ai.assistance.operit.integrations.tasker.triggerAIAgentAction

/**
 * This file contains all tool registrations centralized for easier maintenance and integration It
 * extracts the registerTools logic from AIToolHandler into a dedicated file
 */

/**
 * Register all available tools with the AIToolHandler
 * @param handler The AIToolHandler instance to register tools with
 * @param context Application context for tools that need it
 */
fun registerAllTools(handler: AIToolHandler, context: Context) {

    // Helper function to wrap UI tool execution with visibility changes
    suspend fun executeUiToolWithVisibility(
        tool: AITool,
        showStatusIndicator: Boolean = true,
        delayMs: Long = 50,
        action: suspend (AITool) -> ToolResult
    ): ToolResult {
        val floatingService = FloatingChatService.getInstance()
        return try {
            floatingService?.setFloatingWindowVisible(false)
            if (showStatusIndicator) {
                floatingService?.setStatusIndicatorVisible(true)
            } else {
                floatingService?.setStatusIndicatorVisible(false)
            }
            delay(delayMs)
            action(tool)
        } finally {
            floatingService?.setFloatingWindowVisible(true)
            floatingService?.setStatusIndicatorVisible(false)
        }
    }


    // 不在提示词加入的工具
    handler.registerTool(
            name = "execute_shell",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                "执行ADB Shell: $command"
            },
            executor = { tool ->
                val adbTool = ToolGetter.getShellToolExecutor(context)
                adbTool.invoke(tool)
            }
    )

    // 终端命令执行工具 - 一次性收集输出
    handler.registerTool(
            name = "create_terminal_session",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val sessionName = tool.parameters.find { it.name == "session_name" }?.value
                "创建或获取终端会话: ${sessionName ?: "未命名"}"
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.createOrGetSession(tool)
            }
    )

    handler.registerTool(
            name = "execute_in_terminal_session",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                "在终端会话 '$sessionId' 中执行: $command"
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.executeCommandInSession(tool)
            }
    )

    handler.registerTool(
            name = "close_terminal_session",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                "关闭终端会话: $sessionId"
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.closeSession(tool)
            }
    )

    // 注册问题库查询工具
    handler.registerTool(
            name = "query_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                "查询问题库: $query"
            },
            executor = { tool ->
                val problemLibraryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                problemLibraryTool.invoke(tool)
            }
    )
    
    // 注册根据标题获取单个记忆工具
    handler.registerTool(
            name = "get_memory_by_title",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                "根据标题获取记忆: $title"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册用户偏好更新工具
    handler.registerTool(
            name = "update_user_preferences",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val params = mutableListOf<String>()
                tool.parameters.forEach { param ->
                    when (param.name) {
                        "birth_date" -> params.add("生日")
                        "gender" -> params.add("性别")
                        "personality" -> params.add("性格")
                        "identity" -> params.add("身份")
                        "occupation" -> params.add("职业")
                        "ai_style" -> params.add("AI风格")
                    }
                }
                "更新用户偏好: ${params.joinToString(", ")}"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册创建记忆工具
    handler.registerTool(
            name = "create_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                "创建记忆: $title"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册更新记忆工具
    handler.registerTool(
            name = "update_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val oldTitle = tool.parameters.find { it.name == "old_title" }?.value ?: ""
                val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: oldTitle
                "更新记忆: $oldTitle -> $newTitle"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册删除记忆工具
    handler.registerTool(
            name = "delete_memory",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                "删除记忆: $title"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册链接记忆工具
    handler.registerTool(
            name = "link_memories",
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value ?: ""
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value ?: ""
                val linkType = tool.parameters.find { it.name == "link_type" }?.value ?: "related"
                "链接记忆: '$sourceTitle' -> '$targetTitle' (类型: $linkType)"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 系统操作工具
    handler.registerTool(
            name = "use_package",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "使用工具包: $packageName"
            },
            executor = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                handler
                    .getOrCreatePackageManager()
                    .executeUsePackageTool(tool.name, packageName)
            }
    )

    // ADB命令执行工具

    // 计算器工具
    handler.registerTool(
            name = "calculate",
            descriptionGenerator = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                "计算表达式: $expression"
            },
            executor = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                try {
                    val result = ToolGetter.getCalculator().evalExpression(expression)
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Calculation result: $result")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Calculation error: ${e.message}"
                    )
                }
            }
    )

    // Web搜索工具
    handler.registerTool(
            name = "visit_web",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value
                val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
                val linkNumber = tool.parameters.find { it.name == "link_number" }?.value

                when {
                    !visitKey.isNullOrBlank() && !linkNumber.isNullOrBlank() ->
                        "访问先前搜索结果中的链接 #${linkNumber} (Visit Key: ${visitKey.take(8)}...)"
                    !url.isNullOrBlank() -> "访问网页: $url"
                    else -> "访问网页"
                }
            },
            executor = { tool ->
                val webVisitTool = ToolGetter.getWebVisitTool(context)
                webVisitTool.invoke(tool)
            }
    )

    // 休眠工具
    handler.registerTool(
            name = "sleep",
            descriptionGenerator = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                "休眠 ${durationMs}毫秒"
            },
            executor = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                val limitedDuration = durationMs.coerceIn(0, 10000) // Limit to max 10 seconds

                // Use runBlocking with Dispatchers.IO to ensure sleep happens on background thread
                runBlocking(Dispatchers.IO) {
                    delay(limitedDuration.toLong())
                }

                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Slept for ${limitedDuration}ms")
                )
            }
    )

    // Intent工具
    handler.registerTool(
            name = "execute_intent",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val packageName = tool.parameters.find { it.name == "package" }?.value
                val component = tool.parameters.find { it.name == "component" }?.value
                val type = tool.parameters.find { it.name == "type" }?.value ?: "activity"

                when {
                    !component.isNullOrBlank() -> "执行Intent: 组件 $component (${type})"
                    !packageName.isNullOrBlank() && !action.isNullOrBlank() ->
                            "执行Intent: $action (包: $packageName, 类型: ${type})"
                    !action.isNullOrBlank() -> "执行Intent: $action (类型: ${type})"
                    else -> "执行Android Intent (类型: ${type})"
                }
            },
            executor = { tool ->
                val intentTool = ToolGetter.getIntentToolExecutor(context)
                runBlocking(Dispatchers.IO) { intentTool.invoke(tool) }
            }
    )

    // 设备信息工具
    handler.registerTool(
            name = "device_info",
            descriptionGenerator = { _ -> "获取设备信息" },
            executor = { tool ->
                val deviceInfoTool = ToolGetter.getDeviceInfoToolExecutor(context)
                deviceInfoTool.invoke(tool)
            }
    )
    
    // Tasker事件触发工具
    handler.registerTool(
            name = "trigger_tasker_event",
            descriptionGenerator = { tool ->
                val taskType = tool.parameters.find { it.name == "task_type" }?.value ?: ""
                val args = tool.parameters.filter { it.name.startsWith("arg1") }.joinToString(",")
                "触发Tasker事件: $taskType ($args)"
            },
            executor = { tool ->
                val params = tool.parameters.associate { it.name to it.value }
                val taskType = params["task_type"]
                if (taskType.isNullOrBlank()) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "缺少必需参数: task_type"
                    )
                } else {
                    val args = params.filterKeys { it != "task_type" }
                    try {
                        context.triggerAIAgentAction(
                            taskType,
                            args
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Triggered Tasker event: $taskType")
                        )
                    } catch (e: Exception) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to trigger Tasker event: ${e.message}"
                        )
                    }
                }
            }
    )

    
    // 工作流工具
    val workflowTools = ToolGetter.getWorkflowTools(context)

    // 获取所有工作流
    handler.registerTool(
            name = "get_all_workflows",
            descriptionGenerator = { _ -> "获取所有工作流列表" },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getAllWorkflows(tool) } }
    )

    // 创建工作流
    handler.registerTool(
            name = "create_workflow",
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                "创建工作流: $name"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.createWorkflow(tool) } }
    )

    // 获取工作流详情
    handler.registerTool(
            name = "get_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                "获取工作流详情: $id"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getWorkflow(tool) } }
    )

    // 更新工作流
    handler.registerTool(
            name = "update_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                val name = tool.parameters.find { it.name == "name" }?.value
                if (name != null) {
                    "更新工作流: $id (新名称: $name)"
                } else {
                    "更新工作流: $id"
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.updateWorkflow(tool) } }
    )

    // 删除工作流
    handler.registerTool(
            name = "delete_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                "删除工作流: $id"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.deleteWorkflow(tool) } }
    )

    // 触发工作流执行
    handler.registerTool(
            name = "trigger_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                "触发工作流: $id"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.triggerWorkflow(tool) } }
    )

    // 对话管理工具
    val chatManagerTool = ToolGetter.getChatManagerTool(context)

    // 启动聊天服务
    handler.registerTool(
            name = "start_chat_service",
            descriptionGenerator = { _ -> "启动对话服务（悬浮窗）" },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.startChatService(tool) } }
    )

    // 新建对话
    handler.registerTool(
            name = "create_new_chat",
            descriptionGenerator = { _ -> "创建新的对话" },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.createNewChat(tool) } }
    )

    // 列出所有对话
    handler.registerTool(
            name = "list_chats",
            descriptionGenerator = { _ -> "列出所有对话" },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.listChats(tool) } }
    )

    // 切换对话
    handler.registerTool(
            name = "switch_chat",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                "切换到对话: $chatId"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.switchChat(tool) } }
    )

    // 发送消息给AI
    handler.registerTool(
            name = "send_message_to_ai",
            descriptionGenerator = { tool ->
                val message = tool.parameters.find { it.name == "message" }?.value ?: ""
                val preview = if (message.length > 30) "${message.take(30)}..." else message
                "发送消息给AI: $preview"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.sendMessageToAI(tool) } }
    )

    // 文件系统工具
    val fileSystemTools = ToolGetter.getFileSystemTools(context)

    // 列出目录内容
    handler.registerTool(
            name = "list_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "列出目录内容: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.listFiles(tool) }
            }
    )

    // 读取文件内容
    handler.registerTool(
            name = "read_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "读取文件: $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFile(tool) } }
    )

    // 按行号范围读取文件内容
    handler.registerTool(
            name = "read_file_part",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val startLine = tool.parameters.find { it.name == "start_line" }?.value ?: "1"
                val endLine = tool.parameters.find { it.name == "end_line" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                val rangeInfo = if (endLine != null) "行 $startLine-$endLine" else "从行 $startLine 开始"
                "读取文件 ($rangeInfo): $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.readFilePart(tool) }
            }
    )

    // 读取完整文件内容
    handler.registerTool(
            name = "read_file_full",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "读取完整文件内容: $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileFull(tool) } }
    )

    // 读取二进制文件内容（Base64编码）
    handler.registerTool(
            name = "read_file_binary",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "读取二进制文件内容: $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileBinary(tool) } }
    )

    // 写入文件
    handler.registerTool(
            name = "write_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val append = tool.parameters.find { it.name == "append" }?.value == "true"
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                val operation = if (append) "追加内容到文件" else "写入内容到文件"
                "$operation: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.writeFile(tool) }
            }
    )

    // 写入二进制文件
    handler.registerTool(
        name = "write_file_binary",
        dangerCheck = { true }, // 总是危险操作
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            val environment = tool.parameters.find { it.name == "environment" }?.value
            val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
            "将Base64内容写入二进制文件: $path$envInfo"
        },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { fileSystemTools.writeFileBinary(tool) }
        }
    )

    // 删除文件/目录
    handler.registerTool(
            name = "delete_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val recursive = tool.parameters.find { it.name == "recursive" }?.value == "true"
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                val operation = if (recursive) "递归删除" else "删除文件"
                "$operation: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.deleteFile(tool) }
            }
    )

    // UI自动化工具
    val uiTools = ToolGetter.getUITools(context)

    // 点击元素
    handler.registerTool(
            name = "click_element",
            dangerCheck = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value ?: ""
                val className = tool.parameters.find { it.name == "className" }?.value ?: ""
                val dangerousWords =
                        listOf(
                                "send",
                                "submit",
                                "confirm",
                                "pay",
                                "purchase",
                                "buy",
                                "delete",
                                "remove",
                                "发送",
                                "提交",
                                "确认",
                                "支付",
                                "购买",
                                "删除",
                                "移除"
                        )

                dangerousWords.any { word ->
                    resourceId.contains(word, ignoreCase = true) ||
                            className.contains(word, ignoreCase = true)
                }
            },
            descriptionGenerator = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
                val className = tool.parameters.find { it.name == "className" }?.value
                val bounds = tool.parameters.find { it.name == "bounds" }?.value
                val index = tool.parameters.find { it.name == "index" }?.value ?: "0"

                when {
                    resourceId != null ->
                            "点击元素 [资源ID: $resourceId" +
                                    (if (index != "0") ", 索引: $index" else "") +
                                    "]"
                    className != null ->
                            "点击元素 [类名: $className" +
                                    (if (index != "0") ", 索引: $index" else "") +
                                    "]"
                    bounds != null -> "点击元素 [边界: $bounds]"
                    else -> "点击元素"
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.clickElement(it) }
                }
            }
    )

    // 点击屏幕坐标
    handler.registerTool(
            name = "tap",
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
                val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
                "点击屏幕坐标 ($x, $y)"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.tap(it) }
                }
            }
    )

    handler.registerTool(
            name = "long_press",
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
                val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
                "长按屏幕坐标 ($x, $y)"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.longPress(it) }
                }
            }
    )

    // HTTP请求工具
    val httpTools = ToolGetter.getHttpTools(context)

    // 发送HTTP请求
    handler.registerTool(
            name = "http_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val method = tool.parameters.find { it.name == "method" }?.value ?: "GET"
                "$method 请求: $url"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { httpTools.httpRequest(tool) } }
    )

    // 多部分表单请求（文件上传）
    handler.registerTool(
            name = "multipart_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val filesParam = tool.parameters.find { it.name == "files" }?.value ?: "[]"
                val filesCount =
                        try {
                            JSONArray(filesParam).length()
                        } catch (e: Exception) {
                            0
                        }
                "多部分表单请求: $url (包含 $filesCount 个文件)"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { httpTools.multipartRequest(tool) }
            }
    )

    // 管理Cookie工具
    handler.registerTool(
            name = "manage_cookies",
            descriptionGenerator = { tool ->
                val action =
                        tool.parameters.find { it.name == "action" }?.value?.lowercase() ?: "get"
                val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
                when (action) {
                    "get" -> if (domain.isBlank()) "获取所有Cookie" else "获取域名 $domain 的Cookie"
                    "set" -> "设置Cookie到域名 $domain"
                    "clear" -> if (domain.isBlank()) "清除所有Cookie" else "清除域名 $domain 的Cookie"
                    else -> "管理Cookie: $action"
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { httpTools.manageCookies(tool) } }
    )

    // 检查文件是否存在
    handler.registerTool(
            name = "file_exists",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "检查文件存在: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.fileExists(tool) }
            }
    )

    // 移动/重命名文件或目录
    handler.registerTool(
            name = "move_file",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "移动文件: $source -> $destination$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.moveFile(tool) } }
    )

    // 复制文件或目录
    handler.registerTool(
            name = "copy_file",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val sourceEnv = tool.parameters.find { it.name == "source_environment" }?.value
                val destEnv = tool.parameters.find { it.name == "dest_environment" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value
                
                // 确定源和目标环境
                val srcEnv = sourceEnv ?: environment ?: "android"
                val dstEnv = destEnv ?: environment ?: "android"
                
                val envInfo = if (srcEnv != "android" || dstEnv != "android") {
                    " ($srcEnv → $dstEnv)"
                } else {
                    ""
                }
                "复制文件: $source -> $destination$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.copyFile(tool) } }
    )

    // 创建目录
    handler.registerTool(
            name = "make_directory",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "创建目录: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.makeDirectory(tool) }
            }
    )

    // SSH远程文件系统工具
    val sshTools = ToolGetter.getSSHRemoteConnectionTools(context)

    // 登录SSH服务器
    handler.registerTool(
            name = "ssh_login",
            descriptionGenerator = { tool ->
                val host = tool.parameters.find { it.name == "host" }?.value ?: ""
                val username = tool.parameters.find { it.name == "username" }?.value ?: ""
                val port = tool.parameters.find { it.name == "port" }?.value ?: "22"
                "登录SSH服务器: $username@$host:$port"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { sshTools.sshLogin(tool) } }
    )

    // 退出SSH
    handler.registerTool(
            name = "ssh_exit",
            descriptionGenerator = { _ -> "退出SSH连接" },
            executor = { tool -> runBlocking(Dispatchers.IO) { sshTools.sshExit(tool) } }
    )

    // 搜索文件
    handler.registerTool(
            name = "find_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: "*"
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "搜索文件: 在 $path 中查找 $pattern$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.findFiles(tool) }
            }
    )

    // 获取文件信息
    handler.registerTool(
            name = "file_info",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "获取文件信息: $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.fileInfo(tool) } }
    )

    // 智能应用文件绑定
    handler.registerTool(
            name = "apply_file",
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "智能合并AI代码到文件: $path$envInfo"
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            return runBlocking { fileSystemTools.applyFile(tool).last() }
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            return fileSystemTools.applyFile(tool)
                        }
                    }
    )

    // 压缩文件/目录
    handler.registerTool(
            name = "zip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "压缩文件: $source -> $destination$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.zipFiles(tool) } }
    )

    // 解压缩文件
    handler.registerTool(
            name = "unzip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "解压文件: $source -> $destination$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.unzipFiles(tool) }
            }
    )

    // 打开文件
    handler.registerTool(
            name = "open_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "打开文件: $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.openFile(tool) } }
    )

    // 分享文件
    handler.registerTool(
            name = "share_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "分享文件: $path$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.shareFile(tool) }
            }
    )

    // Grep代码搜索
    handler.registerTool(
            name = "grep_code",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
                val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                val baseDescription = "在 $path 中搜索代码: '$pattern'$envInfo"
                if (filePattern != null && filePattern != "*") {
                    "$baseDescription (文件类型: $filePattern)"
                } else {
                    baseDescription
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepCode(tool) }
            }
    )

    // Grep上下文搜索
    handler.registerTool(
            name = "grep_context",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                val preview = if (intent.length > 40) "${intent.take(40)}..." else intent
                "在 $path 中基于意图搜索相关文件: '$preview'$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepContext(tool) }
            }
    )

    // 下载文件
    handler.registerTool(
            name = "download_file",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = if (!environment.isNullOrBlank() && environment != "android") " (环境: $environment)" else ""
                "下载文件: $url -> $destination$envInfo"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.downloadFile(tool) }
            }
    )

    // 系统操作工具
    val systemOperationTools = ToolGetter.getSystemOperationTools(context)

    // 修改系统设置
    handler.registerTool(
            name = "modify_system_setting",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                val value = tool.parameters.find { it.name == "value" }?.value ?: ""
                "修改系统设置: $key = $value"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.modifySystemSetting(tool) }
            }
    )

    // 获取系统设置
    handler.registerTool(
            name = "get_system_setting",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                "获取系统设置: $key"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getSystemSetting(tool) }
            }
    )

    // 安装应用
    handler.registerTool(
            name = "install_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "安装应用: $path"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.installApp(tool) }
            }
    )

    // 卸载应用
    handler.registerTool(
            name = "uninstall_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "卸载应用: $packageName"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.uninstallApp(tool) }
            }
    )

    // 获取已安装应用列表
    handler.registerTool(
            name = "list_installed_apps",
            descriptionGenerator = { _ -> "列出已安装应用" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.listInstalledApps(tool) }
            }
    )

    // 启动应用
    handler.registerTool(
            name = "start_app",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "启动应用: $packageName"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.startApp(tool) }
            }
    )

    // 停止应用
    handler.registerTool(
            name = "stop_app",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "停止应用: $packageName"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.stopApp(tool) }
            }
    )

    // 获取设备通知
    handler.registerTool(
            name = "get_notifications",
            descriptionGenerator = { tool ->
                val limit = tool.parameters.find { it.name == "limit" }?.value ?: "10"
                val includeOngoing =
                        tool.parameters.find { it.name == "include_ongoing" }?.value == "true"

                val description = "获取设备通知 (最多 $limit 条)"
                if (includeOngoing) "$description，包括常驻通知" else description
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getNotifications(tool) }
            }
    )

    // 获取设备位置
    handler.registerTool(
            name = "get_device_location",
            descriptionGenerator = { tool ->
                val highAccuracy =
                        tool.parameters.find { it.name == "high_accuracy" }?.value == "true"
                if (highAccuracy) "获取设备位置 (高精度)" else "获取设备位置"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getDeviceLocation(tool) }
            }
    )

    // 获取当前页面/窗口信息
    handler.registerTool(
            name = "get_page_info",
            descriptionGenerator = { _ -> "获取当前页面信息" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.getPageInfo(it) }
                }
            }
    )

    handler.registerTool(
            name = "capture_screenshot",
            descriptionGenerator = { _ -> "截取屏幕截图，返回文件路径" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(
                        tool = tool,
                        showStatusIndicator = false,
                        delayMs = 200
                    ) { t ->
                        val (path, _) = uiTools.captureScreenshot(t)
                        if (path.isNullOrBlank()) {
                            ToolResult(toolName = t.name, success = false, result = StringResultData(""), error = "Screenshot failed")
                        } else {
                            ToolResult(toolName = t.name, success = true, result = StringResultData(path), error = null)
                        }
                    }
                }
            }
    )

    handler.registerTool(
            name = "run_ui_subagent",
            descriptionGenerator = { tool ->
                val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
                val maxSteps = tool.parameters.find { it.name == "max_steps" }?.value ?: "20"
                val agentId = tool.parameters.find { it.name == "agent_id" }?.value
                buildString {
                    append("运行UI子代理以完成任务: $intent (最多执行 $maxSteps 步)")
                    if (!agentId.isNullOrBlank()) {
                        append(" (agent_id=$agentId)")
                    }
                    append("。建议在多次调用中尽量复用同一个 agent_id，以持续操作同一虚拟屏幕会话（可沿用上一次返回的 agentId 作为 agent_id）。")
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.runUiSubAgent(tool) } }
    )

    // 在输入框中设置文本
    handler.registerTool(
            name = "set_input_text",
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                "设置输入文本: $text"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.setInputText(it) }
                }
            }
    )

    // 按下特定按键
    handler.registerTool(
            name = "press_key",
            descriptionGenerator = { tool ->
                val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: ""
                "按下按键: $keyCode"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.pressKey(it) }
                }
            }
    )

    // 执行滑动手势
    handler.registerTool(
            name = "swipe",
            descriptionGenerator = { tool ->
                val startX = tool.parameters.find { it.name == "start_x" }?.value ?: "?"
                val startY = tool.parameters.find { it.name == "start_y" }?.value ?: "?"
                val endX = tool.parameters.find { it.name == "end_x" }?.value ?: "?"
                val endY = tool.parameters.find { it.name == "end_y" }?.value ?: "?"
                "滑动: ($startX,$startY) -> ($endX,$endY)"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.swipe(it) }
                }
            }
    )



    // FFmpeg工具 - 执行通用FFmpeg命令
    handler.registerTool(
            name = "ffmpeg_execute",
            dangerCheck = { true }, // 总是危险操作，因为可能会修改文件
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                "执行FFmpeg命令: $command"
            },
            executor = { tool ->
                val ffmpegTool = ToolGetter.getFFmpegToolExecutor(context)
                ffmpegTool.invoke(tool)
            }
    )

    // FFmpeg信息工具 - 获取FFmpeg信息
    handler.registerTool(
            name = "ffmpeg_info",
            descriptionGenerator = { _ -> "获取FFmpeg信息" },
            executor = { tool ->
                val ffmpegInfoTool = ToolGetter.getFFmpegInfoToolExecutor()
                ffmpegInfoTool.invoke(tool)
            }
    )

    // FFmpeg视频转换工具 - 简化的视频转换接口
    handler.registerTool(
            name = "ffmpeg_convert",
            dangerCheck = { true }, // 总是危险操作，因为会创建新文件
            descriptionGenerator = { tool ->
                val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
                val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
                "转换视频: $inputPath → $outputPath"
            },
            executor = { tool ->
                val ffmpegConvertTool = ToolGetter.getFFmpegConvertToolExecutor(context)
                ffmpegConvertTool.invoke(tool)
            }
    )
}