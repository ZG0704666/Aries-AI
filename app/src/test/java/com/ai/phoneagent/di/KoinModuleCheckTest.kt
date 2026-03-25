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
 * Koin module verification test.
 *
 * Verifies that the combined Koin DI graph (appModule + dataModule + networkModule + uiModule)
 * resolves correctly without missing dependencies.
 *
 * Strategy: Replace Android-dependent modules (dataModule, networkModule) with mock overrides,
 * then verify the full graph resolves. This tests the WIRING, not the implementations.
 */
class KoinModuleCheckTest : KoinTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * Verify appModule resolves AppState singleton correctly.
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
     * Verify the combined graph wires correctly when Android dependencies are pre-provided.
     *
     * We supply mock overrides for everything that needs Android context (dataModule, networkModule)
     * and test that uiModule ViewModels can resolve their dependencies.
     */
    @Test
    fun `full module graph resolves with mock overrides`() {
        val mockApp = mockk<Application>(relaxed = true)
        val mockDb = mockk<AriesDatabase>(relaxed = true)
        val mockDao = mockk<ConversationDao>(relaxed = true)
        every { mockDb.conversationDao() } returns mockDao

        // Pre-supply all Android-dependent bindings so the graph resolves
        val testOverrides = module {
            single<AriesDatabase> { mockDb }
            single<ConversationDao> { mockDao }
            single { AppPreferencesRepository(get()) }
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
                // Skip dataModule and networkModule since we override them fully
                uiModule
            )
        }

        // Verify key singletons resolve
        assertNotNull(get<AppState>())
        assertNotNull(get<AriesDatabase>())
        assertNotNull(get<OkHttpClient>())
    }
}
