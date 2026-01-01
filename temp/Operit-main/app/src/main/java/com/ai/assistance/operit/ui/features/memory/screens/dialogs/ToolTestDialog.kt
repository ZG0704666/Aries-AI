package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun ToolTestDialog(
    onDismiss: () -> Unit,
    onExecute: (String) -> Unit,
    result: String,
    isLoading: Boolean
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模拟AI附着记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入查询内容，模拟AI从知识库中检索并附着相关记忆：")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("查询") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (result.isNotEmpty()) {
                    Text("结果:", style = MaterialTheme.typography.titleSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Text(result, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExecute(query) }, enabled = !isLoading) {
                Text("执行")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
} 