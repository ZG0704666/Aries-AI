package com.ai.phoneagent.ui.components.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula

// ─────────────────────────────────────────────────────────────────────────────
//  Delimiter normalisation
// ─────────────────────────────────────────────────────────────────────────────

/** Strips surrounding `$$…$$`, `$…$`, `\[…\]`, `\(…\)` and returns the bare formula. */
fun processLatex(raw: String): String {
    val s = raw.trim()
    return when {
        s.startsWith("$$") && s.endsWith("$$")   -> s.drop(2).dropLast(2).trim()
        s.startsWith("$")  && s.endsWith("$")    -> s.drop(1).dropLast(1).trim()
        s.startsWith("\\[") && s.endsWith("\\]") -> s.drop(2).dropLast(2).trim()
        s.startsWith("\\(") && s.endsWith("\\)") -> s.drop(2).dropLast(2).trim()
        else                                     -> s
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Low-level rendering (call on Dispatchers.Default)
// ─────────────────────────────────────────────────────────────────────────────

/** Renders [formula] to a [Bitmap] with JLatexMath-Android; returns null on failure. */
fun renderLatexToBitmap(formula: String, textSizePx: Float, argb: Int): Bitmap? = try {
    val tf   = TeXFormula(formula)
    val icon = tf.createTeXIcon(TeXConstants.STYLE_DISPLAY, textSizePx)
    if (icon.iconWidth <= 0 || icon.iconHeight <= 0) null
    else {
        val bmp = Bitmap.createBitmap(icon.iconWidth, icon.iconHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.TRANSPARENT)
        val canvas = Canvas(bmp)
        val g2d = ru.noties.jlatexmath.awt.AndroidGraphics2D()
        g2d.setCanvas(canvas)
        icon.paintIcon(null, g2d, 0, 0)
        bmp
    }
} catch (_: Exception) { null }

/** Estimates pixel dimensions of [formula] for placeholder sizing. */
fun assumeLatexSize(formula: String, textSizePx: Float): Pair<Float, Float> = try {
    val icon = TeXFormula(formula).createTeXIcon(TeXConstants.STYLE_TEXT, textSizePx)
    icon.iconWidth.toFloat() to icon.iconHeight.toFloat()
} catch (_: Exception) {
    (formula.length.coerceAtLeast(4) * textSizePx * 0.55f) to (textSizePx * 1.6f)
}

// ─────────────────────────────────────────────────────────────────────────────
//  MathBlock – display-mode formula
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a display-mode LaTeX [formula] centred with horizontal scroll.
 * Falls back to styled monospace text when [LocalMarkdownSettings.enableLatex] is off.
 */
@Composable
fun MathBlock(formula: String, modifier: Modifier = Modifier) {
    val settings   = LocalMarkdownSettings.current
    val fgColor    = MaterialTheme.colorScheme.onSurface
    val density    = LocalDensity.current
    val textSizePx = with(density) { 16.sp.toPx() }

    if (!settings.enableLatex) {
        Text(
            text     = "$$${formula}$$",
            style    = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color    = fgColor,
            modifier = modifier.padding(4.dp),
        )
        return
    }

    val bitmap by produceState<Bitmap?>(null, formula, fgColor) {
        value = withContext(Dispatchers.Default) {
            renderLatexToBitmap(formula, textSizePx * 1.3f, fgColor.toArgb())
        }
    }

    Box(
        modifier          = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        contentAlignment  = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap             = bmp.asImageBitmap(),
                contentDescription = formula,
                modifier           = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Text(
                text  = "$$${formula}$$",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = fgColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MathInlineText – paragraph text with embedded $…$ inline formulas
// ─────────────────────────────────────────────────────────────────────────────

/** Matches `$formula$` inline math (single dollar, not `$$`). */
internal val INLINE_MATH_RE = Regex("""\$(?!\$)(.+?)\$(?!\$)""", RegexOption.DOT_MATCHES_ALL)

internal data class MathSeg(val content: String, val isMath: Boolean)

/**
 * Splits [text] into math / non-math segments based on `$…$` delimiters.
 *
 * Note: `$$…$$` block-math mixed with text is handled separately by
 * [MathMixedText] in Markdown.kt — this function only deals with single-dollar
 * inline math.
 */
internal fun splitTextWithMath(text: String): List<MathSeg> {
    val result = mutableListOf<MathSeg>()
    var cursor = 0
    INLINE_MATH_RE.findAll(text).forEach { m ->
        if (m.range.first > cursor)
            result += MathSeg(text.substring(cursor, m.range.first), false)
        result += MathSeg(m.groupValues[1], true)
        cursor = m.range.last + 1
    }
    if (cursor < text.length) result += MathSeg(text.substring(cursor), false)
    return result
}

/**
 * Renders [text] which may contain `$formula$` inline math expressions.
 *
 * Bitmaps are computed asynchronously on [Dispatchers.Default]; the composable
 * recomposes with actual rendered images once they are ready.
 */
@Composable
fun MathInlineText(text: String, modifier: Modifier = Modifier) {
    val settings   = LocalMarkdownSettings.current
    val style      = MaterialTheme.typography.bodyMedium
    val fgColor    = MaterialTheme.colorScheme.onSurface
    val density    = LocalDensity.current
    val textSizePx = with(density) { style.fontSize.toPx() }

    if (!settings.enableLatex || !INLINE_MATH_RE.containsMatchIn(text)) {
        Text(text = text, style = style, color = fgColor, modifier = modifier)
        return
    }

    val segments = remember(text) { splitTextWithMath(text) }

    val bitmaps by produceState(emptyMap<Int, Bitmap?>(), text, fgColor) {
        val map = mutableMapOf<Int, Bitmap?>()
        withContext(Dispatchers.Default) {
            segments.forEachIndexed { i, seg ->
                if (seg.isMath) map[i] = renderLatexToBitmap(seg.content, textSizePx, fgColor.toArgb())
            }
        }
        value = map
    }

    val (annotated, inlineMap) = remember(segments, bitmaps, density, textSizePx) {
        buildMathAnnotated(segments, bitmaps, density, textSizePx, fgColor)
    }

    BasicText(
        text          = annotated,
        style         = style.copy(color = fgColor),
        inlineContent = inlineMap,
        modifier      = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Internal – AnnotatedString + InlineTextContent builder
// ─────────────────────────────────────────────────────────────────────────────

private fun buildMathAnnotated(
    segments: List<MathSeg>,
    bitmaps: Map<Int, Bitmap?>,
    density: Density,
    textSizePx: Float,
    fgColor: Color,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineMap = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { i, seg ->
            if (!seg.isMath) {
                withStyle(SpanStyle(color = fgColor)) { append(seg.content) }
            } else {
                val key = "math_$i"
                val bmp = bitmaps[i]

                val (wPx, hPx) = if (bmp != null) bmp.width.toFloat() to bmp.height.toFloat()
                                  else assumeLatexSize(seg.content, textSizePx)
                val wSp = with(density) { wPx.toDp().toSp() }
                val hSp = with(density) { hPx.toDp().toSp() }

                appendInlineContent(key, "[math]")
                val captured = bmp          // stable snapshot for the composable lambda
                inlineMap[key] = InlineTextContent(
                    Placeholder(wSp, hSp, PlaceholderVerticalAlign.Center)
                ) {
                    if (captured != null) {
                        Image(
                            bitmap             = captured.asImageBitmap(),
                            contentDescription = seg.content,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    return annotated to inlineMap
}
