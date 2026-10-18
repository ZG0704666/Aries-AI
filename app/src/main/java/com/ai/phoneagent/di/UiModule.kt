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

import com.ai.phoneagent.viewmodel.AboutViewModel
import com.ai.phoneagent.viewmodel.AppearanceViewModel
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.ChatViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import com.ai.phoneagent.viewmodel.UpdateHistoryViewModel
import com.ai.phoneagent.core.tools.ToolRegistration
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * UI 层 Koin DI 模块。
 *
 * 绑定：
 * - [ChatViewModel] — 聊天附件管理的 AndroidViewModel。
 *
 * AboutViewModel / UpdateHistoryViewModel 通过 `get()` 从 [appModule] 解析
 * [com.ai.phoneagent.updates.ReleaseRepository]，而非直接实例化。
 */
val uiModule = module {

    // ChatViewModel 是 AndroidViewModel；Koin 的 viewModel {} 块会自动处理 Application 参数。
    viewModel { ChatViewModel(get()) }
    viewModel {
        AutomationViewModel(
            application = get(),
            appPrefsRepository = get(),
            automationResultsRepository = get(),
            toolRegistration = get<ToolRegistration>(),
            liveNotification = get(),
            automationOverlay = get(),
            appPackageManager = get(),
            screenshotOverlayGuard = get(),
            promptTemplates = get(),
            localMnnInferenceEngine = get(),
            modelScopeModelDownloader = get(),
            virtualScreenPreviewOverlay = get(),
        )
    }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { AppearanceViewModel(get(), get()) }
    viewModel { AboutViewModel(get(), get(), get()) }
    viewModel { UpdateHistoryViewModel(get(), get()) }
}
