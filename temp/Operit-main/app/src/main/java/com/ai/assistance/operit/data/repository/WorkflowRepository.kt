package com.ai.assistance.operit.data.repository

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.core.workflow.WorkflowExecutor
import com.ai.assistance.operit.core.workflow.WorkflowScheduler
import com.ai.assistance.operit.data.model.ExecutionStatus
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.TriggerNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import android.content.Intent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 工作流仓库
 * 负责工作流的持久化存储和管理
 */
class WorkflowRepository(private val context: Context) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }
    
    // Lazy initialization to avoid WorkManager initialization issues during app startup
    private val scheduler by lazy { WorkflowScheduler(context) }
    
    companion object {
        private const val TAG = "WorkflowRepository"
        private const val WORKFLOW_DIR = "Operit/workflow"
    }
    
    /**
     * 获取工作流存储目录
     */
    private fun getWorkflowDirectory(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val workflowDir = File(downloadDir, WORKFLOW_DIR)
        if (!workflowDir.exists()) {
            workflowDir.mkdirs()
        }
        return workflowDir
    }
    
    /**
     * 获取工作流文件
     */
    private fun getWorkflowFile(workflowId: String): File {
        return File(getWorkflowDirectory(), "$workflowId.json")
    }
    
    /**
     * 获取所有工作流
     */
    suspend fun getAllWorkflows(): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val workflowDir = getWorkflowDirectory()
            val workflows = workflowDir.listFiles { file ->
                file.isFile && file.extension == "json"
            }?.mapNotNull { file ->
                try {
                    val content = file.readText()
                    json.decodeFromString<Workflow>(content)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to parse workflow file: ${file.name}", e)
                    null
                }
            }?.sortedByDescending { it.updatedAt } ?: emptyList()
            
            Result.success(workflows)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get all workflows", e)
            Result.failure(e)
        }
    }
    
    /**
     * 根据ID获取工作流
     */
    suspend fun getWorkflowById(id: String): Result<Workflow?> = withContext(Dispatchers.IO) {
        try {
            val file = getWorkflowFile(id)
            if (!file.exists()) {
                return@withContext Result.success(null)
            }
            
            val content = file.readText()
            val workflow = json.decodeFromString<Workflow>(content)
            Result.success(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get workflow by id: $id", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建工作流
     */
    suspend fun createWorkflow(workflow: Workflow): Result<Workflow> = withContext(Dispatchers.IO) {
        try {
            val file = getWorkflowFile(workflow.id)
            val content = json.encodeToString(workflow)
            file.writeText(content)
            
            AppLogger.d(TAG, "Workflow created: ${workflow.id}")
            
            // Schedule if enabled and has schedule trigger
            if (workflow.enabled) {
                scheduleWorkflow(workflow.id)
            }
            
            Result.success(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新工作流
     */
    suspend fun updateWorkflow(workflow: Workflow): Result<Workflow> = withContext(Dispatchers.IO) {
        try {
            val updatedWorkflow = workflow.copy(updatedAt = System.currentTimeMillis())
            val file = getWorkflowFile(updatedWorkflow.id)
            val content = json.encodeToString(updatedWorkflow)
            file.writeText(content)
            
            AppLogger.d(TAG, "Workflow updated: ${updatedWorkflow.id}")
            
            // Reschedule workflow
            rescheduleWorkflow(updatedWorkflow.id)
            
            Result.success(updatedWorkflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除工作流
     */
    suspend fun deleteWorkflow(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Cancel schedule first
            unscheduleWorkflow(id)
            
            val file = getWorkflowFile(id)
            val deleted = if (file.exists()) {
                file.delete()
            } else {
                false
            }
            
            AppLogger.d(TAG, "Workflow deleted: $id, success: $deleted")
            Result.success(deleted)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * 触发工作流执行
     * @param id 工作流ID
     * @param triggerNodeId 指定要触发的节点ID，如果为null则触发所有触发节点
     */
    suspend fun triggerWorkflow(id: String, triggerNodeId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull()
            
            if (workflow == null) {
                return@withContext Result.failure(Exception("工作流不存在: $id"))
            }
            
            if (!workflow.enabled) {
                return@withContext Result.failure(Exception("工作流已禁用: ${workflow.name}"))
            }
            
            AppLogger.d(TAG, "Triggering workflow: ${workflow.name} (${workflow.id})")
            if (triggerNodeId != null) {
                AppLogger.d(TAG, "With specific trigger node: $triggerNodeId")
            }
            
            // 更新为执行中状态
            updateExecutionStatus(id, ExecutionStatus.RUNNING, System.currentTimeMillis())
            
            // 创建执行器并执行工作流
            val executor = WorkflowExecutor(context)
            val result = executor.executeWorkflow(workflow, triggerNodeId) { nodeId, state ->
                // 这里可以通过 Flow 或其他机制传递状态更新到 UI
                AppLogger.d(TAG, "Node $nodeId state: $state")
            }
            
            // 更新执行统计
            val executionStatus = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED
            updateExecutionStatistics(id, executionStatus, result.executionTime)
            
            if (result.success) {
                Result.success("工作流 '${workflow.name}' 执行成功")
            } else {
                Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger workflow", e)
            // 执行失败时也更新状态
            updateExecutionStatus(id, ExecutionStatus.FAILED, System.currentTimeMillis())
            Result.failure(e)
        }
    }
    
    /**
     * 触发工作流执行（带状态回调）
     * @param id 工作流ID
     * @param triggerNodeId 指定要触发的节点ID，如果为null则触发所有触发节点
     * @param onNodeStateChange 节点状态变化回调
     */
    suspend fun triggerWorkflowWithCallback(
        id: String,
        triggerNodeId: String? = null,
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull()
            
            if (workflow == null) {
                return@withContext Result.failure(Exception("工作流不存在: $id"))
            }
            
            if (!workflow.enabled) {
                return@withContext Result.failure(Exception("工作流已禁用: ${workflow.name}"))
            }
            
            AppLogger.d(TAG, "Triggering workflow with callback: ${workflow.name} (${workflow.id})")
            if (triggerNodeId != null) {
                AppLogger.d(TAG, "With specific trigger node: $triggerNodeId")
            }
            
            // 更新为执行中状态
            updateExecutionStatus(id, ExecutionStatus.RUNNING, System.currentTimeMillis())
            
            // 创建执行器并执行工作流
            val executor = WorkflowExecutor(context)
            val result = executor.executeWorkflow(workflow, triggerNodeId, onNodeStateChange)
            
            // 更新执行统计
            val executionStatus = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED
            updateExecutionStatistics(id, executionStatus, result.executionTime)
            
            if (result.success) {
                Result.success("工作流 '${workflow.name}' 执行成功")
            } else {
                Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger workflow with callback", e)
            // 执行失败时也更新状态
            updateExecutionStatus(id, ExecutionStatus.FAILED, System.currentTimeMillis())
            Result.failure(e)
        }
    }
    
    /**
     * 更新工作流执行状态（仅状态和时间）
     */
    private suspend fun updateExecutionStatus(
        id: String,
        status: ExecutionStatus,
        executionTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull() ?: return@withContext
            
            val updatedWorkflow = workflow.copy(
                lastExecutionStatus = status,
                lastExecutionTime = executionTime
            )
            
            val file = getWorkflowFile(id)
            val content = json.encodeToString(updatedWorkflow)
            file.writeText(content)
            
            AppLogger.d(TAG, "Workflow execution status updated: $id -> $status")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update execution status", e)
        }
    }
    
    /**
     * 更新工作流执行统计信息
     */
    private suspend fun updateExecutionStatistics(
        id: String,
        status: ExecutionStatus,
        executionTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull() ?: return@withContext
            
            val updatedWorkflow = workflow.copy(
                lastExecutionStatus = status,
                lastExecutionTime = executionTime,
                totalExecutions = workflow.totalExecutions + 1,
                successfulExecutions = if (status == ExecutionStatus.SUCCESS) {
                    workflow.successfulExecutions + 1
                } else {
                    workflow.successfulExecutions
                }
            )
            
            val file = getWorkflowFile(id)
            val content = json.encodeToString(updatedWorkflow)
            file.writeText(content)
            
            AppLogger.d(TAG, "Workflow execution statistics updated: $id (total: ${updatedWorkflow.totalExecutions}, success: ${updatedWorkflow.successfulExecutions})")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update execution statistics", e)
        }
    }
    
    /**
     * Schedule a workflow
     */
    fun scheduleWorkflow(id: String): Boolean {
        return try {
            val workflowResult = kotlinx.coroutines.runBlocking { getWorkflowById(id) }
            val workflow = workflowResult.getOrNull()
            
            if (workflow == null) {
                AppLogger.w(TAG, "Workflow not found for scheduling: $id")
                return false
            }
            
            if (!workflow.enabled) {
                AppLogger.d(TAG, "Workflow is disabled, not scheduling: $id")
                return false
            }
            
            scheduler.scheduleWorkflow(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule workflow: $id", e)
            false
        }
    }
    
    /**
     * Unschedule a workflow
     */
    fun unscheduleWorkflow(id: String) {
        try {
            scheduler.cancelWorkflow(id)
            AppLogger.d(TAG, "Workflow unscheduled: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unschedule workflow: $id", e)
        }
    }
    
    /**
     * Reschedule a workflow (cancel + schedule)
     */
    fun rescheduleWorkflow(id: String): Boolean {
        unscheduleWorkflow(id)
        return scheduleWorkflow(id)
    }
    
    /**
     * Check if workflow is scheduled
     */
    suspend fun isWorkflowScheduled(id: String): Boolean {
        return try {
            scheduler.isWorkflowScheduled(id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check schedule status: $id", e)
            false
        }
    }
    
    /**
     * Get next execution time for a workflow
     */
    suspend fun getNextExecutionTime(id: String): Long? = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull() ?: return@withContext null
            scheduler.getNextExecutionTime(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get next execution time: $id", e)
            null
        }
    }


    /**
     * Finds and triggers workflows based on a Tasker event.
     * It checks all enabled workflows for a Tasker trigger node whose configuration matches the event data.
     *
     * @param params The list of parameters received from Tasker.
     */
    suspend fun triggerWorkflowsByTaskerEvent(params: List<String>?) = withContext(Dispatchers.IO) {
        if (params.isNullOrEmpty()) return@withContext

        AppLogger.d(TAG, "Checking for Tasker-triggered workflows with params: $params")
        val workflows = getAllWorkflows().getOrNull() ?: return@withContext

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node is TriggerNode && node.triggerType == "tasker") {
                        // Matching logic: The node's config expects a "command".
                        // It checks if any of the parameters from Tasker exactly matches this command.
                        // Example config: `{"command": "start_meeting"}`.
                        // This will match if any of the params from Tasker is "start_meeting" (case-insensitive).
                        val command = node.triggerConfig["command"]
                        if (command != null && params.any { it.equals(command, ignoreCase = true) }) {
                            AppLogger.d(TAG, "Tasker trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering.")
                            launch {
                                triggerWorkflow(workflow.id, node.id)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds and triggers workflows based on a received Intent.
     * It checks all enabled workflows for an Intent trigger node whose configuration matches the Intent's action.
     *
     * @param intent The Intent received by the BroadcastReceiver.
     */
    suspend fun triggerWorkflowsByIntentEvent(intent: Intent) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Checking for Intent-triggered workflows for action: ${intent.action}")
        val workflows = getAllWorkflows().getOrNull() ?: return@withContext

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node is TriggerNode && node.triggerType == "intent") {
                        // Match based on the Intent action.
                        // Example config: `{"action": "com.example.MY_ACTION"}`.
                        val expectedAction = node.triggerConfig["action"]
                        if (expectedAction != null && expectedAction.equals(intent.action, ignoreCase = true)) {
                            AppLogger.d(TAG, "Intent trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering.")
                            launch {
                                triggerWorkflow(workflow.id, node.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

