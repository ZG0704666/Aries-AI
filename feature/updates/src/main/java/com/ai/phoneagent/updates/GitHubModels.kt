package com.ai.phoneagent.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub Release 接口返回的单个发布记录。 */
@Serializable
data class GitHubRelease(
    @SerialName("id") val id: Long,
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String?,
    @SerialName("body") val body: String?,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("draft") val draft: Boolean,
    @SerialName("prerelease") val prerelease: Boolean,
    @SerialName("published_at") val publishedAt: String?,
    @SerialName("assets") val assets: List<GitHubReleaseAsset> = emptyList(),
)

/** 发布记录中的附件（如 APK 安装包）。 */
@Serializable
data class GitHubReleaseAsset(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/** 面向 UI 展示整理后的发布条目。 */
@Serializable
data class ReleaseEntry(
    val versionTag: String,
    val version: String,
    val title: String,
    val date: String,
    val isPrerelease: Boolean,
    val body: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkAssetId: Long?,
)
