package com.ai.assistance.operit.ui.features.packages.utils

import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

object SkillIssueParser {
    private const val TAG = "SkillIssueParser"

    @Serializable
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    data class SkillMetadata(
        @JsonNames("repoUrl")
        val repositoryUrl: String,
        val category: String = "",
        val tags: String = "",
        @JsonNames("version")
        val version: String = ""
    )

    data class ParsedSkillInfo(
        val title: String,
        val description: String,
        val repositoryUrl: String = "",
        val category: String = "",
        val tags: String = "",
        val version: String = "",
        val repositoryOwner: String = ""
    )

    fun parseSkillInfo(issue: GitHubIssue): ParsedSkillInfo {
        val body = issue.body
        if (body.isNullOrBlank()) {
            return ParsedSkillInfo(
                title = issue.title,
                description = "无描述信息"
            )
        }

        val metadata = parseSkillMetadata(body)
        val description = extractDescription(body)

        return if (metadata != null) {
            ParsedSkillInfo(
                title = issue.title,
                description = description,
                repositoryUrl = metadata.repositoryUrl,
                category = metadata.category,
                tags = metadata.tags,
                version = metadata.version,
                repositoryOwner = extractRepositoryOwner(metadata.repositoryUrl)
            )
        } else {
            val repoUrl = extractRepositoryUrlFromDescription(body)
            ParsedSkillInfo(
                title = issue.title,
                description = description,
                repositoryUrl = repoUrl,
                repositoryOwner = extractRepositoryOwner(repoUrl)
            )
        }
    }

    private fun extractDescription(body: String): String {
        val descriptionPattern = Regex("""\*\*描述:\*\*\s*(.+?)(?=\n\*\*|\n##|\Z)""", RegexOption.DOT_MATCHES_ALL)
        val description = descriptionPattern.find(body)?.groupValues?.get(1)?.trim()

        return when {
            !description.isNullOrBlank() -> description
            body.isNotBlank() -> body.take(150).trim()
            else -> "无描述信息"
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

    private fun extractRepositoryOwner(repositoryUrl: String): String {
        if (repositoryUrl.isBlank()) return ""

        val githubPattern = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = githubPattern.find(repositoryUrl)

        return match?.groupValues?.get(1) ?: ""
    }

    private fun extractRepositoryUrlFromDescription(body: String): String {
        val patterns = listOf(
            Regex("""https://github\.com/[^/\s]+/[^/\s]+"""),
            Regex("""github\.com/[^/\s]+/[^/\s]+"""),
            Regex("""\[.*?\]\((https://github\.com/[^/\s)]+/[^/\s)]+)\)"""),
            Regex("""仓库[：:]\s*(https://github\.com/[^/\s]+/[^/\s]+)"""),
            Regex("""Repository[：:]\s*(https://github\.com/[^/\s]+/[^/\s]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val url = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                return if (url.startsWith("http")) url else "https://$url"
            }
        }

        return ""
    }
}
