package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection

/**
 * 连接菜单对话框
 * 显示可连接的目标节点列表，支持创建和删除连接
 */
@Composable
fun ConnectionMenuDialog(
    sourceNode: WorkflowNode,
    allNodes: List<WorkflowNode>,
    existingConnections: List<WorkflowNodeConnection>,
    onCreateConnection: (targetNodeId: String) -> Unit,
    onDeleteConnection: (connectionId: String) -> Unit,
    onDismiss: () -> Unit
) {
    // 获取从源节点出发的所有连接
    val connectionsFromSource = existingConnections.filter { 
        it.sourceNodeId == sourceNode.id 
    }
    
    // 获取可以连接的目标节点（排除自己和已连接的节点）
    val connectedTargetIds = connectionsFromSource.map { it.targetNodeId }.toSet()
    val availableTargets = allNodes.filter { 
        it.id != sourceNode.id && it.id !in connectedTargetIds
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.workflow_manage_connections))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "源节点: ${sourceNode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // 已有连接列表
                if (connectionsFromSource.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.workflow_existing_connections),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    connectionsFromSource.forEach { connection ->
                        val targetNode = allNodes.find { it.id == connection.targetNodeId }
                        if (targetNode != null) {
                            ExistingConnectionItem(
                                targetNode = targetNode,
                                onDelete = { onDeleteConnection(connection.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 可连接的节点列表
                if (availableTargets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.workflow_select_target_node),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableTargets) { targetNode ->
                            AvailableTargetItem(
                                targetNode = targetNode,
                                onConnect = { onCreateConnection(targetNode.id) }
                            )
                        }
                    }
                } else {
                    if (connectionsFromSource.isEmpty()) {
                        Text(
                            text = "没有可连接的节点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    )
}

/**
 * 已有连接项
 */
@Composable
private fun ExistingConnectionItem(
    targetNode: WorkflowNode,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (targetNode.type == "trigger") "🎯" else "⚙️",
                    style = MaterialTheme.typography.bodyLarge
                )
                Column {
                    Text(
                        text = targetNode.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "→ 已连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.workflow_delete_connection)
                )
            }
        }
    }
}

/**
 * 可连接的目标节点项
 */
@Composable
private fun AvailableTargetItem(
    targetNode: WorkflowNode,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (targetNode.type == "trigger") "🎯" else "⚙️",
                    style = MaterialTheme.typography.bodyLarge
                )
                Column {
                    Text(
                        text = targetNode.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (targetNode.description.isNotEmpty()) {
                        Text(
                            text = targetNode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            
            IconButton(
                onClick = onConnect,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.workflow_create_connection)
                )
            }
        }
    }
}

