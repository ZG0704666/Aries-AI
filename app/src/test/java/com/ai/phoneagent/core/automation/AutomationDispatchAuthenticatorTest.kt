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
package com.ai.phoneagent.core.automation

import android.content.Intent
import com.ai.phoneagent.viewmodel.AutomationViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AutomationDispatchAuthenticator] 回归测试。
 *
 * 覆盖 PR-B 阻断项 #4 的修复：纯展示字段 forceTop / keepMain 移出鉴权范围后，
 * 三个内部入口（AutomationOverlay / AutomationLiveNotification /
 * FloatingChatService.navigateToTaskDetail）只附带展示字段时不再被拒绝；
 * 同时确认真正的控制字段 task / autoStart / source 仍需 token，无 token 仍拒绝。
 */
class AutomationDispatchAuthenticatorTest {

    private val authenticator = AutomationDispatchAuthenticator()

    @Test
    fun `null intent 视为无需鉴权_应通过`() {
        assertTrue(authenticator.isAuthorized(null))
    }

    @Test
    fun `无任何 automation extras_应通过`() {
        val intent = Intent().apply { putExtra("unrelated", "value") }
        assertTrue(authenticator.isAuthorized(intent))
    }

    // ── 纯展示字段移出鉴权范围（阻断项 #4 核心）──

    @Test
    fun `仅附带 forceTop_应通过_无须 token`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
        }
        assertTrue("forceTop 移出鉴权范围后，内部入口应无须 token 即通过",
            authenticator.isAuthorized(intent))
    }

    @Test
    fun `仅附带 keepMainOnTop_应通过_无须 token`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_KEEP_MAIN_ON_TOP, true)
        }
        assertTrue("keepMain 移出鉴权范围后，内部入口应无须 token 即通过",
            authenticator.isAuthorized(intent))
    }

    @Test
    fun `forceTop 与 keepMain 同时附带_应通过_无须 token`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
            putExtra(AutomationViewModel.EXTRA_KEEP_MAIN_ON_TOP, true)
        }
        assertTrue(authenticator.isAuthorized(intent))
    }

    // ── 真正的控制字段仍需 token ──

    @Test
    fun `附带 task 但无 token_应拒绝`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "打开设置")
        }
        assertFalse(authenticator.isAuthorized(intent))
    }

    @Test
    fun `附带 autoStart 但无 token_应拒绝`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START, true)
        }
        assertFalse(authenticator.isAuthorized(intent))
    }

    @Test
    fun `附带 source 但无 token_应拒绝`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE, "notification")
        }
        assertFalse(authenticator.isAuthorized(intent))
    }

    @Test
    fun `附带 task 且 token 正确_应通过`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "打开设置")
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_DISPATCH_TOKEN, authenticator.token())
        }
        assertTrue(authenticator.isAuthorized(intent))
    }

    @Test
    fun `附带 task 但 token 错误_应拒绝`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "打开设置")
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_DISPATCH_TOKEN, "wrong-token")
        }
        assertFalse(authenticator.isAuthorized(intent))
    }

    // ── 混合场景：控制字段 + 展示字段同时存在时，token 鉴权仍作用于控制字段 ──

    @Test
    fun `task 与 forceTop 同时存在_无 token 应拒绝`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "打开设置")
            putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
        }
        assertFalse("控制字段 task 存在时仍需 token，forceTop 不豁免它",
            authenticator.isAuthorized(intent))
    }

    @Test
    fun `task 与 forceTop 同时存在_token 正确应通过`() {
        val intent = Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "打开设置")
            putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_DISPATCH_TOKEN, authenticator.token())
        }
        assertTrue(authenticator.isAuthorized(intent))
    }

    // ── hasAutomationControlExtras 鉴权范围边界 ──

    @Test
    fun `hasAutomationControlExtras 仅对控制字段返回 true`() {
        assertFalse(authenticator.hasAutomationControlExtras(Intent()))
        assertFalse(authenticator.hasAutomationControlExtras(Intent().apply {
            putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
        }))
        assertFalse(authenticator.hasAutomationControlExtras(Intent().apply {
            putExtra(AutomationViewModel.EXTRA_KEEP_MAIN_ON_TOP, true)
        }))
        assertTrue(authenticator.hasAutomationControlExtras(Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK, "x")
        }))
        assertTrue(authenticator.hasAutomationControlExtras(Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START, true)
        }))
        assertTrue(authenticator.hasAutomationControlExtras(Intent().apply {
            putExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE, "x")
        }))
    }
}
