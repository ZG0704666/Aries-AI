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

/** 发布相关 UI 辅助：打开链接、生成镜像下载选项并探测可用性。 */
object ReleaseUiUtil {
    /** 镜像站点定义。 */
    private data class MirrorSite(
        val name: String,
        val buildUrl: (String) -> String,
    )

    /** 可选的 GitHub 下载镜像（官方直连 + 加速镜像）。 */
    private val githubMirrors = listOf(
        MirrorSite("官方直连") { it },
        MirrorSite("镜像加速") { "https://ghfast.top/$it" },
    )

    /** 在外部浏览器打开链接，无可用应用时返回 false。 */
    fun openUrl(context: Context, url: String): Boolean {
        val target = url.trim()
        if (target.isBlank()) return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            false
        }
    }

    /**
     * 基于原始下载地址生成「名称 → 地址」形式的镜像下载选项。
     *
     * 非 Release 资产地址只返回官方直连。
     */
    fun mirroredDownloadOptions(originalUrl: String?): List<Pair<String, String>> {
        // 优先使用 release asset 的 browser_download_url，避免镜像站对 latest/download 的
        // 302 跳转兼容性差导致点击无反馈。
        val url = originalUrl?.trim().orEmpty()
        if (url.isBlank()) return emptyList()

        val isReleaseAsset =
            url.contains("github.com") &&
                (url.contains("/releases/download/") || url.contains("/releases/latest/download/"))
        if (!isReleaseAsset) return listOf("官方直连" to url)

        val stableUrl =
            "https://github.com/${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}/releases/latest/download/${UpdateConfig.APK_ASSET_NAME}"
        val target = url.takeIf { it.isNotBlank() } ?: stableUrl

        return githubMirrors.map { site ->
            site.name to site.buildUrl(target)
        }
    }

    /** 单个镜像地址的探测结果。 */
    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long?,
        val error: String? = null,
    )

    /**
     * 探测各镜像地址可用性，仅保留可用项并按延迟升序排列；全部不可用时回退为原始选项。
     */
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

    /** 并发探测多个镜像地址并汇总结果。 */
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

    /** 探测单个镜像地址：优先 HEAD，失败再 GET，记录耗时与状态码。 */
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

    /**
     * 发起一次探测请求并返回状态码。
     *
     * GET 仅请求首个字节（`Range: bytes=0-0`）；不支持该方法时返回 null。
     */
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

    /** 将异常转换为面向用户的可读错误文案。 */
    fun formatError(t: Throwable): String {
        val msg = t.message?.trim().orEmpty()
        val m = Regex("HTTP\\s+(\\d{3})").find(msg)
        if (m != null) {
            val code = m.groupValues.getOrNull(1)?.toIntOrNull()
            if (code != null) {
                return when (code) {
                    401, 403 -> "访问 GitHub 失败($code)：可能触发 API 限流。"
                    404 -> "仓库或 Release 不存在(404)。"
                    else -> "网络错误：HTTP $code"
                }
            }
        }
        return if (msg.isNotBlank()) msg else t.javaClass.simpleName
    }
}
