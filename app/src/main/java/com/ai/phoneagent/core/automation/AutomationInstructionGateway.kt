package com.ai.phoneagent.core.automation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ai.phoneagent.AutomationActivityNew

data class AutomationInstructionRequest(
    val instruction: String,
    val source: Source = Source.MANUAL_AGENT_MODE,
    val autoStart: Boolean = true,
    val forceTopOnEntry: Boolean = false,
    val keepMainOnTop: Boolean = false,
) {
    enum class Source(val wireValue: String) {
        MANUAL_AGENT_MODE("manual_agent_mode"),
        ADVANCED_AI("advanced_ai"),
    }
}

data class AutomationDispatchResult(
    val success: Boolean,
    val message: String,
)

interface AutomationInstructionGateway {
    fun dispatch(context: Context, request: AutomationInstructionRequest): AutomationDispatchResult
}

object ActivityAutomationInstructionGateway : AutomationInstructionGateway {
    override fun dispatch(
        context: Context,
        request: AutomationInstructionRequest
    ): AutomationDispatchResult {
        val task = request.instruction.trim()
        if (task.isBlank()) {
            return AutomationDispatchResult(false, "自动化指令不能为空")
        }
        return runCatching {
            val intent = buildIntent(context, request.copy(instruction = task))
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (context is Activity) {
                context.overridePendingTransition(0, 0)
            }
        }.fold(
            onSuccess = {
                AutomationDispatchResult(true, "任务已转交自动化流程")
            },
            onFailure = { err ->
                AutomationDispatchResult(false, err.message ?: "启动自动化失败")
            }
        )
    }

    fun dispatchManual(context: Context, instruction: String): AutomationDispatchResult {
        return dispatch(
            context,
            AutomationInstructionRequest(
                instruction = instruction,
                source = AutomationInstructionRequest.Source.MANUAL_AGENT_MODE,
                autoStart = true,
                keepMainOnTop = true,
            )
        )
    }

    // 预留给高级 AI 的自动调度入口（当前先和手动路径复用同一链路）
    fun dispatchFromAdvancedAi(context: Context, instruction: String): AutomationDispatchResult {
        return dispatch(
            context,
            AutomationInstructionRequest(
                instruction = instruction,
                source = AutomationInstructionRequest.Source.ADVANCED_AI,
                autoStart = true,
                keepMainOnTop = true,
            )
        )
    }

    fun buildIntent(context: Context, request: AutomationInstructionRequest): Intent {
        return Intent(context, AutomationActivityNew::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(AutomationActivityNew.EXTRA_AUTOMATION_TASK, request.instruction.trim())
            putExtra(AutomationActivityNew.EXTRA_AUTOMATION_SOURCE, request.source.wireValue)
            putExtra(AutomationActivityNew.EXTRA_AUTOMATION_AUTO_START, request.autoStart)
            putExtra(AutomationActivityNew.EXTRA_FORCE_TOP_ON_ENTRY, request.forceTopOnEntry)
            putExtra(AutomationActivityNew.EXTRA_KEEP_MAIN_ON_TOP, request.keepMainOnTop)
        }
    }
}
