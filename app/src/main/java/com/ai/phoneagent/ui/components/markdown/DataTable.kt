package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

// ─────────────────────────────────────────────────────────────────────────────
//  DataTable – GFM table renderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a GFM table AST node.
 *
 * Each cell is rendered recursively via its own [MarkdownNode] invocations so
 * that bold, italic, links, and inline code inside cells all work correctly.
 *
 * Columns are evenly weighted. The table scrolls horizontally on narrow screens.
 */
@Composable
fun DataTable(
    node: ASTNode,
    source: String,
    modifier: Modifier = Modifier,
) {
    data class CellContent(val childNodes: List<ASTNode>)
    data class TableRow(val cells: List<CellContent>)

    val (header, rows) = remember(node, source) { parseGfmTable(node) }
    val colCount = maxOf(header.size, rows.maxOfOrNull { it.cells.size } ?: 0)
    if (colCount == 0) return

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        if (header.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .background(surfaceVariant, MaterialTheme.shapes.small)
                    .width(IntrinsicSize.Max),
            ) {
                header.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(
                        color    = outlineVariant,
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        MarkdownInlineNodes(nodes = cell.childNodes, source = source, bold = true)
                    }
                }
                // Pad missing columns
                repeat((colCount - header.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = outlineVariant)
        }

        // ── Data rows ─────────────────────────────────────────────────────────
        rows.forEachIndexed { rowIdx, row ->
            Row(modifier = Modifier.fillMaxWidth().width(IntrinsicSize.Max)) {
                row.cells.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(
                        color    = outlineVariant,
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        MarkdownInlineNodes(nodes = cell.childNodes, source = source, bold = false)
                    }
                }
                repeat((colCount - row.cells.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            if (rowIdx < rows.lastIndex) HorizontalDivider(color = outlineVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AST parsing helpers
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
//  Inline cell renderer – feeds cell's child nodes back into MarkdownNode
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders the child AST nodes of a table cell, reusing the same [MarkdownNode]
 * pipeline so that all inline Markdown (bold, italic, links, code…) works inside cells.
 */
@Composable
private fun MarkdownInlineNodes(
    nodes: List<ASTNode>,
    source: String,
    bold: Boolean,
) {
    // Wrap in a temporary PARAGRAPH-like virtual node by just rendering each child.
    // The [MarkdownNode] function is called per child; block-level nodes (rare inside
    // a table cell) fall through to their normal renderers.
    if (nodes.isEmpty()) return

    // Build an annotated string from the inline nodes for the cell content.
    // We call MarkdownNode in a Column so that block elements inside cells still work.
    Column {
        nodes.forEach { child ->
            MarkdownNode(node = child, source = source, isBoldContext = bold)
        }
    }
}
