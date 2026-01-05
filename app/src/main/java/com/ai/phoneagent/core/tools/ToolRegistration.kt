package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.data.model.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/**
 * 工具注册中心
 * 集中注册所有可用的工具
 */
object ToolRegistration {

    /**
     * 注册所有工具
     */
    fun registerAllTools(handler: AIToolHandler, context: Context) {
        registerUITools(handler, context)
        registerAppTools(handler, context)
        registerSystemTools(handler, context)
    }

    /**
     * 注册UI交互工具
     */
    private fun registerUITools(handler: AIToolHandler, context: Context) {
        // 点击工具
        handler.registerTool(
            name = "tap",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: ""
                val y = tool.parameters.find { it.name == "y" }?.value ?: ""
                "点击屏幕位置 ($x, $y)"
            },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val x = tool.parameters.find { it.name == "x" }?.value?.toFloatOrNull()
                    val y = tool.parameters.find { it.name == "y" }?.value?.toFloatOrNull()
                    
                    if (x == null || y == null) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "缺少坐标参数"
                        )
                    } else {
                        val success = service.performTap(x, y)
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("tap", success, "点击 ($x, $y)"),
                            error = if (success) "" else "点击失败"
                        )
                    }
                }
            }
        )

        // 滑动工具
        handler.registerTool(
            name = "swipe",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val startX = tool.parameters.find { it.name == "start_x" }?.value ?: ""
                val startY = tool.parameters.find { it.name == "start_y" }?.value ?: ""
                val endX = tool.parameters.find { it.name == "end_x" }?.value ?: ""
                val endY = tool.parameters.find { it.name == "end_y" }?.value ?: ""
                "从 ($startX, $startY) 滑动到 ($endX, $endY)"
            },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val startX = tool.parameters.find { it.name == "start_x" }?.value?.toFloatOrNull()
                    val startY = tool.parameters.find { it.name == "start_y" }?.value?.toFloatOrNull()
                    val endX = tool.parameters.find { it.name == "end_x" }?.value?.toFloatOrNull()
                    val endY = tool.parameters.find { it.name == "end_y" }?.value?.toFloatOrNull()
                    val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 300L
                    
                    if (startX == null || startY == null || endX == null || endY == null) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "缺少坐标参数"
                        )
                    } else {
                        val success = service.performSwipe(startX, startY, endX, endY, duration)
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("swipe", success, "滑动完成"),
                            error = if (success) "" else "滑动失败"
                        )
                    }
                }
            }
        )

        // 截图工具
        handler.registerTool(
            name = "screenshot",
            dangerCheck = { false },
            descriptionGenerator = { "截取当前屏幕" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val screenshot = service.tryCaptureScreenshotBase64()
                    if (screenshot != null) {
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = ImageResultData(
                                width = screenshot.width,
                                height = screenshot.height,
                                base64Data = screenshot.base64Png
                            )
                        )
                    } else {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "截图失败"
                        )
                    }
                }
            }
        )

        // 获取UI树工具
        handler.registerTool(
            name = "get_ui_tree",
            dangerCheck = { false },
            descriptionGenerator = { "获取当前UI层次结构" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val maxNodes = tool.parameters.find { it.name == "max_nodes" }?.value?.toIntOrNull() ?: 200
                    val uiTree = service.dumpUiTree(maxNodes)
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(uiTree)
                    )
                }
            }
        )

        // 返回键
        handler.registerTool(
            name = "press_back",
            dangerCheck = { false },
            descriptionGenerator = { "按下返回键" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val success = service.performGlobalBack()
                    ToolResult(
                        toolName = tool.name,
                        success = success,
                        result = UIActionResultData("press_back", success, "按下返回键"),
                        error = if (success) "" else "操作失败"
                    )
                }
            }
        )

        // Home键
        handler.registerTool(
            name = "press_home",
            dangerCheck = { false },
            descriptionGenerator = { "按下Home键" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val success = service.performGlobalHome()
                    ToolResult(
                        toolName = tool.name,
                        success = success,
                        result = UIActionResultData("press_home", success, "按下Home键"),
                        error = if (success) "" else "操作失败"
                    )
                }
            }
        )

        // 输入文本工具
        handler.registerTool(
            name = "input_text",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                "输入文本: $text"
            },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val text = tool.parameters.find { it.name == "text" }?.value
                    if (text == null) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "缺少text参数"
                        )
                    } else {
                        val success = service.performTextInput(text)
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("input_text", success, "输入: $text"),
                            error = if (success) "" else "输入失败"
                        )
                    }
                }
            }
        )
    }

    /**
     * 注册应用管理工具
     */
    private fun registerAppTools(handler: AIToolHandler, context: Context) {
        // 初始化应用列表缓存（后台操作）
        AppPackageManager.initializeCache(context)
        
        // 启动应用工具 - 绕过模型直接启动（更快更高效）
        handler.registerTool(
            name = "launch_app",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val appName = tool.parameters.find { it.name == "app_name" }?.value ?: ""
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                val target = appName.ifEmpty { packageName }
                "启动应用: $target"
            },
            executor = { tool ->
                val appName = tool.parameters.find { it.name == "app_name" }?.value
                val packageName = tool.parameters.find { it.name == "package_name" }?.value
                
                // 优先使用包名，其次使用应用名，绕过模型直接启动
                val targetPackage = packageName ?: appName?.let { name ->
                    AppPackageManager.resolvePackageName(name)
                }
                
                if (targetPackage == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "未找到应用: ${appName ?: packageName}。可用应用列表需要通过 get_installed_apps 获取"
                    )
                } else {
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            context.startActivity(intent)
                            delay(1200) // 等待应用启动
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData("已启动应用: ${AppPackageManager.getAppName(targetPackage)} ($targetPackage)")
                            )
                        } else {
                            ToolResult(
                                toolName = tool.name,
                                success = false,
                                error = "应用未安装或无启动Activity: $targetPackage"
                            )
                        }
                    } catch (e: Exception) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "启动失败: ${e.message}"
                        )
                    }
                }
            }
        )
        
        // 新增：获取已安装应用列表（供用户选择）
        handler.registerTool(
            name = "get_installed_apps",
            dangerCheck = { false },
            descriptionGenerator = { "获取已安装应用列表" },
            executor = { tool ->
                val maxApps = tool.parameters.find { it.name == "max_apps" }?.value?.toIntOrNull() ?: 50
                val appList = AppPackageManager.getAllInstalledApps()
                    .take(maxApps)
                    .joinToString("\n") { (packageName, appName) ->
                        "$appName ($packageName)"
                    }
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(appList)
                )
            }
        )

        // 获取当前应用包名
        handler.registerTool(
            name = "get_current_package",
            dangerCheck = { false },
            descriptionGenerator = { "获取当前应用包名" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val packageName = service.currentAppPackage()
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(packageName)
                    )
                }
            }
        )
    }

    /**
     * 注册系统工具
     */
    private fun registerSystemTools(handler: AIToolHandler, context: Context) {
        // 等待工具
        handler.registerTool(
            name = "wait",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val seconds = tool.parameters.find { it.name == "seconds" }?.value ?: "1"
                "等待 $seconds 秒"
            },
            executor = { tool ->
                val seconds = tool.parameters.find { it.name == "seconds" }?.value?.toIntOrNull() ?: 1
                delay(seconds * 1000L)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("等待了 $seconds 秒")
                )
            }
        )

        // Finish工具（任务完成）
        handler.registerTool(
            name = "finish",
            dangerCheck = { false },
            descriptionGenerator = { "任务完成" },
            executor = { tool ->
                val message = tool.parameters.find { it.name == "message" }?.value ?: "任务已完成"
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            }
        )
    }
}
