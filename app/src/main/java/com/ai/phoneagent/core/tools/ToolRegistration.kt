package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
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
            descriptionGenerator = { tool ->
                val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
                val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"
                "获取当前UI层次结构(format=$format, detail=$detail)"
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
                    val maxNodes = tool.parameters.find { it.name == "max_nodes" }?.value?.toIntOrNull() ?: 30
                    val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
                    val detail = tool.parameters.find { it.name == "detail" }?.value ?: "minimal"
                    val uiTree = service.getUiHierarchy(format, detail, maxNodes)
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(uiTree)
                    )
                }
            }
        )

        // 获取页面信息（包名+Activity+UI树）
        handler.registerTool(
            name = "get_page_info",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
                val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"
                "获取页面信息(format=$format, detail=$detail)"
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
                    val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
                    val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"
                    val ui = service.getUiHierarchy(format, detail)
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(ui)
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

        // ============================================================================
        // Operit 同款工具接口 - TODO-005 click_element
        // ============================================================================
        handler.registerTool(
            name = "click_element",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value
                val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                val selector = text ?: resourceId ?: contentDesc ?: "元素"
                "点击元素: $selector"
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
                    val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                    val text = tool.parameters.find { it.name == "text" }?.value
                    val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                    val className = tool.parameters.find { it.name == "class_name" }?.value
                    val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
                    val bounds = tool.parameters.find { it.name == "bounds" }?.value
                    val x = tool.parameters.find { it.name == "x" }?.value?.toFloatOrNull()
                    val y = tool.parameters.find { it.name == "y" }?.value?.toFloatOrNull()
                    
                    // 至少需要一个选择器或兜底参数
                    if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
                        contentDesc.isNullOrBlank() && className.isNullOrBlank() &&
                        bounds.isNullOrBlank() && (x == null || y == null)) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "需要选择器(resource_id/text/content_desc/class_name)或bounds/x,y兜底"
                        )
                    } else {
                        Log.d("TOOL_CLICK", "调用click_element: res=$resourceId text=$text class=$className idx=$index bounds=$bounds x=$x y=$y")
                        val success = service.clickElement(resourceId, text, contentDesc, className, index, bounds, x, y)
                        val selector = text ?: resourceId ?: contentDesc ?: className ?: bounds ?: "coords(${x},${y})"
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("click_element", success, "点击: $selector (index=$index)"),
                            error = if (success) "" else "未找到匹配元素或点击失败"
                        )
                    }
                }
            }
        )

        // ============================================================================
        // Operit 同款工具接口 - TODO-006 set_input_text
        // ============================================================================
        handler.registerTool(
            name = "set_input_text",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                val target = resourceId ?: "当前焦点"
                "在[$target]输入文本: $text"
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
                        val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                        val elementText = tool.parameters.find { it.name == "element_text" }?.value
                        val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                        val className = tool.parameters.find { it.name == "class_name" }?.value
                        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
                        
                        val success = if (resourceId.isNullOrBlank() && elementText.isNullOrBlank() && 
                                          contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
                            // 无选择器时，对当前焦点输入
                            service.performTextInput(text)
                        } else {
                            // 有选择器时，定位元素后输入
                            service.setTextOnElement(text, resourceId, elementText, contentDesc, className, index)
                        }
                        
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("set_input_text", success, "输入: $text"),
                            error = if (success) "" else "输入失败，可能未找到目标输入框"
                        )
                    }
                }
            }
        )

        // ============================================================================
        // Operit 同款工具接口 - TODO-007 wait_for_element
        // ============================================================================
        handler.registerTool(
            name = "wait_for_element",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value
                val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value ?: "5000"
                val selector = text ?: resourceId ?: "指定元素"
                "等待元素出现: $selector (timeout=${timeout}ms)"
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
                    val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                    val text = tool.parameters.find { it.name == "text" }?.value
                    val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                    val className = tool.parameters.find { it.name == "class_name" }?.value
                    val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 5000L
                    val pollIntervalMs = tool.parameters.find { it.name == "poll_interval_ms" }?.value?.toLongOrNull() ?: 200L
                    
                    if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
                        contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "需要至少一个选择器参数: resource_id, text, content_desc, 或 class_name"
                        )
                    } else {
                        val found = service.waitForElement(resourceId, text, contentDesc, className, timeoutMs, pollIntervalMs)
                        val selector = text ?: resourceId ?: contentDesc ?: className ?: "unknown"
                        ToolResult(
                            toolName = tool.name,
                            success = found,
                            result = StringResultData(if (found) "元素已出现: $selector" else "等待超时: $selector"),
                            error = if (found) "" else "等待超时，元素未出现"
                        )
                    }
                }
            }
        )

        // ============================================================================
        // Operit 同款工具接口 - TODO-009 press_key
        // ============================================================================
        handler.registerTool(
            name = "press_key",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: "BACK"
                "按键: $keyCode"
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
                    val keyCode = tool.parameters.find { it.name == "key_code" }?.value
                    if (keyCode.isNullOrBlank()) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "缺少key_code参数。支持: HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG, LOCK_SCREEN"
                        )
                    } else {
                        val success = service.pressKey(keyCode)
                        ToolResult(
                            toolName = tool.name,
                            success = success,
                            result = UIActionResultData("press_key", success, "按键: $keyCode"),
                            error = if (success) "" else "按键失败或不支持的key_code"
                        )
                    }
                }
            }
        )

        // ============================================================================
        // Operit 同款工具接口 - TODO-010 get_current_app
        // ============================================================================
        handler.registerTool(
            name = "get_current_app",
            dangerCheck = { false },
            descriptionGenerator = { "获取当前应用信息" },
            executor = { tool ->
                val service = PhoneAgentAccessibilityService.instance
                if (service == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        error = "无障碍服务未启用"
                    )
                } else {
                    val appInfo = service.getCurrentAppInfo()
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(appInfo)
                    )
                }
            }
        )

        // ============================================================================
        // Operit 同款工具接口 - scroll_to_element (优化新增)
        // ============================================================================
        handler.registerTool(
            name = "scroll_to_element",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value
                val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                val direction = tool.parameters.find { it.name == "direction" }?.value ?: "down"
                val selector = text ?: resourceId ?: "目标元素"
                "滚动查找元素: $selector ($direction)"
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
                    val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                    val text = tool.parameters.find { it.name == "text" }?.value
                    val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                    val className = tool.parameters.find { it.name == "class_name" }?.value
                    val direction = tool.parameters.find { it.name == "direction" }?.value ?: "down"
                    val maxScrolls = tool.parameters.find { it.name == "max_scrolls" }?.value?.toIntOrNull() ?: 10
                    val scrollDelayMs = tool.parameters.find { it.name == "scroll_delay_ms" }?.value?.toLongOrNull() ?: 500L
                    
                    if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
                        contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "需要至少一个选择器参数: resource_id, text, content_desc, 或 class_name"
                        )
                    } else {
                        kotlinx.coroutines.runBlocking {
                            val success = service.scrollToElement(
                                resourceId = resourceId,
                                text = text,
                                contentDesc = contentDesc,
                                className = className,
                                direction = direction,
                                maxScrolls = maxScrolls,
                                scrollDelayMs = scrollDelayMs
                            )
                            val selector = text ?: resourceId ?: "目标元素"
                            ToolResult(
                                toolName = tool.name,
                                success = success,
                                result = UIActionResultData("scroll_to_element", success, "滚动查找: $selector ($direction)"),
                                message = if (success) "成功找到并滚动到元素" else "未能找到目标元素"
                            )
                        }
                    }
                }
            }
        )
        
        // ============================================================================
        // Operit 同款工具接口 - TODO-012 find_elements
        // ============================================================================
        handler.registerTool(
            name = "find_elements",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value
                val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                val selector = text ?: resourceId ?: "所有匹配"
                "查找元素: $selector"
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
                    val resourceId = tool.parameters.find { it.name == "resource_id" }?.value
                    val text = tool.parameters.find { it.name == "text" }?.value
                    val contentDesc = tool.parameters.find { it.name == "content_desc" }?.value
                    val className = tool.parameters.find { it.name == "class_name" }?.value
                    val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 10
                    
                    if (resourceId.isNullOrBlank() && text.isNullOrBlank() && 
                        contentDesc.isNullOrBlank() && className.isNullOrBlank()) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            error = "需要至少一个选择器参数: resource_id, text, content_desc, 或 class_name"
                        )
                    } else {
                        val elements = service.findElements(resourceId, text, contentDesc, className, maxResults)
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData(elements)
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
