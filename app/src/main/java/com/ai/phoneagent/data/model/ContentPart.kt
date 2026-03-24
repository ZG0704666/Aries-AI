package com.ai.phoneagent.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Represents a single part of a multimodal content message.
 * OpenAI API format:
 *   - text:      {"type":"text","text":"..."}
 *   - image_url: {"type":"image_url","image_url":{"url":"..."}}
 */
@Serializable(with = ContentPartSerializer::class)
sealed class ContentPart {

    @Serializable
    data class TextPart(
        @SerialName("text") val text: String,
    ) : ContentPart()

    @Serializable
    data class ImageUrlPart(
        @SerialName("image_url") val imageUrl: ImageUrl,
    ) : ContentPart()
}

@Serializable
data class ImageUrl(
    @SerialName("url") val url: String,
)

/**
 * Custom serializer for [ContentPart] that writes/reads the OpenAI "type" discriminator.
 */
object ContentPartSerializer : KSerializer<ContentPart> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ContentPart")

    override fun serialize(encoder: Encoder, value: ContentPart) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ContentPartSerializer only supports JSON encoding")

        val jsonObj: JsonObject = when (value) {
            is ContentPart.TextPart -> buildJsonObject {
                put("type", "text")
                put("text", value.text)
            }
            is ContentPart.ImageUrlPart -> buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", value.imageUrl.url)
                })
            }
        }
        jsonEncoder.encodeJsonElement(jsonObj)
    }

    override fun deserialize(decoder: Decoder): ContentPart {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ContentPartSerializer only supports JSON decoding")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val type = element["type"]?.jsonPrimitive?.content
            ?: error("ContentPart missing 'type' field")

        return when (type) {
            "text" -> {
                val text = element["text"]?.jsonPrimitive?.content
                    ?: error("ContentPart.text missing 'text' field")
                ContentPart.TextPart(text)
            }
            "image_url" -> {
                val imageUrlObj = element["image_url"]?.jsonObject
                    ?: error("ContentPart.image_url missing 'image_url' field")
                val url = imageUrlObj["url"]?.jsonPrimitive?.content
                    ?: error("image_url missing 'url' field")
                ContentPart.ImageUrlPart(ImageUrl(url))
            }
            else -> error("Unknown ContentPart type: $type")
        }
    }
}
