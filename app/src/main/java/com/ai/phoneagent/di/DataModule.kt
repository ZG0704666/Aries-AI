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

import androidx.room.Room
import com.ai.phoneagent.data.local.AriesDatabase
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.AutomationResultsRepository
import com.ai.phoneagent.data.preferences.FloatingChatPreferencesRepository
import com.ai.phoneagent.data.preferences.MainUiPreferencesRepository
import com.ai.phoneagent.data.preferences.TelemetryPreferencesRepository
import com.ai.phoneagent.data.preferences.ToolPermissionsRepository
import com.ai.phoneagent.data.preferences.VirtualDisplayConfigRepository
import com.ai.phoneagent.data.security.AndroidKeyStoreSecretStore
import com.ai.phoneagent.data.security.SecretStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 数据层 Koin DI 模块。
 *
 * 绑定：
 * - [AriesDatabase] — Room 数据库单例（委托给既有的 [AriesDatabase.getInstance]）
 * - [com.ai.phoneagent.data.local.ConversationDao] — 从数据库解析
 * - [MainUiPreferencesRepository] — 基于 DataStore 的偏好仓库
 *
 * 本模块仅为定义——在 T7 之前不改动既有调用点。
 */
val dataModule = module {

    // Room 数据库 — 委托给线程安全的 getInstance()，避免重复初始化。
    single<AriesDatabase> {
        AriesDatabase.getInstance(androidContext())
    }

    // 通过数据库单例解析会话 DAO。
    single { get<AriesDatabase>().conversationDao() }

    // DataStore 偏好仓库 — 均需应用级 Context。
    single { MainUiPreferencesRepository(androidContext()) }
    single<SecretStore> { AndroidKeyStoreSecretStore() }
    single { AppPreferencesRepository(androidContext(), get()) }
    single { FloatingChatPreferencesRepository(androidContext()) }
    single { VirtualDisplayConfigRepository(androidContext()) }
    single { ToolPermissionsRepository(androidContext()) }
    single { AutomationResultsRepository(androidContext()) }
    single { TelemetryPreferencesRepository(androidContext()) }
}
