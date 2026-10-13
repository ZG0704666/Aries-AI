package com.ai.phoneagent.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelResolverTest {
    @Test fun selectedTextModelDoesNotOverrideVision() {
        assertEquals("chosen-text", ChatModelResolver.resolve(false, true, false, " chosen-text ", ""))
        assertEquals(AriesApiClient.ARIES_VISION_MODEL, ChatModelResolver.resolve(false, true, false, "chosen-text", "", true))
    }

    @Test fun providerPrecedenceAndEmptyConfigurationRemainCompatible() {
        assertEquals(ModelScopeModelDownloader.QWEN35_MODEL_NAME, ChatModelResolver.resolve(true, true, true, "selected", "custom", true))
        assertEquals(AriesApiClient.ARIES_CHAT_MODEL, ChatModelResolver.resolve(false, true, false, "  ", ""))
        assertEquals("custom", ChatModelResolver.resolve(false, false, true, "selected", " custom "))
        assertEquals(AutoGlmClient.DEFAULT_MODEL, ChatModelResolver.resolve(false, false, true, "selected", ""))
        assertEquals(AutoGlmClient.DEFAULT_MODEL, ChatModelResolver.resolve(false, false, false, "selected", "custom"))
    }
}
