package com.ai.phoneagent.net

import com.ai.phoneagent.data.model.ChatContent
import com.ai.phoneagent.data.model.ContentPart
import com.ai.phoneagent.data.model.ImageUrl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that [ChatRequestMessage], [ChatRequest], and [ChatResponse]
 * serializes/deserializes according to the OpenAI API format.
 */
class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // ─── ChatRequestMessage with Text content ─────────────────────────────────

    @Test
    fun `ChatRequestMessage text content serializes to bare string`() {
        val message = ChatRequestMessage(role = "user", content = ChatContent.Text("hello"))
        val encoded = json.encodeToString(message)
        // content must be a bare JSON string, not an object
        assertEquals("""{"role":"user","content":"hello"}""", encoded)
    }

    @Test
    fun `ChatRequestMessage assistant role serializes correctly`() {
        val message = ChatRequestMessage(role = "assistant", content = ChatContent.Text("Hi there!"))
        val encoded = json.encodeToString(message)
        assertEquals("""{"role":"assistant","content":"Hi there!"}""", encoded)
    }

    // ─── ChatRequestMessage with Multimodal content ──────────────────────────

    @Test
    fun `ChatRequestMessage multimodal content serializes to array`() {
        val message = ChatRequestMessage(
            role = "user",
            content = ChatContent.Multimodal(
                listOf(
                    ContentPart.TextPart("describe this image"),
                    ContentPart.ImageUrlPart(ImageUrl("https://example.com/img.png"))
                )
            )
        )
        val encoded = json.encodeToString(message)
        val expected = """{"role":"user","content":[{"type":"text","text":"describe this image"},{"type":"image_url","image_url":{"url":"https://example.com/img.png"}}]}"""
        assertEquals(expected, encoded)
    }

    @Test
    fun `ChatRequestMessage text-only multimodal serializes as array`() {
        val message = ChatRequestMessage(
            role = "user",
            content = ChatContent.Multimodal(listOf(ContentPart.TextPart("hello")))
        )
        val encoded = json.encodeToString(message)
        assertEquals("""{"role":"user","content":[{"type":"text","text":"hello"}]}""", encoded)
    }

    // ─── ChatContent.Text in message context ─────────────────────────────────

    @Test
    fun `ChatContent Text in message context serializes as bare string not object`() {
        val message = ChatRequestMessage(role = "system", content = ChatContent.Text("You are a helpful assistant."))
        val encoded = json.encodeToString(message)
        // must NOT contain {"text": ...} — must be a bare string
        assertTrue("content must be a bare string", encoded.contains(""""content":"You are a helpful assistant.""""))
    }

    // ─── ChatRequest full request body ───────────────────────────────────────

    @Test
    fun `ChatRequest serializes model messages and stream fields`() {
        val req = ChatRequest(
            model = "gpt-4",
            messages = listOf(
                ChatRequestMessage(role = "user", content = ChatContent.Text("hello"))
            ),
            stream = true
        )
        val encoded = json.encodeToString(req)
        assertTrue("model field present", encoded.contains(""""model":"gpt-4""""))
        assertTrue("stream field present", encoded.contains(""""stream":true"""))
        assertTrue("messages array present", encoded.contains(""""messages":["""))
        assertTrue("role field present", encoded.contains(""""role":"user""""))
        assertTrue("content as bare string", encoded.contains(""""content":"hello""""))
    }

    @Test
    fun `ChatRequest with temperature and max_tokens serializes optional fields`() {
        val req = ChatRequest(
            model = "gpt-4o",
            messages = listOf(ChatRequestMessage(role = "user", content = ChatContent.Text("hi"))),
            stream = false,
            temperature = 0.7f,
            max_tokens = 1024
        )
        val encoded = json.encodeToString(req)
        assertTrue("temperature present", encoded.contains(""""temperature":"""))
        assertTrue("max_tokens present", encoded.contains(""""max_tokens":1024"""))
    }

    // ─── ChatResponse deserialization ─────────────────────────────────────────

    @Test
    fun `ChatResponse deserializes simple assistant message`() {
        val jsonStr = """{"choices":[{"index":0,"message":{"role":"assistant","content":"Hi!"}}]}"""
        val resp = Json.decodeFromString<ChatResponse>(jsonStr)
        assertNotNull(resp.choices)
        assertEquals(1, resp.choices!!.size)
        val choice = resp.choices[0]
        assertEquals(0, choice.index)
        assertNotNull(choice.message)
        assertEquals("assistant", choice.message!!.role)
        assertEquals("Hi!", choice.message.content)
    }

    @Test
    fun `ChatResponse with empty choices deserializes correctly`() {
        val jsonStr = """{"choices":[]}"""
        val resp = Json.decodeFromString<ChatResponse>(jsonStr)
        assertNotNull(resp.choices)
        assertTrue(resp.choices!!.isEmpty())
    }

    @Test
    fun `ChatResponse with null choices deserializes correctly`() {
        val jsonStr = """{"choices":null}"""
        val resp = Json.decodeFromString<ChatResponse>(jsonStr)
        assertNull(resp.choices)
    }

    @Test
    fun `ChatResponse ignores unknown fields`() {
        // Extra fields like "id", "object", "usage" should not cause failure
        val jsonStr = """
            {
              "id": "chatcmpl-abc123",
              "object": "chat.completion",
              "created": 1711000000,
              "model": "gpt-4",
              "choices": [
                {
                  "index": 0,
                  "message": {"role":"assistant","content":"Hello world"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            }
        """.trimIndent()
        val resp = Json { ignoreUnknownKeys = true }.decodeFromString<ChatResponse>(jsonStr)
        assertNotNull(resp.choices)
        assertEquals(1, resp.choices!!.size)
        assertEquals("Hello world", resp.choices[0].message?.content)
    }

    @Test
    fun `ChatResponse with null message in choice deserializes correctly`() {
        val jsonStr = """{"choices":[{"index":0,"message":null}]}"""
        val resp = Json { ignoreUnknownKeys = true }.decodeFromString<ChatResponse>(jsonStr)
        assertNotNull(resp.choices)
        assertNull(resp.choices!![0].message)
    }

    // ─── ChatContent.Multimodal with base64 image ────────────────────────────

    @Test
    fun `ChatRequestMessage with base64 image url serializes correctly`() {
        val base64Url = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD"
        val message = ChatRequestMessage(
            role = "user",
            content = ChatContent.Multimodal(
                listOf(
                    ContentPart.TextPart("What is in this image?"),
                    ContentPart.ImageUrlPart(ImageUrl(base64Url))
                )
            )
        )
        val encoded = json.encodeToString(message)
        assertTrue("base64 url present", encoded.contains(base64Url))
        assertTrue("type image_url present", encoded.contains(""""type":"image_url""""))
        assertTrue("type text present", encoded.contains(""""type":"text""""))
    }

    // ─── Round-trip tests ─────────────────────────────────────────────────────

    @Test
    fun `ChatRequestMessage Text round-trips through encode and decode`() {
        val original = ChatRequestMessage(role = "user", content = ChatContent.Text("round trip test"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatRequestMessage>(encoded)
        assertEquals(original.role, decoded.role)
        val originalContent = original.content as ChatContent.Text
        val decodedContent = decoded.content as ChatContent.Text
        assertEquals(originalContent.text, decodedContent.text)
    }

    @Test
    fun `ChatRequest full round-trip with multiple messages`() {
        val req = ChatRequest(
            model = "gpt-4-vision-preview",
            messages = listOf(
                ChatRequestMessage(role = "system", content = ChatContent.Text("You are helpful.")),
                ChatRequestMessage(
                    role = "user",
                    content = ChatContent.Multimodal(
                        listOf(
                            ContentPart.TextPart("Look at this"),
                            ContentPart.ImageUrlPart(ImageUrl("https://example.com/photo.jpg"))
                        )
                    )
                )
            ),
            stream = true,
            temperature = 0.5f
        )
        val encoded = json.encodeToString(req)
        // Verify all critical fields are present
        assertTrue(encoded.contains(""""model":"gpt-4-vision-preview""""))
        assertTrue(encoded.contains(""""role":"system""""))
        assertTrue(encoded.contains(""""role":"user""""))
        assertTrue(encoded.contains(""""stream":true"""))
        assertTrue(encoded.contains(""""type":"image_url""""))
    }
}
