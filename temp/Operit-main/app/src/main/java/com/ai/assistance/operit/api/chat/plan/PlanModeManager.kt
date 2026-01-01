package com.ai.assistance.operit.api.chat.plan

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 计划模式管理器，负责协调整个深度搜索模式的执行
 */
class PlanModeManager(
    private val context: Context,
    private val enhancedAIService: EnhancedAIService
) {
    private val isCancelled = AtomicBoolean(false)

    companion object {
        private const val TAG = "PlanModeManager"
        
        // 用于生成执行计划的系统提示词
        private const val PLAN_GENERATION_PROMPT = """
你是一个任务规划专家。用户将向你描述一个复杂的任务或问题，你需要将其分解为多个可以并发或顺序执行的子任务。

请按照以下JSON格式返回执行计划：

```json
{
  "tasks": [
    {
      "id": "task_1",
      "name": "任务描述",
      "instruction": "具体的执行指令，这将被发送给AI执行",
      "dependencies": [],
      "type": "chat"
    },
    {
      "id": "task_2", 
      "name": "任务描述",
      "instruction": "具体的执行指令",
      "dependencies": ["task_1"],
      "type": "chat"
    }
  ],
  "final_summary_instruction": "根据所有子任务的结果，提供最终的完整回答"
}
```

规划原则：
1. 将复杂任务分解为3-6个相对独立的子任务
2. 确保每个子任务都有明确的执行指令
3. 合理设置任务间的依赖关系，优先支持并发执行
4. 所有任务类型都设为"chat"
5. 每个instruction应该是一个完整的、可以独立执行的指令
6. 最终汇总指令应该能够整合所有子任务的结果

请分析用户的请求并生成相应的执行计划。
        """
    }
    
    private val taskExecutor = TaskExecutor(context, enhancedAIService)
    
    /**
     * 执行深度搜索模式
     * @param userMessage 用户消息
     * @param chatHistory 聊天历史
     * @param workspacePath 工作区路径
     * @param maxTokens 最大 token 数
     * @param tokenUsageThreshold token 使用阈值
     * @param onNonFatalError 非致命错误回调
     * @return 流式返回整个执行过程
     */
    suspend fun executeDeepSearchMode(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        
        isCancelled.set(false) // 重置取消状态
        try {
            // 开始时设置执行状态，整个计划执行期间保持这个状态
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Processing("正在执行深度搜索模式...")
            )
            
            emit("<log>🧠 启动深度搜索模式...</log>\n")
            emit("<log>📊 正在分析您的请求并生成执行计划...</log>\n")
            
            // 第一步：生成执行计划
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Processing("正在生成执行计划...")
            )
            
            val executionGraph = generateExecutionPlan(
                userMessage, 
                chatHistory, 
                workspacePath, 
                maxTokens, 
                tokenUsageThreshold, 
                onNonFatalError
            )
            
            if (isCancelled.get()) {
                emit("<log>🟡 任务已取消。</log>\n")
                return@stream
            }

            if (executionGraph == null) {
                emit("<error>❌ 无法生成有效的执行计划，切换回普通模式</error>\n")
                // 计划生成失败，恢复idle状态
                enhancedAIService.setInputProcessingState(
                    InputProcessingState.Idle
                )
                return@stream
            }
            
            emit("<plan>\n")
            
            val gson = Gson()
            val planJson = gson.toJson(executionGraph)
            emit("<graph><![CDATA[$planJson]]></graph>\n")

            // emit("\n" + "=".repeat(50) + "\n")
            
            // 第二步：执行计划
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Processing("正在执行子任务...")
            )
            
            val executionStream = taskExecutor.executeSubtasks(
                executionGraph,
                userMessage,
                chatHistory,
                workspacePath,
                maxTokens,
                tokenUsageThreshold,
                onNonFatalError
            )
            
            // 转发执行过程的所有输出
            executionStream.collect { message ->
                emit(message)
            }

            if (isCancelled.get()) {
                emit("<log>🟡 任务已取消，正在停止...</log>\n")
                emit("</plan>\n")
                return@stream
            }
            
            emit("<log>🎯 所有子任务执行完成，开始汇总结果...</log>\n")
            
            emit("</plan>\n")
            
            // 第三步：汇总结果 - 设置汇总状态
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Processing("正在汇总执行结果...")
            )
            
            // 第三步：汇总结果
            val summaryStream = taskExecutor.summarize(
                executionGraph,
                userMessage,
                chatHistory,
                workspacePath,
                maxTokens,
                tokenUsageThreshold,
                onNonFatalError
            )

            summaryStream.collect { message ->
                emit(message)
            }
            
            // 计划执行完成，设置为完成状态
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Completed
            )
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || isCancelled.get()) {
                AppLogger.d(TAG, "深度搜索模式被取消")
                emit("<log>🟡 深度搜索模式已取消。</log>\n")
            } else {
                AppLogger.e(TAG, "深度搜索模式执行失败", e)
                emit("<error>❌ 深度搜索模式执行失败: ${e.message}</error>\n")
            }
            // 执行失败或取消，设置为idle状态
            enhancedAIService.setInputProcessingState(
                InputProcessingState.Idle
            )
        } finally {
            isCancelled.set(false) // 确保在退出时重置状态
        }
    }
    
    /**
     * 生成执行计划
     */
    private suspend fun generateExecutionPlan(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): ExecutionGraph? {
        try {
            // 构建规划请求
            val planningRequest = buildPlanningRequest(userMessage)
            
            // 调用 AI 生成计划
            // 获取专门用于聊天的AI服务实例
            val planningService = EnhancedAIService.getAIServiceForFunction(context, FunctionType.CHAT)

            // 使用获取到的服务实例来发送规划请求
            // 准备包含系统提示词的聊天历史
            val planningHistory = listOf(Pair("system", planningRequest))

            // 使用获取到的服务实例来发送规划请求
            val planningStream = planningService.sendMessage(
                message = "请为这个请求生成详细的执行计划",
                chatHistory = planningHistory, // 传入包含系统提示词的历史
                modelParameters = emptyList(), // 修正类型为 List
                enableThinking = false,
                stream = true, // 明确启用流式传输
                onTokensUpdated = { _, _, _ -> }, // 空的 token 更新回调
                onNonFatalError = onNonFatalError
            )
            // 收集规划结果
            val planBuilder = StringBuilder()
            planningStream.collect { chunk ->
                planBuilder.append(chunk)
            }
            
            val planResponse = ChatUtils.removeThinkingContent(planBuilder.toString().trim())
            AppLogger.d(TAG, "AI生成的执行计划: $planResponse")
            
            // 解析执行计划
            val executionGraph = PlanParser.parseExecutionGraph(planResponse)
            if (executionGraph == null) {
                AppLogger.e(TAG, "解析执行计划失败")
                return null
            }
            
            // 验证执行计划
            val (isValid, errorMessage) = PlanParser.validateExecutionGraph(executionGraph)
            if (!isValid) {
                AppLogger.e(TAG, "执行计划验证失败: $errorMessage")
                return null
            }
            
            AppLogger.d(TAG, "执行计划生成并验证成功，包含 ${executionGraph.tasks.size} 个任务")
            return executionGraph
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                AppLogger.d(TAG, "执行计划生成被取消")
            } else {
                AppLogger.e(TAG, "生成执行计划时发生错误", e)
            }
            return null
        }
    }
    
    /**
     * 构建规划请求
     */
    private fun buildPlanningRequest(userMessage: String): String {
        return """
$PLAN_GENERATION_PROMPT

用户请求：
$userMessage
        """.trim()
    }
    
    /**
     * 取消当前执行
     */
    fun cancel() {
        isCancelled.set(true)
        taskExecutor.cancelAllTasks()
        // 可以在这里取消正在进行的 planningService.sendMessage
        // 但由于 planningService 是局部变量，需要修改结构或依赖注入
        AppLogger.d(TAG, "PlanModeManager cancel called")
    }
    
    /**
     * 检查消息是否适合使用深度搜索模式
     * 这是一个简单的启发式检查，可以根据需要进行优化
     */
    fun shouldUseDeepSearchMode(message: String): Boolean {
        val messageLength = message.length
        val complexityIndicators = listOf(
            "分析", "比较", "研究", "调查", "总结", "评估", "计划", "设计", "开发",
            "详细", "全面", "深入", "系统", "综合", "多角度", "步骤", "方案",
            "分几个", "多个方面", "详细解释", "具体分析", "如何实现", "实施方案"
        )
        
        val hasComplexityIndicators = complexityIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
        
        // 消息长度超过50字符或包含复杂性指标
        return messageLength > 50 || hasComplexityIndicators
    }
} 