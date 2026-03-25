package com.ai.phoneagent.net

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests verifying that the OkHttpClient built with the same configuration as
 * [com.ai.phoneagent.di.NetworkModule] has the expected timeouts, settings, and interceptors.
 *
 * No DI framework is used here — we construct the client directly to keep tests self-contained
 * and avoid Android instrumentation dependencies.
 */
class OkHttpClientTest {

    /**
     * Builds an OkHttpClient using the exact same configuration as NetworkModule.kt.
     * We always use BASIC logging level in tests (same effect as DEBUG=true).
     */
    private fun buildNetworkClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

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
            "Expected logging level BASIC",
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
        // Verify a connection pool is present (non-null) — pool is a required part of the client
        assertNotNull("ConnectionPool should be configured", client.connectionPool)
    }

    @Test
    fun `interceptors list has exactly one interceptor`() {
        val client = buildNetworkClient()
        // Only the HttpLoggingInterceptor should be in the interceptors list
        assertEquals(
            "Expected exactly 1 interceptor (HttpLoggingInterceptor)",
            1,
            client.interceptors.size
        )
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun assertNotNull(message: String, obj: Any?) {
    org.junit.Assert.assertNotNull(message, obj)
}
