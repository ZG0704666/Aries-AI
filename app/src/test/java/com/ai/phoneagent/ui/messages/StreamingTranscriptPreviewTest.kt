package com.ai.phoneagent.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTranscriptPreviewTest {

    @Test
    fun blankBodyShowsLoadingIndicatorOnly() {
        val preview = buildStreamingTranscriptBodyPreview("")

        assertTrue(preview.showLoadingIndicator)
        assertEquals("", preview.renderMarkdownText)
        assertEquals(0, preview.fullTextLength)
    }

    @Test
    fun smallPlainTextDeltaIsBufferedUntilBoundary() {
        val initial = "这是一段已经显示的内容"
        val state =
            StreamingTranscriptMessageState(
                conversationId = 1L,
                messageIndex = 0,
                id = "streaming-assistant",
                author = "Aries AI",
                retryText = null,
                initialBody = initial,
                initialThinking = null,
                initialCopyText = initial,
            )
        val previousPreview = state.bodyPreview

        state.update(
            nextBody = initial + "慢",
            nextThinking = null,
            nextCopyText = initial + "慢",
        )

        assertEquals(previousPreview, state.bodyPreview)
        assertEquals(initial + "慢", state.copyText)

        state.update(
            nextBody = initial + "慢。",
            nextThinking = null,
            nextCopyText = initial + "慢。",
        )

        assertEquals(initial + "慢。", state.bodyPreview.renderMarkdownText)
    }

    @Test
    fun markdownStructuresUseMarkdownPreviewImmediately() {
        assertTrue(buildStreamingTranscriptBodyPreview("# 标题").usesMarkdownPreview)
        assertTrue(buildStreamingTranscriptBodyPreview("- 列表项").usesMarkdownPreview)
    }

    @Test
    fun openCodeFenceIsTemporarilyClosedForRendering() {
        val raw = "```kotlin\nval x = 1"
        val preview = buildStreamingTranscriptBodyPreview(raw)

        assertEquals(raw.length, preview.fullTextLength)
        assertTrue(preview.renderMarkdownText.endsWith("\n```"))
    }

    @Test
    fun closedCodeFenceIsNotPollutedByTemporaryClosure() {
        val raw = "```kotlin\nval x = 1\n```"
        val preview = buildStreamingTranscriptBodyPreview(raw)

        assertEquals(raw, preview.renderMarkdownText)
        assertEquals(raw.length, preview.fullTextLength)
        assertFalse(preview.showLoadingIndicator)
    }

    @Test
    fun danglingStrongTailIsHeldOutOfRenderSnapshot() {
        val preview = buildStreamingTranscriptBodyPreview("这是 **重要")

        assertEquals("这是", preview.renderMarkdownText)
        assertEquals("这是 **重要".length, preview.fullTextLength)
    }
}
