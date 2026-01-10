package com.ai.phoneagent.helper

import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.ai.phoneagent.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * DeepSeek 风格流式渲染助手
 * 
 * 特点：
 * 1. 思考中：显示"思考中"，实时展示思考过程
 * 2. 已思考：思考结束后显示"已思考 (用时 X 秒)"
 * 3. 平滑打字机动画，避免界面抖动
 */
object StreamRenderHelper {

    data class ViewHolder(
        val thinkingLayout: LinearLayout,
        val thinkingHeader: LinearLayout,
        val thinkingText: TextView,
        val thinkingIndicator: TextView,
        val thinkingContentArea: View,
        val messageContent: TextView,
        val authorName: TextView,
        val actionArea: View,
        val retryButton: View?,
        val copyButton: View?
    )

    // 文本动画器（支持 Markdown 渲染）
    private class TextAnimator(
        textView: TextView,
        private val scope: CoroutineScope,
        private val onUpdate: () -> Unit,
        val useMarkdown: Boolean = false  // 添加 val 使其可访问
    ) {
        private val viewRef = WeakReference(textView)
        private val textBuilder = StringBuilder()
        private var job: Job? = null
        private var displayedLength = 0

        fun append(delta: String) {
            synchronized(textBuilder) {
                textBuilder.append(delta)
            }
            
            // 立即更新显示（不等待动画循环）
            val view = viewRef.get()
            if (view != null) {
                val currentText = synchronized(textBuilder) { textBuilder.toString() }
                if (useMarkdown) {
                    view.text = SimpleMarkdownRenderer.render(currentText)
                } else {
                    view.text = currentText
                }
                displayedLength = currentText.length
            }
            
            startAnimation()
        }

        fun setFullText(text: String) {
            job?.cancel()
            synchronized(textBuilder) {
                textBuilder.clear()
                textBuilder.append(text)
            }
            val view = viewRef.get() ?: return
            if (useMarkdown) {
                view.text = SimpleMarkdownRenderer.render(text)
            } else {
                view.text = text
            }
            displayedLength = text.length
        }

        fun getText(): String = synchronized(textBuilder) { textBuilder.toString() }

        fun appendRaw(delta: String) {
            if (delta.isEmpty()) return
            synchronized(textBuilder) {
                textBuilder.append(delta)
                displayedLength = textBuilder.length
            }
        }
        
        fun clear() {
            job?.cancel()
            synchronized(textBuilder) {
                textBuilder.clear()
            }
            displayedLength = 0
            val view = viewRef.get() ?: return
            view.text = ""
        }

        private fun startAnimation() {
            if (job?.isActive == true) return

            job = scope.launch {
                while (isActive) {
                    val target = synchronized(textBuilder) { textBuilder.toString() }
                    val targetLen = target.length

                    if (displayedLength >= targetLen) {
                        // 再检查一次，防止并发问题
                        if (synchronized(textBuilder) { textBuilder.length } == targetLen) {
                            break
                        }
                        continue
                    }

                    val view = viewRef.get() ?: break

                    // 计算步长（堆积多时加速）
                    val remaining = targetLen - displayedLength
                    val step = when {
                        remaining > 100 -> 15
                        remaining > 50 -> 8
                        remaining > 20 -> 4
                        else -> 1
                    }

                    val nextLen = (displayedLength + step).coerceAtMost(targetLen)
                    val displayText = target.substring(0, nextLen)
                    
                    // 应用 Markdown 渲染
                    if (useMarkdown) {
                        view.text = SimpleMarkdownRenderer.render(displayText)
                    } else {
                        view.text = displayText
                    }
                    displayedLength = nextLen
                    
                    view.post { onUpdate() }
                    delay(16L)
                }
            }
        }

        fun stop() {
            job?.cancel()
            val finalText = synchronized(textBuilder) { textBuilder.toString() }
            val view = viewRef.get() ?: return
            if (useMarkdown) {
                view.text = SimpleMarkdownRenderer.render(finalText)
            } else {
                view.text = finalText
            }
            displayedLength = finalText.length
        }
    }

    // 缓存
    private val animators = ConcurrentHashMap<Int, TextAnimator>()
    private val parsers = ConcurrentHashMap<Int, DeepSeekStreamParser>()
    private var thinkingStartTime = 0L

    fun bindViews(aiView: View): ViewHolder {
        return ViewHolder(
            thinkingLayout = aiView.findViewById(R.id.thinking_layout),
            thinkingHeader = aiView.findViewById(R.id.thinking_header),
            thinkingText = aiView.findViewById(R.id.thinking_text),
            thinkingIndicator = aiView.findViewById(R.id.thinking_indicator_text),
            thinkingContentArea = aiView.findViewById(R.id.thinking_content_area),
            messageContent = aiView.findViewById(R.id.message_content),
            authorName = aiView.findViewById(R.id.ai_author_name),
            actionArea = aiView.findViewById(R.id.action_area),
            retryButton = aiView.findViewById(R.id.btn_retry),
            copyButton = aiView.findViewById(R.id.btn_copy)
        )
    }

