package com.ai.phoneagent.ui.components.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  Atom One Dark colour palette  (hex → Compose Color)
// ─────────────────────────────────────────────────────────────────────────────

object AtomOneDarkPalette {
    val background  = Color(0xFF282c34)
    val foreground  = Color(0xFFabb2bf)

    val palette: Map<String, Color> = mapOf(
        "comment"          to Color(0xFF5c6370),
        "prolog"           to Color(0xFF5c6370),
        "doctype"          to Color(0xFF5c6370),
        "cdata"            to Color(0xFF5c6370),

        "punctuation"      to Color(0xFFabb2bf),
        "namespace"        to Color(0xFFe06c75),

        "property"         to Color(0xFFe06c75),
        "tag"              to Color(0xFFe06c75),
        "boolean"          to Color(0xFFd19a66),
        "number"           to Color(0xFFd19a66),
        "constant"         to Color(0xFFd19a66),
        "symbol"           to Color(0xFFd19a66),
        "deleted"          to Color(0xFFe06c75),

        "selector"         to Color(0xFF98c379),
        "attr-name"        to Color(0xFFe06c75),
        "string"           to Color(0xFF98c379),
        "char"             to Color(0xFF98c379),
        "builtin"          to Color(0xFF56b6c2),
        "inserted"         to Color(0xFF98c379),

        "operator"         to Color(0xFF56b6c2),
        "entity"           to Color(0xFF56b6c2),
        "url"              to Color(0xFF56b6c2),

        "atrule"           to Color(0xFFc678dd),
        "attr-value"       to Color(0xFF98c379),
        "keyword"          to Color(0xFFc678dd),

        "function"         to Color(0xFF61afef),
        "class-name"       to Color(0xFFe5c07b),

        "regex"            to Color(0xFFbe5046),
        "important"        to Color(0xFFbe5046),
        "variable"         to Color(0xFFe06c75),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Atom One Light colour palette
// ─────────────────────────────────────────────────────────────────────────────

object AtomOneLightPalette {
    val background  = Color(0xFFfafafa)
    val foreground  = Color(0xFF383a42)

    val palette: Map<String, Color> = mapOf(
        "comment"          to Color(0xFFa0a1a7),
        "prolog"           to Color(0xFFa0a1a7),
        "doctype"          to Color(0xFFa0a1a7),
        "cdata"            to Color(0xFFa0a1a7),

        "punctuation"      to Color(0xFF383a42),

        "property"         to Color(0xFFe45649),
        "tag"              to Color(0xFFe45649),
        "boolean"          to Color(0xFFb76b00),
        "number"           to Color(0xFFb76b00),
        "constant"         to Color(0xFFb76b00),
        "symbol"           to Color(0xFFb76b00),
        "deleted"          to Color(0xFFe45649),

        "selector"         to Color(0xFF50a14f),
        "attr-name"        to Color(0xFFe45649),
        "string"           to Color(0xFF50a14f),
        "char"             to Color(0xFF50a14f),
        "builtin"          to Color(0xFF0184bc),
        "inserted"         to Color(0xFF50a14f),

        "operator"         to Color(0xFF0184bc),
        "entity"           to Color(0xFF0184bc),
        "url"              to Color(0xFF0184bc),

        "atrule"           to Color(0xFFa626a4),
        "attr-value"       to Color(0xFF50a14f),
        "keyword"          to Color(0xFFa626a4),

        "function"         to Color(0xFF4078f2),
        "class-name"       to Color(0xFFc18401),

        "regex"            to Color(0xFF986801),
        "important"        to Color(0xFF986801),
        "variable"         to Color(0xFFe45649),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Composable renderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a list of [HighlightToken]s as a syntax-coloured [Text], with optional
 * line numbers and soft-wrap toggle.
 *
 * Wrap this inside a [SelectionContainer] externally if copy is needed.
 */
@Composable
fun HighlightText(
    tokens: List<HighlightToken>,
    isDark: Boolean,
    lineNumbers: Boolean = false,
    softWrap: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = if (isDark) AtomOneDarkPalette.palette else AtomOneLightPalette.palette
    val defaultColor = if (isDark) AtomOneDarkPalette.foreground else AtomOneLightPalette.foreground

    // Split tokens into per-line lists
    val lines: List<List<HighlightToken>> = remember(tokens) { splitIntoLines(tokens) }

    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
    )

    if (lineNumbers) {
        val lineNumWidth = lines.size.toString().length
        androidx.compose.foundation.layout.Column(modifier = modifier) {
            lines.forEachIndexed { idx, lineTokens ->
                androidx.compose.foundation.layout.Row {
                    Text(
                        text = (idx + 1).toString().padStart(lineNumWidth) + "│ ",
                        style = codeStyle,
                        color = defaultColor.copy(alpha = 0.45f),
                    )
                    Text(
                        text  = buildHighlightedString(lineTokens, palette, defaultColor),
                        style = codeStyle,
                        softWrap = softWrap,
                        modifier = if (!softWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                    )
                }
            }
        }
    } else {
        // Render all lines together for better wrapping behaviour
        val allTokens = remember(tokens, palette, defaultColor) {
            buildHighlightedString(tokens, palette, defaultColor)
        }
        Text(
            text     = allTokens,
            style    = codeStyle,
            softWrap = softWrap,
            modifier = if (!softWrap) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HighlightCodeVisualTransformation – live syntax colouring for TextField
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A [VisualTransformation] that applies Atom-One-style colouring to the text
 * already tokenised by [Highlighter].
 *
 * Usage: pre-tokenise in a coroutine, pass the resulting token list here.
 */
class HighlightCodeVisualTransformation(
    private val tokens: List<HighlightToken>,
    private val isDark: Boolean,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val palette = if (isDark) AtomOneDarkPalette.palette else AtomOneLightPalette.palette
        val default = if (isDark) AtomOneDarkPalette.foreground else AtomOneLightPalette.foreground

        // If we have no tokens or the total text length differs (stale tokens), pass through.
        val tokenText = tokens.joinToString("") { t ->
            when (t) {
                is HighlightToken.Plain -> t.text
                is HighlightToken.Token -> t.text
            }
        }
        if (tokenText != text.text) return TransformedText(text, OffsetMapping.Identity)

        return TransformedText(
            buildHighlightedString(tokens, palette, default),
            OffsetMapping.Identity
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildHighlightedString(
    tokens: List<HighlightToken>,
    palette: Map<String, Color>,
    defaultColor: Color,
): AnnotatedString = buildAnnotatedString {
    tokens.forEach { token ->
        when (token) {
            is HighlightToken.Plain -> {
                withStyle(SpanStyle(color = defaultColor)) { append(token.text) }
            }
            is HighlightToken.Token -> {
                val color = palette[token.type] ?: defaultColor
                withStyle(SpanStyle(color = color)) { append(token.text) }
            }
        }
    }
}

/** Splits a flat token list into one list per source line. */
private fun splitIntoLines(tokens: List<HighlightToken>): List<List<HighlightToken>> {
    val lines = mutableListOf<MutableList<HighlightToken>>(mutableListOf())
    tokens.forEach { token ->
        val raw = when (token) {
            is HighlightToken.Plain -> token.text
            is HighlightToken.Token -> token.text
        }
        var start = 0
        raw.forEachIndexed { idx, ch ->
            if (ch == '\n') {
                val seg = raw.substring(start, idx)
                if (seg.isNotEmpty()) {
                    lines.last() += when (token) {
                        is HighlightToken.Plain -> HighlightToken.Plain(seg)
                        is HighlightToken.Token -> HighlightToken.Token(token.type, seg)
                    }
                }
                lines.add(mutableListOf())
                start = idx + 1
            }
        }
        val seg = raw.substring(start)
        if (seg.isNotEmpty()) {
            lines.last() += when (token) {
                is HighlightToken.Plain -> HighlightToken.Plain(seg)
                is HighlightToken.Token -> HighlightToken.Token(token.type, seg)
            }
        }
    }
    return lines
}
