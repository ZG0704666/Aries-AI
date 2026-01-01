package com.ai.assistance.operit.data.skill

import android.content.Context
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.util.AppLogger
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: SkillRepository? = null

        private const val TAG = "SkillRepository"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        fun getInstance(context: Context): SkillRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val skillManager by lazy { SkillManager.getInstance(context) }

    fun getSkillsDirectoryPath(): String = skillManager.getSkillsDirectoryPath()

    fun getAvailableSkillPackages(): Map<String, SkillPackage> = skillManager.getAvailableSkills()

    fun readSkillContent(skillName: String): String? = skillManager.readSkillContent(skillName)

    fun deleteSkill(skillName: String): Boolean = skillManager.deleteSkill(skillName)

    fun importSkillFromZip(zipFile: File): String = skillManager.importSkillFromZip(zipFile)

    suspend fun importSkillFromGitHubRepo(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
            val repoOwnerAndName = extractOwnerAndRepo(repoUrl)
                ?: return@withContext "无效的 GitHub 仓库 URL"

            val (owner, repoName) = repoOwnerAndName
            val defaultBranch = getGithubDefaultBranch(owner, repoName)
                ?: return@withContext "无法确定 $owner/$repoName 的默认分支"

            val zipUrl = "https://github.com/$owner/$repoName/archive/refs/heads/$defaultBranch.zip"
            val tempFile = File(context.cacheDir, "skill_${owner}_${repoName}_repo.zip")
            if (tempFile.exists()) tempFile.delete()

            try {
                val skillsRootDir = File(getSkillsDirectoryPath())
                val beforeDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()

                val downloaded = downloadFromUrl(zipUrl, tempFile)
                if (!downloaded || !tempFile.exists() || tempFile.length() <= 0L) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext "下载仓库 ZIP 文件失败"
                }

                val result = importSkillFromZip(tempFile)
                tempFile.delete()

                // Write repoUrl marker for reliable installed-state detection.
                if (result.startsWith("已导入 Skill:")) {
                    val afterDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
                    val newDirs = afterDirs - beforeDirs
                    val newDirName = newDirs.singleOrNull()
                    if (!newDirName.isNullOrBlank()) {
                        try {
                            File(skillsRootDir, newDirName)
                                .resolve(".operit_repo_url")
                                .writeText(repoUrl.trim())
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to write .operit_repo_url marker", e)
                        }
                    }
                }

                result
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to import skill from GitHub repo", e)
                if (tempFile.exists()) tempFile.delete()
                "导入失败: ${e.message}"
            }
        }
    }

    private fun extractOwnerAndRepo(repoUrl: String): Pair<String, String>? {
        val regex = "(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)(?:\\.git)?/?.*".toRegex()
        val matchResult = regex.find(repoUrl.trim()) ?: return null
        val owner = matchResult.groupValues.getOrNull(1).orEmpty()
        val repo = matchResult.groupValues.getOrNull(2).orEmpty()
        if (owner.isBlank() || repo.isBlank()) return null
        return owner to repo
    }

    private fun downloadFromUrl(zipUrl: String, outFile: File): Boolean {
        val url = URL(zipUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doInput = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            AppLogger.e(TAG, "Download failed, HTTP ${connection.responseCode}")
            return false
        }

        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        return true
    }

    private fun getGithubDefaultBranch(owner: String, repoName: String): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        return try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JsonParser.parseString(response).asJsonObject
                jsonObject.get("default_branch")?.asString
            } else {
                AppLogger.e(TAG, "GitHub API failed, HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch GitHub default branch", e)
            null
        }
    }
}
