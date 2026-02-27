package com.ai.phoneagent.helper

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
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
 * Aries AI 的 Markdown 渲染器
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
 * 优化版
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
        
        // 应用等宽字体（颜色由主题统一控制）
        builder.setSpan(
            TypefaceSpan("monospace"),
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
                // 列表项颜色由 TextView / 主题控制
            }
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val match = Regex("^(\\d+)\\.\\s(.*)").find(line)
                if (match != null) {
                    val start = builder.length
                    builder.append("  ${match.groupValues[1]}. ${match.groupValues[2]}")
                    // 有序列表颜色由 TextView / 主题控制
                } else {
                    builder.append(line)
                }
            }
            line.startsWith("> ") -> {
                val content = "  ${line.substring(2)}"
                builder.append(content)
                // 引用块颜色由 TextView / 主题控制
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
            
            // 行内代码保留字形和尺寸，不强制写死前景/背景色
            builder.replace(start, end, content)
            builder.setSpan(
                TypefaceSpan("monospace"),
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
            
            // 粗体颜色由主题控制
            builder.replace(start, end, content)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
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
            // 语言标签颜色由主题控制
            builder.setSpan(
                RelativeSizeSpan(0.85f),
                0, language.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        val codeStart = builder.length
        builder.append(code)
        
        // 应用等宽字体，前景/背景色由主题控制
        builder.setSpan(
            TypefaceSpan("monospace"),
            codeStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
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
