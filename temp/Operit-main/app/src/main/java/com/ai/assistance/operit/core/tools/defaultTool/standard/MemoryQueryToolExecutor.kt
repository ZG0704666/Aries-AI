package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.core.tools.MemoryLinkResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import com.ai.assistance.operit.data.preferences.preferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes queries against the AI's memory graph and manages user preferences.
 */
class MemoryQueryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "MemoryQueryToolExecutor"
    }

    private val memoryRepository by lazy {
        val profileId = runBlocking { preferencesManager.activeProfileIdFlow.first() }
        MemoryRepository(context, profileId)
    }

    override fun invoke(tool: AITool): ToolResult = runBlocking {
        return@runBlocking when (tool.name) {
            "query_memory" -> executeQueryMemory(tool)
            "get_memory_by_title" -> executeGetMemoryByTitle(tool)
            "create_memory" -> executeCreateMemory(tool)
            "update_memory" -> executeUpdateMemory(tool)
            "delete_memory" -> executeDeleteMemory(tool)
            "update_user_preferences" -> executeUpdateUserPreferences(tool)
            "link_memories" -> executeLinkMemories(tool)
            else -> ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Unknown tool: ${tool.name}"
            )
        }
    }

    private suspend fun executeQueryMemory(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        val folderPath = tool.parameters.find { it.name == "folder_path" }?.value
        val threshold = tool.parameters.find { it.name == "threshold" }?.value?.toFloatOrNull() ?: 0.25f
        val limitParam = tool.parameters.find { it.name == "limit" }?.value
        val limit = limitParam?.toIntOrNull()
        
        // 如果查询是 "*" 且用户没有显式指定 limit，则返回所有结果
        val isWildcardQuery = query.trim() == "*"
        val defaultLimit = if (isWildcardQuery && limit == null) {
            Int.MAX_VALUE // 使用最大值表示返回所有结果
        } else {
            5 // 普通查询默认返回 5 条
        }
        val finalLimit = limit ?: defaultLimit

        if (query.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        // 验证参数范围
        val validThreshold = threshold.coerceIn(0.0f, 1.0f)
        // limit 无上限，但至少为 1
        val validLimit = if (finalLimit < 1) 1 else finalLimit

        AppLogger.d(TAG, "Executing memory query: '$query' in folder: '${folderPath ?: "All"}', threshold: $validThreshold, limit: $validLimit")

        return try {
            val results = memoryRepository.searchMemories(
                query = query,
                folderPath = folderPath,
                semanticThreshold = validThreshold
            )
            
            val formattedResult = buildResultData(results.take(validLimit), query, validLimit)
            AppLogger.d(TAG, "Memory query result for '$query':\n$formattedResult")
            ToolResult(toolName = tool.name, success = true, result = formattedResult)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
        }
    }

    private suspend fun executeGetMemoryByTitle(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required"
            )
        }

        // 提取可选的分块相关参数
        val chunkIndexParam = tool.parameters.find { it.name == "chunk_index" }?.value
        val chunkRangeParam = tool.parameters.find { it.name == "chunk_range" }?.value
        val queryParam = tool.parameters.find { it.name == "query" }?.value

        AppLogger.d(TAG, "Getting memory by title: $title, chunk_index: $chunkIndexParam, chunk_range: $chunkRangeParam, query: $queryParam")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            // 如果是文档节点且提供了分块参数，则进行特殊处理
            if (memory.isDocumentNode && (chunkIndexParam != null || chunkRangeParam != null || queryParam != null)) {
                return handleDocumentChunkRetrieval(tool.name, memory, chunkIndexParam, chunkRangeParam, queryParam)
            }

            // 默认行为：返回完整记忆
            val formattedResult = buildResultData(listOf(memory), title, 1)
            AppLogger.d(TAG, "Found memory by title '$title':\n$formattedResult")
            ToolResult(
                toolName = tool.name,
                success = true,
                result = formattedResult
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get memory by title", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to get memory by title: ${e.message}"
            )
        }
    }

    private suspend fun handleDocumentChunkRetrieval(
        toolName: String,
        memory: Memory,
        chunkIndexParam: String?,
        chunkRangeParam: String?,
        queryParam: String?
    ): ToolResult = withContext(Dispatchers.IO) {
        val totalChunks = memoryRepository.getTotalChunkCount(memory.id)
        
        try {
            // 优先级：query > chunk_range > chunk_index
            val chunks = when {
                // 模糊搜索分块
                !queryParam.isNullOrBlank() -> {
                    AppLogger.d(TAG, "Searching chunks in document '${memory.title}' with query: '$queryParam'")
                    memoryRepository.searchChunksInDocument(memory.id, queryParam)
                }
                // 范围查询
                !chunkRangeParam.isNullOrBlank() -> {
                    val rangeParts = chunkRangeParam.split("-")
                    if (rangeParts.size != 2) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid chunk_range format. Expected 'start-end' (e.g., '3-7')"
                        )
                    }
                    // 解析为1-based索引，转换为0-based
                    val startIndex = (rangeParts[0].toIntOrNull() ?: 1) - 1
                    val endIndex = (rangeParts[1].toIntOrNull() ?: totalChunks) - 1
                    
                    if (startIndex < 0 || endIndex >= totalChunks || startIndex > endIndex) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Chunk range out of bounds. Document has $totalChunks chunks. Valid range: 1-$totalChunks"
                        )
                    }
                    AppLogger.d(TAG, "Retrieving chunk range ${startIndex + 1}-${endIndex + 1} from document '${memory.title}'")
                    memoryRepository.getChunksByRange(memory.id, startIndex, endIndex)
                }
                // 单个分块
                !chunkIndexParam.isNullOrBlank() -> {
                    // 解析为1-based索引，转换为0-based
                    val chunkIndex = (chunkIndexParam.toIntOrNull() ?: 1) - 1
                    if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Chunk index out of bounds. Document has $totalChunks chunks. Valid range: 1-$totalChunks"
                        )
                    }
                    AppLogger.d(TAG, "Retrieving chunk ${chunkIndex + 1} from document '${memory.title}'")
                    val chunk = memoryRepository.getChunkByIndex(memory.id, chunkIndex)
                    listOfNotNull(chunk)
                }
                else -> emptyList()
            }

            if (chunks.isEmpty()) {
                return@withContext ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "No matching chunks found"
                )
            }

            // 格式化返回结果
            val content = "Document: ${memory.title}\n" +
                chunks.joinToString("\n---\n") { chunk ->
                    "Chunk ${chunk.chunkIndex + 1}/$totalChunks:\n${chunk.content}"
                }

            val chunkIndices = chunks.map { it.chunkIndex }
            val chunkInfo = if (chunks.size == 1) {
                "Chunk ${chunks[0].chunkIndex + 1}/$totalChunks"
            } else {
                "Chunks ${chunks.map { it.chunkIndex + 1 }.joinToString(", ")}/$totalChunks"
            }

            AppLogger.d(TAG, "Retrieved ${chunks.size} chunks from document '${memory.title}': $chunkInfo")
            
            ToolResult(
                toolName = toolName,
                success = true,
                result = StringResultData(content)
            )
        } catch (e: NumberFormatException) {
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Invalid number format in chunk parameters: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to retrieve document chunks", e)
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Failed to retrieve document chunks: ${e.message}"
            )
        }
    }

    private suspend fun executeCreateMemory(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        
        if (title.isBlank() || content.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Both title and content parameters are required"
            )
        }

        AppLogger.d(TAG, "Creating memory: $title")

        return try {
            val contentType = tool.parameters.find { it.name == "content_type" }?.value ?: "text/plain"
            val source = tool.parameters.find { it.name == "source" }?.value ?: "ai_created"
            val folderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: ""
            
            val memory = memoryRepository.createMemory(
                title = title,
                content = content,
                contentType = contentType,
                source = source,
                folderPath = folderPath
            )
            
            if (memory != null) {
                val message = "Successfully created memory: '$title' (UUID: ${memory.uuid})"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to create memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateMemory(tool: AITool): ToolResult {
        val oldTitle = tool.parameters.find { it.name == "old_title" }?.value
        
        if (oldTitle.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "old_title parameter is required to identify the memory"
            )
        }

        AppLogger.d(TAG, "Updating memory with title: $oldTitle")

        return try {
            val memory = memoryRepository.findMemoryByTitle(oldTitle)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $oldTitle"
                )
            }

            // 获取要更新的字段，如果没有提供则使用原值
            val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: memory.title
            val newContent = tool.parameters.find { it.name == "content" }?.value ?: memory.content
            val newContentType = tool.parameters.find { it.name == "content_type" }?.value ?: memory.contentType
            val newSource = tool.parameters.find { it.name == "source" }?.value ?: memory.source
            val newCredibility = tool.parameters.find { it.name == "credibility" }?.value?.toFloatOrNull() ?: memory.credibility
            val newImportance = tool.parameters.find { it.name == "importance" }?.value?.toFloatOrNull() ?: memory.importance
            val newFolderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: memory.folderPath
            val tagsParam = tool.parameters.find { it.name == "tags" }?.value
            val newTags = tagsParam?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            
            val updatedMemory = memoryRepository.updateMemory(
                memory = memory,
                newTitle = newTitle,
                newContent = newContent,
                newContentType = newContentType,
                newSource = newSource,
                newCredibility = newCredibility,
                newImportance = newImportance,
                newFolderPath = newFolderPath,
                newTags = newTags
            )
            
            if (updatedMemory != null) {
                val message = "Successfully updated memory from '$oldTitle' to '$newTitle'"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to update memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update memory: ${e.message}"
            )
        }
    }

    private suspend fun executeDeleteMemory(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value
        
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required to identify the memory"
            )
        }

        AppLogger.d(TAG, "Deleting memory with title: $title")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            val deleted = memoryRepository.deleteMemory(memory.id)
            
            if (deleted) {
                val message = "Successfully deleted memory: '$title'"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateUserPreferences(tool: AITool): ToolResult {
        AppLogger.d(TAG, "Executing update user preferences")

        return try {
            // 从参数中提取各项偏好设置
            val birthDate = tool.parameters.find { it.name == "birth_date" }?.value?.toLongOrNull()
            val gender = tool.parameters.find { it.name == "gender" }?.value
            val personality = tool.parameters.find { it.name == "personality" }?.value
            val identity = tool.parameters.find { it.name == "identity" }?.value
            val occupation = tool.parameters.find { it.name == "occupation" }?.value
            val aiStyle = tool.parameters.find { it.name == "ai_style" }?.value

            // 检查是否至少有一个参数
            if (birthDate == null && gender == null && personality == null && 
                identity == null && occupation == null && aiStyle == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "At least one preference parameter must be provided"
                )
            }

            // 更新用户偏好
            withContext(Dispatchers.IO) {
                preferencesManager.updateProfileCategory(
                    birthDate = birthDate,
                    gender = gender,
                    personality = personality,
                    identity = identity,
                    occupation = occupation,
                    aiStyle = aiStyle
                )
            }

            val updatedFields = mutableListOf<String>()
            birthDate?.let { updatedFields.add("birth_date") }
            gender?.let { updatedFields.add("gender") }
            personality?.let { updatedFields.add("personality") }
            identity?.let { updatedFields.add("identity") }
            occupation?.let { updatedFields.add("occupation") }
            aiStyle?.let { updatedFields.add("ai_style") }

            val message = "Successfully updated user preferences: ${updatedFields.joinToString(", ")}"
            AppLogger.d(TAG, message)
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(message)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update user preferences", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update user preferences: ${e.message}"
            )
        }
    }

    private suspend fun executeLinkMemories(tool: AITool): ToolResult {
        val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
        val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
        
        if (sourceTitle.isNullOrBlank() || targetTitle.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Both source_title and target_title parameters are required"
            )
        }

        AppLogger.d(TAG, "Linking memories: '$sourceTitle' -> '$targetTitle'")

        return try {
            // 提取可选参数
            val linkType = tool.parameters.find { it.name == "link_type" }?.value ?: "related"
            val weight = tool.parameters.find { it.name == "weight" }?.value?.toFloatOrNull() ?: 0.7f
            val description = tool.parameters.find { it.name == "description" }?.value ?: ""
            
            // 限制 weight 在有效范围内
            val validWeight = weight.coerceIn(0.0f, 1.0f)
            
            // 查找源记忆和目标记忆
            val sourceMemory = memoryRepository.findMemoryByTitle(sourceTitle)
            if (sourceMemory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source memory not found with title: $sourceTitle"
                )
            }
            
            val targetMemory = memoryRepository.findMemoryByTitle(targetTitle)
            if (targetMemory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Target memory not found with title: $targetTitle"
                )
            }
            
            // 创建链接
            memoryRepository.linkMemories(
                source = sourceMemory,
                target = targetMemory,
                type = linkType,
                weight = validWeight,
                description = description
            )
            
            val resultData = MemoryLinkResultData(
                sourceTitle = sourceTitle,
                targetTitle = targetTitle,
                linkType = linkType,
                weight = validWeight,
                description = description
            )
            
            AppLogger.d(TAG, "Successfully linked memories: '$sourceTitle' -> '$targetTitle' (type: $linkType, weight: $validWeight)")
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = resultData
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to link memories", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to link memories: ${e.message}"
            )
        }
    }

    private suspend fun buildResultData(memories: List<Memory>, query: String, limit: Int): MemoryQueryResultData = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        // 当 limit > 20 时，只返回标题和截断内容
        val isTruncatedMode = limit > 20
        val maxContentLength = 40 // 截断后的最大内容长度（更严格）
        
        val memoryInfos = memories.map { memory ->
            val content: String
            val chunkInfo: String?
            val chunkIndices: List<Int>?
            
            if (memory.isDocumentNode) {
                // 对于文档节点，执行"二次探查"，获取匹配的区块内容
                AppLogger.d(TAG, "Memory result is a document ('${memory.title}'). Fetching specific matching chunks for query: '$query'")
                val matchingChunks = memoryRepository.searchChunksInDocument(memory.id, query)
                val totalChunks = memoryRepository.getTotalChunkCount(memory.id)

                if (matchingChunks.isNotEmpty()) {
                    // 收集分块索引（使用1-based显示）
                    chunkIndices = matchingChunks.map { it.chunkIndex }
                    
                    // 生成分块信息摘要
                    chunkInfo = if (matchingChunks.size == 1) {
                        "Chunk ${matchingChunks[0].chunkIndex + 1}/$totalChunks"
                    } else {
                        "Chunks ${matchingChunks.map { it.chunkIndex + 1 }.take(5).joinToString(", ")}/$totalChunks"
                    }
                    
                    if (isTruncatedMode) {
                        // 截断模式：只显示文档标题和分块信息
                        content = "Document: ${memory.title} ($totalChunks chunks)"
                    } else {
                        // 将匹配的区块内容拼接起来，每个区块显示编号
                        content = "Document: ${memory.title}\n" +
                            matchingChunks.take(5) // 最多取5个最相关的区块
                                .joinToString("\n---\n") { chunk -> 
                                    "Chunk ${chunk.chunkIndex + 1}/$totalChunks:\n${chunk.content}"
                                }
                    }
                } else {
                    // 如果二次探查未找到（理论上很少见，因为全局搜索已经认为它相关），提供一个回退信息
                    chunkInfo = null
                    chunkIndices = null
                    if (isTruncatedMode) {
                        content = "Document: ${memory.title}"
                    } else {
                        content = "Document '${memory.title}' was found, but no specific chunks strongly matched the query '$query'. The document's general content is: ${memory.content}"
                    }
                }
            } else {
                // 对于普通记忆
                chunkInfo = null
                chunkIndices = null
                if (isTruncatedMode) {
                    // 截断模式：只返回标题和部分内容
                    content = if (memory.content.length > maxContentLength) {
                        memory.content.take(maxContentLength) + "..."
                    } else {
                        memory.content
                    }
                } else {
                    // 完整模式：返回完整内容
                    content = memory.content
                }
            }

            MemoryQueryResultData.MemoryInfo(
                title = memory.title,
                content = content,
                source = memory.source,
                tags = memory.tags.map { it.name },
                createdAt = sdf.format(memory.createdAt),
                chunkInfo = chunkInfo,
                chunkIndices = chunkIndices
            )
        }
        MemoryQueryResultData(memories = memoryInfos)
    }


    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value
        if (query.isNullOrBlank()) {
            return ToolValidationResult(valid = false, errorMessage = "Missing or empty required parameter: query")
        }
        return ToolValidationResult(valid = true)
    }
} 