    /**
     * 初始化思考状态
     */
    fun initThinkingState(vh: ViewHolder) {
        val viewId = vh.hashCode()
        
        // 1. 先清理旧资源（包括清除缓存的 animators）
        cleanup(vh)
        
        // 2. 强制清空 UI（防止 animator 缓存问题）
        vh.thinkingText.text = ""
        vh.messageContent.text = ""
        
        // 3. 记录开始时间
        thinkingStartTime = System.currentTimeMillis()
        
        // 4. 初始化新的解析器
        parsers[viewId] = DeepSeekStreamParser()
        
        // 5. 设置 UI 状态
        vh.authorName.visibility = View.VISIBLE
        vh.thinkingLayout.visibility = View.VISIBLE
        vh.thinkingLayout.alpha = 1f
        vh.actionArea.visibility = View.GONE

        // 显示"思考中"
        val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
        headerTitle?.text = "思考中"
        
        // 思考区域初始展开
        vh.thinkingText.visibility = View.VISIBLE
        vh.thinkingContentArea.visibility = View.VISIBLE
        vh.thinkingIndicator.text = " ⌄"
        
        // 设置折叠逻辑（只设置一次）
        if (vh.thinkingHeader.tag != "listener_set") {
            var expanded = true
            vh.thinkingHeader.setOnClickListener {
                expanded = !expanded
                vh.thinkingText.visibility = if (expanded) View.VISIBLE else View.GONE
                vh.thinkingContentArea.visibility = if (expanded) View.VISIBLE else View.GONE
                vh.thinkingIndicator.text = if (expanded) " ⌄" else " ›"
            }
            vh.thinkingHeader.tag = "listener_set"
        }
    }

    private fun getParser(vh: ViewHolder): DeepSeekStreamParser {
        return parsers.getOrPut(vh.hashCode()) { DeepSeekStreamParser() }
    }

    private fun getAnimator(
        textView: TextView,
        scope: CoroutineScope,
        onScroll: () -> Unit,
        useMarkdown: Boolean = false
    ): TextAnimator {
        val id = textView.hashCode()
        
        // 检查现有 animator
        val existing = animators[id]
        if (existing != null) {
            // 如果 useMarkdown 参数不匹配，先移除旧的，创建新的
            if (existing.useMarkdown != useMarkdown) {
                existing.stop()
                animators.remove(id)
            } else {
                return existing
            }
        }
        
        // 创建新的 animator
        val newAnimator = TextAnimator(textView, scope, onScroll, useMarkdown)
        animators[id] = newAnimator
        return newAnimator
    }

    /**
     * 处理 reasoning_content 增量（来自 API 的思考字段）
     * 注意：此方法专门处理 API 返回的 reasoning_content 字段，直接追加到思考区域
     */
    fun processReasoningDelta(
        vh: ViewHolder,
        delta: String,
        coroutineScope: CoroutineScope,
        onScroll: () -> Unit
    ) {
        if (delta.isEmpty()) return
        
        // 强制确保思考区域可见并展开
        vh.thinkingLayout.visibility = View.VISIBLE
        vh.thinkingLayout.alpha = 1f
        vh.thinkingText.visibility = View.VISIBLE
        vh.thinkingContentArea.visibility = View.VISIBLE
        
        val parser = getParser(vh)
        parser.processReasoningDelta(delta)
        
        // 追加到思考区域，使用 Markdown 渲染
        val animator = getAnimator(vh.thinkingText, coroutineScope, onScroll, useMarkdown = true)
        animator.append(delta)
        
        // 立即刷新显示（调试用）
        vh.thinkingText.post { onScroll() }
    }

    /**
     * 处理 content 增量
     * 注意：此方法处理 API 返回的 content 字段，会智能解析其中的思考/回答标签
     */
    fun processContentDelta(
        vh: ViewHolder,
        delta: String,
        coroutineScope: CoroutineScope,
        context: Context,
        onScroll: () -> Unit,
        onPhaseChange: (Boolean) -> Unit  // true = 进入回答阶段
    ) {
        if (delta.isEmpty()) return
        
        val parser = getParser(vh)
        val chunks = parser.processContentDelta(delta)
        
        for (chunk in chunks) {
            when (chunk.type) {
                DeepSeekStreamParser.ChunkType.THINKING -> {
                    // 确保思考区域可见
                    if (vh.thinkingLayout.visibility != View.VISIBLE) {
                        vh.thinkingLayout.visibility = View.VISIBLE
                    }
                    
                    // 追加到思考区域
                    val animator = getAnimator(vh.thinkingText, coroutineScope, onScroll, useMarkdown = true)
                    animator.append(chunk.content)
                }
                
                DeepSeekStreamParser.ChunkType.ANSWER -> {
                    // 首次收到回答内容，触发状态切换
                    val answerAnimator = animators[vh.messageContent.hashCode()]
                    if (answerAnimator == null || answerAnimator.getText().isEmpty()) {
                        onPhaseChange(true)
                    }
                    
                    // 追加到回答区域
                    val animator = getAnimator(vh.messageContent, coroutineScope, onScroll, useMarkdown = true)
                    animator.append(chunk.content)
                }
                
                DeepSeekStreamParser.ChunkType.CONTROL -> {
                    when (chunk.content) {
                        "THINKING_END", "ANSWER_START" -> {
                            onPhaseChange(true)
                        }
                    }
                }
            }
        }
    }

