package com.ai.phoneagent.core.prompt

import android.content.Context
import com.ai.phoneagent.core.common.AppJson
import com.ai.phoneagent.core.common.VersionComparator
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 主对话系统提示词仓库。
 *
 * 提示词以内置默认值兜底，并在本地 JSON 文件与远端配置间做版本比对：仅当远端版本更高时才覆盖本地缓存。
 */
object MainChatPromptRepository {

    private const val LOCAL_DIR_NAME = "xyla"
    private const val LOCAL_FILE_NAME = "prompt.json"
    private const val REMOTE_PROMPT_URL = "https://ariesapi.xuanyu.online/prompt.json"

    // 产品要求的内置提示词基线版本，本地缺失时用它兜底。
    private const val DEFAULT_PROMPT_VERSION = "1.0.0"

    /** 内置的主对话系统提示词：约束回答格式，并约定自动化转交标记 [[AUTO_EXECUTE:...]]。 */
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

    /** 提示词快照：版本号 + 提示词正文。 */
    private data class PromptSnapshot(
        val version: String,
        val prompt: String,
    )

    /** 进程内缓存，避免每次读取都访问磁盘。 */
    @Volatile
    private var cachedSnapshot: PromptSnapshot? = null

    /** 返回当前生效的主对话系统提示词正文。 */
    fun getMainChatSystemPrompt(context: Context): String {
        return loadLocalSnapshot(context).prompt
    }

    /** 返回当前生效的提示词版本号。 */
    fun getMainChatSystemPromptVersion(context: Context): String {
        return loadLocalSnapshot(context).version
    }

    /**
     * 从远端拉取提示词，仅当远端版本高于本地时才落盘覆盖。
     *
     * @return 是否发生了覆盖更新。
     */
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

    /**
     * 读取本地提示词快照（双重检查加锁）。
     *
     * 文件缺失或解析失败时回退到内置默认值并写回本地。
     */
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

    /** 获取本地提示词文件，必要时创建目录并用默认快照初始化文件。 */
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

    /** 构造内置默认快照。 */
    private fun defaultSnapshot(): PromptSnapshot {
        return PromptSnapshot(
            version = DEFAULT_PROMPT_VERSION,
            prompt = DEFAULT_MAIN_CHAT_PROMPT,
        )
    }

    /** 解析快照 JSON；版本或提示词任一为空则视为无效。 */
    private fun parseSnapshot(rawJson: String): PromptSnapshot? {
        val obj = runCatching { AppJson.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val version = obj["version"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (version.isBlank() || prompt.isBlank()) return null
        return PromptSnapshot(version = version, prompt = prompt)
    }

    /** 将快照序列化为 JSON 文本。 */
    private fun snapshotToJson(snapshot: PromptSnapshot): String {
        return buildJsonObject {
            put("version", snapshot.version)
            put("prompt", snapshot.prompt)
        }.toString()
    }

    /** 将快照写入本地文件并刷新内存缓存。 */
    private fun saveSnapshot(context: Context, snapshot: PromptSnapshot) {
        val file = getLocalPromptFile(context)
        file.writeText(snapshotToJson(snapshot), Charsets.UTF_8)
        cachedSnapshot = snapshot
    }

    /** 同步拉取远端快照；网络异常或非 2xx 响应时返回 null。 */
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
