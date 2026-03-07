package com.ai.phoneagent.core.prompt

import android.content.Context
import com.ai.phoneagent.updates.VersionComparator
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MainChatPromptRepository {

    private const val LOCAL_DIR_NAME = "xyla"
    private const val LOCAL_FILE_NAME = "prompt.json"
    private const val REMOTE_PROMPT_URL = "https://ariesapi.xuanyu.online/prompt.json"

    // Baseline local prompt version required by product.
    private const val DEFAULT_PROMPT_VERSION = "1.0.0"

    private val DEFAULT_MAIN_CHAT_PROMPT =
        """
        你是 Aries AI。
        你具备手机自动化相关能力：当任务适合自动化时，你需要给出“可转交执行”的自动化指令；
        真正执行由系统在用户确认后完成，而不是由你直接执行。

        要求：
        1) 直接给出最终回答，使用 Markdown：标题/列表/代码块/表格等。
        2) 代码块使用三反引号 ``` 并尽量保持语法完整。
        3) 如果你判断该请求适合转交手机自动化执行，请在回复中追加且仅追加一行：
           [[AUTO_EXECUTE:这里填写可直接执行的中文自动化指令]]
        4) 自动化指令必须是自然语言任务描述（如“打开手机浏览器并访问 https://www.jd.com”），
           严禁输出 Selenium / JavaScript / Python / Node.js 代码。
        5) 如果不需要自动化，不要输出 AUTO_EXECUTE 标记。
        6) 自动化场景回答示例：
           我可以帮你执行这个手机操作。
           [[AUTO_EXECUTE:打开手机浏览器并访问 https://www.jd.com]]
        """.trimIndent()

    private data class PromptSnapshot(
        val version: String,
        val prompt: String,
    )

    @Volatile
    private var cachedSnapshot: PromptSnapshot? = null

    fun getMainChatSystemPrompt(context: Context): String {
        return loadLocalSnapshot(context).prompt
    }

    fun getMainChatSystemPromptVersion(context: Context): String {
        return loadLocalSnapshot(context).version
    }

    suspend fun refreshFromRemoteIfNewer(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val local = loadLocalSnapshot(context)
            val remote = fetchRemoteSnapshot() ?: return@withContext false

            if (VersionComparator.compare(remote.version, local.version) > 0) {
                saveSnapshot(context, remote)
                true
            } else {
                false
            }
        }

    private fun loadLocalSnapshot(context: Context): PromptSnapshot {
        cachedSnapshot?.let { return it }
        synchronized(this) {
            cachedSnapshot?.let { return it }

            val file = getLocalPromptFile(context)
            val parsed = runCatching { parseSnapshot(file.readText(Charsets.UTF_8)) }.getOrNull()
            val snapshot =
                if (parsed != null) {
                    parsed
                } else {
                    val fallback = defaultSnapshot()
                    saveSnapshot(context, fallback)
                    fallback
                }
            cachedSnapshot = snapshot
            return snapshot
        }
    }

    private fun getLocalPromptFile(context: Context): File {
        val dir = File(context.filesDir, LOCAL_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, LOCAL_FILE_NAME)
        if (!file.exists()) {
            val fallback = defaultSnapshot()
            file.writeText(snapshotToJson(fallback), Charsets.UTF_8)
        }
        return file
    }

    private fun defaultSnapshot(): PromptSnapshot {
        return PromptSnapshot(
            version = DEFAULT_PROMPT_VERSION,
            prompt = DEFAULT_MAIN_CHAT_PROMPT,
        )
    }

    private fun parseSnapshot(rawJson: String): PromptSnapshot? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val version = obj.optString("version").trim()
        val prompt = obj.optString("prompt").trim()
        if (version.isBlank() || prompt.isBlank()) return null
        return PromptSnapshot(version = version, prompt = prompt)
    }

    private fun snapshotToJson(snapshot: PromptSnapshot): String {
        val obj = JSONObject()
        obj.put("version", snapshot.version)
        obj.put("prompt", snapshot.prompt)
        return obj.toString(2)
    }

    private fun saveSnapshot(context: Context, snapshot: PromptSnapshot) {
        val file = getLocalPromptFile(context)
        file.writeText(snapshotToJson(snapshot), Charsets.UTF_8)
        cachedSnapshot = snapshot
    }

    private fun fetchRemoteSnapshot(): PromptSnapshot? {
        val connection =
            (URL(REMOTE_PROMPT_URL).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AriesAI-PromptUpdater")
            val code = connection.responseCode
            if (code !in 200..299) {
                return null
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseSnapshot(body)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
