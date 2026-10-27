package com.ai.phoneagent.ui.inputbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputBarVoiceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recordingWaveformRendersInsideTheOriginalVoiceCapsule() {
        renderInputBar(state = InputState.VoiceRecording())

        val capsule = compose.onNodeWithTag("voice_input_capsule")
        val wave = compose.onNodeWithTag("voice_input_waveform")
        capsule.assertHeightIsEqualTo(64.dp)
        wave.assertIsDisplayed()
        val capsuleBounds = capsule.fetchSemanticsNode().boundsInRoot
        val waveBounds = wave.fetchSemanticsNode().boundsInRoot
        assertTrue(waveBounds.left >= capsuleBounds.left)
        assertTrue(waveBounds.right <= capsuleBounds.right)
        assertTrue(waveBounds.top >= capsuleBounds.top)
        assertTrue(waveBounds.bottom <= capsuleBounds.bottom)
    }

    @Test
    fun microphoneClickEntersVoiceMode() {
        var state by mutableStateOf<InputState>(InputState.Idle)
        compose.setContent {
            AriesMaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    InputBar(
                        state = state,
                        text = "",
                        onTextChange = {},
                        onSend = {},
                        onVoiceStart = {},
                        onVoiceEnd = {},
                        onVoiceCancel = {},
                        onAttachmentClick = {},
                        agentModeEnabled = false,
                        onAgentToggle = {},
                        onModelSelect = {},
                        onModeChange = { state = if (it) InputState.VoiceIdle else InputState.Idle },
                    )
                }
            }
        }
        val voiceButtonLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.ai.phoneagent.R.string.voice_input)

        compose.onNodeWithContentDescription(voiceButtonLabel).performClick()

        compose.onNodeWithTag("voice_input_capsule").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun shortTapDoesNotStartRecording() {
        val starts = AtomicInteger()
        renderInputBar(onStart = { starts.incrementAndGet() })

        compose.onNodeWithTag("voice_input_capsule").performTouchInput { click() }

        assertEquals(0, starts.get())
    }

    @Test
    fun longPressStartsAndReleaseEndsRecording() {
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        val cancels = AtomicInteger()
        renderInputBar(
            onStart = { starts.incrementAndGet() },
            onEnd = { ends.incrementAndGet() },
            onCancel = { cancels.incrementAndGet() },
        )

        compose.onNodeWithTag("voice_input_capsule").performTouchInput { longClick() }

        assertEquals(1, starts.get())
        assertEquals(1, ends.get())
        assertEquals(0, cancels.get())
    }

    @Test
    fun swipeUpThenReleaseCancelsRecording() {
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        val cancels = AtomicInteger()
        renderInputBar(
            onStart = { starts.incrementAndGet() },
            onEnd = { ends.incrementAndGet() },
            onCancel = { cancels.incrementAndGet() },
        )
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

        compose.onNodeWithTag("voice_input_capsule").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -200f * density))
            up()
        }

        assertEquals(1, starts.get())
        assertEquals(0, ends.get())
        assertEquals(1, cancels.get())
    }

    @Test
    fun slidingBackOutsideCancelThresholdRestoresRelease() {
        val ends = AtomicInteger()
        val cancels = AtomicInteger()
        renderInputBar(
            onEnd = { ends.incrementAndGet() },
            onCancel = { cancels.incrementAndGet() },
        )
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

        compose.onNodeWithTag("voice_input_capsule").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -200f * density))
            moveBy(Offset(0f, 120f * density))
            up()
        }

        assertEquals(1, ends.get())
        assertEquals(0, cancels.get())
    }

    @Test
    fun recognitionFinishingBeforeReleaseDoesNotTurnIntoCancellation() {
        var state by mutableStateOf<InputState>(InputState.VoiceIdle)
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        val cancels = AtomicInteger()
        compose.setContent {
            AriesMaterialTheme {
                InputBar(
                    state = state,
                    text = "recognized text",
                    onTextChange = {},
                    onSend = {},
                    onVoiceStart = { starts.incrementAndGet(); state = InputState.Idle },
                    onVoiceEnd = { ends.incrementAndGet() },
                    onVoiceCancel = { cancels.incrementAndGet() },
                    onAttachmentClick = {},
                    agentModeEnabled = false,
                    onAgentToggle = {},
                    onModelSelect = {},
                    onModeChange = {},
                )
            }
        }

        compose.onNodeWithTag("voice_input_capsule").performTouchInput { longClick() }

        assertEquals(1, starts.get())
        assertEquals(0, ends.get())
        assertEquals(0, cancels.get())
    }

    private fun renderInputBar(
        state: InputState = InputState.VoiceIdle,
        onStart: () -> Unit = {},
        onEnd: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        compose.setContent {
            AriesMaterialTheme {
                InputBar(
                    state = state,
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onVoiceStart = onStart,
                    onVoiceEnd = onEnd,
                    onVoiceCancel = onCancel,
                    onAttachmentClick = {},
                    agentModeEnabled = false,
                    onAgentToggle = {},
                    onModelSelect = {},
                    onModeChange = {},
                )
            }
        }
        compose.onNodeWithTag("voice_input_capsule").assertHeightIsEqualTo(64.dp)
    }
}
