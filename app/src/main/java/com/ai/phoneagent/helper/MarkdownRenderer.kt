package com.ai.phoneagent.helper

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

/**
 * DeepSeek 风格的 Markdown 渲染器
 * 
 * 支持实时流式渲染，包括：
 * - 标题
 * - 粗体/斜体
 * - 代码块（带语法高亮）
 * - 行内代码
 * - 列表
 * - 表格
 * - 删除线
 */
@PrismBundle(includeAll = true)
class MarkdownRenderer(context: Context) {

    private val markwon: Markwon
    
    init {
        val prism4j = Prism4j(GrammarLocatorDef())
        
        markwon = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            .build()
    }

    /**
     * 渲染 Markdown 文本到 TextView
     */
    fun render(textView: TextView, markdown: String) {
        markwon.setMarkdown(textView, markdown)
    }

    /**
     * 将 Markdown 转换为 Spanned
     */
    fun toSpanned(markdown: String): Spanned {
        return markwon.toMarkdown(markdown)
    }

    /**
     * 增量渲染（用于流式输出）
     * 返回处理后的 Spanned 对象
     */
    fun renderIncremental(currentText: String, newDelta: String): Spanned {
        val fullText = currentText + newDelta
        return markwon.toMarkdown(fullText)
    }
    
    companion object {
        @Volatile
        private var instance: MarkdownRenderer? = null
        
        fun getInstance(context: Context): MarkdownRenderer {
            return instance ?: synchronized(this) {
                instance ?: MarkdownRenderer(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * 简单的 Markdown 渲染器（不依赖外部库，作为后备方案）
 * 优化版：更接近 DeepSeek 的渲染效果
 */
object SimpleMarkdownRenderer {

    /**
     * 将 Markdown 文本转换为 SpannableStringBuilder
     */
    fun render(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val lines = text.split("\n")
        
        var inCodeBlock = false
        val codeBlockContent = StringBuilder()
        
        for ((index, line) in lines.withIndex()) {
            // 处理代码块
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // 结束代码块
                    val codeRendered = renderCodeBlock(codeBlockContent.toString())
                    builder.append(codeRendered)
                    codeBlockContent.clear()
                    inCodeBlock = false
                } else {
                    // 开始代码块
                    inCodeBlock = true
                }
                if (index < lines.size - 1) builder.append("\n")
                continue
            }
            
            if (inCodeBlock) {
                codeBlockContent.append(line)
                if (index < lines.size - 1) codeBlockContent.append("\n")
                continue
            }
            
            val processedLine = processLine(line)
            builder.append(processedLine)
            if (index < lines.size - 1) {
                builder.append("\n")
            }
        }
        
        // 处理行内格式（粗体、斜体、代码等）
        processInlineFormatting(builder)
        
        return builder
    }
    
    private fun renderCodeBlock(code: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        
        // 代码块前后添加换行
        builder.append("\n")
        val start = builder.length
        builder.append(code)
        val end = builder.length
        builder.append("\n")
        
        // 应用等宽字体和背景色（类似 DeepSeek 的浅灰背景）
        builder.setSpan(
            TypefaceSpan("monospace"),
            start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            BackgroundColorSpan(Color.parseColor("#F6F6F6")),
            start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            ForegroundColorSpan(Color.parseColor("#1A1A1A")),
            start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            RelativeSizeSpan(0.95f),
            start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        return builder
    }

    private fun processLine(line: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        
        // 处理标题
        when {
            line.startsWith("### ") -> {
                val content = line.substring(4)
                builder.append(content)
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(1.15f),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.startsWith("## ") -> {
                val content = line.substring(3)
                builder.append(content)
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(1.25f),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.startsWith("# ") -> {
                val content = line.substring(2)
                builder.append(content)
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(1.4f),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                val content = line.substring(2)
                val start = builder.length
                builder.append("  • $content")
                // 列表项使用稍深的颜色
                builder.setSpan(
                    ForegroundColorSpan(Color.parseColor("#2A2A2A")),
                    start, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val match = Regex("^(\\d+)\\.\\s(.*)").find(line)
                if (match != null) {
                    val start = builder.length
                    builder.append("  ${match.groupValues[1]}. ${match.groupValues[2]}")
                    // 有序列表使用稍深的颜色
                    builder.setSpan(
                        ForegroundColorSpan(Color.parseColor("#2A2A2A")),
                        start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    builder.append(line)
                }
            }
            line.startsWith("> ") -> {
                val content = "  ${line.substring(2)}"
                builder.append(content)
                builder.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6B7280")),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0, content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            else -> {
                builder.append(line)
            }
        }
        
        return builder
    }

    private fun processInlineFormatting(builder: SpannableStringBuilder) {
        // 重要：按照优先级处理，避免冲突
        // 1. 先处理行内代码（避免代码内的 * 被当作格式符号）
        processCodePattern(builder)
        
        // 2. 处理粗体 **text**
        processBoldPattern(builder)
        
        // 3. 处理斜体 *text*（在粗体之后，避免冲突）
        processItalicPattern(builder)
    }
    
    private fun processCodePattern(builder: SpannableStringBuilder) {
        val pattern = Regex("`([^`]+?)`")
        var offset = 0
        val text = builder.toString()
        
        pattern.findAll(text).toList().forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            builder.replace(start, end, content)
            // DeepSeek 风格：浅粉背景 + 深红文字
            builder.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFF1F0")),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                TypefaceSpan("monospace"),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                RelativeSizeSpan(0.94f),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset += match.value.length - content.length
        }
    }
    
    private fun processBoldPattern(builder: SpannableStringBuilder) {
        val pattern = Regex("\\*\\*([^*]+?)\\*\\*")
        var offset = 0
        val text = builder.toString()
        
        pattern.findAll(text).toList().forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            builder.replace(start, end, content)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // DeepSeek 风格：更深的黑色 + 稍大字号
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#0A0A0A")),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                RelativeSizeSpan(1.02f),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset += match.value.length - content.length
        }
    }
    
    private fun processItalicPattern(builder: SpannableStringBuilder) {
        // 只匹配单个 * 或 _，不匹配 ** 或 __
        val pattern = Regex("(?<!\\*)\\*(?!\\*)([^*]+?)\\*(?!\\*)")
        var offset = 0
        val text = builder.toString()
        
        pattern.findAll(text).toList().forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            builder.replace(start, end, content)
            builder.setSpan(
                StyleSpan(Typeface.ITALIC),
                start, start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset += match.value.length - content.length
        }
    }

    /**
     * 渲染代码块
     */
    fun renderCodeBlock(code: String, language: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        
        // 添加语言标签
        if (language.isNotEmpty()) {
            builder.append("$language\n")
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#6B7280")),
                0, language.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                RelativeSizeSpan(0.85f),
                0, language.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        val codeStart = builder.length
        builder.append(code)
        
        // 应用等宽字体和背景色
        builder.setSpan(
            TypefaceSpan("monospace"),
            codeStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            BackgroundColorSpan(Color.parseColor("#1F2937")),
            codeStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            ForegroundColorSpan(Color.parseColor("#E5E7EB")),
            codeStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // 简单的语法高亮
        if (language.lowercase() in listOf("kotlin", "java", "javascript", "js", "python", "py")) {
            applySyntaxHighlight(builder, codeStart, language)
        }
        
        return builder
    }

    private fun applySyntaxHighlight(builder: SpannableStringBuilder, offset: Int, language: String) {
        val code = builder.substring(offset)
        
        // 关键字高亮
        val keywords = when (language.lowercase()) {
            "kotlin" -> listOf(
                "fun", "val", "var", "class", "object", "interface", "if", "else", "when",
                "for", "while", "return", "true", "false", "null", "this", "super",
                "private", "public", "protected", "internal", "override", "suspend",
                "data", "sealed", "enum", "companion", "import", "package"
            )
            "java" -> listOf(
                "public", "private", "protected", "class", "interface", "void", "int",
                "boolean", "String", "if", "else", "for", "while", "return", "true",
                "false", "null", "this", "super", "new", "static", "final", "import"
            )
            "javascript", "js" -> listOf(
                "function", "const", "let", "var", "if", "else", "for", "while", "return",
                "true", "false", "null", "undefined", "this", "new", "class", "import",
                "export", "async", "await", "try", "catch"
            )
            "python", "py" -> listOf(
                "def", "class", "if", "elif", "else", "for", "while", "return", "True",
                "False", "None", "self", "import", "from", "as", "try", "except",
                "with", "lambda", "yield", "async", "await"
            )
            else -> emptyList()
        }
        
        for (keyword in keywords) {
            val pattern = Regex("\\b$keyword\\b")
            pattern.findAll(code).forEach { match ->
                builder.setSpan(
                    ForegroundColorSpan(Color.parseColor("#F472B6")), // 粉色
                    offset + match.range.first,
                    offset + match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // 字符串高亮
        val stringPattern = Regex("\"[^\"]*\"|'[^']*'")
        stringPattern.findAll(code).forEach { match ->
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#A3E635")), // 绿色
                offset + match.range.first,
                offset + match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // 注释高亮
        val commentPattern = Regex("//.*|#.*")
        commentPattern.findAll(code).forEach { match ->
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#6B7280")), // 灰色
                offset + match.range.first,
                offset + match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // 数字高亮
        val numberPattern = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberPattern.findAll(code).forEach { match ->
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#FBBF24")), // 黄色
                offset + match.range.first,
                offset + match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

/**
 * Prism4j 语法定义定位器
 */
class GrammarLocatorDef : io.noties.prism4j.GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): io.noties.prism4j.Prism4j.Grammar? {
        return null // 使用默认语法
    }

    override fun languages(): MutableSet<String> {
        return mutableSetOf()
    }
}
