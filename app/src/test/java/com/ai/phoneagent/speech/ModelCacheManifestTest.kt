package com.ai.phoneagent.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型缓存完整性清单（R-04）纯逻辑的 JVM 单测：
 * 覆盖序列化往返、各种不可信输入的拒绝行为，
 * 保证“审计拒绝要优先于通过（宁可重拷，不加载残缺模型）”。
 */
class ModelCacheManifestTest {

    private val validSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"

    /** 合法清单：正常解析并保留全部条目。 */
    @Test
    fun `parse accepts well formed manifest`() {
        val text = "tokens.txt\t64\t$validSha\nencoder_jit_trace-pnnx.ncnn.bin\t1048576\t$validSha\n"
        val parsed = ModelCacheManifest.parse(text)
        assertNotNull(parsed)
        parsed!!
        assertEquals(2, parsed.size)
        assertEquals(ModelCacheManifest.Entry(length = 64L, sha256 = validSha), parsed["tokens.txt"])
        assertTrue(parsed.containsKey("encoder_jit_trace-pnnx.ncnn.bin"))
    }

    /** 序列化/解析往返一致。 */
    @Test
    fun `format then parse round trips`() {
        val entries =
                mapOf(
                        "tokens.txt" to ModelCacheManifest.Entry(100L, validSha),
                        "decoder_jit_trace-pnnx.ncnn.bin" to
                                ModelCacheManifest.Entry(1_234_567L, validSha)
                )
        val parsed = ModelCacheManifest.parse(ModelCacheManifest.format(entries))
        assertEquals(entries, parsed)
    }

    /** 空文本与坏行集合：一律拒绝（返回 null）而不是部分接受。 */
    @Test
    fun `parse rejects blank or malformed lines`() {
        assertNull(ModelCacheManifest.parse(""))
        assertNull(ModelCacheManifest.parse("   \n  \n"))

        // 字段数不对（缺 sha）
        assertNull(ModelCacheManifest.parse("tokens.txt\t64\n"))
        // 长度非法
        assertNull(ModelCacheManifest.parse("tokens.txt\t0\t$validSha"))
        assertNull(ModelCacheManifest.parse("tokens.txt\tnotanumber\t$validSha"))
        // 徒有空行夹带坏行
        assertNull(ModelCacheManifest.parse("\ntokens.txt\t64\txx\n"))
        // 文件名带路径分隔（防改名逃逸）
        assertNull(ModelCacheManifest.parse(
                "..%2Fevil\t64\t$validSha"
        ))
        assertNull(ModelCacheManifest.parse("sub/evil.txt\t64\t$validSha"))
        assertNull(ModelCacheManifest.parse("sub\\evil.txt\t64\t$validSha"))
        // 重复条目
        assertNull(ModelCacheManifest.parse("tokens.txt\t64\t$validSha\ntokens.txt\t64\t$validSha"))
    }

    /** SHA-256 hex 必须是 64 位合法 hex；大小写统一为小写。 */
    @Test
    fun `parse rejects malformed sha and normalizes case`() {
        assertNull(ModelCacheManifest.parse("tokens.txt\t64\t${"x".repeat(64)}"))
        assertNull(ModelCacheManifest.parse("tokens.txt\t64\t${validSha.take(63)}"))
        assertNull(ModelCacheManifest.parse("tokens.txt\t64\tzzzz"))

        val uppercase =
                ModelCacheManifest.parse("tokens.txt\t64\t${validSha.uppercase()}")
        assertNotNull(uppercase)
        assertEquals(validSha, uppercase!!["tokens.txt"]!!.sha256)
    }

    /** 长度必须为正（R-04 的关键点：拷贝中断产生的 0 长度文件不可信）。 */
    @Test
    fun `parse rejects non positive length`() {
        assertNull(ModelCacheManifest.parse("tokens.txt\t0\t$validSha"))
        assertNull(ModelCacheManifest.parse("tokens.txt\t-1\t$validSha"))
    }
}
