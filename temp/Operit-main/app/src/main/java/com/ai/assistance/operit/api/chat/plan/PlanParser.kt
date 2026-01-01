package com.ai.assistance.operit.api.chat.plan

import com.ai.assistance.operit.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.*

/**
 * 计划解析器，负责解析 AI 生成的执行图并提供拓扑排序功能
 */
object PlanParser {
    private const val TAG = "PlanParser"
    private val gson = Gson()
    
    /**
     * 从 JSON 字符串解析执行图
     * @param jsonString AI 生成的 JSON 格式执行图
     * @return 解析后的执行图，如果解析失败则返回 null
     */
    fun parseExecutionGraph(jsonString: String): ExecutionGraph? {
        return try {
            val cleanedJson = extractJsonFromResponse(jsonString)
            AppLogger.d(TAG, "解析执行图: $cleanedJson")
            gson.fromJson(cleanedJson, ExecutionGraph::class.java)
        } catch (e: JsonSyntaxException) {
            AppLogger.e(TAG, "解析执行图失败: JSON 语法错误", e)
            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析执行图失败: 未知错误", e)
            null
        }
    }
    
    /**
     * 从 AI 响应中提取 JSON 部分
     * AI 可能会在 JSON 前后添加说明文字，需要提取出纯净的 JSON
     */
    private fun extractJsonFromResponse(response: String): String {
        // 寻找第一个 { 和最后一个 }
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        
        return if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            response.substring(firstBrace, lastBrace + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            response
        }
    }
    
    /**
     * 对任务进行拓扑排序，确定执行顺序
     * @param graph 执行图
     * @return 按执行顺序排列的任务列表，如果存在循环依赖则返回空列表
     */
    fun topologicalSort(graph: ExecutionGraph): List<TaskNode> {
        val tasks = graph.tasks
        val taskMap = tasks.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val adjList = mutableMapOf<String, MutableList<String>>()
        
        // 初始化
        tasks.forEach { task ->
            inDegree[task.id] = 0
            adjList[task.id] = mutableListOf()
        }
        
        // 构建邻接表和入度表
        tasks.forEach { task ->
            task.dependencies.forEach { depId ->
                if (taskMap.containsKey(depId)) {
                    adjList[depId]?.add(task.id)
                    inDegree[task.id] = inDegree[task.id]!! + 1
                } else {
                    AppLogger.w(TAG, "任务 ${task.id} 依赖的任务 $depId 不存在")
                }
            }
        }
        
        // Kahn 算法进行拓扑排序
        val queue: Queue<String> = LinkedList()
        val result = mutableListOf<TaskNode>()
        
        // 找到所有入度为 0 的节点
        inDegree.forEach { (taskId, degree) ->
            if (degree == 0) {
                queue.offer(taskId)
            }
        }
        
        while (queue.isNotEmpty()) {
            val currentId = queue.poll() ?: break
            val currentTask = taskMap[currentId]
            if (currentTask != null) {
                result.add(currentTask)
                
                // 减少相邻节点的入度
                adjList[currentId]?.forEach { neighborId ->
                    inDegree[neighborId] = inDegree[neighborId]!! - 1
                    if (inDegree[neighborId] == 0) {
                        queue.offer(neighborId)
                    }
                }
            }
        }
        
        // 检查是否存在循环依赖
        if (result.size != tasks.size) {
            AppLogger.e(TAG, "存在循环依赖，无法进行拓扑排序")
            return emptyList()
        }
        
        AppLogger.d(TAG, "拓扑排序完成，执行顺序: ${result.map { it.id }}")
        return result
    }
    
    /**
     * 验证执行图的有效性
     * @param graph 执行图
     * @return 验证结果和错误信息
     */
    fun validateExecutionGraph(graph: ExecutionGraph): Pair<Boolean, String> {
        // 检查任务 ID 是否唯一
        val taskIds = graph.tasks.map { it.id }
        if (taskIds.size != taskIds.toSet().size) {
            return false to "任务 ID 不唯一"
        }
        
        // 检查依赖关系是否有效
        val validTaskIds = taskIds.toSet()
        graph.tasks.forEach { task ->
            task.dependencies.forEach { depId ->
                if (!validTaskIds.contains(depId)) {
                    return false to "任务 ${task.id} 依赖的任务 $depId 不存在"
                }
            }
        }
        
        // 检查是否存在循环依赖
        val sortedTasks = topologicalSort(graph)
        if (sortedTasks.isEmpty() && graph.tasks.isNotEmpty()) {
            return false to "存在循环依赖"
        }
        
        return true to "执行图有效"
    }
} 