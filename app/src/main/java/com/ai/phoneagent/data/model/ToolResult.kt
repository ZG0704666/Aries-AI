package com.ai.phoneagent.data.model

/**
 * 工具执行结果
 * @param toolName 工具名称
 * @param success 是否执行成功
 * @param result 结果数据（可以是字符串、图片、UI信息等）
 * @param error 错误信息（失败时）
 */
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val result: ToolResultData? = null,
    val error: String = ""
)

/**
 * 工具结果数据的基类
 * 不同类型的工具返回不同的结果数据
 */
sealed class ToolResultData

/**
 * 字符串结果数据
 */
data class StringResultData(
    val data: String
) : ToolResultData()

/**
 * 图片结果数据
 */
data class ImageResultData(
    val width: Int,
    val height: Int,
    val base64Data: String
) : ToolResultData()

/**
 * UI页面信息结果数据
 */
data class UIPageResultData(
    val packageName: String,
    val activityName: String,
    val uiElements: List<SimplifiedUINode>
) : ToolResultData()

/**
 * 简化的UI节点
 */
data class SimplifiedUINode(
    val className: String?,
    val text: String?,
    val contentDesc: String?,
    val resourceId: String?,
    val bounds: String?,
    val isClickable: Boolean,
    val children: List<SimplifiedUINode>
)

/**
 * UI操作结果数据
 */
data class UIActionResultData(
    val action: String,
    val success: Boolean,
    val message: String
) : ToolResultData()
