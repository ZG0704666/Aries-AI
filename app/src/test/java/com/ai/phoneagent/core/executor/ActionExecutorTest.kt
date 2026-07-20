/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21B: ActionExecutor 单元测试。
 *
 * 测试目标：覆盖 ActionExecutor.execute(...) 的动作分发逻辑。
 * 策略：使用 AgentConfiguration.DEFAULT（useBackgroundVirtualDisplay=false、
 * useShizukuInteraction=false）并传 service=null，使各动作走到
 * "无可用执行通道" 或参数校验的早退路径，通过 onLog 回调验证分发命中。
 *
 * 注意：AutomationOverlay 依赖 Handler(Looper.getMainLooper())，在
 * isReturnDefaultValues=true 的 mockable android jar 下构造器为 no-op，
 * 且 temporaryHide()/restoreVisibility() 因 container=null 直接 return，
 * 因此无需 mockkObject。
 */
package com.ai.phoneagent.core.executor

import android.content.Context
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.core.agent.ParsedAgentAction
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.tools.AppPackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ActionExecutor] 单元测试。
 *
 * 覆盖 execute() 的分发逻辑（launch/tap/type/swipe/back/home/wait）以及
 * 参数缺失/参数类型错误/未知动作的错误路径。
 */
class ActionExecutorTest {

    private lateinit var mockContext: Context
    private lateinit var automationOverlay: AutomationOverlay
    private lateinit var appPackageManager: AppPackageManager
    private lateinit var executor: ActionExecutor
    private val logs = mutableListOf<String>()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        automationOverlay = mockk(relaxed = true)
        appPackageManager = mockk(relaxed = true)
        executor =
            ActionExecutor(
                mockContext,
                AgentConfiguration.DEFAULT,
                automationOverlay,
                appPackageManager,
            )
        logs.clear()
    }

    @After
    fun tearDown() {
        logs.clear()
    }

    private fun onLog(msg: String) {
        logs.add(msg)
    }

    private fun action(name: String, fields: Map<String, String> = emptyMap()): ParsedAgentAction =
        ParsedAgentAction(metadata = "do", actionName = name, fields = fields)

    private suspend fun runExecute(
        name: String,
        fields: Map<String, String> = emptyMap(),
        screenW: Int = 1080,
        screenH: Int = 1920
    ): Boolean = executor.execute(action(name, fields), null, "", screenW, screenH, ::onLog)

    // ─── 正向分发：验证各动作命中对应执行方法 ───────────────────────────

    @Test
    fun `execute_Launch动作_调用启动逻辑`() = runBlocking {
        every { appPackageManager.resolvePackageName(any()) } returns null
        every { appPackageManager.resolvePackageByLabel(any(), any()) } returns null
        every { appPackageManager.getAllInstalledApps() } returns emptyList()

        // 使用不含 '.' 的目标名，使 candidates 为空 → finalCandidates 为空 →
        // buildLaunchIntent 循环被跳过。这样避免 mockable android jar 下
        // Intent.addCategory() 返回 null 导致的 NPE，同时仍验证 launch 分发命中。
        // service=null 使 context.packageManager 路径可达但 pm 不会被实际使用。
        val result = runExecute("launch", mapOf("package" to "未安装应用"))

        // 分发到 executeLaunch：输出 "执行操作: launch(未安装应用)"，因无候选返回 false
        assertFalse("未安装应用应返回 false", result)
        assertTrue("应分发到 launch 逻辑", logs.any { it.contains("launch") })
    }

    @Test
    fun `execute_Tap动作_调用点击逻辑`() = runBlocking {
        // 提供 x/y 坐标，使分发到达 executeTap 的 onLog("执行操作: 点击(...)")
        val result = runExecute("tap", mapOf("x" to "100", "y" to "100"))

        assertFalse("无可用执行通道应返回 false", result)
        assertTrue("应分发到 tap 逻辑", logs.any { it.contains("点击") })
        assertTrue("应输出无可用执行通道", logs.any { it.contains("无法执行点击") })
    }

    @Test
    fun `execute_Type动作_调用输入逻辑`() = runBlocking {
        val result = runExecute("type", mapOf("text" to "hello"))

        // 分发到 executeType：service=null 且非 Shizuku/虚拟屏 → "无法执行输入：无可用执行通道"
        assertFalse("无可用执行通道应返回 false", result)
        assertTrue("应分发到 type 逻辑", logs.any { it.contains("无法执行输入") })
    }

    @Test
    fun `execute_Swipe动作_调用滑动逻辑`() = runBlocking {
        val result = runExecute(
            "swipe",
            mapOf("start" to "[100,100]", "end" to "[200,200]")
        )

        assertFalse("无可用执行通道应返回 false", result)
        assertTrue("应分发到 swipe 逻辑", logs.any { it.contains("滑动") })
        assertTrue("应输出无可用执行通道", logs.any { it.contains("无法执行滑动") })
    }

    @Test
    fun `execute_Back动作_调用返回逻辑`() = runBlocking {
        val result = runExecute("back")

        assertFalse("无可用执行通道应返回 false", result)
        assertTrue("应分发到 back 逻辑", logs.any { it.contains("返回") })
    }

    @Test
    fun `execute_Home动作_调用主页逻辑`() = runBlocking {
        val result = runExecute("home")

        assertFalse("无可用执行通道应返回 false", result)
        assertTrue("应分发到 home 逻辑", logs.any { it.contains("回到主页") })
    }

    @Test
    fun `execute_Wait动作_调用等待逻辑`() = runBlocking {
        // duration=1ms，快速返回 true
        val result = runExecute("wait", mapOf("duration" to "1ms"))

        assertTrue("wait 应返回 true", result)
        assertTrue("应分发到 wait 逻辑", logs.any { it.contains("wait") && it.contains("执行操作") })
    }

    // ─── 错误路径 ─────────────────────────────────────────────────────

    @Test
    fun `execute_未知动作_返回错误`() = runBlocking {
        val result = runExecute("foobar_unknown")

        assertFalse("未知动作应返回 false", result)
    }

    @Test
    fun `execute_参数缺失_返回错误`() = runBlocking {
        // Tap 缺少 element/point/pos/x/y → 在坐标解析阶段早退返回 false
        val result = runExecute("tap", emptyMap())

        assertFalse("参数缺失应返回 false", result)
    }

    @Test
    fun `execute_参数类型错误_返回错误`() = runBlocking {
        // element 传非坐标字符串（如 "invalid"），parsePoint 返回 null → 早退
        val result = runExecute("tap", mapOf("element" to "invalid_string"))

        assertFalse("参数类型错误应返回 false", result)
    }
}
