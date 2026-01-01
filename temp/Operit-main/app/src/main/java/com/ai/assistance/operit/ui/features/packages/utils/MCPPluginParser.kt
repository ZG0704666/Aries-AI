package com.ai.assistance.operit.ui.features.packages.utils

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.api.GitHubIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames

/**
 * MCP 插件信息解析工具类
 */
object MCPPluginParser {
    private const val TAG = "MCPPluginParser"

    @Serializable
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    data class MCPMetadata(
        val repositoryUrl: String,
        @JsonNames("installCommand") // 兼容旧的 installCommand
        val installConfig: String, // 改为安装配置
        val category: String,
        val tags: String,
        val version: String
    )

    data class ParsedPluginInfo(
        val title: String,
        val description: String,
        val repositoryUrl: String = "",
        val installConfig: String = "", // 改为安装配置
        val category: String = "",
        val tags: String = "",
        val version: String = "",
        val repositoryOwner: String = "", // 仓库所有者（插件作者）
        val repositoryOwnerAvatarUrl: String = "" // 仓库所有者头像URL
    )

    /**
     * 从 GitHub Issue 解析插件信息
     */
    fun parsePluginInfo(issue: GitHubIssue): ParsedPluginInfo {
        val body = issue.body ?: return ParsedPluginInfo(
            title = issue.title,
            description = "无描述信息"
        )

        // 尝试解析 JSON 元数据
        val metadata = parseMCPMetadata(body)
        
        // 提取描述信息
        val description = extractDescription(body)

        return if (metadata != null) {
            ParsedPluginInfo(
                title = issue.title,
                description = description,
                repositoryUrl = metadata.repositoryUrl,
                installConfig = metadata.installConfig,
                category = metadata.category,
                tags = metadata.tags,
                version = metadata.version,
                repositoryOwner = extractRepositoryOwner(metadata.repositoryUrl)
            )
        } else {
            // 尝试从描述中提取GitHub链接
            val repoUrl = extractRepositoryUrlFromDescription(body)
            ParsedPluginInfo(
                title = issue.title,
                description = description,
                repositoryUrl = repoUrl,
                repositoryOwner = extractRepositoryOwner(repoUrl)
            )
        }
    }

    /**
     * 从 Issue body 中提取描述信息
     */
    private fun extractDescription(body: String): String {
        // 优先尝试提取格式化的描述
        val descriptionPattern = Regex("""\*\*描述:\*\*\s*(.+?)(?=\n\*\*|\n##|\Z)""", RegexOption.DOT_MATCHES_ALL)
        val description = descriptionPattern.find(body)?.groupValues?.get(1)?.trim()
        
        return when {
            !description.isNullOrBlank() -> description
            body.isNotBlank() -> body.take(150).trim()
            else -> "无描述信息"
        }
    }

    /**
     * 解析隐藏在注释中的 JSON 元数据
     */
    private fun parseMCPMetadata(body: String): MCPMetadata? {
        val metadataPattern = Regex("""<!-- operit-mcp-json: (\{.*?\}) -->""", RegexOption.DOT_MATCHES_ALL)
        val match = metadataPattern.find(body)
        
        return match?.let {
            val jsonString = it.groupValues[1]
            try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<MCPMetadata>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to parse MCP metadata JSON from issue body.", e)
                null
            }
        }
    }

    /**
     * 从GitHub URL中提取仓库所有者
     */
    private fun extractRepositoryOwner(repositoryUrl: String): String {
        if (repositoryUrl.isBlank()) return ""
        
        val githubPattern = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = githubPattern.find(repositoryUrl)
        
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * 从描述文本中提取GitHub仓库链接
     */
    private fun extractRepositoryUrlFromDescription(body: String): String {
        // 匹配各种GitHub链接格式
        val patterns = listOf(
            Regex("""https://github\.com/[^/\s]+/[^/\s]+"""),
            Regex("""github\.com/[^/\s]+/[^/\s]+"""),
            Regex("""\[.*?\]\((https://github\.com/[^/\s)]+/[^/\s)]+)\)"""), // Markdown链接
            Regex("""仓库[：:]\s*(https://github\.com/[^/\s]+/[^/\s]+)"""), // 中文标签
            Regex("""Repository[：:]\s*(https://github\.com/[^/\s]+/[^/\s]+)""") // 英文标签
        )
        
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val url = if (match.groupValues.size > 1) {
                    match.groupValues[1] // 从捕获组获取
                } else {
                    match.value // 直接使用匹配值
                }
                
                // 确保URL以https://开头
                return if (url.startsWith("http")) {
                    url
                } else {
                    "https://$url"
                }
            }
        }
        
        return ""
    }
} 