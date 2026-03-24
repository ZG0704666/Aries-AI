package com.ai.phoneagent.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that [ChatContent] serializes/deserializes according to the
 * OpenAI API content field format:
 *  - [ChatContent.Text]       → bare JSON string (not a JSON object)
 *  - [ChatContent.Multimodal] → JSON array of content parts
 */
class ChatContentSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ─── ChatContent.Text ──────────────────────────────────────────────────

    @Test
    fun `Text serializes to bare JSON string`() {
        val content: ChatContent = ChatContent.Text("hello")
        val encoded = json.encodeToString(content)
        assertEquals("\"hello\"", encoded)
    }

    @Test
    fun `Text with special chars serializes correctly`() {
        val content: ChatContent = ChatContent.Text("hello\nworld")
        val encoded = json.encodeToString(content)
        assertEquals("\"hello\\nworld\"", encoded)
    }

    @Test
    fun `bare JSON string deserializes to Text`() {
        val decoded = json.decodeFromString<ChatContent>("\"hello\"")
        assertTrue(decoded is ChatContent.Text)
        assertEquals("hello", (decoded as ChatContent.Text).text)
    }

    @Test
    fun `empty string serializes and round-trips as Text`() {
        val content: ChatContent = ChatContent.Text("")
        val encoded = json.encodeToString(content)
        assertEquals("\"\"", encoded)
        val decoded = json.decodeFromString<ChatContent>(encoded)
        assertEquals(content, decoded)
    }

    // ─── ChatContent.Multimodal ────────────────────────────────────────────

    @Test
    fun `Multimodal with single TextPart serializes to JSON array`() {
        val content: ChatContent = ChatContent.Multimodal(
            listOf(ContentPart.TextPart("hello world"))
        )
        val encoded = json.encodeToString(content)
        assertEquals("""[{"type":"text","text":"hello world"}]""", encoded)
    }

    @Test
    fun `Multimodal with ImageUrlPart serializes correctly`() {
        val content: ChatContent = ChatContent.Multimodal(
            listOf(
                ContentPart.ImageUrlPart(ImageUrl("https://example.com/img.png"))
            )
        )
        val encoded = json.encodeToString(content)
        assertEquals(
            """[{"type":"image_url","image_url":{"url":"https://example.com/img.png"}}]""",
            encoded
        )
    }

    @Test
    fun `Multimodal with text and image parts serializes in order`() {
        val content: ChatContent = ChatContent.Multimodal(
            listOf(
                ContentPart.TextPart("describe this image"),
                ContentPart.ImageUrlPart(ImageUrl("data:image/jpeg;base64,abc123")),
            )
        )
        val encoded = json.encodeToString(content)
        val expected = """[{"type":"text","text":"describe this image"},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,abc123"}}]"""
        assertEquals(expected, encoded)
    }

    @Test
    fun `JSON array with text part deserializes to Multimodal`() {
        val jsonStr = """[{"type":"text","text":"hello"}]"""
        val decoded = json.decodeFromString<ChatContent>(jsonStr)
        assertTrue(decoded is ChatContent.Multimodal)
        val multimodal = decoded as ChatContent.Multimodal
        assertEquals(1, multimodal.parts.size)
        val part = multimodal.parts[0]
        assertTrue(part is ContentPart.TextPart)
        assertEquals("hello", (part as ContentPart.TextPart).text)
    }

    @Test
    fun `JSON array with image_url part deserializes to Multimodal`() {
        val jsonStr = """[{"type":"image_url","image_url":{"url":"http://example.com/img.jpg"}}]"""
        val decoded = json.decodeFromString<ChatContent>(jsonStr)
        assertTrue(decoded is ChatContent.Multimodal)
        val multimodal = decoded as ChatContent.Multimodal
        assertEquals(1, multimodal.parts.size)
        val part = multimodal.parts[0]
        assertTrue(part is ContentPart.ImageUrlPart)
        assertEquals("http://example.com/img.jpg", (part as ContentPart.ImageUrlPart).imageUrl.url)
    }

    @Test
    fun `Multimodal round-trips through encode and decode`() {
        val original: ChatContent = ChatContent.Multimodal(
            listOf(
                ContentPart.TextPart("what is in this image?"),
                ContentPart.ImageUrlPart(ImageUrl("data:image/png;base64,xyz")),
            )
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatContent>(encoded)
        assertEquals(original, decoded)
    }

    // ─── ChatRequestMessage integration ────────────────────────────────────

    @Test
    fun `ChatRequestMessage with Text content serializes content as bare string`() {
        // Import net models
        val message = com.ai.phoneagent.net.ChatRequestMessage(
            role = "user",
            content = ChatContent.Text("hello")
        )
        val encoded = json.encodeToString(message)
        // content field must be a bare string, not {"text":"hello"}
        assertTrue("Expected bare string content, got: $encoded",
            encoded.contains("\"content\":\"hello\""))
    }

    @Test
    fun `ChatRequestMessage with Multimodal content serializes content as array`() {
        val message = com.ai.phoneagent.net.ChatRequestMessage(
            role = "user",
            content = ChatContent.Multimodal(listOf(ContentPart.TextPart("hi")))
        )
        val encoded = json.encodeToString(message)
        assertTrue("Expected array content, got: $encoded",
            encoded.contains("\"content\":["))
    }
}
