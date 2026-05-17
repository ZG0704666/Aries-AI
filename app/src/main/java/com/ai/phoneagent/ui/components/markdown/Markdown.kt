package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.phoneagent.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

// ─────────────────────────────────────────────────────────────────────────────
//  Settings model & CompositionLocal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Controls runtime behaviour of the Markdown rendering pipeline.
 *
 * Provide this via [CompositionLocalProvider] to customise code-block and
 * LaTeX defaults for a subtree.
 */
@Immutable
data class MarkdownSettings(
    /** Auto-wrap long code lines. */
    val autoWrap: Boolean     = true,
    /** Show line numbers in code blocks. */
    val lineNumbers: Boolean  = true,
    /** Auto-collapse code blocks with more than 10 lines. */
    val autoCollapse: Boolean = false,
    /** Syntax-highlight code blocks. Disable during streaming to avoid layout churn. */
    val enableCodeHighlight: Boolean = true,
    /** Render LaTeX formulas using JLatexMath (falls back to monospace if false). */
    val enableLatex: Boolean  = true,
)

val LocalMarkdownSettings = compositionLocalOf { MarkdownSettings() }

private val markdownFlavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val markdownParser by lazy { MarkdownParser(markdownFlavour) }

private sealed class MarkdownParseResult {
    data class Ast(val source: String, val root: ASTNode) : MarkdownParseResult()
    data class Html(val html: String) : MarkdownParseResult()
}

// ─────────────────────────────────────────────────────────────────────────────
//  preProcess – normalise LaTeX delimiters before AST parsing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Regexes that locate code ranges so LaTeX replacement skips them.
 *
 * Fenced blocks  (``` … ```) may span multiple lines.
 * Inline code    (`…`) must NOT cross newlines — the original `[\s\S]*?` caused
 *                cross-row matches inside tables, corrupting the GFM parse.
 */
private val FENCED_CODE_RE = Regex("""`{3,}[\s\S]*?`{3,}""")
private val INLINE_CODE_RE = Regex("""`[^\n`]+`""")

// Applied to extracted payload (not raw source) so backtick fences don't interfere.
// Patterns: pure $$…$$, pure single-line $…$, complete \begin{env}…\end{env}.
private val PAYLOAD_IS_LATEX_RE = Regex(
    """^\s*\$\$[\s\S]*?\$\$\s*$""" +
    """|^\s*\$[^$\n]+\$\s*$""" +
    """|^\s*\\begin\{[^}]+\}[\s\S]*?\\end\{[^}]+\}\s*$"""
)

private val LATEX_INLINE_HINT_RE = Regex(
    """\\[A-Za-z]+|[A-Za-z]\s*\(|[A-Za-z0-9)\]}]\s*\^\s*\{?[-+A-Za-z0-9]+|[A-Za-z0-9)\]}]\s*[_=+\-*/]\s*[-+A-Za-z0-9\\({]"""
)

private val BARE_ASSIGNMENT_MATH_RE = Regex(
    """(?<![$`\\])\b([A-Za-z](?:\([^)]+\)|[A-Za-z])?\s*=\s*[-+\\A-Za-z0-9{}\^_*/(). ]{1,48})(?=[，,。.;；:：])"""
)

private val BARE_LATEX_ASSIGNMENT_MATH_RE = Regex(
    """(?<![$`])((?:\\[A-Za-z]+|[A-Za-z])\w*\s*=\s*(?:\\[A-Za-z]+|[-+A-Za-z0-9{}_^\s])+)(?=[，,。.;；:：])"""
)

/**
 * Before passing text to the GFM parser:
 *  1. Locate all code ranges (fenced blocks + inline code) so they are skipped.
 *  2. Outside those ranges, replace `\[…\]` with `$$…$$` and `\(…\)` with `$…$`
 *     so the rendered text contains recognisable block/inline math markers.
 */
fun preProcess(text: String): String {
    val codeRanges = buildList {
        addAll(FENCED_CODE_RE.findAll(text).map { it.range })
        addAll(INLINE_CODE_RE.findAll(text).map { it.range })
    }.sortedBy { it.first }

    fun inCodeRange(index: Int): Boolean {
        for (r in codeRanges) {
            if (index in r) return true
            if (index < r.first) break
        }
        return false
    }

    val sb = StringBuilder(text.length)
    var i  = 0
    while (i < text.length) {
        if (inCodeRange(i)) {
            sb.append(text[i++])
            continue
        }
        // \$…\$  →  $…$ when the payload looks like LaTeX/math.
        if (text.startsWith("\\$", i)) {
            val end = findEscapedDollarMathEnd(text, i + 2, ::inCodeRange)
            if (end != -1) {
                val payload = text.substring(i + 2, end)
                if (isLikelyInlineLatex(payload)) {
                    sb.append("$").append(payload).append("$")
                    i = end + 2
                    continue
                }
            }
        }
        // \[…\]  →  $$…$$
        if (text.startsWith("\\[", i)) {
            val end = text.indexOf("\\]", i + 2)
            if (end != -1 && !inCodeRange(end)) {
                sb.append("$$").append(text.substring(i + 2, end)).append("$$")
                i = end + 2
                continue
            }
        }
        // \(…\)  →  $…$
        if (text.startsWith("\\(", i)) {
            val end = text.indexOf("\\)", i + 2)
            if (end != -1 && !inCodeRange(end)) {
                sb.append("$").append(text.substring(i + 2, end)).append("$")
                i = end + 2
                continue
            }
        }
        sb.append(text[i++])
    }
    return normalizeBareInlineMath(deindentMathBlocks(sb.toString()))
}

