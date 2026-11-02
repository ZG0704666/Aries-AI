package com.ai.phoneagent.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.ThemeColorStyle
import com.ai.phoneagent.core.designsystem.theme.ThemeMode
import com.ai.phoneagent.core.designsystem.theme.ariesHomeBackground
import com.ai.phoneagent.core.designsystem.theme.glass.AriesLiquidButton
import com.ai.phoneagent.core.designsystem.theme.glass.AriesLiquidCircleButton
import com.ai.phoneagent.ui.inputbar.InputBar
import com.ai.phoneagent.ui.inputbar.InputState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android 渲染管线生成亮暗主题、按压、禁用和停止状态的验收截图。 */
@RunWith(AndroidJUnit4::class)
class AriesVisualVerificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themesAndActionStatesRenderOnDevice() {
        var theme by mutableStateOf(ThemeColorStyle.DEFAULT)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var amoled by mutableStateOf(false)
        var disabledClicks = 0
        compose.setContent {
            AriesMaterialTheme(themeMode = mode, themeColorStyle = theme, amoledDark = amoled) {
                Column(
                    Modifier.fillMaxSize().ariesHomeBackground().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("${theme.name} / ${mode.name}", color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AriesLiquidButton(onClick = {}, modifier = Modifier.testTag("enabled")) { Text("普通操作") }
                        AriesLiquidButton(onClick = { disabledClicks++ }, enabled = false,
                            modifier = Modifier.testTag("disabled")) { Text("不可用") }
                        AriesLiquidCircleButton(onClick = {}, size = 48.dp) { Text("＋") }
                    }
                    Text("发送渐变", color = MaterialTheme.colorScheme.onSurface)
                    PreviewInput(InputState.Typing, "检查渐变与文字")
                    Text("停止保持语义色", color = MaterialTheme.colorScheme.onSurface)
                    PreviewInput(InputState.Generating, "")
                    Text("语音入口", color = MaterialTheme.colorScheme.onSurface)
                    PreviewInput(InputState.Idle, "")
                }
            }
        }
        compose.waitForIdle()
        for (style in ThemeColorStyle.entries) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { theme = style; mode = if (night) ThemeMode.DARK else ThemeMode.LIGHT }
                compose.waitForIdle()
                compose.onNodeWithTag("disabled").assertIsNotEnabled()
                save("${style.storageKey}-${if (night) "dark" else "light"}")
            }
        }
        compose.runOnIdle { theme = ThemeColorStyle.DEFAULT; mode = ThemeMode.DARK; amoled = true }
        compose.waitForIdle()
        save("default-amoled")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("enabled").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        save("default-pressed")
        compose.onNodeWithTag("enabled").performTouchInput { up() }
        compose.onNodeWithTag("disabled").performTouchInput { down(center); up() }
        assertEquals(0, disabledClicks)
    }

    @androidx.compose.runtime.Composable
    private fun PreviewInput(state: InputState, text: String) {
        InputBar(state = state, text = text, onTextChange = {}, onSend = {},
            onVoiceStart = {}, onVoiceEnd = {}, onVoiceCancel = {}, onAttachmentClick = {},
            agentModeEnabled = false, onAgentToggle = {}, onModelSelect = {}, onModeChange = {})
    }

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 私有文件可通过 debuggable 测试包的 run-as 导出，避免 Android 11 外部存储隔离差异。
        val folder = File(context.filesDir, "visual-verification").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
