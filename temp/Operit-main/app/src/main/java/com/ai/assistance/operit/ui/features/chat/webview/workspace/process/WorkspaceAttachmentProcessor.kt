package com.ai.assistance.operit.ui.features.chat.webview.workspace.process

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 工作区附着处理器
 * 负责生成包含工作区状态信息的XML附着内容
 */
object WorkspaceAttachmentProcessor {
    private const val TAG = "WorkspaceAttachmentProcessor"
    
    // 用于缓存工作区状态
    private data class FileMetadata(val path: String, val size: Long, val lastModified: Long, val isDirectory: Boolean)
    private val workspaceStateCache = mutableMapOf<String, List<FileMetadata>>()
    
    /**
     * 生成工作区附着XML内容
     * @param context 上下文
     * @param workspacePath 工作区路径
     * @return 包含工作区信息的XML字符串
     */
    suspend fun generateWorkspaceAttachment(
        context: Context,
        workspacePath: String?
    ): String = withContext(Dispatchers.IO) {
        if (workspacePath.isNullOrBlank()) {
            return@withContext generateEmptyWorkspaceXml()
        }
        
        try {
            val workspaceDir = File(workspacePath)
            if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
                AppLogger.w(TAG, "工作区路径不存在或不是目录: $workspacePath")
                // 清除无效路径的缓存
                workspaceStateCache.remove(workspacePath)
                return@withContext generateEmptyWorkspaceXml()
            }
            
            val toolHandler = AIToolHandler.getInstance(context)
            
            // 获取工作区目录结构及其变化
            val directoryStructure = getWorkspaceStructureAndDiff(workspacePath)
            
            // 获取工作区错误信息
            val workspaceErrors = getWorkspaceErrors(toolHandler, workspacePath)
            
            // 获取用户改动记录
            val userChanges = getUserChanges(toolHandler, workspacePath)

            // 获取工作区建议
            val workspaceSuggestions = getWorkspaceSuggestions(workspaceDir)
            
            // 生成完整的XML
            buildWorkspaceXml(
                directoryStructure = directoryStructure,
                workspaceErrors = workspaceErrors,
                userChanges = userChanges,
                workspaceSuggestions = workspaceSuggestions
            )
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成工作区附着失败", e)
            generateErrorWorkspaceXml(e.message ?: "未知错误")
        }
    }
    
    /**
     * 获取工作区建议
     */
    private fun getWorkspaceSuggestions(workspaceDir: File): String {
        val suggestions = mutableListOf<String>()
        try {
            // 通用建议：先了解项目再修改
            suggestions.add("对于用户意图，建议先使用 grep_context（基于意图搜索相关文件）和 grep_code（搜索特定代码模式）工具去了解项目当前情况，再进行修改。")
            
            // 加载 gitignore 规则
            val ignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
            
            // 只检查根目录文件，避免深度遍历
            val hasHtmlFiles = workspaceDir.listFiles()
                ?.filter { !GitIgnoreFilter.shouldIgnore(it, workspaceDir, ignoreRules) }
                ?.filter { it.isFile }
                ?.any { it.extension.lowercase() == "html" || it.extension.lowercase() == "htm" }
                ?: false
            
            // 只有在有HTML文件时才显示H5相关建议
            if (hasHtmlFiles) {
                // 提醒AI分离文件
                suggestions.add("请将HTML, CSS, 和 JavaScript 代码分别存放到独立的文件中。")
                
                // 建议创建子目录来组织文件（常驻建议）
                suggestions.add("如果项目较为复杂，可以考虑新建js文件夹和css文件夹并创建多个文件。")
            }

            return if (suggestions.isNotEmpty()) {
                suggestions.joinToString("\n")
            } else {
                "暂无建议"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取工作区建议失败", e)
            return "获取建议时发生异常: ${e.message}"
        }
    }

    /**
     * 获取工作区目录结构，并与缓存进行比较以生成差异报告
     */
    private fun getWorkspaceStructureAndDiff(workspacePath: String): String {
        val newFileMetadatas = getCurrentWorkspaceState(workspacePath)
        val oldFileMetadatas = workspaceStateCache[workspacePath]

        // 总是更新缓存
        workspaceStateCache[workspacePath] = newFileMetadatas

        val fullStructure = buildStructureStringFromMetadata(newFileMetadatas, workspacePath)

        if (oldFileMetadatas == null) {
            // 首次加载，只显示根目录
            val rootLevelStructure = buildRootLevelStructure(workspacePath)
            return "首次加载工作区:\n$rootLevelStructure"
        }

        // --- 计算差异 ---
        val oldStateMap = oldFileMetadatas.associateBy { it.path }
        val newStateMap = newFileMetadatas.associateBy { it.path }

        val addedFiles = newFileMetadatas.filter { it.path !in oldStateMap }
        val deletedFiles = oldFileMetadatas.filter { it.path !in newStateMap }
        
        val modifiedFiles = newFileMetadatas.filter {
            val oldMeta = oldStateMap[it.path]
            // 文件存在于旧状态中，且不是目录，且大小或修改时间已改变
            oldMeta != null && !it.isDirectory && (it.size != oldMeta.size || it.lastModified != oldMeta.lastModified)
        }

        if (addedFiles.isEmpty() && deletedFiles.isEmpty() && modifiedFiles.isEmpty()) {
            return "工作区结构无变化。\n\n$fullStructure"
        }

        // --- 构建差异报告字符串 ---
        val diffBuilder = StringBuilder()
        diffBuilder.append("工作区结构变化:\n")
        if (addedFiles.isNotEmpty()) {
            diffBuilder.append("  新增:\n")
            addedFiles.forEach { diffBuilder.append("    - ${it.path.replace('\\', '/')}\n") }
        }
        if (modifiedFiles.isNotEmpty()) {
            diffBuilder.append("  修改:\n")
            modifiedFiles.forEach { diffBuilder.append("    - ${it.path.replace('\\', '/')}\n") }
        }
        if (deletedFiles.isNotEmpty()) {
            diffBuilder.append("  删除:\n")
            deletedFiles.forEach { diffBuilder.append("    - ${it.path.replace('\\', '/')}\n") }
        }
        
        diffBuilder.append("\n当前完整结构:\n")
        diffBuilder.append(fullStructure)

        return diffBuilder.toString()
    }

    /**
     * 获取根目录文件状态（仅扫描根目录，不深度遍历）
     */
    private fun getCurrentWorkspaceState(workspacePath: String): List<FileMetadata> {
        val workspaceDir = File(workspacePath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            return emptyList()
        }
        
        // 加载 gitignore 规则
        val ignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
        
        // 只获取根目录下的直接子项，避免深度遍历
        return workspaceDir.listFiles()
            ?.filter { !GitIgnoreFilter.shouldIgnore(it, workspaceDir, ignoreRules) }
            ?.map { file ->
                FileMetadata(
                    path = file.name,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                )
            }
            ?: emptyList()
    }

    /**
     * 构建根目录级别的结构（仅显示根目录下的直接子项）
     */
    private fun buildRootLevelStructure(workspacePath: String): String {
        val workspaceDir = File(workspacePath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            return "工作区不存在或不是目录"
        }
        
        // 加载 gitignore 规则
        val ignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
        
        // 只获取根目录下的直接子项
        val rootItems = workspaceDir.listFiles()
            ?.filter { !GitIgnoreFilter.shouldIgnore(it, workspaceDir, ignoreRules) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()
        
        if (rootItems.isEmpty()) {
            return "工作区为空"
        }
        
        val builder = StringBuilder()
        rootItems.forEachIndexed { index, file ->
            val isLast = index == rootItems.size - 1
            val prefix = if (isLast) "└── " else "├── "
            val icon = if (file.isDirectory) "📁" else "📄"
            
            builder.append("$prefix$icon ${file.name}")
            if (file.isFile && file.length() > 0) {
                builder.append(" (${formatFileSize(file.length())})")
            }
            builder.append("\n")
        }
        
        return builder.toString()
    }

    /**
     * 从文件元数据列表构建树形结构的字符串
     */
    private fun buildStructureStringFromMetadata(metadatas: List<FileMetadata>, _workspacePath: String): String {
        if (metadatas.isEmpty()) return "工作区为空"

        val root = Node(".")
        // 根据路径构建节点树
        metadatas.forEach { metadata ->
            var currentNode = root
            metadata.path.split(File.separatorChar).forEach { component ->
                currentNode = currentNode.children.getOrPut(component) { Node(component) }
            }
            currentNode.metadata = metadata
        }

        val builder = StringBuilder()
        buildTreeString(root, "", true, builder)
        return builder.toString()
    }

    // 辅助节点类
    private data class Node(
        val name: String,
        val children: MutableMap<String, Node> = mutableMapOf(),
        var metadata: FileMetadata? = null
    )

    /**
     * 递归构建树形字符串
     */
    private fun buildTreeString(node: Node, indent: String, _isLast: Boolean, builder: StringBuilder) {
        // 排序：文件夹在前，文件在后，然后按名称排序
        val sortedChildren = node.children.values.sortedWith(
            compareBy({ it.metadata?.isDirectory == false }, { it.name })
        )

        sortedChildren.forEachIndexed { index, childNode ->
            val isCurrentLast = index == sortedChildren.size - 1
            val prefix = if (isCurrentLast) "└── " else "├── "
            val icon = if (childNode.metadata?.isDirectory == true) "📁" else "📄"
            
            builder.append("$indent$prefix$icon ${childNode.name}")
            if (childNode.metadata?.isDirectory == false && childNode.metadata!!.size > 0) {
                builder.append(" (${formatFileSize(childNode.metadata!!.size)})")
            }
            builder.append("\n")

            if (childNode.metadata?.isDirectory == true) {
                val newIndent = indent + if (isCurrentLast) "    " else "│   "
                buildTreeString(childNode, newIndent, isCurrentLast, builder)
            }
        }
    }
    
    /**
     * 获取工作区错误信息
     */
    private suspend fun getWorkspaceErrors(
        toolHandler: AIToolHandler,
        workspacePath: String
    ): String {
        // TODO: 实现具体的错误检测逻辑
        // 这里可以检查文件语法错误、依赖问题等
        return try {
            // 检查常见错误文件类型
            val errorFiles = mutableListOf<String>()
            
            // 检查HTML文件
            checkHtmlErrors(toolHandler, workspacePath, errorFiles)
            
            // 检查CSS文件  
            checkCssErrors(toolHandler, workspacePath, errorFiles)
            
            // 检查JavaScript文件
            checkJsErrors(toolHandler, workspacePath, errorFiles)
            
            if (errorFiles.isEmpty()) {
                "暂无发现错误"
            } else {
                errorFiles.joinToString("\n")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取工作区错误失败", e)
            "获取错误信息时发生异常: ${e.message}"
        }
    }
    
    /**
     * 获取用户改动记录
     */
    private suspend fun getUserChanges(
        _toolHandler: AIToolHandler,
        workspacePath: String
    ): String {
        // TODO: 实现用户改动跟踪逻辑
        // 这里可以记录文件的修改时间、内容变化等
        return try {
            val workspaceDir = File(workspacePath)
            val recentFiles = mutableListOf<String>()
            
            // 获取最近修改的文件
            getRecentlyModifiedFiles(workspaceDir, recentFiles)
            
            if (recentFiles.isEmpty()) {
                "暂无最近改动"
            } else {
                "最近修改的文件:\n${recentFiles.joinToString("\n")}"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取用户改动记录失败", e)
            "获取改动记录时发生异常: ${e.message}"
        }
    }
    
    /**
     * 检查HTML文件错误
     */
    private suspend fun checkHtmlErrors(
        _toolHandler: AIToolHandler,
        _workspacePath: String,
        _errorFiles: MutableList<String>
    ) {
        // TODO: 实现HTML语法检查
        // 可以检查标签闭合、属性格式等
    }
    
    /**
     * 检查CSS文件错误
     */
    private suspend fun checkCssErrors(
        _toolHandler: AIToolHandler,
        _workspacePath: String,
        _errorFiles: MutableList<String>
    ) {
        // TODO: 实现CSS语法检查
        // 可以检查选择器、属性值等
    }
    
    /**
     * 检查JavaScript文件错误
     */
    private suspend fun checkJsErrors(
        _toolHandler: AIToolHandler,
        _workspacePath: String,
        _errorFiles: MutableList<String>
    ) {
        // TODO: 实现JavaScript语法检查
        // 可以检查基本语法错误
    }
    
    /**
     * 获取最近修改的文件
     */
    private fun getRecentlyModifiedFiles(
        workspaceDir: File,
        recentFiles: MutableList<String>
    ) {
        try {
            val currentTime = System.currentTimeMillis()
            val oneDayAgo = currentTime - 24 * 60 * 60 * 1000 // 24小时前
            
            // 加载 gitignore 规则
            val ignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
            
            // 只监听根目录下的文件，与 buildSimpleStructure 保持一致
            workspaceDir.listFiles()
                ?.filter { it.isFile } // 只处理文件
                ?.filter { file ->
                    // 过滤应该被忽略的文件
                    !GitIgnoreFilter.shouldIgnore(file, workspaceDir, ignoreRules)
                }
                ?.filter { it.lastModified() > oneDayAgo }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10) // 最多显示10个文件
                ?.forEach { file ->
                    val timeAgo = formatTimeAgo(currentTime - file.lastModified())
                    recentFiles.add("${file.name} ($timeAgo)")
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取最近修改文件失败", e)
        }
    }
    
    /**
     * 构建完整的工作区XML
     */
    private fun buildWorkspaceXml(
        directoryStructure: String,
        workspaceErrors: String,
        userChanges: String,
        workspaceSuggestions: String
    ): String {
        return """
<workspace_context>
<directory_structure>
    $directoryStructure
</directory_structure>

<workspace_errors>
    $workspaceErrors
</workspace_errors>

<user_changes>
    $userChanges
</user_changes>

<workspace_suggestions>
    $workspaceSuggestions
</workspace_suggestions>
</workspace_context>""".trimIndent()
    }
    
    /**
     * 生成空工作区XML
     */
    private fun generateEmptyWorkspaceXml(): String {
        return """
            <workspace_context>
                <directory_structure>
                    工作区未配置或不存在
                </directory_structure>
                
                <workspace_errors>
                    无法检查错误
                </workspace_errors>
                
                <user_changes>
                    无改动记录
                </user_changes>
                
                <workspace_suggestions>
                    请先配置工作区路径
                </workspace_suggestions>
            </workspace_context>
        """.trimIndent()
    }
    
    /**
     * 生成错误工作区XML
     */
    private fun generateErrorWorkspaceXml(errorMessage: String): String {
        return """
            <workspace_context>
                <directory_structure>
                    获取失败: $errorMessage
                </directory_structure>
                
                <workspace_errors>
                    无法检查错误: $errorMessage
                </workspace_errors>
                
                <user_changes>
                    无法获取改动: $errorMessage
                </user_changes>
                
                <workspace_suggestions>
                    系统错误，请检查工作区配置
                </workspace_suggestions>
            </workspace_context>
        """.trimIndent()
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }
    
    /**
     * 格式化时间差
     */
    private fun formatTimeAgo(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}小时前"
            minutes > 0 -> "${minutes}分钟前"
            else -> "刚刚"
        }
    }
} 