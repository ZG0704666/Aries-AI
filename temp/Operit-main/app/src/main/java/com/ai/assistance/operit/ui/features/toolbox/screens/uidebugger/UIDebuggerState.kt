package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.graphics.Rect
import com.ai.assistance.operit.core.tools.system.action.ActionListener

/** UI调试工具的状态类（仅保留布局分析和 Activity 监听相关状态） */
data class UIDebuggerState(
    val elements: List<UIElement> = emptyList(),
    val selectedElementId: String? = null,
    val showActionFeedback: Boolean = false,
    val actionFeedbackMessage: String = "",
    val errorMessage: String? = null,
    val currentAnalyzedActivityName: String? = null,
    val currentAnalyzedPackageName: String? = null,
    // Activity 监听相关状态
    val isActivityListening: Boolean = false,
    val activityEvents: List<ActionListener.ActionEvent> = emptyList(),
    val showActivityMonitor: Boolean = false,
    val currentActivityName: String? = null
)

/** UI元素数据模型 */
data class UIElement(
    val id: String,
    val className: String,
    val resourceId: String? = null,
    val contentDesc: String? = null,
    val text: String = "",
    val bounds: Rect? = null,
    val isClickable: Boolean = false,
    val activityName: String? = null,
    val packageName: String? = null
) {
    val typeDescription: String
        get() = when {
            className.contains("Button", ignoreCase = true) -> "按钮"
            className.contains("Text", ignoreCase = true) -> "文本"
            className.contains("Edit", ignoreCase = true) -> "输入框"
            className.contains("Image", ignoreCase = true) -> "图片"
            className.contains("View", ignoreCase = true) -> "视图"
            else -> "UI元素"
        }

    fun getFullDetails(): String {
        return buildString {
            append("类名: $className\n")
            if (packageName != null) append("包名: $packageName\n")
            if (activityName != null) append("Activity: $activityName\n")
            if (resourceId != null) append("资源ID: $resourceId\n")
            if (contentDesc != null) append("内容描述: $contentDesc\n")
            if (text.isNotEmpty()) append("文本: $text\n")
            if (bounds != null)
                append("边界: [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]\n")
            append("可点击: ${if (isClickable) "是" else "否"}")
        }
    }
}

/** UI元素操作类型 */
enum class UIElementAction {
    CLICK,
    HIGHLIGHT,
    INSPECT
}
