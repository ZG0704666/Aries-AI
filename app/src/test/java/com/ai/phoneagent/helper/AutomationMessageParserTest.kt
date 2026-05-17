package com.ai.phoneagent.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationMessageParserTest {

    @Test
    fun stripAutomationRuntimeMarkersKeepsCommandAndRemovesStaleRuntimeState() {
        val logMarker = AutomationMessageParser.encodeAutomationLogMarker("[Step 20] 输出：doaction=\"Tap\"")
        val raw =
            """
            待转交自动化命令：
            打开系统设置，开启深色模式
            [[AUTO_CONFIRM:打开系统设置，开启深色模式]]
            [[AUTO_CONFIRMED]]
            [[AUTO_REJECTED]]
            $logMarker
            """.trimIndent()

        val cleaned = AutomationMessageParser.stripAutomationRuntimeMarkers(raw)

        assertEquals(
            "待转交自动化命令：\n打开系统设置，开启深色模式",
            cleaned,
        )
        assertFalse(cleaned.contains("AUTO_CONFIRM"))
        assertFalse(cleaned.contains("AUTO_CONFIRMED"))
        assertFalse(cleaned.contains("AUTO_REJECTED"))
        assertFalse(cleaned.contains("AUTO_LOG_B64"))
    }

    @Test
    fun extractAutomationLogMarkersDoesNotLeaveVisibleLogText() {
        val marker = AutomationMessageParser.encodeAutomationLogMarker("[Step 21] 当前动作：点击设置选项")
        val (cleaned, logs) = AutomationMessageParser.extractAutomationLogMarkers("正文\n$marker")

        assertEquals("正文", cleaned)
        assertEquals(listOf("[Step 21] 当前动作：点击设置选项"), logs)
        assertTrue(marker.contains("AUTO_LOG_B64"))
    }
}
