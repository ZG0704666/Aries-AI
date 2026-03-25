/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.di

import com.ai.phoneagent.net.AutoGlmClient
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import com.ai.phoneagent.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Network-layer Koin DI module.
 *
 * Binds:
 * - [OkHttpClient] — shared HTTP client with connection pooling, timeouts, and optional logging.
 * - [AutoGlmClient] — Kotlin object client for GLM API; bound so it can be injected/mocked in tests.
 *
 * Note: [com.ai.phoneagent.net.OpenAICompatibleProvider] requires runtime parameters
 * (apiKey, baseUrl, modelName) that are user-provided, so it is intentionally NOT registered
 * as a static singleton here. Call sites construct it directly or pass params via Koin parameters.
 * A factory binding will be added in T7 when the full call-site migration happens.
 *
 * This module is DEFINITION ONLY — existing call sites are not changed until T7.
 */
val networkModule = module {

    /**
     * Shared OkHttpClient singleton — mirrors the configuration from [AutoGlmClient]'s
     * internal SharedHttpClient. Using a Koin-managed instance allows tests to swap it out.
     */
    single<OkHttpClient> {
        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    // AutoGlmClient is a Kotlin object (singleton); bind it so it can be injected or mocked in tests.
    single { AutoGlmClient }

    /**
     * Coil ImageLoader singleton for Compose image loading.
     * Configured with memory and disk caching for efficient attachment thumbnail loading.
     */
    single<ImageLoader> {
        ImageLoader.Builder(androidContext())
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(androidContext())
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(androidContext().cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

    // TODO(T7): Add factory for OpenAICompatibleProvider once runtime-param strategy is finalized:
    // factory { (apiKey: String, baseUrl: String, modelName: String) ->
    //     OpenAICompatibleProvider(apiKey = apiKey, baseUrl = baseUrl, modelName = modelName)
    // }
}
