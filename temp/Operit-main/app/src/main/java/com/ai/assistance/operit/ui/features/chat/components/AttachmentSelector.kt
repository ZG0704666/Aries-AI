package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.services.core.AttachmentDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.FileProvider

/** 简约风格的附件选择器组件 */
@Composable
fun AttachmentSelectorPanel(
        visible: Boolean,
        onAttachImage: (String) -> Unit,
        onAttachFile: (String) -> Unit,
        onAttachScreenContent: () -> Unit,
        onAttachNotifications: () -> Unit = {},
        onAttachLocation: () -> Unit = {},
        onAttachMemory: () -> Unit = {},
        onTakePhoto: (Uri) -> Unit,
        userQuery: String = "",
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取AttachmentDelegate实例
    val attachmentManager = remember {
        AttachmentDelegate(context, AIToolHandler.getInstance(context))
    }

    // 文件/图片选择器启动器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getFilePathFromUri(context, uri)?.let { path ->
                        onAttachImage(path)
                    }
                }
                onDismiss()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getFilePathFromUri(context, uri)?.let { path ->
                        onAttachFile(path)
                    }
                }
                onDismiss()
            }
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    tempCameraUri?.let {
                        onTakePhoto(it)
                        onDismiss()
                    }
                }
            }

    fun getTmpFileUri(context: Context): Uri {
        // authority需要与AndroidManifest.xml中provider的authorities一致
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val tmpFile =
                File.createTempFile("temp_image_", ".jpg", context.cacheDir).apply {
                    createNewFile()
                    deleteOnExit()
                }
        return FileProvider.getUriForFile(context, authority, tmpFile)
    }

    // 附件选择面板 - 使用展开动画，从下方向上展开
    AnimatedVisibility(
            visible = visible,
            enter =
                    expandVertically(
                            animationSpec = tween(200),
                            expandFrom = androidx.compose.ui.Alignment.Bottom
                    ) + fadeIn(),
            exit =
                    shrinkVertically(
                            animationSpec = tween(200),
                            shrinkTowards = androidx.compose.ui.Alignment.Bottom
                    ) + fadeOut()
    ) {
        Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                // 顶部指示器
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HorizontalDivider(
                            modifier =
                                    Modifier.width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp)),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 第一行选项
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 照片选项
                    AttachmentOption(
                            icon = Icons.Default.Image,
                            label = context.getString(R.string.attachment_photo),
                            onClick = { imagePickerLauncher.launch("image/*") }
                    )

                    // 拍照选项
                    AttachmentOption(
                            icon = Icons.Default.PhotoCamera,
                            label = context.getString(R.string.attachment_camera),
                            onClick = {
                                val uri = getTmpFileUri(context)
                                tempCameraUri = uri
                                takePictureLauncher.launch(uri)
                            }
                    )

                    // 记忆选项
                    AttachmentOption(
                            icon = Icons.Default.Memory,
                            label = context.getString(R.string.attachment_memory),
                            onClick = { 
                                onAttachMemory()
                                onDismiss()
                            }
                    )

                    // 音频选项
                    AttachmentOption(
                            icon = Icons.Default.AudioFile,
                            label = context.getString(R.string.attachment_audio),
                            onClick = { imagePickerLauncher.launch("audio/*") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 第二行选项
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件选项
                    AttachmentOption(
                        icon = Icons.Default.Description,
                        label = context.getString(R.string.attachment_file),
                        onClick = { filePickerLauncher.launch("*/*") }
                    )

                    // 屏幕内容选项
                    AttachmentOption(
                            icon = Icons.Default.ScreenshotMonitor,
                            label = context.getString(R.string.attachment_screen_content),
                            onClick = {
                                onAttachScreenContent()
                                onDismiss()
                            }
                    )

                    // 当前通知选项
                    AttachmentOption(
                            icon = Icons.Default.Notifications,
                            label = context.getString(R.string.attachment_notifications),
                            onClick = {
                                onAttachNotifications()
                                onDismiss()
                            }
                    )

                    // 当前位置选项
                    AttachmentOption(
                            icon = Icons.Default.LocationOn,
                            label = context.getString(R.string.attachment_location),
                            onClick = {
                                onAttachLocation()
                                onDismiss()
                            }
                    )
                }
            }
        }
    }
}

// 添加Uri转换为文件路径的工具函数
private suspend fun getFilePathFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    // 使用ContentResolver获取真实文件路径
    val contentResolver = context.contentResolver

    // 文件URI直接返回路径
    if (uri.scheme == "file") {
        return@withContext uri.path
    }

    // 处理content URI
    if (uri.scheme == "content") {
        try {
            // 尝试通过DocumentFile获取路径
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 尝试获取_data列（真实路径）
                    val dataIndex = it.getColumnIndex("_data")
                    if (dataIndex != -1) {
                        return@withContext it.getString(dataIndex)
                    }
                }
            }

            // 如果使用_data列无法获取路径，则直接返回URI的字符串表示
            // 这样应用可以通过ContentResolver直接使用这个URI访问文件
            AppLogger.d("AttachmentSelector", "使用URI字符串: ${uri.toString()}")
            return@withContext uri.toString()
        } catch (e: Exception) {
            AppLogger.e("AttachmentSelector", "获取文件路径错误", e)
        }
    }

    return@withContext null
}

/** 简约的附件选项组件 */
@Composable
private fun AttachmentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                    Modifier.clickable(onClick = onClick)
                            .width(70.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // 图标区域 - 改为圆角方形
        Box(
                modifier =
                        Modifier.size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(
                                                alpha = 0.7f
                                        )
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标签
        Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
