package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (GitHubIssue) -> Unit,
    onNavigateToPublish: () -> Unit,
    viewModel: MCPMarketViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val githubAuth = GitHubAuthPreferences.getInstance(context)

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val userPublishedPlugins by viewModel.userPublishedPlugins.collectAsState()
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)

    // 对话框状态
    var showDeleteDialog by remember { mutableStateOf<GitHubIssue?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            viewModel.loadUserPublishedPlugins()
        } else {
            showLoginDialog = true
        }
    }

    CustomScaffold(
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(
                    onClick = onNavigateToPublish,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.publish_new_plugin)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            // 未登录提示
            if (!isLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.please_login_github_first),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.need_login_github_manage_plugins),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.initiateGitHubLogin(context) }
                        ) {
                            Text(stringResource(R.string.login_github))
                        }
                    }
                }
            }

            // 错误信息
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 加载状态
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.loading_your_plugins))
                    }
                }
            } else if (isLoggedIn) {
                // 插件列表
                if (userPublishedPlugins.isEmpty()) {
                    // 空状态
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_published_plugins_yet),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.click_button_publish_first_mcp_plugin),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(userPublishedPlugins) { plugin ->
                            val pluginInfo = remember(plugin) {
                                MCPPluginParser.parsePluginInfo(plugin)
                            }
                            PluginManageCard(
                                plugin = plugin,
                                pluginInfo = pluginInfo,
                                onEdit = { onNavigateToEdit(plugin) },
                                onDelete = { showDeleteDialog = plugin },
                                onReopen = {
                                    scope.launch {
                                        viewModel.reopenPublishedPlugin(plugin.number, plugin.title)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 删除确认对话框
        showDeleteDialog?.let { plugin ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = {
                    Text(stringResource(R.string.confirm_remove_plugin_from_market, plugin.title))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.deletePublishedPlugin(plugin.number, plugin.title)
                                showDeleteDialog = null
                            }
                        }
                    ) {
                        Text(stringResource(R.string.confirm_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManageCard(
    plugin: GitHubIssue,
    pluginInfo: MCPPluginParser.ParsedPluginInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReopen: () -> Unit
) {
    val isOpen = plugin.state == "open"
    val statusColor = if (isOpen) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = plugin.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isOpen) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOpen) stringResource(R.string.published) else stringResource(R.string.removed),
                            color = statusColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Issue 编号
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "#${plugin.number}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 描述
            if (pluginInfo.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluginInfo.description.take(150) + if (pluginInfo.description.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 标签
            if (plugin.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    plugin.labels.take(3).forEach { label ->
                        Surface(
                            color = Color(android.graphics.Color.parseColor("#${label.color}")).copy(
                                alpha = 0.2f
                            ),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = label.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 操作按钮
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑按钮
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }

                if (isOpen) {
                    // 移除按钮
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.remove))
                    }
                } else {
                    // 重新发布按钮
                    Button(
                        onClick = onReopen,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.republish))
                    }
                }
            }
        }
    }
}