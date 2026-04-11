package com.ai.phoneagent.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import kotlin.math.max

/**
 * GFM Table 渲染 – 从 AST 节点提取表头/数据行并渲染为 DataTable
 */
@Composable
internal fun MarkdownTableNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    if (columnCount == 0) return

    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { content.substring(it.startOffset, it.endOffset).trim() }
        ?: emptyList()

    val rows = rowNodes.map { rowNode ->
        rowNode.children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { content.substring(it.startOffset, it.endOffset).trim() }
    }

    val headers = List(columnCount) { col ->
        @Composable {
            MarkdownBlock(content = if (col < headerCells.size) headerCells[col] else "")
        }
    }

    val rowComposables = rows.map { rowData ->
        List(columnCount) { col ->
            @Composable {
                MarkdownBlock(content = if (col < rowData.size) rowData[col] else "")
            }
        }
    }

    AriesDataTable(
        headers = headers,
        rows = rowComposables,
        modifier = modifier.padding(vertical = 8.dp),
        columnMinWidths = List(columnCount) { 80.dp },
        columnMaxWidths = List(columnCount) { 200.dp },
    )
}

/**
 * DataTable – SubcomposeLayout 实现自适应列宽 + 行内等高
 *
 * 源自 RikkaHub 的 DataTable 组件，适配到 Aries AI 项目。
 */
@Composable
private fun AriesDataTable(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 4.dp,
    cellBorder: BorderStroke? = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    headerBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    cellAlignment: Alignment = Alignment.CenterStart,
) {
    val hScroll = rememberScrollState()

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small)
            .horizontalScroll(hScroll)
    ) {
        SubcomposeLayout { constraints ->
            val columnCount = max(headers.size, rows.maxOfOrNull { it.size } ?: 0)
            val rowCount = rows.size
            if (columnCount == 0) return@SubcomposeLayout layout(0, 0) {}

            val infinity = Constraints.Infinity
            val unbounded = Constraints(0, infinity, 0, infinity)
            val minWidthsPx = IntArray(columnCount) { i -> columnMinWidths.getOrNull(i)?.roundToPx() ?: 0 }
            val maxWidthsPx = IntArray(columnCount) { i -> columnMaxWidths.getOrNull(i)?.roundToPx() ?: Int.MAX_VALUE }
            val colWidths = IntArray(columnCount) { 0 }

            // Phase 1: Natural-size measurement
            fun subcomposeHeaderOnce(c: Int): Placeable {
                val measurables = subcompose("h1_$c") {
                    CellBox(cellPadding, cellBorder, headerBackground, cellAlignment) {
                        headers.getOrNull(c)?.invoke()
                    }
                }
                val cst = if (maxWidthsPx[c] != Int.MAX_VALUE) {
                    Constraints(0, maxWidthsPx[c], 0, infinity)
                } else unbounded
                val p = measurables.first().measure(cst)
                colWidths[c] = max(colWidths[c], max(p.width, minWidthsPx[c])).coerceAtMost(maxWidthsPx[c])
                return p
            }

            fun subcomposeBodyOnce(r: Int, c: Int): Placeable {
                val measurables = subcompose("b1_${r}_$c") {
                    CellBox(cellPadding, cellBorder, Color.Transparent, cellAlignment) {
                        rows[r].getOrNull(c)?.invoke()
                    }
                }
                val cst = if (maxWidthsPx[c] != Int.MAX_VALUE) {
                    Constraints(0, maxWidthsPx[c], 0, infinity)
                } else unbounded
                val p = measurables.first().measure(cst)
                colWidths[c] = max(colWidths[c], max(p.width, minWidthsPx[c])).coerceAtMost(maxWidthsPx[c])
                return p
            }

            val headerP1 = arrayOfNulls<Placeable>(columnCount)
            val bodyP1 = arrayOfNulls<Placeable>(rowCount * columnCount)
            for (c in 0 until columnCount) headerP1[c] = subcomposeHeaderOnce(c)
            for (r in 0 until rowCount) for (c in 0 until columnCount) bodyP1[r * columnCount + c] = subcomposeBodyOnce(r, c)

            val rowHeights = IntArray(rowCount) { r ->
                var h = 0
                for (c in 0 until columnCount) h = max(h, bodyP1[r * columnCount + c]!!.height)
                h
            }
            val headerHeight = headerP1.maxOf { it?.height ?: 0 }

            // Phase 2: Fixed-width re-measure
            fun constraintsFor(colWidth: Int, minH: Int): Constraints {
                return Constraints(
                    minWidth = colWidth.coerceAtLeast(0),
                    maxWidth = colWidth.coerceAtLeast(0),
                    minHeight = minH.coerceAtLeast(0),
                    maxHeight = infinity,
                )
            }

            val headerPlaceables = Array(columnCount) { c ->
                subcompose("h2_$c") {
                    CellBox(cellPadding, cellBorder, headerBackground, cellAlignment) {
                        headers.getOrNull(c)?.invoke()
                    }
                }.first().measure(constraintsFor(colWidths[c], headerHeight))
            }

            val bodyPlaceables = Array(rowCount * columnCount) { i ->
                val r = i / columnCount
                val c = i % columnCount
                subcompose("b2_${r}_$c") {
                    CellBox(cellPadding, cellBorder, Color.Transparent, cellAlignment) {
                        rows[r].getOrNull(c)?.invoke()
                    }
                }.first().measure(constraintsFor(colWidths[c], rowHeights[r]))
            }

            val tableWidth = colWidths.sum()
            val tableHeight = headerHeight + rowHeights.sum()
            val finalWidth = tableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val finalHeight = tableHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(finalWidth, finalHeight) {
                var x = 0
                for (c in 0 until columnCount) {
                    headerPlaceables[c].placeRelative(x, 0)
                    x += colWidths[c]
                }
                var y = headerHeight
                for (r in 0 until rowCount) {
                    x = 0
                    for (c in 0 until columnCount) {
                        bodyPlaceables[r * columnCount + c].placeRelative(x, y)
                        x += colWidths[c]
                    }
                    y += rowHeights[r]
                }
            }
        }
    }
}

@Composable
private fun CellBox(
    padding: Dp,
    border: BorderStroke?,
    background: Color,
    alignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(if (background != Color.Transparent) Modifier.background(background) else Modifier)
            .then(if (border != null) Modifier.border(border) else Modifier)
            .padding(padding),
        contentAlignment = alignment,
    ) {
        content()
    }
}
