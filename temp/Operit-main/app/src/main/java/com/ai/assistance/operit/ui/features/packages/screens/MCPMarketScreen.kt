package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPMarketScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onNavigateToManage: () -> Unit = {},
    onNavigateToDetail: ((GitHubIssue) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context.applicationContext) }
    val viewModel: MCPMarketViewModel = viewModel(
        factory = MCPMarketViewModel.Factory(context.applicationContext, mcpRepository)
    )

    // GitHub认证状态
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)

    // 市场数据状态
    val mcpIssues by viewModel.mcpIssues.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // 安装状态
    val installingPlugins by viewModel.installingPlugins.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installedPluginIds by viewModel.installedPluginIds.collectAsState()

    // 搜索状态
    val searchQuery by viewModel.searchQuery.collectAsState()

    // UI状态
    var selectedTab by remember { mutableStateOf(0) }
    var showLoginDialog by remember { mutableStateOf(false) }

    // 在组件启动时加载数据
    LaunchedEffect(Unit) {
        viewModel.loadMCPMarketData()
    }

    // 错误处理
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 标签栏区域，包含登录状态
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                // 登录状态栏（如果未登录则显示）
                if (!isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLoginDialog = true }
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.login_github_to_manage_mcp_plugins),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 标签栏
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.browse)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.my_tab))
                                if (isLoggedIn && currentUser != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Image(
                                        painter = rememberAsyncImagePainter(currentUser!!.avatarUrl),
                                        contentDescription = stringResource(R.string.user_avatar),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // 内容区域
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> MCPBrowseTab(
                    issues = mcpIssues,
                    isLoading = isLoading,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    installingPlugins = installingPlugins,
                    installProgress = installProgress,
                    installedPluginIds = installedPluginIds,
                    onInstallMCP = { issue ->
                        viewModel.installMCPFromIssue(issue)
                    },
                    onRefresh = {
                        scope.launch {
                            viewModel.loadMCPMarketData()
                        }
                    },
                    onNavigateToDetail = onNavigateToDetail,
                    viewModel = viewModel
                )
                1 -> MCPMyTab(
                    isLoggedIn = isLoggedIn,
                    currentUser = currentUser,
                    onLogin = { showLoginDialog = true },
                    onLogout = {
                        scope.launch {
                            viewModel.logoutFromGitHub()
                        }
                    },
                    onNavigateToPublish = onNavigateToPublish,
                    onNavigateToManage = onNavigateToManage
                )
            }


        }
    }

    // GitHub登录对话框
    if (showLoginDialog) {
        GitHubLoginDialog(
            isLoggedIn = isLoggedIn,
            currentUser = currentUser,
            onDismiss = { showLoginDialog = false },
            onLogin = {
                scope.launch {
                    viewModel.initiateGitHubLogin(context)
                    showLoginDialog = false
                }
            },
            onLogout = {
                scope.launch {
                    viewModel.logoutFromGitHub()
                    showLoginDialog = false
                }
            }
        )
    }


}

@Composable
private fun MCPBrowseTab(
    issues: List<GitHubIssue>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    installingPlugins: Set<String>,
    installProgress: Map<String, com.ai.assistance.operit.data.mcp.InstallProgress>,
    installedPluginIds: Set<String>,
    onInstallMCP: (GitHubIssue) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToDetail: ((GitHubIssue) -> Unit)? = null,
    viewModel: MCPMarketViewModel
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("搜索插件名称、描述、作者...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 仅当没有搜索时显示标题
                    if (searchQuery.isBlank()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.available_mcp_plugins),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = onRefresh) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                                }
                            }
                        }
                    }

                    items(issues) { issue ->
                        val pluginInfo = remember(issue) {
                            MCPPluginParser.parsePluginInfo(issue)
                        }
                        // 使用issue的title作为插件ID，过滤非法字符
                        val pluginId = remember(issue) {
                            pluginInfo.title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                        }
                        val isInstalling = pluginId in installingPlugins
                        val isInstalled = pluginId in installedPluginIds
                        val currentProgress = installProgress[pluginId]
                        
                        MCPIssueCard(
                            issue = issue,
                            pluginInfo = pluginInfo,
                            onInstall = { onInstallMCP(issue) },
                            onViewDetails = {
                                // 优先使用内部详情页面，如果没有则在浏览器中打开
                                if (onNavigateToDetail != null) {
                                    onNavigateToDetail(issue)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issue.html_url))
                                    context.startActivity(intent)
                                }
                            },
                            isInstalling = isInstalling,
                            isInstalled = isInstalled,
                            installProgress = currentProgress,
                            onNavigateToDetail = onNavigateToDetail,
                            viewModel = viewModel
                        )
                        
                        // 加载reactions数据
                        LaunchedEffect(issue.number) {
                            viewModel.loadIssueReactions(issue.number)
                        }
                    }

                    if (issues.isEmpty() && !isLoading) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Store,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (searchQuery.isNotBlank()) stringResource(R.string.no_matching_plugins_found) else stringResource(R.string.no_mcp_plugins_available),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (searchQuery.isNotBlank()) stringResource(R.string.try_changing_keywords) else stringResource(R.string.refresh_or_try_again_later),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MCPRecommendedTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Stars,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.recommended_features),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.coming_soon_for_mcp_plugins),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MCPMyTab(
    isLoggedIn: Boolean,
    currentUser: GitHubUser?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToPublish: () -> Unit,
    onNavigateToManage: () -> Unit
) {
    if (!isLoggedIn) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.please_login_github_first),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.after_logging_in_you_can_view_and_manage_your_mcp_plugins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onLogin) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.login_github))
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentUser != null) {
                    Image(
                        painter = rememberAsyncImagePainter(currentUser.avatarUrl),
                        contentDescription = stringResource(R.string.user_avatar),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        currentUser.name ?: currentUser.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "@${currentUser.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToPublish,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.publish_new_plugin))
                }
                OutlinedButton(
                    onClick = onNavigateToManage,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.manage_my_plugins))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.logout))
                }
            }
        }
    }
}

