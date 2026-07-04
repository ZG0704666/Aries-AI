/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21B: ToolPermissionSystem 单元测试。
 *
 * 测试目标：覆盖 ToolPermissionSystem 的权限矩阵（ALLOW/CAUTION/FORBID/ASK）、
 * 主开关级别、工具级别覆盖、危险操作二次确认流程。
 *
 * 注意：
 * 1. ToolPermissionSystem 是 private 构造的单例，通过反射调用私有构造器实例化，
 *    绕过 getInstance 中的 Koin 依赖。
 * 2. 构造器内部调用 AIToolHandler.getInstance(context)，故需先初始化 AIToolHandler
 *    并注册测试用工具（dangerous_tool / safe_tool）以驱动 dangerCheck 分支。
 * 3. ToolPermissionsRepository 使用 mockk 模拟，避免 DataStore 对 Android Context 的依赖。
 * 4. 任务原始描述中的 listAllowedTools/listDangerousTools 在实际代码中不存在，
 *    已改为测试 getMasterPermissionLevel/setMasterPermissionLevel。
 */
package com.ai.phoneagent.permissions

import android.content.Context
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.ToolExecutor
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import com.ai.phoneagent.data.preferences.ToolPermissionsRepository
import com.ai.phoneagent.permissions.ToolPermissionSystem.PermissionLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ToolPermissionSystem] 单元测试。
 *
 * 覆盖：权限矩阵（ALLOW/CAUTION/FORBID/ASK）、主开关、工具级覆盖、
 * 危险操作二次确认、授权/撤销循环。
 */
class ToolPermissionSystemTest {

    private lateinit var mockContext: Context
    private lateinit var mockRepo: ToolPermissionsRepository
    private lateinit var system: ToolPermissionSystem

    @Before
    fun setup() {
        // 重置单例，保证测试隔离
        resetAIToolHandlerSingleton()
        resetToolPermissionSystemSingleton()

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        // 初始化 AIToolHandler 并注册测试工具
        val handler = AIToolHandler.getInstance(mockContext)
        handler.registerTool(
            name = "dangerous_tool",
            dangerCheck = { true },
            descriptionGenerator = { "危险操作：删除文件" },
            executor = successExecutor()
        )
        handler.registerTool(
            name = "safe_tool",
            dangerCheck = { false },
            descriptionGenerator = { "安全操作：读取文件" },
            executor = successExecutor()
        )

        // 模拟 ToolPermissionsRepository（默认 CAUTION）
        mockRepo = mockk(relaxed = true)
        every { mockRepo.getMasterSwitchBlocking() } returns "CAUTION"
        every { mockRepo.getToolPermissionBlocking(any()) } returns null

        // 通过反射调用私有构造器
        system = createSystem()
    }

    // ─── 单例重置 ───────────────────────────────────────────────────────

