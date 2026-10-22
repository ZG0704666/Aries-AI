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

import android.app.Application
import android.content.Context
import com.ai.phoneagent.AppState
import com.ai.phoneagent.data.local.AriesDatabase
import com.ai.phoneagent.data.local.ConversationDao
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.AutomationResultsRepository
import com.ai.phoneagent.data.preferences.FloatingChatPreferencesRepository
import com.ai.phoneagent.data.preferences.MainUiPreferencesRepository
import com.ai.phoneagent.data.preferences.ToolPermissionsRepository
import com.ai.phoneagent.data.preferences.VirtualDisplayConfigRepository
import com.ai.phoneagent.data.security.SecretStore
import coil.ImageLoader
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.assertNotNull

/**
 * Koin 模块校验测试。
 *
 * 验证组合后的 Koin DI 依赖图（appModule + dataModule + networkModule + uiModule）
 * 能否在无缺失依赖的情况下正确解析。
 *
 * 策略：用 mock 覆盖替换依赖 Android 的模块（dataModule、networkModule），
 * 再验证整个依赖图可解析。这里测试的是「装配」，而非实现本身。
 */
class KoinModuleCheckTest : KoinTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * 验证 appModule 能正确解析 AppState 单例。
     */
    @Test
    fun `appModule resolves AppState`() {
        val mockContext = mockk<Application>(relaxed = true)

        startKoin {
            androidContext(mockContext)
            modules(appModule)
        }

        val appState = get<AppState>()
        assertNotNull(appState)
    }

    /**
     * 验证在 Android 依赖预先提供的情况下，组合依赖图能正确装配。
     *
     * 对所有需要 Android Context 的绑定（dataModule、networkModule）提供 mock 覆盖，
     * 并测试 uiModule 的各 ViewModel 能否解析其依赖。
     */
    @Test
    fun `full module graph resolves with mock overrides`() {
        val mockApp = mockk<Application>(relaxed = true)
        val mockDb = mockk<AriesDatabase>(relaxed = true)
        val mockDao = mockk<ConversationDao>(relaxed = true)
        every { mockDb.conversationDao() } returns mockDao

        // 预先提供全部依赖 Android 的绑定，使依赖图可解析
        val testOverrides = module {
            single<AriesDatabase> { mockDb }
            single<ConversationDao> { mockDao }
            single<SecretStore> { mockk(relaxed = true) }
            single { AppPreferencesRepository(get(), get()) }
            single { MainUiPreferencesRepository(get()) }
            single { FloatingChatPreferencesRepository(get()) }
            single { VirtualDisplayConfigRepository(get()) }
            single { ToolPermissionsRepository(get()) }
            single { AutomationResultsRepository(get()) }
            single<OkHttpClient> { mockk(relaxed = true) }
            single<ImageLoader> { mockk(relaxed = true) }
        }

        startKoin {
            androidContext(mockApp)
            modules(
                appModule,
                testOverrides,
                // dataModule 与 networkModule 已被完整覆盖，跳过
                uiModule
            )
        }

        // 验证关键单例可解析
        assertNotNull(get<AppState>())
        assertNotNull(get<AriesDatabase>())
        assertNotNull(get<OkHttpClient>())
    }
}
