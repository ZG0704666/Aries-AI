package com.ai.phoneagent.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.helper.AttachmentManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 附件选择器面板 - 千问风格底部弹出
 * 支持拖动关闭、背景变暗（包括状态栏区域）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSelectorPanel(
    visible: Boolean,
    attachmentManager: AttachmentManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun processPickedUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    attachmentManager.handleAttachment(uri.toString())
                }
            }
        }
    }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }

    val systemPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    attachmentManager.handleAttachment(uri.toString())
                }
                onDismiss()
            }
        }
    }

    val launchImagePicker: () -> Unit = {
        onDismiss()
        coroutineScope.launch {
            // 让底部面板先完成收起，避免与系统选择器过渡时颜色不一致
            delay(120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                imagePickerLauncher.launch("image/*")
            }
        }
    }
    
    // 相机拍照
    val onCameraClick: () -> Unit = {
        val activity = context as? com.ai.phoneagent.MainActivity
        activity?.let { act ->
            try {
                val method = act.javaClass.getDeclaredMethod("launchCamera")
                method.isAccessible = true
                method.invoke(act)
                onDismiss()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "启动相机失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 附件选项列表 - 千问风格（移除音频，保留相机/相册/文件）
    val attachmentOptions = listOf(
        AttachmentOption(
            icon = Icons.Default.PhotoCamera,
            label = "拍照",
            onClick = onCameraClick
        ),
        AttachmentOption(
            icon = Icons.Default.Image,
            label = "相册",
            onClick = launchImagePicker
        ),
        AttachmentOption(
            icon = Icons.Default.Description,
            label = "文件",
            onClick = { filePickerLauncher.launch("*/*") }
        )
    )
    
    // 使用 ModalBottomSheet 实现可拖动关闭 + 背景变暗（包括状态栏）
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                // 千问风格拖动手柄
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }
            },
            scrimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), // 背景变暗效果（包括状态栏）
            windowInsets = WindowInsets(0, 0, 0, 0) // 确保遮罩覆盖整个屏幕包括状态栏
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                // 附件选项 - 千问风格布局
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    attachmentOptions.forEach { option ->
                        AttachmentOptionItem(
                            icon = option.icon,
                            label = option.label,
                            onClick = option.onClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * 附件预览列表
 * 横向滚动显示所有附件
 */
@Composable
fun AttachmentPreviewList(
    attachments: List<AttachmentInfo>,
    attachmentManager: AttachmentManager,
    onInsertReference: (AttachmentInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    val titleColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_title)
    
    Column(modifier = modifier) {
        Text(
            text = "附件 (${attachments.size})",
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attachments) { attachment ->
                AttachmentPreviewItem(
                    attachment = attachment,
                    attachmentManager = attachmentManager,
                    onRemove = { attachmentManager.removeAttachment(attachment.filePath) },
                    onInsert = { onInsertReference(attachment) }
                )
            }
        }
    }
}

/**
 * 单个附件预览项
 */
@Composable
private fun AttachmentPreviewItem(
    attachment: AttachmentInfo,
    attachmentManager: AttachmentManager,
    onRemove: () -> Unit,
    onInsert: () -> Unit
) {
    val previewCardBg = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_card_bg)
    val previewCardStroke = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_card_stroke)
    val previewIconColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_icon)
    val previewNameColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_name)
    val previewSizeColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_preview_size)
    val icon = when {
        attachment.fileName.startsWith("camera_") -> Icons.Default.PhotoCamera
        attachment.mimeType.startsWith("image/") -> Icons.Default.Image
        attachment.filePath.startsWith("screen_") -> Icons.Default.ScreenshotMonitor
        attachment.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        attachment.mimeType.startsWith("video/") -> Icons.Default.VideoLibrary
        else -> Icons.Default.Description
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = previewCardStroke,
                shape = RoundedCornerShape(8.dp)
            )
            .background(previewCardBg)
            .clickable(onClick = onInsert)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = previewIconColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 文件信息
            Column {
                Text(
                    text = attachmentManager.getDisplayName(attachment),
                    style = MaterialTheme.typography.bodySmall,
                    color = previewNameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (attachment.fileSize > 0) {
                    Text(
                        text = attachmentManager.formatFileSize(attachment.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = previewSizeColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 删除按钮
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除附件",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 附件选项项 - 千问简约风格
 */
@Composable
private fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val optionBgColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_option_bg)
    val optionIconColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_option_icon)
    val optionTextColor = colorResource(id = com.ai.phoneagent.R.color.m3t_attachment_option_text)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = {
                // 点击时触发80ms震动反馈
                performVibration(context, 80)
                onClick()
            })
            .width(90.dp)
            .padding(vertical = 8.dp)
    ) {
        // 图标背景 - 千问风格圆角
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(optionBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = optionIconColor,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 标签 - 千问风格字体
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = optionTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp
        )
    }
}

/**
 * 执行震动反馈
 * @param context 上下文
 * @param durationMs 震动时长（毫秒）
 */
private fun performVibration(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(context, VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(context, Vibrator::class.java)
        }
        
        vibrator?.let {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            } catch (_: Throwable) {
                // 忽略震动失败
            }
        }
    } catch (_: Throwable) {
        // 忽略震动失败
    }
}

/**
 * 附件选项数据类
 */
private data class AttachmentOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * 获取临时文件URI（用于相机拍照）
 */
private fun getTempFileUri(context: Context): Uri {
    val authority = "${context.applicationContext.packageName}.fileprovider"
    val tmpFile = File.createTempFile("temp_image_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, authority, tmpFile)
}
