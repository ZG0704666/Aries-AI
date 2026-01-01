package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.serialization.json.Json

/** 获取文件图标 */
fun getFileIcon(file: FileItem): ImageVector {
    return if (file.isDirectory) {
        Icons.Default.Folder
    } else {
        when {
            file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
            file.name.endsWith(".jpg", ignoreCase = true) ||
                    file.name.endsWith(".jpeg", ignoreCase = true) ||
                    file.name.endsWith(".png", ignoreCase = true) ||
                    file.name.endsWith(".gif", ignoreCase = true) ||
                    file.name.endsWith(".bmp", ignoreCase = true) -> Icons.Default.Image
            file.name.endsWith(".mp3", ignoreCase = true) ||
                    file.name.endsWith(".wav", ignoreCase = true) ||
                    file.name.endsWith(".ogg", ignoreCase = true) -> Icons.Default.AudioFile
            file.name.endsWith(".mp4", ignoreCase = true) ||
                    file.name.endsWith(".avi", ignoreCase = true) ||
                    file.name.endsWith(".mkv", ignoreCase = true) ||
                    file.name.endsWith(".mov", ignoreCase = true) -> Icons.Default.VideoFile
            file.name.endsWith(".zip", ignoreCase = true) ||
                    file.name.endsWith(".rar", ignoreCase = true) ||
                    file.name.endsWith(".7z", ignoreCase = true) ||
                    file.name.endsWith(".tar", ignoreCase = true) -> Icons.Default.FolderZip
            file.name.endsWith(".txt", ignoreCase = true) -> Icons.Default.TextSnippet
            file.name.endsWith(".doc", ignoreCase = true) ||
                    file.name.endsWith(".docx", ignoreCase = true) -> Icons.Default.Description
            file.name.endsWith(".xls", ignoreCase = true) ||
                    file.name.endsWith(".xlsx", ignoreCase = true) -> Icons.Default.TableChart
            file.name.endsWith(".ppt", ignoreCase = true) ||
                    file.name.endsWith(".pptx", ignoreCase = true) -> Icons.Default.PictureAsPdf
            else -> Icons.Default.InsertDriveFile
        }
    }
}

/** 获取文件类型描述 */
fun getFileType(fileName: String): String {
    return when {
        fileName.endsWith(".pdf", ignoreCase = true) -> "PDF 文档"
        fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) -> "JPEG 图片"
        fileName.endsWith(".png", ignoreCase = true) -> "PNG 图片"
        fileName.endsWith(".gif", ignoreCase = true) -> "GIF 图片"
        fileName.endsWith(".bmp", ignoreCase = true) -> "BMP 图片"
        fileName.endsWith(".mp3", ignoreCase = true) -> "MP3 音频"
        fileName.endsWith(".wav", ignoreCase = true) -> "WAV 音频"
        fileName.endsWith(".ogg", ignoreCase = true) -> "OGG 音频"
        fileName.endsWith(".mp4", ignoreCase = true) -> "MP4 视频"
        fileName.endsWith(".avi", ignoreCase = true) -> "AVI 视频"
        fileName.endsWith(".mkv", ignoreCase = true) -> "MKV 视频"
        fileName.endsWith(".mov", ignoreCase = true) -> "MOV 视频"
        fileName.endsWith(".zip", ignoreCase = true) -> "ZIP 压缩文件"
        fileName.endsWith(".rar", ignoreCase = true) -> "RAR 压缩文件"
        fileName.endsWith(".7z", ignoreCase = true) -> "7Z 压缩文件"
        fileName.endsWith(".tar", ignoreCase = true) -> "TAR 压缩文件"
        fileName.endsWith(".txt", ignoreCase = true) -> "文本文档"
        fileName.endsWith(".doc", ignoreCase = true) -> "Word 文档"
        fileName.endsWith(".docx", ignoreCase = true) -> "Word 文档"
        fileName.endsWith(".xls", ignoreCase = true) -> "Excel 表格"
        fileName.endsWith(".xlsx", ignoreCase = true) -> "Excel 表格"
        fileName.endsWith(".ppt", ignoreCase = true) -> "PowerPoint 演示文稿"
        fileName.endsWith(".pptx", ignoreCase = true) -> "PowerPoint 演示文稿"
        else -> "文件"
    }
}

/** 格式化文件大小 */
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

    return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

/** 格式化日期 */
fun formatDate(dateString: String): String {
    return try {
        val simpleDateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)
        val date = simpleDateFormat.parse(dateString)
        if (date != null) {
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

/** 解析文件列表 */
fun parseFileList(result: String): List<FileItem> {
    return try {
        if (result.isBlank()) {
            AppLogger.d("FileUtils", "Empty result string")
            return emptyList()
        }

        AppLogger.d("FileUtils", "Parsing directory listing: $result")

        val directoryListing = Json.decodeFromString<DirectoryListingData>(result)
        AppLogger.d("FileUtils", "Parsed directory listing: $directoryListing")

        directoryListing.entries.map { entry ->
            FileItem(
                    name = entry.name,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    lastModified = entry.lastModified.toLongOrNull() ?: 0
            )
        }
    } catch (e: Exception) {
        AppLogger.e("FileUtils", "Error parsing file list", e)
        emptyList()
    }
}
