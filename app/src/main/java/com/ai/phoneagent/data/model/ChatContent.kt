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
 * OpenAI 兼容对话消息中 `content` 字段的类型化表示。
 *
 * 接口定义了两种形态：
 *  - **Text**：纯 JSON 字符串 → `"hello world"`
 *  - **Multimodal**：内容分段组成的 JSON 数组 → `[{"type":"text","text":"..."},...]`
 *
 * 读写双向均由自定义序列化器 [ChatContentSerializer] 处理。
 */
@Serializable(with = ChatContentSerializer::class)
sealed class ChatContent {

    /** 纯文本消息，序列化为 JSON 字符串。 */
    @Serializable
    data class Text(val text: String) : ChatContent()

    /** 由一个或多个内容分段组成的多模态消息，序列化为 JSON 数组。 */
    @Serializable
    data class Multimodal(val parts: List<ContentPart>) : ChatContent()
}

/**
 * [ChatContent] 的自定义 [KSerializer]。
 *
 * 序列化约定：
 * - [ChatContent.Text]       → JSON 字符串（如 `"hello"`）
 * - [ChatContent.Multimodal] → [ContentPart] 对象组成的 JSON 数组
 *
 * 反序列化约定：
 * - JSON 字符串 → [ChatContent.Text]
 * - JSON 数组   → [ChatContent.Multimodal]
 */
object ChatContentSerializer : KSerializer<ChatContent> {

    // buildClassSerialDescriptor 为公开 API（无需 @InternalSerializationApi）
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ChatContent")

    /** 按内容形态编码为 JSON 字符串或数组。 */
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

    /** 按 JSON 元素类型解码为文本或多模态内容。 */
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
