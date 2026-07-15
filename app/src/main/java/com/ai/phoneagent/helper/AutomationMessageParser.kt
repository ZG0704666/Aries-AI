package com.ai.phoneagent.helper

import java.util.Base64

object AutomationMessageParser {

    fun hasEquivalentAssistantMessage(existingAssistantMessages: List<String>, incomingRaw: String): Boolean {
        val incomingStripped = stripAutomationMarker(incomingRaw).trim()
        val incomingAnswer = parseStoredAiContent(incomingStripped).second.trim()
        return existingAssistantMessages.any { existing ->
            val existingStripped = stripAutomationMarker(existing).trim()
            if (existingStripped == incomingStripped) return@any true

            val existingAnswer = parseStoredAiContent(existingStripped).second.trim()
            incomingAnswer.isNotBlank() &&
                existingAnswer.isNotBlank() &&
                existingAnswer == incomingAnswer
        }
    }

    fun encodeAutomationLogMarker(logLine: String): String {
        val encoded = Base64.getEncoder().encodeToString(logLine.toByteArray(Charsets.UTF_8))
        return "[[AUTO_LOG_B64:$encoded]]"
    }

    fun decodeAutomationLogMarker(markerPayload: String): String? {
        return runCatching {
            val bytes = Base64.getDecoder().decode(markerPayload)
            String(bytes, Charsets.UTF_8).trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun extractAutomationLogMarkers(rawMessage: String): Pair<String, List<String>> {
        val markerRegex =
            Regex(
                """\[\[AUTO_LOG_B64:(.*?)]]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val logs =
            markerRegex.findAll(rawMessage).mapNotNull { match ->
                decodeAutomationLogMarker(match.groupValues.getOrNull(1)?.trim().orEmpty())
            }.toList()
        val cleaned = markerRegex.replace(rawMessage, "").trim()
        return cleaned to logs
    }

    fun extractAutomationInstruction(rawAnswer: String): Pair<String, String?> {
        val markerRegex =
            Regex(
                """\[\[AUTO_EXECUTE:(.*?)]]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val match = markerRegex.find(rawAnswer)
        val instruction = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val cleaned = markerRegex.replace(rawAnswer, "").trim()
        return cleaned to instruction.ifBlank { null }
    }

    fun extractAutomationConfirmInstruction(rawMessage: String): Pair<String, String?> {
        val markerRegex =
            Regex(
                """\[\[AUTO_CONFIRM:(.*?)]]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val match = markerRegex.find(rawMessage)
        val instruction = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val cleaned = markerRegex.replace(rawMessage, "").trim()
        return cleaned to instruction.ifBlank { null }
    }

    fun extractAutomationConfirmedMarker(rawMessage: String): Pair<String, Boolean> {
        val markerRegex =
            Regex(
                """\[\[AUTO_CONFIRMED]]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val hasMarker = markerRegex.containsMatchIn(rawMessage)
        val cleaned = markerRegex.replace(rawMessage, "").trim()
        return cleaned to hasMarker
    }

    fun extractAutomationRejectedMarker(rawMessage: String): Pair<String, Boolean> {
        val markerRegex =
            Regex(
                """\[\[AUTO_REJECTED]]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val hasMarker = markerRegex.containsMatchIn(rawMessage)
        val cleaned = markerRegex.replace(rawMessage, "").trim()
        return cleaned to hasMarker
    }

    fun stripAutomationMarker(rawText: String): String {
        val withoutExecute = extractAutomationInstruction(rawText).first
        val withoutConfirm = extractAutomationConfirmInstruction(withoutExecute).first
        val withoutConfirmed = extractAutomationConfirmedMarker(withoutConfirm).first
        val withoutRejected = extractAutomationRejectedMarker(withoutConfirmed).first
        return extractAutomationLogMarkers(withoutRejected).first
    }

    fun stripAutomationRuntimeMarkers(rawText: String): String {
        val withoutLogs = extractAutomationLogMarkers(rawText).first
        val withoutConfirm = extractAutomationConfirmInstruction(withoutLogs).first
        val withoutConfirmed = extractAutomationConfirmedMarker(withoutConfirm).first
        val withoutRejected = extractAutomationRejectedMarker(withoutConfirmed).first
        return withoutRejected.trim()
    }

    fun normalizeAutomationLogLine(rawLine: String): String {
        val line = rawLine.trim()
        return line.replace(Regex("""^\[Step\s+\d+]\s*"""), "").trim()
    }

    fun isAutomationTerminalLog(rawLogLine: String): Boolean {
        val normalized = normalizeAutomationLogLine(rawLogLine)
        if (normalized.isBlank()) return false
        return normalized.startsWith("结束：") ||
            normalized.startsWith("结束:") ||
            normalized == "已停止" ||
            normalized == "已请求停止" ||
            normalized.startsWith("已请求停止") ||
            normalized.startsWith("异常：") ||
            normalized.startsWith("异常:")
    }

    fun parseStoredAiContent(raw: String): Pair<String?, String> {
        val source = raw.trim()
        if (source.isBlank()) return null to ""

        val thinkTagRegex = "<think>([\\s\\S]*?)</think>\\s*([\\s\\S]*)".toRegex()
        thinkTagRegex.find(source)?.let { match ->
            val thinking = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val answer = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val markerStart = source.indexOf('杲')
        val markerEnd = if (markerStart >= 0) source.indexOf('杲', markerStart + 1) else -1
        if (markerStart >= 0 && markerEnd > markerStart) {
            val thinking = source.substring(markerStart + 1, markerEnd).trim()
            val answer = source.substring(markerEnd + 1).trimStart('\n', '\r', ' ').trim()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val leshootRegex = "leshoot([\\s\\S]*?)leshoot([\\s\\S]*)".toRegex()
        leshootRegex.find(source)?.let { match ->
            val thinking = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val answer = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val thinkStartTag = "【思考开始】"
        val thinkEndTag = "【思考结束】"
        val answerStartTag = "【回答开始】"
        val answerEndTag = "【回答结束】"
        val thinkStartIdx = source.indexOf(thinkStartTag)
        val thinkEndIdx = source.indexOf(thinkEndTag)
        if (thinkStartIdx >= 0 && thinkEndIdx > thinkStartIdx) {
            val thinking =
                source.substring(
                    thinkStartIdx + thinkStartTag.length,
                    thinkEndIdx,
                ).trim()
            var answerPart = source.substring(thinkEndIdx + thinkEndTag.length)
            val answerStartIdx = answerPart.indexOf(answerStartTag)
            if (answerStartIdx >= 0) {
                answerPart = answerPart.substring(answerStartIdx + answerStartTag.length)
            }
            val answerEndIdx = answerPart.indexOf(answerEndTag)
            if (answerEndIdx >= 0) {
                answerPart = answerPart.substring(0, answerEndIdx)
            }
            return (thinking.ifBlank { null }) to stripAutomationMarker(answerPart.trim())
        }

        if (source.contains(answerStartTag)) {
            var answerPart = source.substring(source.indexOf(answerStartTag) + answerStartTag.length)
            val answerEndIdx = answerPart.indexOf(answerEndTag)
            if (answerEndIdx >= 0) {
                answerPart = answerPart.substring(0, answerEndIdx)
            }
            return null to stripAutomationMarker(answerPart.trim())
        }

        return null to stripAutomationMarker(source.replace("杲", "").trim())
    }
}
