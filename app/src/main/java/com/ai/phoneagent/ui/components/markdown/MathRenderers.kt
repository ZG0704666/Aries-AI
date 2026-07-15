package com.ai.phoneagent.ui.components.markdown

import android.util.LruCache
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

// 剥离定界符 → 提取文档体 → 去掉文档级命令 → 提取数学环境
fun processLatex(raw: String): String {
    var s = raw.trim()

    // step-1: 去掉外层 $$…$$ / $…$ / \[…\] / \(…\) 定界符
    s = when {
        s.startsWith("\$\$") && s.endsWith("\$\$") -> s.drop(2).dropLast(2).trim()
        s.startsWith("\$")   && s.endsWith("\$")   -> s.drop(1).dropLast(1).trim()
        s.startsWith("\\[")  && s.endsWith("\\]")  -> s.drop(2).dropLast(2).trim()
        s.startsWith("\\(")  && s.endsWith("\\)")  -> s.drop(2).dropLast(2).trim()
        else                                        -> s
    }

    // step-2: 去掉 \begin{document}…\end{document} 完整文档外壳，保留内部内容
    val docBodyRe = Regex("""\\begin\{document\}([\s\S]*?)\\end\{document\}""")
    val docMatch = docBodyRe.find(s)
    if (docMatch != null) s = docMatch.groupValues[1].trim()

    // step-3: 去掉文档级命令行（JLatexMath 不支持）
    val docLevelRe = Regex(
        """^\\(?:documentclass|usepackage|title|author|date|maketitle|begin\{titlepage\}|end\{titlepage\})[^\n]*\n?""",
        RegexOption.MULTILINE
    )
    s = docLevelRe.replace(s, "").trim()

    if (s.isEmpty()) return raw.trim()

    // step-4: 优先提取数学环境 \begin{equation}…\end{equation} 等
    val mathEnvRe = Regex("""\\begin\{(equation|align|matrix|pmatrix|bmatrix|cases|gather|multline|eqnarray)[*]?\}[\s\S]*?\\end\{\1[*]?\}""")
    val mathParts = mathEnvRe.findAll(s).map { it.value }.toList()
    if (mathParts.isNotEmpty()) return mathParts.joinToString("\n\n")

    // step-4.5: 提取 \[…\] 和 \(…\) 块（代码围栏内未被 preProcess 转换的）
    val displayBlockRe  = Regex("""\\\[[\s\S]*?\\\]""")
    val inlineParenRe   = Regex("""\\\([\s\S]*?\\\)""")
    val displayParts = (displayBlockRe.findAll(s) + inlineParenRe.findAll(s))
        .map { m ->
            val inner = m.value
            when {
                inner.startsWith("\\[") -> inner.drop(2).dropLast(2).trim()
                inner.startsWith("\\(") -> inner.drop(2).dropLast(2).trim()
                else -> inner
            }
        }.filter { it.isNotBlank() }.toList()
    if (displayParts.isNotEmpty()) return displayParts.joinToString("\n\n")

    // step-5: 提取行内 $…$ / $$…$$ 公式
    val inlineMathRe = Regex("""\$\$?[^$\n]+?\$\$?""")
    val inlineParts = inlineMathRe.findAll(s).map { it.value }.toList()
    if (inlineParts.isNotEmpty()) return inlineParts.joinToString("  ")

    return s
}

