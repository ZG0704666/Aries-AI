package com.ai.phoneagent.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases Atom 订阅兜底方案。
 *
 * 当 API 不可用时，改为解析 `releases.atom` 获取最新版本信息。
 */
object AtomReleaseFallback {

    /** 兜底请求用的 OkHttp 客户端（较短超时）。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    /** 解析 Atom 订阅中的最新条目，无条目或请求失败时返回 null / 失败结果。 */
    suspend fun fetchLatest(owner: String, repo: String): Result<ReleaseEntry?> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://github.com/$owner/$repo/releases.atom"
                val req =
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "PhoneAgent")
                        .build()

                val resp = client.newCall(req).execute()
                try {
                    if (!resp.isSuccessful) {
                        throw RuntimeException("HTTP ${resp.code}")
                    }
                    val xml = resp.body?.string().orEmpty()

                    val entryXml = extractFirstEntry(xml) ?: return@runCatching null

                    val title = extractTagValue(entryXml, "title")?.trim().orEmpty()
                    val updated = extractTagValue(entryXml, "updated")?.trim().orEmpty()
                    val date = updated.take(10)

                    val linkHref =
                        extractLinkHref(entryXml) ?: "https://github.com/$owner/$repo/releases"
                    val versionTag = parseTagFromReleaseLink(linkHref) ?: title.ifBlank { "最新版本" }
                    val version = versionTag.removePrefix("v")

                    val rawContent = extractContent(entryXml).orEmpty()
                    val body = stripHtml(rawContent).trim()

                    val apkUrl =
                        "https://github.com/$owner/$repo/releases/download/$versionTag/${UpdateConfig.APK_ASSET_NAME}"

                    ReleaseEntry(
                        versionTag = versionTag,
                        version = version,
                        title = if (title.isNotBlank()) title else "版本更新",
                        date = date,
                        isPrerelease = false,
                        body = body,
                        releaseUrl = linkHref,
                        apkUrl = apkUrl,
                        apkAssetId = null,
                    )
                } finally {
                    resp.close()
                }
            }
        }
    }

    /** 截取 XML 中第一个 `<entry>...</entry>` 片段。 */
    private fun extractFirstEntry(xml: String): String? {
        val start = xml.indexOf("<entry")
        if (start < 0) return null
        val end = xml.indexOf("</entry>", start)
        if (end < 0) return null
        return xml.substring(start, end + "</entry>".length)
    }

    /** 提取指定标签的文本内容（忽略标签属性）。 */
    private fun extractTagValue(xml: String, tag: String): String? {
        val open = "<$tag"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val gt = xml.indexOf('>', start)
        if (gt < 0) return null
        val close = "</$tag>"
        val end = xml.indexOf(close, gt + 1)
        if (end < 0) return null
        return xml.substring(gt + 1, end)
    }

    /** 提取第一个 `<link>` 的 href 属性值。 */
    private fun extractLinkHref(entryXml: String): String? {
        val idx = entryXml.indexOf("<link")
        if (idx < 0) return null
        val hrefIdx = entryXml.indexOf("href=\"", idx)
        if (hrefIdx < 0) return null
        val start = hrefIdx + "href=\"".length
        val end = entryXml.indexOf('"', start)
        if (end < 0) return null
        return entryXml.substring(start, end)
    }

    /** 提取正文内容，优先 `content`，回退 `summary`。 */
    private fun extractContent(entryXml: String): String? {
        val content = extractTagValue(entryXml, "content")
        if (!content.isNullOrBlank()) return content
        return extractTagValue(entryXml, "summary")
    }

    /** 从 `/releases/tag/<tag>` 链接中解析版本标签。 */
    private fun parseTagFromReleaseLink(url: String): String? {
        val marker = "/releases/tag/"
        val idx = url.indexOf(marker)
        if (idx < 0) return null
        val tag = url.substring(idx + marker.length).trim().trim('/')
        return tag.takeIf { it.isNotBlank() }
    }

    /** 去除 CDATA 包裹与 HTML 标签，并将 <br>/<p> 转换为换行。 */
    private fun stripHtml(s: String): String {
        return s
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]*>"), "")
    }
}
