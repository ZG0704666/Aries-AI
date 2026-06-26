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
package com.ai.phoneagent.core.executor

import android.content.Context
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.config.AgentConfiguration

/**
 * 动作执行路由器
 *
 * 将不同类型的动作分发到对应的执行器。
 * 这是一个路由层，实际执行逻辑仍由 [ActionExecutor] 处理。
 * 后续可逐步将各执行方法提取为独立的执行器类。
 */
class ActionExecutorRouter(
    private val context: Context,
    private val config: AgentConfiguration = AgentConfiguration.DEFAULT,
) {
    private val actionExecutor = ActionExecutor(context, config)

    /**
     * 执行动作
     * 委托给 ActionExecutor 处理
     */
    suspend fun execute(
        action: ParsedAgentAction,
        service: PhoneAgentAccessibilityService?,
        uiDump: String,
        screenW: Int,
        screenH: Int,
        onLog: (String) -> Unit
    ): Boolean {
        return actionExecutor.execute(action, service, uiDump, screenW, screenH, onLog)
    }

    /**
     * 重置会话状态
     */
    fun resetSessionState() {
        actionExecutor.resetSessionState()
    }

    /**
     * 获取底层 ActionExecutor（过渡期使用，后续应移除）
     */
    fun getActionExecutor(): ActionExecutor = actionExecutor
}
