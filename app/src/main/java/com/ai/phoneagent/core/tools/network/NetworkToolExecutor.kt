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
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 网络工具执行器
 * 提供 HTTP 请求、下载、Ping 等网络功能
 */
class NetworkToolExecutor(
    private val appContext: Context,
    private val client: OkHttpClient,
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
            // SSRF 防护：拒绝内网 / 私有 / 云元数据地址
            InetAddressGuard.requirePublic(URL(url).host)

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            // 添加自定义请求头
            headersStr?.let { headers ->
                parseHeaders(headers).forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()

            val body = response.body?.string() ?: ""
            val statusCode = response.code

            val success = response.isSuccessful
            val resultMessage = if (success) {
                "HTTP GET 成功 (${statusCode}): ${body.take(200)}"
            } else {
                "HTTP GET 失败 (${statusCode}): $body"
            }

            ToolResult(
                toolName = tool.name,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "HTTP Error: $statusCode"
            )
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

        try {
            // SSRF 防护：拒绝内网 / 私有 / 云元数据地址
            InetAddressGuard.requirePublic(URL(url).host)

            val requestBody = body.toRequestBody(contentType.toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", contentType)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val statusCode = response.code

            val success = response.isSuccessful
            val resultMessage = if (success) {
                "HTTP POST 成功 (${statusCode}): ${responseBody.take(200)}"
            } else {
                "HTTP POST 失败 (${statusCode}): $responseBody"
            }

            ToolResult(
                toolName = tool.name,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "HTTP Error: $statusCode"
            )
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

        try {
            // SSRF 防护：拒绝内网 / 私有 / 云元数据地址
            InetAddressGuard.requirePublic(URL(url).host)

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext errorResult(tool.name, "下载失败: ${response.code}")
            }

            val bytes = response.body?.bytes()
                ?: return@withContext errorResult(tool.name, "下载失败: 空响应")

            val fileName = if (savePath.isNullOrBlank()) {
                extractFileName(url)
            } else {
                savePath
            }

            // 保存文件到缓存目录
            val context = appContext
            val file = java.io.File(context.cacheDir, fileName)
            file.writeBytes(bytes)

            val success = file.exists()
            val resultMessage = if (success) {
                "下载成功: ${file.absolutePath} (${bytes.size} bytes)"
            } else {
                "下载失败: 文件写入失败"
            }

            ToolResult(
                toolName = tool.name,
                success = success,
                result = StringResultData(resultMessage),
                error = if (success) "" else "文件写入失败"
            )
        } catch (e: Exception) {
            errorResult(tool.name, "下载失败: ${e.message}")
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
            .associate {
                val parts = it.split(":", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }
    }

    private fun extractFileName(url: String): String {
        return try {
            val path = URL(url).path
            val fileName = path.substringAfterLast("/")
            if (fileName.isNotEmpty()) fileName else "download_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
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
        @Volatile private var fallbackInstance: NetworkToolExecutor? = null

        fun init(context: Context) {
            if (fallbackInstance == null) {
                fallbackInstance = NetworkToolExecutor(
                    context.applicationContext,
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build(),
                )
            }
        }

        private fun getInstance(): NetworkToolExecutor =
            runCatching {
                org.koin.core.context.GlobalContext.get().get<NetworkToolExecutor>()
            }.getOrNull() ?: (fallbackInstance ?: throw IllegalStateException("NetworkToolExecutor 未初始化"))

        suspend fun httpGet(tool: AITool): ToolResult = getInstance().httpGet(tool)
        suspend fun httpPost(tool: AITool): ToolResult = getInstance().httpPost(tool)
        suspend fun download(tool: AITool): ToolResult = getInstance().download(tool)
        suspend fun ping(tool: AITool): ToolResult = getInstance().ping(tool)
        suspend fun getIP(tool: AITool): ToolResult = getInstance().getIP(tool)
        suspend fun dnsLookup(tool: AITool): ToolResult = getInstance().dnsLookup(tool)
    }
}

/**
 * 注册网络工具到 AIToolHandler
 */
fun registerNetworkTools(handler: AIToolHandler, context: Context) {
    NetworkToolExecutor.init(context)

    // HTTP GET
    handler.registerTool(
        name = "http_get",
        dangerCheck = { true },
        descriptionGenerator = { tool ->
            val url = tool.parameters.find { it.name == "url" }?.value ?: ""
            "HTTP GET: $url"
        },
        executor = { tool ->
            NetworkToolExecutor.httpGet(tool)
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
            NetworkToolExecutor.httpPost(tool)
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
            NetworkToolExecutor.download(tool)
        }
    )

    // Ping
    handler.registerTool(
        name = "ping",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val host = tool.parameters.find { it.name == "host" }?.value ?: ""
            "Ping: $host"
        },
        executor = { tool ->
            NetworkToolExecutor.ping(tool)
        }
    )

    // Get IP
    handler.registerTool(
        name = "get_ip",
        dangerCheck = { false },
        descriptionGenerator = { "获取本机 IP 地址" },
        executor = { tool ->
            NetworkToolExecutor.getIP(tool)
        }
    )

    // DNS Lookup
    handler.registerTool(
        name = "dns_lookup",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
            "DNS 查询: $domain"
        },
        executor = { tool ->
            NetworkToolExecutor.dnsLookup(tool)
        }
    )

    if (BuildConfig.DEBUG) Log.d("NetworkTools", "网络工具注册完成")
}
