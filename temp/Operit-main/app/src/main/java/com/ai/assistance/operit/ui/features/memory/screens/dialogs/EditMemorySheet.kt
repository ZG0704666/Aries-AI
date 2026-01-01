package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Memory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemorySheet(
    memory: Memory?,
    allFolderPaths: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        memory: Memory?,
        title: String,
        content: String,
        contentType: String,
        source: String,
        credibility: Float,
        importance: Float,
        folderPath: String,
        tags: List<String>
    ) -> Unit
) {
    var title by remember { mutableStateOf(memory?.title ?: "") }
    var content by remember { mutableStateOf(memory?.content ?: "") }
    var contentType by remember { mutableStateOf(memory?.contentType ?: "text/plain") }
    var source by remember { mutableStateOf(memory?.source ?: "user_input") }
    var credibility by remember { mutableStateOf(memory?.credibility ?: 0.8f) }
    var importance by remember { mutableStateOf(memory?.importance ?: 0.5f) }
    var folderPath by remember { mutableStateOf(memory?.folderPath ?: "未分类") }
    val tags = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(memory) {
        memory?.tags?.let {
            tags.clear()
            tags.addAll(it.map { tag -> tag.name })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (memory == null) "新建记忆" else "编辑记忆",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp),
                enabled = memory?.isDocumentNode != true // 文档节点内容不可编辑
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Folder Path Dropdown
            FolderSelector(
                allFolderPaths = allFolderPaths,
                selectedPath = folderPath,
                onPathSelected = { folderPath = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Tags Editor
            TagsEditor(tags = tags, onTagsChanged = { tags.clear(); tags.addAll(it) })
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("来源") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Credibility Slider
            Text("可信度: ${String.format("%.2f", credibility)}")
            Slider(
                value = credibility,
                onValueChange = { credibility = it },
                valueRange = 0f..1f
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Importance Slider
            Text("重要性: ${String.format("%.2f", importance)}")
            Slider(
                value = importance,
                onValueChange = { importance = it },
                valueRange = 0f..1f
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    onSave(memory, title, content, contentType, source, credibility, importance, folderPath, tags.toList())
                    onDismiss()
                }) {
                    Text("保存")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(
    allFolderPaths: List<String>,
    selectedPath: String,
    onPathSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPath,
            onValueChange = {},
            readOnly = true,
            label = { Text("文件夹") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allFolderPaths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path) },
                    onClick = {
                        onPathSelected(path)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        Text("标签", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { /* Not used */ },
                    label = { Text(tag) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onTagsChanged(tags - tag) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Remove tag")
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            placeholder = { Text("添加新标签...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (newTagText.isNotBlank() && newTagText !in tags) {
                    onTagsChanged(tags + newTagText)
                    newTagText = ""
                }
                keyboardController?.hide()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (newTagText.isNotBlank() && newTagText !in tags) {
                        onTagsChanged(tags + newTagText)
                        newTagText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            }
        )
    }
}
