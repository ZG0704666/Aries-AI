package com.ai.phoneagent.data

/**
 * 附件信息数据类
 * 
 * 用于表示聊天中的附件，支持实际文件和虚拟附件（如OCR文本、位置信息等）
 * 
 * @property filePath 文件路径或虚拟ID（如 "screen_ocr_123456"）
 * @property fileName 显示名称
 * @property mimeType MIME类型（如 "image/jpeg", "text/plain"）
 * @property fileSize 文件大小（字节），虚拟附件为内容长度
 * @property content 内联内容，用于存储OCR文本、位置JSON等不对应实际文件的数据
 */
data class AttachmentInfo(
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val content: String = ""
)

/**
 * 附件类型枚举
 */
enum class AttachmentType {
    IMAGE,           // 图片
    FILE,            // 文件
    AUDIO,           // 音频
    VIDEO,           // 视频
    SCREEN_CONTENT,  // 屏幕内容（OCR）
    LOCATION,        // 位置
    MEMORY,          // 记忆文件夹
    CAMERA           // 相机拍照
}

/**
 * 附件引用数据类
 * 用于解析消息中的附件引用标签
 */
data class AttachmentRef(
    val id: String,
    val filename: String,
    val type: String,
    val size: Long = 0,
    val content: String? = null
)

/**
 * 多媒体链接数据类
 * 用于表示内嵌的图片/音频/视频数据
 */
data class MediaLink(
    val type: String,        // "image", "audio", "video"
    val mimeType: String,    // MIME类型
    val base64Data: String   // Base64编码的数据
)