    /**
     * 通过反射重置 AIToolHandler 的单例 INSTANCE。
     * Kotlin 将 companion object 中的 private var 编译为外层类的 private static 字段，
     * 因此用 AIToolHandler::class.java.getDeclaredField("INSTANCE") + field.set(null, null)。
     */
    private fun resetAIToolHandlerSingleton() {
        val field = AIToolHandler::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    /**
     * 通过反射重置 ToolPermissionSystem 的单例 INSTANCE。
     * 同上，INSTANCE 是 ToolPermissionSystem 类上的 private static 字段。
     */
    private fun resetToolPermissionSystemSingleton() {
        val field = ToolPermissionSystem::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun createSystem(): ToolPermissionSystem {
        val constructor = ToolPermissionSystem::class.java.getDeclaredConstructor(
            Context::class.java,
            ToolPermissionsRepository::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(mockContext, mockRepo)
    }

    private fun successExecutor(): ToolExecutor = object : ToolExecutor {
        override suspend fun invoke(tool: AITool): ToolResult =
            ToolResult(tool.name, success = true, result = StringResultData("ok"))
    }

    // ─── 权限矩阵：主开关 ────────────────────────────────────────────────

    @Test
    fun `checkPermission_用户已授权工具_返回允许`() = runBlocking {
        every { mockRepo.getMasterSwitchBlocking() } returns "ALLOW"

        val result = system.checkPermission(AITool("safe_tool")) { _ ->
            throw AssertionError("ALLOW 级别不应触发确认")
        }

        assertTrue("ALLOW 级别应直接允许", result)
    }

    @Test
    fun `checkPermission_用户未授权工具_返回拒绝`() = runBlocking {
        every { mockRepo.getMasterSwitchBlocking() } returns "FORBID"

        val result = system.checkPermission(AITool("safe_tool")) { _ ->
            throw AssertionError("FORBID 级别不应触发确认")
        }

        assertFalse("FORBID 级别应直接拒绝", result)
    }

    @Test
    fun `checkPermission_dangerCheck=true_需要二次确认`() = runBlocking {
        every { mockRepo.getMasterSwitchBlocking() } returns "CAUTION"

        var confirmCalled = false
        var confirmDescription = ""

        val result = system.checkPermission(AITool("dangerous_tool")) { desc ->
            confirmCalled = true
            confirmDescription = desc
            true
        }

        assertTrue("危险操作应触发确认回调", confirmCalled)
        assertTrue("确认描述应包含危险操作信息", confirmDescription.contains("危险操作"))
        assertTrue("用户确认后应允许执行", result)
    }

    @Test
    fun `checkPermission_dangerCheck=false_直接执行`() = runBlocking {
        every { mockRepo.getMasterSwitchBlocking() } returns "CAUTION"

        var confirmCalled = false
        val result = system.checkPermission(AITool("safe_tool")) { _ ->
            confirmCalled = true
            true
        }

        assertFalse("非危险操作不应触发确认", confirmCalled)
        assertTrue("CAUTION 级别下非危险操作应直接允许", result)
    }

    // ─── 权限矩阵：工具级覆盖 ────────────────────────────────────────────

    @Test
    fun `checkPermission_工具级别FORBID_返回拒绝`() = runBlocking {
        // 主开关 ALLOW，但工具级 FORBID → 拒绝
        every { mockRepo.getMasterSwitchBlocking() } returns "ALLOW"
        every { mockRepo.getToolPermissionBlocking("blocked_tool") } returns "FORBID"

        val result = system.checkPermission(AITool("blocked_tool")) { _ ->
            throw AssertionError("FORBID 级别不应触发确认")
        }

        assertFalse("工具级 FORBID 应覆盖主开关 ALLOW", result)
    }

    @Test
    fun `grantPermission_授权后_再次检查应允许`() = runBlocking {
        // 初始：工具级 FORBID
        every { mockRepo.getToolPermissionBlocking("grant_tool") } returns "FORBID"
        val before = system.checkPermission(AITool("grant_tool")) { _ -> true }
        assertFalse("授权前应拒绝", before)

        // 授权
        system.setToolPermissionLevel("grant_tool", PermissionLevel.ALLOW)
        verify { mockRepo.setToolPermissionBlocking("grant_tool", "ALLOW") }

        // 更新 mock 反映授权后状态
        every { mockRepo.getToolPermissionBlocking("grant_tool") } returns "ALLOW"
        val after = system.checkPermission(AITool("grant_tool")) { _ ->
            throw AssertionError("ALLOW 级别不应触发确认")
        }
        assertTrue("授权后应允许", after)
    }

    @Test
    fun `revokePermission_撤销后_再次检查应拒绝`() = runBlocking {
        // 初始：工具级 ALLOW
        every { mockRepo.getToolPermissionBlocking("revoke_tool") } returns "ALLOW"
        val before = system.checkPermission(AITool("revoke_tool")) { _ ->
            throw AssertionError("ALLOW 级别不应触发确认")
        }
        assertTrue("撤销前应允许", before)

        // 撤销
        system.setToolPermissionLevel("revoke_tool", PermissionLevel.FORBID)
        verify { mockRepo.setToolPermissionBlocking("revoke_tool", "FORBID") }

        // 更新 mock 反映撤销后状态
        every { mockRepo.getToolPermissionBlocking("revoke_tool") } returns "FORBID"
        val after = system.checkPermission(AITool("revoke_tool")) { _ ->
            throw AssertionError("FORBID 级别不应触发确认")
        }
        assertFalse("撤销后应拒绝", after)
    }

    // ─── 权限级别查询 ───────────────────────────────────────────────────

    @Test
    fun `getToolPermissionLevel_新工具_继承主开关级别`() {
        every { mockRepo.getMasterSwitchBlocking() } returns "CAUTION"
        every { mockRepo.getToolPermissionBlocking("brand_new_tool") } returns null

        val level = system.getToolPermissionLevel("brand_new_tool")

        assertEquals("新工具应继承主开关级别", PermissionLevel.CAUTION, level)
    }

    @Test
    fun `getMasterPermissionLevel_默认为CAUTION`() {
        every { mockRepo.getMasterSwitchBlocking() } returns "CAUTION"

        val level = system.getMasterPermissionLevel()

        assertEquals("默认主开关应为 CAUTION", PermissionLevel.CAUTION, level)
    }

    @Test
    fun `setMasterPermissionLevel_设置后可读取`() {
        every { mockRepo.getMasterSwitchBlocking() } returns "ALLOW"

        system.setMasterPermissionLevel(PermissionLevel.ALLOW)

        verify { mockRepo.setMasterSwitchBlocking("ALLOW") }
        assertEquals("设置后应能读取到 ALLOW", PermissionLevel.ALLOW, system.getMasterPermissionLevel())
    }
}
