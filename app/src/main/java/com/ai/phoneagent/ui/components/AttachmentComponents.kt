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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Music2
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.ai.phoneagent.core.designsystem.theme.glass.AriesGlassTier
import com.ai.phoneagent.core.designsystem.theme.glass.ariesLiquidGlass
import com.ai.phoneagent.core.designsystem.theme.glass.ariesLiquidInteractive
import com.ai.phoneagent.core.designsystem.theme.glass.rememberAriesPressState
import com.ai.phoneagent.R
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
 * 附件选择器面板，统一玻璃外框并支持拖动关闭。
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
    // 组合期解析资源，Configuration 变化随重组刷新（LocalContextGetResourceValueCall）
    val cameraFailedText = stringResource(R.string.ui_v2_camera_failed)

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
                android.widget.Toast.makeText(context, cameraFailedText, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 附件选项列表（相机/相册/文件）
    val attachmentOptions = listOf(
        AttachmentOption(
            icon = Lucide.Camera,
            label = stringResource(R.string.ui_v2_attachment_camera),
            onClick = onCameraClick
        ),
        AttachmentOption(
            icon = Lucide.Image,
            label = stringResource(R.string.ui_v2_attachment_gallery),
            onClick = launchImagePicker
        ),
        AttachmentOption(
            icon = Lucide.FileText,
            label = stringResource(R.string.ui_v2_attachment_file),
            onClick = { filePickerLauncher.launch("*/*") }
        )
    )
    
    // 玻璃绘制放在随面板一起移动的内容中，避免外层偏移造成背景遮挡。
    if (visible) {
        val sheetRadius = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_radius)
        val sheetShape = RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = sheetShape,
            dragHandle = null,
            scrimColor = BottomSheetDefaults.ScrimColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ariesLiquidGlass(
                        shape = sheetShape,
                        tier = AriesGlassTier.Card,
                        backdrop = com.ai.phoneagent.core.designsystem.theme.glass.LocalAriesGlassBackdrop.current
                    )
                    .padding(bottom = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding))
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                // 附件选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    attachmentOptions.forEach { option ->
                        AttachmentOptionItem(
                            icon = option.icon,
                            label = option.label,
                            onClick = option.onClick,
                            modifier = Modifier.weight(1f),
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
    val titleColor = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_attachment_preview_title)
    
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.ui_v2_attachment_count, attachments.size),
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
    val previewShape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_action_radius))
    val previewIconColor = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_attachment_preview_icon)
    val previewNameColor = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_attachment_preview_name)
    val previewSizeColor = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_attachment_preview_size)
    val icon = when {
        attachment.fileName.startsWith("camera_") -> Lucide.Camera
        attachment.mimeType.startsWith("image/") -> Lucide.Image
        attachment.filePath.startsWith("screen_") -> Lucide.Monitor
        attachment.mimeType.startsWith("audio/") -> Lucide.Music2
        attachment.mimeType.startsWith("video/") -> Lucide.Video
        else -> Lucide.FileText
    }
    
    Box(
        modifier = Modifier
            .ariesLiquidInteractive(
                shape = previewShape,
                press = rememberAriesPressState(),
                tier = AriesGlassTier.Pill,
            )
            .clip(previewShape)
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
                    imageVector = Lucide.X,
                    contentDescription = stringResource(R.string.ui_v2_attachment_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 附件选项项（图标 + 标签）
 */
@Composable
private fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val optionIconColor = MaterialTheme.colorScheme.primary
    val optionTextColor = MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = {
                // 点击时触发30ms震动反馈
                performVibration(context, 30)
                onClick()
            })
            .padding(vertical = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm))
    ) {
        // 图标背景
        Box(
            modifier = Modifier
                .size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_attachment_tile))
                .ariesGlass(RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_card_radius))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = optionIconColor,
                modifier = Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_attachment_icon))
            )
        }
        
        Spacer(modifier = Modifier.height(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_home_icon_gap)))
        
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = optionTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
