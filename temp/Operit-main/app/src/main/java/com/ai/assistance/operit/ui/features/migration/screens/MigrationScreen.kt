package com.ai.assistance.operit.ui.features.migration.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.migration.ChatHistoryMigrationManager
import java.io.File
import kotlinx.coroutines.launch

/** 数据迁移状态 */
sealed class MigrationState {
    object Initial : MigrationState()
    object InProgress : MigrationState()
    data class Completed(val count: Int) : MigrationState()
    data class Failed(val error: String) : MigrationState()
    object Exporting : MigrationState()
    data class Exported(val path: String) : MigrationState()
    object Importing : MigrationState()
    data class Imported(val count: Int) : MigrationState()
}

/**
 * 数据迁移界面
 * @param migrationManager 数据迁移管理器
 * @param onComplete 迁移完成回调
 */
@Composable
fun MigrationScreen(migrationManager: ChatHistoryMigrationManager, onComplete: () -> Unit) {
    var migrationState by remember { mutableStateOf<MigrationState>(MigrationState.Initial) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 文件选择器
    val filePickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        // 复制文件到应用私有目录
                        scope.launch {
                            migrationState = MigrationState.Importing
                            try {
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val tempFile = File(context.cacheDir, "import_temp.json")
                                inputStream?.use { input ->
                                    tempFile.outputStream().use { output -> input.copyTo(output) }
                                }

                                // 导入文件
                                val importCount =
                                        migrationManager.importChatHistoryFromBackup(
                                                tempFile.absolutePath
                                        )
                                migrationState =
                                        if (importCount >= 0) {
                                            MigrationState.Imported(importCount)
                                        } else {
                                            MigrationState.Failed("导入失败")
                                        }
                            } catch (e: Exception) {
                                migrationState = MigrationState.Failed("导入过程中出错: ${e.message}")
                            }
                        }
                    }
                }
            }

    // 打开文件选择器
    fun openFilePicker() {
        val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
        filePickerLauncher.launch(intent)
    }

    // 打开导出的文件
    fun openExportedFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/json")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                when (val state = migrationState) {
                    MigrationState.Initial -> {
                        Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "迁移信息",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "发现旧版聊天记录",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "检测到之前版本的聊天记录数据，您可以将这些聊天记录导入到新版本中。",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                                onClick = {
                                    migrationState = MigrationState.InProgress
                                    scope.launch {
                                        val result = migrationManager.migrateData()
                                        migrationState =
                                                if (result >= 0) {
                                                    MigrationState.Completed(result)
                                                } else {
                                                    MigrationState.Failed("迁移过程中出现错误")
                                                }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        ) { Text("开始迁移") }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { onComplete() }, modifier = Modifier.fillMaxWidth()) {
                            Text("跳过")
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "高级选项",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                Text(
                                        text = "您也可以手动备份或导入聊天记录：",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                )

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    migrationState = MigrationState.Exporting
                                                    val exportPath =
                                                            migrationManager.exportOldChatHistory()
                                                    migrationState =
                                                            if (exportPath != null) {
                                                                MigrationState.Exported(exportPath)
                                                            } else {
                                                                MigrationState.Failed("导出失败")
                                                            }
                                                }
                                            }
                                    ) {
                                        Icon(
                                                imageVector = Icons.Filled.CloudDownload,
                                                contentDescription = "导出",
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("导出备份")
                                    }

                                    OutlinedButton(onClick = { openFilePicker() }) {
                                        Icon(
                                                imageVector = Icons.Filled.CloudUpload,
                                                contentDescription = "导入",
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("导入备份")
                                    }
                                }
                            }
                        }
                    }
                    MigrationState.InProgress -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "正在迁移数据...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "请耐心等待，迁移过程中请勿关闭应用",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )
                    }
                    is MigrationState.Completed -> {
                        Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "迁移完成",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "迁移完成",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "成功迁移 ${state.count} 条聊天记录",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = { onComplete() }, modifier = Modifier.fillMaxWidth()) {
                            Text("继续")
                        }

                        LaunchedEffect(true) {
                            if (state.count == 0) {
                                // 如果没有记录需要迁移，自动继续
                                onComplete()
                            }
                        }
                    }
                    is MigrationState.Failed -> {
                        Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "迁移失败",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "迁移失败",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                                onClick = { migrationState = MigrationState.Initial },
                                modifier = Modifier.fillMaxWidth()
                        ) { Text("重试") }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { onComplete() }, modifier = Modifier.fillMaxWidth()) {
                            Text("跳过")
                        }
                    }
                    MigrationState.Exporting -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "正在导出备份...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )
                    }
                    is MigrationState.Exported -> {
                        Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = "导出完成",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "导出完成",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "备份文件已保存到:\n${state.path}",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                                onClick = { migrationState = MigrationState.Initial },
                                modifier = Modifier.fillMaxWidth()
                        ) { Text("返回") }
                    }
                    MigrationState.Importing -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "正在导入备份...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )
                    }
                    is MigrationState.Imported -> {
                        Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = "导入完成",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "导入完成",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "成功导入 ${state.count} 条聊天记录",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = { onComplete() }, modifier = Modifier.fillMaxWidth()) {
                            Text("继续")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(onClick = { migrationState = MigrationState.Initial }) {
                            Text("返回迁移界面")
                        }
                    }
                }
            }
        }
    }
}
