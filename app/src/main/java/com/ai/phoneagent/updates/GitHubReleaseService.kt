package com.ai.phoneagent.updates

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubReleaseService {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
    ): List<GitHubRelease>
}
