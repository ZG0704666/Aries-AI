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
import com.ai.phoneagent.data.preferences.ToolPermissionsRepository
import com.ai.phoneagent.data.preferences.VirtualDisplayConfigRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Data-layer Koin DI module.
 *
 * Binds:
 * - [AriesDatabase] — Room database singleton (delegates to existing [AriesDatabase.getInstance])
 * - [com.ai.phoneagent.data.local.ConversationDao] — resolved from the database
 * - [MainUiPreferencesRepository] — DataStore-backed preferences repository
 *
 * This module is DEFINITION ONLY — existing call sites are not changed until T7.
 */
val dataModule = module {

    // Room database — delegates to the existing thread-safe getInstance() to avoid double-init.
    single<AriesDatabase> {
        AriesDatabase.getInstance(androidContext())
    }

    // Conversation DAO resolved through the database singleton.
    single { get<AriesDatabase>().conversationDao() }

    // DataStore preferences repositories — all require application context.
    single { MainUiPreferencesRepository(androidContext()) }
    single { AppPreferencesRepository(androidContext()) }
    single { FloatingChatPreferencesRepository(androidContext()) }
    single { VirtualDisplayConfigRepository(androidContext()) }
    single { ToolPermissionsRepository(androidContext()) }
    single { AutomationResultsRepository(androidContext()) }
}
