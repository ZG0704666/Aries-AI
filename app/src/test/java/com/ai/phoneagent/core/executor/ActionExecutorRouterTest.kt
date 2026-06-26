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
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.config.AgentConfiguration
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * [ActionExecutorRouter] 单元测试。
 *
 * 说明：
 * - Router 依赖 [Context] 与 [com.ai.phoneagent.PhoneAgentAccessibilityService]，
 *   完整的端到端执行需在 Android 设备/插桩环境下验证。
 * - 本测试使用 mockk 构造 relaxed Context，覆盖可在纯 JVM 运行的子集：
 *   1) 构造与基本结构（getActionExecutor / resetSessionState）
 *   2) 委托给 ActionExecutor 的、不触及 Android 运行时 API 的动作分支：
 *      finish（直接返回 true）、take_over（仅记日志返回 false）、
 *      null/未知动作名（直接返回 false）。
 * - 涉及无障碍服务、PackageManager、虚拟屏、Shizuku 的分支不在本测试覆盖范围，
 *   需通过 connectedAndroidTest 或集成测试验证。
 */
class ActionExecutorRouterTest {

    private lateinit var context: Context
    private lateinit var router: ActionExecutorRouter

    @Before
    fun setUp() {
        // relaxed = true：未配置桩的方法返回默认值，避免构造期触发 Android API。
        context = mockk(relaxed = true)
        router = ActionExecutorRouter(context, AgentConfiguration.TEST)
    }

    // ========== 基本结构 ==========

    @Test
    fun `getActionExecutor returns non-null instance`() {
        val executor = router.getActionExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `resetSessionState does not throw`() {
        // 多次调用都不应抛异常
        router.resetSessionState()
        router.resetSessionState()
    }

    // ========== 委托给 ActionExecutor 的纯逻辑分支 ==========
    // 以下分支在 ActionExecutor.execute 内不调用 Android 运行时 API，可在 JVM 验证。

    @Test
    fun `execute finish action returns true`() = runBlocking {
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = "finish",
            fields = emptyMap()
        )
        val logs = mutableListOf<String>()
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = { logs += it }
        )
        assertTrue(result)
    }

    @Test
    fun `execute with null actionName returns false`() = runBlocking {
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = null,
            fields = emptyMap()
        )
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = {}
        )
        assertFalse(result)
    }

    @Test
    fun `execute unknown action returns false`() = runBlocking {
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = "totally_unknown_action",
            fields = emptyMap()
        )
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = {}
        )
        assertFalse(result)
    }

    @Test
    fun `execute take_over returns false and logs message`() = runBlocking {
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = "take_over",
            fields = mapOf("message" to "需要用户输入验证码")
        )
        val logs = mutableListOf<String>()
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = { logs += it }
        )
        assertFalse(result)
        // 应输出接管提示日志
        assertTrue(logs.any { it.contains("需要接管") })
        assertTrue(logs.any { it.contains("需要用户输入验证码") })
    }

    @Test
    fun `execute take_over without message uses default text`() = runBlocking {
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = "takeover",
            fields = emptyMap()
        )
        val logs = mutableListOf<String>()
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = { logs += it }
        )
        assertFalse(result)
        // 字段为空时应回退到默认文案
        assertTrue(logs.any { it.contains("User assistance required") })
    }

    @Test
    fun `execute finish with quoted name is normalized`() = runBlocking {
        // 模型输出常带引号/空格，验证归一化后仍命中 finish
        val action = ParsedAgentAction(
            metadata = "do",
            actionName = " \"Finish\" ",
            fields = emptyMap()
        )
        val result = router.execute(
            action = action,
            service = null,
            uiDump = "",
            screenW = 1080,
            screenH = 1920,
            onLog = {}
        )
        assertTrue(result)
    }
}
