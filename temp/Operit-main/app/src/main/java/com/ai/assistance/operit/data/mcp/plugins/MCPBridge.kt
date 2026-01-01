package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * MCPBridge - 用于与TCP桥接器通信的插件类 支持以下命令:
 * - spawn: 启动新的MCP服务
 * - shutdown: 关闭当前MCP服务
 * - listtools: 列出所有可用工具
 * - toolcall: 调用特定工具
 * - list: 列出已注册的MCP服务或查询单个服务状态
 * - register: 注册新的MCP服务
 * - unregister: 取消注册MCP服务
 * - reset: 重置桥接器
 */
class MCPBridge private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPBridge"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val BRIDGE_PORT = 8752  // 远程bridge监听的端口
        private const val CLIENT_PORT = 8751  // Android客户端连接的端口（SSH转发）
        private const val TERMUX_BRIDGE_PATH = "~/bridge"
        private var appContext: Context? = null
        
        @Volatile
        private var INSTANCE: MCPBridge? = null
        
        fun getInstance(context: Context): MCPBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MCPBridge(context.applicationContext).also { 
                    INSTANCE = it
                    appContext = context.applicationContext
                }
            }
        }
        
        /**
         * 智能检测可用端口
         * 优先尝试 8752（本地直连），失败后尝试 8751（SSH转发）
         */
        private suspend fun detectPort(): Int = withContext(Dispatchers.IO) {
            // 优先尝试 8752（远程端口）- 本地环境
            if (isPortAvailable(DEFAULT_HOST, BRIDGE_PORT)) {
                AppLogger.d(TAG, "检测到本地环境，使用端口 $BRIDGE_PORT")
                return@withContext BRIDGE_PORT
            }
            
            // 降级到 8751（SSH转发端口）
            AppLogger.d(TAG, "本地连接失败，尝试SSH转发端口 $CLIENT_PORT")
            return@withContext CLIENT_PORT
        }
        
        /**
         * 检查端口是否可用（快速检测，无日志污染）
         */
        private fun isPortAvailable(host: String, port: Int): Boolean {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.reuseAddress = true
                socket.connect(java.net.InetSocketAddress(host, port), 500) // 500ms快速检测
                socket.isConnected
            } catch (e: Exception) {
                false // 静默失败，不记录日志
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // 静默关闭
                }
            }
        }

        // 部署桥接器到终端
        suspend fun deployBridge(context: Context, sessionId: String? = null): Boolean {
            appContext = context.applicationContext
            return withContext(Dispatchers.IO) {
                try {
                    // 1. 首先将桥接器从assets复制到sdcard/Download/Operit/bridge目录
                    val sdcardPath = "/sdcard"
                    val operitDir = File("$sdcardPath/Download/Operit")
                    if (!operitDir.exists()) {
                        operitDir.mkdirs()
                    }

                    val publicBridgeDir = File(operitDir, "bridge")
                    if (!publicBridgeDir.exists()) {
                        publicBridgeDir.mkdirs()
                    }

                    // 复制打包后的 index.js 到公共目录（已包含所有依赖）
                    val inputStream = context.assets.open("bridge/index.js")
                    val indexJsContent = inputStream.bufferedReader().use { it.readText() }
                    val outputFile = File(publicBridgeDir, "index.js")
                    outputFile.writeText(indexJsContent)
                    inputStream.close()

                    // 复制打包后的 spawn-helper.js 到公共目录（已包含所有依赖）
                    val spawnHelperInputStream = context.assets.open("bridge/spawn-helper.js")
                    val spawnHelperJsContent = spawnHelperInputStream.bufferedReader().use { it.readText() }
                    val spawnHelperOutputFile = File(publicBridgeDir, "spawn-helper.js")
                    spawnHelperOutputFile.writeText(spawnHelperJsContent)
                    spawnHelperInputStream.close()

                    AppLogger.d(TAG, "桥接器文件已复制到公共目录: ${publicBridgeDir.absolutePath}")

                    // 2. 确保终端目录存在并复制文件
                    // 获取终端管理器
                    val terminal = Terminal.getInstance(context)
                    
                    // 确保已连接到终端服务
                    if (!terminal.isConnected()) {
                        val connected = terminal.initialize()
                        if (!connected) {
                            AppLogger.e(TAG, "无法连接到终端服务")
                            return@withContext false
                        }
                    }

                    // 使用传入的sessionId或创建新的会话
                    val actualSessionId = sessionId ?: run {
                        val newSessionId = terminal.createSession("mcp-bridge-deploy")
                        if (newSessionId == null) {
                            AppLogger.e(TAG, "无法创建终端会话或会话初始化超时")
                            return@withContext false
                        }
                        newSessionId
                    }

                    // 使用sdcard路径而不是Android storage路径
                    val sdcardBridgePath = "/sdcard/Download/Operit/bridge"
                    
                    // 获取 AIToolHandler 实例
                    val toolHandler = AIToolHandler.getInstance(context)
                    
                    // 先创建目标目录
                    val mkdirCommand = "mkdir -p $TERMUX_BRIDGE_PATH"
                    terminal.executeCommand(actualSessionId, mkdirCommand)
                    delay(100) // 等待目录创建
                    
                    // 使用 AIToolHandler 复制打包后的文件（跨环境复制：Android -> Linux）
                    // 打包后的文件已包含所有依赖，不需要 package.json 和 node_modules
                    val filesToCopy = listOf("index.js", "spawn-helper.js")
                    
                    for (fileName in filesToCopy) {
                        val copyTool = AITool(
                            name = "copy_file",
                            parameters = listOf(
                                ToolParameter("source", "$sdcardBridgePath/$fileName"),
                                ToolParameter("destination", "$TERMUX_BRIDGE_PATH/$fileName"),
                                ToolParameter("source_environment", "android"),
                                ToolParameter("dest_environment", "linux"),
                                ToolParameter("recursive", "false")
                            )
                        )
                        
                        val result = toolHandler.executeTool(copyTool)
                        if (!result.success) {
                            AppLogger.e(TAG, "复制文件 $fileName 失败: ${result.error}")
                            return@withContext false
                        }
                        AppLogger.d(TAG, "成功复制文件: $fileName")
                    }
                    
                    // 打包后的文件已包含所有依赖，无需安装 node_modules

                    AppLogger.d(TAG, "桥接器成功部署到终端")
                    return@withContext true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "部署桥接器异常", e)
                    return@withContext false
                }
            }
        }

        // 在终端中启动桥接器
        suspend fun startBridge(
                context: Context? = null,
                port: Int = BRIDGE_PORT,
                mcpCommand: String? = null,
                mcpArgs: List<String>? = null,
                sessionId: String? = null
        ): Boolean =
                withContext(Dispatchers.IO) {
                    try {
                        // 使用传入的context或保存的appContext
                        val ctx = context ?: appContext
                        if (ctx == null) {
                            AppLogger.e(TAG, "没有可用的上下文，无法执行命令")
                            return@withContext false
                        }

                        // 首先检查桥接器是否已经在运行
                        val listResult = getInstance(ctx).listMcpServices()
                        if (listResult != null && listResult.optBoolean("success", false)) {
                            AppLogger.d(TAG, "桥接器已经在运行，无需重新启动")
                            return@withContext true
                        }

                        // 获取终端管理器
                        val terminal = Terminal.getInstance(ctx)
                        
                        // 确保已连接到终端服务
                        if (!terminal.isConnected()) {
                            val connected = terminal.initialize()
                            if (!connected) {
                                AppLogger.e(TAG, "无法连接到终端服务")
                                return@withContext false
                            }
                        }

                        // 使用传入的sessionId或创建新的会话
                        val actualSessionId = sessionId ?: run {
                            val newSessionId = terminal.createSession("mcp-bridge-daemon")
                            if (newSessionId == null) {
                                AppLogger.e(TAG, "无法创建终端会话或会话初始化超时")
                                return@withContext false
                            }
                            newSessionId
                        }

                        // 构建启动命令 - 使用后台方式运行
                        val command = StringBuilder("cd $TERMUX_BRIDGE_PATH && node index.js $port")
                        if (mcpCommand != null) {
                            command.append(" $mcpCommand")
                            if (mcpArgs != null && mcpArgs.isNotEmpty()) {
                                command.append(" ${mcpArgs.joinToString(" ")}")
                            }
                        }
                        command.append(" &")

                        AppLogger.d(TAG, "发送启动命令: $command")

                        // 异步方式发送启动命令 - 不等待完成，因为它会作为后台进程一直运行
                        AppLogger.d(TAG, "进行桥接器启动...")
                        terminal.executeCommand(actualSessionId, command.toString())

                        // 等待一段时间让桥接器启动
                        AppLogger.d(TAG, "等待桥接器启动...")
                        delay(2000)

                        // 验证桥接器是否成功启动 - 尝试三次
                        var isRunning = false
                        for (i in 1..3) {
                            val checkResult = getInstance(ctx).listMcpServices()
                            if (checkResult != null && checkResult.optBoolean("success", false)) {
                                AppLogger.d(TAG, "桥接器成功启动，list响应: $checkResult")
                                isRunning = true
                                break
                            }
                            AppLogger.d(TAG, "第${i}次尝试连接桥接器失败，等待1秒后重试")
                            delay(1000)
                        }

                        // 如果三次尝试后仍然无法ping通，检查日志
                        if (!isRunning) {
                            AppLogger.e(TAG, "桥接器可能未成功启动。请检查终端会话 'mcp-bridge-daemon' 的输出。")
                        }

                        return@withContext isRunning
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "启动桥接器异常", e)
                        return@withContext false
                    }
                }

        // 重置桥接器（静态方法）
        suspend fun reset(context: Context? = null): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        AppLogger.d(TAG, "重置桥接器 - 关闭所有服务并清空注册表...")
                        val command =
                                JSONObject().apply {
                                    put("command", "reset")
                                    put("id", UUID.randomUUID().toString())
                                }

                        val response = sendCommand(command)
                        if (response?.optBoolean("success", false) == true) {
                            AppLogger.i(TAG, "桥接器重置成功")
                        } else {
                            AppLogger.w(TAG, "桥接器重置失败")
                        }
                        return@withContext response
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "重置桥接器异常", e)
                        return@withContext null
                    }
                }

        // 发送命令到桥接器
        suspend fun sendCommand(
                command: JSONObject,
                host: String = DEFAULT_HOST,
                port: Int? = null
        ): JSONObject? =
                withContext(Dispatchers.IO) {
                    var socket: Socket? = null
                    var writer: PrintWriter? = null
                    var reader: BufferedReader? = null

                    try {
                        // 自动检测端口（如果未指定）
                        val actualPort = port ?: detectPort()
                        
                        // Extract command details for better logging
                        val cmdType = command.optString("command", "unknown")
                        val cmdId = command.optString("id", "no-id")
                        val params = command.optJSONObject("params")

                        // Enhanced logging with special handling for commands with service names
                        val serviceName = params?.optString("name")
                        val logMessage =
                                if (serviceName != null && serviceName.isNotEmpty()) {
                                    "发送桥接器命令[$cmdId]: $cmdType 服务: $serviceName 其他参数: ${params.toString()}"
                                } else {
                                    "发送桥接器命令[$cmdId]: $cmdType ${if (params != null) "参数: $params" else ""}"
                                }

                        AppLogger.d(TAG, logMessage)

                        // Create socket with timeout and proper options
                        socket = Socket()
                        socket.reuseAddress = true
                        socket.soTimeout = 180000 // 180 seconds read timeout (3 minutes)
                        socket.connect(
                                java.net.InetSocketAddress(host, actualPort),
                                5000
                        ) // 5 seconds connect timeout

                        writer = PrintWriter(socket.getOutputStream(), true)
                        reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                        // 发送命令
                        writer.println(command.toString())
                        writer.flush()

                        // 读取响应
                        val response = reader.readLine()
                        if (response != null) {
                            try {
                                val jsonResponse = JSONObject(response)
                                val success = jsonResponse.optBoolean("success", false)
                                val result = jsonResponse.optJSONObject("result")
                                val error = jsonResponse.optJSONObject("error")

                                // Log the raw JSON response first for detailed debugging
                                AppLogger.d(TAG, "命令[$cmdId: $cmdType]原始JSON响应: $response")

                                // Enhanced response logging
                                val responseLog = StringBuilder()
                                responseLog.append("命令[$cmdId: $cmdType")
                                if (serviceName != null) responseLog.append(" 服务: $serviceName")
                                responseLog.append("]响应: ${if (success) "成功" else "失败"} ")

                                if (result != null) {
                                    // For listtools, show both the summary and full result
                                    if (cmdType == "listtools" && result.has("tools")) {
                                        val tools = result.optJSONArray("tools")
                                        val toolCount = tools?.length() ?: 0
                                        responseLog.append("获取到 $toolCount 个工具")
                                        // Add tool names if available
                                        if (toolCount > 0) {
                                            responseLog.append(" [")
                                            for (i in 0 until toolCount) {
                                                val tool = tools?.optJSONObject(i)
                                                val toolName = tool?.optString("name", "未命名工具")
                                                if (i > 0) responseLog.append(", ")
                                                responseLog.append(toolName)
                                                if (i >= 2 && toolCount > 3) {
                                                    responseLog.append("... (共 $toolCount 个)")
                                                    break
                                                }
                                            }
                                            responseLog.append("]")
                                        }
                                    } else {
                                        responseLog.append("结果: $result")
                                    }
                                }

                                if (error != null) responseLog.append(" 错误: $error")

                                AppLogger.d(TAG, responseLog.toString())

                                return@withContext jsonResponse
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "解析响应失败: $response", e)
                                return@withContext null
                            }
                        } else {
                            AppLogger.e(TAG, "命令[$cmdId: $cmdType]没有收到响应")
                            return@withContext null
                        }
                    } catch (e: Exception) {
                        // 简化错误日志 - 只记录关键信息
                        val cmdType = command.optString("command", "unknown")
                        AppLogger.e(TAG, "发送命令失败[$cmdType]: ${e.message}")
                        return@withContext null
                    } finally {
                        // 静默清理资源
                        try { writer?.close() } catch (e: Exception) { }
                        try { reader?.close() } catch (e: Exception) { }
                        try { socket?.close() } catch (e: Exception) { }
                    }
                }
    }

    // 注册新的MCP服务
    suspend fun registerMcpService(
            name: String,
            command: String,
            args: List<String> = emptyList(),
            description: String? = null,
            env: Map<String, String>? = null,
            cwd: String? = null
    ): JSONObject? {
        val params =
                JSONObject().apply {
                    put("type", "local") // Explicitly set type for local services
                    put("name", name)
                    put("command", command)
                    if (args.isNotEmpty()) {
                        // 显式创建JSONArray
                        val argsArray = JSONArray()
                        for (arg in args) {
                            argsArray.put(arg)
                        }
                        put("args", argsArray)
                    }
                    if (description != null) {
                        put("description", description)
                    }
                    if (env != null && env.isNotEmpty()) {
                        val envObj = JSONObject()
                        env.forEach { (key, value) -> envObj.put(key, value) }
                        put("env", envObj)
                    }
                    if (cwd != null) {
                        put("cwd", cwd)
                    }
                }

        val commandObj =
                JSONObject().apply {
                    put("command", "register")
                    put("id", UUID.randomUUID().toString())
                    put("params", params)
                }

        return sendCommand(commandObj)
    }

    // Overload for remote services
    suspend fun registerMcpService(
        name: String,
        type: String,
        endpoint: String,
        connectionType: String?,
        description: String? = null,
        bearerToken: String? = null,
        headers: Map<String, String>? = null
    ): JSONObject? {
        val params =
            JSONObject().apply {
                put("type", type)
                put("name", name)
                put("endpoint", endpoint)
                if (connectionType != null) {
                    put("connectionType", connectionType)
                }
                if (description != null) {
                    put("description", description)
                }
                if (bearerToken != null) {
                    put("bearerToken", bearerToken)
                }
                if (headers != null && headers.isNotEmpty()) {
                    val headersObj = JSONObject()
                    headers.forEach { (key, value) -> headersObj.put(key, value) }
                    put("headers", headersObj)
                }
            }

        val commandObj =
            JSONObject().apply {
                put("command", "register")
                put("id", UUID.randomUUID().toString())
                put("params", params)
            }

        return sendCommand(commandObj)
    }

    // 取消注册MCP服务
    suspend fun unregisterMcpService(name: String): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "unregister")
                    put("id", UUID.randomUUID().toString())
                    put("params", JSONObject().apply { put("name", name) })
                }

        return sendCommand(command)
    }

    // 列出所有注册的MCP服务或查询单个服务
    suspend fun listMcpServices(serviceName: String? = null): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "list")
                    put("id", UUID.randomUUID().toString())
                    if (serviceName != null) {
                        put("params", JSONObject().apply { put("name", serviceName) })
                    }
                }

        return sendCommand(command)
    }

    // 启动MCP服务
    suspend fun spawnMcpService(
            name: String? = null,
            command: String? = null,
            args: List<String>? = null,
            env: Map<String, String>? = null,
            cwd: String? = null
    ): JSONObject? {
        val params = JSONObject()

        if (name != null) {
            params.put("name", name)
        }
        // Command and args are now only relevant for direct, unregistered local spawns
        if (command != null) {
            params.put("command", command)
        }
        if (args != null && args.isNotEmpty()) {
            // 使用JSONArray来正确处理数组
            val jsonArray = JSONArray()
            for (arg in args) {
                jsonArray.put(arg)
            }
            params.put("args", jsonArray)
        }
        if (env != null && env.isNotEmpty()) {
            val envObj = JSONObject()
            env.forEach { (key, value) -> envObj.put(key, value) }
            params.put("env", envObj)
        }
        if (cwd != null) {
            params.put("cwd", cwd)
        }

        val commandObj =
                JSONObject().apply {
                    put("command", "spawn")
                    put("id", UUID.randomUUID().toString())
                    put("params", params)
                }

        return sendCommand(commandObj)
    }

    // 停止MCP服务（不注销）
    suspend fun unspawnMcpService(name: String): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "unspawn")
                    put("id", UUID.randomUUID().toString())
                    put("params", JSONObject().apply { put("name", name) })
                }

        return sendCommand(command)
    }

    // 获取工具列表
    suspend fun listTools(serviceName: String? = null): JSONObject? {
        val params = JSONObject()

        // Add service name if provided
        if (serviceName != null) {
            params.put("name", serviceName)
        }

        val command =
                JSONObject().apply {
                    put("command", "listtools")
                    put("id", UUID.randomUUID().toString())

                    // Only add params if we have any
                    if (params.length() > 0) {
                        put("params", params)
                    }
                }

        // Enhanced logging with service name
        AppLogger.d(TAG, "获取工具列表${if (serviceName != null) " 服务: $serviceName" else " (默认服务)"}")

        return sendCommand(command)
    }

    // 缓存工具列表到bridge（用于已有缓存的插件）
    suspend fun cacheTools(serviceName: String, tools: List<JSONObject>): JSONObject? {
        val toolsArray = JSONArray()
        tools.forEach { tool ->
            toolsArray.put(tool)
        }

        val params = JSONObject().apply {
            put("name", serviceName)
            put("tools", toolsArray)
        }

        val command = JSONObject().apply {
            put("command", "cachetools")
            put("id", UUID.randomUUID().toString())
            put("params", params)
        }

        AppLogger.d(TAG, "缓存工具列表到bridge 服务: $serviceName 工具数: ${tools.size}")

        return sendCommand(command)
    }

    // 调用工具
    suspend fun callTool(method: String, params: JSONObject): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "toolcall")
                    put("id", UUID.randomUUID().toString())
                    put(
                            "params",
                            JSONObject().apply {
                                put("method", method)
                                put("params", params)
                            }
                    )
                }

        return sendCommand(command)
    }

    // 简化调用工具的方法
    suspend fun toolcall(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callTool(method, paramsJson)
    }

    /**
     * 查询特定MCP服务的状态
     *
     * @param serviceName 要查询的服务名称
     * @return 服务信息响应，如果失败则返回null
     */
    suspend fun getServiceStatus(serviceName: String): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "查询服务 $serviceName 的状态")
                    return@withContext listMcpServices(serviceName)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "查询服务状态时出错: ${e.message}")
                    return@withContext null
                }
            }

    /**
     * 重置桥接器 - 关闭所有服务、清空注册表和池子
     * 
     * @return 重置是否成功
     */
    suspend fun resetBridge(): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "开始重置桥接器，关闭所有服务并清空注册表...")

                    val command =
                            JSONObject().apply {
                                put("command", "reset")
                                put("id", UUID.randomUUID().toString())
                            }

                    val response = sendCommand(command)

                    if (response?.optBoolean("success", false) == true) {
                        AppLogger.i(TAG, "桥接器重置成功")
                        return@withContext response
                    } else {
                        AppLogger.w(TAG, "桥接器重置失败")
                        return@withContext null
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "重置桥接器时出错: ${e.message}")
                    return@withContext null
                }
            }
}
