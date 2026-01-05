package com.ai.phoneagent.updates

import com.ai.phoneagent.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GitHubApiClient {
    private const val BASE_URL = "https://api.github.com/"

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

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val releaseService: GitHubReleaseService by lazy {
        retrofit.create(GitHubReleaseService::class.java)
    }
}
