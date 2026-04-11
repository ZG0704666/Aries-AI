package com.ai.phoneagent.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 代码块自动折叠阈值 */
private const val COLLAPSE_LINES = 10

/**
 * Syntax highlight 色板 – Atom One Dark 风格
 */
object CodeDarkPalette {
    val keyword = Color(0xFFCC7832)
    val string = Color(0xFF6A8759)
    val number = Color(0xFF6897BB)
    val comment = Color(0xFF808080)
    val function = Color(0xFFFFC66D)
    val operator = Color(0xFFCC7832)
    val punctuation = Color(0xFFA9B7C6)
    val className = Color(0xFFCB772F)
    val property = Color(0xFF9876AA)
    val boolean = Color(0xFF6897BB)
    val variable = Color(0xFFA9B7C6)
    val tag = Color(0xFFE8BF6A)
    val attrName = Color(0xFFBABABA)
    val attrValue = Color(0xFF6A8759)
    val plain = Color(0xFFA9B7C6)
}

object CodeLightPalette {
    val keyword = Color(0xFFA626A4)
    val string = Color(0xFF50A14F)
    val number = Color(0xFF986801)
    val comment = Color(0xFFA0A1A7)
    val function = Color(0xFF4078F2)
    val operator = Color(0xFF0184BC)
    val punctuation = Color(0xFF383A42)
    val className = Color(0xFFC18401)
    val property = Color(0xFF986801)
    val boolean = Color(0xFF986801)
    val variable = Color(0xFF383A42)
    val tag = Color(0xFFE45649)
    val attrName = Color(0xFF986801)
    val attrValue = Color(0xFF50A14F)
    val plain = Color(0xFF383A42)
}

/**
 * 代码块组件 – 带语言标签、复制/保存按钮、语法着色、等宽字体
 *
 * 功能对齐 RikkaHub:
 *  - 语言标签 (左上)
 *  - 复制 + 保存按钮 (右上)
 *  - 横向滚动
 *  - 基于正则的语法着色
 *  - Mermaid 图表检测 → 使用 WebView 渲染
 *  - 超过 10 行自动折叠/展开
 */
@Composable
fun HighlightCodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    completeCodeBlock: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var isExpanded by remember { mutableStateOf(true) }
    val codeLines = remember(code) { code.lines() }
    val shouldCollapse = codeLines.size > COLLAPSE_LINES
    val collapsedCode = remember(codeLines) {
        codeLines.take(COLLAPSE_LINES).joinToString("\n")
    }
    val displayCode = if (isExpanded || !shouldCollapse) code else collapsedCode
    val displayLines = remember(displayCode) { displayCode.lines() }

    // 保存文件 launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(code.toByteArray())
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        // ── Header: language label + action buttons ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 保存按钮
                    Text(
                        text = "保存",
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val ext = langToExtension(language)
                                createDocumentLauncher.launch(
                                    "code_${System.currentTimeMillis()}.$ext"
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    // 复制按钮
                    Text(
                        text = "复制",
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                                Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // ── Code body ──
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                // Mermaid 图表: 使用 WebView 渲染
                completeCodeBlock && language.lowercase() == "mermaid" -> {
                    Mermaid(
                        code = code,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    val codeStyle = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    val highlightedCode = remember(displayCode, language, isDark) {
                        highlightCode(displayCode, language, isDark)
                    }

                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .animateContentSize()
                        ) {
                            // Line numbers
                            val lineNumberWidth = remember(displayLines.size) {
                                displayLines.size.toString().length
                            }
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                displayLines.forEachIndexed { index, _ ->
                                    Text(
                                        text = (index + 1).toString()
                                            .padStart(lineNumberWidth, ' '),
                                        style = codeStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.35f),
                                        softWrap = false,
                                    )
                                }
                            }
                            // Code
                            Text(
                                text = highlightedCode,
                                style = codeStyle,
                                softWrap = false,
                            )
                        }
                    }

                    // 折叠/展开按钮
                    if (shouldCollapse) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isExpanded)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isExpanded) "收起"
                                    else "展开全部 (${codeLines.size} 行)",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────── Helpers ──────────

private fun langToExtension(language: String): String = when (language.lowercase()) {
    "kotlin", "kt" -> "kt"
    "java" -> "java"
    "python", "py" -> "py"
    "javascript", "js" -> "js"
    "typescript", "ts" -> "ts"
    "cpp", "c++" -> "cpp"
    "c" -> "c"
    "html" -> "html"
    "css" -> "css"
    "xml" -> "xml"
    "json" -> "json"
    "yaml", "yml" -> "yml"
    "markdown", "md" -> "md"
    "sql" -> "sql"
    "sh", "bash" -> "sh"
    "svg" -> "svg"
    "go", "golang" -> "go"
    "rust", "rs" -> "rs"
    "swift" -> "swift"
    else -> "txt"
}

