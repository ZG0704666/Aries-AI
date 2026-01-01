package com.ai.assistance.operit.ui.features.workflow.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.TriggerNode
import com.ai.assistance.operit.data.model.ExecuteNode
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.workflow.viewmodel.WorkflowViewModel
import com.ai.assistance.operit.ui.features.workflow.components.GridWorkflowCanvas
import com.ai.assistance.operit.ui.features.workflow.components.ConnectionMenuDialog
import com.ai.assistance.operit.ui.features.workflow.components.NodeActionMenuDialog
import com.ai.assistance.operit.ui.features.workflow.components.ScheduleConfigDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailScreen(
    workflowId: String,
    onNavigateBack: () -> Unit,
    viewModel: WorkflowViewModel = viewModel()
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTriggerResult by remember { mutableStateOf<String?>(null) }
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showDeleteNodeDialog by remember { mutableStateOf<String?>(null) }
    var showNodeActionMenu by remember { mutableStateOf<String?>(null) }
    var showConnectionMenu by remember { mutableStateOf<String?>(null) }
    var showEditNodeDialog by remember { mutableStateOf<WorkflowNode?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(workflowId) {
        viewModel.loadWorkflow(workflowId)
    }

    val workflow = viewModel.currentWorkflow
    val nodeExecutionStates by viewModel.nodeExecutionStates.collectAsState()

    CustomScaffold(
        floatingActionButton = {
            if (workflow != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Animated secondary actions
                    AnimatedVisibility(
                        visible = isFabMenuExpanded,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (workflow.enabled) {
                                SpeedDialAction(
                                    text = "触发工作流",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = {
                                        viewModel.triggerWorkflow(workflowId) { result -> showTriggerResult = result }
                                        isFabMenuExpanded = false
                                    }
                                )
                            }
                            SpeedDialAction(
                                text = "添加节点",
                                icon = Icons.Default.Add,
                                onClick = {
                                    showAddNodeDialog = true
                                    isFabMenuExpanded = false
                                }
                            )
                            SpeedDialAction(
                                text = "编辑工作流",
                                icon = Icons.Default.Edit,
                                onClick = {
                                    showEditDialog = true
                                    isFabMenuExpanded = false
                                }
                            )
                            SpeedDialAction(
                                text = "删除工作流",
                                icon = Icons.Default.Delete,
                                onClick = {
                                    showDeleteDialog = true
                                    isFabMenuExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    // Main FAB
                    FloatingActionButton(
                        onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        val rotation by animateFloatAsState(targetValue = if (isFabMenuExpanded) 45f else 0f, label = "fab_icon_rotation")
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "打开操作菜单",
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            when {
                viewModel.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                workflow == null -> {
                    Text(
                        text = "工作流不存在",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 网格画布
                        if (workflow.nodes.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "📋",
                                        style = MaterialTheme.typography.displayMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.workflow_nodes_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "点击右上角 + 按钮添加节点",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            GridWorkflowCanvas(
                                nodes = workflow.nodes,
                                connections = workflow.connections,
                                nodeExecutionStates = nodeExecutionStates,
                                onNodePositionChanged = { nodeId, x, y ->
                                    viewModel.updateNodePosition(workflowId, nodeId, x, y)
                                },
                                onNodeLongPress = { nodeId ->
                                    // 长按节点显示操作菜单
                                    showNodeActionMenu = nodeId
                                },
                                onNodeClick = { nodeId ->
                                    // 点击节点不做任何操作（避免拖动时误触发）
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 编辑对话框
            if (showEditDialog && workflow != null) {
                EditWorkflowDialog(
                    workflow = workflow,
                    onDismiss = { showEditDialog = false },
                    onSave = { name, description, enabled ->
                        viewModel.updateWorkflow(
                            workflow.copy(
                                name = name,
                                description = description,
                                enabled = enabled
                            )
                        ) {
                            showEditDialog = false
                        }
                    }
                )
            }

            // 删除确认对话框
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("确认删除") },
                    text = { Text("确定要删除工作流 \"${workflow?.name}\" 吗？此操作不可恢复。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteWorkflow(workflowId) {
                                    showDeleteDialog = false
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 触发结果提示
            showTriggerResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { showTriggerResult = null },
                    title = { Text("执行结果") },
                    text = { Text(result) },
                    confirmButton = {
                        TextButton(onClick = { showTriggerResult = null }) {
                            Text("确定")
                        }
                    }
                )
            }

            // 错误提示
            viewModel.error?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("错误") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("确定")
                        }
                    }
                )
            }

            // 添加节点对话框
            if (showAddNodeDialog && workflow != null) {
                NodeDialog(
                    node = null, // 创建模式
                    workflow = workflow,
                    onDismiss = { showAddNodeDialog = false },
                    onConfirm = { node ->
                        viewModel.addNode(workflowId, node) {
                            showAddNodeDialog = false
                        }
                    }
                )
            }

            // 删除节点确认对话框
            showDeleteNodeDialog?.let { nodeId ->
                val node = workflow?.nodes?.find { it.id == nodeId }
                AlertDialog(
                    onDismissRequest = { showDeleteNodeDialog = null },
                    title = { Text("确认删除") },
                    text = { Text("确定要删除节点 \"${node?.name}\" 吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteNode(workflowId, nodeId) {
                                    showDeleteNodeDialog = null
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteNodeDialog = null }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 节点操作菜单对话框
            showNodeActionMenu?.let { nodeId ->
                val node = workflow?.nodes?.find { it.id == nodeId }
                if (node != null) {
                    NodeActionMenuDialog(
                        nodeName = node.name,
                        onEdit = {
                            showEditNodeDialog = node
                            showNodeActionMenu = null
                        },
                        onConnect = {
                            showConnectionMenu = nodeId
                            showNodeActionMenu = null
                        },
                        onDismiss = {
                            showNodeActionMenu = null
                        }
                    )
                }
            }

            // 节点编辑对话框
            if (workflow != null) {
                showEditNodeDialog?.let { node ->
                    NodeDialog(
                        node = node, // 编辑模式
                        workflow = workflow,
                        onDismiss = { showEditNodeDialog = null },
                        onConfirm = { updatedNode ->
                            viewModel.updateNode(workflowId, updatedNode) {
                                showEditNodeDialog = null
                            }
                        }
                    )
                }
            }

            // 连接菜单对话框
            showConnectionMenu?.let { sourceNodeId ->
                val sourceNode = workflow?.nodes?.find { it.id == sourceNodeId }
                if (sourceNode != null && workflow != null) {
                    ConnectionMenuDialog(
                        sourceNode = sourceNode,
                        allNodes = workflow.nodes,
                        existingConnections = workflow.connections,
                        onCreateConnection = { targetNodeId ->
                            viewModel.createConnection(workflowId, sourceNodeId, targetNodeId) {
                                // 连接创建成功，保持对话框打开以便继续操作
                            }
                        },
                        onDeleteConnection = { connectionId ->
                            viewModel.deleteConnection(workflowId, connectionId) {
                                // 连接删除成功
                            }
                        },
                        onDismiss = { showConnectionMenu = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(icon, contentDescription = text)
        }
    }
}

/**
 * 参数配置数据类
 */
data class ParameterConfig(
    val key: String,
    val isReference: Boolean, // true表示引用节点，false表示静态值
    val value: String // 静态值或节点ID
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDialog(
    node: WorkflowNode? = null, // null 表示创建新节点，非 null 表示编辑
    workflow: Workflow, // 用于获取前置节点信息
    onDismiss: () -> Unit,
    onConfirm: (WorkflowNode) -> Unit
) {
    // 判断是编辑还是创建模式
    val isEditMode = node != null
    
    // 初始化节点类型
    val initialNodeType = when (node) {
        is TriggerNode -> "trigger"
        is ExecuteNode -> "execute"
        else -> "trigger"
    }
    
    var nodeType by remember { mutableStateOf(initialNodeType) }
    var name by remember { mutableStateOf(node?.name ?: "") }
    var description by remember { mutableStateOf(node?.description ?: "") }
    var expanded by remember { mutableStateOf(false) }

    // 执行节点配置
    var actionType by remember {
        mutableStateOf(if (node is ExecuteNode) node.actionType else "")
    }
    var actionTypeExpanded by remember { mutableStateOf(false) }
    
    // 将 actionConfig (Map<String, ParameterValue>) 转换为可变的参数配置列表
    val initialActionConfigPairs = if (node is ExecuteNode) {
        node.actionConfig.map { (key, paramValue) ->
            when (paramValue) {
                is com.ai.assistance.operit.data.model.ParameterValue.StaticValue -> 
                    ParameterConfig(key, false, paramValue.value)
                is com.ai.assistance.operit.data.model.ParameterValue.NodeReference -> 
                    ParameterConfig(key, true, paramValue.nodeId)
            }
        }
    } else {
        emptyList()
    }
    var actionConfigPairs by remember { mutableStateOf(initialActionConfigPairs) }
    
    // 获取可用的前置节点
    val availablePredecessors = if (node != null) {
        workflow.connections
            .filter { it.targetNodeId == node.id }
            .mapNotNull { conn -> 
                workflow.nodes.find { it.id == conn.sourceNodeId }
            }
    } else {
        emptyList()
    }

    // 触发节点配置
    var triggerType by remember {
        mutableStateOf(if (node is TriggerNode) node.triggerType else "manual")
    }
    var triggerTypeExpanded by remember { mutableStateOf(false) }
    var triggerConfig by remember {
        mutableStateOf(
            if (node is TriggerNode && node.triggerConfig.isNotEmpty()) {
                org.json.JSONObject(node.triggerConfig).toString(2)
            } else ""
        )
    }
    
    // 定时配置对话框状态
    var showScheduleDialog by remember { mutableStateOf(false) }

    val nodeTypes = mapOf(
        "trigger" to "触发节点",
        "execute" to "执行节点"
    )

    val triggerTypes = mapOf(
        "manual" to "手动触发",
        "schedule" to "定时触发",
        "tasker" to "Tasker 触发",
        "intent" to "Intent 触发"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "编辑节点" else "添加节点") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 节点类型选择（仅在创建模式下显示）
                if (!isEditMode) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = nodeTypes[nodeType] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("节点类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        nodeTypes.forEach { (key, value) ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    nodeType = key
                                    expanded = false
                                }
                            )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称（留空自动生成）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { 
                        Text(
                            when (nodeType) {
                                "trigger" -> when (triggerType) {
                                    "manual" -> "如: 手动触发"
                                    "schedule" -> "如: 定时触发"
                                    "tasker" -> "如: Tasker 触发"
                                    "intent" -> "如: Intent 触发"
                                    else -> "如: 触发器"
                                }
                                "execute" -> "如: ${actionType.takeIf { it.isNotBlank() } ?: "执行动作"}"
                                else -> nodeTypes[nodeType] ?: ""
                            }
                        )
                    }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // 根据节点类型显示不同的配置选项
                when (nodeType) {
                    "execute" -> {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "执行配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 工具名称输入
                            OutlinedTextField(
                            value = actionType,
                            onValueChange = { actionType = it },
                            label = { Text("工具名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("例如: execute_shell") }
                        )

                        // 动态参数配置
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "工具参数",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        actionConfigPairs.forEachIndexed { index, param ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 参数名输入
                                    OutlinedTextField(
                                        value = param.key,
                                        onValueChange = { newKey ->
                                            val newList = actionConfigPairs.toMutableList()
                                            newList[index] = param.copy(key = newKey)
                                            actionConfigPairs = newList
                                        },
                                        label = { Text("参数名") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    // 参数值输入框（如果是引用则显示节点名称）
                                    OutlinedTextField(
                                        value = if (param.isReference) {
                                            // 显示引用节点的名称
                                            workflow.nodes.find { it.id == param.value }?.name ?: "[未知节点]"
                                        } else {
                                            param.value
                                        },
                                        onValueChange = { newValue ->
                                            if (!param.isReference) {
                                                val newList = actionConfigPairs.toMutableList()
                                                newList[index] = param.copy(value = newValue)
                                                actionConfigPairs = newList
                                            }
                                        },
                                        label = { Text("参数值") },
                                        modifier = Modifier.weight(1f),
                                        readOnly = param.isReference,
                                        colors = if (param.isReference) {
                                            OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.primary,
                                                disabledBorderColor = MaterialTheme.colorScheme.primary,
                                                disabledLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            OutlinedTextFieldDefaults.colors()
                                        },
                                        enabled = !param.isReference,
                                        prefix = if (param.isReference) {
                                            { Text("🔗 ", style = MaterialTheme.typography.bodyLarge) }
                                        } else null
                                    )
                                    
                                    // 连接选择器按钮
                                    var showNodeSelector by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showNodeSelector = true },
                                        enabled = availablePredecessors.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "选择前置节点",
                                            tint = if (param.isReference) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }
                                    
                                    // 前置节点选择下拉菜单
                                    DropdownMenu(
                                        expanded = showNodeSelector,
                                        onDismissRequest = { showNodeSelector = false }
                                    ) {
                                        // 选项：切换回静态值
                                        if (param.isReference) {
                                            DropdownMenuItem(
                                                text = { Text("使用静态值") },
                                                onClick = {
                                                    val newList = actionConfigPairs.toMutableList()
                                                    newList[index] = param.copy(isReference = false, value = "")
                                                    actionConfigPairs = newList
                                                    showNodeSelector = false
                                                }
                                            )
                                            HorizontalDivider()
                                        }
                                        
                                        // 显示所有可用的前置节点
                                        availablePredecessors.forEach { predecessorNode ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(
                                                            text = predecessorNode.name,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            text = predecessorNode.type,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    val newList = actionConfigPairs.toMutableList()
                                                    newList[index] = param.copy(isReference = true, value = predecessorNode.id)
                                                    actionConfigPairs = newList
                                                    showNodeSelector = false
                                                }
                                            )
                                        }
                                        
                                        if (availablePredecessors.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("无可用前置节点") },
                                                onClick = { showNodeSelector = false },
                                                enabled = false
                                            )
                                        }
                                    }
                                    
                                    // 删除按钮
                                    IconButton(onClick = {
                                        val newList = actionConfigPairs.toMutableList()
                                        newList.removeAt(index)
                                        actionConfigPairs = newList
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除参数")
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                actionConfigPairs = actionConfigPairs + ParameterConfig("", false, "")
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加参数")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加参数")
                        }
                    }
                    "trigger" -> {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "触发配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 触发类型选择
                        ExposedDropdownMenuBox(
                            expanded = triggerTypeExpanded,
                            onExpandedChange = { triggerTypeExpanded = !triggerTypeExpanded }
                        ) {
                            OutlinedTextField(
                                value = triggerTypes[triggerType] ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("触发类型") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = triggerTypeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = triggerTypeExpanded,
                                onDismissRequest = { triggerTypeExpanded = false }
                            ) {
                                triggerTypes.forEach { (key, value) ->
                                    DropdownMenuItem(
                                        text = { Text(value) },
                                        onClick = {
                                            triggerType = key
                                            triggerTypeExpanded = false
                                            // 设置默认配置示例
                                            triggerConfig = when (key) {
                                                "schedule" -> """{"schedule_type":"interval","interval_ms":"900000","repeat":"true","enabled":"true"}"""
                                                "tasker" -> """{"variable_name": "%evtprm()"}"""
                                                "intent" -> """{"action": "com.example.MY_ACTION"}"""
                                                else -> "{}"
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (triggerType == "schedule") {
                            Button(
                                onClick = { showScheduleDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("配置定时触发")
                            }
                            
                            if (triggerConfig.isNotBlank()) {
                                Text(
                                    text = "已配置定时",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else if (triggerType != "manual") {
                            OutlinedTextField(
                                value = triggerConfig,
                                onValueChange = { triggerConfig = it },
                                label = { Text("触发配置 (JSON)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                placeholder = { Text("""{"key": "value"}""") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 自动生成节点名称
                    val nodeName = if (name.isBlank()) {
                        when (nodeType) {
                            "trigger" -> {
                                // 根据触发类型生成名称
                                when (triggerType) {
                                    "manual" -> "手动触发"
                                    "schedule" -> "定时触发"
                                    "tasker" -> "Tasker 触发"
                                    "intent" -> "Intent 触发"
                                    else -> "触发器"
                                }
                            }
                            "execute" -> {
                                // 根据动作类型生成名称
                                actionType.takeIf { it.isNotBlank() } ?: "执行动作"
                            }
                            else -> nodeTypes[nodeType] ?: "节点"
                        }
                    } else {
                        name
                    }
                    
                    val resultNode: WorkflowNode = if (isEditMode && node != null) {
                        // 编辑模式：更新现有节点
                        when (node) {
                            is TriggerNode -> node.copy(
                            name = nodeName,
                            description = description,
                            triggerType = triggerType,
                            triggerConfig = if (triggerConfig.isNotBlank()) {
                                try {
                                    org.json.JSONObject(triggerConfig).let { json ->
                                        json.keys().asSequence().associateWith { json.getString(it) }
                                    }
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                            } else emptyMap()
                        )
                            is ExecuteNode -> node.copy(
                            name = nodeName,
                            description = description,
                            actionType = actionType,
                                actionConfig = actionConfigPairs
                                    .filter { it.key.isNotBlank() } // 过滤掉空的参数名
                                    .associate { param ->
                                        param.key to if (param.isReference) {
                                            ParameterValue.NodeReference(param.value)
                                        } else {
                                            ParameterValue.StaticValue(param.value)
                                        }
                                    }
                            )
                            else -> node
                        }
                    } else {
                        // 创建模式：创建新节点
                        when (nodeType) {
                            "trigger" -> TriggerNode(
                                name = nodeName,
                                description = description,
                                triggerType = triggerType,
                                triggerConfig = if (triggerConfig.isNotBlank()) {
                                    try {
                                        org.json.JSONObject(triggerConfig).let { json ->
                                        json.keys().asSequence().associateWith { json.getString(it) }
                                    }
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                            } else emptyMap()
                        )
                            "execute" -> ExecuteNode(
                                name = nodeName,
                                description = description,
                                actionType = actionType,
                                actionConfig = actionConfigPairs
                                    .filter { it.key.isNotBlank() } // 过滤掉空的参数名
                                    .associate { param ->
                                        param.key to if (param.isReference) {
                                            ParameterValue.NodeReference(param.value)
                                        } else {
                                            ParameterValue.StaticValue(param.value)
                                        }
                                    }
                        )
                        else -> TriggerNode(name = nodeName, description = description)
                    }
                    }
                    onConfirm(resultNode)
                }
            ) {
                Text(if (isEditMode) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
    
    // 定时配置对话框
    if (showScheduleDialog) {
        // 解析现有配置
        val parsedConfig = if (triggerConfig.isNotBlank()) {
            try {
                val json = org.json.JSONObject(triggerConfig)
                val map = mutableMapOf<String, String>()
                json.keys().forEach { key ->
                    map[key] = json.getString(key)
                }
                map
                                } catch (e: Exception) {
                                    emptyMap()
                                }
        } else {
                                    emptyMap()
                                }
        
        ScheduleConfigDialog(
            initialScheduleType = parsedConfig["schedule_type"] ?: "interval",
            initialConfig = parsedConfig,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { scheduleType, config ->
                // 将 Map 转换为 JSON 字符串
                val json = org.json.JSONObject()
                json.put("schedule_type", scheduleType)
                config.forEach { (key, value) ->
                    json.put(key, value)
                }
                triggerConfig = json.toString(2)
                showScheduleDialog = false
            }
        )
    }
}


@Composable
fun EditWorkflowDialog(
    workflow: Workflow,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(workflow.name) }
    var description by remember { mutableStateOf(workflow.description) }
    var enabled by remember { mutableStateOf(workflow.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑工作流") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workflow_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.workflow_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用工作流")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, enabled) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

