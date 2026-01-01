package com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubReaction
import com.ai.assistance.operit.data.api.GitHubRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SkillMarketViewModel(
    private val context: Context,
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val githubApiService = GitHubApiService(context)
    val githubAuth = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isRateLimitError = MutableStateFlow(false)
    val isRateLimitError: StateFlow<Boolean> = _isRateLimitError.asStateFlow()

    private val _skillIssues = MutableStateFlow<List<GitHubIssue>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val skillIssues: StateFlow<List<GitHubIssue>> =
        combine(_skillIssues, _searchQuery) { issues, query ->
            if (query.isBlank()) {
                issues
            } else {
                val lower = query.lowercase()
                issues.filter { issue ->
                    issue.title.lowercase().contains(lower) ||
                        (issue.body?.lowercase()?.contains(lower) == true) ||
                        issue.user.login.lowercase().contains(lower) ||
                        issue.labels.any { it.name.lowercase().contains(lower) }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _installingSkills = MutableStateFlow<Set<String>>(emptySet())
    val installingSkills: StateFlow<Set<String>> = _installingSkills.asStateFlow()

    private val _installedSkillRepoUrls = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillRepoUrls: StateFlow<Set<String>> = _installedSkillRepoUrls.asStateFlow()

    private val _installedSkillNames = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillNames: StateFlow<Set<String>> = _installedSkillNames.asStateFlow()

    private val _userPublishedSkills = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val userPublishedSkills: StateFlow<List<GitHubIssue>> = _userPublishedSkills.asStateFlow()

    private val _issueComments = MutableStateFlow<Map<Int, List<GitHubComment>>>(emptyMap())
    val issueComments: StateFlow<Map<Int, List<GitHubComment>>> = _issueComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingComments: StateFlow<Set<Int>> = _isLoadingComments.asStateFlow()

    private val _isPostingComment = MutableStateFlow<Set<Int>>(emptySet())
    val isPostingComment: StateFlow<Set<Int>> = _isPostingComment.asStateFlow()

    private val _userAvatarCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAvatarCache: StateFlow<Map<String, String>> = _userAvatarCache.asStateFlow()

    private val _issueReactions = MutableStateFlow<Map<Int, List<GitHubReaction>>>(emptyMap())
    val issueReactions: StateFlow<Map<Int, List<GitHubReaction>>> = _issueReactions.asStateFlow()

    private val _isLoadingReactions = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingReactions: StateFlow<Set<Int>> = _isLoadingReactions.asStateFlow()

    private val _isReacting = MutableStateFlow<Set<Int>>(emptySet())
    val isReacting: StateFlow<Set<Int>> = _isReacting.asStateFlow()

    private val _repositoryCache = MutableStateFlow<Map<String, GitHubRepository>>(emptyMap())
    val repositoryCache: StateFlow<Map<String, GitHubRepository>> = _repositoryCache.asStateFlow()

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("skill_publish_draft", Context.MODE_PRIVATE)

    data class PublishDraft(
        val title: String = "",
        val description: String = "",
        val repositoryUrl: String = ""
    )

    val publishDraft: PublishDraft
        get() = PublishDraft(
            title = sharedPrefs.getString("title", "") ?: "",
            description = sharedPrefs.getString("description", "") ?: "",
            repositoryUrl = sharedPrefs.getString("repositoryUrl", "") ?: ""
        )

    @Serializable
    private data class SkillMetadata(
        val repositoryUrl: String,
        val category: String = "",
        val tags: String = "",
        val version: String = "v1"
    )

    class Factory(
        private val context: Context,
        private val skillRepository: SkillRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SkillMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SkillMarketViewModel(context, skillRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "SkillMarketViewModel"
        private const val MARKET_REPO_OWNER = "AAswordman"
        private const val MARKET_REPO_NAME = "OperitSkillMarket"
        private const val SKILL_LABEL = "skill-plugin"
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun saveDraft(title: String, description: String, repositoryUrl: String) {
        sharedPrefs.edit().apply {
            putString("title", title)
            putString("description", description)
            putString("repositoryUrl", repositoryUrl)
            apply()
        }
    }

    fun clearDraft() {
        sharedPrefs.edit().clear().apply()
    }

    fun parseSkillInfoFromIssue(issue: GitHubIssue): PublishDraft {
        val body = issue.body ?: return PublishDraft(title = issue.title)
        val metadata = parseSkillMetadata(body)
        val description = extractDescription(body)

        return if (metadata != null) {
            PublishDraft(
                title = issue.title,
                description = description,
                repositoryUrl = metadata.repositoryUrl
            )
        } else {
            PublishDraft(
                title = issue.title,
                description = description.ifBlank { "无法解析Skill描述，请手动填写。" },
                repositoryUrl = ""
            )
        }
    }

    fun initiateGitHubLogin(context: Context) {
        try {
            val authUrl = githubAuth.getAuthorizationUrl()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "启动登录失败: ${e.message}"
            AppLogger.e(TAG, "Failed to initiate GitHub login", e)
        }
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = "退出登录失败: ${e.message}"
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    fun loadSkillMarketData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isRateLimitError.value = false

            val isLoggedIn = try {
                githubAuth.isLoggedIn()
            } catch (_: Exception) {
                false
            }

            try {
                val result = githubApiService.getRepositoryIssues(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    state = "open",
                    labels = SKILL_LABEL,
                    perPage = 50
                )

                result.fold(
                    onSuccess = { issues ->
                        _skillIssues.value = issues
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "未知错误"
                        _errorMessage.value = "加载Skill市场失败: $msg"
                        _skillIssues.value = emptyList()

                        if (msg.contains("403") || msg.contains("rate") || msg.contains("Rate")) {
                            _isRateLimitError.value = !isLoggedIn
                        }

                        AppLogger.e(TAG, "Failed to load skill market data", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                _skillIssues.value = emptyList()
                AppLogger.e(TAG, "Exception while loading skill market data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshInstalledSkills() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                try {
                    skillRepository.getAvailableSkillPackages()
                } catch (_: Exception) {
                    emptyMap()
                }
            }

            val installedNames = installed.keys.toSet()
            val installedRepoUrls = installed.values.mapNotNull { pkg ->
                try {
                    val marker = pkg.directory.resolve(".operit_repo_url")
                    if (marker.exists() && marker.isFile) {
                        marker.readText().trim().ifBlank { null }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }.toSet()

            _installedSkillNames.value = installedNames
            _installedSkillRepoUrls.value = installedRepoUrls
        }
    }

    fun loadUserPublishedSkills() {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = "无法获取用户信息"
                    return@launch
                }

                val resultWithLabel = githubApiService.getRepositoryIssues(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    state = "all",
                    labels = SKILL_LABEL,
                    creator = userInfo.login,
                    perPage = 100
                )

                val finalResult = resultWithLabel.fold(
                    onSuccess = { issues ->
                        if (issues.isNotEmpty()) {
                            Result.success(issues)
                        } else {
                            githubApiService.getRepositoryIssues(
                                owner = MARKET_REPO_OWNER,
                                repo = MARKET_REPO_NAME,
                                state = "all",
                                labels = null,
                                creator = userInfo.login,
                                perPage = 100
                            )
                        }
                    },
                    onFailure = { Result.failure(it) }
                )

                finalResult.fold(
                    onSuccess = { issues ->
                        _userPublishedSkills.value = issues
                    },
                    onFailure = { error ->
                        _errorMessage.value = "加载已发布 Skill 失败: ${error.message}"
                        AppLogger.e(TAG, "Failed to load user published skills", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                AppLogger.e(TAG, "Network error while loading user published skills", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeSkillFromMarket(issueNumber: Int) {
        viewModelScope.launch {
            try {
                if (!githubAuth.isLoggedIn()) {
                    _errorMessage.value = "请先登录GitHub"
                    return@launch
                }

                _isLoading.value = true
                val result = githubApiService.updateIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    state = "closed"
                )

                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "已从市场移除", Toast.LENGTH_SHORT).show()
                        loadUserPublishedSkills()
                        loadSkillMarketData()
                    },
                    onFailure = { error ->
                        _errorMessage.value = "移除失败: ${error.message}"
                        AppLogger.e(TAG, "Failed to remove skill from market", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "移除失败: ${e.message}"
                AppLogger.e(TAG, "Failed to remove skill from market", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadIssueComments(issueNumber: Int) {
        viewModelScope.launch {
            _isLoadingComments.value = _isLoadingComments.value + issueNumber
            try {
                val result = githubApiService.getIssueComments(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    perPage = 50
                )

                result.fold(
                    onSuccess = { comments ->
                        _issueComments.value = _issueComments.value + (issueNumber to comments)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "加载评论失败: ${error.message}"
                        AppLogger.e(TAG, "Failed to load issue comments", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "加载评论失败: ${e.message}"
                AppLogger.e(TAG, "Failed to load issue comments", e)
            } finally {
                _isLoadingComments.value = _isLoadingComments.value - issueNumber
            }
        }
    }

    fun postIssueComment(issueNumber: Int, body: String) {
        val text = body.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _isPostingComment.value = _isPostingComment.value + issueNumber
            try {
                if (!githubAuth.isLoggedIn()) {
                    _errorMessage.value = "请先登录GitHub"
                    return@launch
                }

                val result = githubApiService.createIssueComment(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    body = text
                )

                result.fold(
                    onSuccess = {
                        loadIssueComments(issueNumber)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "发表评论失败: ${error.message}"
                        AppLogger.e(TAG, "Failed to post issue comment", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "发表评论失败: ${e.message}"
                AppLogger.e(TAG, "Failed to post issue comment", e)
            } finally {
                _isPostingComment.value = _isPostingComment.value - issueNumber
            }
        }
    }

    suspend fun publishSkill(
        title: String,
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): Boolean {
        return try {
            if (!githubAuth.isLoggedIn()) return false

            val body = buildSkillPublishIssueBody(
                description = description,
                repositoryUrl = repositoryUrl,
                version = version
            )

            val result = githubApiService.createIssue(
                owner = MARKET_REPO_OWNER,
                repo = MARKET_REPO_NAME,
                title = title,
                body = body,
                labels = listOf(SKILL_LABEL)
            )

            if (result.isSuccess) return true

            val errMsg = result.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("422")) {
                val retry = githubApiService.createIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    title = title,
                    body = body,
                    labels = emptyList()
                )
                retry.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to publish skill", e)
            false
        }
    }

    suspend fun updatePublishedSkill(
        issueNumber: Int,
        title: String,
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): Boolean {
        return try {
            if (!githubAuth.isLoggedIn()) return false

            val body = buildSkillPublishIssueBody(
                description = description,
                repositoryUrl = repositoryUrl,
                version = version
            )

            val result = githubApiService.updateIssue(
                owner = MARKET_REPO_OWNER,
                repo = MARKET_REPO_NAME,
                issueNumber = issueNumber,
                title = title,
                body = body
            )

            result.isSuccess
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update skill", e)
            false
        }
    }

    private fun buildSkillPublishIssueBody(
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): String {
        return buildString {
            try {
                val metadata = SkillMetadata(
                    repositoryUrl = repositoryUrl,
                    version = version
                )
                val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
                val metadataJson = json.encodeToString(metadata)
                appendLine("<!-- operit-skill-json: $metadataJson -->")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to serialize skill metadata", e)
            }

            appendLine("<!-- operit-parser-version: $version -->")
            appendLine()

            appendLine("## 📋 Skill 信息")
            appendLine()
            appendLine("**描述:** $description")
            appendLine()

            if (repositoryUrl.isNotBlank()) {
                appendLine("## 🔗 仓库信息")
                appendLine()
                appendLine("**仓库地址:** $repositoryUrl")
                appendLine()
            }

            if (repositoryUrl.isNotBlank()) {
                appendLine("## 📦 安装方式")
                appendLine()
                appendLine("1. 打开 Operit → 包管理 → Skills")
                appendLine("2. 点击「导入 Skill」→ 「从仓库导入」")
                appendLine("3. 输入仓库地址：`$repositoryUrl`")
                appendLine("4. 确认导入")
                appendLine()
            }

            appendLine("## 🛠️ 技术信息")
            appendLine()
            appendLine("| 项目 | 值 |")
            appendLine("|------|-----|")
            appendLine("| 发布平台 | Operit Skill 市场 |")
            appendLine("| 解析版本 | 1.0 |")
            appendLine("| 发布时间 | ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))} |")
            appendLine("| 状态 | ⏳ Pending Review |")
            appendLine()
        }
    }

    private fun parseSkillMetadata(body: String): SkillMetadata? {
        val metadataPattern = Regex("""<!-- operit-skill-json: (\{.*?\}) -->""", RegexOption.DOT_MATCHES_ALL)
        val match = metadataPattern.find(body)
        return match?.let {
            val jsonString = it.groupValues[1]
            try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<SkillMetadata>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to parse skill metadata JSON from issue body.", e)
                null
            }
        }
    }

    private fun extractDescription(body: String): String {
        val descriptionPattern = Regex("""\*\*描述:\*\*\s*(.+?)(?=\n\*\*|\n##|\Z)""", RegexOption.DOT_MATCHES_ALL)
        val description = descriptionPattern.find(body)?.groupValues?.get(1)?.trim()
        return when {
            !description.isNullOrBlank() -> description
            body.isNotBlank() -> body.take(200).trim()
            else -> ""
        }
    }

    fun installSkillFromRepoUrl(repoUrl: String) {
        val key = repoUrl.trim()
        if (key.isBlank()) {
            _errorMessage.value = "无效的仓库地址"
            return
        }

        viewModelScope.launch {
            _installingSkills.value = _installingSkills.value + key
            try {
                val result = skillRepository.importSkillFromGitHubRepo(key)
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                _installedSkillRepoUrls.value = _installedSkillRepoUrls.value + key
                refreshInstalledSkills()
            } catch (e: Exception) {
                _errorMessage.value = "安装失败: ${e.message}"
                AppLogger.e(TAG, "Failed to install skill from repo", e)
            } finally {
                _installingSkills.value = _installingSkills.value - key
            }
        }
    }

    fun fetchUserAvatar(username: String) {
        if (username.isBlank() || _userAvatarCache.value.containsKey(username)) {
            return
        }

        viewModelScope.launch {
            try {
                val result = githubApiService.getUser(username)
                result.fold(
                    onSuccess = { user ->
                        val currentCache = _userAvatarCache.value.toMutableMap()
                        currentCache[username] = user.avatarUrl
                        _userAvatarCache.value = currentCache
                    },
                    onFailure = { error ->
                        AppLogger.w(TAG, "Failed to fetch user avatar for $username: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "Exception while fetching user avatar for $username", e)
            }
        }
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        if (repositoryUrl.isBlank() || _repositoryCache.value.containsKey(repositoryUrl)) {
            return
        }

        val repoPath = repositoryUrl.removePrefix("https://github.com/")
        val parts = repoPath.split("/")
        if (parts.size < 2) {
            AppLogger.w(TAG, "Invalid repository URL: $repositoryUrl")
            return
        }

        val owner = parts[0]
        val repo = parts[1]

        viewModelScope.launch {
            try {
                val result = githubApiService.getRepository(owner, repo)
                result.fold(
                    onSuccess = { repository ->
                        val currentCache = _repositoryCache.value.toMutableMap()
                        currentCache[repositoryUrl] = repository
                        _repositoryCache.value = currentCache
                    },
                    onFailure = { error ->
                        AppLogger.w(TAG, "Failed to fetch repository info for $repositoryUrl: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "Exception while fetching repository info for $repositoryUrl", e)
            }
        }
    }

    fun loadIssueReactions(issueNumber: Int, force: Boolean = false) {
        if (issueNumber in _isLoadingReactions.value) {
            return
        }

        if (!force && _issueReactions.value.containsKey(issueNumber)) {
            return
        }

        viewModelScope.launch {
            try {
                _isLoadingReactions.value = _isLoadingReactions.value + issueNumber

                val result = githubApiService.getIssueReactions(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber
                )

                result.fold(
                    onSuccess = { reactions ->
                        val currentReactions = _issueReactions.value.toMutableMap()
                        currentReactions[issueNumber] = reactions
                        _issueReactions.value = currentReactions
                    },
                    onFailure = { error ->
                        AppLogger.e(TAG, "Failed to load reactions for issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Exception while loading reactions for issue #$issueNumber", e)
            } finally {
                _isLoadingReactions.value = _isLoadingReactions.value - issueNumber
            }
        }
    }

    fun addReactionToIssue(issueNumber: Int, reactionType: String) {
        if (issueNumber in _isReacting.value) {
            return
        }

        viewModelScope.launch {
            try {
                _isReacting.value = _isReacting.value + issueNumber

                val result = githubApiService.createIssueReaction(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    content = reactionType
                )

                result.fold(
                    onSuccess = { newReaction ->
                        val currentReactions = _issueReactions.value.toMutableMap()
                        val existingReactions = currentReactions[issueNumber] ?: emptyList()
                        currentReactions[issueNumber] = existingReactions + newReaction
                        _issueReactions.value = currentReactions

                        Toast.makeText(
                            context,
                            "点赞成功！",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        _errorMessage.value = "点赞失败: ${error.message}"
                        AppLogger.e(TAG, "Failed to add reaction to issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "点赞时发生错误: ${e.message}"
                AppLogger.e(TAG, "Exception while adding reaction to issue #$issueNumber", e)
            } finally {
                _isReacting.value = _isReacting.value - issueNumber
            }
        }
    }
}
