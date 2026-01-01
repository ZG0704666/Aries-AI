package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.data.converter.ExportFormat
import com.ai.assistance.operit.data.converter.ChatFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ChatHistoryOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    DELETING,
    DELETED,
    FAILED
}

enum class MemoryOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    FAILED
}

enum class ModelConfigOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    FAILED
}

@Composable
fun ChatBackupSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var memoryRepo by remember { mutableStateOf<MemoryRepository?>(null) }

    var totalChatCount by remember { mutableStateOf(0) }
    var totalMemoryCount by remember { mutableStateOf(0) }
    var totalMemoryLinkCount by remember { mutableStateOf(0) }
    var totalModelConfigCount by remember { mutableStateOf(0) }
    var operationState by remember { mutableStateOf(ChatHistoryOperation.IDLE) }
    var operationMessage by remember { mutableStateOf("") }
    var memoryOperationState by remember { mutableStateOf(MemoryOperation.IDLE) }
    var memoryOperationMessage by remember { mutableStateOf("") }
    var modelConfigOperationState by remember { mutableStateOf(ModelConfigOperation.IDLE) }
    var modelConfigOperationMessage by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMemoryImportStrategyDialog by remember { mutableStateOf(false) }
    var pendingMemoryImportUri by remember { mutableStateOf<Uri?>(null) }
    
    // 模型配置导出安全警告
    var showModelConfigExportWarning by remember { mutableStateOf(false) }
    var exportedModelConfigPath by remember { mutableStateOf("") }
    
    // Operit 目录备份文件统计
    var chatBackupFileCount by remember { mutableStateOf(0) }
    var memoryBackupFileCount by remember { mutableStateOf(0) }
    var modelConfigBackupFileCount by remember { mutableStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }

    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    var selectedExportProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedImportProfileId by remember { mutableStateOf(activeProfileId) }
    var showExportProfileDialog by remember { mutableStateOf(false) }
    var showImportProfileDialog by remember { mutableStateOf(false) }
    
    // 导出格式选择
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf(ExportFormat.JSON) }
    
    // 导入格式选择
    var showImportFormatDialog by remember { mutableStateOf(false) }
    var selectedImportFormat by remember { mutableStateOf(ChatFormat.OPERIT) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(activeProfileId) {
        memoryRepo = MemoryRepository(context, activeProfileId)
        selectedExportProfileId = activeProfileId
        selectedImportProfileId = activeProfileId
    }

    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }

    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }

    LaunchedEffect(memoryRepo) {
        memoryRepo?.let { repo ->
            val memories = repo.searchMemories("*")
            totalMemoryCount = memories.count { !it.isDocumentNode }
            val graph = repo.getMemoryGraph()
            totalMemoryLinkCount = graph.edges.size
        }
    }
    
    LaunchedEffect(Unit) {
        modelConfigManager.configListFlow.collect { configList ->
            totalModelConfigCount = configList.size
        }
    }
    
    // 扫描 Operit 目录中的备份文件
    LaunchedEffect(Unit) {
        scope.launch {
            isScanning = true
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val operitDir = File(downloadDir, "Operit")
                
                if (operitDir.exists() && operitDir.isDirectory) {
                    val files = operitDir.listFiles() ?: emptyArray()
                    
                    chatBackupFileCount = files.count { file ->
                        file.name.startsWith("chat_backup_") && file.extension == "json" ||
                        file.name.startsWith("chat_export_") && file.extension in listOf("json", "md", "html", "txt")
                    }
                    
                    memoryBackupFileCount = files.count { file ->
                        file.name.startsWith("memory_backup_") && file.extension == "json"
                    }
                    
                    modelConfigBackupFileCount = files.count { file ->
                        file.name.startsWith("model_config_backup_") && file.extension == "json"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanning = false
            }
        }
    }

    val chatFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // 保存URI，显示格式选择对话框
                    pendingImportUri = uri
                    showImportFormatDialog = true
                }
            }
        }

    val memoryFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingMemoryImportUri = uri
                    showImportProfileDialog = true
                }
            }
        }
    
    val modelConfigFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    scope.launch {
                        modelConfigOperationState = ModelConfigOperation.IMPORTING
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val jsonContent = inputStream?.bufferedReader()?.use { it.readText() }
                            if (jsonContent != null) {
                                val (newCount, updatedCount, skippedCount) = 
                                    modelConfigManager.importConfigs(jsonContent)
                                modelConfigOperationState = ModelConfigOperation.IMPORTED
                                modelConfigOperationMessage = "成功导入模型配置：\n" +
                                    "- 新增配置：${newCount}个\n" +
                                    "- 更新配置：${updatedCount}个" +
                                    (if (skippedCount > 0) "\n- 跳过无效配置：${skippedCount}个" else "")
                            } else {
                                modelConfigOperationState = ModelConfigOperation.FAILED
                                modelConfigOperationMessage = "导入失败：无法读取文件"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            modelConfigOperationState = ModelConfigOperation.FAILED
                            modelConfigOperationMessage = "导入失败：${e.localizedMessage ?: e.toString()}"
                        }
                    }
                }
            }
        }

    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name ?: "默认配置"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OverviewCard(
                totalChatCount = totalChatCount,
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                activeProfileName = activeProfileName
            )
        }
        item {
            BackupFilesStatisticsCard(
                chatBackupCount = chatBackupFileCount,
                memoryBackupCount = memoryBackupFileCount,
                modelConfigBackupCount = modelConfigBackupFileCount,
                isScanning = isScanning,
                onRefresh = {
                    scope.launch {
                        isScanning = true
                        try {
                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val operitDir = File(downloadDir, "Operit")
                            
                            if (operitDir.exists() && operitDir.isDirectory) {
                                val files = operitDir.listFiles() ?: emptyArray()
                                
                                chatBackupFileCount = files.count { file ->
                                    file.name.startsWith("chat_backup_") && file.extension == "json" ||
                                    file.name.startsWith("chat_export_") && file.extension in listOf("json", "md", "html", "txt")
                                }
                                
                                memoryBackupFileCount = files.count { file ->
                                    file.name.startsWith("memory_backup_") && file.extension == "json"
                                }
                                
                                modelConfigBackupFileCount = files.count { file ->
                                    file.name.startsWith("model_config_backup_") && file.extension == "json"
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isScanning = false
                        }
                    }
                }
            )
        }
        item {
            DataManagementCard(
                totalChatCount = totalChatCount,
                operationState = operationState,
                operationMessage = operationMessage,
                onExport = {
                    // 显示格式选择对话框
                    showExportFormatDialog = true
                },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"  // 接受所有类型
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/json",
                            "text/markdown",
                            "text/plain",
                            "text/csv"
                        ))
                    }
                    chatFilePickerLauncher.launch(intent)
                },
                onDelete = { showDeleteConfirmDialog = true }
            )
        }
        item {
            MemoryManagementCard(
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                operationState = memoryOperationState,
                operationMessage = memoryOperationMessage,
                onExport = { showExportProfileDialog = true },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    memoryFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            ModelConfigManagementCard(
                totalConfigCount = totalModelConfigCount,
                operationState = modelConfigOperationState,
                operationMessage = modelConfigOperationMessage,
                onExport = {
                    scope.launch {
                        modelConfigOperationState = ModelConfigOperation.EXPORTING
                        try {
                            val jsonContent = modelConfigManager.exportAllConfigs()
                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val exportDir = File(downloadDir, "Operit")
                            if (!exportDir.exists()) {
                                exportDir.mkdirs()
                            }
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                            val timestamp = dateFormat.format(Date())
                            val exportFile = File(exportDir, "model_config_backup_$timestamp.json")
                            exportFile.writeText(jsonContent)
                            
                            // 导出成功，显示安全警告对话框
                            exportedModelConfigPath = exportFile.absolutePath
                            showModelConfigExportWarning = true
                            modelConfigOperationState = ModelConfigOperation.EXPORTED
                        } catch (e: Exception) {
                            e.printStackTrace()
                            modelConfigOperationState = ModelConfigOperation.FAILED
                            modelConfigOperationMessage = "导出失败：${e.localizedMessage ?: e.toString()}"
                        }
                    }
                },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    modelConfigFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            FaqCard()
        }
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                scope.launch {
                    operationState = ChatHistoryOperation.DELETING
                    try {
                        val result = deleteAllChatHistories(context)
                        operationState = ChatHistoryOperation.DELETED
                        operationMessage =
                            "成功清除 ${result.deletedCount} 条聊天记录" +
                            (if (result.skippedLockedCount > 0) "\n已跳过 ${result.skippedLockedCount} 条锁定聊天" else "")
                    } catch (e: Exception) {
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = "清除失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            }
        )
    }

    if (showMemoryImportStrategyDialog) {
        MemoryImportStrategyDialog(
            onDismiss = {
                showMemoryImportStrategyDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = { strategy ->
                showMemoryImportStrategyDialog = false
                val uri = pendingMemoryImportUri
                pendingMemoryImportUri = null

                if (uri != null) {
                    scope.launch {
                        memoryOperationState = MemoryOperation.IMPORTING
                        try {
                            val importRepo = MemoryRepository(context, selectedImportProfileId)
                            val result = importMemoriesFromUri(context, importRepo, uri, strategy)
                            memoryOperationState = MemoryOperation.IMPORTED
                            val profileName = allProfiles.find { it.id == selectedImportProfileId }?.name
                                ?: selectedImportProfileId
                            memoryOperationMessage = "导入到配置「$profileName」成功：\n" +
                                "- 新增记忆：${result.newMemories}条\n" +
                                "- 更新记忆：${result.updatedMemories}条\n" +
                                "- 跳过记忆：${result.skippedMemories}条\n" +
                                "- 新增链接：${result.newLinks}个"

                            if (selectedImportProfileId == activeProfileId) {
                                val repo = memoryRepo
                                if (repo != null) {
                                    val memories = repo.searchMemories("")
                                    totalMemoryCount = memories.count { !it.isDocumentNode }
                                    val graph = repo.getMemoryGraph()
                                    totalMemoryLinkCount = graph.edges.size
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            memoryOperationState = MemoryOperation.FAILED
                            memoryOperationMessage = "导入失败：${e.localizedMessage ?: e.toString()}"
                        }
                    }
                }
            }
        )
    }

    if (showExportProfileDialog) {
        ProfileSelectionDialog(
            title = "选择要导出的配置",
            profiles = allProfiles,
            selectedProfileId = selectedExportProfileId,
            onProfileSelected = { selectedExportProfileId = it },
            onDismiss = { showExportProfileDialog = false },
            onConfirm = {
                showExportProfileDialog = false
                scope.launch {
                    memoryOperationState = MemoryOperation.EXPORTING
                    try {
                        val exportRepo = MemoryRepository(context, selectedExportProfileId)
                        val filePath = exportMemories(context, exportRepo)
                        if (filePath != null) {
                            memoryOperationState = MemoryOperation.EXPORTED
                            val profileName = allProfiles.find { it.id == selectedExportProfileId }?.name
                                ?: selectedExportProfileId
                            val memories = exportRepo.searchMemories("")
                            val memoryCount = memories.count { !it.isDocumentNode }
                            val graph = exportRepo.getMemoryGraph()
                            val linkCount = graph.edges.size
                            memoryOperationMessage =
                                "成功从配置「$profileName」导出 $memoryCount 条记忆和 $linkCount 个链接到：\n$filePath"
                        } else {
                            memoryOperationState = MemoryOperation.FAILED
                            memoryOperationMessage = "导出失败：无法创建文件"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        memoryOperationState = MemoryOperation.FAILED
                        memoryOperationMessage = "导出失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            }
        )
    }

    if (showImportProfileDialog) {
        ProfileSelectionDialog(
            title = "选择要导入到的配置",
            profiles = allProfiles,
            selectedProfileId = selectedImportProfileId,
            onProfileSelected = { selectedImportProfileId = it },
            onDismiss = {
                showImportProfileDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = {
                showImportProfileDialog = false
                showMemoryImportStrategyDialog = true
            }
        )
    }
    
    if (showExportFormatDialog) {
        ExportFormatDialog(
            selectedFormat = selectedExportFormat,
            onFormatSelected = { selectedExportFormat = it },
            onDismiss = { showExportFormatDialog = false },
            onConfirm = {
                showExportFormatDialog = false
                scope.launch {
                    operationState = ChatHistoryOperation.EXPORTING
                    try {
                        val filePath = chatHistoryManager.exportChatHistoriesToDownloads(selectedExportFormat)
                        if (filePath != null) {
                            operationState = ChatHistoryOperation.EXPORTED
                            val chatCount = chatHistoryManager.chatHistoriesFlow.first().size
                            val formatName = when (selectedExportFormat) {
                                ExportFormat.JSON -> "JSON"
                                ExportFormat.MARKDOWN -> "Markdown"
                                ExportFormat.HTML -> "HTML"
                                ExportFormat.TXT -> "文本"
                                ExportFormat.CSV -> "CSV"
                            }
                            operationMessage = "成功导出 $chatCount 条聊天记录为 $formatName 格式到：\n$filePath"
                        } else {
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage = "导出失败：无法创建文件"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = "导出失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            }
        )
    }
    
    if (showImportFormatDialog) {
        ImportFormatDialog(
            selectedFormat = selectedImportFormat,
            onFormatSelected = { selectedImportFormat = it },
            onDismiss = {
                showImportFormatDialog = false
                pendingImportUri = null
            },
            onConfirm = {
                showImportFormatDialog = false
                pendingImportUri?.let { uri ->
                    scope.launch {
                        operationState = ChatHistoryOperation.IMPORTING
                        try {
                            val importResult = chatHistoryManager.importChatHistoriesFromUri(uri, selectedImportFormat)
                            operationMessage = if (importResult.total > 0) {
                                operationState = ChatHistoryOperation.IMPORTED
                                val formatName = when (selectedImportFormat) {
                                    ChatFormat.OPERIT -> "Operit JSON"
                                    ChatFormat.CHATGPT -> "ChatGPT"
                                    ChatFormat.CHATBOX -> "ChatBox"
                                    ChatFormat.MARKDOWN -> "Markdown"
                                    ChatFormat.GENERIC_JSON -> "通用 JSON"
                                    ChatFormat.CLAUDE -> "Claude"
                                    else -> "未知格式"
                                }
                                "成功导入 $formatName 格式：\n" +
                                    "- 新增记录：${importResult.new}条\n" +
                                    "- 更新记录：${importResult.updated}条\n" +
                                    (if (importResult.skipped > 0) "- 跳过无效记录：${importResult.skipped}条" else "")
                            } else {
                                operationState = ChatHistoryOperation.FAILED
                                "导入失败：未找到有效的聊天记录"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage = "导入失败：${e.localizedMessage ?: e.toString()}"
                        } finally {
                            pendingImportUri = null
                        }
                    }
                }
            }
        )
    }
    
    // 模型配置导出安全警告对话框
    if (showModelConfigExportWarning) {
        ModelConfigExportWarningDialog(
            exportPath = exportedModelConfigPath,
            onDismiss = {
                showModelConfigExportWarning = false
                modelConfigOperationMessage = "成功导出到：$exportedModelConfigPath"
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewCard(
    totalChatCount: Int,
    totalMemoryCount: Int,
    totalLinkCount: Int,
    activeProfileName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.backup_data_overview),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.backup_current_profile, activeProfileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(
                    icon = Icons.Default.History,
                    title = "$totalChatCount",
                    subtitle = stringResource(R.string.backup_chat_count)
                )
                StatChip(
                    icon = Icons.Default.Psychology,
                    title = "$totalMemoryCount",
                    subtitle = stringResource(R.string.backup_memory_count)
                )
                StatChip(
                    icon = Icons.Default.Link,
                    title = "$totalLinkCount",
                    subtitle = stringResource(R.string.backup_memory_link_count)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupFilesStatisticsCard(
    chatBackupCount: Int,
    memoryBackupCount: Int,
    modelConfigBackupCount: Int,
    isScanning: Boolean,
    onRefresh: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📁 " + stringResource(R.string.backup_files_statistics),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.backup_files_statistics_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    FilledTonalButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.backup_refresh),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BackupFileStatItem(
                    icon = Icons.Default.History,
                    count = chatBackupCount,
                    label = stringResource(R.string.backup_chat_files),
                    color = MaterialTheme.colorScheme.primary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Psychology,
                    count = memoryBackupCount,
                    label = stringResource(R.string.backup_memory_files),
                    color = MaterialTheme.colorScheme.secondary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Settings,
                    count = modelConfigBackupCount,
                    label = stringResource(R.string.backup_model_config_files),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            
            if (!isScanning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "💡 " + stringResource(R.string.backup_files_location_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupFileStatItem(
    icon: ImageVector,
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataManagementCard(
    totalChatCount: Int,
    operationState: ChatHistoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "聊天记录",
                subtitle = "备份、恢复或清空历史记录",
                icon = Icons.Default.History
            )

            Text(
                text = "当前共有 $totalChatCount 条聊天记录。导出的文件会保存在「下载/Operit」文件夹中。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = "导出",
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "导入",
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "清除所有记录",
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    isDestructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = operationState != ChatHistoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ChatHistoryOperation.EXPORTING -> OperationProgressView(message = "正在导出聊天记录...")
                        ChatHistoryOperation.IMPORTING -> OperationProgressView(message = "正在导入聊天记录...")
                        ChatHistoryOperation.DELETING -> OperationProgressView(message = "正在删除聊天记录...")
                        ChatHistoryOperation.EXPORTED -> OperationResultCard(
                            title = "导出成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )
                        ChatHistoryOperation.IMPORTED -> OperationResultCard(
                            title = "导入成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )
                        ChatHistoryOperation.DELETED -> OperationResultCard(
                            title = "删除成功",
                            message = operationMessage,
                            icon = Icons.Default.Delete
                        )
                        ChatHistoryOperation.FAILED -> OperationResultCard(
                            title = "操作失败",
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val colors = if (isDestructive) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryManagementCard(
    totalMemoryCount: Int,
    totalLinkCount: Int,
    operationState: MemoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "记忆库",
                subtitle = "跨配置备份与恢复，保持思维链一致",
                icon = Icons.Default.Psychology
            )

            Text(
                text = "当前共有 $totalMemoryCount 条记忆和 $totalLinkCount 个链接。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = "导出",
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "导入",
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != MemoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        MemoryOperation.EXPORTING -> OperationProgressView(message = "正在导出记忆库...")
                        MemoryOperation.IMPORTING -> OperationProgressView(message = "正在导入记忆库...")
                        MemoryOperation.EXPORTED -> OperationResultCard(
                            title = "导出成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )
                        MemoryOperation.IMPORTED -> OperationResultCard(
                            title = "导入成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )
                        MemoryOperation.FAILED -> OperationResultCard(
                            title = "操作失败",
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelConfigManagementCard(
    totalConfigCount: Int,
    operationState: ModelConfigOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = stringResource(R.string.backup_model_config),
                subtitle = stringResource(R.string.backup_model_config_subtitle),
                icon = Icons.Default.Settings
            )

            Text(
                text = stringResource(R.string.backup_model_config_current_count, totalConfigCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_import),
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != ModelConfigOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ModelConfigOperation.EXPORTING -> OperationProgressView(message = stringResource(R.string.backup_exporting, stringResource(R.string.backup_model_config)))
                        ModelConfigOperation.IMPORTING -> OperationProgressView(message = stringResource(R.string.backup_importing, stringResource(R.string.backup_model_config)))
                        ModelConfigOperation.EXPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_export_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )
                        ModelConfigOperation.IMPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_import_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )
                        ModelConfigOperation.FAILED -> OperationResultCard(
                            title = stringResource(R.string.backup_operation_failed),
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "常见问题",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "了解备份与导入时的注意事项，避免常见误区。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            FaqItem(
                question = "为什么要备份数据？",
                answer = "备份聊天记录可以防止应用卸载或数据丢失时，您的重要内容丢失。定期备份是个好习惯！"
            )
            FaqItem(
                question = "导出的文件保存在哪里？",
                answer = "导出的备份文件会保存在您手机的「下载/Operit」文件夹中，文件名包含导出的数据类型、日期和时间。"
            )
            FaqItem(
                question = "导入后会出现重复的数据吗？",
                answer = "系统会根据记录ID判断，相同ID的记录会被更新而不是重复导入。不同ID的记录会作为新记录添加。"
            )
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认清除聊天记录") },
        text = { Text("您确定要清除所有聊天记录吗？此操作无法撤销，建议先备份数据。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("确认清除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OperationResultCard(
    title: String,
    message: String,
    icon: ImageVector,
    isError: Boolean = false
) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun OperationProgressView(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private data class DeleteAllChatsResult(
    val deletedCount: Int,
    val skippedLockedCount: Int
)

private suspend fun deleteAllChatHistories(context: Context): DeleteAllChatsResult =
    withContext(Dispatchers.IO) {
        try {
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            var deletedCount = 0
            var skippedLockedCount = 0

            for (chatHistory in chatHistories) {
                val deleted = chatHistoryManager.deleteChatHistory(chatHistory.id)
                if (deleted) {
                    deletedCount++
                } else {
                    skippedLockedCount++
                }
            }

            return@withContext DeleteAllChatsResult(
                deletedCount = deletedCount,
                skippedLockedCount = skippedLockedCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

@Composable
private fun MemoryImportStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (ImportStrategy) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.SKIP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入策略") },
        text = {
            Column {
                Text(
                    text = "遇到重复的记忆（UUID相同）时如何处理？",
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyOption(
                        title = "跳过（推荐）",
                        description = "保留现有记忆，不导入重复数据",
                        selected = selectedStrategy == ImportStrategy.SKIP,
                        onClick = { selectedStrategy = ImportStrategy.SKIP }
                    )

                    StrategyOption(
                        title = "更新",
                        description = "用导入的数据更新现有记忆",
                        selected = selectedStrategy == ImportStrategy.UPDATE,
                        onClick = { selectedStrategy = ImportStrategy.UPDATE }
                    )

                    StrategyOption(
                        title = "创建新记录",
                        description = "即使UUID相同也创建新记忆（可能导致重复）",
                        selected = selectedStrategy == ImportStrategy.CREATE_NEW,
                        onClick = { selectedStrategy = ImportStrategy.CREATE_NEW }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy) }) {
                Text("开始导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend fun exportMemories(_context: Context, memoryRepository: MemoryRepository): String? =
    withContext(Dispatchers.IO) {
        try {
            val jsonString = memoryRepository.exportMemoriesToJson()

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadDir, "Operit")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val exportFile = File(exportDir, "memory_backup_$timestamp.json")

            exportFile.writeText(jsonString)

            return@withContext exportFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

private suspend fun importMemoriesFromUri(
    context: Context,
    memoryRepository: MemoryRepository,
    uri: Uri,
    strategy: ImportStrategy
) = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw Exception("无法打开文件")
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    inputStream.close()

    if (jsonString.isBlank()) {
        throw Exception("导入的文件为空")
    }

    memoryRepository.importMemoriesFromJson(jsonString, strategy)
}

@Composable
private fun ProfileSelectionDialog(
    title: String,
    profiles: List<PreferenceProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                profiles.forEach { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onProfileSelected(profile.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProfileId == profile.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedProfileId == profile.id)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { onProfileSelected(profile.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedProfileId == profile.id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ExportFormatDialog(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导出格式") },
        text = {
            Column {
                Text(
                    text = "请选择导出聊天记录的文件格式：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                FormatOption(
                    format = ExportFormat.JSON,
                    title = "JSON",
                    description = "标准格式，支持完整数据结构（推荐）",
                    selected = selectedFormat == ExportFormat.JSON,
                    onClick = { onFormatSelected(ExportFormat.JSON) }
                )
                
                FormatOption(
                    format = ExportFormat.MARKDOWN,
                    title = "Markdown",
                    description = "纯文本格式，易于阅读和编辑",
                    selected = selectedFormat == ExportFormat.MARKDOWN,
                    onClick = { onFormatSelected(ExportFormat.MARKDOWN) }
                )
                
                FormatOption(
                    format = ExportFormat.HTML,
                    title = "HTML",
                    description = "网页格式，在浏览器中查看",
                    selected = selectedFormat == ExportFormat.HTML,
                    onClick = { onFormatSelected(ExportFormat.HTML) }
                )
                
                FormatOption(
                    format = ExportFormat.TXT,
                    title = "纯文本",
                    description = "简单文本格式，通用性最强",
                    selected = selectedFormat == ExportFormat.TXT,
                    onClick = { onFormatSelected(ExportFormat.TXT) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportFormatDialog(
    selectedFormat: ChatFormat,
    onFormatSelected: (ChatFormat) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入格式") },
        text = {
            Column {
                Text(
                    text = "请选择要导入的聊天记录格式：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                ImportFormatOption(
                    format = ChatFormat.OPERIT,
                    title = "Operit JSON（推荐）",
                    description = "本应用的原生格式，完整保留所有数据",
                    selected = selectedFormat == ChatFormat.OPERIT,
                    onClick = { onFormatSelected(ChatFormat.OPERIT) }
                )
                
                ImportFormatOption(
                    format = ChatFormat.CHATGPT,
                    title = "ChatGPT",
                    description = "OpenAI ChatGPT conversations.json 导出格式",
                    selected = selectedFormat == ChatFormat.CHATGPT,
                    onClick = { onFormatSelected(ChatFormat.CHATGPT) }
                )
                
                ImportFormatOption(
                    format = ChatFormat.CHATBOX,
                    title = "ChatBox",
                    description = "ChatBox 桌面应用导出格式",
                    selected = selectedFormat == ChatFormat.CHATBOX,
                    onClick = { onFormatSelected(ChatFormat.CHATBOX) }
                )
                
                ImportFormatOption(
                    format = ChatFormat.MARKDOWN,
                    title = "Markdown",
                    description = "Markdown 格式的聊天记录文件",
                    selected = selectedFormat == ChatFormat.MARKDOWN,
                    onClick = { onFormatSelected(ChatFormat.MARKDOWN) }
                )
                
                ImportFormatOption(
                    format = ChatFormat.GENERIC_JSON,
                    title = "通用 JSON",
                    description = "标准 role-content 结构的 JSON（支持 Claude、LibreChat 等）",
                    selected = selectedFormat == ChatFormat.GENERIC_JSON,
                    onClick = { onFormatSelected(ChatFormat.GENERIC_JSON) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportFormatOption(
    format: ChatFormat,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FormatOption(
    format: ExportFormat,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelConfigExportWarningDialog(
    exportPath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "⚠️ " + stringResource(R.string.backup_model_config_warning_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.backup_model_config_warning_contains),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SecurityWarningItem("🔑 " + stringResource(R.string.backup_model_config_warning_api_key))
                    SecurityWarningItem("🌐 " + stringResource(R.string.backup_model_config_warning_api_endpoint))
                    SecurityWarningItem("⚙️ " + stringResource(R.string.backup_model_config_warning_model_params))
                    SecurityWarningItem("🔧 " + stringResource(R.string.backup_model_config_warning_custom_params))
                }
                
                Spacer(modifier = Modifier.size(8.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📋 " + stringResource(R.string.backup_model_config_warning_security_tips),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.backup_model_config_warning_tips),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.size(4.dp))
                
                Text(
                    text = stringResource(R.string.backup_model_config_warning_export_path),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = exportPath,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.backup_model_config_warning_confirm))
            }
        }
    )
}

@Composable
private fun SecurityWarningItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

