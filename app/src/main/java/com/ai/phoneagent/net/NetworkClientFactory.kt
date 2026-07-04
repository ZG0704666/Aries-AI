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
package com.ai.phoneagent.net

import com.ai.phoneagent.BuildConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient 构建工厂。
 *
 * 把原本散落在 [AutoGlmClient.SharedHttpClient] 中的 builder 配置抽取为单一函数，
 * 让生产代码与单元测试共用同一份实现，避免测试维护"逻辑副本"导致回归被掩盖。
 *
 * - useFastTimeouts=false：标准超时配置，适配慢速模型响应
 * - useFastTimeouts=true：自动化场景短超时，让异常连接更快失败触发重试
 *
 * 日志级别由 BuildConfig.DEBUG 决定，与历史行为一致。
 */
internal fun buildNetworkClient(useFastTimeouts: Boolean): OkHttpClient {
    val logger = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val builder = OkHttpClient.Builder()
        .addInterceptor(logger)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

    if (useFastTimeouts) {
        builder
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
    } else {
        builder
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
    }

    return builder.build()
}
