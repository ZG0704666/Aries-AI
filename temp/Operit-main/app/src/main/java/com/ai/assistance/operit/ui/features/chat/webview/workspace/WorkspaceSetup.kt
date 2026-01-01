package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.createAndGetDefaultWorkspace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * VSCode风格的工作区设置组件
 * 用于初始绑定工作区
 */
@Composable
fun WorkspaceSetup(chatId: String, onBindWorkspace: (String) -> Unit) {
    val context = LocalContext.current
    var showFileBrowser by remember { mutableStateOf(false) }
    var showProjectTypeDialog by remember { mutableStateOf(false) }

    if (showFileBrowser) {
        FileBrowser(
            initialPath = context.filesDir.absolutePath, // 默认应用内部目录
            onBindWorkspace = onBindWorkspace,
            onCancel = { showFileBrowser = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 移除点击时的涟漪效果
                    enabled = true,
                    onClick = {}
                ) // 添加点击拦截
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProjectTypeDialog) {
                AlertDialog(
                    onDismissRequest = { showProjectTypeDialog = false },
                    title = {
                        Text(
                            text = "选择项目类型",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "请选择要创建的默认工作区类型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ProjectTypeCard(
                                icon = Icons.Default.CreateNewFolder,
                                title = "空白工作区",
                                description = "仅创建一个空的工作区目录，不包含任何模板文件",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "blank")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Office 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Description,
                                title = "办公文档",
                                description = "用于文档编辑、文件处理和通用办公任务",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "office")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Web 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Language,
                                title = "Web 项目",
                                description = "适用于网页开发，支持 HTML/CSS/JavaScript，自动启动本地服务器",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId)
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Node.js 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Terminal,
                                title = "Node.js 项目",
                                description = "适用于 Node.js 后端开发，提供 npm 命令快捷按钮",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "node")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // TypeScript 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Code,
                                title = "TypeScript 项目",
                                description = "TypeScript + pnpm，支持类型安全开发和 tsc watch 实时编译",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "typescript")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Python 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Code,
                                title = "Python 项目",
                                description = "适用于 Python 开发，支持 pip 和 HTTP 服务器",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "python")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Java 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Settings,
                                title = "Java 项目",
                                description = "适用于 Java 开发，支持 Gradle 和 Maven 构建",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "java")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Go 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Build,
                                title = "Go 项目",
                                description = "适用于 Go 开发，提供 go mod 和 build 命令",
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "go")
                                    onBindWorkspace(workspaceDir.absolutePath)
                                    showProjectTypeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showProjectTypeDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // VSCode风格的图标
            Icon(
                imageVector = Icons.Default.Widgets, // 使用更通用的图标
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = context.getString(R.string.setup_workspace),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = context.getString(R.string.workspace_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // VSCode风格的选项卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceOption(
                    icon = Icons.Default.CreateNewFolder,
                    title = context.getString(R.string.create_default_workspace),
                    description = context.getString(R.string.create_new_workspace_in_app),
                    onClick = {
                        showProjectTypeDialog = true
                    }
                )
                
                WorkspaceOption(
                    icon = Icons.Default.FolderOpen,
                    title = context.getString(R.string.select_existing_workspace),
                    description = context.getString(R.string.select_folder_from_device),
                    onClick = { showFileBrowser = true }
                )
            }
        }
    }
}

/**
 * 项目类型卡片组件（IDE风格）
 */
@Composable
fun ProjectTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 文字内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 箭头指示
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 工作区选项卡组件
 */
@Composable
fun WorkspaceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp) // 调整大小
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp)) // 更圆的角
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp, // 移除阴影
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 