/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ai.phoneagent.core.tools.network

import android.content.Context
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolParameter
import com.ai.phoneagent.data.model.ToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import kotlin.math.min

class NetworkToolExecutorBehaviorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appContext: Context
    private lateinit var cacheRoot: java.io.File
    private val publicAddress: InetAddress = InetAddress.getByName("8.8.8.8")
    private val privateAddress: InetAddress = InetAddress.getByName("127.0.0.1")

    @Before
    fun setUp() {
        cacheRoot = tempFolder.newFolder("cache")
        appContext = mockk(relaxed = true)
        every { appContext.applicationContext } returns appContext
        every { appContext.filesDir } returns tempFolder.newFolder("files")
        every { appContext.cacheDir } returns cacheRoot
        every { appContext.getExternalFilesDir(any()) } returns null
    }

    private fun tool(name: String, vararg params: Pair<String, String>): AITool =
        AITool(name = name, parameters = params.map { ToolParameter(it.first, it.second) })

    private fun text(result: ToolResult): String =
        (result.result as? StringResultData)?.data.orEmpty()

    private fun executor(
        dns: Dns = dns { listOf(publicAddress) },
        responder: (Request) -> Response = { response(it, 200, "ok") },
    ): NetworkToolExecutor {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build()
        return NetworkToolExecutor(appContext, client, dns)
    }

    @Test
    fun `http_get_IP字面量内网_请求前拒绝`() = runBlocking {
        var calls = 0
        val result = executor(Dns.SYSTEM) {
            calls++
            response(it, 200, "must not run")
        }.httpGet(tool("http_get", "url" to "http://127.0.0.1/admin"))

        assertFalse(result.success)
        assertEquals(0, calls)
    }


    @Test
    fun `http_get_自定义Host头大小写变体_请求前拒绝`() = runBlocking {
        listOf("Host", "hOsT").forEach { headerName ->
            var calls = 0
            val result = executor {
                calls++
                response(it, 200, "must not run")
            }.httpGet(
                tool(
                    "http_get",
                    "url" to "https://public.test/resource",
                    "headers" to "$headerName: internal.example",
                )
            )

            assertFalse("$headerName 必须被拒绝", result.success)
            assertTrue(result.error.contains("Host"))
            assertEquals("拒绝 Host 头后不得进入网络客户端", 0, calls)
        }
    }

    @Test
    fun `受控DNS_混合公网与私网地址_拒绝整个结果`() {
        val guarded = PinnedPublicDns(dns { listOf(publicAddress, privateAddress) })

        try {
            guarded.pin("mixed.test")
            fail("混合 DNS 结果必须被拒绝")
        } catch (expected: SecurityException) {
            assertTrue(expected.message.orEmpty().contains("127.0.0.1"))
        }
    }

    @Test
    fun `受控DNS_校验结果与连接层结果相同`() {
        var resolutions = 0
        val guarded = PinnedPublicDns(
            dns {
                resolutions++
                if (resolutions == 1) listOf(publicAddress) else listOf(privateAddress)
            }
        )

        val validated = guarded.pin("rebind.test")
        val connectionAddresses = guarded.lookup("rebind.test")

        assertEquals(validated, connectionAddresses)
        assertEquals(1, resolutions)
    }

    @Test
    fun `同主机重定向_重新解析为私网时拒绝`() = runBlocking {
        var resolutions = 0
        var requests = 0
        val rebindingDns = dns {
            resolutions++
            if (resolutions == 1) listOf(publicAddress) else listOf(privateAddress)
        }
        val result = executor(rebindingDns) { request ->
            requests++
            response(request, 302, "", "Location" to "http://rebind.test/next")
        }.httpGet(tool("http_get", "url" to "http://rebind.test/start"))

        assertFalse(result.success)
        assertEquals(1, requests)
        assertEquals(2, resolutions)
    }

    @Test
    fun `重定向到私网主机_第二跳请求前拒绝`() = runBlocking {
        var requests = 0
        val hostDns = dns { host ->
            if (host == "private.test") listOf(privateAddress) else listOf(publicAddress)
        }
        val result = executor(hostDns) { request ->
            requests++
            response(request, 302, "", "Location" to "http://private.test/secret")
        }.httpGet(tool("http_get", "url" to "http://public.test/start"))

        assertFalse(result.success)
        assertEquals(1, requests)
    }

    @Test
    fun `跨域重定向_移除全部调用方自定义请求头`() = runBlocking {
        val requests = mutableListOf<Request>()
        val result = executor { request ->
            requests += request
            if (requests.size == 1) {
                response(request, 302, "", "Location" to "https://other.test/next")
            } else {
                response(request, 200, "ok")
            }
        }.httpGet(
            tool(
                "http_get",
                "url" to "https://public.test/start",
                "headers" to "X-API-Key: secret-value\nX-Custom-Signature: signed-value\nAccept: application/json",
            )
        )

        assertTrue(result.success)
        assertEquals(2, requests.size)
        assertEquals("secret-value", requests.first().header("X-API-Key"))
        assertTrue("跨域后的第二跳不应保留上一跳 Header", requests.last().headers.names().isEmpty())
    }

    @Test
    fun `重定向循环_超过五次后失败`() = runBlocking {
        var requests = 0
        val result = executor { request ->
            requests++
            response(request, 302, "", "Location" to "http://public.test/loop")
        }.httpGet(tool("http_get", "url" to "http://public.test/loop"))

        assertFalse(result.success)
        assertTrue(result.error.contains("5"))
        assertEquals(6, requests)
    }

    @Test
    fun `HTTPS重定向降级到HTTP_拒绝`() = runBlocking {
        val result = executor { request ->
            response(request, 302, "", "Location" to "http://public.test/insecure")
        }.httpGet(tool("http_get", "url" to "https://public.test/secure"))

        assertFalse(result.success)
        assertTrue(result.error.contains("降级"))
    }

    @Test
    fun `HTTP响应恰好4MiB_允许完整读取`() = runBlocking {
        val body = ByteArray(NetworkToolExecutor.MAX_RESPONSE_BYTES.toInt()) { 'a'.code.toByte() }
        val result = executor(responder = { request ->
            response(request, 200, body.toResponseBody("text/plain".toMediaType()))
        }).httpGet(tool("http_get", "url" to "https://public.test/data"))

        assertTrue("边界响应应成功: ${result.error}", result.success)
        assertTrue(text(result).contains("HTTP GET 成功"))
    }

    @Test
    fun `HTTP响应超过4MiB_返回大小限制错误`() = runBlocking {
        val result = executor(responder = { request ->
            response(request, 200, declaredOversizeBody(NetworkToolExecutor.MAX_RESPONSE_BYTES + 1))
        }).httpGet(tool("http_get", "url" to "https://public.test/large"))

        assertFalse(result.success)
        assertTrue(result.error.contains("大小限制"))
    }

    @Test
    fun `download_流式写入并原子替换目标`() = runBlocking {
        val result = executor(responder = { request ->
            response(request, 200, "download-body")
        }).download(
            tool(
                "download",
                "url" to "https://public.test/file.bin",
                "save_path" to "nested/file.bin",
            )
        )

        assertTrue("下载应成功: ${result.error}", result.success)
        assertEquals("download-body", java.io.File(cacheRoot, "nested/file.bin").readText())
        assertTrue(cacheRoot.walkTopDown().none { it.name.endsWith(".part") })
    }

    @Test
    fun `download_目标路径穿越_请求前拒绝`() = runBlocking {
        var requests = 0
        val result = executor(responder = {
            requests++
            response(it, 200, "data")
        }).download(
            tool(
                "download",
                "url" to "https://public.test/file.bin",
                "save_path" to "../outside.bin",
            )
        )

        assertFalse(result.success)
        assertEquals(0, requests)
        assertFalse(java.io.File(cacheRoot.parentFile, "outside.bin").exists())
    }

    @Test
    fun `download_超过200MiB_删除临时文件且不替换目标`() = runBlocking {
        val target = java.io.File(cacheRoot, "large.bin")
        target.writeText("original")
        val result = executor(responder = { request ->
            response(
                request,
                200,
                declaredOversizeBody(NetworkToolExecutor.MAX_DOWNLOAD_BYTES + 1),
            )
        }).download(
            tool(
                "download",
                "url" to "https://public.test/large.bin",
                "save_path" to "large.bin",
            )
        )

        assertFalse(result.success)
        assertTrue(result.error.contains("大小限制"))
        assertEquals("original", target.readText())
        assertTrue(cacheRoot.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `download_未知响应长度流式超限_删除临时文件且不替换目标`() = runBlocking {
        val target = java.io.File(cacheRoot, "chunked-large.bin")
        target.writeText("original")
        val result = executor(responder = { request ->
            response(
                request,
                200,
                streamingBody(NetworkToolExecutor.MAX_DOWNLOAD_BYTES + 1),
            )
        }).download(
            tool(
                "download",
                "url" to "https://public.test/chunked-large.bin",
                "save_path" to "chunked-large.bin",
            )
        )

        assertFalse(result.success)
        assertTrue(result.error.contains("大小限制"))
        assertEquals("original", target.readText())
        assertTrue(cacheRoot.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `流式复制恰好200MiB_允许完成`() = runBlocking {
        val copied = copyLimited(
            CountingInputStream(NetworkToolExecutor.MAX_DOWNLOAD_BYTES),
            NullOutputStream,
            NetworkToolExecutor.MAX_DOWNLOAD_BYTES,
        )

        assertEquals(NetworkToolExecutor.MAX_DOWNLOAD_BYTES, copied)
    }

    @Test
    fun `流式复制超过限制_不输出越界字节`() = runBlocking {
        val output = CountingOutputStream()
        try {
            copyLimited(
                CountingInputStream(NetworkToolExecutor.MAX_RESPONSE_BYTES + 1),
                output,
                NetworkToolExecutor.MAX_RESPONSE_BYTES,
            )
            fail("超过限制必须失败")
        } catch (expected: SizeLimitExceededException) {
            assertTrue(output.count <= NetworkToolExecutor.MAX_RESPONSE_BYTES)
        }
    }

    @Test
    fun `缺少必填参数_返回明确错误`() = runBlocking {
        val network = executor()

        assertFalse(network.httpGet(tool("http_get")).success)
        assertFalse(network.httpPost(tool("http_post", "body" to "{}")).success)
        assertFalse(network.download(tool("download")).success)
        assertFalse(network.ping(tool("ping")).success)
        assertFalse(network.dnsLookup(tool("dns_lookup")).success)
    }

    @Test
    fun `网络探测工具_注册为自动允许并披露剩余风险`() {
        val handler = AIToolHandler(appContext)
        registerNetworkTools(handler, executor())

        val probes = listOf(
            tool("ping", "host" to "public.test") to "网络探测",
            tool("get_ip") to "网络接口信息",
            tool("dns_lookup", "domain" to "public.test") to "网络探测",
        )

        probes.forEach { (probe, riskDescription) ->
            assertFalse("${probe.name} 必须保持自动允许", handler.isDangerousOperation(probe))
            val description = handler.getOperationDescription(probe)
            assertTrue("${probe.name} 描述必须注明自动允许", description.contains("自动允许"))
            assertTrue("${probe.name} 描述必须披露剩余风险", description.contains(riskDescription))
        }
    }

    @Test
    fun `网络探测工具_保持可调用`() = runBlocking {
        val network = executor()
        val dnsResult = network.dnsLookup(tool("dns_lookup", "domain" to "localhost"))
        val ipResult = network.getIP(tool("get_ip"))
        val pingResult = network.ping(
            tool("ping", "host" to "localhost", "count" to "1", "timeout_ms" to "100")
        )

        assertTrue(dnsResult.success)
        assertTrue(text(dnsResult).contains("127.0.0.1") || text(dnsResult).contains("::1"))
        assertTrue(text(ipResult).isNotEmpty() || ipResult.error.isNotEmpty())
        assertFalse(pingResult.error.contains("缺少"))
    }

    private fun response(
        request: Request,
        code: Int,
        body: String,
        vararg headers: Pair<String, String>,
    ): Response = response(request, code, body.toResponseBody("text/plain".toMediaType()), *headers)

    private fun response(
        request: Request,
        code: Int,
        body: ResponseBody,
        vararg headers: Pair<String, String>,
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(code.toString())
            .body(body)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun declaredOversizeBody(contentLength: Long): ResponseBody =
        object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = contentLength
            override fun source(): BufferedSource = Buffer().writeByte(0)
        }

    private fun streamingBody(byteCount: Long): ResponseBody =
        object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = CountingInputStream(byteCount).source().buffer()
        }

    private fun dns(resolver: (String) -> List<InetAddress>): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = resolver(hostname)
        }

    private class CountingInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            remaining--
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = min(length.toLong(), remaining).toInt()
            remaining -= count
            return count
        }
    }

    private class CountingOutputStream : OutputStream() {
        var count: Long = 0
            private set

        override fun write(value: Int) {
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            count += length
        }
    }

    private data object NullOutputStream : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}
