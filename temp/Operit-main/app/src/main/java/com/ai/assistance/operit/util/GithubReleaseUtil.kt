package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.api.GitHubApiService
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub Release 工具类
 * 使用统一的 GitHubApiService 来获取 Release 信息
 * 支持自动添加认证头以提高 API 配额
 */
class GithubReleaseUtil(private val context: Context) {
    private val TAG = "GithubReleaseUtil"
    private val githubApiService = GitHubApiService(context)

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releasePageUrl: String
    )

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long?,
        val error: String? = null
    )

    companion object {
        private val GITHUB_MIRRORS = mapOf(
            "Ghfast" to "https://ghfast.top/",
            "GhProxy" to "https://ghproxy.com/",
            "GhProxyNet" to "https://ghproxy.net/",
            "GhProxyMirror" to "https://mirror.ghproxy.com/",
            "Gh-Proxy" to "https://gh-proxy.com/",
            "GitMirror" to "https://hub.gitmirror.com/",
            "Moeyy" to "https://github.moeyy.xyz/",
            "Workers" to "https://github.abskoop.workers.dev/"
        )

        /**
         * 获取镜像加速 URL
         * 用于加速 GitHub 下载
         */
        fun getMirroredUrls(originalUrl: String): Map<String, String> {
            if (!originalUrl.contains("github.com") || !originalUrl.contains("/releases/download/")) {
                return emptyMap()
            }

            return GITHUB_MIRRORS.mapValues { entry ->
                "${entry.value}$originalUrl"
            }
        }

        suspend fun probeMirrorUrls(
            urls: Map<String, String>,
            timeoutMs: Int = 2500
        ): Map<String, ProbeResult> {
            return withContext(Dispatchers.IO) {
                coroutineScope {
                    urls.entries
                        .map { (name, url) ->
                            async { name to probeOneUrl(url, timeoutMs) }
                        }
                        .awaitAll()
                        .toMap()
                }
            }
        }

        private fun probeOneUrl(url: String, timeoutMs: Int): ProbeResult {
            val startNs = System.nanoTime()
            return try {
                val code = requestOnce(url, timeoutMs, method = "HEAD")
                    ?: requestOnce(url, timeoutMs, method = "GET")
                    ?: -1

                val costMs = (System.nanoTime() - startNs) / 1_000_000
                val ok = code in 200..399
                ProbeResult(
                    ok = ok,
                    latencyMs = costMs,
                    error = if (ok) null else "HTTP $code"
                )
            } catch (e: Exception) {
                ProbeResult(
                    ok = false,
                    latencyMs = null,
                    error = e.javaClass.simpleName
                )
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
                conn.setRequestProperty("User-Agent", "Operit")
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
    }

    /**
     * 获取最新的 Release 信息
     * 如果用户已登录，会自动带上认证头以提高 API 配额
     */
    suspend fun fetchLatestReleaseInfo(repoOwner: String, repoName: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val result = githubApiService.getRepositoryReleases(
                owner = repoOwner,
                repo = repoName,
                page = 1,
                perPage = 1
            )

            result.fold(
                onSuccess = { releases ->
                    if (releases.isEmpty()) {
                        AppLogger.e(TAG, "No releases found for $repoOwner/$repoName")
                        return@withContext null
                    }

                    val latestRelease = releases.first()
                    val tagName = latestRelease.tag_name
                    val version = tagName.removePrefix("v")

                    // 查找 APK 资源
                    val apkAsset = latestRelease.assets.find { it.name.endsWith(".apk") }
                    val downloadUrl = apkAsset?.browser_download_url ?: latestRelease.html_url

                    ReleaseInfo(
                        version = version,
                        downloadUrl = downloadUrl,
                        releaseNotes = latestRelease.body ?: "",
                        releasePageUrl = latestRelease.html_url
                    )
                },
                onFailure = { exception ->
                    AppLogger.e(TAG, "Failed to get release info for $repoOwner/$repoName", exception)
                    null
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching latest release info for $repoOwner/$repoName", e)
            null
        }
    }
} 