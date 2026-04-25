package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// ─────────────────────────────────────────────────────────────────────────────
//  HtmlBlock – renders an arbitrary HTML fragment with Jsoup
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses and renders [html] using Jsoup, supporting a rich subset of HTML elements.
 *
 * Supported tags:
 *  Block: `p`, `h1–h6`, `ul`/`ol`+`li`, `details`+`summary`, `blockquote`,
 *         `pre`+`code`, `div`, `table`+`thead`+`tbody`+`tr`+`th`+`td`
 *  Inline: `b`/`strong`, `i`/`em`, `u`, `s`/`del`, `a`, `code`, `br`, `img`,
 *          `span`, `font`, `progress`
 */
@Composable
fun HtmlBlock(html: String, modifier: Modifier = Modifier) {
    val document = remember(html) { Jsoup.parseBodyFragment(html) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        document.body().childNodes().forEach { node ->
            HtmlNode(node = node)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Recursive node renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HtmlNode(
    node: Node,
    inheritedStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    inheritedColor: Color? = null,
    isInlineLambda: (@Composable () -> Unit)? = null,
) {
    when (node) {
        is TextNode -> {
            val text = node.text()
            if (text.isBlank()) return
            Text(
                text  = text,
                style = inheritedStyle,
                color = inheritedColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }

        is Element  -> HtmlElement(element = node, inheritedStyle = inheritedStyle, inheritedColor = inheritedColor)
        else        -> {}
    }
}

@Composable
private fun HtmlElement(
    element: Element,
    inheritedStyle: TextStyle,
    inheritedColor: Color?,
) {
    val tag      = element.tagName().lowercase()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary   = MaterialTheme.colorScheme.primary

    // Parse inline CSS colour
    val styleColor = parseStyleColor(element.attr("style"))
    val textColor  = styleColor ?: inheritedColor ?: onSurface
    val isBold     = isStyleBold(element.attr("style"))

    when (tag) {
        // ── Block elements ────────────────────────────────────────────────────
        "p", "div" -> {
            HtmlParagraph(
                element = element,
                inheritedStyle = inheritedStyle,
                textColor = textColor,
                primary = primary,
            )
        }

        "h1" -> HeadingText(element, textColor, primary, MaterialTheme.typography.headlineLarge)
        "h2" -> HeadingText(element, textColor, primary, MaterialTheme.typography.headlineMedium)
        "h3" -> HeadingText(element, textColor, primary, MaterialTheme.typography.headlineSmall)
        "h4" -> HeadingText(element, textColor, primary, MaterialTheme.typography.titleLarge)
        "h5" -> HeadingText(element, textColor, primary, MaterialTheme.typography.titleMedium)
        "h6" -> HeadingText(element, textColor, primary, MaterialTheme.typography.titleSmall)

        "ul" -> HtmlList(element, ordered = false, textColor = textColor, primary = primary, style = inheritedStyle)
        "ol" -> HtmlList(element, ordered = true,  textColor = textColor, primary = primary, style = inheritedStyle)

        "blockquote" -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .heightIn(min = 16.dp)
                        .background(primary, MaterialTheme.shapes.small)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    element.childNodes().forEach { HtmlNode(it, inheritedStyle = inheritedStyle,
                        inheritedColor = textColor.copy(alpha = 0.8f)) }
                }
            }
        }

        "details" -> {
            HtmlDetails(element = element, style = inheritedStyle, textColor = textColor, primary = primary)
        }

        "pre" -> {
            val codeEl  = element.selectFirst("code")
            val rawCode = codeEl?.text() ?: element.text()
            val lang    = codeEl?.className()
                ?.splitToSequence(',', ' ')
                ?.firstOrNull { it.startsWith("language-") }
                ?.removePrefix("language-")
                ?: ""
            CodeBlock(language = lang, code = rawCode)
        }

        "table" -> {
            HtmlTable(element)
        }

        "br" -> Spacer(Modifier.height(4.dp))

        "hr" -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        "img" -> {
            val src = element.attr("src").ifBlank { element.attr("data-src") }
            val alt = element.attr("alt")
            if (src.isNotBlank()) {
                ZoomableAsyncImage(
                    url                = src,
                    contentDescription = alt.ifBlank { null },
                    modifier           = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }

        "progress" -> {
            val value = element.attr("value").toFloatOrNull() ?: 0f
            val max   = element.attr("max").toFloatOrNull()?.takeIf { it > 0f } ?: 100f
            LinearProgressIndicator(
                progress = { value / max },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        // ── Inline elements (rendered as annotated Text) ────────────────────
        "span", "font" -> {
            val spanColor = styleColor ?: parseFontColor(element.attr("color")) ?: textColor
            val text = buildHtmlAnnotatedString(element, spanColor, primary)
            Text(
                text = text,
                style = inheritedStyle,
                modifier = Modifier.clickOnHtmlLinks(text, LocalUriHandler.current),
            )
        }

        "code" -> {
            val codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text  = buildAnnotatedString { withStyle(codeStyle) { append(element.text()) } },
                style = inheritedStyle,
            )
        }

        "b", "strong" -> {
            Text(
                text  = buildHtmlAnnotatedString(element, textColor, primary),
                style = inheritedStyle,
                fontWeight = FontWeight.Bold,
            )
        }

        "i", "em" -> {
            Text(
                text      = buildHtmlAnnotatedString(element, textColor, primary),
                style     = inheritedStyle,
                fontStyle = FontStyle.Italic,
            )
        }

        "u" -> {
            Text(
                text            = buildHtmlAnnotatedString(element, textColor, primary),
                style           = inheritedStyle,
                textDecoration  = TextDecoration.Underline,
            )
        }

        "s", "del", "strike" -> {
            Text(
                text           = buildHtmlAnnotatedString(element, textColor, primary),
                style          = inheritedStyle,
                textDecoration = TextDecoration.LineThrough,
            )
        }

        "a" -> {
            val href = element.attr("href")
            val uriHandler = LocalUriHandler.current
            Text(
                text     = buildHtmlAnnotatedString(element, primary, primary),
                style    = inheritedStyle.copy(
                    color          = primary,
                    textDecoration = TextDecoration.Underline,
                ),
                modifier = Modifier.clickable(enabled = href.isNotBlank()) {
                    runCatching { uriHandler.openUri(href) }
                },
            )
        }

        // Recurse for unknown/wrapper elements
        else -> element.childNodes().forEach {
            HtmlNode(it, inheritedStyle = inheritedStyle, inheritedColor = textColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeadingText(element: Element, textColor: Color, primary: Color, style: TextStyle) {
    val text = buildHtmlAnnotatedString(element, textColor, primary)
    val uriHandler = LocalUriHandler.current
    Text(
        text  = text,
        style = style,
        color = textColor,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 2.dp)
            .clickOnHtmlLinks(text, uriHandler),
    )
}

@Composable
private fun HtmlParagraph(
    element: Element,
    inheritedStyle: TextStyle,
    textColor: Color,
    primary: Color,
) {
    val uriHandler = LocalUriHandler.current
    val directImages = remember(element) {
        element.childNodes().mapNotNull { child ->
            (child as? Element)?.takeIf { it.tagName().equals("img", ignoreCase = true) }
        }
    }
    val text = buildHtmlAnnotatedString(element, textColor, primary)
    val hasOnlyImages = remember(element, directImages) {
        directImages.isNotEmpty() &&
            element.childNodes().all { child ->
                when (child) {
                    is TextNode -> child.text().isBlank()
                    is Element -> child.tagName().equals("img", ignoreCase = true) ||
                        child.tagName().equals("br", ignoreCase = true)
                    else -> true
                }
            }
    }

    if (hasOnlyImages) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            directImages.forEach { image ->
                val src = image.attr("src").ifBlank { image.attr("data-src") }
                val alt = image.attr("alt")
                if (src.isNotBlank()) {
                    ZoomableAsyncImage(
                        url = src,
                        contentDescription = alt.ifBlank { null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    if (text.text.isBlank()) return

    Text(
        text = text,
        style = inheritedStyle,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickOnHtmlLinks(text, uriHandler),
    )
}

@Composable
private fun HtmlList(
    element: Element,
    ordered: Boolean,
    textColor: Color,
    primary: Color,
    style: TextStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var idx = 1
        element.children().filter { it.tagName() == "li" }.forEach { li ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = if (ordered) "${idx++}.  " else "•  ",
                    style = style,
                    color = primary,
                )
                Column {
                    li.childNodes().forEach { HtmlNode(it, inheritedStyle = style, inheritedColor = textColor) }
                }
            }
        }
    }
}

@Composable
private fun HtmlDetails(element: Element, style: TextStyle, textColor: Color, primary: Color) {
    val summary = element.selectFirst("summary")?.text() ?: "Details"
    var expanded by rememberSaveable(element.html().hashCode()) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = (if (expanded) "▾ " else "▸ ") + summary,
            style    = style,
            color    = primary,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
        )
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                element.childNodes()
                    .filter { it !is Element || (it as Element).tagName() != "summary" }
                    .forEach { HtmlNode(it, inheritedStyle = style, inheritedColor = textColor) }
            }
        }
    }
}

@Composable
private fun HtmlTable(table: Element) {
    val allRows = table.select("tr")
    if (allRows.isEmpty()) return

    var headerCells = table.select("thead tr").firstOrNull()
        ?.select("th, td")
        ?.map { it.html() }
        .orEmpty()

    val bodyRows = when {
        table.select("tbody tr").isNotEmpty() -> table.select("tbody tr")
        headerCells.isEmpty() && allRows.firstOrNull()?.select("th")?.isNotEmpty() == true ->
            allRows.drop(1)
        else -> allRows
    }

    val firstRow = allRows.firstOrNull()
    if (headerCells.isEmpty() && firstRow?.select("th")?.isNotEmpty() == true) {
        headerCells = firstRow.select("th, td").map { it.html() }
    }

    val rows = bodyRows.map { row ->
        row.select("th, td").map { it.html() }
    }
    val colCount = maxOf(headerCells.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        if (headerCells.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                headerCells.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        HtmlBlock(html = cell)
                    }
                }
                // fill missing columns
                repeat((colCount - headerCells.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
            HorizontalDivider()
        }
        // Rows
        rows.forEach { cols ->
            Row(modifier = Modifier.fillMaxWidth()) {
                cols.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        HtmlBlock(html = cell)
                    }
                }
                repeat((colCount - cols.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AnnotatedString builder for inline HTML trees
// ─────────────────────────────────────────────────────────────────────────────

private fun buildHtmlAnnotatedString(
    element: Element,
    baseColor: Color,
    linkColor: Color,
) = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is TextNode -> withStyle(SpanStyle(color = baseColor)) { append(node.text()) }
            is Element  -> {
                val tg = node.tagName().lowercase()
                val nodeColor = parseStyleColor(node.attr("style"))
                    ?: parseFontColor(node.attr("color"))
                    ?: baseColor
                when (tg) {
                    "b", "strong" -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = nodeColor))
                        node.childNodes().forEach(::appendNode)
                        pop()
                    }
                    "i", "em" -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = nodeColor))
                        node.childNodes().forEach(::appendNode)
                        pop()
                    }
                    "u" -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = nodeColor))
                        node.childNodes().forEach(::appendNode)
                        pop()
                    }
                    "s", "del", "strike" -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = nodeColor))
                        node.childNodes().forEach(::appendNode)
                        pop()
                    }
                    "code" -> {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = nodeColor))
                        append(node.text())
                        pop()
                    }
                    "a" -> {
                        val url = node.attr("href")
                        pushStringAnnotation("URL", url)
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        node.childNodes().forEach(::appendNode)
                        pop(); pop()
                    }
                    "span", "font" -> {
                        pushStyle(SpanStyle(color = nodeColor))
                        node.childNodes().forEach(::appendNode)
                        pop()
                    }
                    "br" -> append("\n")
                    else -> node.childNodes().forEach(::appendNode)
                }
            }
        }
    }
    element.childNodes().forEach(::appendNode)
}

