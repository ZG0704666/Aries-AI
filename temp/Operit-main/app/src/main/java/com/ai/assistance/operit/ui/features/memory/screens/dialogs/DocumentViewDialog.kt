package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.Memory

@Composable
fun DocumentViewDialog(
    memoryTitle: String,
    onTitleChange: (String) -> Unit,
    chunks: List<DocumentChunk>,
    chunkStates: Map<Long, String>,
    onChunkChange: (Long, String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    folderPath: String = "" // 添加文件夹路径参数
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.85f),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = memoryTitle,
                    onValueChange = onTitleChange,
                    label = { Text("文档标题") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                if (folderPath.isNotEmpty()) {
                    Text(
                        text = "文件夹: $folderPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("在文档内搜索...") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            onPerformSearch()
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onPerformSearch()
                            keyboardController?.hide()
                        }
                    )
                )
                if (chunks.isEmpty() && searchQuery.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到相关内容。")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chunks, key = { it.id }) { chunk ->
                            val content = chunkStates[chunk.id] ?: ""
                            OutlinedTextField(
                                value = content,
                                onValueChange = { newContent -> onChunkChange(chunk.id, newContent) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                label = { Text("区块 ${chunk.chunkIndex + 1}") }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
             OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
            ) {
                Button(onClick = onSave) {
                    Text("保存全部")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除文档")
                }
            }
        }
    )
} 