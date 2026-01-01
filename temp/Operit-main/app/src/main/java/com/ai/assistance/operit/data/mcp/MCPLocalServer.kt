package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 统一的MCP配置管理中心
 * 
 * 负责管理所有MCP相关的配置，包括：
 * - 官方MCP配置格式的读写
 * - 插件配置管理
 * - 服务器状态管理
 * - 统一存储在下载/Operit/mcp_plugins目录
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PATH = "server_path"
        
        // 配置文件名称
        private const val MCP_CONFIG_FILE = "mcp_config.json"
        private const val SERVER_STATUS_FILE = "server_status.json"
        
        // 默认目录名称
        private const val OPERIT_DIR_NAME = "Operit"
        private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"

        @Volatile private var INSTANCE: MCPLocalServer? = null

        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 配置目录路径
    private val configBaseDir by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        val mcpPluginsDir = File(operitDir, MCP_PLUGINS_DIR_NAME)
        
        // 确保目录存在
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }
        if (!mcpPluginsDir.exists()) {
            mcpPluginsDir.mkdirs()
        }
        
        mcpPluginsDir
    }

    // 配置文件路径
    private val mcpConfigFile get() = File(configBaseDir, MCP_CONFIG_FILE)
    private val serverStatusFile get() = File(configBaseDir, SERVER_STATUS_FILE)

    // 服务路径
    private val _serverPath = MutableStateFlow(configBaseDir.absolutePath)
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    // 配置状态
    private val _mcpConfig = MutableStateFlow(MCPConfig())
    val mcpConfig: StateFlow<MCPConfig> = _mcpConfig.asStateFlow()

    // 插件元数据 - 现在从MCPConfig派生
    val pluginMetadata: StateFlow<Map<String, PluginMetadata>> = _mcpConfig
        .map { it.pluginMetadata.toMap() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    // 服务器状态
    private val _serverStatus = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val serverStatus: StateFlow<Map<String, ServerStatus>> = _serverStatus.asStateFlow()

    // Gson实例 - 使用格式化输出
    private val gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        // 初始化时加载所有配置
        loadAllConfigurations()
    }

    // ==================== 官方MCP配置格式支持 ====================

    /**
     * 官方MCP配置格式数据结构
     */
    @Serializable
    data class MCPConfig(
        @SerializedName("mcpServers")
        val mcpServers: MutableMap<String, ServerConfig> = mutableMapOf(),
        @SerializedName("pluginMetadata")
        val pluginMetadata: MutableMap<String, PluginMetadata> = mutableMapOf()
    ) {
        @Serializable
        data class ServerConfig(
            @SerializedName("command")
            val command: String,
            @SerializedName("args")
            val args: List<String>? = emptyList(),
            @SerializedName("disabled")
            val disabled: Boolean = false,
            @SerializedName("autoApprove")
            val autoApprove: List<String>? = emptyList(),
            @SerializedName("env")
            val env: Map<String, String>? = emptyMap()
        )
    }

    /**
     * 插件元数据
     */
    @Serializable
    data class PluginMetadata(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String,
        @SerializedName("logoUrl")
        val logoUrl: String? = null,
        @SerializedName("author")
        val author: String = "Unknown",
        @SerializedName("isInstalled")
        val isInstalled: Boolean = false,
        @SerializedName("version")
        val version: String = "",
        @SerializedName("updatedAt")
        val updatedAt: String = "",
        @SerializedName("longDescription")
        val longDescription: String = "",
        @SerializedName("repoUrl")
        val repoUrl: String = "",
        // 新增字段以支持远程服务
        @SerializedName("type")
        val type: String = "local", // "local" or "remote"
        @SerializedName("endpoint")
        val endpoint: String? = null,
        @SerializedName("connectionType")
        val connectionType: String? = "httpStream",
        // 认证相关字段（用于远程服务）
        @SerializedName("bearerToken")
        val bearerToken: String? = null,
        @SerializedName("headers")
        val headers: Map<String, String>? = null,
        // 本地安装相关字段
        @SerializedName("installedPath")
        val installedPath: String? = null,
        @SerializedName("installedTime")
        val installedTime: Long = System.currentTimeMillis(),
        // 市场配置（来自 GitHub Issue）
        @SerializedName("marketConfig")
        val marketConfig: String? = null
    )

    /**
     * 服务器运行状态
     * 注意：启用/禁用状态已移至ServerConfig.disabled字段
     */
    @Serializable
    data class ServerStatus(
        @SerializedName("serverId")
        val serverId: String,
        @SerializedName("active")
        val active: Boolean = false,
        @SerializedName("lastStartTime")
        val lastStartTime: Long = 0L,
        @SerializedName("lastStopTime")
        val lastStopTime: Long = 0L,
        @SerializedName("errorMessage")
        val errorMessage: String? = null,
        @SerializedName("cachedTools")
        val cachedTools: List<CachedToolInfo>? = null,
        @SerializedName("toolsCachedTime")
        val toolsCachedTime: Long = 0L
    )

    /**
     * 缓存的工具信息
     */
    @Serializable
    data class CachedToolInfo(
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String = "",
        @SerializedName("inputSchema")
        val inputSchema: String = "{}", // JSON字符串形式的schema
        @SerializedName("cachedAt")
        val cachedAt: Long = System.currentTimeMillis()
    )

    // ==================== 配置文件操作 ====================
    
    /**
     * 重新加载配置文件（用于用户手动编辑配置后刷新）
     */
    suspend fun reloadConfigurations() {
        withContext(Dispatchers.IO) {
            loadAllConfigurations()
            AppLogger.d(TAG, "配置已重新加载")
        }
    }

    /**
     * 加载所有配置文件
     */
    private fun loadAllConfigurations() {
        try {
            // 加载MCP配置
            if (mcpConfigFile.exists()) {
                val configJson = mcpConfigFile.readText()
                val config = gson.fromJson(configJson, MCPConfig::class.java) ?: MCPConfig()
                
                // 自动为 mcpServers 中存在但 pluginMetadata 中缺失的服务器创建默认元数据
                val updatedConfig = autoFillMissingMetadata(config)
                _mcpConfig.value = updatedConfig
                
                // 如果有新增的元数据，保存配置
                if (updatedConfig.pluginMetadata.size > config.pluginMetadata.size) {
                    coroutineScope.launch {
                        saveMCPConfig()
                        AppLogger.d(TAG, "自动创建了 ${updatedConfig.pluginMetadata.size - config.pluginMetadata.size} 个缺失的插件元数据")
                    }
                }
            }

            // 加载服务器状态
            if (serverStatusFile.exists()) {
                val statusJson = serverStatusFile.readText()
                val typeToken = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val status = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken) ?: emptyMap()
                _serverStatus.value = status
            }
            
            // 为新配置的服务器初始化状态
            initializeMissingServerStatus()

            AppLogger.d(TAG, "配置加载完成 - MCP服务器: ${_mcpConfig.value.mcpServers.size}, 插件元数据: ${_mcpConfig.value.pluginMetadata.size}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载配置时出错", e)
        }
    }
    
    /**
     * 自动为缺失的服务器创建默认元数据
     */
    private fun autoFillMissingMetadata(config: MCPConfig): MCPConfig {
        val newMetadata = config.pluginMetadata.toMutableMap()
        var hasNewMetadata = false
        
        config.mcpServers.forEach { (serverId, serverConfig) ->
            if (!newMetadata.containsKey(serverId)) {
                // 从 serverId 生成友好的显示名称
                val displayName = serverId
                    .replace("_", " ")
                    .replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                
                // 创建默认元数据
                val metadata = PluginMetadata(
                    id = serverId,
                    name = displayName,
                    description = "",
                    logoUrl = null,
                    author = "Unknown",
                    isInstalled = true,
                    version = "1.0.0",
                    updatedAt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    longDescription = "通过配置文件自动识别的 MCP 服务器",
                    repoUrl = "",
                    type = "local",
                    endpoint = null,
                    connectionType = "httpStream"
                )
                
                newMetadata[serverId] = metadata
                hasNewMetadata = true
                AppLogger.d(TAG, "自动创建元数据: $serverId -> $displayName")
            }
        }
        
        return if (hasNewMetadata) {
            config.copy(pluginMetadata = newMetadata)
        } else {
            config
        }
    }
    
    /**
     * 为新配置的服务器初始化状态
     */
    private fun initializeMissingServerStatus() {
        val currentStatus = _serverStatus.value.toMutableMap()
        var hasNewStatus = false
        
        _mcpConfig.value.mcpServers.forEach { (serverId, _) ->
            if (!currentStatus.containsKey(serverId)) {
                currentStatus[serverId] = ServerStatus(
                    serverId = serverId,
                    active = false,
                    lastStartTime = 0L,
                    lastStopTime = 0L,
                    errorMessage = null
                )
                hasNewStatus = true
                AppLogger.d(TAG, "初始化服务器状态: $serverId")
            }
        }
        
        if (hasNewStatus) {
            _serverStatus.value = currentStatus
            coroutineScope.launch {
                saveServerStatus()
            }
        }
    }

    /**
     * 保存MCP配置
     */
    suspend fun saveMCPConfig() {
        try {
            val configJson = gson.toJson(_mcpConfig.value)
            mcpConfigFile.writeText(configJson)
            AppLogger.d(TAG, "MCP配置已保存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存MCP配置时出错", e)
        }
    }

    /**
     * 保存服务器状态
     */
    suspend fun saveServerStatus() {
        try {
            val statusJson = gson.toJson(_serverStatus.value)
            serverStatusFile.writeText(statusJson)
            AppLogger.d(TAG, "服务器状态已保存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存服务器状态时出错", e)
        }
    }

    // ==================== MCP服务器管理 ====================

    /**
     * 添加或更新MCP服务器配置
     */
    suspend fun addOrUpdateMCPServer(
        serverId: String,
        command: String,
        args: List<String>? = emptyList(),
        env: Map<String, String>? = emptyMap(),
        disabled: Boolean = false,
        autoApprove: List<String>? = emptyList()
    ) {
        _mcpConfig.update { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            newServers[serverId] = MCPConfig.ServerConfig(
                command = command,
                args = args ?: emptyList(),
                disabled = disabled,
                autoApprove = autoApprove ?: emptyList(),
                env = env ?: emptyMap()
            )
            currentConfig.copy(mcpServers = newServers)
        }
        saveMCPConfig()
        AppLogger.d(TAG, "MCP服务器配置已更新: $serverId")
    }

    /**
     * 删除MCP服务器配置
     */
    suspend fun removeMCPServer(serverId: String) {
        _mcpConfig.update { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            newServers.remove(serverId)
            currentConfig.copy(mcpServers = newServers)
        }
        saveMCPConfig()

        // 同时清理相关的元数据和状态
        removePluginMetadata(serverId)
        removeServerStatus(serverId)
        
        AppLogger.d(TAG, "MCP服务器配置已删除: $serverId")
    }

    /**
     * 合并JSON配置到现有配置
     */
    suspend fun mergeConfigFromJson(jsonConfig: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "开始合并配置，输入长度: ${jsonConfig.length}")
                AppLogger.d(TAG, "配置内容预览: ${jsonConfig.take(200)}")
                
                val parsedConfig = try {
                    gson.fromJson(jsonConfig, MCPConfig::class.java)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "JSON 解析失败", e)
                    return@withContext Result.failure(Exception("JSON 格式错误: ${e.message}"))
                }
                
                if (parsedConfig?.mcpServers == null) {
                    AppLogger.e(TAG, "配置解析结果为 null 或 mcpServers 字段为 null")
                    return@withContext Result.failure(Exception("配置中没有找到 mcpServers 字段"))
                }
                
                if (parsedConfig.mcpServers.isEmpty()) {
                    AppLogger.e(TAG, "mcpServers 为空")
                    return@withContext Result.failure(Exception("配置中 mcpServers 为空"))
                }
                
                AppLogger.d(TAG, "解析到 ${parsedConfig.mcpServers.size} 个服务器配置")
                parsedConfig.mcpServers.forEach { (serverId, serverConfig) ->
                    AppLogger.d(TAG, "服务器: $serverId, command: ${serverConfig.command}, args: ${serverConfig.args}")
                }
                
                var addedCount = 0
                _mcpConfig.update { currentConfig ->
                    val newServers = currentConfig.mcpServers.toMutableMap()
                    parsedConfig.mcpServers.forEach { (serverId, serverConfig) ->
                        newServers[serverId] = serverConfig
                        addedCount++
                        AppLogger.d(TAG, "添加服务器配置: $serverId")
                    }
                    currentConfig.copy(mcpServers = newServers)
                }
                
                AppLogger.d(TAG, "自动填充缺失的元数据")
                val updatedConfig = autoFillMissingMetadata(_mcpConfig.value)
                _mcpConfig.value = updatedConfig
                
                AppLogger.d(TAG, "保存配置文件")
                saveMCPConfig()
                
                AppLogger.d(TAG, "初始化服务器状态")
                initializeMissingServerStatus()
                
                AppLogger.i(TAG, "成功合并 $addedCount 个服务器配置")
                Result.success(addedCount)
            } catch (e: Exception) {
                AppLogger.e(TAG, "合并配置失败: ${e.message}", e)
                e.printStackTrace()
                Result.failure(Exception("合并配置失败: ${e.message}"))
            }
        }
    }

    /**
     * 获取配置文件路径
     */
    fun getConfigFilePath(): String = mcpConfigFile.absolutePath

    /**
     * 获取MCP服务器配置
     */
    fun getMCPServer(serverId: String): MCPConfig.ServerConfig? {
        return _mcpConfig.value.mcpServers[serverId]
    }

    /**
     * 获取所有MCP服务器配置
     */
    fun getAllMCPServers(): Map<String, MCPConfig.ServerConfig> {
        return _mcpConfig.value.mcpServers.toMap()
    }

    // ==================== 插件元数据管理 ====================

    /**
     * 添加或更新插件元数据
     */
    suspend fun addOrUpdatePluginMetadata(metadata: PluginMetadata) {
        _mcpConfig.update { currentConfig ->
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            newMetadata[metadata.id] = metadata
            currentConfig.copy(pluginMetadata = newMetadata)
        }
        saveMCPConfig()
        AppLogger.d(TAG, "插件元数据已更新: ${metadata.id} - ${metadata.name}")
    }

    /**
     * 删除插件元数据
     */
    suspend fun removePluginMetadata(pluginId: String) {
        _mcpConfig.update { currentConfig ->
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            newMetadata.remove(pluginId)
            currentConfig.copy(pluginMetadata = newMetadata)
        }
        saveMCPConfig()
        AppLogger.d(TAG, "插件元数据已删除: $pluginId")
    }

    /**
     * 获取插件元数据
     */
    fun getPluginMetadata(pluginId: String): PluginMetadata? {
        return _mcpConfig.value.pluginMetadata[pluginId]
    }

    /**
     * 获取所有插件元数据
     */
    fun getAllPluginMetadata(): Map<String, PluginMetadata> {
        return _mcpConfig.value.pluginMetadata.toMap()
    }

    // ==================== 服务器状态管理 ====================

    /**
     * 更新服务器状态
     * 注意：启用/禁用状态请使用 setServerEnabled() 方法
     */
    suspend fun updateServerStatus(
        serverId: String,
        active: Boolean? = null,
        errorMessage: String? = null,
        cachedTools: List<CachedToolInfo>? = null
    ) {
        val currentStatus = _serverStatus.value.toMutableMap()
        val existingStatus = currentStatus[serverId] ?: ServerStatus(serverId)
        
        val updatedStatus = existingStatus.copy(
            active = active ?: existingStatus.active,
            errorMessage = errorMessage ?: existingStatus.errorMessage,
            cachedTools = cachedTools ?: existingStatus.cachedTools,
            toolsCachedTime = if (cachedTools != null) System.currentTimeMillis() else existingStatus.toolsCachedTime,
            lastStartTime = if (active == true) System.currentTimeMillis() else existingStatus.lastStartTime,
            lastStopTime = if (active == false) System.currentTimeMillis() else existingStatus.lastStopTime
        )
        
        currentStatus[serverId] = updatedStatus
        _serverStatus.value = currentStatus
        saveServerStatus()
        AppLogger.d(TAG, "服务器状态已更新: $serverId")
    }

    /**
     * 缓存服务器的工具列表
     */
    suspend fun cacheServerTools(serverId: String, tools: List<CachedToolInfo>) {
        updateServerStatus(serverId = serverId, cachedTools = tools)
        AppLogger.d(TAG, "已缓存服务器 $serverId 的 ${tools.size} 个工具")
    }

    /**
     * 获取缓存的工具列表
     */
    fun getCachedTools(serverId: String): List<CachedToolInfo>? {
        return _serverStatus.value[serverId]?.cachedTools
    }

    /**
     * 检查工具缓存是否有效 (有效期1天)
     */
    fun hasValidToolCache(serverId: String): Boolean {
        val status = _serverStatus.value[serverId] ?: return false
        
        val cachedTools = status.cachedTools
        val cacheTime = status.toolsCachedTime
        
        if (cachedTools.isNullOrEmpty() || cacheTime <= 0) {
            return false
        }
        
        // 缓存有效期为1天
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - cacheTime) < oneDayInMillis
    }

    /**
     * 删除服务器状态
     */
    suspend fun removeServerStatus(serverId: String) {
        val currentStatus = _serverStatus.value.toMutableMap()
        currentStatus.remove(serverId)
        _serverStatus.value = currentStatus
        saveServerStatus()
        AppLogger.d(TAG, "服务器状态已删除: $serverId")
    }

    /**
     * 获取服务器状态
     */
    fun getServerStatus(serverId: String): ServerStatus? {
        return _serverStatus.value[serverId]
    }

    /**
     * 获取所有服务器状态
     */
    fun getAllServerStatus(): Map<String, ServerStatus> {
        return _serverStatus.value.toMap()
    }

    /**
     * 检查服务器是否启用
     * 从MCP配置的disabled字段读取，默认为启用（disabled=false或不存在）
     */
    fun isServerEnabled(serverId: String): Boolean {
        val serverConfig = getMCPServer(serverId)
        return serverConfig?.disabled != true // disabled为true表示禁用，其他情况均为启用
    }

    /**
     * 设置服务器启用状态
     * 修改MCP配置的disabled字段
     */
    suspend fun setServerEnabled(serverId: String, enabled: Boolean) {
        val serverConfig = getMCPServer(serverId) ?: return
        addOrUpdateMCPServer(
            serverId = serverId,
            command = serverConfig.command,
            args = serverConfig.args ?: emptyList(),
            env = serverConfig.env ?: emptyMap(),
            disabled = !enabled,
            autoApprove = serverConfig.autoApprove ?: emptyList()
        )
        AppLogger.d(TAG, "服务器启用状态已更新: $serverId, enabled=$enabled")
    }

    /**
     * 检查插件是否已部署
     * 对于虚拟路径插件（npx/uvx/uv）：直接返回true
     * 对于实体路径插件：检查Linux文件系统中目录是否存在且包含至少1个文件
     */
    suspend fun isPluginDeployed(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取插件元数据
            val metadata = getPluginMetadata(pluginId)
            val installedPath = metadata?.installedPath
            
            // 如果是虚拟路径，直接返回true
            if (installedPath?.startsWith("virtual://") == true) {
                AppLogger.d(TAG, "插件 $pluginId 是虚拟路径，判定为已部署")
                return@withContext true
            }
            
            // 检查Linux文件系统中是否存在插件目录
            val pluginHomeDir = "~/mcp_plugins"
            val pluginDir = "$pluginHomeDir/${pluginId.split("/").last()}"
            
            // 使用AIToolHandler检查目录是否存在
            val toolHandler = AIToolHandler.getInstance(context)
            
            // 先检查目录是否存在
            val checkExistsTool = AITool(
                name = "file_exists",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val existsResult = toolHandler.executeTool(checkExistsTool)
            val dirExists = existsResult.success && existsResult.result is FileExistsData && 
                            (existsResult.result as FileExistsData).exists
            
            if (!dirExists) {
                AppLogger.d(TAG, "插件 $pluginId 目录不存在: $pluginDir")
                return@withContext false
            }
            
            // 目录存在，检查是否有至少1个文件
            val listFilesTool = AITool(
                name = "list_files",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val listResult = toolHandler.executeTool(listFilesTool)
            val hasFiles = if (listResult.success && listResult.result is DirectoryListingData) {
                val listing = listResult.result as DirectoryListingData
                listing.entries.isNotEmpty()
            } else {
                false
            }
            
            AppLogger.d(TAG, "插件 $pluginId 部署检查: $hasFiles (路径: $pluginDir, 包含${if (hasFiles) "有" else "无"}文件)")
            return@withContext hasFiles
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查插件部署状态时出错: $pluginId", e)
            return@withContext false
        }
    }

    // ==================== 兼容性方法 ====================

    /**
     * 获取插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空对象
     */
    fun getPluginConfig(pluginId: String): String {
        val serverConfig = getMCPServer(pluginId)
        return if (serverConfig != null) {
            val configForOnePlugin = MCPConfig(
                mcpServers = mutableMapOf(pluginId to serverConfig)
            )
            gson.toJson(configForOnePlugin)
        } else {
            gson.toJson(MCPConfig())
        }
    }

    /**
     * 保存插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串，可以是完整的MCPConfig或单个ServerConfig
     * @return 是否保存成功
     */
    suspend fun savePluginConfig(pluginId: String, config: String): Boolean {
        return try {
            // 先尝试解析为完整的MCPConfig（getPluginConfig返回的格式）
            val serverConfig = try {
                val fullConfig = gson.fromJson(config, MCPConfig::class.java)
                // 如果包含mcpServers且有对应的pluginId，使用该配置
                fullConfig.mcpServers[pluginId] ?: throw Exception("No server config found for $pluginId")
            } catch (e: Exception) {
                // 如果失败，尝试直接解析为ServerConfig
                gson.fromJson(config, MCPConfig.ServerConfig::class.java)
            }
            
            _mcpConfig.update { currentConfig ->
                val newServers = currentConfig.mcpServers.toMutableMap()
                newServers[pluginId] = serverConfig
                currentConfig.copy(mcpServers = newServers)
            }
            saveMCPConfig()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存插件配置失败: $pluginId", e)
            false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 导出配置为JSON字符串
     */
    fun exportConfigAsJson(): String {
        val exportData = mapOf(
            "mcpConfig" to _mcpConfig.value,
            "serverStatus" to _serverStatus.value,
            "exportTime" to System.currentTimeMillis(),
            "version" to "1.0"
        )
        return gson.toJson(exportData)
    }

    /**
     * 从JSON字符串导入配置
     */
    suspend fun importConfigFromJson(json: String): Boolean {
        return try {
            val typeToken = object : TypeToken<Map<String, Any>>() {}.type
            val importData = gson.fromJson<Map<String, Any>>(json, typeToken)
            
            importData["mcpConfig"]?.let { config ->
                val configJson = gson.toJson(config)
                val mcpConfig = gson.fromJson(configJson, MCPConfig::class.java)
                _mcpConfig.value = mcpConfig
                saveMCPConfig()
            }
            
            importData["serverStatus"]?.let { status ->
                val statusJson = gson.toJson(status)
                val typeToken3 = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val serverStatus = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken3)
                _serverStatus.value = serverStatus
                saveServerStatus()
            }
            
            AppLogger.d(TAG, "配置导入成功")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入配置失败", e)
            false
        }
    }

    /**
     * 获取配置目录路径
     */
    fun getConfigDirectory(): String = configBaseDir.absolutePath

    /**
     * 清理无效配置
     */
    suspend fun cleanupInvalidConfigurations() {
        try {
            // 清理不存在的插件配置
            val validPluginIds = _mcpConfig.value.pluginMetadata.keys
            val mcpConfig = _mcpConfig.value
            val serversToRemove = mcpConfig.mcpServers.keys.filter { it !in validPluginIds }
            
            serversToRemove.forEach { serverId ->
                mcpConfig.mcpServers.remove(serverId)
            }
            
            if (serversToRemove.isNotEmpty()) {
                _mcpConfig.value = mcpConfig
                saveMCPConfig()
                AppLogger.d(TAG, "清理了 ${serversToRemove.size} 个无效的MCP服务器配置")
            }
            
            // 清理无效的服务器状态
            val statusToRemove = _serverStatus.value.keys.filter { it !in validPluginIds }
            if (statusToRemove.isNotEmpty()) {
                val currentStatus = _serverStatus.value.toMutableMap()
                statusToRemove.forEach { serverId ->
                    currentStatus.remove(serverId)
                }
                _serverStatus.value = currentStatus
                saveServerStatus()
                AppLogger.d(TAG, "清理了 ${statusToRemove.size} 个无效的服务器状态")
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理配置时出错", e)
        }
    }
}