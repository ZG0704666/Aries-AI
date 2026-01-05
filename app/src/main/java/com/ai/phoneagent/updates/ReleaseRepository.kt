package com.ai.phoneagent.updates

class ReleaseRepository(
    private val owner: String = UpdateConfig.REPO_OWNER,
    private val repo: String = UpdateConfig.REPO_NAME,
) {
    private val service = GitHubApiClient.releaseService

    suspend fun fetchReleasePage(page: Int, perPage: Int): Result<List<ReleaseEntry>> {
        return runCatching {
            val releases = service.listReleases(owner = owner, repo = repo, page = page, perPage = perPage)
            releases
                .asSequence()
                .filter { !it.draft }
                .map { r ->
                    val versionTag = r.tagName
                    val version = versionTag.removePrefix("v")
                    val title = r.name?.takeIf { it.isNotBlank() } ?: "版本更新"
                    val date = r.publishedAt?.take(10) ?: ""

                    val apkAsset = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                    ReleaseEntry(
                        versionTag = versionTag,
                        version = version,
                        title = title,
                        date = date,
                        isPrerelease = r.prerelease,
                        body = r.body ?: "",
                        releaseUrl = r.htmlUrl,
                        apkUrl = apkAsset?.browserDownloadUrl,
                        apkAssetId = apkAsset?.id,
                    )
                }
                .toList()
        }
    }

    suspend fun fetchLatestRelease(includePrerelease: Boolean): Result<ReleaseEntry?> {
        return runCatching {
            var page = 1
            while (page <= 3) {
                val entries = fetchReleasePage(page = page, perPage = 10).getOrElse { throw it }
                val candidate = entries.firstOrNull { e -> includePrerelease || !e.isPrerelease }
                if (candidate != null) return@runCatching candidate
                page += 1
            }
            null
        }
    }
}
