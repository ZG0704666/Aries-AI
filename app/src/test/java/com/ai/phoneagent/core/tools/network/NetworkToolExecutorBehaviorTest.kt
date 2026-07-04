/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21A: NetworkToolExecutor 行为测试。
 *
 * 测试策略：
 * - NetworkToolExecutor 是 object 单例，不能直接 mock。OkHttpClient 为
 *   private val，同样无法注入。
 * - 不实际发起 HTTP 请求：聚焦于 SSRF 拦截（内网/回环/云元数据 URL）与参数校验。
 * - httpGet/httpPost/download 在发请求前调用 InetAddressGuard.requirePublic(host)，
 *   对 IP 字面量（如 10.0.0.1）不触发 DNS，直接解析并拒绝。因此 SSRF 用例
 *   不依赖网络即可验证拦截行为，SecurityException 被 executor 捕获后返回
 *   success=false 的 ToolResult。
 * - "公网 IP 通过 SSRF 校验" 用例直接调用 InetAddressGuard.requirePublic("8.8.8.8")
 *   验证校验逻辑（IP 字面量不触发 DNS，不发起网络请求），符合"不实际发起网络请求"约束。
 *   该用例与"内网 URL 被拦截"用例形成对照，证明 SSRF 护栏对公网/内网 IP 的区分正确。
 * - download 在 SSRF 通过后访问 Context.cacheDir，通过 init(mockContext) 注入。
 * - url_encode 操作在 NetworkToolExecutor 中不存在，故不测试。
 * - get_ip 返回本机网卡 IP（非域名解析），仅验证不崩溃。
 * - dns_lookup 使用 "localhost"（通过 hosts 文件解析，不依赖外部 DNS）。
 */
package com.ai.phoneagent.core.tools.network

import android.content.Context
import com.ai.phoneagent.core.security.InetAddressGuard
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolParameter
import com.ai.phoneagent.data.model.ToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [NetworkToolExecutor] 行为测试。
 *
 * 覆盖 SSRF 拦截、参数校验、dns_lookup、get_ip、ping 的行为集成，
 * 不实际发起 HTTP 请求（SSRF 拦截用例在请求前即被拦截；公网 IP 用例直接验证 InetAddressGuard）。
 */
class NetworkToolExecutorBehaviorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        val cacheRoot = tempFolder.newFolder("cache")
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.cacheDir } returns cacheRoot
        NetworkToolExecutor.init(mockContext)
    }

    private fun tool(name: String, vararg params: Pair<String, String>): AITool =
        AITool(
            name = name,
            parameters = params.map { ToolParameter(it.first, it.second) }
        )

    private fun text(result: ToolResult): String =
        (result.result as? StringResultData)?.data ?: ""

    // ===== SSRF 拦截：http_get =====

    @Test
    fun `http_get_内网URL_被InetAddressGuard拦截`() = runBlocking {
        val result = NetworkToolExecutor.httpGet(
            tool("http_get", "url" to "http://10.0.0.1/admin")
        )

        assertFalse("内网 URL 不应成功: $result", result.success)
        assertTrue("应返回错误信息: ${result.error}", result.error.isNotEmpty())
    }

    @Test
    fun `http_get_云元数据URL_被InetAddressGuard拦截`() = runBlocking {
        val result = NetworkToolExecutor.httpGet(
            tool("http_get", "url" to "http://169.254.169.254/latest/meta-data")
        )

        assertFalse("云元数据 URL 不应成功", result.success)
    }

    @Test
    fun `http_get_本地回环_被InetAddressGuard拦截`() = runBlocking {
        val result = NetworkToolExecutor.httpGet(
            tool("http_get", "url" to "http://127.0.0.1:8080")
        )

        assertFalse("本地回环不应成功", result.success)
    }

    // ===== SSRF 拦截：http_post =====

    @Test
    fun `http_post_内网URL_被InetAddressGuard拦截`() = runBlocking {
        val result = NetworkToolExecutor.httpPost(
            tool("http_post", "url" to "http://10.0.0.1/admin", "body" to "{}")
        )

        assertFalse("内网 URL POST 不应成功", result.success)
    }

    @Test
    fun `http_post_公网IP_通过SSRF校验`() {
        // 直接验证 InetAddressGuard 对公网 IP 字面量不拦截（IP 字面量不触发 DNS，
        // 不发起网络请求），符合"不实际发起网络请求"约束。
        // http_post 在发请求前调用此方法，校验通过后才会执行 HTTP 调用；
        // 此处仅验证 SSRF 校验逻辑，避免依赖网络。
        // 8.8.8.8（Google DNS）为公网 IP，requirePublic 不应抛异常。
        try {
            InetAddressGuard.requirePublic("8.8.8.8")
        } catch (e: SecurityException) {
            fail("公网 IP 8.8.8.8 不应被 SSRF 拦截: ${e.message}")
        }

        // 对照：内网 IP 应被拦截（验证护栏逻辑正确工作）
        var blocked = false
        try {
            InetAddressGuard.requirePublic("10.0.0.1")
        } catch (e: SecurityException) {
            blocked = true
        }
        assertTrue("内网 IP 10.0.0.1 应被 SSRF 拦截", blocked)
    }

    // ===== SSRF 拦截：download =====

    @Test
    fun `download_内网URL_被InetAddressGuard拦截`() = runBlocking {
        val result = NetworkToolExecutor.download(
            tool("download", "url" to "http://10.0.0.1/file", "save_path" to "test.bin")
        )

        assertFalse("内网 URL 下载不应成功", result.success)
    }

    // ===== 参数校验：缺少必填参数 =====

    @Test
    fun `http_get_缺少url参数_返回错误`() = runBlocking {
        val result = NetworkToolExecutor.httpGet(tool("http_get"))

        assertFalse(result.success)
        assertTrue(
            "应提示缺少 url: ${result.error}",
            result.error.contains("url") || result.error.contains("缺少")
        )
    }

    @Test
    fun `http_post_缺少url参数_返回错误`() = runBlocking {
        val result = NetworkToolExecutor.httpPost(
            tool("http_post", "body" to "data")
        )

        assertFalse(result.success)
        assertTrue(
            "应提示缺少 url: ${result.error}",
            result.error.contains("url") || result.error.contains("缺少")
        )
    }

    @Test
    fun `download_缺少url参数_返回错误`() = runBlocking {
        val result = NetworkToolExecutor.download(tool("download"))

        assertFalse(result.success)
        assertTrue(
            "应提示缺少 url: ${result.error}",
            result.error.contains("url") || result.error.contains("缺少")
        )
    }

    @Test
    fun `ping_缺少host参数_返回错误`() = runBlocking {
        val result = NetworkToolExecutor.ping(tool("ping"))

        assertFalse(result.success)
        assertTrue(
            "应提示缺少 host: ${result.error}",
            result.error.contains("host") || result.error.contains("缺少")
        )
    }

    @Test
    fun `dns_lookup_缺少domain参数_返回错误`() = runBlocking {
        val result = NetworkToolExecutor.dnsLookup(tool("dns_lookup"))

        assertFalse(result.success)
        assertTrue(
            "应提示缺少 domain: ${result.error}",
            result.error.contains("domain") || result.error.contains("缺少")
        )
    }

    // ===== dns_lookup（localhost 通过 hosts 文件解析，不依赖外部 DNS）=====

    @Test
    fun `dns_lookup_localhost_返回回环地址`() = runBlocking {
        val result = NetworkToolExecutor.dnsLookup(
            tool("dns_lookup", "domain" to "localhost")
        )

        assertTrue("dns_lookup localhost 应成功: ${result.error}", result.success)
        assertTrue(
            "应包含 127.0.0.1: ${text(result)}",
            text(result).contains("127.0.0.1")
        )
    }

    // ===== get_ip（返回本机网卡 IP，非域名解析）=====

    @Test
    fun `get_ip_执行不崩溃_返回结果`() = runBlocking {
        val result = NetworkToolExecutor.getIP(tool("get_ip"))

        // get_ip 枚举本机网卡，结果可能为成功（找到非回环 IP）或失败（无可用网卡）。
        // 关键是不崩溃并返回有效 ToolResult。
        val combined = text(result) + result.error
        assertTrue("应返回某种结果: $combined", combined.isNotEmpty())
    }

    // ===== ping 参数解析 =====

    @Test(timeout = 15000L)
    fun `ping_localhost_参数解析正确_不崩溃`() = runBlocking {
        val result = NetworkToolExecutor.ping(
            tool("ping", "host" to "localhost", "count" to "1", "timeout_ms" to "1000")
        )

        // localhost 通过 hosts 文件解析，isReachable(1000) 可能返回 true 或 false。
        // 关键是参数被正确解析（host/count/timeout_ms），不因缺少参数失败。
        assertFalse(
            "不应因缺少参数失败: ${result.error}",
            result.error.contains("缺少")
        )
    }
}