    /**
     * 从"思考中"过渡到"已思考"
     */
    fun transitionToAnswer(vh: ViewHolder) {
        // 计算思考耗时
        val elapsed = (System.currentTimeMillis() - thinkingStartTime) / 1000
        
        // 更新标题
        val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
        headerTitle?.text = "已思考 (用时 ${elapsed} 秒)"
        
        // 思考区域变淡
        vh.thinkingLayout.animate()
            .alpha(0.85f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * 标记完成
     */
    fun markCompleted(vh: ViewHolder, timeCostSec: Long) {
        val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
        headerTitle?.text = "已思考 (用时 ${timeCostSec} 秒)"
        
        // 显示操作按钮
        vh.actionArea.visibility = View.VISIBLE
        vh.actionArea.alpha = 0f
        vh.actionArea.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // 停止动画，确保完整显示
        val thinkingAnimator = animators[vh.thinkingText.hashCode()]
        val answerAnimator = animators[vh.messageContent.hashCode()]
        thinkingAnimator?.stop()
        answerAnimator?.stop()
        // 刷新解析器缓冲
        val flushedChunks = parsers[vh.hashCode()]?.flush().orEmpty()
        val extraThinking = StringBuilder()
        val extraAnswer = StringBuilder()
        for (chunk in flushedChunks) {
            when (chunk.type) {
                DeepSeekStreamParser.ChunkType.THINKING -> extraThinking.append(chunk.content)
                DeepSeekStreamParser.ChunkType.ANSWER -> extraAnswer.append(chunk.content)
                DeepSeekStreamParser.ChunkType.CONTROL -> Unit
            }
        }

        val extraThinkingStr = sanitizeFlushTail(extraThinking.toString())
        val extraAnswerStr = sanitizeFlushTail(extraAnswer.toString())
        if (extraThinkingStr.isNotEmpty()) thinkingAnimator?.appendRaw(extraThinkingStr)
        if (extraAnswerStr.isNotEmpty()) answerAnimator?.appendRaw(extraAnswerStr)

        val thinkingRaw = thinkingAnimator?.getText() ?: extraThinkingStr
        val answerRaw = answerAnimator?.getText() ?: extraAnswerStr
        vh.thinkingLayout.visibility = if (thinkingRaw.isBlank()) View.GONE else View.VISIBLE
        if (thinkingRaw.isNotBlank()) {
            applyMarkdownToHistory(vh.thinkingText, thinkingRaw)
        }
        if (answerRaw.isNotBlank()) {
            applyMarkdownToHistory(vh.messageContent, answerRaw)
        }
    }

    /**
     * 获取思考文本
     */
    fun getThinkingText(vh: ViewHolder): String {
        return animators[vh.thinkingText.hashCode()]?.getText()
            ?: vh.thinkingText.text?.toString()
            ?: ""
    }

    /**
     * 获取回答文本
     */
    fun getAnswerText(vh: ViewHolder): String {
        return animators[vh.messageContent.hashCode()]?.getText()
            ?: vh.messageContent.text?.toString()
            ?: ""
    }

    /**
     * 清理资源
     */
    fun cleanup(vh: ViewHolder) {
        val thinkingId = vh.thinkingText.hashCode()
        val contentId = vh.messageContent.hashCode()
        
        // 停止并清空 animator
        animators[thinkingId]?.clear()
        animators[contentId]?.clear()
        
        // 移除缓存
        animators.remove(thinkingId)
        animators.remove(contentId)
        parsers.remove(vh.hashCode())
    }
    
    /**
     * 为历史消息应用 Markdown 渲染
     * 用于加载历史对话时渲染已保存的消息
     */
    fun applyMarkdownToHistory(textView: TextView, content: String) {
        if (content.isBlank()) {
            textView.text = ""
            return
        }
        MarkdownRenderer.getInstance(textView.context).render(textView, content)
    }

    private fun sanitizeFlushTail(tail: String): String {
        if (tail.isBlank()) return tail

        var core = tail
        val whitespaceSuffix = core.takeLastWhile { it.isWhitespace() }
        if (whitespaceSuffix.isNotEmpty()) {
            core = core.dropLast(whitespaceSuffix.length)
        }

        val tags = listOf(
            "【思考开始】",
            "【思考结束】",
            "【思考】",
            "【回答开始】",
            "【回答结束】",
            "【回答】",
            "<think>",
            "</think>",
            "<思考>",
            "</思考>",
            "<思考：",
            "<思考:"
        )

        for (tag in tags) {
            core = core.replace(tag, "")
        }

        for (tag in tags) {
            if (core.isEmpty()) break
            for (i in 1 until tag.length) {
                val prefix = tag.substring(0, i)
                if (core.endsWith(prefix)) {
                    core = core.dropLast(prefix.length)
                    break
                }
            }
        }

        return core + whitespaceSuffix
    }
}
