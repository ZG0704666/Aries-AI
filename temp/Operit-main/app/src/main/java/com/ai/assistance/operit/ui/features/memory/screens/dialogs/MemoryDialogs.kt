package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryInfoDialog(
        memory: Memory,
        onDismiss: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "记忆详情") },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("标题: ${memory.title}", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text("内容:", style = MaterialTheme.typography.titleSmall)
                    Text(memory.content)
                    HorizontalDivider()
                    Text("文件夹: ${memory.folderPath?.ifEmpty { "未分类" }}", style = MaterialTheme.typography.bodySmall)
                    Text("UUID: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                    Text("来源: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                    Text(
                            "重要性: ${String.format("%.2f", memory.importance)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "可信度: ${String.format("%.2f", memory.credibility)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "创建时间: ${dateFormat.format(memory.createdAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "更新时间: ${dateFormat.format(memory.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onEdit) { Text("编辑") }
                    Button(
                            onClick = onDelete,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text("删除") }
                    OutlinedButton(onClick = onDismiss) { Text("关闭") }
                }
            }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeInfoDialog(
    edge: Edge,
    graph: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceNode = graph.nodes.find { it.id == edge.sourceId }
    val targetNode = graph.nodes.find { it.id == edge.targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("从: ${sourceNode?.label ?: "未知"}")
                Text("到: ${targetNode?.label ?: "未知"}")
                HorizontalDivider()
                Text("类型: ${edge.label}")
                Text("权重: ${edge.weight}")
                // 这里可以显示description，如果MemoryLink里有的话
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit) { Text("编辑") }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
                OutlinedButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
fun EditEdgeDialog(
    edge: Edge,
    onDismiss: () -> Unit,
    onSave: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf(edge.label ?: "related") }
    var weight by remember { mutableStateOf(edge.weight.toString()) }
    var description by remember { mutableStateOf("") } // 假设需要编辑description

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑连接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("类型") })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("权重") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }) { Text("保存") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun LinkMemoryDialog(
    sourceNodeLabel: String,
    targetNodeLabel: String,
    onDismiss: () -> Unit,
    onLink: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf("related") }
    var weight by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 '$sourceNodeLabel' 到 '$targetNodeLabel'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("类型") }
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("权重") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            ) { Text("创建连接") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "确定要删除选中的 $selectedCount 个记忆节点吗？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "此操作不可撤销，删除后将无法恢复这些记忆及其相关连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 