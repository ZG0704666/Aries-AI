package com.ai.phoneagent.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import retrofit2.HttpException

object ReleaseUiUtil {
    private val GITHUB_MIRRORS = linkedMapOf(
        "官方" to "",
        "GhProxy" to "https://ghproxy.com/",
        "GhProxyNet" to "https://ghproxy.net/",
        "Ghfast" to "https://ghfast.top/",
        "GitMirror" to "https://hub.gitmirror.com/",
        "Moeyy" to "https://github.moeyy.xyz/",
    )

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun mirroredDownloadOptions(originalUrl: String?): List<Pair<String, String>> {
        if (originalUrl.isNullOrBlank()) return emptyList()
        val url = originalUrl.trim()
        val isReleaseAsset = url.contains("github.com") && url.contains("/releases/download/")
        if (!isReleaseAsset) return listOf("官方" to url)

        return GITHUB_MIRRORS.mapNotNull { (name, prefix) ->
            if (name == "官方") name to url else name to (prefix + url)
        }
    }

    fun formatError(t: Throwable): String {
        val http = t as? HttpException
        if (http != null) {
            val code = http.code()
            return when (code) {
                401, 403 -> "访问 GitHub 失败($code)：私有仓库需要 github.token（至少 repo 权限），或触发了 API 限流。"
                404 -> "仓库或 Release 不存在(404)。"
                else -> "网络错误：HTTP $code"
            }
        }

        val msg = t.message?.trim().orEmpty()
        return if (msg.isNotBlank()) msg else t.javaClass.simpleName
    }
}
