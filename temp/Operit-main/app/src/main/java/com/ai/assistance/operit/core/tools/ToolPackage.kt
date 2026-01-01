package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last

/**
 * Represents a package of tools that can be imported by the AI
 */
@Serializable
data class ToolPackage(
    val name: String,
    val description: String,
    val tools: List<PackageTool>,
    /**
     * Optional list of required environment variable names for this package.
     * If non-empty, PackageManager will validate that these variables exist
     * before activating the package.
     */
    val env: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val enabledByDefault: Boolean = false
)

/**
 * Represents a tool within a package
 */
@Serializable
data class PackageTool(
    val name: String,
    val description: String,
    val parameters: List<PackageToolParameter>,
    val script: String // JavaScript or compatible script that defines this tool's behavior (formerly operScript)
)

/**
 * Represents a parameter for a tool in a package
 */
@Serializable
data class PackageToolParameter(
    val name: String,
    val description: String,
    val type: String, // e.g., "string", "number", "boolean"
    val required: Boolean = true
)

/**
 * Executor for package tools
 */
class PackageToolExecutor(
    private val toolPackage: ToolPackage,
    private val context: Context,
    private val packageManager: PackageManager
) : ToolExecutor {
    
    private val jsToolManager = JsToolManager.getInstance(context, packageManager)
    
    override fun invoke(tool: AITool): ToolResult {
        // Parse packageName:toolName pattern
        val parts = tool.name.split(":")
        if (parts.size != 2) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid package tool format. Expected 'packageName:toolName'"
            )
        }
        
        val packageName = parts[0]
        val toolName = parts[1]
        
        // Verify this executor is for the right package
        if (packageName != toolPackage.name) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Package mismatch: expected ${toolPackage.name}, got $packageName"
            )
        }
        
        // Find the tool in the package
        val packageTool = toolPackage.tools.find { it.name == toolName }
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Tool '$toolName' not found in package '${toolPackage.name}'"
            )
        
        // Execute the script using runBlocking since we can't make this a suspending function
        // without changing the interface. We collect the last result for single-result compatibility.
        return runBlocking {
            jsToolManager.executeScript(packageTool.script, tool).last()
        }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> {
        // Find the tool in the package
        val packageTool = toolPackage.tools.find { it.name.endsWith(tool.name.split(":").last()) }
            ?: error("Tool not found in package for streaming") // Should be validated before

        return jsToolManager.executeScript(packageTool.script, tool)
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        // Parse packageName:toolName pattern
        val parts = tool.name.split(":")
        if (parts.size != 2) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Invalid package tool format. Expected 'packageName:toolName'"
            )
        }
        
        val packageName = parts[0]
        val toolName = parts[1]
        
        // Verify this executor is for the right package
        if (packageName != toolPackage.name) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Package mismatch: expected ${toolPackage.name}, got $packageName"
            )
        }
        
        // Find the tool in the package
        val packageTool = toolPackage.tools.find { it.name == toolName }
            ?: return ToolValidationResult(
                valid = false,
                errorMessage = "Tool '$toolName' not found in package '${toolPackage.name}'"
            )
        
        // Validate that all required parameters are present
        val missingParams = packageTool.parameters
            .filter { it.required }
            .map { it.name }
            .filter { paramName -> tool.parameters.none { it.name == paramName } }
        
        if (missingParams.isNotEmpty()) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Missing required parameters: ${missingParams.joinToString(", ")}"
            )
        }
        
        return ToolValidationResult(valid = true)
    }
    
    /**
     * Returns information about the tools available in this package
     */
    fun describePackage(): String {
        val sb = StringBuilder()
        sb.appendLine("Package: ${toolPackage.name}")
        sb.appendLine("Description: ${toolPackage.description}")
        sb.appendLine("Tools:")
        
        toolPackage.tools.forEach { tool ->
            sb.appendLine("  - ${tool.name}: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("    Parameters:")
                tool.parameters.forEach { param ->
                    val required = if (param.required) " (required)" else " (optional)"
                    sb.appendLine("      - ${param.name}: ${param.description} [${param.type}]$required")
                }
            }
        }
        
        return sb.toString()
    }
}