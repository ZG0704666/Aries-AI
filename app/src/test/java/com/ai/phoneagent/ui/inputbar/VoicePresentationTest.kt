package com.ai.phoneagent.ui.inputbar

import org.junit.Assert.assertEquals
import org.junit.Test

class VoicePresentationTest {
    @Test
    fun idleStateUsesIdlePresentation() {
        assertEquals(VoicePresentation.Idle, voicePresentation(InputState.VoiceIdle))
    }

    @Test
    fun recordingStateUsesBlueWaveform() {
        assertEquals(VoicePresentation.Listening, voicePresentation(InputState.VoiceRecording()))
    }

    @Test
    fun cancellingStateUsesRedWaveform() {
        assertEquals(VoicePresentation.Cancelling, voicePresentation(InputState.VoiceRecording(isCancelling = true)))
    }

    @Test
    fun recognizingStateKeepsVoiceCapsuleVisible() {
        assertEquals(VoicePresentation.Recognizing, voicePresentation(InputState.VoiceRecognizing()))
    }

    @Test
    fun recognizingStateCanShowCancelFeedback() {
        assertEquals(
            VoicePresentation.Cancelling,
            voicePresentation(InputState.VoiceRecognizing(isCancelling = true)),
        )
    }

    @Test
    fun finalizingStateKeepsVoiceCapsuleVisible() {
        assertEquals(VoicePresentation.Finalizing, voicePresentation(InputState.VoiceFinalizing))
    }
}
