package com.ai.phoneagent.net

import com.ai.phoneagent.helper.StreamingJsonXmlConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 流式响应解析测试。
 *
 * 这些测试通过 [parseAutoGlmSseLine] / [parseOpenAiSseLine] 这两个生产解析器
 * 验证 SSE 协议契约，不再维护私有副本。StreamingJsonXmlConverter 等工具仍来自生产代码，
 * 确保"测试通过"等价于"生产行为正确"。
 *
 * OpenAI 端测试保留了完整的 tool-call 流式编排逻辑（converter 状态机、tool 块开闭、
 * 未终止块的 done flush），这部分编排策略确实存在于测试而非生产代码中——
 * 因为生产侧把 onChunk 回调直接交给调用方决定，编排策略由调用方决定；
 * 测试将其固化为参考实现，便于回归对照。
 */
class SseParsingTest {

    @Test
    fun `openai parser parses single token stream`() {
        val result = parseOpenAiSse(
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
        val result = parseOpenAiSse(
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
        val result = parseOpenAiSse(
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
        val result = parseOpenAiSse(
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
        val result = parseAutoGlmSse(
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
        val result = parseAutoGlmSse(
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
        val result = parseAutoGlmSse(
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

    /**
     * OpenAI SSE 流式编排参考实现：消费 [parseOpenAiSseLine]，按 enableToolCall
     * 决定是否将 tool_calls 增量交给 StreamingJsonXmlConverter，并在普通 content
     * 到达或 [DONE] 时正确开闭 tool 块。
     */
    private fun parseOpenAiSse(lines: List<String>, enableToolCall: Boolean): OpenAiParseResult {
        val chunks = mutableListOf<String>()
        val converter = StreamingJsonXmlConverter()
        var isInToolCall = false
        var doneSeen = false

        for (currentLine in lines) {
            when (val ev = parseOpenAiSseLine(currentLine)) {
                is OpenAiSseEvent.Skip -> continue
                is OpenAiSseEvent.Done -> {
                    doneSeen = true
                    break
                }
                is OpenAiSseEvent.Delta -> {
                    val toolCalls = ev.toolCalls.orEmpty()
                    if (toolCalls.isNotEmpty() && enableToolCall) {
                        processToolCallsDelta(toolCalls, converter, chunks)
                        isInToolCall = true
                        continue
                    }

                    val content = ev.content.orEmpty()
                    if (content.isNotEmpty()) {
                        if (isInToolCall) {
                            flushConverter(converter, chunks)
                            chunks += "\n</tool>\n"
                            isInToolCall = false
                        }
                        chunks += content
                    }
                }
            }
        }

        if (isInToolCall) {
            flushConverter(converter, chunks)
            chunks += "\n</tool>\n"
        }

        return OpenAiParseResult(chunks = chunks, doneSeen = doneSeen)
    }

    private fun processToolCallsDelta(
        toolCalls: List<OpenAiSseToolCall>,
        converter: StreamingJsonXmlConverter,
        chunks: MutableList<String>,
    ) {
        for (toolCall in toolCalls) {
            val name = toolCall.name.orEmpty()
            if (name.isNotEmpty()) {
                chunks += "\n<tool name=\"$name\">"
            }

            val arguments = toolCall.arguments.orEmpty()
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

    private fun flushConverter(converter: StreamingJsonXmlConverter, chunks: MutableList<String>) {
        val events = converter.flush()
        events.forEach { event ->
            when (event) {
                is StreamingJsonXmlConverter.Event.Tag -> chunks += event.text
                is StreamingJsonXmlConverter.Event.Content -> chunks += event.text
            }
        }
    }

    /**
     * AutoGlm SSE 流式编排参考实现：消费 [parseAutoGlmSseLine]，把 reasoning 与 content
     * 增量分别归并到列表，并跟踪是否收到过任意有效 delta。
     */
    private fun parseAutoGlmSse(lines: List<String>): AutoGlmParseResult {
        val reasoningDeltas = mutableListOf<String>()
        val contentDeltas = mutableListOf<String>()
        var receivedAnyDelta = false
        var doneSeen = false

        for (line in lines) {
            when (val ev = parseAutoGlmSseLine(line)) {
                is AutoGlmSseEvent.Skip -> continue
                is AutoGlmSseEvent.Done -> {
                    doneSeen = true
                    break
                }
                is AutoGlmSseEvent.Delta -> {
                    val reasoning = ev.reasoning
                    val content = ev.content
                    if (!reasoning.isNullOrEmpty()) reasoningDeltas += reasoning
                    if (!content.isNullOrEmpty()) contentDeltas += content
                    if (!reasoning.isNullOrEmpty() || !content.isNullOrEmpty()) {
                        receivedAnyDelta = true
                    }
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
