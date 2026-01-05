package com.ai.phoneagent.updates

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("id") val id: Long,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("draft") val draft: Boolean,
    @SerializedName("prerelease") val prerelease: Boolean,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("assets") val assets: List<GitHubReleaseAsset> = emptyList(),
)

data class GitHubReleaseAsset(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)

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
