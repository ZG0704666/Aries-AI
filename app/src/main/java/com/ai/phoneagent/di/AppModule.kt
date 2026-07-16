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

import com.ai.phoneagent.AppState
import com.ai.phoneagent.core.automation.ActivityAutomationInstructionGateway
import com.ai.phoneagent.core.automation.AutomationDispatchAuthenticator
import com.ai.phoneagent.core.automation.AutomationInstructionGateway
import com.ai.phoneagent.core.tools.extended.ExtendedAppMapping
import com.ai.phoneagent.core.templates.PromptTemplates
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.AppPackageManager
import com.ai.phoneagent.core.tools.ToolRegistration
import com.ai.phoneagent.core.tools.file.FileToolExecutor
import com.ai.phoneagent.permissions.ToolPermissionSystem
import com.ai.phoneagent.vdiso.ShizukuVirtualDisplayEngine
import com.ai.phoneagent.input.InputHelper
import com.ai.phoneagent.VirtualScreenPreviewOverlay
import com.ai.phoneagent.AutomationLiveNotification
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.core.cache.ScreenshotOverlayGuard
import com.ai.phoneagent.net.AriesOidcAuthManager
import com.ai.phoneagent.net.LocalMnnInferenceEngine
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.telemetry.TelemetryHeartbeatManager
import com.ai.phoneagent.ui.UIAutomationProgressOverlay
import com.ai.phoneagent.updates.ReleaseRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Application-level Koin DI module.
 *
 * Binds global application state singletons:
 * - [AppState] — global context holder (Kotlin object; bound so it can be injected or resolved via Koin)
 *
 * Note: ConversationManager and ConversationTranscriptState bindings are reserved here
 * and will be activated once those classes are introduced in subsequent migration tasks (T3+).
 *
 * This module is DEFINITION ONLY — existing call sites are not changed until T7.
 */
val appModule = module {

    // AppState is a Kotlin object (singleton); bind it so Koin-injected code can resolve it.
    single { AppState }
    single { AriesOidcAuthManager(androidApplication(), get()) }
    single { TelemetryHeartbeatManager(androidApplication(), get(), get(), get()) }

    // ReleaseRepository — fetches GitHub release metadata for in-app updates.
    // Constructor params (owner/repo) default to UpdateConfig.REPO_OWNER/REPO_NAME.
    single { ReleaseRepository() }

    // Task 19 Phase 1: object → class DI migration
    single { ExtendedAppMapping() }
    single { PromptTemplates() }
    // Task 19 Phase 2: 工具执行器链迁移
    single { FileToolExecutor(androidContext()) }
    single { AIToolHandler(androidContext()) }
    single { ToolPermissionSystem(androidContext(), get(), get()) }

    // Task 19 Phase 3: 虚拟屏集群迁移
    single { ShizukuVirtualDisplayEngine() }
    single { InputHelper() }
    single { VirtualScreenPreviewOverlay(get()) }

    // Task 19 Phase 4: object → class DI 迁移
    single { AppPackageManager(get()) }

    // Task 19 Phase 4: 本地推理引擎 object → class DI 迁移
    single { LocalMnnInferenceEngine(get()) }

    // Task 19 Phase 4: ModelScope 模型下载器迁移
    single { ModelScopeModelDownloader() }

    // Guards the automation dispatch entrypoint with a per-process token.
    single { AutomationDispatchAuthenticator() }

    // Task 19 Phase 4: 自动化指令网关 object → class DI 迁移
    single { ActivityAutomationInstructionGateway(get()) }
    single<AutomationInstructionGateway> { get<ActivityAutomationInstructionGateway>() }

    single { AutomationLiveNotification(androidContext()) }
    single { AutomationOverlay(get()) }
    single { UIAutomationProgressOverlay(androidContext()) }
    single { ScreenshotOverlayGuard(get(), get()) }

    single {
        ToolRegistration(
            handler = get(),
            context = androidContext(),
            fileToolExecutor = get(),
            networkToolExecutor = get(),
            appPackageManager = get(),
            extendedAppMapping = get(),
        )
    }

    // TODO(T3): Uncomment once ConversationManager is extracted from object/singleton:
    // single { ConversationManager(get()) }

    // TODO(T3): Uncomment once ConversationTranscriptState is introduced:
    // single { ConversationTranscriptState() }
}
