package com.ai.assistance.operit.data.api

import android.content.Context
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubAccessTokenResponse(
    val access_token: String,
    val token_type: String,
    val scope: String? = null
)

@Serializable
data class GitHubRepository(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String?,
    val html_url: String,
    val clone_url: String,
    val stargazers_count: Int,
    val forks_count: Int,
    val language: String?,
    val topics: List<String> = emptyList(),
    val created_at: String,
    val updated_at: String,
    val owner: GitHubUser
)

@Serializable
data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val html_url: String,
    val state: String,
    val labels: List<GitHubLabel> = emptyList(),
    val user: GitHubUser,
    val created_at: String,
    val updated_at: String,
    val reactions: GitHubReactions? = null
)

@Serializable
data class GitHubLabel(
    val id: Long,
    val name: String,
    val color: String,
    val description: String?
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String> = emptyList()
)

@Serializable
data class UpdateIssueRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val labels: List<String>? = null
)

@Serializable
data class GitHubComment(
    val id: Long,
    val body: String,
    val user: GitHubUser,
    val created_at: String,
    val updated_at: String,
    val html_url: String
)

@Serializable
data class CreateCommentRequest(
    val body: String
)

@Serializable
data class GitHubReactions(
    val total_count: Int = 0,
    val thumbs_up: Int = 0, // +1
    val thumbs_down: Int = 0, // -1
    val laugh: Int = 0,
    val hooray: Int = 0,
    val confused: Int = 0,
    val heart: Int = 0,
    val rocket: Int = 0,
    val eyes: Int = 0
)

@Serializable
data class GitHubReaction(
    val id: Long,
    val content: String, // "+1", "-1", "laugh", "confused", "heart", "hooray", "rocket", "eyes"
    val user: GitHubUser,
    val created_at: String
)

@Serializable
data class CreateReactionRequest(
    val content: String
)

@Serializable
data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String?,
    val body: String?,
    val html_url: String,
    val published_at: String,
    val created_at: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val download_count: Int,
    val content_type: String
)

/**
 * GitHub API服务类
 * 提供GitHub OAuth认证、用户信息、仓库操作等功能
 */
