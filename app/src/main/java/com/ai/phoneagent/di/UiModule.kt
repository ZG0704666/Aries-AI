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

import com.ai.phoneagent.viewmodel.AppearanceViewModel
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.ChatViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * UI-layer Koin DI module.
 *
 * Binds:
 * - [ChatViewModel] — AndroidViewModel for chat attachment management.
 *
 * This module is DEFINITION ONLY — existing call sites are not changed until T7.
 */
val uiModule = module {

    // ChatViewModel is an AndroidViewModel; Koin's viewModel {} block handles
    // the Application parameter automatically.
    viewModel { ChatViewModel(get()) }
    viewModel { AutomationViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { AppearanceViewModel(get(), get()) }
    viewModel { com.ai.phoneagent.viewmodel.AboutViewModel(get(), com.ai.phoneagent.updates.ReleaseRepository(), get()) }
    viewModel { com.ai.phoneagent.viewmodel.UpdateHistoryViewModel(get(), com.ai.phoneagent.updates.ReleaseRepository()) }
}