// ────────── Simple regex-based syntax highlighting ──────────

private fun highlightCode(code: String, language: String, isDark: Boolean): AnnotatedString {
    val plainColor = if (isDark) CodeDarkPalette.plain else CodeLightPalette.plain
    val lang = language.lowercase().trim()

    return buildAnnotatedString {
        val patterns = buildHighlightPatterns(lang, isDark)
        if (patterns.isEmpty()) {
            withStyle(SpanStyle(color = plainColor)) { append(code) }
            return@buildAnnotatedString
        }

        data class MatchInfo(val start: Int, val end: Int, val style: SpanStyle, val text: String)

        val matches = mutableListOf<MatchInfo>()
        for ((regex, style) in patterns) {
            regex.findAll(code).forEach { result ->
                matches.add(
                    MatchInfo(result.range.first, result.range.last + 1, style, result.value)
                )
            }
        }
        matches.sortBy { it.start }
        val filtered = mutableListOf<MatchInfo>()
        var lastEnd = 0
        for (m in matches) {
            if (m.start >= lastEnd) {
                filtered.add(m)
                lastEnd = m.end
            }
        }

        var pos = 0
        for (m in filtered) {
            if (pos < m.start) {
                withStyle(SpanStyle(color = plainColor)) {
                    append(code.substring(pos, m.start))
                }
            }
            withStyle(m.style) { append(m.text) }
            pos = m.end
        }
        if (pos < code.length) {
            withStyle(SpanStyle(color = plainColor)) {
                append(code.substring(pos))
            }
        }
    }
}

