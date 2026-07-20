package com.ai.phoneagent.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApiConfigSignature] 回归测试（PR #25 review 阻断项 #1 的哈希化要求）：
 * 签名仅用于变更检测，不得包含可还原的原始 API Key。
 */
class ApiConfigSignatureTest {

    @Test
    fun `签名不包含原始Key`() {
        val sig = ApiConfigSignature.compute(
            apiKey = "sk-super-secret-key",
            baseUrl = "https://api.example.com",
            model = "gpt-4",
            mode = "third_party",
        )
        assertFalse("签名不得包含原始 Key", sig.contains("sk-super-secret-key"))
        assertTrue("签名应带 v2 版本前缀", sig.startsWith("v2|third_party|"))
    }

    @Test
    fun `相同输入_签名确定`() {
        val a = ApiConfigSignature.compute("sk-abc", "https://api", "m", "default")
        val b = ApiConfigSignature.compute("sk-abc", "https://api", "m", "default")
        assertEquals(a, b)
    }

    @Test
    fun `Key不同_签名不同`() {
        val a = ApiConfigSignature.compute("sk-aaa", "https://api", "m", "default")
        val b = ApiConfigSignature.compute("sk-bbb", "https://api", "m", "default")
        assertNotEquals(a, b)
    }

    @Test
    fun `baseUrl或model或mode不同_签名不同`() {
        val base = ApiConfigSignature.compute("sk-abc", "https://api", "m", "default")
        assertNotEquals(base, ApiConfigSignature.compute("sk-abc", "https://other", "m", "default"))
        assertNotEquals(base, ApiConfigSignature.compute("sk-abc", "https://api", "m2", "default"))
        assertNotEquals(base, ApiConfigSignature.compute("sk-abc", "https://api", "m", "aries"))
    }

    @Test
    fun `Key首尾空白不影响签名`() {
        val a = ApiConfigSignature.compute("sk-abc", "https://api", "m", "default")
        val b = ApiConfigSignature.compute("  sk-abc  ", "https://api", "m", "default")
        assertEquals(a, b)
    }
}
