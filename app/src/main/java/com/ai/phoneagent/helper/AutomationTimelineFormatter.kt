package com.ai.phoneagent.helper

data class AutomationTimelineEntry(
    var displayText: String,
    var action: String? = null,
)

object AutomationTimelineFormatter {

    fun buildTimeline(logs: List<String>): MutableList<AutomationTimelineEntry> {
        val timeline = mutableListOf<AutomationTimelineEntry>()
        logs.map { AutomationMessageParser.normalizeAutomationLogLine(it) }
            .filter { it.isNotBlank() }
            .forEach { appendEntry(timeline, it) }
        return timeline
    }

    fun appendEntry(timeline: MutableList<AutomationTimelineEntry>, logLine: String) {
        val normalizedLogLine = AutomationMessageParser.normalizeAutomationLogLine(logLine)
        if (normalizedLogLine.isBlank()) return

        val thinkingText = extractDisplayText(normalizedLogLine)
        val intentText = extractIntentTextFromOutput(normalizedLogLine)
        val actionLabel = extractActionLabel(normalizedLogLine)

        if (!thinkingText.isNullOrBlank()) {
            timeline.add(AutomationTimelineEntry(displayText = thinkingText, action = actionLabel))
            return
        }

        if (!intentText.isNullOrBlank()) {
            val lastWithoutAction = timeline.lastOrNull { it.action.isNullOrBlank() }
            if (lastWithoutAction != null) {
                lastWithoutAction.displayText = intentText
                if (!actionLabel.isNullOrBlank()) {
                    lastWithoutAction.action = actionLabel
                }
            } else {
                timeline.add(AutomationTimelineEntry(displayText = intentText, action = actionLabel))
            }
            return
        }

        if (!actionLabel.isNullOrBlank()) {
            val lastWithoutAction = timeline.lastOrNull { it.action.isNullOrBlank() }
            if (lastWithoutAction != null) {
                lastWithoutAction.action = actionLabel
            } else {
                timeline.add(AutomationTimelineEntry(displayText = "", action = actionLabel))
            }
        }
    }

    private fun extractDisplayText(logLine: String): String? {
        val thought =
            when {
                logLine.startsWith("思考：") -> logLine.substringAfter("思考：").trim()
                logLine.startsWith("修复思考：") -> logLine.substringAfter("修复思考：").trim()
                else -> ""
            }
        return thought.ifBlank { null }
    }

    private fun extractIntentTextFromOutput(logLine: String): String? {
        val outputPayload =
            when {
                logLine.startsWith("输出：") -> logLine.substringAfter("输出：").trim()
                logLine.startsWith("修复输出：") -> logLine.substringAfter("修复输出：").trim()
                else -> ""
            }
        if (outputPayload.isBlank()) return null

        val actionFromDo =
            Regex("""action\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.lowercase()
                .orEmpty()
        if (actionFromDo in setOf("type", "input", "text", "type_name")) {
            return null
        }

        val textFromDo =
            Regex("""text\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (!textFromDo.isNullOrBlank()) return textFromDo

        val textFromJson =
            Regex(""""text"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (!textFromJson.isNullOrBlank()) return textFromJson

        return null
    }

    private fun extractActionLabel(logLine: String): String? {
        val actionByCurrent = logLine.substringAfter("当前动作：", "").trim()
        if (actionByCurrent.isNotBlank() && actionByCurrent != logLine) {
            return actionByCurrent.take(10)
        }
        return null
    }
}
