package com.ai.phoneagent.updates

import com.ai.phoneagent.core.common.AppJson
import com.ai.phoneagent.feature.updates.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

object GitHubApiClient {
    private const val BASE_URL = "https://api.github.com"

    private fun buildAuthHeader(token: String): String? {
        val t = token.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("github_pat_")) {
            "Bearer $t"
        } else {
            "token $t"
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logger =
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }

        val headerInterceptor = Interceptor { chain ->
            val reqBuilder = chain.request().newBuilder()
                .header("User-Agent", "PhoneAgent")
                .header("Accept", "application/vnd.github+json")

            buildAuthHeader(BuildConfig.GITHUB_TOKEN)?.let { auth ->
                reqBuilder.header("Authorization", auth)
            }

            chain.proceed(reqBuilder.build())
        }

        OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(logger)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun listReleases(owner: String, repo: String, page: Int, perPage: Int): List<GitHubRelease> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/repos/$owner/$repo/releases?page=$page&per_page=$perPage"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} fetching releases")
                }
                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                AppJson.decodeFromString<List<GitHubRelease>>(body)
            }
        }
}
