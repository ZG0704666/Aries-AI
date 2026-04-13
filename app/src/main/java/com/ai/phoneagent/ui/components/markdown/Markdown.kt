package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import android.util.Log
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private const val MD_DBG = "MD_DEBUG"

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
    val lineNumbers: Boolean  = false,
    /** Auto-collapse code blocks with more than 10 lines. */
    val autoCollapse: Boolean = false,
    /** Render LaTeX formulas using JLatexMath (falls back to monospace if false). */
    val enableLatex: Boolean  = true,
)

val LocalMarkdownSettings = compositionLocalOf { MarkdownSettings() }

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

/**
 * Before passing text to the GFM parser:
 *  1. Locate all code ranges (fenced blocks + inline code) so they are skipped.
 *  2. Outside those ranges, replace `\[…\]` with `$$…$$` and `\(…\)` with `$…$`
 *     so the rendered text contains recognisable block/inline math markers.
 */
fun preProcess(text: String): String {
    // Build a sorted list of code ranges to avoid replacement inside them.
    // Fenced blocks and inline code are found separately so inline code
    // never matches across newlines (the root cause of table corruption).
    val codeRanges = buildList {
        addAll(FENCED_CODE_RE.findAll(text).map { it.range })
        addAll(INLINE_CODE_RE.findAll(text).map { it.range })
    }.sortedBy { it.first }

    fun inCodeRange(index: Int): Boolean {
        // Binary-search-friendly linear scan (ranges are sorted and few)
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
    return sb.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
//  LaTeX detection patterns (used by PARAGRAPH handler)
// ─────────────────────────────────────────────────────────────────────────────

/** Matches `\begin{env}...\end{env}` blocks (may span multiple lines). */
private val LATEX_ENV_RE = Regex("""\\begin\{[^}]+\}[\s\S]*?\\end\{[^}]+\}""")

/** Matches a paragraph that is purely `$$…$$` (possibly with leading/trailing whitespace/newlines). */
private val PURE_BLOCK_MATH_RE = Regex("""^\s*\$\$[\s\S]*?\$\$\s*$""")

/** Detects `$$…$$` anywhere inside text (for mixed-content paragraphs). */
private val BLOCK_MATH_INLINE_RE = Regex("""\$\$[\s\S]*?\$\$""")

// ─────────────────────────────────────────────────────────────────────────────
//  Debug: dump AST tree to Logcat
// ─────────────────────────────────────────────────────────────────────────────

/** Recursively logs the AST tree for debugging. */
private fun dumpAstTree(node: ASTNode, source: String, depth: Int, maxDepth: Int) {
    val indent = "  ".repeat(depth)
    val snippet = source.substring(node.startOffset, node.endOffset.coerceAtMost(source.length))
        .take(80).replace('\n', '↵')
    Log.d(MD_DBG, "${indent}[${node.type}] ${node.startOffset}..${node.endOffset} «${snippet}»")
    if (depth < maxDepth) {
        node.children.forEach { dumpAstTree(it, source, depth + 1, maxDepth) }
    }
}

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
    data class ParseResult(val source: String, val root: ASTNode)

    var parsed by remember { mutableStateOf<ParseResult?>(null) }

    // Reactive parse: cancel in-flight parse if text changes (streaming)
    LaunchedEffect(Unit) {
        snapshotFlow { text }
            .distinctUntilChanged()
            .mapLatest { src ->
                val processed = preProcess(src)
                Log.d(MD_DBG, "═══ preProcess INPUT (${src.length} chars) ═══")
                Log.d(MD_DBG, src.take(500))
                Log.d(MD_DBG, "═══ preProcess OUTPUT (${processed.length} chars) ═══")
                Log.d(MD_DBG, processed.take(500))
                val root = withContext(Dispatchers.Default) {
                    val flavour = GFMFlavourDescriptor()
                    MarkdownParser(flavour).buildMarkdownTreeFromString(processed)
                }
                Log.d(MD_DBG, "═══ AST root type=${root.type} children=${root.children.size} ═══")
                dumpAstTree(root, processed, depth = 0, maxDepth = 3)
                ParseResult(processed, root)
            }
            .collect { parsed = it }
    }

    val result = parsed
    if (result == null) {
        // Streaming fallback: plain text until first parse completes
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Group consecutive pipe-table-like paragraphs so they render as a single
        // table even when the GFM parser fails to recognise them (e.g. missing
        // separator line).
        val groups = groupPipeTableParagraphs(result.root.children, result.source)
        Log.d(MD_DBG, "═══ groupPipeTableParagraphs: ${groups.size} groups ═══")
        groups.forEachIndexed { idx, g ->
            when (g) {
                is ContentGroup.PipeTable -> Log.d(MD_DBG, "  group[$idx] = PipeTable (${g.lines.size} lines)")
                is ContentGroup.Node -> Log.d(MD_DBG, "  group[$idx] = Node type=${g.node.type}")
            }
        }
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
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // Parse rows: skip separator line, split on `|`
    val rows = rawLines
        .filter { it.contains('|') }
        .filterNot { it.matches(Regex("""^\|?[\s-:|]+\|?$""")) }
        .map { line ->
            line.trim()
                .removePrefix("|")
                .removeSuffix("|")
                .split('|')
                .map { it.trim() }
        }
        .filter { it.any { cell -> cell.isNotBlank() } }

    if (rows.isEmpty()) return

    val colCount = rows.maxOfOrNull { it.size } ?: 0
    if (colCount == 0) return

    // Determine header: if first row has `-` patterns, it's a separator (already filtered).
    // Otherwise first row is the header.
    val headerRow = rows.firstOrNull() ?: return
    val dataRows = rows.drop(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier
                .background(surfaceVariant, MaterialTheme.shapes.small)
                .width(IntrinsicSize.Max),
        ) {
            headerRow.forEachIndexed { idx, cell ->
                if (idx > 0) VerticalDivider(color = outlineVariant, modifier = Modifier.fillMaxHeight())
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(text = cell, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            repeat((colCount - headerRow.size).coerceAtLeast(0)) {
                Box(modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = outlineVariant)

        // Data rows
        dataRows.forEachIndexed { rowIdx, row ->
            Row(modifier = Modifier.fillMaxWidth().width(IntrinsicSize.Max)) {
                row.forEachIndexed { idx, cell ->
                    if (idx > 0) VerticalDivider(color = outlineVariant, modifier = Modifier.fillMaxHeight())
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(text = cell, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                repeat((colCount - row.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            if (rowIdx < dataRows.lastIndex) HorizontalDivider(color = outlineVariant)
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
            Log.d(MD_DBG, "MarkdownNode: MARKDOWN_FILE (${node.children.size} children)")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children.forEach { MarkdownNode(it, source, depth) }
            }
        }

        // ── Block math ($$…$$ / \begin{…}…\end{…}) ────────────────────────────
        MarkdownElementTypes.PARAGRAPH -> {
            val raw = source.substring(node.startOffset, node.endOffset)
            Log.d(MD_DBG, "MarkdownNode: PARAGRAPH «${raw.take(100).replace('\n','↵')}»")
            when {
                // Pure block-math paragraph: $$…$$ only
                PURE_BLOCK_MATH_RE.matches(raw.trim()) -> {
                    MathBlock(formula = processLatex(raw.trim()))
                }
                // LaTeX environment block: \begin{…}…\end{…}
                LATEX_ENV_RE.containsMatchIn(raw) -> {
                    val match = LATEX_ENV_RE.find(raw)
                    if (match != null) {
                        val before = raw.substring(0, match.range.first).trim()
                        val envContent = match.value
                        val after = raw.substring(match.range.last + 1).trim()

                        // Render leading text
                        if (before.isNotEmpty()) {
                            Text(text = before, style = MaterialTheme.typography.bodyMedium)
                        }
                        // Render the LaTeX environment
                        MathBlock(formula = envContent)
                        // Render trailing text
                        if (after.isNotEmpty()) {
                            Text(text = after, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        ParagraphNode(node = node, source = source, isBold = isBoldContext)
                    }
                }
                // Mixed content with inline $$…$$: e.g. "公式：$$x=1$$ 如下"
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
            val lang = extractFenceLang(node, source)
            val code = extractFenceContent(node, source)
            val rawText = source.substring(node.startOffset, node.endOffset)
            Log.w(MD_DBG, "▶ CODE_FENCE  lang=$lang  raw(120)=«${rawText.take(120).replace('\n','↵')}»")
            CodeBlock(language = lang, code = code)
        }

        MarkdownElementTypes.CODE_BLOCK -> {
            val rawText = source.substring(node.startOffset, node.endOffset)
            Log.w(MD_DBG, "▶ CODE_BLOCK  raw(120)=«${rawText.take(120).replace('\n','↵')}»")
            val code = source.substring(node.startOffset, node.endOffset)
                .lines().joinToString("\n") { it.removePrefix("    ") }.trimEnd()
            CodeBlock(language = "", code = code)
        }

        // ── HTML block ────────────────────────────────────────────────────────
        MarkdownElementTypes.HTML_BLOCK -> {
            val html = source.substring(node.startOffset, node.endOffset)
            HtmlBlock(html = html)
        }

        // ── GFM Table ─────────────────────────────────────────────────────────
        GFMElementTypes.TABLE -> {
            Log.d(MD_DBG, "▶ GFM TABLE detected!")
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
            // Inline nodes at block level → wrap in a paragraph
            val text = buildInlineAnnotatedString(listOf(node), source,
                MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary)
            if (text.text.isNotBlank()) Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }

        // ── Recurse for wrapper / unknown nodes ───────────────────────────────
        else -> {
            val rawSnippet = source.substring(node.startOffset, node.endOffset.coerceAtMost(source.length)).take(80).replace('\n','↵')
            Log.d(MD_DBG, "MarkdownNode: UNKNOWN/PASS type=${node.type} «${rawSnippet}»")
            node.children.forEach { MarkdownNode(it, source, depth) }
        }
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
    val boldWeight  = if (isBold) FontWeight.Bold else null

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

    // Full text of the paragraph — check for inline math
    val fullText = source.substring(node.startOffset, node.endOffset).trim()
    val hasMath  = settings.enableLatex && INLINE_MATH_RE.containsMatchIn(fullText)

    if (hasMath) {
        MathInlineText(
            text     = buildAnnotatedStringWithMathFallback(inlineNodes, source, onSurface, primary).text,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    // Build an AnnotatedString for the entire paragraph
    val annotated = buildInlineAnnotatedString(
        nodes     = inlineNodes,
        source    = source,
        baseColor = onSurface,
        linkColor = primary,
    )

    val uriHandler = LocalUriHandler.current
    val style = MaterialTheme.typography.bodyMedium.let {
        if (boldWeight != null) it.copy(fontWeight = boldWeight) else it
    }

    // Render images within the paragraph inline
    if (imageNodes.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (annotated.text.isNotBlank()) {
                Text(
                    text     = annotated,
                    style    = style,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickOnLinks(annotated, uriHandler),
                )
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
        Text(
            text     = annotated,
            style    = style,
            modifier = Modifier
                .fillMaxWidth()
                .clickOnLinks(annotated, uriHandler),
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { seg ->
            if (seg.isMath) {
                MathBlock(formula = processLatex(seg.content))
            } else if (seg.content.isNotBlank()) {
                // Fall back to normal inline rendering for the text portion
                val onSurface = MaterialTheme.colorScheme.onSurface
                val primary = MaterialTheme.colorScheme.primary
                val style = MaterialTheme.typography.bodyMedium.let {
                    val boldWeight = if (isBold) FontWeight.Bold else null
                    if (boldWeight != null) it.copy(fontWeight = boldWeight) else it
                }
                Text(text = seg.content, style = style)
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
private fun ListNode(node: ASTNode, source: String, ordered: Boolean, depth: Int) {
    val primary = MaterialTheme.colorScheme.primary
    var counter = 1

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = if (ordered) "${counter++}.  " else "•  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    item.children.forEach { child ->
                        MarkdownNode(child, source, depth + 1)
                    }
                }
            }
        }
    }
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
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                        background = Color(0x1A808080),
                        color      = baseColor,
                    )
                )
                append(source.substring(start, end))
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

private fun extractFenceContent(node: ASTNode, source: String): String =
    node.children
        .filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        .joinToString("") { source.substring(it.startOffset, it.endOffset) }
        .trimEnd('\n', '\r')

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