@Composable
private fun MCPIssueCard(
    issue: GitHubIssue,
    pluginInfo: MCPPluginParser.ParsedPluginInfo,
    onInstall: () -> Unit,
    onViewDetails: () -> Unit,
    isInstalling: Boolean = false,
    isInstalled: Boolean = false,
    installProgress: com.ai.assistance.operit.data.mcp.InstallProgress? = null,
    onNavigateToDetail: ((GitHubIssue) -> Unit)? = null,
    viewModel: MCPMarketViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 标题和作者
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 显示作者（仓库所有者）
                        if (pluginInfo.repositoryOwner.isNotBlank()) {
                            // 获取用户头像
                            LaunchedEffect(pluginInfo.repositoryOwner) {
                                viewModel.fetchUserAvatar(pluginInfo.repositoryOwner)
                            }
                            
                            val avatarUrl by viewModel.userAvatarCache.collectAsState()
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val userAvatarUrl = avatarUrl[pluginInfo.repositoryOwner]
                                if (userAvatarUrl != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(userAvatarUrl),
                                        contentDescription = stringResource(R.string.author_avatar),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.author_colon) + " ${pluginInfo.repositoryOwner}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // 显示分享者
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(issue.user.avatarUrl),
                                contentDescription = stringResource(R.string.sharer_avatar),
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.share_colon) + " ${issue.user.login}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 状态标签
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (issue.state) {
                        "open" -> Color(0xFF22C55E).copy(alpha = 0.1f)
                        else -> Color(0xFF64748B).copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = when (issue.state) {
                            "open" -> stringResource(R.string.available)
                            else -> stringResource(R.string.closed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (issue.state) {
                            "open" -> Color(0xFF22C55E)
                            else -> Color(0xFF64748B)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
                }
            }

            // 描述
            if (pluginInfo.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluginInfo.description.take(80) + if (pluginInfo.description.length > 80) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Reactions显示
            val reactionsMap by viewModel.issueReactions.collectAsState()
            val reactions = reactionsMap[issue.number] ?: emptyList()
            val thumbsUpCount = reactions.count { it.content == "+1" }
            val heartCount = reactions.count { it.content == "heart" }
            
            if (thumbsUpCount > 0 || heartCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (thumbsUpCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "点赞",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = thumbsUpCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    if (heartCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "喜欢",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFFE91E63)
                            )
                            Text(
                                text = heartCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE91E63)
                            )
                        }
                    }
                }
            }



            // 操作按钮
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(stringResource(R.string.details), fontSize = 11.sp)
                }
                
                if (issue.state == "open") {
                    if (isInstalled) {
                        Button(
                            onClick = { /* No-op */ },
                            modifier = Modifier.weight(1f),
                            enabled = false,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(stringResource(R.string.installed), fontSize = 11.sp)
                        }
                    } else if (isInstalling) {
                        // 显示安装进度，使用Button样式保持一致性
                        Button(
                            onClick = { /* 安装中时不可点击 */ },
                            modifier = Modifier.weight(1f),
                            enabled = false,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            when (installProgress) {
                                is com.ai.assistance.operit.data.mcp.InstallProgress.Downloading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.downloading_progress, if (installProgress.progress >= 0) "${installProgress.progress}%" else ""),
                                        fontSize = 11.sp
                                    )
                                }
                                is com.ai.assistance.operit.data.mcp.InstallProgress.Extracting -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.extracting_progress),
                                        fontSize = 11.sp
                                    )
                                }
                                else -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.installing_progress),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(stringResource(R.string.install), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubLoginDialog(
    isLoggedIn: Boolean,
    currentUser: GitHubUser?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (isLoggedIn) stringResource(R.string.github_account) else stringResource(R.string.login_github)) 
        },
        text = {
            if (isLoggedIn && currentUser != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(currentUser.avatarUrl),
                        contentDescription = stringResource(R.string.user_avatar),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = currentUser.name ?: currentUser.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${currentUser.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentUser.bio != null) {
                        Text(
                            text = currentUser.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column {
                    Text(stringResource(R.string.after_logging_in_you_can))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• " + stringResource(R.string.publish_your_mcp_plugin_to_the_market))
                    Text("• " + stringResource(R.string.manage_your_published_plugins))
                    Text("• " + stringResource(R.string.better_installation_experience))
                }
            }
        },
        confirmButton = {
            if (isLoggedIn) {
                Button(onClick = onLogout) {
                    Text(stringResource(R.string.logout))
                }
            } else {
                Button(onClick = onLogin) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.login_github))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

 