/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.helper

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ai.phoneagent.R
import com.ai.phoneagent.core.utils.ThinkingTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Aries AI 流式渲染助手
 *
 * 核心改进：使用 WebView 进行实时流式渲染
 * 1. 流式阶段：WebView 实时渲染 markdown（代码块、公式、表格等即时可见）
 * 2. 思考中：SimpleMarkdownRenderer 渲染思考过程（轻量级足够）
 * 3. 完成后：WebView 最终渲染（含 mermaid 图表等）
 */
object StreamRenderHelper {

    data class ViewHolder(
        val thinkingLayout: LinearLayout,
        val thinkingHeader: LinearLayout,
        val thinkingText: TextView,
        val thinkingIndicator: TextView,
        val thinkingContentArea: View,
        val messageContent: TextView,
        val messageContentContainer: FrameLayout,
        val authorName: TextView,
        val actionArea: View,
        val retryButton: View?,
        val copyButton: View?,
        var messageWebView: WebView? = null
    )

    // 思考区域的文本动画器（轻量级，仅用于思考文本）
    private class TextAnimator(
        textView: TextView,
        private val scope: CoroutineScope,
        private val onUpdate: () -> Unit,
        val useMarkdown: Boolean = false
    ) {
        private val viewRef = WeakReference(textView)
        private val textBuilder = StringBuilder()
        private var job: Job? = null
        private var displayedLength = 0

        fun append(delta: String) {
            synchronized(textBuilder) {
                textBuilder.append(delta)
            }

            // 立即更新显示
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
                        if (synchronized(textBuilder) { textBuilder.length } == targetLen) {
                            break
                        }
                        continue
                    }

                    val view = viewRef.get() ?: break

                    val remaining = targetLen - displayedLength
                    val step = when {
                        remaining > 100 -> 15
                        remaining > 50 -> 8
                        remaining > 20 -> 4
                        else -> 1
                    }

                    val nextLen = (displayedLength + step).coerceAtMost(targetLen)
                    val displayText = target.substring(0, nextLen)

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
    private val parsers = ConcurrentHashMap<Int, AriesStreamParser>()
    private var thinkingStartTime = 0L

    // 流式 WebView 状态
    private val streamWebViews = ConcurrentHashMap<Int, WebView>()
    private val streamWebViewReady = ConcurrentHashMap<Int, Boolean>()
    private val streamAnswerBuffers = ConcurrentHashMap<Int, StringBuilder>()
    private val pendingDeltas = ConcurrentHashMap<Int, MutableList<String>>()

    fun bindViews(aiView: View): ViewHolder {
        return ViewHolder(
            thinkingLayout = aiView.findViewById(R.id.thinking_layout),
            thinkingHeader = aiView.findViewById(R.id.thinking_header),
            thinkingText = aiView.findViewById(R.id.thinking_text),
            thinkingIndicator = aiView.findViewById(R.id.thinking_indicator_text),
            thinkingContentArea = aiView.findViewById(R.id.thinking_content_area),
            messageContent = aiView.findViewById(R.id.message_content),
            messageContentContainer = aiView.findViewById(R.id.message_content_container),
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

        // 1. 先清理旧资源
        cleanup(vh)

        // 2. 强制清空 UI
        vh.thinkingText.text = ""
        vh.messageContent.text = ""

        // 3. 记录开始时间
        thinkingStartTime = System.currentTimeMillis()

        // 4. 初始化新的解析器
        parsers[viewId] = AriesStreamParser()

        // 5. 初始化流式回答缓冲区
        streamAnswerBuffers[viewId] = StringBuilder()
        pendingDeltas[viewId] = mutableListOf()

        // 6. 设置 UI 状态
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

    private fun getParser(vh: ViewHolder): AriesStreamParser {
        return parsers.getOrPut(vh.hashCode()) { AriesStreamParser() }
    }

    private fun getAnimator(
        textView: TextView,
        scope: CoroutineScope,
        onScroll: () -> Unit,
        useMarkdown: Boolean = false
    ): TextAnimator {
        val id = textView.hashCode()

        val existing = animators[id]
        if (existing != null) {
            if (existing.useMarkdown != useMarkdown) {
                existing.stop()
                animators.remove(id)
            } else {
                return existing
            }
        }

        val newAnimator = TextAnimator(textView, scope, onScroll, useMarkdown)
        animators[id] = newAnimator
        return newAnimator
    }

    /**
     * 确保流式 WebView 已创建并准备就绪
     */
    private fun ensureStreamWebView(vh: ViewHolder, context: Context, onScroll: () -> Unit) {
        val viewId = vh.hashCode()
        if (streamWebViews.containsKey(viewId)) return

        val manager = MarkdownWebViewManager.getInstance(context)
        var webViewRef: WebView? = null

        val webView = manager.createWebView(
            context = context,
            onHeightChanged = { heightPx ->
                webViewRef?.let { wv ->
                    val lp = wv.layoutParams
                    if (lp != null && lp.height != heightPx) {
                        lp.height = heightPx
                        wv.layoutParams = lp
                    }
                }
                onScroll()
            },
            onReady = {
                streamWebViewReady[viewId] = true
                // 发送积攒的 pending deltas
                val pending = pendingDeltas[viewId]
                if (pending != null && pending.isNotEmpty()) {
                    webViewRef?.let { wv ->
                        for (delta in pending) {
                            manager.appendStreamDelta(wv, delta)
                        }
                        pending.clear()
                    }
                }
            }
        )
        webViewRef = webView

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        webView.layoutParams = lp

        // 隐藏 TextView，显示 WebView
        vh.messageContent.visibility = View.GONE
        vh.messageContentContainer.addView(webView)
        vh.messageWebView = webView
        streamWebViews[viewId] = webView
        streamWebViewReady[viewId] = false
    }

    /**
     * 处理 reasoning_content 增量（来自 API 的思考字段）
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

        // 追加到思考区域，使用 SimpleMarkdownRenderer（思考区域够用）
        val animator = getAnimator(vh.thinkingText, coroutineScope, onScroll, useMarkdown = true)
        animator.append(delta)

        vh.thinkingText.post { onScroll() }
    }

    /**
     * 处理 content 增量 - 使用 WebView 实时流式渲染
     */
    fun processContentDelta(
        vh: ViewHolder,
        delta: String,
        coroutineScope: CoroutineScope,
        context: Context,
        onScroll: () -> Unit,
        onPhaseChange: (Boolean) -> Unit
    ) {
        if (delta.isEmpty()) return

        val viewId = vh.hashCode()
        val parser = getParser(vh)
        val chunks = parser.processContentDelta(delta)

        for (chunk in chunks) {
            when (chunk.type) {
                AriesStreamParser.ChunkType.THINKING -> {
                    // 确保思考区域可见并展开（如果在思考中）
                    val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
                    if (headerTitle?.text == "思考中") {
                        vh.thinkingLayout.visibility = View.VISIBLE
                        vh.thinkingLayout.alpha = 1f
                        vh.thinkingText.visibility = View.VISIBLE
                        vh.thinkingContentArea.visibility = View.VISIBLE
                    }

                    val animator = getAnimator(vh.thinkingText, coroutineScope, onScroll, useMarkdown = true)
                    animator.append(chunk.content)
                    vh.thinkingText.post { onScroll() }
                }
                AriesStreamParser.ChunkType.CONTROL -> {
                    if (chunk.content == "ANSWER_START" || chunk.content == "THINKING_END") {
                        onPhaseChange(true)
                    }
                }
                AriesStreamParser.ChunkType.ANSWER -> {
                    if (chunk.content.isEmpty()) continue

                    val buffer = streamAnswerBuffers.getOrPut(viewId) { StringBuilder() }
                    val isFirstChunk = buffer.isEmpty()
                    buffer.append(chunk.content)

                    if (isFirstChunk) {
                        onPhaseChange(true)
                        ensureStreamWebView(vh, context, onScroll)
                    }

                    val webView = streamWebViews[viewId]
                    val isReady = streamWebViewReady[viewId] == true
                    if (webView != null && isReady) {
                        MarkdownWebViewManager.getInstance(context).appendStreamDelta(webView, chunk.content)
                    } else {
                        pendingDeltas.getOrPut(viewId) { mutableListOf() }.add(chunk.content)
                    }
                }
            }
        }
    }

    /**
     * 从"思考中"过渡到"已思考"
     */
    fun transitionToAnswer(vh: ViewHolder) {
        val elapsed = (System.currentTimeMillis() - thinkingStartTime) / 1000

        val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
        headerTitle?.text = "已思考 (用时 ${elapsed} 秒)"

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
        val viewId = vh.hashCode()
        val headerTitle = vh.thinkingHeader.getChildAt(0) as? TextView
        headerTitle?.text = "已思考 (用时 ${timeCostSec} 秒)"

        // 显示操作按钮
        vh.actionArea.visibility = View.VISIBLE
        vh.actionArea.alpha = 0f
        vh.actionArea.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // 停止思考动画
        val thinkingAnimator = animators[vh.thinkingText.hashCode()]
        thinkingAnimator?.stop()

        // 刷新解析器缓冲
        val flushedChunks = parsers[viewId]?.flush().orEmpty()
        val extraThinking = StringBuilder()
        for (chunk in flushedChunks) {
            when (chunk.type) {
                AriesStreamParser.ChunkType.THINKING -> extraThinking.append(chunk.content)
                AriesStreamParser.ChunkType.ANSWER -> Unit
                AriesStreamParser.ChunkType.CONTROL -> Unit
            }
        }

        val extraThinkingStr = sanitizeFlushTail(extraThinking.toString())
        if (extraThinkingStr.isNotEmpty()) thinkingAnimator?.appendRaw(extraThinkingStr)

        val thinkingRaw = thinkingAnimator?.getText() ?: extraThinkingStr
        val answerRaw = streamAnswerBuffers[viewId]?.toString() ?: ""

        vh.thinkingLayout.visibility = if (thinkingRaw.isBlank()) View.GONE else View.VISIBLE
        if (thinkingRaw.isNotBlank()) {
            applyMarkdownToHistory(vh.thinkingText, thinkingRaw)
        }

        // 通知 WebView 流式结束，做最终渲染
        val webView = streamWebViews[viewId]
        if (webView != null && answerRaw.isNotBlank()) {
            val manager = MarkdownWebViewManager.getInstance(webView.context)
            manager.finishStream(webView)
        } else if (answerRaw.isNotBlank()) {
            // 没有 WebView（理论上不该发生），回退到旧逻辑
            if (MarkdownWebViewManager.shouldUseWebView(answerRaw)) {
                upgradeToWebView(vh, answerRaw, vh.messageContent.context)
            } else {
                applyMarkdownToHistory(vh.messageContent, answerRaw)
            }
        }

        // 清理流式状态（但保留 WebView 引用以便后续操作）
        streamWebViewReady.remove(viewId)
        pendingDeltas.remove(viewId)
    }

    /**
     * 获取思考文本
     */
    fun getThinkingText(vh: ViewHolder): String {
        val animatorText = animators[vh.thinkingText.hashCode()]?.getText().orEmpty()
        if (animatorText.isNotBlank()) return animatorText
        return vh.thinkingText.text?.toString().orEmpty()
    }

    /**
     * 获取回答文本
     */
    fun getAnswerText(vh: ViewHolder): String {
        val viewId = vh.hashCode()
        val bufferText = streamAnswerBuffers[viewId]?.toString().orEmpty()
        if (bufferText.isNotBlank()) return bufferText
        return vh.messageContent.text?.toString().orEmpty()
    }

    /**
     * 清理资源
     */
    fun cleanup(vh: ViewHolder) {
        val viewId = vh.hashCode()
        val thinkingId = vh.thinkingText.hashCode()
        val contentId = vh.messageContent.hashCode()

        // 停止并清空 animator
        animators[thinkingId]?.clear()
        animators[contentId]?.clear()

        // 移除缓存
        animators.remove(thinkingId)
        animators.remove(contentId)
        parsers.remove(viewId)

        // 清理流式状态
        streamWebViews.remove(viewId)
        streamWebViewReady.remove(viewId)
        streamAnswerBuffers.remove(viewId)
        pendingDeltas.remove(viewId)

        // 清理 WebView
        vh.messageWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            MarkdownWebViewManager.getInstance(wv.context).destroyWebView(wv)
        }
        vh.messageWebView = null
    }

    /**
     * 为历史消息应用 Markdown 渲染
     */
    fun applyMarkdownToHistory(textView: TextView, content: String) {
        if (content.isBlank()) {
            textView.text = ""
            return
        }
        MarkdownRenderer.getInstance(textView.context).render(textView, content)
    }

    /**
     * 为历史消息应用 Markdown 渲染（含 WebView 升级支持）
     */
    fun applyMarkdownToHistory(textView: TextView, container: FrameLayout, content: String) {
        if (content.isBlank()) {
            textView.text = ""
            return
        }
        if (MarkdownWebViewManager.shouldUseWebView(content)) {
            val manager = MarkdownWebViewManager.getInstance(textView.context)
            var webViewRef: WebView? = null
            val webView = manager.createWebView(
                context = textView.context,
                onHeightChanged = { heightPx ->
                    webViewRef?.let { wv ->
                        val lp = wv.layoutParams
                        if (lp != null && lp.height != heightPx) {
                            lp.height = heightPx
                            wv.layoutParams = lp
                        }
                    }
                },
                onReady = {
                    webViewRef?.let { wv -> manager.renderContent(wv, content) }
                }
            )
            webViewRef = webView
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            webView.layoutParams = lp
            textView.visibility = View.GONE
            container.addView(webView)
        } else {
            MarkdownRenderer.getInstance(textView.context).render(textView, content)
        }
    }

    /**
     * 将 message_content 区域升级为 WebView 渲染
     */
    private fun upgradeToWebView(vh: ViewHolder, markdown: String, context: Context) {
        if (vh.messageWebView != null) return
        val manager = MarkdownWebViewManager.getInstance(context)
        var webViewRef: WebView? = null

        val webView = manager.createWebView(
            context = context,
            onHeightChanged = { heightPx ->
                webViewRef?.let { wv ->
                    val lp = wv.layoutParams
                    if (lp != null && lp.height != heightPx) {
                        lp.height = heightPx
                        wv.layoutParams = lp
                    }
                }
            },
            onReady = {
                webViewRef?.let { wv ->
                    manager.renderContent(wv, markdown)
                }
            }
        )
        webViewRef = webView

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        webView.layoutParams = lp

        vh.messageContent.visibility = View.GONE
        vh.messageContentContainer.addView(webView)
        vh.messageWebView = webView
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