class GitHubApiService(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val newRequest = request.newBuilder()
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "Operit-MCP-Client")
                .build()
            chain.proceed(newRequest)
        }
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val authPreferences = GitHubAuthPreferences.getInstance(context)
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITHUB_OAUTH_BASE = "https://github.com/login/oauth"
    }
    
    /**
     * 通过授权码获取访问令牌
     */
    suspend fun getAccessToken(code: String): Result<GitHubAccessTokenResponse> = withContext(Dispatchers.IO) {
        try {
            // GitHub OAuth API 要求使用 application/x-www-form-urlencoded 格式
            val formBody = FormBody.Builder()
                .add("client_id", GitHubAuthPreferences.GITHUB_CLIENT_ID)
                .add("client_secret", GitHubAuthPreferences.GITHUB_CLIENT_SECRET)
                .add("code", code)
                .build()
            
            val request = Request.Builder()
                .url("$GITHUB_OAUTH_BASE/access_token")
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                try {
                    com.ai.assistance.operit.util.AppLogger.d("GitHubApiService", "Token response: $responseBody")
                    val tokenResponse = json.decodeFromString<GitHubAccessTokenResponse>(responseBody)
                    Result.success(tokenResponse)
                } catch (e: Exception) {
                    com.ai.assistance.operit.util.AppLogger.e("GitHubApiService", "Failed to parse token response: $responseBody", e)
                    Result.failure(Exception("Failed to parse token response: ${e.message}. Response: $responseBody"))
                }
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}. Response: $responseBody"
                com.ai.assistance.operit.util.AppLogger.e("GitHubApiService", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("GitHubApiService", "Exception in getAccessToken", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/user")
                .addHeader("Authorization", authHeader)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val user = json.decodeFromString<GitHubUser>(responseBody)
                    Result.success(user)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 根据用户名获取GitHub用户信息
     */
    suspend fun getUser(username: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/users/$username")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val user = json.decodeFromString<GitHubUser>(responseBody)
                    Result.success(user)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 搜索仓库
     */
    suspend fun searchRepositories(
        query: String,
        sort: String = "stars",
        order: String = "desc",
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("search")
                .addPathSegment("repositories")
                .addQueryParameter("q", query)
                .addQueryParameter("sort", sort)
                .addQueryParameter("order", order)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val searchResult = json.parseToJsonElement(responseBody).jsonObject
                    val itemsArray = searchResult["items"]?.jsonArray
                    val repositories = itemsArray?.map { item ->
                        json.decodeFromJsonElement(GitHubRepository.serializer(), item)
                    } ?: emptyList()
                    Result.success(repositories)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取仓库的Issues
     */
    suspend fun getRepositoryIssues(
        owner: String,
        repo: String,
        state: String = "open",
        labels: String? = null,
        creator: String? = null,
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubIssue>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(repo)
                .addPathSegment("issues")
                .addQueryParameter("state", state)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .apply {
                    labels?.let { addQueryParameter("labels", it) }
                    creator?.let { addQueryParameter("creator", it) }
                }
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val issues = json.decodeFromString<List<GitHubIssue>>(responseBody)
                    Result.success(issues)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建Issue
     */
    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): Result<GitHubIssue> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val createRequest = CreateIssueRequest(title, body, labels)
            val requestBody = json.encodeToString(CreateIssueRequest.serializer(), createRequest)
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val issue = json.decodeFromString<GitHubIssue>(responseBody)
                    Result.success(issue)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: ${response.message}\n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 更新Issue
     */
    suspend fun updateIssue(
        owner: String,
        repo: String,
        issueNumber: Int,
        updateRequest: UpdateIssueRequest
    ): Result<GitHubIssue> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val requestBody = json.encodeToString(UpdateIssueRequest.serializer(), updateRequest)
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues/$issueNumber")
                .patch(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val issue = json.decodeFromString<GitHubIssue>(responseBody)
                    Result.success(issue)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: ${response.message}\n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 更新Issue（便利方法 - 更新标题和内容）
     */
    suspend fun updateIssue(
        owner: String,
        repo: String,
        issueNumber: Int,
        title: String? = null,
        body: String? = null
    ): Result<GitHubIssue> {
        val updateRequest = UpdateIssueRequest(title = title, body = body)
        return updateIssue(owner, repo, issueNumber, updateRequest)
    }
    
    /**
     * 更新Issue状态（便利方法）
     */
    suspend fun updateIssue(
        owner: String,
        repo: String,
        issueNumber: Int,
        state: String
    ): Result<GitHubIssue> {
        val updateRequest = UpdateIssueRequest(state = state)
        return updateIssue(owner, repo, issueNumber, updateRequest)
    }
    
    /**
     * 获取用户的仓库列表
     */
    suspend fun getUserRepositories(
        username: String? = null,
        type: String = "all",
        sort: String = "updated",
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val url = if (username != null) {
                "$GITHUB_API_BASE/users/$username/repos"
            } else {
                "$GITHUB_API_BASE/user/repos"
            }
            
            val httpUrl = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .apply {
                    if (username != null) {
                        addPathSegment("users")
                        addPathSegment(username)
                        addPathSegment("repos")
                    } else {
                        addPathSegment("user")
                        addPathSegment("repos")
                    }
                }
                .addQueryParameter("type", type)
                .addQueryParameter("sort", sort)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder().url(httpUrl)
            
            // 如果是获取当前用户的仓库，需要认证
            if (username == null) {
                val authHeader = authPreferences.getAuthorizationHeader()
                    ?: return@withContext Result.failure(Exception("No access token available"))
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val repositories = json.decodeFromString<List<GitHubRepository>>(responseBody)
                    Result.success(repositories)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取Issue的评论
     */
    suspend fun getIssueComments(
        owner: String,
        repo: String,
        issueNumber: Int,
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubComment>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(repo)
                .addPathSegment("issues")
                .addPathSegment(issueNumber.toString())
                .addPathSegment("comments")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val comments = json.decodeFromString<List<GitHubComment>>(responseBody)
                    Result.success(comments)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 为Issue创建评论
     */
    suspend fun createIssueComment(
        owner: String,
        repo: String,
        issueNumber: Int,
        body: String
    ): Result<GitHubComment> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val createRequest = CreateCommentRequest(body)
            val requestBody = json.encodeToString(CreateCommentRequest.serializer(), createRequest)
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues/$issueNumber/comments")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val comment = json.decodeFromString<GitHubComment>(responseBody)
                    Result.success(comment)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: ${response.message}\n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取Issue的reactions
     */
    suspend fun getIssueReactions(
        owner: String,
        repo: String,
        issueNumber: Int
    ): Result<List<GitHubReaction>> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues/$issueNumber/reactions")
                .addHeader("Accept", "application/vnd.github+json")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val reactions = json.decodeFromString<List<GitHubReaction>>(responseBody)
                    Result.success(reactions)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 为Issue添加reaction
     */
    suspend fun createIssueReaction(
        owner: String,
        repo: String,
        issueNumber: Int,
        content: String // "+1", "-1", "laugh", "confused", "heart", "hooray", "rocket", "eyes"
    ): Result<GitHubReaction> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val createRequest = CreateReactionRequest(content)
            val requestBody = json.encodeToString(CreateReactionRequest.serializer(), createRequest)
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues/$issueNumber/reactions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val reaction = json.decodeFromString<GitHubReaction>(responseBody)
                    Result.success(reaction)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: ${response.message}\n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除Issue的reaction
     */
    suspend fun deleteIssueReaction(
        owner: String,
        repo: String,
        issueNumber: Int,
        reactionId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/issues/$issueNumber/reactions/$reactionId")
                .delete()
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: ${response.message}\n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取仓库信息（包含星数）
     */
    suspend fun getRepository(
        owner: String,
        repo: String
    ): Result<GitHubRepository> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val repository = json.decodeFromString<GitHubRepository>(responseBody)
                    Result.success(repository)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取仓库的Releases
     */
    suspend fun getRepositoryReleases(
        owner: String,
        repo: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(repo)
                .addPathSegment("releases")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val releases = json.decodeFromString<List<GitHubRelease>>(responseBody)
                    Result.success(releases)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 