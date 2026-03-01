package com.ai.phoneagent.helper

import com.ai.phoneagent.data.AttachmentRef

/**
 * 附件解析器
 * 
 * 用于解析和处理消息中的附件引用标签
 * 格式：<attachment id="path" filename="name" type="mime" size="123" content="text"/>
 */
object AttachmentParser {
    
    private val ATTACHMENT_PATTERN = """<attachment\s+([^>]+?)/>""".toRegex()
    private val ATTR_PATTERN = """(\w+)="([^"]*)"""".toRegex()
    
    /**
     * 检查文本中是否包含附件引用
     */
    fun hasAttachmentRefs(text: String): Boolean {
        return ATTACHMENT_PATTERN.containsMatchIn(text)
    }
    
    /**
     * 从文本中提取所有附件引用
     */
    fun extractAttachmentRefs(text: String): List<AttachmentRef> {
        val refs = mutableListOf<AttachmentRef>()
        
        ATTACHMENT_PATTERN.findAll(text).forEach { match ->
            val attrsText = match.groupValues[1]
            val attrs = parseAttributes(attrsText)
            
            refs.add(
                AttachmentRef(
                    id = attrs["id"] ?: "",
                    filename = attrs["filename"] ?: "",
                    type = attrs["type"] ?: "",
                    size = attrs["size"]?.toLongOrNull() ?: 0,
                    content = attrs["content"]
                )
            )
        }
        
        return refs
    }
    
    /**
     * 从文本中移除附件引用标签
     */
    fun removeAttachmentRefs(text: String): String {
        return ATTACHMENT_PATTERN.replace(text, "").trim()
    }
    
    /**
     * 解析属性字符串
     */
    private fun parseAttributes(attrsText: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        
        ATTR_PATTERN.findAll(attrsText).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            attrs[key] = value
        }
        
        return attrs
    }
    
    /**
     * 创建附件引用标签
     */
    fun createAttachmentRef(
        id: String,
        filename: String,
        type: String,
        size: Long = 0,
        content: String? = null
    ): String {
        val sb = StringBuilder("<attachment ")
        sb.append("id=\"$id\" ")
        sb.append("filename=\"$filename\" ")
        sb.append("type=\"$type\" ")
        
        if (size > 0) {
            sb.append("size=\"$size\" ")
        }
        
        if (!content.isNullOrEmpty()) {
            // XML转义
            val escapedContent = content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
            sb.append("content=\"$escapedContent\" ")
        }
        
        sb.append("/>")
        return sb.toString()
    }
}
