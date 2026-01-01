package com.ai.assistance.operit.ui.features.packages.screens.mcp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer.DeploymentStatus

/** MCP插件部署进度对话框 - 简约风格 */
@Composable
fun MCPDeployProgressDialog(
        deploymentStatus: DeploymentStatus,
        onDismissRequest: () -> Unit,
        onRetry: (() -> Unit)? = null,
        pluginName: String,
        outputMessages: List<String> = emptyList(),
        environmentVariables: Map<String, String> = emptyMap(),
        onEnvironmentVariablesChange: ((Map<String, String>) -> Unit)? = null
) {
    var showEnvVarsDialog by remember { mutableStateOf(false) }

    Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                    DialogProperties(
                            dismissOnBackPress = deploymentStatus !is DeploymentStatus.InProgress,
                            dismissOnClickOutside = deploymentStatus !is DeploymentStatus.InProgress
                    )
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // 标题区域
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = if (deploymentStatus is DeploymentStatus.Success) "部署成功" 
                                  else if (deploymentStatus is DeploymentStatus.Error) "部署失败" 
                                  else "正在部署",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 环境变量按钮
                    if (onEnvironmentVariablesChange != null) {
                        IconButton(
                                onClick = { showEnvVarsDialog = true },
                                modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "环境变量",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 非进行中状态才显示关闭按钮
                    if (deploymentStatus !is DeploymentStatus.InProgress) {
                        IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 插件名称
                Text(
                        text = pluginName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 状态显示
                when (deploymentStatus) {
                    is DeploymentStatus.NotStarted, is DeploymentStatus.InProgress -> {
                        LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                                text = if (deploymentStatus is DeploymentStatus.InProgress) 
                                       deploymentStatus.message else "准备部署...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is DeploymentStatus.Success -> {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = "部署完成",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = deploymentStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DeploymentStatus.Error -> {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = "部署失败",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = deploymentStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 输出日志区域
                if (outputMessages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                                text = "部署日志",
                                style = MaterialTheme.typography.titleSmall
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // 显示日志行数
                        Text(
                                text = "${outputMessages.size} 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Column(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState())
                        ) {
                            outputMessages.forEach { message ->
                                Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 底部按钮
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                ) {
                    if (deploymentStatus is DeploymentStatus.Error && onRetry != null) {
                        OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.padding(end = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                )
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试")
                        }
                    }

                    if (deploymentStatus !is DeploymentStatus.InProgress) {
                        Button(
                                onClick = onDismissRequest,
                                colors = ButtonDefaults.buttonColors(
                                        containerColor = if (deploymentStatus is DeploymentStatus.Success)
                                                MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (deploymentStatus is DeploymentStatus.Success)
                                                MaterialTheme.colorScheme.onPrimary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        ) { 
                            Text("关闭") 
                        }
                    }
                }

                // 环境变量对话框
                if (showEnvVarsDialog && onEnvironmentVariablesChange != null) {
                    MCPEnvironmentVariablesDialog(
                            environmentVariables = environmentVariables,
                            onDismiss = { showEnvVarsDialog = false },
                            onConfirm = { newEnvVars ->
                                onEnvironmentVariablesChange(newEnvVars)
                                showEnvVarsDialog = false
                            }
                    )
                }
            }
        }
    }
}
