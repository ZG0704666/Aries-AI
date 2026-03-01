package com.ai.phoneagent.helper

import android.util.Base64
import com.ai.phoneagent.data.MediaLink
import java.io.File

/**
 * 多媒体链接解析器
 * 
 * 用于检测、提取和处理消息中的多媒体内容
 * 格式：[IMAGE:mime_type:base64_data]
 *      [AUDIO:mime_type:base64_data]
 *      [VIDEO:mime_type:base64_data]
 */
object MediaLinkParser {
    
    private val IMAGE_PATTERN = """\[IMAGE:([^:]+):([^\]]+)\]""".toRegex()
    private val AUDIO_PATTERN = """\[AUDIO:([^:]+):([^\]]+)\]""".toRegex()
    private val VIDEO_PATTERN = """\[VIDEO:([^:]+):([^\]]+)\]""".toRegex()
    private val MEDIA_PATTERN = """\[(IMAGE|AUDIO|VIDEO):([^:]+):([^\]]+)\]""".toRegex()
    
    /**
     * 检查是否包含图片链接
     */
    fun hasImageLinks(text: String): Boolean {
        return IMAGE_PATTERN.containsMatchIn(text)
    }
    
    /**
     * 检查是否包含媒体链接（音频/视频）
     */
    fun hasMediaLinks(text: String): Boolean {
        return AUDIO_PATTERN.containsMatchIn(text) || VIDEO_PATTERN.containsMatchIn(text)
    }
    
    /**
     * 提取所有图片链接
     */
    fun extractImageLinks(text: String): List<MediaLink> {
        val links = mutableListOf<MediaLink>()
        
        IMAGE_PATTERN.findAll(text).forEach { match ->
            val mimeType = match.groupValues[1]
            val base64Data = match.groupValues[2]
            
            links.add(
                MediaLink(
                    type = "image",
                    mimeType = mimeType,
                    base64Data = base64Data
                )
            )
        }
        
        return links
    }
    
    /**
     * 提取所有媒体链接（音频/视频）
     */
    fun extractMediaLinks(text: String): List<MediaLink> {
        val links = mutableListOf<MediaLink>()
        
        MEDIA_PATTERN.findAll(text).forEach { match ->
            val type = match.groupValues[1].lowercase()
            val mimeType = match.groupValues[2]
            val base64Data = match.groupValues[3]
            
            if (type == "audio" || type == "video") {
                links.add(
                    MediaLink(
                        type = type,
                        mimeType = mimeType,
                        base64Data = base64Data
                    )
                )
            }
        }
        
        return links
    }
    
    /**
     * 移除图片链接
     */
    fun removeImageLinks(text: String): String {
        return IMAGE_PATTERN.replace(text, "").trim()
    }
    
    /**
     * 移除媒体链接
     */
    fun removeMediaLinks(text: String): String {
        return MEDIA_PATTERN.replace(text, "").trim()
    }
    
    /**
     * 从文件创建媒体链接标记
     */
    fun createMediaLink(filePath: String, mimeType: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            val type = when {
                mimeType.startsWith("image/") -> "IMAGE"
                mimeType.startsWith("audio/") -> "AUDIO"
                mimeType.startsWith("video/") -> "VIDEO"
                else -> return null
            }
            
            "[$type:$mimeType:$base64]"
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从Base64字符串解码为字节数组
     */
    fun decodeBase64(base64Data: String): ByteArray? {
        return try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
}
