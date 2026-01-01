package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 顶级常量，用于顶级函数中的日志
private const val PROBLEM_LIBRARY_TOOL_TAG = "ProblemLibraryTool_Reg"

/**
 * @Deprecated This tool is part of a legacy system for simple problem-solution storage.
 * The new system uses ProblemLibrary to directly analyze conversations and build a knowledge graph
 * in MemoryRepository. This class is maintained for backward compatibility only.
 */
@Deprecated(
    "Use ProblemLibrary for knowledge graph creation instead.",
    ReplaceWith("MemoryRepository", "com.ai.assistance.operit.data.repository.MemoryRepository")
)
class ProblemLibraryTool private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ProblemLibraryTool"

        @Volatile private var INSTANCE: ProblemLibraryTool? = null

        @Deprecated("This tool is part of a legacy system.")
        fun getInstance(context: Context): ProblemLibraryTool {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: ProblemLibraryTool(context.applicationContext).also {
                                    INSTANCE = it
                                    AppLogger.d(TAG, "ProblemLibraryTool (Legacy) 单例实例已创建")
                                }
                    }
        }
    }

    // 问题记录数据类 - 用于与外部API交互
    @Deprecated("ProblemRecord is a legacy data structure. Use Memory objects directly.")
    data class ProblemRecord(
            val uuid: String,
            val query: String,
            val solution: String,
            val tools: List<String>,
            val summary: String = "",
            val timestamp: Long = System.currentTimeMillis()
    )

    // 使用MemoryRepository代替直接数据库访问
    private val memoryRepository = MemoryRepository(context, "default")

    // 将ProblemRecord转换为Memory
    private fun convertToMemory(record: ProblemRecord): Memory {
        return Memory(
                uuid = record.uuid,
                title = record.summary.ifEmpty { record.query.take(50) },
                content = "问题: ${record.query}\n\n解决方案: ${record.solution}",
                contentType = "text/plain",
                source = "problem_library_legacy", // Mark as legacy
                importance = 0.5f, // Lower importance for legacy data
                createdAt = Date(record.timestamp),
                updatedAt = Date(record.timestamp)
        )
    }

    // 将Memory转换为ProblemRecord
    private fun convertToProblemRecord(memory: Memory): ProblemRecord {
        // 尝试从内容中提取问题和解决方案
        val contentParts = memory.content.split("\n\n")
        val query =
                if (contentParts.isNotEmpty() && contentParts[0].startsWith("问题:")) {
                    contentParts[0].substringAfter("问题:").trim()
                } else {
                    memory.title
                }

        val solution =
                if (contentParts.size > 1 && contentParts[1].startsWith("解决方案:")) {
                    contentParts[1].substringAfter("解决方案:").trim()
                } else {
                    memory.content
                }

        // 提取工具信息 - 从标签中获取
        val tools =
                memory.tags.filter { it.name.startsWith("tool:") }.map {
                    it.name.substringAfter("tool:")
                }

        return ProblemRecord(
                uuid = memory.uuid,
                query = query,
                solution = solution,
                tools = tools,
                summary = memory.title,
                timestamp = memory.createdAt.time
        )
    }

    // 保存问题记录
    @Deprecated("This method saves to a legacy data structure.")
    fun saveProblemRecord(record: ProblemRecord) {
        AppLogger.d(TAG, "[Legacy] 开始保存问题记录, UUID: ${record.uuid}")
        kotlinx.coroutines.runBlocking {
            try {
                // 转换为Memory对象
                val memory = convertToMemory(record)
                AppLogger.d(TAG, "[Legacy] 已将ProblemRecord转换为Memory对象")

                // 保存Memory
                memoryRepository.saveMemory(memory)

                // 添加相关标签
                memoryRepository.addTagToMemory(memory, "ProblemLibrary_Legacy")
                AppLogger.d(TAG, "[Legacy] 已添加 'ProblemLibrary_Legacy' 标签")

                // 为每个工具添加标签
                record.tools.forEach { tool ->
                    memoryRepository.addTagToMemory(memory, "tool:$tool")
                }
                AppLogger.d(TAG, "[Legacy] 已为工具添加标签: ${record.tools.joinToString()}")

                AppLogger.d(TAG, "[Legacy] 问题记录已成功保存到Memory系统: ${record.uuid}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "[Legacy] 保存问题记录失败: ${e.message}", e)
            }
        }
    }

    // 获取所有问题记录
    @Deprecated("This method retrieves legacy data.")
    fun getAllProblemRecords(): List<ProblemRecord> {
        return kotlinx.coroutines.runBlocking {
            try {
                // 查询带有ProblemLibrary标签的所有Memory
                val memories = memoryRepository.searchMemories("ProblemLibrary_Legacy")
                memories.map { convertToProblemRecord(it) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取所有 Legacy 问题记录失败: ${e.message}", e)
                emptyList()
            }
        }
    }

    // 搜索问题库
    @Deprecated("This search method uses a legacy data structure.")
    suspend fun searchProblemLibrary(query: String): List<ProblemRecord> =
            withContext(Dispatchers.IO) {
                try {
                    if (query.isBlank()) {
                        // 如果查询为空，返回所有带ProblemLibrary标签的Memory
                        val memories = memoryRepository.searchMemories("ProblemLibrary_Legacy")
                        return@withContext memories.map { convertToProblemRecord(it) }
                    }

                    // 使用MemoryRepository的语义搜索
                    val memories = memoryRepository.searchMemories(query)

                    // 只返回带有ProblemLibrary标签的结果
                    val filteredMemories =
                            memories.filter { memory ->
                                memory.tags.any { it.name == "ProblemLibrary_Legacy" }
                            }

                    return@withContext filteredMemories.map { convertToProblemRecord(it) }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "搜索 Legacy 问题库失败: ${e.message}", e)
                    emptyList()
                }
            }

    // 删除问题记录
    @Deprecated("This method deletes legacy data.")
    fun deleteProblemRecord(uuid: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                // 查找对应UUID的Memory
                val memory = memoryRepository.findMemoryByUuid(uuid)
                if (memory != null && memory.tags.any { it.name == "ProblemLibrary_Legacy" }) {
                    // 删除Memory
                    memoryRepository.deleteMemory(memory.id)
                    AppLogger.d(TAG, "Legacy 问题记录已删除: $uuid")
                    true
                } else {
                    AppLogger.w(TAG, "未找到要删除的 Legacy 问题记录: $uuid")
                    false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "删除 Legacy 问题记录失败: ${e.message}", e)
                false
            }
        }
    }

    // 查询问题库并格式化结果，用于AI工具返回
    @Deprecated("This query method is for a legacy tool.")
    suspend fun queryProblemLibrary(query: String): String =
            withContext(Dispatchers.IO) {
                try {
                    // 搜索问题库
                    val searchResults = searchProblemLibrary(query).take(5) // 最多返回5条记录

                    if (searchResults.isEmpty()) {
                        return@withContext "未找到相关 Legacy 记录"
                    }

                    return@withContext formatProblemLibraryResults(searchResults)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "查询 Legacy 问题库失败: ${e.message}", e)
                    "查询 Legacy 问题库时出错: ${e.message}"
                }
            }

    // 格式化问题库查询结果
    private fun formatProblemLibraryResults(records: List<ProblemRecord>): String {
        val result = StringBuilder()
        result.appendLine("找到 ${records.size} 条相关 Legacy 记录:")

        records.forEach { record ->
            result.appendLine("\nUUID: ${record.uuid}")

            // 优先显示摘要，如果没有则显示原始查询
            if (record.summary.isNotEmpty()) {
                result.appendLine("摘要: ${record.summary}")
            } else {
                result.appendLine("问题: ${record.query}")
            }

            // 显示使用的工具
            result.appendLine("使用工具: ${record.tools.joinToString(", ")}")

            // 显示时间
            result.appendLine(
                    "时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(record.timestamp))}"
            )
        }

        return result.toString()
    }
}
