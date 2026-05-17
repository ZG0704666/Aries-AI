package com.ai.phoneagent.ui.components.markdown

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.WrapText

private const val COLLAPSE_THRESHOLD = 10

// ─────────────────────────────────────────────────────────────────────────────
//  CodeBlock – full code block widget
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a fenced code block with:
 *  - Async syntax highlighting via [Highlighter] + Prism.js
 *  - Language label + toolbar (copy / save / line-numbers / wrap / collapse)
 *  - HTML/SVG preview via [HtmlPreviewDialog]
 *  - Mermaid delegation to [Mermaid]
 */
@Composable
fun CodeBlock(
    language: String,
    code: String,
    blockKey: String = code.hashCode().toString(),
    modifier: Modifier = Modifier,
) {
    // ── Mermaid short-circuit ─────────────────────────────────────────────────
    if (language.lowercase() == "mermaid") {
        Mermaid(code = code, modifier = modifier)
        return
    }

    val settings     = LocalMarkdownSettings.current
    val isDark       = isSystemInDarkTheme()
    val context      = LocalContext.current
    val clipboard    = LocalClipboardManager.current

    // ── Token state ───────────────────────────────────────────────────────────
    var tokens by remember(code, language) {
        mutableStateOf<List<HighlightToken>>(listOf(HighlightToken.Plain(code)))
    }
    LaunchedEffect(code, language, settings.enableCodeHighlight) {
        tokens =
            if (settings.enableCodeHighlight) {
                Highlighter.highlight(code, language)
            } else {
                listOf(HighlightToken.Plain(code))
            }
    }

    // ── UI state ──────────────────────────────────────────────────────────────
    val lines      = remember(code) { if (code.isEmpty()) listOf("") else code.split('\n') }
    val canCollapse = settings.autoCollapse && lines.size > COLLAPSE_THRESHOLD
    var expanded   by rememberSaveable(blockKey, settings.autoCollapse) { mutableStateOf(!canCollapse) }
    var lineNums   by rememberSaveable(blockKey) { mutableStateOf(settings.lineNumbers) }
    var softWrap   by rememberSaveable(blockKey) { mutableStateOf(settings.autoWrap) }
    var showHtmlPreview by remember { mutableStateOf(false) }

    val bgColor = if (isDark) AtomOneDarkPalette.background else AtomOneLightPalette.background
    val isHtmlSvg = language.lowercase() in setOf("html", "svg", "xml")

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = bgColor,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Header bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Language label
                    if (language.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Lucide.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isHtmlSvg) {
                            IconButton(onClick = { showHtmlPreview = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Lucide.Eye, contentDescription = "Preview", modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Line numbers toggle
                        IconButton(onClick = { lineNums = !lineNums }, modifier = Modifier.size(28.dp)) {
                            Icon(Lucide.Hash, contentDescription = "Line numbers", modifier = Modifier.size(16.dp),
                                tint = if (lineNums) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Wrap toggle
                        IconButton(onClick = { softWrap = !softWrap }, modifier = Modifier.size(28.dp)) {
                            Icon(Lucide.WrapText, contentDescription = "Wrap", modifier = Modifier.size(16.dp),
                                tint = if (softWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Save to Downloads
                        IconButton(onClick = { saveToDownloads(context, code, language) }, modifier = Modifier.size(28.dp)) {
                            Icon(Lucide.Save, contentDescription = "Save", modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Copy
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(code)) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Lucide.Copy, contentDescription = "Copy", modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Code content ──────────────────────────────────────────────
                val visibleTokens = if (expanded) tokens else trimTokensToLines(tokens, COLLAPSE_THRESHOLD)

                SelectionContainer {
                    AnimatedVisibility(
                        visible   = true,
                        enter     = expandVertically(),
                        exit      = shrinkVertically(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            HighlightText(
                                tokens      = visibleTokens,
                                isDark      = isDark,
                                lineNumbers = lineNums,
                                softWrap    = softWrap,
                                modifier    = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // ── Collapse toggle ───────────────────────────────────────────
                if (canCollapse) {
                    TextButton(
                        onClick   = { expanded = !expanded },
                        modifier  = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = if (expanded) "Collapse" else "Show ${lines.size} lines",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }

    // ── HTML / SVG preview dialog ─────────────────────────────────────────────
    if (showHtmlPreview) {
        HtmlPreviewDialog(html = code, onDismiss = { showHtmlPreview = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HtmlPreviewDialog – renders HTML/SVG in a WebView inside a Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewDialog(html: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HtmlWebView(html = html, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Saves [code] to the public Downloads folder. */
private fun saveToDownloads(context: Context, code: String, language: String) {
    val ext = languageToExtension(language)
    val filename = "code_${System.currentTimeMillis()}.$ext"
    try {
        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        uri?.let { resolver.openOutputStream(it)?.use { os -> os.write(code.toByteArray()) } }
        Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun languageToExtension(lang: String): String = when (lang.lowercase()) {
    "kotlin"          -> "kt"
    "java"            -> "java"
    "python"          -> "py"
    "javascript", "js" -> "js"
    "typescript", "ts" -> "ts"
    "html"            -> "html"
    "css"             -> "css"
    "svg"             -> "svg"
    "xml"             -> "xml"
    "json"            -> "json"
    "bash", "sh"      -> "sh"
    "c"               -> "c"
    "cpp"             -> "cpp"
    "rust"            -> "rs"
    "go"              -> "go"
    "ruby"            -> "rb"
    "swift"           -> "swift"
    else              -> "txt"
}

/**
 * Trims a token list to the first [maxLines] lines (for collapsed display).
 */
private fun trimTokensToLines(tokens: List<HighlightToken>, maxLines: Int): List<HighlightToken> {
    var lineCount = 0
    val result = mutableListOf<HighlightToken>()
    for (token in tokens) {
        val text = when (token) {
            is HighlightToken.Plain -> token.text
            is HighlightToken.Token -> token.text
        }
        val newlines = text.count { it == '\n' }
        if (lineCount + newlines >= maxLines) {
            // Include only up to maxLines
            val lines = text.split('\n')
            val keep = lines.take(maxLines - lineCount + 1).joinToString("\n")
            result += when (token) {
                is HighlightToken.Plain -> HighlightToken.Plain(keep)
                is HighlightToken.Token -> HighlightToken.Token(token.type, keep)
            }
            break
        }
        result += token
        lineCount += newlines
    }
    return result
}
