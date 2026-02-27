package com.ai.phoneagent.core.automation

import android.content.Context
import android.content.Intent

object AutomationLogBridge {
    const val ACTION_AUTOMATION_LOG =
        "com.ai.phoneagent.action.AUTOMATION_LOG"
    private const val EXTRA_LOG_LINE = "automation_log_line"

    fun publish(context: Context, line: String) {
        val normalized = line.trim()
        if (normalized.isBlank()) return
        val intent = Intent(ACTION_AUTOMATION_LOG).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LOG_LINE, normalized)
        }
        context.sendBroadcast(intent)
    }

    fun extract(intent: Intent?): String? {
        val line = intent?.getStringExtra(EXTRA_LOG_LINE)?.trim().orEmpty()
        return line.ifBlank { null }
    }
}
