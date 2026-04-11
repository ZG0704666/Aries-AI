package com.ai.phoneagent.ui.markdown

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

// Lazy-init parser, shared across all instances
private val gfmFlavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val mdParser by lazy {
    MarkdownParser(gfmFlavour)
}

private val BREAK_LINE_REGEX = Regex("(?i)<br\\s*/?>")

// ────────── LaTeX 预处理 ──────────
private val INLINE_LATEX_REGEX = Regex("\\\\\\((.+?)\\\\\\)")
private val BLOCK_LATEX_REGEX = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)
private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)

/**
 * 预处理 Markdown 内容：
 * - 将 \( ... \) 转换为 $ ... $ (行内公式)
 * - 将 \[ ... \] 转换为 $$ ... $$ (块级公式)
 * - 跳过代码块内的内容
 */
private fun preProcess(content: String): String {
    val codeBlocks = mutableListOf<IntRange>()
    CODE_BLOCK_REGEX.findAll(content).forEach { match ->
        codeBlocks.add(match.range)
    }

    fun isInCodeBlock(position: Int): Boolean {
        return codeBlocks.any { range -> position in range }
    }

    var result = INLINE_LATEX_REGEX.replace(content) { matchResult ->
        if (isInCodeBlock(matchResult.range.first)) {
            matchResult.value
        } else {
            "$" + matchResult.groupValues[1] + "$"
        }
    }

    result = BLOCK_LATEX_REGEX.replace(result) { matchResult ->
        if (isInCodeBlock(matchResult.range.first)) {
            matchResult.value
        } else {
            "$$" + matchResult.groupValues[1] + "$$"
        }
    }

    return result
}

