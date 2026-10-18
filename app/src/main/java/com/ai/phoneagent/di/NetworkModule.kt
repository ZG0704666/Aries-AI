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
import com.ai.phoneagent.net.AriesApiClient
import com.ai.phoneagent.core.tools.network.NetworkToolExecutor
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import com.ai.phoneagent.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * 网络层 Koin DI 模块。
 *
 * 绑定：
 * - [OkHttpClient] — 共享 HTTP 客户端，带连接池、超时与可选日志。
 * - [AutoGlmClient] — Kotlin object 客户端；绑定后可在测试中注入/替换。
 *
 * 注意：[com.ai.phoneagent.net.OpenAICompatibleProvider] 需要运行时参数
 * （apiKey、baseUrl、modelName），均由用户提供，因此刻意不在静态单例中注册。
 * 调用点直接构造，或通过 Koin 参数传入。T7 完成调用点整体迁移时会补充 factory 绑定。
 *
 * 本模块仅为定义——在 T7 之前不改动既有调用点。
 */
val networkModule = module {

    /**
     * 共享 OkHttpClient 单例 — 配置与 [AutoGlmClient] 内部的 SharedHttpClient 保持一致。
     * 使用 Koin 托管实例后，测试可随时替换。
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

    // AutoGlmClient 是 Kotlin object（单例）；绑定后可在测试中注入或替换。
    single { AutoGlmClient }

    // 任务 19 第四阶段：AriesApiClient 由 object 迁移为 class（companion 外观 DI）。
    single { AriesApiClient() }

    // Task 19 Phase 2: 工具专用 OkHttpClient（30s 超时，与共享 client 60s/300s 区分）
    single(named("tool")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Task 19 Phase 2: NetworkToolExecutor 迁移
    single { NetworkToolExecutor(androidContext(), get(named("tool"))) }

    /**
     * Coil ImageLoader 单例，用于 Compose 图片加载。
     * 配置内存与磁盘缓存，保证附件缩略图加载高效。
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

    // TODO(T7)：待运行时参数策略确定后为 OpenAICompatibleProvider 补充 factory 绑定：
    // factory { (apiKey: String, baseUrl: String, modelName: String) ->
    //     OpenAICompatibleProvider(apiKey = apiKey, baseUrl = baseUrl, modelName = modelName)
    // }
}