private fun buildHighlightPatterns(
    lang: String,
    isDark: Boolean,
): List<Pair<Regex, SpanStyle>> {
    val keyword = if (isDark) CodeDarkPalette.keyword else CodeLightPalette.keyword
    val string = if (isDark) CodeDarkPalette.string else CodeLightPalette.string
    val number = if (isDark) CodeDarkPalette.number else CodeLightPalette.number
    val comment = if (isDark) CodeDarkPalette.comment else CodeLightPalette.comment
    val function = if (isDark) CodeDarkPalette.function else CodeLightPalette.function
    val boolean = if (isDark) CodeDarkPalette.boolean else CodeLightPalette.boolean

    val commentStyle = SpanStyle(color = comment, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = string)
    val numberStyle = SpanStyle(color = number)
    val keywordStyle = SpanStyle(color = keyword)
    val functionStyle = SpanStyle(color = function)
    val booleanStyle = SpanStyle(color = boolean)

    val commonPatterns = mutableListOf<Pair<Regex, SpanStyle>>()

    commonPatterns.add(Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to stringStyle)
    commonPatterns.add(Regex("'(?:[^'\\\\]|\\\\.)*'") to stringStyle)
    commonPatterns.add(Regex("\\b\\d+\\.?\\d*([eE][+-]?\\d+)?[fFdDlL]?\\b") to numberStyle)
    commonPatterns.add(Regex("\\b0[xX][0-9a-fA-F]+\\b") to numberStyle)
    commonPatterns.add(Regex("\\b(true|false|null|nil|None|True|False)\\b") to booleanStyle)

    val keywords = when (lang) {
        "kotlin", "kt" -> "val|var|fun|class|object|interface|when|if|else|for|while|return|import|package|private|public|protected|internal|override|open|abstract|sealed|data|companion|suspend|coroutine|inline|reified|try|catch|finally|throw|is|as|in|this|super|null|by|lateinit|const|enum|annotation|typealias|init|constructor|get|set|field|it"
        "java" -> "public|private|protected|static|final|abstract|class|interface|enum|extends|implements|import|package|new|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|throws|void|int|long|double|float|boolean|char|byte|short|String|this|super|null|synchronized|volatile|transient|native|instanceof"
        "python", "py" -> "def|class|import|from|as|if|elif|else|for|while|return|try|except|finally|raise|with|yield|lambda|pass|break|continue|and|or|not|in|is|global|nonlocal|assert|del|print|self|async|await"
        "javascript", "js", "typescript", "ts", "jsx", "tsx" -> "const|let|var|function|class|extends|import|export|from|default|if|else|for|while|do|switch|case|break|continue|return|try|catch|finally|throw|new|this|super|typeof|instanceof|async|await|yield|void|null|undefined|interface|type|enum|namespace|declare|readonly|abstract|implements"
        "c", "cpp", "c++", "cxx" -> "int|long|double|float|char|void|bool|unsigned|signed|short|struct|class|enum|union|typedef|static|const|volatile|extern|register|auto|return|if|else|for|while|do|switch|case|break|continue|goto|sizeof|new|delete|try|catch|throw|namespace|using|template|typename|virtual|override|public|private|protected|include|define|ifdef|ifndef|endif|pragma"
        "go", "golang" -> "func|package|import|var|const|type|struct|interface|map|chan|go|select|defer|range|return|if|else|for|switch|case|break|continue|fallthrough|default|nil"
        "rust", "rs" -> "fn|let|mut|const|struct|enum|impl|trait|pub|use|mod|crate|self|super|match|if|else|for|while|loop|return|break|continue|async|await|move|ref|where|type|unsafe|extern"
        "swift" -> "func|class|struct|enum|protocol|extension|import|var|let|if|else|for|while|switch|case|break|continue|return|guard|defer|try|catch|throw|self|super|nil|true|false|init|deinit|subscript|typealias|associatedtype|public|private|internal|open|fileprivate|static|override|final|lazy|weak|unowned|optional|required|convenience|mutating"
        "html", "xml", "svg" -> "html|head|body|div|span|p|a|img|ul|ol|li|table|tr|td|th|form|input|button|script|style|link|meta|title|header|footer|nav|section|article|aside|main|h1|h2|h3|h4|h5|h6|class|id|href|src|alt|type|value|name|content|rel|xmlns"
        "css", "scss", "sass", "less" -> "color|background|margin|padding|border|font|display|position|width|height|top|left|right|bottom|flex|grid|align|justify|text|line|overflow|opacity|transition|transform|animation|z-index|content|cursor|visibility|box|max|min|important"
        "sql" -> "SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TABLE|INDEX|VIEW|JOIN|INNER|LEFT|RIGHT|OUTER|ON|AND|OR|NOT|IN|BETWEEN|LIKE|ORDER|BY|GROUP|HAVING|LIMIT|OFFSET|AS|SET|VALUES|INTO|NULL|IS|EXISTS|UNION|ALL|DISTINCT|COUNT|SUM|AVG|MAX|MIN|CASE|WHEN|THEN|ELSE|END|PRIMARY|KEY|FOREIGN|REFERENCES|CONSTRAINT|DEFAULT|AUTO_INCREMENT"
        "bash", "sh", "shell", "zsh" -> "if|then|else|elif|fi|for|while|do|done|case|esac|function|return|exit|echo|export|source|alias|unset|local|readonly|shift|trap|eval|exec|set|unset|declare|typeset"
        "json", "yaml", "yml", "markdown", "md" -> ""
        else -> ""
    }

    when (lang) {
        "html", "xml", "svg", "css", "scss", "sass", "less", "json", "yaml", "yml", "markdown", "md" -> {}
        "python", "py", "bash", "sh", "shell", "zsh" ->
            commonPatterns.add(Regex("#[^\n]*") to commentStyle)
        "sql" ->
            commonPatterns.add(Regex("--[^\n]*") to commentStyle)
        else ->
            commonPatterns.add(Regex("//[^\n]*") to commentStyle)
    }
    when (lang) {
        "html", "xml", "svg" ->
            commonPatterns.add(Regex("<!--[\\s\\S]*?-->") to commentStyle)
        "css", "scss", "sass", "less" ->
            commonPatterns.add(Regex("/\\*[\\s\\S]*?\\*/") to commentStyle)
        "python", "py" -> {
            commonPatterns.add(Regex("\"\"\"[\\s\\S]*?\"\"\"") to stringStyle)
            commonPatterns.add(Regex("'''[\\s\\S]*?'''") to stringStyle)
        }
        "bash", "sh", "shell", "zsh", "json", "yaml", "yml", "markdown", "md", "sql" -> {}
        else ->
            commonPatterns.add(Regex("/\\*[\\s\\S]*?\\*/") to commentStyle)
    }

    if (keywords.isNotEmpty()) {
        commonPatterns.add(Regex("\\b($keywords)\\b") to keywordStyle)
    }

    if (lang !in listOf("html", "xml", "svg", "css", "scss", "json", "yaml", "yml", "markdown", "md")) {
        commonPatterns.add(Regex("\\b([a-zA-Z_]\\w*)\\s*(?=\\()") to functionStyle)
    }

    return commonPatterns
}

// Simple luminance calculation
private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
