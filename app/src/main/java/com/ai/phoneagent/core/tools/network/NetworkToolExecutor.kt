/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.tools.network

import android.content.Context
import android.util.Log
import com.ai.phoneagent.BuildConfig
import com.ai.phoneagent.core.security.InetAddressGuard
import com.ai.phoneagent.core.security.PathGuard
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 网络工具执行器
 * 提供 HTTP 请求、下载、Ping 等网络功能
 */
class NetworkToolExecutor(
    private val appContext: Context,
    private val client: OkHttpClient,
    private val dns: Dns = Dns.SYSTEM,
) {

    private val TAG = "NetworkTools"

    /**
     * HTTP GET 请求
     */
    suspend fun httpGet(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val url = tool.parameters.find { it.name == "url" }?.value
            ?: return@withContext errorResult(tool.name, "缺少 url 参数")

        val headersStr = tool.parameters.find { it.name == "headers" }?.value
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 10000L

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            // 添加自定义请求头
            headersStr?.let { headers ->
                parseHeaders(headers).forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
            }

            executeTextRequest(tool.name, "HTTP GET", requestBuilder.build(), timeout)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResult(tool.name, "HTTP GET 失败: ${e.message}")
        }
    }

    /**
     * HTTP POST 请求
     */
    suspend fun httpPost(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val url = tool.parameters.find { it.name == "url" }?.value
            ?: return@withContext errorResult(tool.name, "缺少 url 参数")

        val body = tool.parameters.find { it.name == "body" }?.value ?: ""
        val contentType = tool.parameters.find { it.name == "content_type" }?.value ?: "application/json"
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 10000L

        try {
            val requestBody = body.toRequestBody(contentType.toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", contentType)
                .build()

            executeTextRequest(tool.name, "HTTP POST", request, timeout)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResult(tool.name, "HTTP POST 失败: ${e.message}")
        }
    }

    /**
     * 下载文件
     */
    suspend fun download(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val url = tool.parameters.find { it.name == "url" }?.value
            ?: return@withContext errorResult(tool.name, "缺少 url 参数")

        val savePath = tool.parameters.find { it.name == "save_path" }?.value
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 30000L
        var temporaryFile: File? = null

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val fileName = savePath?.takeIf { it.isNotBlank() } ?: extractFileName(request.url)
            var target = PathGuard.canonicalizeWithin(listOf(appContext.cacheDir), fileName)
            target.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("无法创建下载目录: ${parent.absolutePath}")
                }
            }
            target = PathGuard.canonicalizeWithin(listOf(appContext.cacheDir), target.absolutePath)
            if (target.isDirectory) {
                throw IOException("下载目标是目录: ${target.absolutePath}")
            }

            temporaryFile = File.createTempFile(".${target.name}.", ".part", target.parentFile)
            val response = executeWithRedirects(request, timeout)
            response.use {
                if (!it.isSuccessful) {
                    return@withContext errorResult(tool.name, "下载失败: ${it.code}")
                }
                val responseBody = it.body
                    ?: return@withContext errorResult(tool.name, "下载失败: 空响应")
                rejectDeclaredOversize(responseBody.contentLength(), MAX_DOWNLOAD_BYTES, "下载")
                val written = FileOutputStream(temporaryFile).use { output ->
                    val count = copyLimited(responseBody.byteStream(), output, MAX_DOWNLOAD_BYTES) {
                        currentCoroutineContext().ensureActive()
                    }
                    output.fd.sync()
                    count
                }
                atomicReplace(temporaryFile, target)
                temporaryFile = null
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("下载成功: ${target.absolutePath} ($written bytes)"),
                    error = ""
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResult(tool.name, "下载失败: ${e.message}")
        } finally {
            temporaryFile?.delete()
        }
    }

    /**
     * Ping 主机
     */
    suspend fun ping(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val host = tool.parameters.find { it.name == "host" }?.value
            ?: return@withContext errorResult(tool.name, "缺少 host 参数")

        val count = tool.parameters.find { it.name == "count" }?.value?.toIntOrNull() ?: 4
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value?.toIntOrNull() ?: 5000

        try {
            val reachable = InetAddress.getByName(host).isReachable(timeout)
            val resultMessage = if (reachable) {
                "Ping $host 成功 - 主机可达"
            } else {
                "Ping $host 失败 - 主机不可达"
            }

            ToolResult(
                toolName = tool.name,
                success = reachable,
                result = StringResultData(resultMessage),
                error = if (reachable) "" else "主机不可达"
            )
        } catch (e: Exception) {
            errorResult(tool.name, "Ping 失败: ${e.message}")
        }
    }

    /**
     * 获取本机 IP 地址
     */
    suspend fun getIP(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            var ipAddress = ""

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains('.') == true) {
                        ipAddress = address.hostAddress ?: ""
                        break
                    }
                }
                if (ipAddress.isNotEmpty()) break
            }

            val success = ipAddress.isNotEmpty()
            val resultMessage = if (success) {
                "本机 IP: $ipAddress"
            } else {
                "无法获取本机 IP"
            }

            ToolResult(
                toolName = tool.name,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "获取 IP 失败"
            )
        } catch (e: Exception) {
            errorResult(tool.name, "获取 IP 失败: ${e.message}")
        }
    }

    /**
     * DNS 查询
     */
    suspend fun dnsLookup(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val domain = tool.parameters.find { it.name == "domain" }?.value
            ?: return@withContext errorResult(tool.name, "缺少 domain 参数")

        try {
            val addresses = InetAddress.getAllByName(domain)
            val ips = addresses.map { it.hostAddress }.filterNotNull().joinToString(", ")

            val success = ips.isNotEmpty()
            val resultMessage = if (success) {
                "$domain -> $ips"
            } else {
                "DNS 查询无结果: $domain"
            }

            ToolResult(
                toolName = tool.name,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "DNS 查询失败"
            )
        } catch (e: Exception) {
            errorResult(tool.name, "DNS 查询失败: ${e.message}")
        }
    }

    // ============ 辅助函数 ============

    private fun parseHeaders(headersStr: String): Map<String, String> {
        return headersStr.split("\n")
            .map { it.trim() }
            .filter { it.contains(":") }
            .associate { header ->
                val parts = header.split(":", limit = 2)
                val name = parts[0].trim()
                if (name.isBlank() || name.startsWith(":")) {
                    throw IllegalArgumentException("请求头名称不能为空或使用伪首部")
                }
                if (name.equals("Host", ignoreCase = true)) {
                    throw SecurityException("禁止自定义 Host 请求头")
                }
                name to parts.getOrElse(1) { "" }.trim()
            }
    }

    private suspend fun executeTextRequest(
        toolName: String,
        operation: String,
        request: Request,
        timeoutMs: Long,
    ): ToolResult {
        return executeWithRedirects(request, timeoutMs).use { response ->
            val body = readBodyLimited(response)
            val success = response.isSuccessful
            val resultMessage = "$operation ${if (success) "成功" else "失败"} " +
                "(${response.code}): ${body.take(200)}"
            ToolResult(
                toolName = toolName,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "HTTP Error: ${response.code}"
            )
        }
    }

    private suspend fun executeWithRedirects(initialRequest: Request, timeoutMs: Long): Response {
        val pinnedDns = PinnedPublicDns(dns)
        val requestClient = client.newBuilder()
            .dns(pinnedDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS), TimeUnit.MILLISECONDS)
            .build()
        var request = initialRequest
        var redirectCount = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            validateUrl(request.url, pinnedDns)
            val response = requestClient.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) {
                return response
            }
            if (redirectCount >= MAX_REDIRECTS) {
                response.close()
                throw IOException("重定向次数超过 $MAX_REDIRECTS 次")
            }
            val location = response.header("Location")
            val nextUrl = location?.let(request.url::resolve)
            if (nextUrl == null) {
                response.close()
                throw IOException("重定向响应缺少有效 Location")
            }
            if (request.url.isHttps && !nextUrl.isHttps) {
                response.close()
                throw SecurityException("禁止从 HTTPS 降级重定向到 HTTP")
            }

            val nextRequest = redirectedRequest(request, nextUrl, response.code)
            response.close()
            request = nextRequest
            redirectCount++
        }
    }

    private fun validateUrl(url: HttpUrl, pinnedDns: PinnedPublicDns) {
        if (url.scheme != "http" && url.scheme != "https") {
            throw SecurityException("仅允许 http/https URL")
        }
        if (url.host.isBlank() || url.port !in 1..65535) {
            throw SecurityException("URL 主机或端口非法")
        }
        pinnedDns.pin(url.host)
    }

    private fun redirectedRequest(request: Request, nextUrl: HttpUrl, code: Int): Request {
        val builder = request.newBuilder().url(nextUrl)
        val switchToGet = code == 303 ||
            ((code == 301 || code == 302) && request.method != "GET" && request.method != "HEAD")
        if (switchToGet) {
            builder.method("GET", null)
            builder.removeHeader("Content-Length")
            builder.removeHeader("Content-Type")
            builder.removeHeader("Transfer-Encoding")
        }
        if (!sameOrigin(request.url, nextUrl)) {
            builder.removeHeader("Authorization")
            builder.removeHeader("Cookie")
            builder.removeHeader("Proxy-Authorization")
        }
        return builder.build()
    }

    private fun sameOrigin(first: HttpUrl, second: HttpUrl): Boolean =
        first.scheme == second.scheme &&
            first.host.equals(second.host, ignoreCase = true) &&
            first.port == second.port

    private suspend fun readBodyLimited(response: Response): String {
        val body = response.body ?: return ""
        rejectDeclaredOversize(body.contentLength(), MAX_RESPONSE_BYTES, "HTTP 响应")
        val output = ByteArrayOutputStream()
        copyLimited(body.byteStream(), output, MAX_RESPONSE_BYTES) {
            currentCoroutineContext().ensureActive()
        }
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return output.toByteArray().toString(charset)
    }

    private fun extractFileName(url: HttpUrl): String {
        val fileName = url.pathSegments.lastOrNull().orEmpty()
        return fileName.ifBlank { "download_${System.currentTimeMillis()}" }
    }

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            throw IOException("目标文件系统不支持原子替换", e)
        }
    }

    private fun errorResult(toolName: String, error: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error = error
        )
    }

    companion object {
        internal const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
        internal const val MAX_DOWNLOAD_BYTES = 200L * 1024L * 1024L
        private const val MAX_REDIRECTS = 5
        private const val MAX_TIMEOUT_MS = 300_000L
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal class PinnedPublicDns(
    private val delegate: Dns,
) : Dns {
    private val pinned = mutableMapOf<String, List<InetAddress>>()

    fun pin(hostname: String): List<InetAddress> {
        val addresses = try {
            delegate.lookup(hostname)
        } catch (e: UnknownHostException) {
            throw SecurityException("Host '$hostname' cannot be resolved", e)
        }
        InetAddressGuard.requirePublic(hostname, addresses)
        val immutable = addresses.toList()
        pinned[hostname.lowercase(Locale.US)] = immutable
        return immutable
    }

    override fun lookup(hostname: String): List<InetAddress> =
        pinned[hostname.lowercase(Locale.US)] ?: pin(hostname)
}

internal fun rejectDeclaredOversize(contentLength: Long, limit: Long, label: String) {
    if (contentLength > limit) {
        throw SizeLimitExceededException("$label 超过大小限制 $limit bytes")
    }
}

internal suspend fun copyLimited(
    input: InputStream,
    output: OutputStream,
    limit: Long,
    checkCancelled: suspend () -> Unit = {},
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        checkCancelled()
        val read = input.read(buffer)
        if (read < 0) {
            return total
        }
        if (total > limit - read) {
            throw SizeLimitExceededException("内容超过大小限制 $limit bytes")
        }
        output.write(buffer, 0, read)
        total += read
    }
}

