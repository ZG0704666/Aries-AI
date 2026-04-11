package com.ai.phoneagent.ui.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val ANNOTATION_KEY_URL = "url"

/**
 * Parses an HTML string and renders it as Compose UI.
 */
@Composable
fun SimpleHtmlBlock(html: String, modifier: Modifier = Modifier) {
    val parseResult = runCatching { Jsoup.parse(html) }
    val document = parseResult.getOrNull() ?: run {
        Text(text = html, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        processElementNodes(document.body().childNodes())
    }
}

@Composable
private fun processElementNodes(nodes: List<Node>) {
    for (node in nodes) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    Text(text = text)
                }
            }
            is Element -> {
                processElement(node)
            }
        }
    }
}

@Composable
private fun processElement(element: Element) {
    val tag = element.tagName().lowercase()
    when (tag) {
        "p" -> renderParagraph(element)
        "h1", "h2", "h3", "h4", "h5", "h6" -> renderHeading(element, tag)
        "ul" -> renderUnorderedList(element)
        "ol" -> renderOrderedList(element)
        "details" -> renderDetails(element)
        "img" -> renderImage(element)
        "progress" -> renderProgress(element)
        "table" -> renderTable(element)
        "br" -> Spacer(modifier = Modifier.height(8.dp))
        "div" -> renderDiv(element)
        "li" -> renderListItem(element)
        else -> {
            // Default: render children
            processElementNodes(element.childNodes())
        }
    }
}

@Composable
private fun renderParagraph(element: Element) {
    val uriHandler = LocalUriHandler.current
    val inlineStyle = parseInlineStyle(element.attr("style"))
    val annotatedString = buildAnnotatedString {
        processInlineNodes(element.childNodes(), inlineStyle)
    }
    ClickableText(
        text = annotatedString,
        modifier = Modifier.padding(vertical = 4.dp),
        onClick = { offset ->
            annotatedString.getStringAnnotations(ANNOTATION_KEY_URL, offset, offset)
                .firstOrNull()?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
        }
    )
}

@Composable
private fun renderHeading(element: Element, tag: String) {
    val uriHandler = LocalUriHandler.current
    val level = tag.substring(1).toIntOrNull() ?: 1
    val typography = when (level) {
        1 -> MaterialTheme.typography.displayLarge
        2 -> MaterialTheme.typography.displayMedium
        3 -> MaterialTheme.typography.headlineLarge
        4 -> MaterialTheme.typography.headlineMedium
        5 -> MaterialTheme.typography.titleLarge
        6 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyLarge
    }

    val inlineStyle = parseInlineStyle(element.attr("style"))
    val annotatedString = buildAnnotatedString {
        processInlineNodes(element.childNodes(), inlineStyle)
    }
    ClickableText(
        text = annotatedString,
        style = typography,
        modifier = Modifier.padding(vertical = 8.dp),
        onClick = { offset ->
            annotatedString.getStringAnnotations(ANNOTATION_KEY_URL, offset, offset)
                .firstOrNull()?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
        }
    )
}

@Composable
private fun renderUnorderedList(element: Element) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        element.children().forEach { li ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(text = "\u2022 ", modifier = Modifier.padding(end = 4.dp))
                processElementNodes(li.childNodes())
            }
        }
    }
}

@Composable
private fun renderOrderedList(element: Element) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        element.children().forEachIndexed { index, li ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(text = "${index + 1}. ", modifier = Modifier.padding(end = 4.dp))
                processElementNodes(li.childNodes())
            }
        }
    }
}

@Composable
private fun renderListItem(element: Element) {
    processElementNodes(element.childNodes())
}

@Composable
private fun renderDetails(element: Element) {
    var expanded by remember { mutableStateOf(false) }
    val summaryElement = element.children().firstOrNull { it.tagName().lowercase() == "summary" }
    val summaryText = summaryElement?.wholeText()?.trim() ?: "Details"

    Column {
        Text(
            text = if (expanded) "\u25BC $summaryText" else "\u25B6 $summaryText",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp)
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                val children = element.children().filter { it.tagName().lowercase() != "summary" }
                children.forEach { child ->
                    processElement(child)
                }
                // Also process non-element children
                element.childNodes().filterIsInstance<TextNode>().forEach { textNode ->
                    val text = textNode.text()
                    if (text.isNotBlank()) {
                        Text(text = text)
                    }
                }
            }
        }
    }
}