private fun findEscapedDollarMathEnd(
    text: String,
    start: Int,
    inCodeRange: (Int) -> Boolean,
): Int {
    var searchFrom = start
    while (searchFrom < text.length) {
        val end = text.indexOf("\\$", searchFrom)
        if (end == -1) return -1
        if (!inCodeRange(end) && text.substring(start, end).none { it == '\n' }) {
            return end
        }
        searchFrom = end + 2
    }
    return -1
}

private fun isLikelyInlineLatex(payload: String): Boolean {
    val s = payload.trim()
    if (s.isEmpty() || s.length > 160) return false
    if (LATEX_INLINE_HINT_RE.containsMatchIn(s)) return true
    return Regex("""^[\\A-Za-z0-9{}_().+\-*/=<>\s]{1,80}$""").matches(s)
}

private fun normalizeBareInlineMath(text: String): String {
    val codeRanges = buildList {
        addAll(FENCED_CODE_RE.findAll(text).map { it.range })
        addAll(INLINE_CODE_RE.findAll(text).map { it.range })
    }.sortedBy { it.first }

    fun inCodeRange(index: Int): Boolean {
        for (r in codeRanges) {
            if (index in r) return true
            if (index < r.first) break
        }
        return false
    }

    val normalizedAssignments = BARE_ASSIGNMENT_MATH_RE.replace(text) { match ->
        if (inCodeRange(match.range.first) || isInsideDollarMath(text, match.range.first)) {
            match.value
        } else {
            val expression = match.groupValues[1].trim()
            if (isLikelyBareAssignmentMath(expression)) "$" + expression + "$" else match.value
        }
    }
    return BARE_LATEX_ASSIGNMENT_MATH_RE.replace(normalizedAssignments) { match ->
        if (inCodeRange(match.range.first) || isInsideDollarMath(normalizedAssignments, match.range.first)) {
            match.value
        } else {
            "$" + match.groupValues[1].trim() + "$"
        }
    }
}

private fun isInsideDollarMath(text: String, index: Int): Boolean {
    val before = text.substring(0, index).count { it == '$' }
    return before % 2 == 1
}

private fun isLikelyBareAssignmentMath(expression: String): Boolean {
    val right = expression.substringAfter('=', missingDelimiterValue = "").trim()
    if (right.isEmpty()) return false
    return right.contains('^') ||
        right.contains('\\') ||
        Regex("""\d\s*[A-Za-z]|[A-Za-z]\s*\^|[*/]""").containsMatchIn(right)
}

/**
 * Strips leading whitespace from lines inside `$$…$$` blocks so the GFM parser
 * does not treat indented math as an indented-code-block (4-space rule).
 * Also ensures `$$` markers sit on their own lines with surrounding blank lines
 * so the parser sees them as plain PARAGRAPH nodes.
 */
private val BLOCK_MATH_DEINDENT_RE = Regex("""\$\$[\s\S]*?\$\$""")

private fun deindentMathBlocks(text: String): String {
    var result = text
    BLOCK_MATH_DEINDENT_RE.findAll(text).toList().asReversed().forEach { m ->
        val original = m.value
        val cleaned = original.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("$$")) line.trimStart()
            else line.replaceFirst(Regex("""^ {1,4}"""), "")
        }
        val prefix = if (m.range.first > 0 && result[m.range.first - 1] != '\n') "\n" else ""
        val suffix = if (m.range.last + 1 < result.length && result[m.range.last + 1] != '\n') "\n" else ""
        result = result.substring(0, m.range.first) + prefix + cleaned + suffix + result.substring(m.range.last + 1)
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
//  LaTeX detection patterns (used by PARAGRAPH handler)
// ─────────────────────────────────────────────────────────────────────────────

// 匹配任意 \begin{env}…\end{env}（含 document 级别的完整文档）
private val LATEX_ENV_RE = Regex("""\\begin\{[^}]+\}[\s\S]*?\\end\{[^}]+\}""")

// 识别完整 LaTeX 文档（含 \begin{document}）
private val LATEX_FULL_DOC_RE = Regex("""\\begin\{document\}[\s\S]*?\\end\{document\}""")

/** Matches a paragraph that is purely `$$…$$` (possibly with leading/trailing whitespace/newlines). */
private val PURE_BLOCK_MATH_RE = Regex("""^\s*\$\$[\s\S]*?\$\$\s*$""")

/** Detects `$$…$$` anywhere inside text (for mixed-content paragraphs). */
private val BLOCK_MATH_INLINE_RE = Regex("""\$\$[\s\S]*?\$\$""")

