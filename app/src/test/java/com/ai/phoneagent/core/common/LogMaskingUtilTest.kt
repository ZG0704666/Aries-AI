package com.ai.phoneagent.core.common

import org.junit.Assert.*
import org.junit.Test

class LogMaskingUtilTest {

    @Test
    fun `maskApiKey masks long key correctly`() {
        val result = LogMaskingUtil.maskApiKey("sk-abcdefghijklmn")
        assertEquals("sk-a****klmn", result)
    }

    @Test
    fun `maskApiKey returns stars for short key`() {
        val result = LogMaskingUtil.maskApiKey("short")
        assertEquals("****", result)
    }

    @Test
    fun `maskApiKey returns empty for blank input`() {
        assertEquals("", LogMaskingUtil.maskApiKey(""))
        assertEquals("", LogMaskingUtil.maskApiKey("   "))
    }

    @Test
    fun `maskApiKey handles Bearer prefix`() {
        val result = LogMaskingUtil.maskApiKey("Bearer sk-abcdefghijklmn")
        assertEquals("sk-a****klmn", result)
    }

    @Test
    fun `maskAuthorizationHeader preserves Bearer prefix`() {
        val result = LogMaskingUtil.maskAuthorizationHeader("Bearer sk-abcdefghijklmn")
        assertEquals("Bearer sk-a****klmn", result)
    }

    @Test
    fun `maskUrl masks key parameter`() {
        val result = LogMaskingUtil.maskUrl("https://api.example.com/data?key=sk-abcdefghijklmn")
        assertTrue(result.contains("key=sk-a****klmn"))
        assertFalse(result.contains("sk-abcdefghijklmn"))
    }

    @Test
    fun `maskUrl masks token parameter`() {
        val result = LogMaskingUtil.maskUrl("https://api.example.com/data?token=sk-abcdefghijklmn")
        assertTrue(result.contains("token=sk-a****klmn"))
    }

    @Test
    fun `maskSensitiveInMessage masks Bearer token`() {
        val message = "Authorization: Bearer sk-abcdefghijklmn"
        val result = LogMaskingUtil.maskSensitiveInMessage(message)
        assertTrue(result.contains("Bearer sk-a****klmn"))
        assertFalse(result.contains("sk-abcdefghijklmn"))
    }

    @Test
    fun `maskSensitiveInMessage masks sk- pattern`() {
        val message = "Using key: sk-abcdefghijklmn for auth"
        val result = LogMaskingUtil.maskSensitiveInMessage(message)
        assertFalse(result.contains("sk-abcdefghijklmn"))
        assertTrue(result.contains("sk-a****klmn"))
    }

    @Test
    fun `maskSensitiveInMessage handles empty string`() {
        assertEquals("", LogMaskingUtil.maskSensitiveInMessage(""))
    }

    @Test
    fun `maskApiKey handles exactly 8 chars`() {
        val result = LogMaskingUtil.maskApiKey("12345678")
        assertEquals("****", result)
    }

    @Test
    fun `maskApiKey handles 9 chars`() {
        val result = LogMaskingUtil.maskApiKey("123456789")
        assertEquals("1234****6789", result)
    }
}
