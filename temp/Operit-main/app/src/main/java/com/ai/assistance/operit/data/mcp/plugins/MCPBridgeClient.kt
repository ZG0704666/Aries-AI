package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** MCPBridgeClient - Client for communicating with MCP services through a bridge */
class MCPBridgeClient(context: Context, private val serviceName: String) {
    companion object {
        private const val TAG = "MCPBridgeClient"
    }

    private val bridge = MCPBridge.getInstance(context)
    private val isConnected = AtomicBoolean(false)
    private var lastPingTime = 0L

    /**
     * Connect to the MCP service.
     * If the service is registered but not active, this will attempt to spawn it.
     */
    suspend fun connect(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // 1. First, try a quick ping. If it's already running and responsive, we're good.
                    if (ping()) {
                        AppLogger.d(TAG, "Service $serviceName is already connected and responsive.")
                        isConnected.set(true)
                        return@withContext true
                    }

                    AppLogger.d(
                            TAG,
                            "Service $serviceName is not immediately responsive. Checking status and attempting to spawn if needed."
                    )

                    // 2. If ping fails, check the actual service info from the bridge.
                    val serviceInfo = getServiceInfo()

                    if (serviceInfo == null) {
                        AppLogger.w(TAG, "Service $serviceName is not registered with the bridge.")
                        isConnected.set(false)
                        return@withContext false
                    }

                    // 3. If it's registered but not active, try to spawn it.
                    if (!serviceInfo.active) {
                        AppLogger.i(
                                TAG,
                                "Service $serviceName is registered but not active. Attempting to spawn..."
                        )
                        val spawnSuccess = spawn()
                        if (!spawnSuccess) {
                            AppLogger.e(
                                    TAG,
                                    "Failed to spawn service $serviceName during connect sequence."
                            )
                            isConnected.set(false)
                            return@withContext false
                        }
                        // Give it a moment to initialize after spawning
                        delay(1000)
                    }

                    // 4. After spawning (or if it was already active but not ready), ping again to confirm.
                    val finalPingSuccess = ping()
                    if (finalPingSuccess) {
                        AppLogger.i(TAG, "Successfully connected to service $serviceName.")
                        isConnected.set(true)
                    } else {
                        AppLogger.e(
                                TAG,
                                "Failed to connect to service $serviceName even after spawn attempt."
                        )
                        isConnected.set(false)
                    }

                    return@withContext finalPingSuccess
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error connecting to MCP service $serviceName: ${e.message}", e)
                    isConnected.set(false)
                    return@withContext false
                }
            }

    /** Check if connected */
    fun isConnected(): Boolean = isConnected.get()

    /** Ping the service */
    suspend fun ping(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    val result = bridge.getServiceStatus(serviceName)

                    if (result != null && result.optBoolean("success", false)) {
                        val responseObj = result.optJSONObject("result")
                        
                        // getServiceStatus always returns single service object format
                        val active = responseObj?.optBoolean("active", false) ?: false
                        val ready = responseObj?.optBoolean("ready", false) ?: false
                        
                        // Check if this service is active and ready
                        if (active && ready) {
                            lastPingTime = System.currentTimeMillis() - startTime
                            isConnected.set(true)
                            return@withContext true
                        }

                        // Also consider it connected if active (even if not fully ready)
                        if (active) {
                            lastPingTime = System.currentTimeMillis() - startTime
                            isConnected.set(true)
                            return@withContext true
                        }

                        // If it's registered but not active, we're not truly connected
                        AppLogger.d(TAG, "Service $serviceName status - active: $active, ready: $ready")
                        return@withContext false
                    }
                    return@withContext false
                } catch (e: Exception) {
                    return@withContext false
                }
            }

    /** Synchronous ping method */
    fun pingSync(): Boolean = kotlinx.coroutines.runBlocking { ping() }

    /** Get last ping time */
    fun getLastPingTime(): Long = lastPingTime

    /** Spawn the MCP service if it's not already active */
    suspend fun spawn(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "Attempting to spawn service: $serviceName")
                    val serviceInfo = getServiceInfo()

                    if (serviceInfo?.active == true) {
                        AppLogger.d(TAG, "Service $serviceName is already active.")
                        if (!isConnected.get()) {
                            // If it's active but our client state is not connected, try to ping to
                            // sync up
                            return@withContext ping()
                        }
                        return@withContext true
                    }

                    val spawnResult = bridge.spawnMcpService(name = serviceName)
                    if (spawnResult?.optBoolean("success", false) == true) {
                        AppLogger.i(TAG, "Service $serviceName spawned successfully.")
                        // Wait a moment for the service to be fully ready before setting connected
                        // state
                        delay(500)
                        isConnected.set(true)
                        return@withContext true
                    } else {
                        val error =
                                spawnResult?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        AppLogger.e(TAG, "Failed to spawn service $serviceName: $error")
                        isConnected.set(false)
                        return@withContext false
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Exception during spawn for service $serviceName: ${e.message}", e)
                    isConnected.set(false)
                    return@withContext false
                }
            }

    /** Unspawn the MCP service (stops the process, but keeps it registered) */
    suspend fun unspawn(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "Attempting to unspawn service: $serviceName")
                    val unspawnResult = bridge.unspawnMcpService(name = serviceName)
                    if (unspawnResult?.optBoolean("success", false) == true) {
                        AppLogger.i(TAG, "Service $serviceName unspawned successfully.")
                        disconnect() // Set local state to disconnected
                        return@withContext true
                    } else {
                        val error =
                                unspawnResult?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        AppLogger.e(TAG, "Failed to unspawn service $serviceName: $error")
                        return@withContext false
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Exception during unspawn for service $serviceName: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Check if the service is currently active on the bridge */
    suspend fun isActive(): Boolean {
        return getServiceInfo()?.active ?: false
    }

    /** Call a tool on the MCP service - 返回完整的响应（包括 success, result, error） */
    suspend fun callTool(method: String, params: JSONObject): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        AppLogger.d(TAG, "尝试重新连接 $serviceName 服务")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            AppLogger.e(TAG, "无法连接到 $serviceName 服务")
                            // 返回一个包含错误信息的响应
                            return@withContext JSONObject().apply {
                                put("success", false)
                                put("error", JSONObject().apply {
                                    put("code", -1)
                                    put("message", "无法连接到 $serviceName 服务")
                                })
                            }
                        }
                    }

                    // Build parameters
                    val callParams =
                            JSONObject().apply {
                                put("method", method)
                                put("params", params)
                                put("name", serviceName)
                                put("id", UUID.randomUUID().toString())
                            }

                    // Build command
                    val command =
                            JSONObject().apply {
                                put("command", "toolcall")
                                put("id", UUID.randomUUID().toString())
                                put("params", callParams)
                            }

                    // Send command
                    val response = MCPBridge.sendCommand(command)

                    if (response == null) {
                        // 如果响应为空，返回一个包含错误信息的对象
                        return@withContext JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().apply {
                                put("code", -1)
                                put("message", "无法连接到桥接器或未收到响应")
                            })
                        }
                    }

                    // 返回完整的响应（包括 success, result, error）
                    if (response.optBoolean("success", false)) {
                        return@withContext response
                    } else {
                        val errorMsg =
                                response.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"

                        // Check for connection errors and handle reconnection
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout")
                        ) {
                            AppLogger.w(TAG, "检测到连接错误: $errorMsg, 标记为已断开")
                            isConnected.set(false)

                            // Try to reconnect once
                            AppLogger.d(TAG, "尝试立即重新连接")
                            if (connect()) {
                                // If reconnect succeeds, try the call again (one retry)
                                AppLogger.d(TAG, "重新连接成功，重试工具调用")
                                val retryCommand = JSONObject(command.toString())
                                val retryResponse = MCPBridge.sendCommand(retryCommand)

                                if (retryResponse != null) {
                                    return@withContext retryResponse
                                }
                            }
                        }

                        AppLogger.e(TAG, "工具调用错误: $errorMsg")
                        return@withContext response
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error calling tool $method: ${e.message}", e)
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    // 返回包含异常信息的响应
                    return@withContext JSONObject().apply {
                        put("success", false)
                        put("error", JSONObject().apply {
                            put("code", -1)
                            put("message", "调用工具时发生异常: ${e.message}")
                        })
                    }
                }
            }
    /** Synchronous tool call */
    fun callToolSync(method: String, params: JSONObject): JSONObject? {
        return kotlinx.coroutines.runBlocking { callTool(method, params) }
    }

    /** Synchronous tool call with Map */
    fun callToolSync(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> 
            // 将值转换为正确的 JSON 类型
            val jsonValue = convertToJsonType(value)
            paramsJson.put(key, jsonValue)
        }
        return callToolSync(method, paramsJson)
    }

    /**
     * 将 Kotlin 类型转换为 JSON 类型
     * - List -> JSONArray
     * - Map -> JSONObject
     * - 其他 -> 保持原样
     */
    private fun convertToJsonType(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is List<*> -> {
                val jsonArray = JSONArray()
                value.forEach { item ->
                    jsonArray.put(convertToJsonType(item))
                }
                jsonArray
            }
            is Map<*, *> -> {
                val jsonObject = JSONObject()
                value.forEach { (k, v) ->
                    if (k is String) {
                        jsonObject.put(k, convertToJsonType(v))
                    }
                }
                jsonObject
            }
            // 基本类型和 JSONObject/JSONArray 保持原样
            else -> value
        }
    }

    /** Get all tools provided by the service */
    suspend fun getTools(): List<JSONObject> =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        AppLogger.d(TAG, "尝试重新连接 $serviceName 服务以获取工具列表")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            AppLogger.e(TAG, "无法连接到 $serviceName 服务")
                            return@withContext emptyList()
                        }
                    }

                    // Get tools - pass the service name to be more specific
                    val response = bridge.listTools(serviceName)

                    if (response?.optBoolean("success", false) == true) {
                        val toolsArray =
                                response.optJSONObject("result")?.optJSONArray("tools")
                                        ?: return@withContext emptyList()

                        val tools = mutableListOf<JSONObject>()
                        for (i in 0 until toolsArray.length()) {
                            val tool = toolsArray.optJSONObject(i)
                            if (tool != null) {
                                tools.add(tool)
                            }
                        }

                        if (tools.isNotEmpty()) {
                            AppLogger.d(TAG, "成功获取 ${tools.size} 个工具")
                        } else {
                            AppLogger.w(TAG, "服务 $serviceName 未返回任何工具")
                        }

                        return@withContext tools
                    } else {
                        // Check for connection errors
                        val errorMsg =
                                response?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout") ||
                                        response == null
                        ) {
                            AppLogger.w(TAG, "获取工具列表时检测到连接错误，标记为已断开")
                            isConnected.set(false)
                        }

                        AppLogger.e(TAG, "获取工具列表失败: $errorMsg")
                        return@withContext emptyList()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting tools: ${e.message}")
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    return@withContext emptyList()
                }
            }

    /** Get tool names provided by the service as a simple list of strings */
    suspend fun getToolNames(): List<String> = 
            withContext(Dispatchers.IO) {
                try {
                    val tools = getTools()
                    return@withContext tools.mapNotNull { it.optString("name", null) }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting tool names: ${e.message}")
                    return@withContext emptyList()
                }
            }

    /** Get service info including tools count and running status */
    suspend fun getServiceInfo(): ServiceInfo? =
            withContext(Dispatchers.IO) {
                try {
                    val listResponse = bridge.listMcpServices() ?: return@withContext null
                    
                    if (listResponse.optBoolean("success", false)) {
                        val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                        
                        if (services != null) {
                            for (i in 0 until services.length()) {
                                val service = services.optJSONObject(i)
                                val name = service?.optString("name", "")
                                
                                if (name == serviceName) {
                                    val active = service.optBoolean("active", false)
                                    val ready = service.optBoolean("ready", false)
                                    val toolCount = service.optInt("toolCount", 0)
                                    
                                    // 从响应中提取工具名称列表
                                    val toolNames = mutableListOf<String>()
                                    val toolsArray = service.optJSONArray("tools")
                                    if (toolsArray != null) {
                                        for (j in 0 until toolsArray.length()) {
                                            val tool = toolsArray.optJSONObject(j)
                                            val toolName = tool?.optString("name", "")
                                            if (!toolName.isNullOrEmpty()) {
                                                toolNames.add(toolName)
                                            }
                                        }
                                    }
                                    
                                    return@withContext ServiceInfo(
                                        name = name,
                                        active = active,
                                        ready = ready,
                                        toolCount = toolCount,
                                        toolNames = toolNames
                                    )
                                }
                            }
                        }
                    }
                    return@withContext null
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting service info: ${e.message}")
                    return@withContext null
                }
            }

    /** Get tool descriptions provided by the service as a list of strings */
    suspend fun getToolDescriptions(): List<String> = 
            withContext(Dispatchers.IO) {
                try {
                    val tools = getTools()
                    return@withContext tools.mapNotNull { tool ->
                        val name = tool.optString("name", "")
                        val description = tool.optString("description", "")
                        if (name.isNotEmpty()) {
                            if (description.isNotEmpty()) {
                                "$name: $description"
                            } else {
                                name
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting tool descriptions: ${e.message}")
                    return@withContext emptyList()
                }
            }

    /** Disconnect from the service */
    fun disconnect() {
        isConnected.set(false)
    }
}

/** Data class to hold service information */
data class ServiceInfo(
    val name: String,
    val active: Boolean,
    val ready: Boolean,
    val toolCount: Int,
    val toolNames: List<String>
)
