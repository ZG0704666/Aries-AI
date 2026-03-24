package com.ai.phoneagent.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents the `content` field of an OpenAI-compatible chat request message.
 *
 * Two formats are defined by the API:
 *  - **Text**: a bare JSON string  →  `"hello world"`
 *  - **Multimodal**: a JSON array of content parts  →  `[{"type":"text","text":"..."},...]`
 *
 * The custom serializer [ChatContentSerializer] handles both read and write paths.
 */
@Serializable(with = ChatContentSerializer::class)
sealed class ChatContent {

    /** Plain text message. Serializes to a bare JSON string. */
    @Serializable
    data class Text(val text: String) : ChatContent()

    /** Multimodal message with one or more content parts. Serializes to a JSON array. */
    @Serializable
    data class Multimodal(val parts: List<ContentPart>) : ChatContent()
}

/**
 * Custom [KSerializer] for [ChatContent].
 *
 * Serialization contract:
 * - [ChatContent.Text]       → JSON primitive string (e.g. `"hello"`)
 * - [ChatContent.Multimodal] → JSON array of [ContentPart] objects
 *
 * Deserialization contract:
 * - JSON primitive string → [ChatContent.Text]
 * - JSON array            → [ChatContent.Multimodal]
 */
object ChatContentSerializer : KSerializer<ChatContent> {

    // buildClassSerialDescriptor is the public API (no @InternalSerializationApi needed)
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ChatContent")

    override fun serialize(encoder: Encoder, value: ChatContent) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ChatContentSerializer only supports JSON encoding")

        when (value) {
            is ChatContent.Text -> {
                jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))
            }
            is ChatContent.Multimodal -> {
                val listSerializer = ListSerializer(ContentPart.serializer())
                jsonEncoder.encodeSerializableValue(listSerializer, value.parts)
            }
        }
    }

    override fun deserialize(decoder: Decoder): ChatContent {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ChatContentSerializer only supports JSON decoding")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (!element.isString) error("ChatContent primitive must be a string, got: $element")
                ChatContent.Text(element.content)
            }
            is JsonArray -> {
                val listSerializer = ListSerializer(ContentPart.serializer())
                val parts = jsonDecoder.json.decodeFromJsonElement(listSerializer, element)
                ChatContent.Multimodal(parts)
            }
            else -> error("Unexpected ChatContent JSON element: $element")
        }
    }
}
