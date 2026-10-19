package com.ai.phoneagent.core.automation

import android.content.Context
import android.content.Intent

/** 自动化日志广播桥：通过广播把自动化执行日志分发给 UI 层展示。 */
object AutomationLogBridge {
    /** 广播 Action，用于注册接收器。 */
    const val ACTION_AUTOMATION_LOG =
        "com.ai.phoneagent.action.AUTOMATION_LOG"
    private const val EXTRA_LOG_LINE = "automation_log_line"

    /** 发布一条日志广播；空白行会被忽略。 */
    fun publish(context: Context, line: String) {
        val normalized = line.trim()
        if (normalized.isBlank()) return
        val intent = Intent(ACTION_AUTOMATION_LOG).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LOG_LINE, normalized)
        }
        context.sendBroadcast(intent)
    }

    /** 从广播 Intent 中还原日志行，空白返回 null。 */
    fun extract(intent: Intent?): String? {
        val line = intent?.getStringExtra(EXTRA_LOG_LINE)?.trim().orEmpty()
        return line.ifBlank { null }
    }
}
