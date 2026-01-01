package com.ai.assistance.operit.ui.features.workflow.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 工作流ViewModel
 * 管理工作流的状态和业务逻辑
 */
class WorkflowViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WorkflowRepository(application)
    
    var workflows by mutableStateOf<List<Workflow>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var currentWorkflow by mutableStateOf<Workflow?>(null)
        private set
    
    // 节点执行状态 Map
    private val _nodeExecutionStates = MutableStateFlow<Map<String, NodeExecutionState>>(emptyMap())
    val nodeExecutionStates: StateFlow<Map<String, NodeExecutionState>> = _nodeExecutionStates.asStateFlow()
    
    init {
        loadWorkflows()
    }
    
    /**
     * 加载所有工作流
     */
    fun loadWorkflows() {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getAllWorkflows().fold(
                onSuccess = { workflows = it },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 根据ID加载工作流
     */
    fun loadWorkflow(id: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(id).fold(
                onSuccess = { currentWorkflow = it },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 创建工作流
     */
    fun createWorkflow(name: String, description: String, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val workflow = Workflow(
                name = name,
                description = description
            )
            
            repository.createWorkflow(workflow).fold(
                onSuccess = { 
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新工作流
     */
    fun updateWorkflow(workflow: Workflow, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.updateWorkflow(workflow).fold(
                onSuccess = { 
                    currentWorkflow = it
                    loadWorkflows()
                    onSuccess()
                },
                onFailure = { error = it.message ?: "更新工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除工作流
     */
    fun deleteWorkflow(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.deleteWorkflow(id).fold(
                onSuccess = { 
                    if (it) {
                        loadWorkflows()
                        onSuccess()
                    } else {
                        error = "删除工作流失败"
                    }
                },
                onFailure = { error = it.message ?: "删除工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 触发工作流
     */
    fun triggerWorkflow(id: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            // 不设置全局 isLoading，以便用户可以看到执行过程
            error = null
            _nodeExecutionStates.value = emptyMap()
            
            repository.triggerWorkflowWithCallback(id) { nodeId, state ->
                // 实时更新节点执行状态，用户可以在画布上看到执行进度
                _nodeExecutionStates.value = _nodeExecutionStates.value + (nodeId to state)
            }.fold(
                onSuccess = { message -> 
                    // 刷新工作流列表以显示更新的执行统计
                    loadWorkflows()
                    // 如果当前正在查看这个工作流，也刷新它
                    if (currentWorkflow?.id == id) {
                        loadWorkflow(id)
                    }
                    onComplete(message)
                },
                onFailure = { error -> 
                    // 即使失败也刷新，因为失败状态也会被记录
                    loadWorkflows()
                    if (currentWorkflow?.id == id) {
                        loadWorkflow(id)
                    }
                    this@WorkflowViewModel.error = error.message ?: "触发工作流失败"
                    onComplete("执行失败: ${error.message}")
                }
            )
        }
    }
    
    /**
     * 清除节点执行状态
     */
    fun clearNodeExecutionStates() {
        _nodeExecutionStates.value = emptyMap()
    }
    
    /**
     * 添加节点到工作流
     */
    fun addNode(workflowId: String, node: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes + node,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "添加节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除节点
     */
    fun deleteNode(workflowId: String, nodeId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.filter { it.id != nodeId },
                        connections = workflow.connections.filter { 
                            it.sourceNodeId != nodeId && it.targetNodeId != nodeId 
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "删除节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点
     */
    fun updateNode(workflowId: String, nodeId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.map { if (it.id == nodeId) updatedNode else it },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "更新节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点（重载方法，直接接受节点对象）
     */
    fun updateNode(workflowId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        updateNode(workflowId, updatedNode.id, updatedNode, onSuccess)
    }
    
    /**
     * 创建连接
     */
    fun createConnection(
        workflowId: String,
        sourceId: String,
        targetId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    
                    // 检查连接是否已存在
                    val connectionExists = workflow.connections.any {
                        it.sourceNodeId == sourceId && it.targetNodeId == targetId
                    }
                    
                    if (connectionExists) {
                        error = "连接已存在"
                        isLoading = false
                        return@fold
                    }
                    
                    val newConnection = com.ai.assistance.operit.data.model.WorkflowNodeConnection(
                        sourceNodeId = sourceId,
                        targetNodeId = targetId
                    )
                    
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections + newConnection,
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "创建连接失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除连接
     */
    fun deleteConnection(
        workflowId: String,
        connectionId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections.filter { it.id != connectionId },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "删除连接失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点位置
     */
    fun updateNodePosition(
        workflowId: String,
        nodeId: String,
        x: Float,
        y: Float,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedNodes = workflow.nodes.map { node ->
                        if (node.id == nodeId) {
                            node.position.x = x
                            node.position.y = y
                            node
                        } else {
                            node
                        }
                    }
                    val updatedWorkflow = workflow.copy(
                        nodes = updatedNodes,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            onSuccess()
                        },
                        onFailure = { /* 静默失败，位置更新不是关键操作 */ }
                    )
                },
                onFailure = { /* 静默失败 */ }
            )
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        error = null
    }
    
    /**
     * Schedule a workflow
     */
    fun scheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val success = repository.scheduleWorkflow(workflowId)
                if (success) {
                    loadWorkflows()
                    onSuccess()
                } else {
                    onFailure("无法调度工作流")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "调度工作流失败")
            }
        }
    }
    
    /**
     * Unschedule a workflow
     */
    fun unscheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.unscheduleWorkflow(workflowId)
                loadWorkflows()
                onSuccess()
            } catch (e: Exception) {
                error = e.message ?: "取消调度失败"
            }
        }
    }
    
    /**
     * Check if workflow is scheduled
     */
    fun isWorkflowScheduled(workflowId: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            repository.isWorkflowScheduled(workflowId)
        }
    }
    
    /**
     * Get next execution time for workflow
     */
    fun getNextExecutionTime(workflowId: String, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val nextTime = repository.getNextExecutionTime(workflowId)
            onResult(nextTime)
        }
    }
}

