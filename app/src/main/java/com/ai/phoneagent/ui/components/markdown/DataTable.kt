package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun DataTable(
    node: ASTNode,
    source: String,
    modifier: Modifier = Modifier,
) {
    val (header, rows) = remember(node, source) { parseGfmTable(node) }
    val colCount = maxOf(header.size, rows.maxOfOrNull { it.cells.size } ?: 0)
    if (colCount == 0) return

    val headerBg     = MaterialTheme.colorScheme.primaryContainer
    val rowAltBg     = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val borderColor  = MaterialTheme.colorScheme.outline
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val tableShape   = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(tableShape)
            .border(1.dp, borderColor, tableShape),
    ) {
        if (header.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .background(headerBg)
                    .width(IntrinsicSize.Max),
            ) {
                header.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(
                        color    = borderColor.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        MarkdownInlineNodes(nodes = cell.childNodes, source = source, bold = true)
                    }
                }
                repeat((colCount - header.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = borderColor, thickness = 1.dp)
        }

        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .width(IntrinsicSize.Max)
                    .background(if (rowIdx % 2 == 1) rowAltBg else MaterialTheme.colorScheme.surface),
            ) {
                row.cells.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(
                        color    = dividerColor,
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        MarkdownInlineNodes(nodes = cell.childNodes, source = source, bold = false)
                    }
                }
                repeat((colCount - row.cells.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            if (rowIdx < rows.lastIndex) HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
        }
    }
}

private data class CellContent(val childNodes: List<ASTNode>)
private data class TableRow(val cells: List<CellContent>)

private fun parseGfmTable(tableNode: ASTNode): Pair<List<CellContent>, List<TableRow>> {
    val header = mutableListOf<CellContent>()
    val rows   = mutableListOf<TableRow>()

    tableNode.children.forEach { child ->
        when (child.type) {
            GFMElementTypes.HEADER -> {
                child.children.forEach { cell ->
                    if (cell.type == GFMTokenTypes.CELL) {
                        header += CellContent(cell.children.toList())
                    }
                }
            }
            GFMElementTypes.ROW -> {
                val cells = child.children
                    .filter { it.type == GFMTokenTypes.CELL }
                    .map { CellContent(it.children.toList()) }
                if (cells.isNotEmpty()) rows += TableRow(cells)
            }
        }
    }
    return header to rows
}

@Composable
private fun MarkdownInlineNodes(
    nodes: List<ASTNode>,
    source: String,
    bold: Boolean,
) {
    if (nodes.isEmpty()) return
    Column {
        nodes.forEach { child ->
            MarkdownNode(node = child, source = source, isBoldContext = bold)
        }
    }
}
