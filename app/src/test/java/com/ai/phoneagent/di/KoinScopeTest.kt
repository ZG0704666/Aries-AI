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
import com.ai.phoneagent.AppState
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.assertSame

/**
 * Koin scope and singleton verification test.
 *
 * Verifies that Koin singleton definitions are correctly enforced:
 * same instance is returned on multiple get() calls.
 */
class KoinScopeTest : KoinTest {

    @Before
    fun setUp() {
        startKoin {
            androidContext(mockk<Application>(relaxed = true))
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `AppState singleton returns same instance on repeated get`() {
        val instance1 = get<AppState>()
        val instance2 = get<AppState>()
        assertSame(instance1, instance2)
    }

    @Test
    fun `verify singleton scope behavior across multiple resolutions`() {
        val instances = List(5) { get<AppState>() }
        instances.forEach { instance ->
            assertSame(instances[0], instance)
        }
    }
}
