package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageSupport

/**
 * 代码补全弹出组件
 */
@Composable
fun CompletionPopup(
    completionItems: List<CompletionItem>,
    onItemSelected: (CompletionItem) -> Unit,
    onDismissRequest: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier
) {
    if (completionItems.isEmpty()) return
    
    val listState = rememberLazyListState()
    
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset.copy(x = -16), // 使用.copy确保在现有y偏移基础上修改
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = false, // 设置为false，避免抢占焦点
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false // 允许在父布局边界外绘制，防止截断
        )
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .width(240.dp) // 减小宽度
                .heightIn(max = 180.dp) // 减小最大高度
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp) // 减小顶部和底部的内边距
            ) {
                items(completionItems) { item ->
                    CompletionItemRow(item, onItemSelected)
                }
            }
        }
    }
}

/**
 * 补全项行组件
 */
@Composable
fun CompletionItemRow(
    item: CompletionItem,
    onItemSelected: (CompletionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemSelected(item) }
            .padding(horizontal = 12.dp, vertical = 4.dp), // 减小垂直和水平padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = when (item.kind) {
                CompletionItemKind.KEYWORD -> Icons.Default.Code
                CompletionItemKind.FUNCTION -> Icons.Default.Functions
                CompletionItemKind.METHOD -> Icons.Default.PlayArrow
                CompletionItemKind.VARIABLE -> Icons.Default.DataObject
                CompletionItemKind.CLASS -> Icons.Default.Class
                CompletionItemKind.PROPERTY -> Icons.Default.Label
                CompletionItemKind.SNIPPET -> Icons.Default.ContentCopy
                else -> Icons.Default.TextFields
            },
            contentDescription = null,
            tint = when (item.kind) {
                CompletionItemKind.KEYWORD -> Color(LanguageSupport.KEYWORD_COLOR)
                CompletionItemKind.FUNCTION, CompletionItemKind.METHOD -> Color(LanguageSupport.FUNCTION_COLOR)
                CompletionItemKind.VARIABLE -> Color(LanguageSupport.VARIABLE_COLOR)
                CompletionItemKind.CLASS -> Color(LanguageSupport.TYPE_COLOR)
                CompletionItemKind.PROPERTY -> Color(LanguageSupport.VARIABLE_COLOR)
                else -> LocalContentColor.current
            },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp)) // 减小间隔
        
        // 标签
        Column {
            Text(
                text = item.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            // 详情信息
            if (item.detail != null) {
                Text(
                    text = item.detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}