// ────────── Header styles ──────────
private object HeaderStyle {
    val H1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
    val H2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val H3 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val H4 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val H5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val H6 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

/**
 * Aries AI Markdown Block – Compose 版本
 *
 * 基于 intellij-markdown AST 解析，在后台线程异步解析以避免掉帧。
 * 支持: 标题、段落、加粗/斜体/删除线、行内代码、代码块(语法高亮)、
 *       列表(有序/无序/嵌套/checkbox)、引用、表格、水平线、链接、
 *       图片、LaTeX 数学公式(行内/块级)、HTML 块、Mermaid 图表。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    if (content.isBlank()) return

    // 最简单可靠的方式：content 变化时同步重解析，runCatching 兜底
    val parsedData = remember(content) {
        runCatching {
            val preprocessed = preProcess(content)
            val tree = mdParser.buildMarkdownTreeFromString(preprocessed)
            preprocessed to tree
        }.getOrNull()
    }

    ProvideTextStyle(style) {
        SelectionContainer {
            Column(modifier = modifier.padding(start = 4.dp)) {
                if (parsedData != null) {
                    val (src, astTree) = parsedData
                    astTree.children.forEach { child ->
                        MarkdownNode(node = child, content = src)
                    }
                } else {
                    // Fallback: AST 解析失败，显示原始文本
                    Text(text = content)
                }
            }
        }
    }
}

// ────────── Node renderer (recursive) ──────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    listLevel: Int = 0,
) {
    when (node.type) {
        // Root
        MarkdownElementTypes.MARKDOWN_FILE -> {
            node.children.forEach { child ->
                MarkdownNode(node = child, content = content, modifier = modifier)
            }
        }

        // Paragraph
        MarkdownElementTypes.PARAGRAPH -> {
            // 如果段落包含图片或块级公式，用 FlowRow 逐子节点渲染
            if (node.findChildOfTypeRecursive(
                    MarkdownElementTypes.IMAGE,
                    GFMElementTypes.BLOCK_MATH,
                ) != null
            ) {
                FlowRow(modifier = modifier) {
                    node.children.forEach { child ->
                        MarkdownNode(node = child, content = content)
                    }
                }
                return
            }
            Paragraph(node = node, content = content, modifier = modifier)
        }

        // Headings
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val textStyle = when (node.type) {
                MarkdownElementTypes.ATX_1 -> HeaderStyle.H1
                MarkdownElementTypes.ATX_2 -> HeaderStyle.H2
                MarkdownElementTypes.ATX_3 -> HeaderStyle.H3
                MarkdownElementTypes.ATX_4 -> HeaderStyle.H4
                MarkdownElementTypes.ATX_5 -> HeaderStyle.H5
                MarkdownElementTypes.ATX_6 -> HeaderStyle.H6
                else -> HeaderStyle.H6
            }
            val headingPadding = when (node.type) {
                MarkdownElementTypes.ATX_1 -> 16.dp
                MarkdownElementTypes.ATX_2 -> 14.dp
                MarkdownElementTypes.ATX_3 -> 12.dp
                MarkdownElementTypes.ATX_4 -> 10.dp
                MarkdownElementTypes.ATX_5 -> 8.dp
                else -> 6.dp
            }
            ProvideTextStyle(value = textStyle) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.children.forEach { child ->
                        if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                            Paragraph(
                                node = child,
                                content = content,
                                modifier = modifier.padding(vertical = headingPadding),
                                trim = true,
                            )
                        }
                    }
                }
            }
        }

        // Unordered list
        MarkdownElementTypes.UNORDERED_LIST -> {
            UnorderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                level = listLevel,
            )
        }

        // Ordered list
        MarkdownElementTypes.ORDERED_LIST -> {
            OrderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                level = listLevel,
            )
        }

        // Checkbox
        GFMTokenTypes.CHECK_BOX -> {
            val isChecked = node.getTextInNode(content).trim() == "[x]"
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(2.dp)
                    .size(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Block quote
        MarkdownElementTypes.BLOCK_QUOTE -> {
            ProvideTextStyle(LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)) {
                val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                Column(
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            drawRect(color = bgColor, size = size)
                            drawRect(color = borderColor, size = Size(10f, size.height))
                        }
                        .padding(8.dp)
                ) {
                    node.children.forEach { child ->
                        MarkdownNode(node = child, content = content)
                    }
                }
            }
        }

        // Inline link
        MarkdownElementTypes.INLINE_LINK -> {
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.findChildOfTypeRecursive(GFMTokenTypes.GFM_AUTOLINK, MarkdownTokenTypes.TEXT)
                ?.getTextInNode(content) ?: ""
            val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(content) ?: ""
            val context = LocalContext.current
            Text(
                text = linkText,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = modifier.clickable {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, linkDest.toUri())
                        context.startActivity(intent)
                    }
                },
            )
        }

        // Emphasis (italic)
        MarkdownElementTypes.EMPH -> {
            ProvideTextStyle(TextStyle(fontStyle = FontStyle.Italic)) {
                node.children.forEach { child ->
                    MarkdownNode(node = child, content = content, modifier = modifier)
                }
            }
        }

        // Strong (bold)
        MarkdownElementTypes.STRONG -> {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.forEach { child ->
                    MarkdownNode(node = child, content = content, modifier = modifier)
                }
            }
        }

        // Strikethrough
        GFMElementTypes.STRIKETHROUGH -> {
            Text(
                text = node.getTextInNode(content),
                textDecoration = TextDecoration.LineThrough,
                modifier = modifier,
            )
        }

        // GFM Table
        GFMElementTypes.TABLE -> {
            MarkdownTableNode(node = node, content = content, modifier = modifier)
        }

        // Horizontal rule
        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
        }

        // ────── 图片 (IMAGE) ──────
        MarkdownElementTypes.IMAGE -> {
            val altText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.getTextInNode(content) ?: ""
            val imageUrl = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(content) ?: ""
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = altText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .widthIn(min = 120.dp)
                        .heightIn(min = 60.dp, max = 400.dp),
                )
            }
        }

        // ────── 行内数学公式 (INLINE_MATH) ──────
        GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            MathInline(
                latex = formula,
                modifier = modifier.padding(horizontal = 1.dp),
            )
        }

        // ────── 块级数学公式 (BLOCK_MATH) ──────
        GFMElementTypes.BLOCK_MATH -> {
            val formula = node.getTextInNode(content)
            MathBlock(
                latex = formula,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }

        // Inline code
        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                modifier = modifier,
            )
        }

        // Indented code block (not fenced)
        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(content)
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                modifier = modifier,
            )
        }

        // Fenced code block
        MarkdownElementTypes.CODE_FENCE -> {
            val contentStartIndex =
                node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            if (contentStartIndex != -1) {
                val eolElement = node.children.subList(0, contentStartIndex)
                    .findLast { it.type == MarkdownTokenTypes.EOL }
                if (eolElement != null) {
                    val codeContentStartOffset = eolElement.endOffset
                    val codeContentEndOffset =
                        node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset
                    if (codeContentEndOffset != null) {
                        val code = content.substring(codeContentStartOffset, codeContentEndOffset)
                            .trimIndent()
                        val language = node.findChildOfTypeRecursive(MarkdownTokenTypes.FENCE_LANG)
                            ?.getTextInNode(content) ?: "plaintext"
                        val hasEnd =
                            node.findChildOfTypeRecursive(MarkdownTokenTypes.CODE_FENCE_END) != null

                        HighlightCodeBlock(
                            code = code,
                            language = language,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .fillMaxWidth(),
                            completeCodeBlock = hasEnd,
                        )
                    }
                }
            }
        }

        // Plain text token
        MarkdownTokenTypes.TEXT -> {
            Text(text = node.getTextInNode(content), modifier = modifier)
        }

        // ────── HTML block → SimpleHtmlBlock ──────
        MarkdownElementTypes.HTML_BLOCK -> {
            val text = node.getTextInNode(content)
            SimpleHtmlBlock(html = text, modifier = modifier)
        }

        // Fallback: recurse into children
        else -> {
            node.children.forEach { child ->
                MarkdownNode(node = child, content = content, modifier = modifier)
            }
        }
    }
}

// ────────── Paragraph ──────────
@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    trim: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme

    val annotatedString = remember(content) {
        buildAnnotatedString {
            node.children.forEach { child ->
                appendMarkdownNodeContent(
                    node = child,
                    content = content,
                    trim = trim,
                    primaryColor = colorScheme.primary,
                    secondaryContainerColor = colorScheme.secondaryContainer,
                )
            }
        }
    }
    Text(
        text = annotatedString,
        softWrap = true,
        overflow = TextOverflow.Visible,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

// ────────── Inline content builder ──────────
private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryContainerColor: androidx.compose.ui.graphics.Color,
) {
    when {
        node.type == MarkdownTokenTypes.BLOCK_QUOTE -> {}

        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getTextInNode(content)
            pushStringAnnotation(tag = "URL", annotation = link)
            withStyle(SpanStyle(color = primaryColor, fontStyle = FontStyle.Italic, textDecoration = TextDecoration.Underline)) {
                append(link)
            }
            pop()
        }

        node is LeafASTNode -> {
            val text = node.getTextInNode(content).let {
                if (trim) it.trim() else it
            }.replace(BREAK_LINE_REGEX, "\n")
            append(text)
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 1).forEach {
                    appendMarkdownNodeContent(it, content, trim, primaryColor, secondaryContainerColor)
                }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 2).forEach {
                    appendMarkdownNodeContent(it, content, trim, primaryColor, secondaryContainerColor)
                }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children.trim(GFMTokenTypes.TILDE, 2).forEach {
                    appendMarkdownNodeContent(it, content, trim, primaryColor, secondaryContainerColor)
                }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(content) ?: ""
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.getTextInNode(content)?.trim { it == '[' || it == ']' } ?: linkDest
            pushStringAnnotation(tag = "URL", annotation = linkDest)
            withStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) {
                append(linkText)
            }
            pop()
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            val links = node.children.trim(MarkdownTokenTypes.LT, 1).trim(MarkdownTokenTypes.GT, 1)
            links.forEach { link ->
                val linkUrl = link.getTextInNode(content)
                pushStringAnnotation(tag = "URL", annotation = linkUrl)
                withStyle(SpanStyle(color = primaryColor, fontStyle = FontStyle.Italic, textDecoration = TextDecoration.Underline)) {
                    append(linkUrl)
                }
                pop()
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.95.em,
                    background = secondaryContainerColor.copy(alpha = 0.2f),
                )
            ) {
                append(code)
            }
        }

        // 行内数学公式 (AnnotatedString 内 fallback 为等宽文本)
        node.type == GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.95.em,
                )
            ) {
                append(formula)
            }
        }

        else -> {
            node.children.forEach {
                appendMarkdownNodeContent(it, content, trim, primaryColor, secondaryContainerColor)
            }
        }
    }
}

// ────────── Lists ──────────
@Composable
private fun UnorderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    level: Int = 0,
) {
    val bulletStyle = when (level % 3) {
        0 -> "• "
        1 -> "◦ "
        else -> "▪ "
    }
    Column(modifier = modifier.padding(start = (level * 8).dp)) {
        node.children.forEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                ListItemNode(
                    node = child,
                    content = content,
                    bulletText = bulletStyle,
                    level = level,
                )
            }
        }
    }
}

@Composable
private fun OrderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    level: Int = 0,
) {
    Column(modifier.padding(start = (level * 8).dp)) {
        var index = 1
        node.children.forEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val numberText = child.findChildOfTypeRecursive(MarkdownTokenTypes.LIST_NUMBER)
                    ?.getTextInNode(content) ?: "$index. "
                ListItemNode(
                    node = child,
                    content = content,
                    bulletText = numberText,
                    level = level,
                )
                index++
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListItemNode(
    node: ASTNode,
    content: String,
    bulletText: String,
    level: Int,
) {
    Column {
        val (directContent, nestedLists) = separateContentAndLists(node)
        if (directContent.isNotEmpty()) {
            Row {
                Text(
                    text = bulletText,
                    modifier = Modifier.alignByBaseline(),
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    directContent.forEach { contentChild ->
                        MarkdownNode(
                            node = contentChild,
                            content = content,
                            listLevel = level,
                        )
                    }
                }
            }
        }
        nestedLists.forEach { nestedList ->
            MarkdownNode(
                node = nestedList,
                content = content,
                listLevel = level + 1,
            )
        }
    }
}

private fun separateContentAndLists(
    listItemNode: ASTNode,
): Pair<List<ASTNode>, List<ASTNode>> {
    val directContent = mutableListOf<ASTNode>()
    val nestedLists = mutableListOf<ASTNode>()
    listItemNode.children.forEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                nestedLists.add(child)
            }
            else -> {
                directContent.add(child)
            }
        }
    }
    return directContent to nestedLists
}

// ────────── AST utilities ──────────
private fun ASTNode.getTextInNode(text: String): String {
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.findChildOfTypeRecursive(vararg types: IElementType): ASTNode? {
    if (this.type in types) return this
    for (child in children) {
        val result = child.findChildOfTypeRecursive(*types)
        if (result != null) return result
    }
    return null
}

private fun List<ASTNode>.trim(type: IElementType, size: Int): List<ASTNode> {
    if (this.isEmpty() || size <= 0) return this
    var start = 0
    var end = this.size
    var trimmed = 0
    while (start < end && trimmed < size && this[start].type == type) {
        start++; trimmed++
    }
    trimmed = 0
    while (end > start && trimmed < size && this[end - 1].type == type) {
        end--; trimmed++
    }
    return this.subList(start, end)
}
