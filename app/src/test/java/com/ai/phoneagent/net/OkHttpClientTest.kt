package com.ai.phoneagent.net

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OkHttpClient 构建配置测试。
 *
 * 直接调用生产侧的 [buildNetworkClient]，验证超时、可靠性、协议、连接池等配置项符合预期。
 * 不再在测试中维护 builder 副本——若有人修改生产配置而未同步测试断言，本测试应失败。
 *
 * 注意：日志级别由 BuildConfig.DEBUG 决定；debug 构建变体下应返回 BASIC。
 */
class OkHttpClientTest {

    /**
     * 构建标准（非 fast）网络客户端，与生产侧 SharedHttpClient.instance 共用同一函数。
     */
    private fun buildNetworkClient(): OkHttpClient =
        com.ai.phoneagent.net.buildNetworkClient(useFastTimeouts = false)

    // ─── Timeout configuration ────────────────────────────────────────────────

    @Test
    fun `connect timeout is 60 seconds`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected connectTimeoutMillis = 60_000ms",
            60_000,
            client.connectTimeoutMillis
        )
    }

    @Test
    fun `read timeout is 300 seconds`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected readTimeoutMillis = 300_000ms",
            300_000,
            client.readTimeoutMillis
        )
    }

    @Test
    fun `write timeout is 120 seconds`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected writeTimeoutMillis = 120_000ms",
            120_000,
            client.writeTimeoutMillis
        )
    }

    @Test
    fun `call timeout is 360 seconds`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected callTimeoutMillis = 360_000ms",
            360_000,
            client.callTimeoutMillis
        )
    }

    // ─── Reliability settings ─────────────────────────────────────────────────

    @Test
    fun `retry on connection failure is enabled`() {
        val client = buildNetworkClient()
        assertTrue("retryOnConnectionFailure should be true", client.retryOnConnectionFailure)
    }

    // ─── Interceptors ────────────────────────────────────────────────────────

    @Test
    fun `client has HttpLoggingInterceptor`() {
        val client = buildNetworkClient()
        val hasLoggingInterceptor = client.interceptors.any { it is HttpLoggingInterceptor }
        assertTrue("Expected HttpLoggingInterceptor in interceptors", hasLoggingInterceptor)
    }

    @Test
    fun `logging interceptor level is BASIC`() {
        val client = buildNetworkClient()
        val loggingInterceptor = client.interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()
        assertNotNull("HttpLoggingInterceptor must be present", loggingInterceptor)
        assertEquals(
            "Expected logging level BASIC (BuildConfig.DEBUG=true in debug variant)",
            HttpLoggingInterceptor.Level.BASIC,
            loggingInterceptor!!.level
        )
    }

    // ─── Protocol support ─────────────────────────────────────────────────────

    @Test
    fun `protocols include HTTP_2`() {
        val client = buildNetworkClient()
        assertTrue(
            "HTTP/2 must be in protocols list",
            client.protocols.contains(Protocol.HTTP_2)
        )
    }

    @Test
    fun `protocols include HTTP_1_1`() {
        val client = buildNetworkClient()
        assertTrue(
            "HTTP/1.1 must be in protocols list",
            client.protocols.contains(Protocol.HTTP_1_1)
        )
    }

    @Test
    fun `protocols list contains exactly HTTP_2 and HTTP_1_1`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected exactly [HTTP_2, HTTP_1_1]",
            listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
            client.protocols
        )
    }

    // ─── Connection pool ──────────────────────────────────────────────────────

    @Test
    fun `client has a connection pool configured`() {
        val client = buildNetworkClient()
        assertNotNull("ConnectionPool should be configured", client.connectionPool)
    }

    @Test
    fun `interceptors list has exactly one interceptor`() {
        val client = buildNetworkClient()
        assertEquals(
            "Expected exactly 1 interceptor (HttpLoggingInterceptor)",
            1,
            client.interceptors.size
        )
    }

    // ─── Fast timeouts variant ────────────────────────────────────────────────

    @Test
    fun `fast variant uses shorter timeouts`() {
        val client = com.ai.phoneagent.net.buildNetworkClient(useFastTimeouts = true)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(25_000, client.readTimeoutMillis)
        assertEquals(25_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
    }
}
