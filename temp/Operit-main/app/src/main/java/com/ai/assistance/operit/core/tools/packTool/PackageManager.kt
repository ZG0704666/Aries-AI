package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.PackageToolExecutor
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.model.PackageToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolResult
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

/**
 * Manages the loading, registration, and handling of tool packages
 *
 * Package Lifecycle:
 * 1. Available Packages: All packages in assets (both JS and HJSON format)
 * 2. Imported Packages: Packages that user has imported (but not necessarily using)
 * 3. Used Packages: Packages that are loaded and registered with AI in current session
 */
class PackageManager
private constructor(private val context: Context, private val aiToolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "PackageManager"
        private const val PACKAGES_DIR = "packages" // Directory for packages
        private const val ASSETS_PACKAGES_DIR = "packages" // Directory in assets for packages
        private const val PACKAGE_PREFS = "com.ai.assistance.operit.core.tools.PackageManager"
        private const val IMPORTED_PACKAGES_KEY = "imported_packages"
        private const val DISABLED_PACKAGES_KEY = "disabled_packages"
        private const val ACTIVE_PACKAGES_KEY = "active_packages"

        @Volatile
        private var INSTANCE: PackageManager? = null

        fun getInstance(context: Context, aiToolHandler: AIToolHandler): PackageManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: PackageManager(context.applicationContext, aiToolHandler).also {
                            INSTANCE = it
                        }
                }
        }
    }

    // Map of package name to package description (all available packages in market)
    private val availablePackages = mutableMapOf<String, ToolPackage>()

    @Volatile
    private var isInitialized = false
    private val initLock = Any()

    private val skillManager by lazy { SkillManager.getInstance(context) }

    // JavaScript engine for executing JS package code
    private val jsEngine by lazy { JsEngine(context) }

    // Environment preferences for package-level env variables
    private val envPreferences by lazy { EnvPreferences.getInstance(context) }

    // MCP Manager instance (lazy loading)
    private val mcpManager by lazy { MCPManager.getInstance(context) }

    // Get the external packages directory
    private val externalPackagesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            AppLogger.d(TAG, "External packages directory: ${dir.absolutePath}")
            return dir
        }

    private fun ensureInitialized() {
        if (isInitialized) return
        synchronized(initLock) {
            if (isInitialized) return
            // Create packages directory if it doesn't exist
            externalPackagesDir // This will create the directory if it doesn't exist

            // Load available packages info (metadata only) from assets and external storage
            loadAvailablePackages()

            // Automatically import built-in packages that are enabled by default
            initializeDefaultPackages()

            isInitialized = true
        }
    }

    /**
     * Automatically imports built-in packages that are marked as enabled by default.
     * This ensures that essential or commonly used packages are available without
     * manual user intervention. It also respects a user's choice to disable a
     * default package.
     */
    private fun initializeDefaultPackages() {
        val importedPackages = getImportedPackagesInternal().toMutableSet()
        val disabledPackages = getDisabledPackagesInternal().toSet()
        var packagesChanged = false

        availablePackages.values.forEach { toolPackage ->
            if (toolPackage.isBuiltIn && toolPackage.enabledByDefault && !disabledPackages.contains(
                    toolPackage.name
                )
            ) {
                if (importedPackages.add(toolPackage.name)) {
                    packagesChanged = true
                    AppLogger.d(TAG, "Auto-importing default package: ${toolPackage.name}")
                }
            }
        }

        if (packagesChanged) {
            val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
            val updatedJson = Json.encodeToString(importedPackages.toList())
            prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
            AppLogger.d(TAG, "Updated imported packages with default packages.")
        }
    }

    /**
     * Loads all available packages metadata (from assets and external storage, both JS and HJSON
     * format)
     */
    private fun loadAvailablePackages() {
        synchronized(initLock) {
            // Load packages from assets (JS only, skip TS files)
            val assetManager = context.assets
            val packageFiles = assetManager.list(ASSETS_PACKAGES_DIR) ?: emptyArray()

            for (fileName in packageFiles) {
                if (fileName.endsWith(".js")) {
                    // Only load JavaScript files, skip TypeScript files which require compilation
                    val packageMetadata = loadPackageFromJsAsset("$ASSETS_PACKAGES_DIR/$fileName")
                    if (packageMetadata != null) {
                        // Packages from assets are built-in
                        availablePackages[packageMetadata.name] =
                            packageMetadata.copy(isBuiltIn = true)
                        AppLogger.d(
                            TAG,
                            "Loaded JavaScript package from assets: ${packageMetadata.name} with description: ${packageMetadata.description}, tools: ${packageMetadata.tools.size}"
                        )
                    }
                }
            }

            // Also load packages from external storage (imported from external sources)
            if (externalPackagesDir.exists()) {
                val externalFiles = externalPackagesDir.listFiles() ?: emptyArray()

                for (file in externalFiles) {
                    if (file.isFile && file.name.endsWith(".js")) {
                        val packageMetadata = loadPackageFromJsFile(file)
                        if (packageMetadata != null) {
                            // Packages from external storage are not built-in
                            availablePackages[packageMetadata.name] =
                                packageMetadata.copy(isBuiltIn = false)
                            AppLogger.d(
                                TAG,
                                "Loaded JS package from external storage: ${packageMetadata.name}"
                            )
                        }
                    }
                }
            }
        }
    }

    /** Loads a complete ToolPackage from a JavaScript file */
    private fun loadPackageFromJsFile(file: File): ToolPackage? {
        try {
            val jsContent = file.readText()
            return parseJsPackage(jsContent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS file: ${file.path}", e)
            return null
        }
    }

    /** Loads a complete ToolPackage from a JavaScript file in assets */
    private fun loadPackageFromJsAsset(assetPath: String): ToolPackage? {
        try {
            val assetManager = context.assets
            val jsContent = assetManager.open(assetPath).bufferedReader().use { it.readText() }
            return parseJsPackage(jsContent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS asset: $assetPath", e)
            return null
        }
    }

    /**
     * Parses a JavaScript package file into a ToolPackage object Uses the metadata in the file
     * header and extracts function definitions using JsEngine
     */
    private fun parseJsPackage(jsContent: String): ToolPackage? {
        try {
            // Extract metadata from comments at the top of the file
            val metadataString = extractMetadataFromJs(jsContent)

            // 先将元数据解析为 JSONObject 以便修改 tools 数组中的每个元素
            val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())

            // 检查并修复 tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("tools") && metadataJson.get("tools") is org.json.JSONArray) {
                val toolsArray = metadataJson.getJSONArray("tools")
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.getJSONObject(i)
                    if (!tool.has("script")) {
                        // 添加一个临时的空 script 字段
                        tool.put("script", "")
                    }
                }
            }

            // 使用修改后的 JSON 字符串进行反序列化
            val jsonString = metadataJson.toString()

            val jsonConfig = Json { ignoreUnknownKeys = true }
            val packageMetadata = jsonConfig.decodeFromString<ToolPackage>(jsonString)

            // 更新所有工具，使用相同的完整脚本内容，但记录每个工具的函数名
            val tools =
                packageMetadata.tools.map { tool ->
                    // 检查函数是否存在于脚本中
                    validateToolFunctionExists(jsContent, tool.name)

                    // 使用整个脚本，并记录函数名，而不是提取单个函数
                    tool.copy(script = jsContent)
                }

            return packageMetadata.copy(tools = tools)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing JS package: ${e.message}", e)
            return null
        }
    }

    /** 验证JavaScript文件中是否存在指定的函数 这确保了我们可以在运行时调用该函数 */
    private fun validateToolFunctionExists(jsContent: String, toolName: String): Boolean {
        // 各种函数声明模式
        val patterns =
            listOf(
                """async\s+function\s+$toolName\s*\(""",
                """function\s+$toolName\s*\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?function""",
                """(?:const|let|var)\s+$toolName\s*=\s*(?:async\s+)?\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?\(?"""
            )

        for (pattern in patterns) {
            if (pattern.toRegex().find(jsContent) != null) {
                return true
            }
        }

        AppLogger.w(TAG, "Could not find function '$toolName' in JavaScript file")
        return false
    }

    /** Extracts the metadata from JS comments at the top of the file */
    private fun extractMetadataFromJs(jsContent: String): String {
        val metadataPattern = """/\*\s*METADATA\s*([\s\S]*?)\*/""".toRegex()
        val match = metadataPattern.find(jsContent)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If no metadata block is found, return empty metadata
            "{}"
        }
    }

    /**
     * Returns the path to the external packages directory This can be used to show the user where
     * the packages are stored for manual editing
     */
    fun getExternalPackagesPath(): String {
        val path = externalPackagesDir.absolutePath
        // 为了更易读，改成Android/data/包名/files/packages的形式
        return "Android/data/${context.packageName}/files/packages"
    }

    /**
     * Imports a package from external storage path
     * @param filePath The file path to the JS or HJSON package file in external storage
     * @return Success message with package details or error message
     */
    fun importPackageFromExternalStorage(filePath: String): String {
        try {
            // Check if the file exists and is readable
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return "Cannot access file at path: $filePath"
            }

            // Check if it's a supported file type
            if (!filePath.endsWith(".hjson") &&
                !filePath.endsWith(".js") &&
                !filePath.endsWith(".ts")
            ) {
                return "Only HJSON, JavaScript (.js) and TypeScript (.ts) package files are supported"
            }

            // Parse the file to get package metadata
            val packageMetadata =
                if (filePath.endsWith(".hjson")) {
                    val hjsonContent = file.readText()
                    val jsonString = JsonValue.readHjson(hjsonContent).toString()

                    val jsonConfig = Json { ignoreUnknownKeys = true }
                    jsonConfig.decodeFromString<ToolPackage>(jsonString)
                } else {
                    // Treat both .js and .ts files as JavaScript packages
                    loadPackageFromJsFile(file)
                        ?: return "Failed to parse ${if (filePath.endsWith(".ts")) "TypeScript" else "JavaScript"} package file"
                }

            // Check if package with same name already exists
            if (availablePackages.containsKey(packageMetadata.name)) {
                return "A package with name '${packageMetadata.name}' already exists in available packages"
            }

            // Copy the file to app's external storage
            val destinationFile = File(externalPackagesDir, file.name)
            file.inputStream().use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Add to available packages
            availablePackages[packageMetadata.name] = packageMetadata

            AppLogger.d(
                TAG,
                "Successfully imported external package to: ${destinationFile.absolutePath}"
            )
            return "Successfully imported package: ${packageMetadata.name}\nStored at: ${destinationFile.absolutePath}"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error importing package from external storage", e)
            return "Error importing package: ${e.message}"
        }
    }

    /**
     * Import a package by name, adding it to the user's imported packages list This does NOT
     * register the package with the AIToolHandler - it just adds it to imported list
     * @param packageName The name of the package to import
     * @return Success/failure message
     */
    fun importPackage(packageName: String): String {
        // Check if the package is available
        if (!availablePackages.containsKey(packageName)) {
            return "Package not found in available packages: $packageName"
        }

        // Check if already imported
        val importedPackages = getImportedPackages()
        if (importedPackages.contains(packageName)) {
            return "Package '$packageName' is already imported"
        }

        // Add to imported packages
        val updatedPackages = importedPackages.toMutableList()
        updatedPackages.add(packageName)

        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(updatedPackages)
        prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()

        // Remove from disabled packages list if it was there
        val disabledPackages = getDisabledPackages().toMutableList()
        if (disabledPackages.remove(packageName)) {
            saveDisabledPackages(disabledPackages)
            AppLogger.d(TAG, "Removed package from disabled list: $packageName")
        }

        AppLogger.d(TAG, "Successfully imported package: $packageName")
        return "Successfully imported package: $packageName"
    }

    /**
     * Activates and loads a package for use in the current AI session This loads the full package
     * data and registers its tools with AIToolHandler
     * @param packageName The name of the imported package to use
     * @return Package description and tools for AI prompt enhancement, or error message
     */
    fun usePackage(packageName: String): String {
        ensureInitialized()
        // First check if packageName is a standard imported package (priority)
        val importedPackages = getImportedPackages()
        if (importedPackages.contains(packageName)) {
            // Load the full package data for a standard package
            val toolPackage =
                getPackageTools(packageName)
                    ?: return "Failed to load package data for: $packageName"

            // Validate required environment variables, if any
            if (toolPackage.env.isNotEmpty()) {
                val missingEnv =
                    toolPackage.env
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .filter { envName ->
                            val value = try {
                                envPreferences.getEnv(envName)
                            } catch (e: Exception) {
                                AppLogger.e(
                                    TAG,
                                    "Error reading environment variable '$envName' for package '$packageName'",
                                    e
                                )
                                null
                            }
                            value.isNullOrEmpty()
                        }

                if (missingEnv.isNotEmpty()) {
                    val msg =
                        buildString {
                            append("Package '")
                            append(packageName)
                            append("' requires environment variable")
                            if (missingEnv.size > 1) append("s")
                            append(": ")
                            append(missingEnv.joinToString(", "))
                            append(". Please set them before using this package.")
                        }
                    AppLogger.w(TAG, msg)
                    return msg
                }
            }

            // Register the package tools with AIToolHandler
            registerPackageTools(toolPackage)

            AppLogger.d(TAG, "Successfully loaded and activated package: $packageName")

            // Generate and return the system prompt enhancement
            return generatePackageSystemPrompt(toolPackage)
        }

        // Then check if it's a Skill package
        val skillPrompt = skillManager.getSkillSystemPrompt(packageName)
        if (skillPrompt != null) {
            return skillPrompt
        }

        // Next check if it's an MCP server by checking with MCPManager
        if (isRegisteredMCPServer(packageName)) {
            return useMCPServer(packageName)
        }

        return "Package not found: $packageName. Please import it first or register it as an MCP server."
    }

    /**
     * Wrapper for tool execution: builds ToolResult for the 'use_package' tool.
     * Keeps registration site minimal by centralizing result construction here.
     */
    fun executeUsePackageTool(toolName: String, packageName: String): ToolResult {
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "缺少必需参数: package_name"
            )
        }
        val text = usePackage(packageName)
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(text)
        )
    }

    /**
     * 检查是否是已注册的MCP服务器
     *
     * @param serverName 服务器名称
     * @return 如果是已注册的MCP服务器则返回true
     */
    private fun isRegisteredMCPServer(serverName: String): Boolean {
        return mcpManager.isServerRegistered(serverName)
    }

    /**
     * 获取所有可用的MCP服务器包
     *
     * @return MCP服务器列表
     */
    fun getAvailableServerPackages(): Map<String, MCPServerConfig> {
        return mcpManager.getRegisteredServers()
    }

    // Helper function to determine if a package is an MCP server
    private fun isMCPServerPackage(toolPackage: ToolPackage): Boolean {
        // Check if any tool has MCP script placeholder
        return if (toolPackage.tools.isNotEmpty()) {
            val script = toolPackage.tools[0].script
            script.contains("/* MCPJS") // Check for MCP script marker
        } else {
            false
        }
    }

    /** Registers all tools in a package with the AIToolHandler */
    private fun registerPackageTools(toolPackage: ToolPackage) {
        val packageToolExecutor = PackageToolExecutor(toolPackage, context, this)

        // Register each tool with the format packageName:toolName
        toolPackage.tools.forEach { packageTool ->
            val toolName = "${toolPackage.name}:${packageTool.name}"
            aiToolHandler.registerTool(toolName) { tool ->
                packageToolExecutor.invoke(tool)
            }
        }
    }

    /** Generates a system prompt enhancement for the imported package */
    private fun generatePackageSystemPrompt(toolPackage: ToolPackage): String {
        val sb = StringBuilder()

        sb.appendLine("Using package: ${toolPackage.name}")
        sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Description: ${toolPackage.description}")
        sb.appendLine()
        sb.appendLine("Available tools in this package:")

        toolPackage.tools.forEach { tool ->
            sb.appendLine("- ${toolPackage.name}:${tool.name}: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Gets a list of all available packages for discovery (the "market")
     * @return A map of package name to description
     */
    fun getAvailablePackages(): Map<String, ToolPackage> {
        ensureInitialized()
        // Refresh the list to ensure it's up to date
        loadAvailablePackages()
        return availablePackages
    }

    /**
     * Get a list of all imported packages
     * @return A list of imported package names
     */
    fun getImportedPackages(): List<String> {
        ensureInitialized()
        return getImportedPackagesInternal()
    }

    private fun getImportedPackagesInternal(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(IMPORTED_PACKAGES_KEY, "[]")
        return try {
            // 创建配置了ignoreUnknownKeys的JSON解析器
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val packages = jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")

            // 自动清理不存在的包
            cleanupNonExistentPackages(packages)

            packages
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding imported packages", e)
            emptyList()
        }
    }

    /**
     * 清理导入列表中不存在的包
     * 自动移除那些已经被删除但仍然在导入列表中的包
     * @param currentPackages 当前的导入包列表
     */
    private fun cleanupNonExistentPackages(currentPackages: List<String>) {
        val packagesToRemove = currentPackages.filter { packageName ->
            // 如果包不在availablePackages中，说明已被删除
            !availablePackages.containsKey(packageName)
        }

        if (packagesToRemove.isNotEmpty()) {
            AppLogger.d(
                TAG,
                "Found ${packagesToRemove.size} non-existent packages in imported list: $packagesToRemove"
            )

            val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
            val cleanedPackages = currentPackages.filter { !packagesToRemove.contains(it) }
            val updatedJson = Json.encodeToString(cleanedPackages)
            prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()

            AppLogger.d(TAG, "Cleaned up imported packages list. Removed: $packagesToRemove")
        }
    }

    /**
     * Get a list of all disabled packages
     * @return A list of disabled package names
     */
    fun getDisabledPackages(): List<String> {
        ensureInitialized()
        return getDisabledPackagesInternal()
    }

    private fun getDisabledPackagesInternal(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(DISABLED_PACKAGES_KEY, "[]")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding disabled packages", e)
            emptyList()
        }
    }

    /** Helper to save disabled packages */
    private fun saveDisabledPackages(disabledPackages: List<String>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(disabledPackages)
        prefs.edit().putString(DISABLED_PACKAGES_KEY, updatedJson).apply()
    }

    /**
     * Get the tools for a loaded package
     * @param packageName The name of the loaded package
     * @return The ToolPackage object or null if the package is not loaded
     */
    fun getPackageTools(packageName: String): ToolPackage? {
        ensureInitialized()
        return availablePackages[packageName]
    }

    /** Checks if a package is imported */
    fun isPackageImported(packageName: String): Boolean {
        return getImportedPackages().contains(packageName)
    }

    /**
     * Remove an imported package
     * @param packageName The name of the package to remove from imported list
     * @return Success/failure message
     */
    fun removePackage(packageName: String): String {
        // Then remove from imported packages
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val currentPackages = getImportedPackages().toMutableList()

        val packageWasRemoved = currentPackages.remove(packageName)

        // If the package is a default-enabled package, add it to the disabled list
        val toolPackage = availablePackages[packageName]
        if (toolPackage != null && toolPackage.isBuiltIn && toolPackage.enabledByDefault) {
            val disabledPackages = getDisabledPackages().toMutableList()
            if (!disabledPackages.contains(packageName)) {
                disabledPackages.add(packageName)
                saveDisabledPackages(disabledPackages)
                AppLogger.d(TAG, "Added default package to disabled list: $packageName")
            }
        }

        if (packageWasRemoved) {
            val updatedJson = Json.encodeToString(currentPackages)
            prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
            AppLogger.d(TAG, "Removed package from imported list: $packageName")
            return "Successfully removed package: $packageName"
        } else {
            AppLogger.d(TAG, "Package not found in imported list: $packageName")
            return "Package not found in imported list: $packageName"
        }
    }

    /**
     * Get the script content for a package by name
     * @param packageName The name of the package
     * @return The full JavaScript content of the package or null if not found
     */
    fun getPackageScript(packageName: String): String? {
        ensureInitialized()
        val toolPackage = availablePackages[packageName] ?: return null

        // All tools in a package share the same script, so we can get it from any tool
        return if (toolPackage.tools.isNotEmpty()) {
            toolPackage.tools[0].script
        } else {
            null
        }
    }

    /**
     * 使用MCP服务器
     *
     * @param serverName 服务器名称
     * @return 成功或失败的消息
     */
    fun useMCPServer(serverName: String): String {
        // 检查服务器是否已注册
        if (!mcpManager.isServerRegistered(serverName)) {
            return "MCP服务器 '$serverName' 不存在或未注册。"
        }

        // 获取服务器配置
        val serverConfig =
            mcpManager.getRegisteredServers()[serverName]
                ?: return "无法获取MCP服务器配置: $serverName"

        // 创建MCP包
        val mcpPackage =
            MCPPackage.fromServer(context, serverConfig)
                ?: return "无法连接到MCP服务器: $serverName"

        // 转换为标准工具包
        val toolPackage = mcpPackage.toToolPackage()

        // 获取或创建MCP工具执行器
        val mcpToolExecutor = MCPToolExecutor(context, mcpManager)

        // 注册包中的每个工具 - 使用 serverName:toolName 格式
        toolPackage.tools.forEach { packageTool ->
            val toolName = "$serverName:${packageTool.name}"

            // 使用MCP特定的执行器注册工具
            aiToolHandler.registerTool(
                name = toolName,
                executor = mcpToolExecutor
            )

            AppLogger.d(TAG, "已注册MCP工具: $toolName")
        }

        return generateMCPSystemPrompt(toolPackage, serverName)
    }

    /** 为MCP服务器生成系统提示 */
    private fun generateMCPSystemPrompt(toolPackage: ToolPackage, serverName: String): String {
        val sb = StringBuilder()

        sb.appendLine("正在使用MCP服务器: $serverName")
        sb.appendLine("使用时间: ${java.time.LocalDateTime.now()}")
        sb.appendLine("描述: ${toolPackage.description}")
        sb.appendLine()
        sb.appendLine("可用工具列表:")

        toolPackage.tools.forEach { tool ->
            // 使用 serverName:toolName 格式
            sb.appendLine("- $serverName:${tool.name}: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  参数:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(必需)" else "(可选)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Deletes a package file from external storage and removes it from the in-memory cache.
     * This action is permanent and cannot be undone.
     *
     * @param packageName The name of the package to delete.
     * @return True if the package was deleted successfully, false otherwise.
     */
    fun deletePackage(packageName: String): Boolean {
        AppLogger.d(TAG, "Attempting to delete package: $packageName")
        // Find the package file.
        val packageFile = findPackageFile(packageName)

        if (packageFile == null || !packageFile.exists()) {
            AppLogger.w(
                TAG,
                "Package file not found for deletion: $packageName. It might be already deleted or never existed."
            )
            // If the file doesn't exist, we can still attempt to clean up the import record.
            removePackage(packageName)
            // Consider it "successfully" deleted if it's already gone.
            return true
        }

        AppLogger.d(TAG, "Found package file to delete: ${packageFile.absolutePath}")

        // Try to delete the file.
        val fileDeleted = packageFile.delete()

        if (fileDeleted) {
            AppLogger.d(TAG, "Successfully deleted package file: ${packageFile.absolutePath}")
            // If file deletion is successful, remove it from the imported list and in-memory cache.
            removePackage(packageName)
            val removedFromCache = availablePackages.remove(packageName)
            AppLogger.d(
                TAG,
                "Removed '$packageName' from availablePackages cache. Was it present? ${removedFromCache != null}"
            )
            AppLogger.d(TAG, "Package '$packageName' fully deleted.")
            return true
        } else {
            // If file deletion fails, log the error and do not change the state.
            AppLogger.e(TAG, "Failed to delete package file: ${packageFile.absolutePath}")
            return false
        }
    }

    /**
     * Finds the File object for a given package name in the external storage.
     * It checks for both .js and .hjson extensions.
     *
     * @param packageName The name of the package to find.
     * @return The File object if found, otherwise null.
     */
    private fun findPackageFile(packageName: String): File? {
        // Use the same directory logic as when loading packages.
        val externalPackagesDir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
        if (!externalPackagesDir.exists()) return null

        // First, try direct name match
        val jsFile = File(externalPackagesDir, "$packageName.js")
        if (jsFile.exists()) return jsFile

        // Fallback: iterate and parse files to find matching package name
        externalPackagesDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".js")) {
                val loadedPackage = loadPackageFromJsFile(file)
                if (loadedPackage?.name == packageName) {
                    return file
                }
            }
        }

        return null
    }

    /**
     * 将 ToolPackage 转换为 PackageToolPromptCategory
     * 用于生成结构化的包工具提示词
     *
     * @param toolPackage 要转换的工具包
     * @return PackageToolPromptCategory 对象
     */
    fun toPromptCategory(toolPackage: ToolPackage): PackageToolPromptCategory {
        val toolPrompts = toolPackage.tools.map { packageTool ->
            // 将 PackageTool 转换为 ToolPrompt
            val parametersString = if (packageTool.parameters.isNotEmpty()) {
                packageTool.parameters.joinToString(", ") { param ->
                    val required = if (param.required) "required" else "optional"
                    "${param.name} (${param.type}, $required)"
                }
            } else {
                ""
            }

            ToolPrompt(
                name = packageTool.name,
                description = packageTool.description,
                parameters = parametersString
            )
        }

        return PackageToolPromptCategory(
            packageName = toolPackage.name,
            packageDescription = toolPackage.description,
            tools = toolPrompts
        )
    }

    /**
     * 获取所有已导入包的提示词分类列表
     *
     * @return 已导入包的 PackageToolPromptCategory 列表
     */
    fun getImportedPackagesPromptCategories(): List<PackageToolPromptCategory> {
        ensureInitialized()
        val importedPackageNames = getImportedPackages()
        return importedPackageNames.mapNotNull { packageName ->
            getPackageTools(packageName)?.let { toolPackage ->
                toPromptCategory(toolPackage)
            }
        }
    }

    /** Clean up resources when the manager is no longer needed */
    fun destroy() {
        jsEngine.destroy()
        mcpManager.shutdown()
    }
}