@Composable
private fun renderImage(element: Element) {
    val alt = element.attr("alt").takeIf { it.isNotBlank() } ?: "Image"
    Text(
        text = "[Image: $alt]",
        style = MaterialTheme.typography.bodySmall.copy(color = LocalContentColor.current.copy(alpha = 0.6f)),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun renderProgress(element: Element) {
    val value = element.attr("value").toFloatOrNull() ?: 0f
    val max = element.attr("max").toFloatOrNull() ?: 100f
    val progress = if (max > 0) value / max else 0f
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun renderTable(element: Element) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        val thead = element.children().firstOrNull { it.tagName().lowercase() == "thead" }
        val tbody = element.children().firstOrNull { it.tagName().lowercase() == "tbody" }
        val directRows = element.children().filter { it.tagName().lowercase() == "tr" }

        val headerRows = thead?.children()?.filter { it.tagName().lowercase() == "tr" } ?: emptyList()
        val bodyRows = tbody?.children()?.filter { it.tagName().lowercase() == "tr" }
            ?: directRows

        headerRows.forEach { row ->
            renderTableRow(row, isHeader = true)
        }
        bodyRows.forEach { row ->
            renderTableRow(row, isHeader = false)
        }
    }
}

@Composable
private fun renderTableRow(row: Element, isHeader: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val cells = row.children().filter { it.tagName().lowercase() in listOf("td", "th") }
        cells.forEach { cell ->
            val cellText = cell.wholeText().trim()
            Text(
                text = cellText,
                style = if (isHeader) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun renderDiv(element: Element) {
    Column {
        processElementNodes(element.childNodes())
    }
}

/**
 * Processes inline nodes and appends to an AnnotatedString.Builder.
 */
private fun AnnotatedString.Builder.processInlineNodes(
    nodes: List<Node>,
    parentStyle: SpanStyle? = null
) {
    for (node in nodes) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (parentStyle != null) {
                    withStyle(style = parentStyle) {
                        append(text)
                    }
                } else {
                    append(text)
                }
            }
            is Element -> {
                processInlineElement(node, parentStyle)
            }
        }
    }
}

private fun AnnotatedString.Builder.processInlineElement(
    element: Element,
    parentStyle: SpanStyle? = null
) {
    val tag = element.tagName().lowercase()
    val elementStyle = parseInlineStyle(element.attr("style"))
    val combinedStyle = combineSpanStyles(parentStyle, elementStyle)

    when (tag) {
        "b", "strong" -> {
            val boldStyle = (combinedStyle ?: SpanStyle()).copy(fontWeight = FontWeight.Bold)
            withStyle(style = boldStyle) {
                processInlineNodes(element.childNodes(), boldStyle)
            }
        }
        "i", "em" -> {
            val italicStyle = (combinedStyle ?: SpanStyle()).copy(fontStyle = FontStyle.Italic)
            withStyle(style = italicStyle) {
                processInlineNodes(element.childNodes(), italicStyle)
            }
        }
        "u" -> {
            val underlineStyle = (combinedStyle ?: SpanStyle()).copy(textDecoration = TextDecoration.Underline)
            withStyle(style = underlineStyle) {
                processInlineNodes(element.childNodes(), underlineStyle)
            }
        }
        "code" -> {
            val codeStyle = (combinedStyle ?: SpanStyle()).copy(
                fontFamily = FontFamily.Monospace,
                background = Color.LightGray.copy(alpha = 0.3f)
            )
            withStyle(style = codeStyle) {
                processInlineNodes(element.childNodes(), codeStyle)
            }
        }
        "a" -> {
            val href = element.attr("href")
            val linkStyle = (combinedStyle ?: SpanStyle()).copy(
                color = Color.Blue,
                textDecoration = TextDecoration.Underline
            )
            withStyle(style = linkStyle) {
                if (href.isNotBlank()) {
                    pushStringAnnotation(tag = ANNOTATION_KEY_URL, annotation = href)
                }
                processInlineNodes(element.childNodes(), linkStyle)
                if (href.isNotBlank()) {
                    pop()
                }
            }
        }
        "span" -> {
            withStyle(style = combinedStyle ?: SpanStyle()) {
                processInlineNodes(element.childNodes(), combinedStyle)
            }
        }
        "font" -> {
            val colorAttr = element.attr("color")
            val fontColor = parseColor(colorAttr)
            val fontStyle = if (fontColor != null) {
                (combinedStyle ?: SpanStyle()).copy(color = fontColor)
            } else {
                combinedStyle ?: SpanStyle()
            }
            withStyle(style = fontStyle) {
                processInlineNodes(element.childNodes(), fontStyle)
            }
        }
        "br" -> {
            append("\n")
        }
        "img" -> {
            val alt = element.attr("alt").takeIf { it.isNotBlank() } ?: "Image"
            append("[Image: $alt]")
        }
        else -> {
            // Default: process children
            processInlineNodes(element.childNodes(), combinedStyle)
        }
    }
}

private fun combineSpanStyles(base: SpanStyle?, overlay: SpanStyle?): SpanStyle? {
    if (base == null) return overlay
    if (overlay == null) return base
    return base.merge(overlay)
}

/**
 * Parses inline CSS style string into a SpanStyle.
 */
fun parseInlineStyle(style: String): SpanStyle? {
    if (style.isBlank()) return null

    var color: Color? = null
    var fontWeight: FontWeight? = null

    val declarations = style.split(";")
    for (declaration in declarations) {
        val parts = declaration.split(":", limit = 2)
        if (parts.size != 2) continue
        val property = parts[0].trim().lowercase()
        val value = parts[1].trim()

        when (property) {
            "color" -> color = parseColor(value)
            "font-weight" -> fontWeight = parseFontWeight(value)
        }
    }

    if (color == null && fontWeight == null) return null

    return SpanStyle(
        color = color ?: Color.Unspecified,
        fontWeight = fontWeight
    )
}

/**
 * Parses a CSS color string into a Compose Color.
 */
fun parseColor(colorString: String): Color? {
    if (colorString.isBlank()) return null

    val trimmed = colorString.trim().lowercase()

    // Hex colors
    if (trimmed.startsWith("#")) {
        return parseHexColor(trimmed)
    }

    // rgb() or rgba()
    if (trimmed.startsWith("rgb(") || trimmed.startsWith("rgba(")) {
        return parseRgbColor(trimmed)
    }

    // Named colors
    return namedColors[trimmed]
}

private fun parseHexColor(hex: String): Color? {
    val hexValue = hex.substring(1)
    return try {
        val parsed = when (hexValue.length) {
            3 -> {
                // #RGB -> #RRGGBB
                val r = hexValue[0].digitToInt(16)
                val g = hexValue[1].digitToInt(16)
                val b = hexValue[2].digitToInt(16)
                (r shl 4 and 0xFF) shl 16 or (g shl 4 and 0xFF) shl 8 or (b shl 4 and 0xFF)
            }
            6 -> {
                hexValue.toInt(16)
            }
            8 -> {
                // #RRGGBBAA -> Android expects #AARRGGBB, so we need to rearrange
                val rr = hexValue.substring(0, 2).toInt(16)
                val gg = hexValue.substring(2, 4).toInt(16)
                val bb = hexValue.substring(4, 6).toInt(16)
                val aa = hexValue.substring(6, 8).toInt(16)
                (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
            else -> return null
        }
        Color(0xFF000000L or parsed.toLong())
    } catch (e: NumberFormatException) {
        null
    }
}

private fun parseRgbColor(rgb: String): Color? {
    return try {
        val prefix = if (rgb.startsWith("rgba(")) "rgba(" else "rgb("
        val suffix = ")"
        if (!rgb.startsWith(prefix) || !rgb.endsWith(suffix)) return null

        val content = rgb.substring(prefix.length, rgb.length - suffix.length)
        val parts = content.split(",").map { it.trim() }

        if (parts.size < 3 || parts.size > 4) return null

        val r = parts[0].toIntOrNull() ?: return null
        val g = parts[1].toIntOrNull() ?: return null
        val b = parts[2].toIntOrNull() ?: return null
        val a = if (parts.size == 4) {
            (parts[3].toFloatOrNull() ?: 1f).coerceIn(0f, 1f)
        } else {
            1f
        }

        Color(
            red = r / 255f,
            green = g / 255f,
            blue = b / 255f,
            alpha = a
        )
    } catch (e: Exception) {
        null
    }
}

private val namedColors = mapOf(
    "red" to Color.Red,
    "green" to Color.Green,
    "blue" to Color.Blue,
    "black" to Color.Black,
    "white" to Color.White,
    "gray" to Color.Gray,
    "grey" to Color.Gray,
    "yellow" to Color.Yellow,
    "cyan" to Color.Cyan,
    "magenta" to Color.Magenta,
    "orange" to Color(0xFFFFA500),
    "purple" to Color(0xFF800080),
    "brown" to Color(0xFFA52A2A),
    "pink" to Color(0xFFFFC0CB),
)

/**
 * Parses a CSS font-weight value into a Compose FontWeight.
 */
fun parseFontWeight(weightString: String): FontWeight? {
    return when (weightString.trim().lowercase()) {
        "normal", "400" -> FontWeight.Normal
        "bold", "700" -> FontWeight.Bold
        "100" -> FontWeight.Thin
        "200" -> FontWeight.ExtraLight
        "300" -> FontWeight.Light
        "500" -> FontWeight.Medium
        "600" -> FontWeight.SemiBold
        "800" -> FontWeight.ExtraBold
        "900" -> FontWeight.Black
        else -> weightString.toIntOrNull()?.let {
            when {
                it <= 100 -> FontWeight.Thin
                it <= 200 -> FontWeight.ExtraLight
                it <= 300 -> FontWeight.Light
                it <= 400 -> FontWeight.Normal
                it <= 500 -> FontWeight.Medium
                it <= 600 -> FontWeight.SemiBold
                it <= 700 -> FontWeight.Bold
                it <= 800 -> FontWeight.ExtraBold
                else -> FontWeight.Black
            }
        }
    }
}
