package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.util.regex.Pattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeout

/**
 * Manages JavaScript tool execution using JsEngine This class handles the execution of JavaScript
 * code in package tools and coordinates tool calls from JavaScript back to native Android code.
 */
class JsToolManager
private constructor(private val context: Context, private val packageManager: PackageManager) {
    companion object {
        private const val TAG = "JsToolManager"

        @Volatile private var INSTANCE: JsToolManager? = null

        fun getInstance(context: Context, packageManager: PackageManager): JsToolManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: JsToolManager(context.applicationContext, packageManager).also {
                                    INSTANCE = it
                                }
                    }
        }
    }

    // JavaScript engine for executing code
    private val jsEngine = JsEngine(context)

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    /**
     * Execute a specific JavaScript tool
     * @param toolName The name of the tool to execute (format: packageName.functionName)
     * @param params Parameters to pass to the tool function
     * @return The result of tool execution
     */
    fun executeScript(toolName: String, params: Map<String, String>): String {
        try {
            // Split the tool name to get package and function names
            val parts = toolName.split(".")
            if (parts.size < 2) {
                return "Invalid tool name format: $toolName. Expected format: packageName.functionName"
            }

            val packageName = parts[0]
            val functionName = parts[1]

            // Get the package script
            val script =
                    packageManager.getPackageScript(packageName)
                            ?: return "Package not found: $packageName"

            AppLogger.d(TAG, "Executing function $functionName in package $packageName")

            // Execute the function in the script
            val result = jsEngine.executeScriptFunction(script, functionName, params)

            return result?.toString() ?: "null"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing script: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Execute a JavaScript script with the given tool parameters
     * @param script The JavaScript code to execute
     * @param tool The tool being executed (provides parameters)
     * @return The result of script execution
     */
    fun executeScript(script: String, tool: AITool): Flow<ToolResult> = channelFlow {
        try {
            AppLogger.d(TAG, "Executing script for tool: ${tool.name}")

            // Extract the function name from the tool name (packageName:toolName)
            val parts = tool.name.split(":")
            if (parts.size != 2) {
                send(
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Invalid tool name format. Expected 'packageName:toolName'"
                        )
                )
                return@channelFlow
            }

            val packageName = parts[0]
            val functionName = parts[1]

            // Get tool definition from PackageManager to access parameter types
            val toolDefinition = packageManager.getPackageTools(packageName)?.tools?.find { it.name == functionName }

            // Convert tool parameters to map for the script, with type conversion
            val params: Map<String, Any?> = tool.parameters.associate { param ->
                val paramDefinition = toolDefinition?.parameters?.find { it.name == param.name }
                // default to string if not found in metadata
                val paramType = paramDefinition?.type ?: "string"

                val convertedValue: Any? = try {
                    when (paramType.lowercase()) {
                        "number" -> param.value.toDoubleOrNull() ?: param.value.toLongOrNull() ?: param.value
                        "boolean" -> param.value.toBoolean()
                        "integer" -> param.value.toLongOrNull() ?: param.value
                        else -> param.value // string and other types
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to convert parameter '${param.name}' with value '${param.value}' to type '$paramType'. Using string value. Error: ${e.message}")
                    param.value // Fallback to string
                }
                param.name to convertedValue
            }

            // Execute the script with timeout
            try {
                withTimeout(JsTimeoutConfig.SCRIPT_TIMEOUT_MS) {
                    AppLogger.d(TAG, "Starting script execution for function: $functionName")

                    val startTime = System.currentTimeMillis()
                    val scriptResult =
                            jsEngine.executeScriptFunction(
                                    script,
                                    functionName,
                                    params
                            ) { intermediateResult ->
                                val resultString = intermediateResult?.toString() ?: "null"
                                AppLogger.d(TAG, "Intermediate JS result: $resultString")
                                trySend(
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result = StringResultData(resultString)
                                        )
                                )
                            }

                    val executionTime = System.currentTimeMillis() - startTime
                    AppLogger.d(
                            TAG,
                            "Script execution completed in ${executionTime}ms with result type: ${scriptResult?.javaClass?.name ?: "null"}"
                    )

                    // Handle different types of results
                    when {
                        scriptResult == null -> {
                            send(
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = "Script returned null result"
                                    )
                            )
                        }
                        scriptResult is String && scriptResult.startsWith("Error:") -> {
                            val errorMsg = scriptResult.substring("Error:".length).trim()
                            AppLogger.e(TAG, "Script execution error: $errorMsg")
                            send(
                                    ToolResult(
                                            toolName = tool.name,
                                            success = false,
                                            result = StringResultData(""),
                                            error = errorMsg
                                    )
                            )
                        }
                        else -> {
                            val finalResultString = scriptResult.toString()
                            AppLogger.d(
                                    TAG,
                                    "Final script result: ${finalResultString.take(100)}${if (finalResultString.length > 100) "..." else ""}"
                            )
                            send(
                                    ToolResult(
                                            toolName = tool.name,
                                            success = true,
                                            result = StringResultData(finalResultString)
                                    )
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                AppLogger.w(TAG, "Script execution timed out: ${e.message}")
                send(
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Script execution timed out after ${JsTimeoutConfig.SCRIPT_TIMEOUT_MS}ms"
                        )
                )
            } catch (e: Exception) {
                // Catch other execution exceptions
                AppLogger.e(TAG, "Exception during script execution: ${e.message}", e)
                send(
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Script execution failed: ${e.message}"
                        )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing script for tool ${tool.name}: ${e.message}", e)
            send(
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Script execution error: ${e.message}"
                    )
            )
        }
    }

    /** Clean up resources when the manager is no longer needed */
    fun destroy() {
        jsEngine.destroy()
    }
}
