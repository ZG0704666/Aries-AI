package com.ai.phoneagent.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException

object ReleaseUiUtil {
    private data class MirrorSite(
        val name: String,
        val buildUrl: (String) -> String,
    )

    private val GITHUB_MIRRORS = listOf(
        MirrorSite("官方直连") { it },
        MirrorSite("镜像加速1") { "https://ghfast.top/$it" }
    )

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun mirroredDownloadOptions(originalUrl: String?): List<Pair<String, String>> {
        // 优先使用 API 返回的 browser_download_url（通常是 /releases/download/<tag>/xxx.apk）
        // 不强制 latest/download，避免镜像站对 302/链式跳转兼容性差导致“打开空白”。
        val url = originalUrl?.trim().orEmpty()
        if (url.isBlank()) return emptyList()
        val isReleaseAsset =
            url.contains("github.com") &&
                (url.contains("/releases/download/") || url.contains("/releases/latest/download/"))
        if (!isReleaseAsset) return listOf("官方" to url)

        val stableUrl =
            "https://github.com/${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}/releases/latest/download/${UpdateConfig.APK_ASSET_NAME}"
        val target = url.takeIf { it.isNotBlank() } ?: stableUrl

        return GITHUB_MIRRORS.map { site ->
            site.name to site.buildUrl(target)
        }
    }

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long?,
        val error: String? = null,
    )

    suspend fun mirroredDownloadOptionsChecked(
        originalUrl: String?,
        timeoutMs: Int = 2500,
    ): List<Pair<String, String>> {
        val options = mirroredDownloadOptions(originalUrl)
        if (options.size <= 1) return options

        val probe = probeMirrorUrls(options.associate { it.first to it.second }, timeoutMs)
        val ok =
            options
                .mapNotNull { (name, u) ->
                    val r = probe[name]
                    if (r?.ok == true) name to u else null
                }
                .sortedWith(
                    compareBy<Pair<String, String>> {
                        probe[it.first]?.latencyMs ?: Long.MAX_VALUE
                    }
                )

        return if (ok.isNotEmpty()) ok else options
    }

    private suspend fun probeMirrorUrls(
        urls: Map<String, String>,
        timeoutMs: Int,
    ): Map<String, ProbeResult> {
        return withContext(Dispatchers.IO) {
            coroutineScope {
                urls.entries
                    .map { (name, u) ->
                        async { name to probeOneUrl(u, timeoutMs) }
                    }
                    .awaitAll()
                    .toMap()
            }
        }
    }

    private fun probeOneUrl(url: String, timeoutMs: Int): ProbeResult {
        val startNs = System.nanoTime()
        return try {
            val code =
                requestOnce(url, timeoutMs, method = "HEAD")
                    ?: requestOnce(url, timeoutMs, method = "GET")
                    ?: -1
            val costMs = (System.nanoTime() - startNs) / 1_000_000
            val ok = code in 200..399
            ProbeResult(ok = ok, latencyMs = costMs, error = if (ok) null else "HTTP $code")
        } catch (e: Exception) {
            ProbeResult(ok = false, latencyMs = null, error = e.javaClass.simpleName)
        }
    }

    private fun requestOnce(url: String, timeoutMs: Int, method: String): Int? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "PhoneAgent")
            if (method == "GET") {
                conn.setRequestProperty("Range", "bytes=0-0")
            }
            conn.connect()
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == 501) null else code
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
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
        val m = Regex("HTTP\\s+(\\d{3})").find(msg)
        if (m != null) {
            val code = m.groupValues.getOrNull(1)?.toIntOrNull()
            if (code != null) {
                return when (code) {
                    401, 403 -> "访问 GitHub 失败($code)：可能触发了 API 限流，建议配置 github.token。"
                    404 -> "仓库或 Release 不存在(404)。"
                    else -> "网络错误：HTTP $code"
                }
            }
        }
        return if (msg.isNotBlank()) msg else t.javaClass.simpleName
    }
}