fun isRenderableMath(formula: String): Boolean {
    val s = formula.trim()
    if (s.isEmpty()) return false
    if (s.contains("\\begin{document}")) return false
    if (s.contains(Regex("""\\begin\{(equation|align|matrix|pmatrix|bmatrix|cases|gather|multline|eqnarray)"""))) return true
    if (s.contains(Regex("""\$\$?"""))) return true
    if (s.contains(Regex("""\\[\[\(]"""))) return true
    if (s.contains(Regex("""\\(?:frac|sqrt|sum|int|prod|lim|infty|alpha|beta|gamma|delta|theta|pi|sigma|omega|cdot|times|div|leq|geq|neq|approx|rightarrow|leftarrow|Rightarrow|Leftarrow|partial|nabla|hat|bar|vec|mathbf|text)\b"""))) return true
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
//  LaTeX document segmentation
// ─────────────────────────────────────────────────────────────────────────────

internal data class LatexSegment(val content: String, val isMath: Boolean)

// 匹配 \[...\]、\(...\)、\begin{equation/align/...}...\end{...}
private val MATH_CHUNK_RE = Regex(
    """\\\[[\s\S]*?\\\]""" +
    """|\\\([\s\S]*?\\\)""" +
    """|\\begin\{(equation|align|matrix|pmatrix|bmatrix|cases|gather|multline|eqnarray)[*]?\}[\s\S]*?\\end\{\1[*]?\}"""
)

// JLatexMath 不支持 equation/eqnarray/multline 环境，需要剥离外壳只保留内部公式
private val STRIP_ENV_RE = Regex(
    """^\\begin\{(equation|eqnarray|multline)[*]?\}\s*([\s\S]*?)\s*\\end\{\1[*]?\}$"""
)

internal fun splitLatexDocSegments(payload: String): List<LatexSegment> {
    val result = mutableListOf<LatexSegment>()
    var cursor = 0
    MATH_CHUNK_RE.findAll(payload).forEach { m ->
        if (m.range.first > cursor) {
            val text = payload.substring(cursor, m.range.first).trim()
            if (text.isNotEmpty()) result += LatexSegment(text, isMath = false)
        }
        val inner = m.value.let {
            when {
                it.startsWith("\\[") -> it.drop(2).dropLast(2).trim()
                it.startsWith("\\(") -> it.drop(2).dropLast(2).trim()
                else -> {
                    // equation/eqnarray/multline → 剥离环境取内部；align/matrix/cases 等保留
                    val strip = STRIP_ENV_RE.find(it)
                    strip?.groupValues?.get(2)?.trim() ?: it
                }
            }
        }
        if (inner.isNotEmpty()) result += LatexSegment(inner, isMath = true)
        cursor = m.range.last + 1
    }
    if (cursor < payload.length) {
        val tail = payload.substring(cursor).trim()
        if (tail.isNotEmpty()) result += LatexSegment(tail, isMath = false)
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
//  Low-level rendering (call on Dispatchers.Default)
// ─────────────────────────────────────────────────────────────────────────────

private data class LatexBitmapCacheEntry(
    val bitmap: Bitmap,
    val widthPx: Float,
    val heightPx: Float,
)

private val latexBitmapCache = object : LruCache<String, LatexBitmapCacheEntry>(96) {}

private fun buildLatexCacheKey(
    formula: String,
    textSizePx: Float,
    argb: Int,
    displayMode: Boolean,
): String {
    return listOf(
        if (displayMode) "display" else "inline",
        formula,
        textSizePx,
        argb,
    ).joinToString("|")
}

/** Renders [formula] to a [Bitmap] with JLatexMath-Android; returns null on failure. */
fun renderLatexToBitmap(
    formula: String,
    textSizePx: Float,
    argb: Int,
    displayMode: Boolean,
): Bitmap? = try {
    val tf   = TeXFormula(formula)
    val style = if (displayMode) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
    val icon = tf.createTeXIcon(style, textSizePx)
    icon.setForeground(ru.noties.jlatexmath.awt.Color(argb))
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
fun assumeLatexSize(
    formula: String,
    textSizePx: Float,
    displayMode: Boolean,
): Pair<Float, Float> = try {
    val style = if (displayMode) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
    val icon = TeXFormula(formula).createTeXIcon(style, textSizePx)
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
    val displayTextSizePx = with(density) { 16.sp.toPx() * 1.3f }
    val cacheKey = remember(formula, fgColor, displayTextSizePx) {
        buildLatexCacheKey(
            formula = formula,
            textSizePx = displayTextSizePx,
            argb = fgColor.toArgb(),
            displayMode = true,
        )
    }
    val cachedEntry = remember(cacheKey) { latexBitmapCache.get(cacheKey) }
    val placeholderSize = remember(formula, displayTextSizePx) {
        assumeLatexSize(
            formula = formula,
            textSizePx = displayTextSizePx,
            displayMode = true,
        )
    }

    if (!settings.enableLatex) {
        Text(
            text     = "$$${formula}$$",
            style    = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color    = fgColor,
            modifier = modifier.padding(4.dp),
        )
        return
    }

    val cacheAwareInitialBitmap = cachedEntry?.bitmap
    val bitmap by produceState(cacheAwareInitialBitmap, cacheKey) {
        if (cacheAwareInitialBitmap != null) {
            value = cacheAwareInitialBitmap
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            renderLatexToBitmap(
                formula = formula,
                textSizePx = displayTextSizePx,
                argb = fgColor.toArgb(),
                displayMode = true,
            )
                ?.also { rendered ->
                    latexBitmapCache.put(
                        cacheKey,
                        LatexBitmapCacheEntry(
                            bitmap = rendered,
                            widthPx = rendered.width.toFloat(),
                            heightPx = rendered.height.toFloat(),
                        ),
                    )
                }
        }
    }

    val widthPx = cachedEntry?.widthPx ?: bitmap?.width?.toFloat() ?: placeholderSize.first
    val heightPx = cachedEntry?.heightPx ?: bitmap?.height?.toFloat() ?: placeholderSize.second
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    Box(
        modifier          = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        contentAlignment  = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(widthDp)
                .height(heightDp)
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = formula,
                    modifier = Modifier,
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(heightDp))
                Text(
                    text = formula,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = fgColor.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MathInlineText – paragraph text with embedded $…$ inline formulas
// ─────────────────────────────────────────────────────────────────────────────

/** Matches `$formula$` inline math (single dollar, not `$$`). */
internal val INLINE_MATH_RE = Regex("""\$(?!\$)(.+?)\$(?!\$)""", RegexOption.DOT_MATCHES_ALL)

internal data class MathSeg(val content: String, val isMath: Boolean)

internal fun containsInlineMath(text: String): Boolean =
    splitTextWithMath(text).any { it.isMath }

private val INLINE_GREEK_WORD_RE = Regex(
    """(?<![\\A-Za-z])(alpha|beta|gamma|delta|epsilon|varepsilon|theta|vartheta|lambda|mu|pi|rho|sigma|omega)(?![A-Za-z])"""
)

internal fun normalizeInlineLatexFormula(raw: String): String {
    var s = raw.trim()
    s = s.replace(Regex("""\\theta0\b"""), """\\theta_0""")
    s = s.replace(Regex("""\\mut\b"""), """\\mu(t)""")
    s = INLINE_GREEK_WORD_RE.replace(s) { match -> "\\${match.value}" }
    return s
}

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
    while (cursor < text.length) {
        val start = findInlineMathStart(text, cursor)
        if (start == -1) break
        val end = findInlineMathEnd(text, start + 1)
        if (end == -1) break
        val payload = text.substring(start + 1, end)
        if (payload.isBlank() || payload.contains('\n')) {
            cursor = start + 1
            continue
        }
        if (start > cursor) {
            result += MathSeg(text.substring(cursor, start), false)
        }
        result += MathSeg(payload, true)
        cursor = end + 1
    }
    if (cursor < text.length) result += MathSeg(text.substring(cursor), false)
    return result
}

private fun findInlineMathStart(text: String, fromIndex: Int): Int {
    var index = fromIndex
    while (index < text.length) {
        val candidate = text.indexOf('$', index)
        if (candidate == -1) return -1
        val prev = text.getOrNull(candidate - 1)
        val next = text.getOrNull(candidate + 1)
        if (prev != '\\' && next != '$' && next != null && !next.isWhitespaceOnlyNewline()) {
            return candidate
        }
        index = candidate + 1
    }
    return -1
}

private fun findInlineMathEnd(text: String, fromIndex: Int): Int {
    var index = fromIndex
    while (index < text.length) {
        val candidate = text.indexOf('$', index)
        if (candidate == -1) return -1
        val prev = text.getOrNull(candidate - 1)
        val next = text.getOrNull(candidate + 1)
        if (prev != '\\' && prev != '$' && next != '$') {
            return candidate
        }
        index = candidate + 1
    }
    return -1
}

private fun Char.isWhitespaceOnlyNewline(): Boolean =
    this == '\n' || this == '\r'

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

    if (!settings.enableLatex || !containsInlineMath(text)) {
        Text(text = text, style = style, color = fgColor, modifier = modifier)
        return
    }

    val segments = remember(text) { splitTextWithMath(text) }

    val bitmaps by produceState(emptyMap<Int, Bitmap?>(), text, fgColor) {
        val map = mutableMapOf<Int, Bitmap?>()
        withContext(Dispatchers.Default) {
            segments.forEachIndexed { i, seg ->
                if (seg.isMath) {
                    val normalized = normalizeInlineLatexFormula(seg.content)
                    val cacheKey = buildLatexCacheKey(
                        formula = normalized,
                        textSizePx = textSizePx,
                        argb = fgColor.toArgb(),
                        displayMode = false,
                    )
                    val cached = latexBitmapCache.get(cacheKey)
                    if (cached != null) {
                        map[i] = cached.bitmap
                    } else {
                        val rendered = renderLatexToBitmap(
                            formula = normalized,
                            textSizePx = textSizePx,
                            argb = fgColor.toArgb(),
                            displayMode = false,
                        )
                        if (rendered != null) {
                            latexBitmapCache.put(
                                cacheKey,
                                LatexBitmapCacheEntry(
                                    bitmap = rendered,
                                    widthPx = rendered.width.toFloat(),
                                    heightPx = rendered.height.toFloat(),
                                ),
                            )
                        }
                        map[i] = rendered
                    }
                }
            }
        }
        value = map
    }

    val (annotated, inlineMap) = remember(segments, bitmaps, density, textSizePx) {
        buildMathAnnotated(segments, bitmaps, density, textSizePx, fgColor)
    }

    Box(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = annotated,
            style = style.copy(color = fgColor),
            inlineContent = inlineMap,
        )
    }
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
                appendSimpleMarkdownText(seg.content, fgColor)
            } else {
                val key = "math_$i"
                val formula = normalizeInlineLatexFormula(seg.content)
                val bmp = bitmaps[i]

                val (wPx, hPx) = if (bmp != null) bmp.width.toFloat() to bmp.height.toFloat()
                                  else assumeLatexSize(
                                      formula = formula,
                                      textSizePx = textSizePx,
                                      displayMode = false,
                                  )
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
                            contentDescription = formula,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text  = formula,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = fgColor,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    return annotated to inlineMap
}

private fun AnnotatedString.Builder.appendSimpleMarkdownText(text: String, fgColor: Color) {
    var cursor = 0
    Regex("""(\*\*|__)(.+?)\1""").findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            withStyle(SpanStyle(color = fgColor)) {
                append(text.substring(cursor, match.range.first))
            }
        }
        withStyle(SpanStyle(color = fgColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
            append(match.groupValues[2])
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        withStyle(SpanStyle(color = fgColor)) {
            append(text.substring(cursor))
        }
    }
}
