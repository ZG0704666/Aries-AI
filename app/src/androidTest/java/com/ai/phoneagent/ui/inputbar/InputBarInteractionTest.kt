package com.ai.phoneagent.ui.inputbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI 测试：InputBar 关键交互（5 个场景）。
 *
 * 测试 MainActivity 底部输入栏的核心用户交互，验证：
 * 1. 空闲状态显示输入提示
 * 2. 已输入文本在折叠态正确展示
 * 3. 附件按钮点击触发回调
 * 4. 有文本时发送按钮触发 onSend
 * 5. 无文本时语音按钮触发 onModeChange
 *
 * 运行环境：Android 设备/模拟器（androidTest）。
 */
@RunWith(AndroidJUnit4::class)
class InputBarInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun TestHarness(
        state: InputState,
        text: String,
        onTextChange: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onAttachmentClick: () -> Unit = {},
        onModeChange: (Boolean) -> Unit = {},
    ) {
        MaterialTheme {
            Surface {
                InputBar(
                    state = state,
                    text = text,
                    onTextChange = onTextChange,
                    onSend = onSend,
                    onVoiceStart = {},
                    onVoiceEnd = {},
                    onVoiceCancel = {},
                    onAttachmentClick = onAttachmentClick,
                    hasAttachments = false,
                    agentModeEnabled = false,
                    onAgentToggle = {},
                    onModelSelect = {},
                    onModeChange = onModeChange,
                )
            }
        }
    }

    // ─── 场景 1：空闲状态显示输入提示 ─────────────────────────────────────

    @Test
    fun `场景1_空闲状态_空文本_显示输入提示`() {
        composeTestRule.setContent {
            TestHarness(state = InputState.Idle, text = "")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("让 Aries AI 帮你操作手机")
            .assertIsDisplayed()
    }

    // ─── 场景 2：已输入文本在折叠态正确展示 ───────────────────────────────

    @Test
    fun `场景2_已输入文本_折叠态显示文本内容`() {
        val testMessage = "测试消息内容"
        composeTestRule.setContent {
            TestHarness(state = InputState.Idle, text = testMessage)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(testMessage)
            .assertIsDisplayed()
    }

    // ─── 场景 3：附件按钮点击触发回调 ─────────────────────────────────────

    @Test
    fun `场景3_点击附件按钮_触发onAttachmentClick`() {
        var attachmentClicked = false
        composeTestRule.setContent {
            TestHarness(
                state = InputState.Idle,
                text = "",
                onAttachmentClick = { attachmentClicked = true },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("附件")
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue("附件按钮点击应触发 onAttachmentClick 回调", attachmentClicked)
    }

    // ─── 场景 4：有文本时发送按钮触发 onSend ──────────────────────────────

    @Test
    fun `场景4_有文本时_点击发送按钮_触发onSend`() {
        var sendClicked = false
        composeTestRule.setContent {
            TestHarness(
                state = InputState.Idle,
                text = "你好",
                onSend = { sendClicked = true },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("发送")
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue("有文本时发送按钮应触发 onSend 回调", sendClicked)
    }

    // ─── 场景 5：无文本时语音按钮触发 onModeChange ───────────────────────

    @Test
    fun `场景5_无文本时_点击语音按钮_触发onModeChange`() {
        var modeChangeCalled = false
        var modeChangeValue: Boolean? = null
        composeTestRule.setContent {
            TestHarness(
                state = InputState.Idle,
                text = "",
                onModeChange = { isVoiceMode ->
                    modeChangeCalled = true
                    modeChangeValue = isVoiceMode
                },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("语音输入")
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue("无文本时语音按钮应触发 onModeChange 回调", modeChangeCalled)
        assertEquals("应请求切换到语音模式 (true)", true, modeChangeValue)
    }
}