@Composable
private fun Modifier.clickOnHtmlLinks(
    annotated: androidx.compose.ui.text.AnnotatedString,
    uriHandler: androidx.compose.ui.platform.UriHandler,
): Modifier {
    val urls = remember(annotated) { annotated.getStringAnnotations("URL", 0, annotated.length) }
    if (urls.isEmpty()) return this
    return clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
    ) {
        urls.firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CSS helpers
// ─────────────────────────────────────────────────────────────────────────────

private val CSS_COLOR_RE = Regex("""color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)

private fun parseStyleColor(style: String): Color? {
    val value = CSS_COLOR_RE.find(style)?.groupValues?.getOrNull(1)?.trim() ?: return null
    return parseCssColor(value)
}

private fun parseFontColor(attr: String): Color? {
    if (attr.isBlank()) return null
    return parseCssColor(attr.trim())
}

private fun parseCssColor(value: String): Color? = try {
    when {
        value.startsWith("#") -> Color(android.graphics.Color.parseColor(value))
        value.startsWith("rgb") -> {
            val nums = Regex("""\d+""").findAll(value).map { it.value.toInt() }.toList()
            if (nums.size >= 3) Color(nums[0], nums[1], nums[2]) else null
        }
        else -> Color(android.graphics.Color.parseColor(value))
    }
} catch (_: Exception) { null }

private fun isStyleBold(style: String): Boolean =
    style.contains("font-weight\\s*:\\s*(bold|700|800|900)".toRegex(RegexOption.IGNORE_CASE))
