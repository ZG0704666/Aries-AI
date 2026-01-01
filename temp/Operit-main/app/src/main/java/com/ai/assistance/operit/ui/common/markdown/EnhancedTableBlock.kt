package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

private const val TAG = "TableBlock"

/**
 * 增强型表格组件
 *
 * 具有以下功能:
 * 1. 智能解析表格内容和结构
 * 2. 表头样式
 * 3. 边框
 * 4. 内容对齐
 * 5. 行渲染保护（使用key机制避免重复渲染）
 * 6. 水平滚动支持（处理长内容）
 * 7. 纵列对齐（保证列宽一致）
 * 8. 完整显示内容（不截断/省略）
 */
@Composable
fun EnhancedTableBlock(
    tableContent: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // 使用remember创建组件唯一ID
    val componentId = remember { "table-${System.identityHashCode(tableContent)}" }
    AppLogger.d(TAG, "表格组件初始化: id=$componentId, 内容长度=${tableContent.length}")
    
    // 表格颜色
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    // 水平滚动状态
    val horizontalScrollState = rememberScrollState()
    
    // 解析表格内容
    val tableData by remember(tableContent) {
        derivedStateOf {
            parseTable(tableContent)
        }
    }
    
    // 渲染表格
    if (tableData.rows.isEmpty()) {
        return
    }
    
    // 计算每列的最大宽度
    val columnWidths = remember(tableData) {
        if (tableData.rows.isEmpty()) {
            emptyList()
        } else {
            // 确定列数
            val columnCount = tableData.rows.maxOf { it.size }
            
            // 初始化列宽数组
            val widths = MutableList(columnCount) { 0 }
            
            // 计算每列的最大宽度
            tableData.rows.forEach { row ->
                row.forEachIndexed { colIndex, cell ->
                    // 使用更准确的宽度计算 - 考虑字体大小和中文字符
                    val cellMinWidth = calculateTextWidth(cell)
                    widths[colIndex] = maxOf(widths[colIndex], cellMinWidth)
                }
            }
            
            widths
        }
    }
    
    val tableBlockDesc = stringResource(R.string.table_block)
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = tableBlockDesc },
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            // 使用horizontalScroll包装表格内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                    // 使用表格ID作为key
                    tableData.rows.forEachIndexed { rowIndex, row ->
                        val isHeader = rowIndex == 0 && tableData.hasHeader
                        
                        // 使用行索引和内容哈希作为复合key
                        val rowKey = "$componentId-row-$rowIndex-${row.joinToString("").hashCode()}"
                        
                        androidx.compose.runtime.key(rowKey) {
                            Row(
                                modifier = Modifier
                                    .then(
                                        if (isHeader) Modifier.background(headerBackground) else Modifier
                                    )
                            ) {
                                row.forEachIndexed { colIndex, cell ->
                                    // 使用预计算的列宽
                                    val columnWidth = if (colIndex < columnWidths.size) {
                                        columnWidths[colIndex]
                                    } else {
                                        80 // 默认宽度
                                    }
                                    
                                    // 表格单元格
                                    Box(
                                        modifier = Modifier
                                            .width(columnWidth.dp)
                                            .border(width = 0.5.dp, color = borderColor)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cell,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            // 不限制行数，也不使用省略号，确保文本完整显示
                                            // 但设置为单行，防止文本换行
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计算文本显示所需的宽度（单位：dp）
 * 考虑了不同字符的宽度差异
 */
private fun calculateTextWidth(text: String): Int {
    // 空字符串返回最小宽度
    if (text.isEmpty()) return 80
    
    var width = 0
    
    // 遍历每个字符，根据字符类型估算宽度
    text.forEach { char ->
        width += when {
            // 中文、日文、韩文等宽字符
            char.isIdeographic() -> 16
            // 数字、英文字母
            char.isLetterOrDigit() -> 8
            // 标点符号等
            else -> 6
        }
    }
    
    // 添加一定的边距，确保文本不会太靠近边缘
    width += 16
    
    // 确保最小宽度
    return width.coerceAtLeast(80)
}

/**
 * 判断字符是否为表意文字（如中文、日文、韩文等）
 */
private fun Char.isIdeographic(): Boolean {
    val code = this.code
    return (code in 0x4E00..0x9FFF) || // CJK统一表意文字
           (code in 0x3040..0x309F) || // 平假名
           (code in 0x30A0..0x30FF) || // 片假名
           (code in 0xAC00..0xD7A3)    // 韩文音节
}

/**
 * 表格数据结构
 */
private data class TableData(
    val rows: List<List<String>>,
    val hasHeader: Boolean
)

/**
 * 解析Markdown表格内容
 */
private fun parseTable(content: String): TableData {
    // 解析表格行
    val lines = content.lines().filter { it.trim().isNotEmpty() && it.contains('|') }
    
    if (lines.isEmpty()) {
        return TableData(emptyList(), false)
    }
    
    // 解析行
    val rows = mutableListOf<List<String>>()
    var maxColumns = 0
    
    lines.forEachIndexed { index, line ->
        // 跳过分隔行 (形如 |---|---|---|)
        if (index == 1 && line.trim().matches(Regex("\\|[-:\\s|]+\\|"))) {
            return@forEachIndexed
        }
        
        // 处理单元格
        val cells = line.split('|')
            .drop(1) // 删除第一个空元素
            .dropLast(1) // 删除最后一个空元素
            .map { it.trim() }
            .toMutableList()
        
        // 更新最大列数
        maxColumns = maxOf(maxColumns, cells.size)
        
        // 确保每行有相同数量的列
        while (cells.size < maxColumns) {
            cells.add("")
        }
        
        rows.add(cells)
    }
    
    // 检查是否有表头分隔行
    val hasHeader = lines.size > 1 && 
            lines[1].trim().matches(Regex("\\|[-:\\s|]+\\|"))
    
    return TableData(rows, hasHeader)
} 