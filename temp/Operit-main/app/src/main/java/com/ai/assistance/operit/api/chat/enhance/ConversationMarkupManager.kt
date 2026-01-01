package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 *
 * This class handles the generation of standardized XML-formatted status messages, tool invocation
 * formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        private const val TAG = "ConversationMarkupManager"

        /**
         * Creates a 'complete' status markup element.
         *
         * @return The formatted status element
         */
        fun createCompleteStatus(): String {
            return "<status type=\"complete\"></status>"
        }

        /**
         * Creates a 'wait for user need' status markup element. Similar to complete but doesn't
         * trigger problem analysis.
         *
         * @return The formatted status element
         */
        fun createWaitForUserNeedStatus(): String {
            return "<status type=\"wait_for_user_need\"></status>"
        }

        /**
         * Creates an 'error' status markup element for a tool.
         *
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return """<tool_result name="${toolName}" status="error"><content><error>${errorMessage}</error></content></tool_result>""".trimIndent()
        }

        /**
         * Creates a 'warning' status markup element.
         *
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }

        /**
         * Formats a tool result message for sending to the AI.
         *
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return if (result.success) {
                """<tool_result name="${result.toolName}" status="success"><content>${result.result}</content></tool_result>""".trimIndent()
            } else {
                """<tool_result name="${result.toolName}" status="error"><content><error>${result.error ?: "Unknown error"}</error></content></tool_result>""".trimIndent()
            }
        }

        /**
         * Formats a message indicating multiple tool invocations were found but only one will be
         * processed.
         *
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(toolName: String): String {
            return createWarningStatus(
                    "检测到多个工具调用。系统将只执行第一个工具 `$toolName`，忽略其它工具调用。请避免在单个消息中同时调用多个工具。"
            )
        }

        /**
         * Creates a message for when a tool is not available.
         *
         * @param toolName The name of the unavailable tool
         * @param details Optional detailed error message
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String, details: String? = null): String {
            val errorMessage = details ?: "The tool `$toolName` is not available."
            return createToolErrorStatus(toolName, errorMessage)
        }

        /**
         * Creates a warning when tools and task completion are reported together.
         *
         * @param toolNames The names of the tools involved
         * @return The formatted warning message
         */
        fun createToolsSkippedByCompletionWarning(toolNames: List<String>): String {
            val uniqueNames =
                    toolNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val toolDescription =
                    if (uniqueNames.isEmpty()) {
                        "工具调用"
                    } else {
                        "工具 `${uniqueNames.joinToString("`, `")}` 调用"
                    }
            val message =
                    "警告：检测到任务完成标记的同时存在$toolDescription，系统仍会执行这些工具，但这是高风险组合。" +
                            "请勿在发送任务完成状态的同时调用工具，以避免重复或矛盾的响应。"
            return createWarningStatus(message)
        }

        /**
         * Cleans task completion content by removing the completion marker and adding a completion
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with completion status
         */
        fun createTaskCompletionContent(content: String): String {
            return content.replace("<status type=\"complete\"></status>", "").trim() +
                    "\n" +
                    createCompleteStatus()
        }

        /**
         * Cleans wait for user need content by removing the marker and adding the appropriate
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with wait_for_user_need status
         */
        fun createWaitForUserNeedContent(content: String): String {
            return content.replace("<status type=\"wait_for_user_need\"></status>", "").trim() +
                    "\n" +
                    createWaitForUserNeedStatus()
        }

        /**
         * Checks if content contains a task completion marker.
         *
         * @param content The content to check
         * @return True if the content contains a task completion marker
         */
        fun containsTaskCompletion(content: String): Boolean {
            return content.contains("<status type=\"complete\"></status>")
        }

        /**
         * Checks if content contains a wait for user need marker.
         *
         * @param content The content to check
         * @return True if the content contains a wait for user need marker
         */
        fun containsWaitForUserNeed(content: String): Boolean {
            return content.contains("<status type=\"wait_for_user_need\"></status>")
        }


    }
}
