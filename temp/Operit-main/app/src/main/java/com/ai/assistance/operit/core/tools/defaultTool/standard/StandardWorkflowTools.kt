package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.WorkflowDetailResultData
import com.ai.assistance.operit.core.tools.WorkflowListResultData
import com.ai.assistance.operit.core.tools.WorkflowResultData
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 工作流管理工具
 * 提供工作流的创建、查询、更新和删除功能
 */
class StandardWorkflowTools(private val context: Context) {

    companion object {
        private const val TAG = "StandardWorkflowTools"
    }

    private val workflowRepository = WorkflowRepository(context)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }

    /**
     * 获取所有工作流
     */
    suspend fun getAllWorkflows(tool: AITool): ToolResult {
        return try {
            val result = workflowRepository.getAllWorkflows()
            
            if (result.isSuccess) {
                val workflows = result.getOrNull() ?: emptyList()
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowListResultData(
                        workflows = workflows.map { workflow ->
                            WorkflowResultData(
                                id = workflow.id,
                                name = workflow.name,
                                description = workflow.description,
                                nodeCount = workflow.nodes.size,
                                connectionCount = workflow.connections.size,
                                enabled = workflow.enabled,
                                createdAt = workflow.createdAt,
                                updatedAt = workflow.updatedAt,
                                lastExecutionTime = workflow.lastExecutionTime,
                                lastExecutionStatus = workflow.lastExecutionStatus?.name,
                                totalExecutions = workflow.totalExecutions,
                                successfulExecutions = workflow.successfulExecutions,
                                failedExecutions = workflow.failedExecutions
                            )
                        },
                        totalCount = workflows.size
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowListResultData.empty(),
                    error = "获取工作流列表失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get all workflows", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowListResultData.empty(),
                error = "获取工作流列表失败: ${e.message}"
            )
        }
    }

    /**
     * 创建工作流
     */
    suspend fun createWorkflow(tool: AITool): ToolResult {
        return try {
            val name = tool.parameters.find { it.name == "name" }?.value
            if (name.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "工作流名称不能为空"
                )
            }

            val description = tool.parameters.find { it.name == "description" }?.value ?: ""
            val nodesJson = tool.parameters.find { it.name == "nodes" }?.value
            val connectionsJson = tool.parameters.find { it.name == "connections" }?.value
            val enabled = tool.parameters.find { it.name == "enabled" }?.value?.toBoolean() ?: true

            // 解析节点
            val nodes = if (!nodesJson.isNullOrBlank()) {
                parseNodes(nodesJson)
            } else {
                emptyList()
            }

            // 解析连接
            val connections = if (!connectionsJson.isNullOrBlank()) {
                parseConnections(connectionsJson)
            } else {
                emptyList()
            }

            val workflow = Workflow(
                name = name,
                description = description,
                nodes = nodes,
                connections = connections,
                enabled = enabled
            )

            val result = workflowRepository.createWorkflow(workflow)
            
            if (result.isSuccess) {
                val createdWorkflow = result.getOrNull()!!
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowDetailResultData(
                        id = createdWorkflow.id,
                        name = createdWorkflow.name,
                        description = createdWorkflow.description,
                        nodes = createdWorkflow.nodes,
                        connections = createdWorkflow.connections,
                        enabled = createdWorkflow.enabled,
                        createdAt = createdWorkflow.createdAt,
                        updatedAt = createdWorkflow.updatedAt,
                        lastExecutionTime = createdWorkflow.lastExecutionTime,
                        lastExecutionStatus = createdWorkflow.lastExecutionStatus?.name,
                        totalExecutions = createdWorkflow.totalExecutions,
                        successfulExecutions = createdWorkflow.successfulExecutions,
                        failedExecutions = createdWorkflow.failedExecutions
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "创建工作流失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "创建工作流失败: ${e.message}"
            )
        }
    }

    /**
     * 获取工作流详情
     */
    suspend fun getWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "工作流ID不能为空"
                )
            }

            val result = workflowRepository.getWorkflowById(workflowId)
            
            if (result.isSuccess) {
                val workflow = result.getOrNull()
                if (workflow == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = WorkflowDetailResultData.empty(),
                        error = "工作流不存在: $workflowId"
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = WorkflowDetailResultData(
                            id = workflow.id,
                            name = workflow.name,
                            description = workflow.description,
                            nodes = workflow.nodes,
                            connections = workflow.connections,
                            enabled = workflow.enabled,
                            createdAt = workflow.createdAt,
                            updatedAt = workflow.updatedAt,
                            lastExecutionTime = workflow.lastExecutionTime,
                            lastExecutionStatus = workflow.lastExecutionStatus?.name,
                            totalExecutions = workflow.totalExecutions,
                            successfulExecutions = workflow.successfulExecutions,
                            failedExecutions = workflow.failedExecutions
                        )
                    )
                }
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "获取工作流失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "获取工作流失败: ${e.message}"
            )
        }
    }

    /**
     * 更新工作流
     */
    suspend fun updateWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "工作流ID不能为空"
                )
            }

            // 获取现有工作流
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "工作流不存在: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!

            // 更新字段（如果提供了新值）
            val name = tool.parameters.find { it.name == "name" }?.value ?: existingWorkflow.name
            val description = tool.parameters.find { it.name == "description" }?.value ?: existingWorkflow.description
            val nodesJson = tool.parameters.find { it.name == "nodes" }?.value
            val connectionsJson = tool.parameters.find { it.name == "connections" }?.value
            val enabledParam = tool.parameters.find { it.name == "enabled" }?.value
            val enabled = if (enabledParam != null) enabledParam.toBoolean() else existingWorkflow.enabled

            // 解析节点（如果提供了）
            val nodes = if (!nodesJson.isNullOrBlank()) {
                parseNodes(nodesJson)
            } else {
                existingWorkflow.nodes
            }

            // 解析连接（如果提供了）
            val connections = if (!connectionsJson.isNullOrBlank()) {
                parseConnections(connectionsJson)
            } else {
                existingWorkflow.connections
            }

            val updatedWorkflow = existingWorkflow.copy(
                name = name,
                description = description,
                nodes = nodes,
                connections = connections,
                enabled = enabled
            )

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            
            if (result.isSuccess) {
                val savedWorkflow = result.getOrNull()!!
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowDetailResultData(
                        id = savedWorkflow.id,
                        name = savedWorkflow.name,
                        description = savedWorkflow.description,
                        nodes = savedWorkflow.nodes,
                        connections = savedWorkflow.connections,
                        enabled = savedWorkflow.enabled,
                        createdAt = savedWorkflow.createdAt,
                        updatedAt = savedWorkflow.updatedAt,
                        lastExecutionTime = savedWorkflow.lastExecutionTime,
                        lastExecutionStatus = savedWorkflow.lastExecutionStatus?.name,
                        totalExecutions = savedWorkflow.totalExecutions,
                        successfulExecutions = savedWorkflow.successfulExecutions,
                        failedExecutions = savedWorkflow.failedExecutions
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "更新工作流失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "更新工作流失败: ${e.message}"
            )
        }
    }

    /**
     * 删除工作流
     */
    suspend fun deleteWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "工作流ID不能为空"
                )
            }

            val result = workflowRepository.deleteWorkflow(workflowId)
            
            if (result.isSuccess) {
                val deleted = result.getOrNull() ?: false
                if (deleted) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("工作流已删除: $workflowId")
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "工作流不存在或删除失败: $workflowId"
                    )
                }
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "删除工作流失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "删除工作流失败: ${e.message}"
            )
        }
    }

    /**
     * 触发工作流执行
     */
    suspend fun triggerWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "工作流ID不能为空"
                )
            }

            val result = workflowRepository.triggerWorkflow(workflowId)
            
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(result.getOrNull() ?: "工作流执行成功")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "触发工作流失败: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "触发工作流失败: ${e.message}"
            )
        }
    }

    /**
     * 解析节点JSON字符串
     */
    private fun parseNodes(nodesJson: String): List<WorkflowNode> {
        return try {
            val jsonArray = JSONArray(nodesJson)
            val nodes = mutableListOf<WorkflowNode>()

            for (i in 0 until jsonArray.length()) {
                val nodeObj = jsonArray.getJSONObject(i)
                val node = parseNode(nodeObj)
                if (node != null) {
                    nodes.add(node)
                }
            }

            nodes
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse nodes JSON", e)
            throw IllegalArgumentException("节点JSON格式错误: ${e.message}")
        }
    }

    /**
     * 解析单个节点
     */
    private fun parseNode(nodeObj: JSONObject): WorkflowNode? {
        return try {
            val type = nodeObj.optString("type", "")
            val id = nodeObj.optString("id", UUID.randomUUID().toString())
            val name = nodeObj.optString("name", "")
            val description = nodeObj.optString("description", "")
            
            // 解析位置
            val positionObj = nodeObj.optJSONObject("position")
            val position = if (positionObj != null) {
                NodePosition(
                    x = positionObj.optDouble("x", 0.0).toFloat(),
                    y = positionObj.optDouble("y", 0.0).toFloat()
                )
            } else {
                NodePosition(0f, 0f)
            }

            when (type) {
                "trigger" -> {
                    val triggerType = nodeObj.optString("triggerType", "manual")
                    val triggerConfigObj = nodeObj.optJSONObject("triggerConfig")
                    val triggerConfig = if (triggerConfigObj != null) {
                        jsonObjectToStringMap(triggerConfigObj)
                    } else {
                        emptyMap()
                    }

                    TriggerNode(
                        id = id,
                        name = name.ifBlank { "触发器" },
                        description = description,
                        position = position,
                        triggerType = triggerType,
                        triggerConfig = triggerConfig
                    )
                }
                "execute" -> {
                    val actionType = nodeObj.optString("actionType", "")
                    val actionConfigObj = nodeObj.optJSONObject("actionConfig")
                    val actionConfig = if (actionConfigObj != null) {
                        jsonObjectToParameterValueMap(actionConfigObj)
                    } else {
                        emptyMap()
                    }
                    val jsCode = nodeObj.optString("jsCode", null)

                    ExecuteNode(
                        id = id,
                        name = name.ifBlank { "执行动作" },
                        description = description,
                        position = position,
                        actionType = actionType,
                        actionConfig = actionConfig,
                        jsCode = jsCode
                    )
                }
                else -> {
                    AppLogger.w(TAG, "Unknown node type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse node", e)
            null
        }
    }

    /**
     * 解析连接JSON字符串
     */
    private fun parseConnections(connectionsJson: String): List<WorkflowNodeConnection> {
        return try {
            val jsonArray = JSONArray(connectionsJson)
            val connections = mutableListOf<WorkflowNodeConnection>()

            for (i in 0 until jsonArray.length()) {
                val connObj = jsonArray.getJSONObject(i)
                val connection = parseConnection(connObj)
                if (connection != null) {
                    connections.add(connection)
                }
            }

            connections
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse connections JSON", e)
            throw IllegalArgumentException("连接JSON格式错误: ${e.message}")
        }
    }

    /**
     * 解析单个连接
     */
    private fun parseConnection(connObj: JSONObject): WorkflowNodeConnection? {
        return try {
            val id = connObj.optString("id", UUID.randomUUID().toString())
            val sourceNodeId = connObj.optString("sourceNodeId", "")
            val targetNodeId = connObj.optString("targetNodeId", "")
            val condition = connObj.optString("condition", null)

            if (sourceNodeId.isBlank() || targetNodeId.isBlank()) {
                AppLogger.w(TAG, "Connection missing source or target node ID")
                return null
            }

            WorkflowNodeConnection(
                id = id,
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                condition = condition
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse connection", e)
            null
        }
    }

    /**
     * 将JSONObject转换为Map<String, String>
     */
    private fun jsonObjectToStringMap(jsonObject: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.optString(key, "")
        }
        return map
    }

    /**
     * 将JSONObject转换为Map<String, ParameterValue>
     */
    private fun jsonObjectToParameterValueMap(jsonObject: JSONObject): Map<String, ParameterValue> {
        val map = mutableMapOf<String, ParameterValue>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = ParameterValue.StaticValue(jsonObject.optString(key, ""))
        }
        return map
    }
}

