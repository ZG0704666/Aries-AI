package com.ai.phoneagent.ui.inputbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 语音会话状态机六场景验收（P0-1）：
 * 1. 无 partial 松手  2. 有 partial 松手  3. 取消
 * 4. 权限拒绝         5. final 不返回触发看门狗  6. 旧会话回调不得污染新会话
 */
class VoiceSessionControllerTest {

    private lateinit var controller: VoiceSessionController

    @Before
    fun setUp() {
        controller = VoiceSessionController()
    }

    private fun beginSession(id: Long = 1L) {
        controller.begin(id)
    }

    /** 场景 1：无 partial 松手 —— 录音态直接进入收尾并标记发送。 */
    @Test
    fun `release without partial moves to finalizing and marks send`() {
        beginSession()
        assertTrue(controller.onVoiceEnd(1L))
        assertTrue(controller.state is InputState.VoiceFinalizing)
        assertTrue(controller.consumePendingSend())
        assertFalse(controller.consumePendingSend()) // 只消费一次
    }

    /** 场景 2：有 partial 松手 —— 识别态仍可进入收尾（修复的关键回归）。 */
    @Test
    fun `release after partial moves to finalizing and marks send`() {
        beginSession()
        assertTrue(controller.onPartialResult(1L))
        assertTrue(controller.state is InputState.VoiceRecognizing)
        assertTrue(controller.onVoiceEnd(1L))
        assertTrue(controller.state is InputState.VoiceFinalizing)
        assertTrue(controller.consumePendingSend())
    }

    /** partial 后上滑仍进入取消态，松手由手势层走取消而非发送。 */
    @Test
    fun `cancel feedback remains available after partial result`() {
        beginSession()
        assertTrue(controller.onPartialResult(1L))
        assertTrue(controller.onUpdateCancelState(true))
        val state = controller.state as InputState.VoiceRecognizing
        assertTrue(state.isCancelling)
        assertTrue(controller.onVoiceCancel(1L))
        assertFalse(controller.consumePendingSend())
    }

    /** 已进入取消区后收到 partial，不得把红色取消反馈覆盖掉。 */
    @Test
    fun `partial result preserves active cancel feedback`() {
        beginSession()
        assertTrue(controller.onUpdateCancelState(true))
        assertTrue(controller.onPartialResult(1L))
        val state = controller.state as InputState.VoiceRecognizing
        assertTrue(state.isCancelling)
    }

    /** 松手进入收尾后，迟到的拖动事件不得重新打开取消态。 */
    @Test
    fun `finalizing rejects stale drag updates`() {
        beginSession()
        assertTrue(controller.onVoiceEnd(1L))
        assertFalse(controller.onUpdateCancelState(true))
        assertTrue(controller.state is InputState.VoiceFinalizing)
    }

    /** 场景 3：上滑取消 —— 复位为 Idle、不发送。 */
    @Test
    fun `cancel resets to idle and never sends`() {
        beginSession()
        controller.onPartialResult(1L)
        assertTrue(controller.onVoiceCancel(1L))
        assertTrue(controller.state is InputState.Idle)
        assertFalse(controller.consumePendingSend())
    }

    /** 场景 4：权限拒绝 —— 会话清空后迟到的抬手/回调不再生效。 */
    @Test
    fun `permission denied clears session so late callbacks are ignored`() {
        beginSession()
        // 权限结果处理：清除会话与发送标记（对应 clearVoiceSession + consumePendingSend）
        controller.consumePendingSend()
        controller.clearSession()
        assertFalse(controller.isActive(1L))
        assertFalse(controller.onVoiceEnd(1L))
        assertFalse(controller.state is InputState.VoiceFinalizing)
        assertFalse(controller.shouldResetToIdle(1L))
    }

    /** 场景 5：final 不返回 —— 收尾态允许看门狗兜底复位。 */
    @Test
    fun `watchdog can reset stuck finalizing session`() {
        beginSession()
        controller.onVoiceEnd(1L)
        assertTrue(controller.shouldResetToIdle(1L))
        controller.resetToIdle()
        assertTrue(controller.state is InputState.Idle)
        assertFalse(controller.consumePendingSend())
        // 复位后迟到的 final 不应再次复位 UI
        assertFalse(controller.shouldResetToIdle(1L))
    }

    /** 场景 6：旧会话回调不得污染新会话。 */
    @Test
    fun `stale session callbacks do not pollute new session`() {
        beginSession(1L)
        controller.clearSession(1L)
        beginSession(2L)
        assertTrue(controller.isActive(2L))
        // 旧会话 id 的回调一律拒绝
        assertFalse(controller.onPartialResult(1L))
        assertFalse(controller.onVoiceEnd(1L))
        assertFalse(controller.onVoiceCancel(1L))
        assertFalse(controller.shouldResetToIdle(1L))
        assertTrue(controller.state is InputState.VoiceRecording)
        // 新会话仍然正常工作
        assertTrue(controller.onPartialResult(2L))
        assertTrue(controller.onVoiceEnd(2L))
        assertTrue(controller.state is InputState.VoiceFinalizing)
    }

    /** 补充：begin 重置发送标记，防止上一会话的松手标记泄漏到新会话。 */
    @Test
    fun `begin resets pending send flag`() {
        beginSession(1L)
        controller.onVoiceEnd(1L)
        assertTrue(controller.consumePendingSend())
        beginSession(2L)
        assertFalse(controller.consumePendingSend())
        assertEquals(2L, controller.activeSessionId)
    }

    /** 补充：clearSession 带 expectedSessionId 时只清除匹配会话。 */
    @Test
    fun `clearSession with mismatched id keeps active session`() {
        beginSession(2L)
        controller.clearSession(1L)
        assertTrue(controller.isActive(2L))
        controller.clearSession(2L)
        assertFalse(controller.isActive(2L))
    }
}