internal class SizeLimitExceededException(message: String) : IOException(message)

/**
 * 注册网络工具到 AIToolHandler
 */
fun registerNetworkTools(handler: AIToolHandler, executor: NetworkToolExecutor) {
    // HTTP GET
    handler.registerTool(
        name = "http_get",
        dangerCheck = { true },
        descriptionGenerator = { tool ->
            val url = tool.parameters.find { it.name == "url" }?.value ?: ""
            "HTTP GET: $url"
        },
        executor = { tool ->
            executor.httpGet(tool)
        }
    )

    // HTTP POST
    handler.registerTool(
        name = "http_post",
        dangerCheck = { true },
        descriptionGenerator = { tool ->
            val url = tool.parameters.find { it.name == "url" }?.value ?: ""
            "HTTP POST: $url"
        },
        executor = { tool ->
            executor.httpPost(tool)
        }
    )

    // Download
    handler.registerTool(
        name = "download",
        dangerCheck = { true },
        descriptionGenerator = { tool ->
            val url = tool.parameters.find { it.name == "url" }?.value ?: ""
            "下载文件: $url"
        },
        executor = { tool ->
            executor.download(tool)
        }
    )

    // Ping
    handler.registerTool(
        name = "ping",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val host = tool.parameters.find { it.name == "host" }?.value ?: ""
            "Ping: $host（自动允许；会向目标发起网络探测并暴露本机网络可达性）"
        },
        executor = { tool ->
            executor.ping(tool)
        }
    )

    // Get IP
    handler.registerTool(
        name = "get_ip",
        dangerCheck = { false },
        descriptionGenerator = { "获取本机 IP 地址（自动允许；会读取并暴露本机网络接口信息）" },
        executor = { tool ->
            executor.getIP(tool)
        }
    )

    // DNS Lookup
    handler.registerTool(
        name = "dns_lookup",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
            "DNS 查询: $domain（自动允许；会向 DNS 解析链发起网络探测）"
        },
        executor = { tool ->
            executor.dnsLookup(tool)
        }
    )

    if (BuildConfig.DEBUG) Log.d("NetworkTools", "网络工具注册完成")
}
