package com.ai.phoneagent.net

import com.ai.phoneagent.helper.StreamingJsonXmlConverter
import com.google.gson.JsonParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParsingTest {

    @Test
    fun `openai parser parses single token stream`() {
        val result =
            parseOpenAiSse(
                listOf(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}",
                    "data: [DONE]",
                ),
                enableToolCall = false,
            )

        assertEquals(listOf("Hi"), result.chunks)
        assertTrue(result.doneSeen)
    }

    @Test
    fun `openai parser parses multi token fixture and stops at done`() {
        val lines = readFixtureLines("normal-stream.txt")

        val result = parseOpenAiSse(lines, enableToolCall = false)

        assertEquals(listOf("Hello", " world"), result.chunks)
        assertEquals("Hello world", result.chunks.joinToString(""))
        assertTrue(result.doneSeen)
    }

    @Test
    fun `openai parser ignores empty lines and non data lines`() {
        val result =
            parseOpenAiSse(
                listOf(
                    "",
                    "event: message",
                    "data:    ",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}",
                    "data: [DONE]",
                ),
                enableToolCall = false,
            )

        assertEquals(listOf("ok"), result.chunks)
    }

    @Test
    fun `openai parser requires data prefix with trailing space`() {
        val result =
            parseOpenAiSse(
                listOf(
                    "data:{\"choices\":[{\"delta\":{\"content\":\"ignored\"}}]}",
                    "data: [DONE]",
                ),
                enableToolCall = false,
            )

        assertTrue(result.chunks.isEmpty())
        assertTrue(result.doneSeen)
    }

    @Test
    fun `openai parser ignores malformed chunks and continues`() {
        val lines = readFixtureLines("error-stream.txt")

        val result = parseOpenAiSse(lines, enableToolCall = false)

        assertEquals(listOf("ok"), result.chunks)
        assertTrue(result.doneSeen)
    }

    @Test
    fun `openai parser emits tool call xml then content`() {
        val lines = readFixtureLines("tool-call-stream.txt")

        val result = parseOpenAiSse(lines, enableToolCall = true)

        assertEquals(
            listOf(
                "\n<tool name=\"search\">",
                "<param name=\"query\">",
                "kotlin",
                "</param>",
                "\n</tool>\n",
                "result ready",
            ),
            result.chunks,
        )
    }

    @Test
    fun `openai parser flushes unterminated tool block on done`() {
        val result =
            parseOpenAiSse(
                listOf(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"query\\\":\\\"kotlin\\\"}\"}}]}}]}",
                    "data: [DONE]",
                ),
                enableToolCall = true,
            )

        assertTrue(result.chunks.contains("\n<tool name=\"search\">"))
        assertTrue(result.chunks.contains("<param name=\"query\">"))
        assertTrue(result.chunks.contains("kotlin"))
        assertTrue(result.chunks.contains("</param>"))
        assertEquals("\n</tool>\n", result.chunks.last())
    }

    @Test
    fun `autoglm parser accepts data prefix without trailing space`() {
        val result =
            parseAutoGlmSse(
                listOf(
                    "data:{\"choices\":[{\"delta\":{\"content\":\"A\"}}]}",
                    "data: [DONE]",
                )
            )

        assertEquals(listOf("A"), result.contentDeltas)
        assertTrue(result.receivedAnyDelta)
    }

    @Test
    fun `autoglm parser reads reasoning content and message fallback`() {
        val result =
            parseAutoGlmSse(
                listOf(
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"think\",\"content\":\"answer\"}}]}",
                    "data: {\"choices\":[{\"message\":{\"content\":\"fallback\"}}]}",
                    "data: [DONE]",
                )
            )

        assertEquals(listOf("think"), result.reasoningDeltas)
        assertEquals(listOf("answer", "fallback"), result.contentDeltas)
        assertTrue(result.receivedAnyDelta)
    }

    @Test
    fun `autoglm parser ignores malformed empty and choice empty chunks`() {
        val lines = readFixtureLines("error-stream.txt")

        val result = parseAutoGlmSse(lines)

        assertEquals(listOf("ok"), result.contentDeltas)
        assertTrue(result.receivedAnyDelta)
        assertTrue(result.doneSeen)
    }

    @Test
    fun `autoglm parser marks stream as empty when no delta emitted`() {
        val result =
            parseAutoGlmSse(
                listOf(
                    "event: message",
                    "",
                    "data: {\"choices\":[]}",
                    "data: [DONE]",
                )
            )

        assertFalse(result.receivedAnyDelta)
        assertTrue(result.contentDeltas.isEmpty())
    }

    private fun readFixtureLines(name: String): List<String> {
        val stream = javaClass.classLoader?.getResourceAsStream("sse/$name")
            ?: throw AssertionError("Missing test fixture: sse/$name")
        return stream.bufferedReader().use { it.readLines() }
    }

    private fun parseOpenAiSse(lines: List<String>, enableToolCall: Boolean): OpenAiParseResult {
        val chunks = mutableListOf<String>()
        val converter = StreamingJsonXmlConverter()
        var isInToolCall = false
        var doneSeen = false

        for (currentLine in lines) {
            if (!currentLine.startsWith("data: ")) continue

            val data = currentLine.substring(6).trim()
            if (data == "[DONE]") {
                doneSeen = true
                break
            }
            if (data.isBlank()) continue

            try {
                val jsonResponse = JSONObject(data)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) continue

                val choice = choices.getJSONObject(0)
                val delta = choice.optJSONObject("delta") ?: continue

                val toolCalls = delta.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0 && enableToolCall) {
                    processToolCallsDelta(toolCalls, converter, chunks)
                    isInToolCall = true
                    continue
                }

                val content = delta.optString("content", "")
                if (content.isNotEmpty()) {
                    if (isInToolCall) {
                        val events = converter.flush()
                        events.forEach { event ->
                            when (event) {
                                is StreamingJsonXmlConverter.Event.Tag -> chunks += event.text
                                is StreamingJsonXmlConverter.Event.Content -> chunks += event.text
                            }
                        }
                        chunks += "\n</tool>\n"
                        isInToolCall = false
                    }
                    chunks += content
                }
            } catch (_: Exception) {
                // Capture exact current behavior: ignore parse errors
            }
        }

        if (isInToolCall) {
            val events = converter.flush()
            events.forEach { event ->
                when (event) {
                    is StreamingJsonXmlConverter.Event.Tag -> chunks += event.text
                    is StreamingJsonXmlConverter.Event.Content -> chunks += event.text
                }
            }
            chunks += "\n</tool>\n"
        }

        return OpenAiParseResult(chunks = chunks, doneSeen = doneSeen)
    }

    private fun processToolCallsDelta(
        toolCalls: JSONArray,
        converter: StreamingJsonXmlConverter,
        chunks: MutableList<String>,
    ) {
        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.getJSONObject(i)
            val function = toolCall.optJSONObject("function") ?: continue

            val name = function.optString("name", "")
            if (name.isNotEmpty()) {
                chunks += "\n<tool name=\"$name\">"
            }

            val arguments = function.optString("arguments", "")
            if (arguments.isNotEmpty()) {
                val events = converter.feed(arguments)
                events.forEach { event ->
                    when (event) {
                        is StreamingJsonXmlConverter.Event.Tag -> chunks += event.text
                        is StreamingJsonXmlConverter.Event.Content -> chunks += event.text
                    }
                }
            }
        }
    }

    private fun parseAutoGlmSse(lines: List<String>): AutoGlmParseResult {
        val reasoningDeltas = mutableListOf<String>()
        val contentDeltas = mutableListOf<String>()
        var receivedAnyDelta = false
        var doneSeen = false

        for (line in lines) {
            if (line.isBlank()) continue
            if (!line.startsWith("data:")) continue

            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") {
                doneSeen = true
                break
            }

            val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue

            val choices = obj.getAsJsonArray("choices") ?: continue
            if (choices.size() == 0) continue

            val choice0 = choices[0].asJsonObject

            val deltaEl = choice0.get("delta")
            val delta = if (deltaEl != null && deltaEl.isJsonObject) deltaEl.asJsonObject else null

            if (delta != null) {
                val reasoningEl = delta.get("reasoning_content") ?: delta.get("reasoning")
                val contentEl = delta.get("content")
                val reasoning =
                    if (reasoningEl != null && !reasoningEl.isJsonNull) reasoningEl.asString else null
                val content = if (contentEl != null && !contentEl.isJsonNull) contentEl.asString else null

                if (!reasoning.isNullOrEmpty()) reasoningDeltas += reasoning
                if (!content.isNullOrEmpty()) contentDeltas += content
                if (!reasoning.isNullOrEmpty() || !content.isNullOrEmpty()) {
                    receivedAnyDelta = true
                }
            } else {
                val messageEl = choice0.get("message")
                val message = if (messageEl != null && messageEl.isJsonObject) messageEl.asJsonObject else null
                val contentEl = message?.get("content")
                val content = if (contentEl != null && !contentEl.isJsonNull) contentEl.asString else null
                if (!content.isNullOrEmpty()) contentDeltas += content
                if (!content.isNullOrEmpty()) {
                    receivedAnyDelta = true
                }
            }
        }

        return AutoGlmParseResult(
            reasoningDeltas = reasoningDeltas,
            contentDeltas = contentDeltas,
            receivedAnyDelta = receivedAnyDelta,
            doneSeen = doneSeen,
        )
    }

    private data class OpenAiParseResult(
        val chunks: List<String>,
        val doneSeen: Boolean,
    )

    private data class AutoGlmParseResult(
        val reasoningDeltas: List<String>,
        val contentDeltas: List<String>,
        val receivedAnyDelta: Boolean,
        val doneSeen: Boolean,
    )
}
