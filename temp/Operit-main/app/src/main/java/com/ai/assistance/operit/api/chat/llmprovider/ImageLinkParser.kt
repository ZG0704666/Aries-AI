package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ImageBitmapLimiter
import com.ai.assistance.operit.util.ImagePoolManager

/**
 * 图片链接数据类
 */
data class ImageLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String
)

/**
 * 图片链接解析工具
 */
object ImageLinkParser {
    // 匹配普通形式的图片链接: <link type="image" id="..."></link>
    private val LINK_PATTERN_PLAIN = Regex(
        """<link\s+type="image"\s+id="([^"]+)"\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )

    // 匹配被 JSON 转义后的图片链接字符串: <link type=\"image\" id=\"...\"></link>
    // 这里 \" 在正则中表示匹配反斜杠+双引号两个字符
    private val LINK_PATTERN_ESCAPED = Regex(
        """<link\s+type=\\"image\\"\s+id=\\"([^\"]+)\\"\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )
    
    /**
     * 提取消息中的所有图片链接并获取其base64数据
     * 如果图片不存在或已过期，会被静默跳过
     */
    fun extractImageLinks(message: String): List<ImageLink> {
        val imageLinks = mutableListOf<ImageLink>()
        val seenIds = mutableSetOf<String>()

        fun collectFromPattern(pattern: Regex) {
            pattern.findAll(message).forEach { match ->
                val id = match.groupValues[1]

                // 跳过error标记
                if (id == "error") {
                    return@forEach
                }

                // 去重，避免同一ID在普通形式和转义形式中被重复解析
                if (!seenIds.add(id)) {
                    return@forEach
                }

                // 从池中获取图片数据
                val imageData = ImagePoolManager.getImage(id)
                if (imageData == null) {
                    // 图片已过期，静默跳过
                    return@forEach
                }

                val limited = ImageBitmapLimiter.limitBase64ForAi(imageData.base64, imageData.mimeType)
                    ?: return@forEach

                imageLinks.add(
                    ImageLink(
                        type = "image",
                        id = id,
                        base64Data = limited.base64,
                        mimeType = limited.mimeType
                    )
                )
            }
        }

        // 先解析普通形式，再解析 JSON 转义形式
        collectFromPattern(LINK_PATTERN_PLAIN)
        collectFromPattern(LINK_PATTERN_ESCAPED)

        return imageLinks
    }
    
    /**
     * 移除消息中的所有图片链接标签
     */
    fun removeImageLinks(message: String): String {
        return message
            .replace(LINK_PATTERN_PLAIN, "")
            .replace(LINK_PATTERN_ESCAPED, "")
    }
    
    /**
     * 检查消息是否包含图片链接
     */
    fun hasImageLinks(message: String): Boolean {
        return LINK_PATTERN_PLAIN.containsMatchIn(message) || LINK_PATTERN_ESCAPED.containsMatchIn(message)
    }
}