// ─────────────────────────────────────────────────────────────────────────────
//  Public entry composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The main Markdown rendering composable.
 *
 * Parses [text] on [Dispatchers.Default] using the JetBrains GFM parser and
 * builds a Compose component tree from the resulting AST.  While parsing is
 * in-progress (e.g. during streaming), the raw text is shown as a plain-text
 * fallback to avoid visible layout jumps.
 *
 * Wrap with [CompositionLocalProvider]([LocalMarkdownSettings]) to customise
 * code-block and LaTeX options.
 */
@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    settings: MarkdownSettings = LocalMarkdownSettings.current,
) {
    CompositionLocalProvider(LocalMarkdownSettings provides settings) {
        MarkdownContent(text = text, modifier = modifier)
    }
}

@Composable
private fun MarkdownContent(text: String, modifier: Modifier) {
    var parsed by remember { mutableStateOf(parseMarkdownResult(text)) }
    val latestText by rememberUpdatedState(text)

    LaunchedEffect(Unit) {
        snapshotFlow { latestText }
            .distinctUntilChanged()
            .mapLatest { candidate ->
                parseMarkdownResult(candidate)
            }
            .flowOn(Dispatchers.Default)
            .collect { parsed = it }
    }

    when (val result = parsed) {
        is MarkdownParseResult.Html -> {
            HtmlBlock(
                html = result.html,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is MarkdownParseResult.Ast -> {
            Column(
                modifier            = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Group consecutive pipe-table-like paragraphs so they render as a single
                // table even when the GFM parser fails to recognise them (e.g. missing
                // separator line).
                val groups = groupPipeTableParagraphs(result.root.children, result.source)
                groups.forEach { group ->
                    when (group) {
                        is ContentGroup.PipeTable -> {
                            PipeTableFallback(rawLines = group.lines, source = result.source)
                        }
                        is ContentGroup.Node -> {
                            MarkdownNode(node = group.node, source = result.source)
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownResult(text: String): MarkdownParseResult {
    val processed = preProcess(text)
    return if (shouldUseHtmlRenderPipeline(processed)) {
        MarkdownParseResult.Html(generateMarkdownHtml(processed))
    } else {
        MarkdownParseResult.Ast(
            source = processed,
            root = markdownParser.buildMarkdownTreeFromString(processed),
        )
    }
}

private val HTML_RENDER_TAG_RE = Regex(
    """(?is)<\s*(details|summary|table|thead|tbody|tr|td|th|div|span|font|progress|img|figure|figcaption|blockquote|pre|code|hr|br)\b"""
)

private val HTML_RENDER_ATTR_RE = Regex(
    """(?is)<\s*[a-z][a-z0-9-]*\b[^>]*\b(style|class|open|src|href|width|height|align)\s*="""
)

private val MERMAID_FENCE_RE = Regex("""(?is)`{3,}\s*mermaid\b""")

internal fun shouldUseHtmlRenderPipeline(text: String): Boolean {
    if (!text.contains('<') || MERMAID_FENCE_RE.containsMatchIn(text)) {
        return false
    }
    return HTML_RENDER_TAG_RE.containsMatchIn(text) || HTML_RENDER_ATTR_RE.containsMatchIn(text)
}

private fun generateMarkdownHtml(content: String): String {
    val tree = markdownParser.buildMarkdownTreeFromString(content)
    return HtmlGenerator(content, tree, markdownFlavour).generateHtml()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pipe-table grouping – catches tables the GFM parser missed
// ─────────────────────────────────────────────────────────────────────────────

/** Logical content group emitted by [groupPipeTableParagraphs]. */
private sealed class ContentGroup {
    /** A group of consecutive paragraphs that look like a pipe-delimited table. */
    data class PipeTable(val lines: List<String>) : ContentGroup()
    /** A regular AST node to be dispatched by [MarkdownNode]. */
    data class Node(val node: ASTNode) : ContentGroup()
}

/**
 * Scans top-level children and groups consecutive PARAGRAPH nodes whose raw
 * text looks like pipe-table rows (contains `|` and at least one row has `-|`).
 *
 * This catches tables that the GFM parser failed to recognise because the
 * separator line was missing or malformed.
 */
private fun groupPipeTableParagraphs(
    children: List<ASTNode>,
    source: String,
): List<ContentGroup> {
    val groups = mutableListOf<ContentGroup>()
    var buffer = mutableListOf<ASTNode>()

    fun flushBuffer() {
        if (buffer.isEmpty()) return
        // Check if all buffered paragraphs look like pipe-table rows
        val lines = buffer.map { n ->
            source.substring(n.startOffset, n.endOffset).trim()
        }
        val looksLikeTable = lines.size >= 2 &&
            lines.all { it.contains('|') } &&
            lines.any { it.contains(Regex("""\|[\s-]*:?-+:?[\s|]""")) }

        if (looksLikeTable) {
            groups += ContentGroup.PipeTable(lines)
        } else {
            buffer.forEach { groups += ContentGroup.Node(it) }
        }
        buffer = mutableListOf()
    }

    for (child in children) {
        if (child.type == MarkdownElementTypes.PARAGRAPH) {
            buffer += child
        } else {
            flushBuffer()
            groups += ContentGroup.Node(child)
        }
    }
    flushBuffer()
    return groups
}

// ─────────────────────────────────────────────────────────────────────────────
//  PipeTableFallback – renders pipe-table text that GFM didn't recognise
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses raw pipe-table lines and renders them as a visual table.
 *
 * Unlike [DataTable] which consumes a GFM AST node, this works directly on
 * text lines — making it a fallback for malformed tables.
 */
@Composable
private fun PipeTableFallback(
    rawLines: List<String>,
    source: String,
) {
    val rows = rawLines
        .filter { it.contains('|') }
        .filterNot { it.matches(Regex("""^\|?[\s-:|]+\|?$""")) }
        .map { line ->
            line.trim().removePrefix("|").removeSuffix("|")
                .split('|').map { it.trim() }
        }
        .filter { it.any { cell -> cell.isNotBlank() } }

    if (rows.isEmpty()) return
    val colCount = rows.maxOfOrNull { it.size } ?: 0
    if (colCount == 0) return

    val headerRow    = rows.first()
    val dataRows     = rows.drop(1)
    val headerBg     = MaterialTheme.colorScheme.primaryContainer
    val rowAltBg     = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val borderColor  = MaterialTheme.colorScheme.outline
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val tableShape   = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(tableShape)
            .border(1.dp, borderColor, tableShape),
    ) {
        Row(
            modifier = Modifier
                .background(headerBg)
                .width(IntrinsicSize.Max),
        ) {
            headerRow.forEachIndexed { idx, cell ->
                if (idx > 0) VerticalDivider(color = borderColor.copy(alpha = 0.5f), modifier = Modifier.fillMaxHeight())
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(text = cell, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            repeat((colCount - headerRow.size).coerceAtLeast(0)) { Box(modifier = Modifier.weight(1f)) }
        }
        HorizontalDivider(color = borderColor, thickness = 1.dp)

        dataRows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .width(IntrinsicSize.Max)
                    .background(if (rowIdx % 2 == 1) rowAltBg else MaterialTheme.colorScheme.surface),
            ) {
                row.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(color = dividerColor, modifier = Modifier.fillMaxHeight())
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(text = cell, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                repeat((colCount - row.size).coerceAtLeast(0)) { Box(modifier = Modifier.weight(1f)) }
            }
            if (rowIdx < dataRows.lastIndex) HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MarkdownNode – recursive AST dispatcher
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Recursively dispatches an AST [node] to the appropriate renderer.
 *
 * @param isBoldContext  True when rendering inside a table header cell.
 */
@Composable
fun MarkdownNode(
    node: ASTNode,
    source: String,
    depth: Int = 0,
    isBoldContext: Boolean = false,
) {
    when (node.type) {
        // ── Document root ─────────────────────────────────────────────────────
        MarkdownElementTypes.MARKDOWN_FILE -> {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xs))) {
                node.children.forEach { MarkdownNode(it, source, depth) }
            }
        }

        // ── Block math ($$…$$ / \begin{…}…\end{…}) ────────────────────────────
        MarkdownElementTypes.PARAGRAPH -> {
            val raw = source.substring(node.startOffset, node.endOffset)
            when {
                PURE_BLOCK_MATH_RE.matches(raw.trim()) -> {
                    MathBlock(formula = processLatex(raw.trim()))
                }
                // 完整 LaTeX 文档（\begin{document}…\end{document}）：提取内部数学内容渲染
                LATEX_FULL_DOC_RE.containsMatchIn(raw) || raw.trim().contains("\\begin{document}") -> {
                    val extracted = processLatex(raw.trim())
                    if (extracted.isNotBlank() && extracted != raw.trim() && isRenderableMath(extracted)) {
                        MathBlock(formula = extracted)
                    } else {
                        ParagraphNode(node = node, source = source, isBold = isBoldContext)
                    }
                }
                LATEX_ENV_RE.containsMatchIn(raw) -> {
                    val match = LATEX_ENV_RE.find(raw)
                    if (match != null) {
                        val before = raw.substring(0, match.range.first).trim()
                        val envContent = match.value
                        val after = raw.substring(match.range.last + 1).trim()
                        if (before.isNotEmpty()) {
                            Text(text = before, style = MaterialTheme.typography.bodyMedium)
                        }
                        MathBlock(formula = envContent)
                        if (after.isNotEmpty()) {
                            Text(text = after, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        ParagraphNode(node = node, source = source, isBold = isBoldContext)
                    }
                }
                BLOCK_MATH_INLINE_RE.containsMatchIn(raw) -> {
                    MathMixedText(raw = raw, source = source, node = node, isBold = isBoldContext)
                }
                else -> {
                    ParagraphNode(node = node, source = source, isBold = isBoldContext)
                }
            }
        }

        // ── Headings ──────────────────────────────────────────────────────────
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
            HeadingNode(node, source, MaterialTheme.typography.headlineLarge)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
            HeadingNode(node, source, MaterialTheme.typography.headlineMedium)
        MarkdownElementTypes.ATX_3 ->
            HeadingNode(node, source, MaterialTheme.typography.headlineSmall)
        MarkdownElementTypes.ATX_4 ->
            HeadingNode(node, source, MaterialTheme.typography.titleLarge)
        MarkdownElementTypes.ATX_5 ->
            HeadingNode(node, source, MaterialTheme.typography.titleMedium)
        MarkdownElementTypes.ATX_6 ->
            HeadingNode(node, source, MaterialTheme.typography.titleSmall)

        // ── Code ──────────────────────────────────────────────────────────────
        MarkdownElementTypes.CODE_FENCE -> {
            val lang    = extractFenceLang(node, source)
            val payload = extractFenceContent(node, source)

            val isExplicitMathLang = lang.lowercase() in setOf("math", "latex", "tex")
            val isUnlabelledMath   = lang.isEmpty() && PAYLOAD_IS_LATEX_RE.matches(payload.trim())

            // 含 \begin{document} 的完整 LaTeX 文档：按数学片段切割，公式渲染为图像，其余保留为代码块
            val isFullLatexDoc = payload.contains("\\begin{document}")

            if ((isExplicitMathLang || isUnlabelledMath) && !isFullLatexDoc) {
                val trimmed = payload.trim()
                val processed = processLatex(trimmed)
                val hasMathContent = processed.isNotBlank() && (
                    isRenderableMath(processed) ||
                    (isExplicitMathLang && processed.length < trimmed.length)
                )
                if (hasMathContent) {
                    when {
                        PURE_BLOCK_MATH_RE.matches(trimmed) ->
                            MathBlock(formula = processed)
                        BLOCK_MATH_INLINE_RE.containsMatchIn(trimmed) ->
                            MathMixedText(raw = trimmed, source = source, node = node, isBold = isBoldContext)
                        else ->
                            MathBlock(formula = processed)
                    }
                } else {
                    CodeBlock(language = lang, code = payload)
                }
            } else if (isFullLatexDoc && isExplicitMathLang) {
                LatexDocBlock(lang = lang, payload = payload)
            } else {
                CodeBlock(language = lang, code = payload)
            }
        }

        MarkdownElementTypes.CODE_BLOCK -> {
            val rawText = source.substring(node.startOffset, node.endOffset)
            val payload = rawText.lines().joinToString("\n") { it.removePrefix("    ") }.trimEnd()

            if (!payload.contains("\\begin{document}") && PAYLOAD_IS_LATEX_RE.matches(payload.trim())) {
                val processed = processLatex(payload.trim())
                if (processed.isNotBlank() && isRenderableMath(processed)) {
                    MathBlock(formula = processed)
                } else {
                    CodeBlock(language = "", code = payload)
                }
            } else {
                CodeBlock(language = "", code = payload)
            }
        }

        // ── HTML block ────────────────────────────────────────────────────────
        MarkdownElementTypes.HTML_BLOCK -> {
            val html = source.substring(node.startOffset, node.endOffset)
            HtmlBlock(html = html)
        }

        // ── GFM Table ─────────────────────────────────────────────────────────
        GFMElementTypes.TABLE -> {
            DataTable(node = node, source = source)
        }

        // ── Lists ─────────────────────────────────────────────────────────────
        MarkdownElementTypes.UNORDERED_LIST -> ListNode(node, source, ordered = false, depth = depth)
        MarkdownElementTypes.ORDERED_LIST   -> ListNode(node, source, ordered = true,  depth = depth)

        // ── Block quote ───────────────────────────────────────────────────────
        MarkdownElementTypes.BLOCK_QUOTE -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .padding(vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    node.children.forEach { MarkdownNode(it, source, depth + 1) }
                }
            }
        }

        // ── Horizontal rule ───────────────────────────────────────────────────
        MarkdownTokenTypes.HORIZONTAL_RULE ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Inline elements at top level ──────────────────────────────────────
        MarkdownElementTypes.EMPH,
        MarkdownElementTypes.STRONG,
        GFMElementTypes.STRIKETHROUGH,
        MarkdownElementTypes.CODE_SPAN,
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.IMAGE,
        MarkdownElementTypes.AUTOLINK,
        MarkdownTokenTypes.TEXT,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.EOL        -> {
            RenderInlineNodes(nodes = listOf(node), source = source, isBold = isBoldContext)
        }

        // ── Recurse for wrapper / unknown nodes ───────────────────────────────
        else -> {
            node.children.forEach { MarkdownNode(it, source, depth) }
        }
    }
}

@Composable
private fun RenderInlineNodes(
    nodes: List<ASTNode>,
    source: String,
    isBold: Boolean,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) return
    val annotated = buildInlineAnnotatedString(
        nodes = nodes,
        source = source,
        baseColor = MaterialTheme.colorScheme.onSurface,
        linkColor = MaterialTheme.colorScheme.primary,
    )
    val rawText = source.substring(nodes.first().startOffset, nodes.last().endOffset).trim()
    val mathText = when {
        LocalMarkdownSettings.current.enableLatex && containsInlineMath(annotated.text) -> annotated.text
        LocalMarkdownSettings.current.enableLatex && containsInlineMath(rawText) -> rawText
        else -> null
    }
    if (mathText != null) {
        MathInlineText(text = mathText, modifier = modifier)
    } else {
        RenderInlineText(text = annotated, isBold = isBold, modifier = modifier)
    }
}

@Composable
private fun RenderInlineText(
    text: AnnotatedString,
    isBold: Boolean,
    modifier: Modifier = Modifier,
) {
    if (text.text.isBlank()) return
    val uriHandler = LocalUriHandler.current
    val style = MaterialTheme.typography.bodyMedium.let {
        if (isBold) it.copy(fontWeight = FontWeight.Bold) else it
    }
    if (LocalMarkdownSettings.current.enableLatex && containsInlineMath(text.text)) {
        MathInlineText(text = text.text, modifier = modifier)
    } else {
        Text(
            text     = text,
            style    = style,
            modifier = modifier.clickOnLinks(text, uriHandler),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Paragraph renderer (handles inline math, formatting, links, images)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParagraphNode(node: ASTNode, source: String, isBold: Boolean) {
    val settings    = LocalMarkdownSettings.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val primary     = MaterialTheme.colorScheme.primary

    // Collect IMAGE nodes that live as block-level items inside the paragraph
    val imageNodes = remember(node) {
        node.children.filter { it.type == MarkdownElementTypes.IMAGE }
    }

    // Collect non-image inline nodes for text rendering
    val inlineNodes = remember(node) {
        node.children.filter { it.type != MarkdownElementTypes.IMAGE }
    }

    // Check whether the entire paragraph is just a single image reference
    if (imageNodes.size == 1 && inlineNodes.all { isEmptyTextNode(it, source) }) {
        val imgNode = imageNodes.first()
        val imgUrl  = extractImageUrl(imgNode, source)
        val altText = extractImageAlt(imgNode, source)
        if (imgUrl.isNotBlank()) {
            ZoomableAsyncImage(url = imgUrl, contentDescription = altText.ifBlank { null })
            return
        }
    }

    // Build an AnnotatedString for the entire paragraph
    val annotated = buildInlineAnnotatedString(
        nodes     = inlineNodes,
        source    = source,
        baseColor = onSurface,
        linkColor = primary,
    )

    val rawParagraphText = source.substring(node.startOffset, node.endOffset).trim()
    val mathText = when {
        settings.enableLatex && containsInlineMath(annotated.text) -> annotated.text
        settings.enableLatex && containsInlineMath(rawParagraphText) -> rawParagraphText
        else -> null
    }
    if (mathText != null) {
        MathInlineText(text = mathText, modifier = Modifier.fillMaxWidth())
        return
    }

    // Render images within the paragraph inline
    if (imageNodes.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (annotated.text.isNotBlank()) {
                RenderInlineText(text = annotated, isBold = isBold, modifier = Modifier.fillMaxWidth())
            }
            imageNodes.forEach { imgNode ->
                val imgUrl = extractImageUrl(imgNode, source)
                val alt    = extractImageAlt(imgNode, source)
                if (imgUrl.isNotBlank()) {
                    ZoomableAsyncImage(url = imgUrl, contentDescription = alt.ifBlank { null })
                }
            }
        }
    } else {
        RenderInlineText(text = annotated, isBold = isBold, modifier = Modifier.fillMaxWidth())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MathMixedText – paragraph with embedded $$…$$ block math
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a paragraph that contains `$$…$$` block-math delimiters mixed with
 * regular text.  Each `$$…$$` segment is rendered as a centred [MathBlock],
 * while surrounding text is rendered as normal [Text].
 */
@Composable
private fun MathMixedText(
    raw: String,
    source: String,
    node: ASTNode,
    isBold: Boolean,
) {
    val segments = remember(raw) { splitMixedMath(raw) }
    var nextOrderedListStart = remember(raw) { firstOrderedListMarker(raw) ?: 1 }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { seg ->
            if (seg.isMath) {
                MathBlock(formula = processLatex(seg.content))
            } else if (seg.content.isNotBlank()) {
                val orderedItems = countOrderedListMarkers(seg.content)
                RenderMarkdownFragment(
                    text = seg.content,
                    isBold = isBold,
                    orderedListStart = if (orderedItems > 0) nextOrderedListStart else null,
                )
                if (orderedItems > 0) {
                    nextOrderedListStart += orderedItems
                }
            }
        }
    }
}

@Composable
private fun RenderMarkdownFragment(
    text: String,
    isBold: Boolean,
    orderedListStart: Int? = null,
    modifier: Modifier = Modifier,
) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return

    val root = remember(trimmed) { markdownParser.buildMarkdownTreeFromString(trimmed) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var nextOrderedListStart = orderedListStart
        root.children.forEach { child ->
            if (child.type == MarkdownElementTypes.ORDERED_LIST) {
                ListNode(
                    node = child,
                    source = trimmed,
                    ordered = true,
                    depth = 0,
                    startNumber = nextOrderedListStart,
                )
                nextOrderedListStart = (nextOrderedListStart ?: orderedListStart(child, trimmed))
                    ?.plus(countOrderedListItems(child))
            } else {
                MarkdownNode(
                    node = child,
                    source = trimmed,
                    isBoldContext = isBold,
                )
            }
        }
    }
}

/** Splits text into math / non-math segments based on `$$…$$` delimiters. */
private data class MathSegRaw(val content: String, val isMath: Boolean)

private fun splitMixedMath(text: String): List<MathSegRaw> {
    val result = mutableListOf<MathSegRaw>()
    var cursor = 0
    BLOCK_MATH_INLINE_RE.findAll(text).forEach { m ->
        if (m.range.first > cursor) {
            result += MathSegRaw(text.substring(cursor, m.range.first), false)
        }
        result += MathSegRaw(m.value, true)
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        result += MathSegRaw(text.substring(cursor), false)
    }
    return result
}

@Composable
private fun LatexDocBlock(lang: String, payload: String) {
    val segments = remember(payload) { splitLatexDocSegments(payload) }
    if (segments.none { it.isMath }) {
        CodeBlock(language = lang, code = payload)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xs))) {
        segments.forEach { seg ->
            if (seg.isMath) {
                MathBlock(formula = seg.content)
            } else {
                CodeBlock(language = lang, code = seg.content)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Heading renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeadingNode(node: ASTNode, source: String, style: TextStyle) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary   = MaterialTheme.colorScheme.primary

    // Content is in ATX_CONTENT token or directly inside the node
    val contentNodes = node.children.filter { it.type == MarkdownTokenTypes.ATX_CONTENT }
        .flatMap { it.children }
        .ifEmpty { node.children }

    val text = buildInlineAnnotatedString(contentNodes, source, onSurface, primary)
    Text(
        text     = text,
        style    = style,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  List renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ListNode(
    node: ASTNode,
    source: String,
    ordered: Boolean,
    depth: Int,
    startNumber: Int? = null,
) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    val itemCount = countOrderedListItems(node)
    val markerWidth = dimensionResource(
        if (ordered && itemCount >= 10) R.dimen.m3t_spacing_xxl else R.dimen.m3t_spacing_lg
    )
    val markerGap = dimensionResource(R.dimen.m3t_spacing_xs)
    val listIndent = dimensionResource(R.dimen.m3t_spacing_lg)
    var counter = startNumber ?: orderedListStart(node, source) ?: 1

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(start = listIndent * depth),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (ordered) "${counter++}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = markerColor,
                    modifier = Modifier.width(markerWidth),
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(markerGap))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val inlineBuffer = mutableListOf<ASTNode>()

                    @Composable
                    fun flushInlineBuffer() {
                        if (inlineBuffer.isEmpty()) return
                        RenderInlineNodes(
                            nodes = inlineBuffer.toList(),
                            source = source,
                            isBold = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        inlineBuffer.clear()
                    }

                    item.children.forEach { child ->
                        when {
                            isListSyntaxNode(child, source) -> Unit
                            isInlineRenderableNode(child) -> inlineBuffer += child
                            else -> {
                                flushInlineBuffer()
                                MarkdownNode(child, source, depth + 1)
                            }
                        }
                    }
                    flushInlineBuffer()
                }
            }
        }
    }
}

private fun isListSyntaxNode(node: ASTNode, source: String): Boolean {
    val text = source.substring(node.startOffset, node.endOffset).trim()
    return text.isBlank() ||
        text.matches(Regex("""\d+[.)]""")) ||
        text == "-" ||
        text == "*" ||
        text == "+"
}

private val ORDERED_LIST_MARKER_RE = Regex("""(?m)^[ \t]{0,3}(\d+)[.)]\s+""")

private fun firstOrderedListMarker(text: String): Int? =
    ORDERED_LIST_MARKER_RE.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun countOrderedListMarkers(text: String): Int =
    ORDERED_LIST_MARKER_RE.findAll(text).count()

private fun orderedListStart(node: ASTNode, source: String): Int? =
    firstOrderedListMarker(source.substring(node.startOffset, node.endOffset))

private fun countOrderedListItems(node: ASTNode): Int =
    node.children.count { it.type == MarkdownElementTypes.LIST_ITEM }

private fun isInlineRenderableNode(node: ASTNode): Boolean = when (node.type) {
    MarkdownElementTypes.EMPH,
    MarkdownElementTypes.STRONG,
    GFMElementTypes.STRIKETHROUGH,
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.INLINE_LINK,
    MarkdownElementTypes.IMAGE,
    MarkdownElementTypes.AUTOLINK,
    MarkdownTokenTypes.TEXT,
    MarkdownTokenTypes.WHITE_SPACE,
    MarkdownTokenTypes.EOL,
    MarkdownTokenTypes.HARD_LINE_BREAK -> true
    else -> false
}

// ─────────────────────────────────────────────────────────────────────────────
//  Inline AnnotatedString builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Recursively builds an [AnnotatedString] from a list of inline AST [nodes].
 * Handles: plain text, EMPH, STRONG, STRIKETHROUGH, CODE_SPAN, INLINE_LINK, AUTOLINK.
 */
private fun buildInlineAnnotatedString(
    nodes: List<ASTNode>,
    source: String,
    baseColor: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    fun process(node: ASTNode) {
        when (node.type) {
            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE -> {
                withStyle(SpanStyle(color = baseColor)) {
                    append(source.substring(node.startOffset, node.endOffset))
                }
            }

            MarkdownTokenTypes.HARD_LINE_BREAK -> append("\n")
            MarkdownTokenTypes.EOL,
            MarkdownTokenTypes.SINGLE_QUOTE,
            MarkdownTokenTypes.DOUBLE_QUOTE   -> {
                if (node.type == MarkdownTokenTypes.EOL) append(" ")
                else withStyle(SpanStyle(color = baseColor)) {
                    append(source.substring(node.startOffset, node.endOffset))
                }
            }

            MarkdownElementTypes.EMPH -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                node.children.forEach(::process)
                pop()
            }

            MarkdownElementTypes.STRONG -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                node.children.forEach(::process)
                pop()
            }

            GFMElementTypes.STRIKETHROUGH -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor))
                node.children.forEach(::process)
                pop()
            }

            MarkdownElementTypes.CODE_SPAN -> {
                val start = node.startOffset + 1  // skip opening `
                val end   = (node.endOffset - 1).coerceAtLeast(start)
                val content = source.substring(start, end)
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                        color      = baseColor,
                    )
                )
                append(content)
                pop()
            }

            MarkdownElementTypes.INLINE_LINK -> {
                val url      = extractLinkUrl(node, source)
                val textNodes = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.children ?: emptyList()
                pushStringAnnotation("URL", url)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                textNodes.forEach(::process)
                pop(); pop()
            }

            MarkdownElementTypes.AUTOLINK -> {
                val url = source.substring(node.startOffset + 1, node.endOffset - 1).trim()
                pushStringAnnotation("URL", url)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(url)
                pop(); pop()
            }

            MarkdownElementTypes.IMAGE -> {
                // Images in inline context: show alt text as fallback in the string
                val alt = extractImageAlt(node, source)
                withStyle(SpanStyle(color = baseColor.copy(alpha = 0.6f))) {
                    append("[image: $alt]")
                }
            }

            // Recurse for wrapper nodes
            else -> node.children.forEach(::process)
        }
    }
    nodes.forEach(::process)
}

/** Fallback: plain text representation used before MathInlineText gets the string. */
private fun buildAnnotatedStringWithMathFallback(
    nodes: List<ASTNode>,
    source: String,
    baseColor: Color,
    linkColor: Color,
): AnnotatedString = buildInlineAnnotatedString(nodes, source, baseColor, linkColor)

// ─────────────────────────────────────────────────────────────────────────────
//  AST extraction helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun extractFenceLang(node: ASTNode, source: String): String =
    node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
        ?.let { source.substring(it.startOffset, it.endOffset).trim() }
        ?: ""

private fun extractFenceContent(node: ASTNode, source: String): String {
    val tokens = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (tokens.isEmpty()) return ""
    return source.substring(tokens.first().startOffset, tokens.last().endOffset).trimEnd('\n', '\r')
}

private fun extractLinkUrl(node: ASTNode, source: String): String =
    node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        ?.let { source.substring(it.startOffset, it.endOffset).trim() }
        ?: ""

private fun extractImageUrl(imgNode: ASTNode, source: String): String {
    // IMAGE → INLINE_LINK → LINK_DESTINATION  OR  IMAGE → LINK_DESTINATION directly
    val inner = imgNode.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
        ?: imgNode
    return inner.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        ?.let { source.substring(it.startOffset, it.endOffset).trim() }
        ?: ""
}

private fun extractImageAlt(imgNode: ASTNode, source: String): String {
    val inner = imgNode.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
        ?: imgNode
    return inner.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        ?.let { source.substring(it.startOffset, it.endOffset).trim().removeSurrounding("[", "]") }
        ?: ""
}

private fun isEmptyTextNode(node: ASTNode, source: String): Boolean {
    val type = node.type
    if (type == MarkdownTokenTypes.TEXT || type == MarkdownTokenTypes.WHITE_SPACE ||
        type == MarkdownTokenTypes.EOL) {
        return source.substring(node.startOffset, node.endOffset).isBlank()
    }
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
//  Modifier helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Modifier.clickOnLinks(
    annotated: AnnotatedString,
    uriHandler: androidx.compose.ui.platform.UriHandler,
): Modifier = clickable(
    indication    = null,
    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
) {
    // find first URL annotation at the tap position – fallback: open the first URL
    val urls = annotated.getStringAnnotations("URL", 0, annotated.length)
    urls.firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
}
