package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** 工具执行结果显示组件 简洁风格，显示工具执行结果，无边框，与CompactToolDisplay风格一致 通过缩进和特殊图标区分工具调用和执行结果 支持点击查看详细内容 */
@Composable
fun ToolResultDisplay(
        toolName: String,
        result: String,
        isSuccess: Boolean = true,
        onCopyResult: () -> Unit = {},
        enableDialog: Boolean = true  // 新增参数：是否启用弹窗功能，默认启用
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hasContent = result.isNotBlank()

    // 弹窗状态
    var showDetailDialog by remember { mutableStateOf(false) }

    // 显示详细内容的弹窗 - 仅在启用弹窗时显示
    if (showDetailDialog && enableDialog) {
        ToolResultDetailDialog(
                toolName = toolName,
                result = result,
                isSuccess = isSuccess,
                onDismiss = { showDetailDialog = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(result))
                    onCopyResult()
                }
        )
    }

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = hasContent && enableDialog) {
                                // 仅在启用弹窗时才允许点击打开详情
                                if (hasContent && enableDialog) showDetailDialog = true
                            }
                            .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 子目录箭头图标，表示这是上个工具的执行结果
        Icon(
                imageVector = Icons.Default.SubdirectoryArrowRight,
                contentDescription = context.getString(R.string.tool_execution_result),
                tint =
                        if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 状态图标
        Icon(
                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (isSuccess) context.getString(R.string.success) else context.getString(R.string.failed),
                tint =
                        if (isSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 结果内容（确保在一行显示）
        Text(
                text = if (hasContent) result.take(200) else if (isSuccess) context.getString(R.string.execution_success) else context.getString(R.string.execution_failed),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (!hasContent) FontWeight.Medium else FontWeight.Normal,
                color =
                        if (isSuccess) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
        )

        // 复制按钮（仅在有内容时显示）
        if (hasContent) {
            IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(result))
                        onCopyResult()
                    },
                    modifier = Modifier.size(24.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = context.getString(R.string.copy_result),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** 工具结果详情弹窗 美观的弹窗显示完整的工具执行结果 */
@Composable
private fun ToolResultDetailDialog(
        toolName: String,
        result: String,
        isSuccess: Boolean,
        onDismiss: () -> Unit,
        onCopy: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题栏
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态图标
                    Icon(
                            imageVector =
                                    if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isSuccess) context.getString(R.string.success) else context.getString(R.string.failed),
                            tint =
                                    if (isSuccess) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 工具名称
                    Text(
                            text = "$toolName ${if (isSuccess) context.getString(R.string.execution_success) else context.getString(R.string.execution_failed)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 复制按钮
                    IconButton(onClick = onCopy) {
                        Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = context.getString(R.string.copy_result),
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分隔线
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // 结果内容
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 50.dp, max = 300.dp)
                                        .verticalScroll(rememberScrollState())
                                        .background(
                                                color =
                                                        if (isSuccess)
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer.copy(
                                                                        alpha = 0.2f
                                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                ) {
                    Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) { Text(context.getString(R.string.close)) }
            }
        }
    }
}
