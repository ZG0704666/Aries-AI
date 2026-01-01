package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.AppLogger
import android.widget.ImageView
import androidx.collection.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.ai.assistance.operit.R
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.NestedMarkdownProcessor
import com.ai.assistance.operit.util.markdown.SmartString
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamInterceptor
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import com.ai.assistance.operit.util.stream.stream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "MarkdownRenderer"
private const val RENDER_INTERVAL_MS = 200L // 渲染间隔 0.2 秒
private const val FADE_IN_DURATION_MS = 800 // 淡入动画持续时间

/**
 * Converts a mutable [MarkdownNode] to an immutable, stable [MarkdownNodeStable].
 * This function is recursive and converts the entire node tree.
 */
private fun MarkdownNode.toStableNode(): MarkdownNodeStable {
    return MarkdownNodeStable(
        type = this.type,
        content = this.content.toString(),
        children = this.children.map { it.toStableNode() }
    )
}

// XML内容渲染器接口，用于自定义XML渲染
interface XmlContentRenderer {
    @Composable fun RenderXmlContent(xmlContent: String, modifier: Modifier, textColor: Color)
}

// 默认XML渲染器
class DefaultXmlRenderer : XmlContentRenderer {
    @Composable
    override fun RenderXmlContent(xmlContent: String, modifier: Modifier, textColor: Color) {
        val xmlBlockDesc = stringResource(R.string.xml_block)
        
        Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = xmlBlockDesc },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(2.dp)
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.5f
                                                    ),
                                            shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(8.dp)
            ) {
                Text(
                        text = "XML内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = xmlContent,
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                ),
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 扩展函数：去除字符串首尾的所有空白字符（包括空格、制表符、换行符等） 与标准trim()相比，这个函数更明确地处理所有类型的空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

/**
 * StreamMarkdownRenderer的状态类
 * 用于在流式渲染和静态渲染之间共享状态，避免切换时重新计算
 */
@Stable
class StreamMarkdownRendererState {
    // 原始数据收集列表
    val nodes = mutableStateListOf<MarkdownNode>()
    // 用于UI渲染的列表
    val renderNodes = mutableStateListOf<MarkdownNodeStable>()
    // 节点动画状态映射表
    val nodeAnimationStates = mutableStateMapOf<String, Boolean>()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = mutableStateMapOf<Int, Pair<Int, MarkdownNodeStable>>()
    // 保存流式渲染收集的完整内容，用于切换时判断是否需要重新解析
    val collectedContent = SmartString()
    // 渲染器ID
    var rendererId: String = ""
        private set
    
    /**
     * 更新渲染器ID
     */
    fun updateRendererId(id: String) {
        rendererId = id
    }
    
    /**
     * 重置所有状态（用于切换内容源时）
     */
    fun reset() {
        nodes.clear()
        renderNodes.clear()
        nodeAnimationStates.clear()
        conversionCache.clear()
        collectedContent.clear()
    }
}

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() },
        state: StreamMarkdownRendererState? = null,
        fillMaxWidth: Boolean = true
) {
    // 使用传入的state或创建新的state
    val rendererState = state ?: remember { StreamMarkdownRendererState() }
    
    // 原始数据收集列表
    val nodes = rendererState.nodes
    // 用于UI渲染的列表
    val renderNodes = rendererState.renderNodes
    // 节点动画状态映射表
    val nodeAnimationStates = rendererState.nodeAnimationStates
    // 用于在`finally`块中启动协程
    val scope = rememberCoroutineScope()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = rendererState.conversionCache

    // 当流实例变化时，获得一个稳定的渲染器ID
    val rendererId = remember(markdownStream) { 
        val id = "renderer-${System.identityHashCode(markdownStream)}"
        rendererState.updateRendererId(id)
        id
    }

    // 创建一个中间流，用于拦截和批处理渲染更新
    val interceptedStream =
            remember(markdownStream) {
                // 移除时间计算变量和日志
                // 先创建拦截器
                val processor =
                        StreamInterceptor<Char, Char>(
                                sourceStream = markdownStream,
                                onEach = { it } // 先使用简单的转发函数，后面再设置
                        )

                // 然后创建批处理更新器
                val batchUpdater =
                        BatchNodeUpdater(
                                nodes = nodes,
                                renderNodes = renderNodes,
                                conversionCache = conversionCache,
                                nodeAnimationStates = nodeAnimationStates,
                                rendererId = rendererId,
                                isInterceptedStream = processor.interceptedStream,
                                scope = scope
                        )

                // 最后设置拦截器的onEach函数
                processor.setOnEach { char ->
                    // 收集字符到 state 的 collectedContent
                    rendererState.collectedContent + char
                    batchUpdater.startBatchUpdates()
                    char
                }

                processor.interceptedStream
            }

    // 处理Markdown流的变化
    LaunchedEffect(interceptedStream) {
        // 移除时间计算变量和日志

        // 重置状态
        nodes.clear()
        renderNodes.clear()
        rendererState.collectedContent.clear()

        try {
            interceptedStream.streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect {
                    blockGroup ->
                // 移除时间计算变量和日志
                val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

                // 对于水平分割线，内容无关紧要，直接添加节点
                if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                    nodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                    return@collect
                }

                // 判断是否为LaTeX块，如果是，先作为文本节点处理
                val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                // 临时类型：如果是LaTeX块，先作为纯文本处理
                val tempBlockType =
                        if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                val isInlineContainer =
                        tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                                tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                                tempBlockType != MarkdownProcessorType.XML_BLOCK &&
                                tempBlockType != MarkdownProcessorType.PLAN_EXECUTION

                // 为新块创建并添加节点
                val newNode = MarkdownNode(type = tempBlockType)
                nodes.add(newNode)
                val nodeIndex = nodes.lastIndex
                if (isInlineContainer) {
                    // Stream-parse the block stream for inline elements
                    // 将 lastCharWasNewline 提升到这个作用域，以便跨 inlineGroup 保持换行符状态
                    var lastCharWasNewline = false
                    
                    blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                            .collect { inlineGroup ->
                                val originalInlineType =
                                        NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
                                val isInlineLatex =
                                        originalInlineType == MarkdownProcessorType.INLINE_LATEX
                                val tempInlineType =
                                        if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT
                                        else originalInlineType

                                var childNode: MarkdownNode? = null

                                inlineGroup.stream.collect { str ->
                                    // 检查是否为空白内容
                                    val isCurrentCharNewline = str == "\n" || str == "\r\n" || str == "\r"

                                    // 处理连续换行符逻辑
                                    if (isCurrentCharNewline) {
                                        lastCharWasNewline = true
                                        return@collect
                                    }

                                    if (childNode == null) {
                                        childNode = MarkdownNode(type = tempInlineType)
                                        newNode.children.add(childNode!!)
                                    }

                                    if (lastCharWasNewline) {
                                        // 更新父节点和子节点内容
                                        if (newNode.content.isNotEmpty()) {
                                            newNode.content + ("\n" + str)
                                            childNode!!.content + ("\n" + str)
                                        } else {
                                            newNode.content + str
                                            childNode!!.content + str
                                        }
                                        lastCharWasNewline = false
                                    } else {
                                        newNode.content + str
                                        childNode!!.content + str
                                    }

                                    // 更新lastCharWasNewline状态
                                    lastCharWasNewline = isCurrentCharNewline
                                }

                                // 如果是内联LaTeX，在收集完内容后，将节点替换为INLINE_LATEX类型
                                if (isInlineLatex && childNode != null) {
                                    val latexContent = childNode!!.content.toString()
                                    val latexChildNode =
                                            MarkdownNode(
                                                    type = MarkdownProcessorType.INLINE_LATEX,
                                                    initialContent = latexContent
                                            )
                                    val childIndex = newNode.children.lastIndexOf(childNode)
                                    if (childIndex != -1) {
                                        newNode.children[childIndex] = latexChildNode
                                    }
                                }

                                // 优化：如果子节点内容经过trim后为空，则移除该子节点
                                if (childNode != null &&
                                                childNode!!.content.toString().trimAll().isEmpty() &&
                                                originalInlineType ==
                                                        MarkdownProcessorType.PLAIN_TEXT
                                ) {
                                    val lastIndex = newNode.children.lastIndex
                                    if (lastIndex >= 0 && newNode.children[lastIndex] == childNode
                                    ) {
                                        newNode.children.removeAt(lastIndex)
                                    }
                                }
                            }
                } else {
                    // 对于没有内联格式的代码块，直接流式传输内容。
                    blockGroup.stream.collect { contentChunk ->
                        newNode.content + contentChunk
                    }
                }

                // 如果原始类型是LaTeX块，现在收集完毕，将其转换回LaTeX节点
                if (isLatexBlock) {
                    val latexContent = newNode.content.toString()
                    // 创建新的LaTeX节点
                    val latexNode =
                            MarkdownNode(
                                    type = MarkdownProcessorType.BLOCK_LATEX, initialContent = latexContent
                            )
                    // 原地替换节点，以保持索引的稳定性，避免不必要的重组
                    nodes[nodeIndex] = latexNode
                }

                // 移除块处理时间日志
            }

            // 移除收集完成时间日志
        } catch (e: Exception) {
            AppLogger.e(TAG, "【流渲染】Markdown流处理异常: ${e.message}", e)
        } finally {
            // 移除时间计算变量和日志
            synchronizeRenderNodes(nodes, renderNodes, conversionCache, nodeAnimationStates, rendererId, scope)
            // 移除最终同步耗时日志
        }
    }

    // 渲染Markdown内容 - 使用统一的Canvas渲染器
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        key(rendererId) {
            UnifiedMarkdownCanvas(
                nodes = renderNodes,
                rendererId = rendererId,
                nodeAnimationStates = nodeAnimationStates,
                textColor = textColor,
                onLinkClick = onLinkClick,
                xmlRenderer = xmlRenderer,
                modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                fillMaxWidth = fillMaxWidth
            )
        }
    }
}

/** A cache for parsed markdown nodes to improve performance. */
private object MarkdownNodeCache {
    // Cache up to 100 parsed messages
    private val cache = LruCache<String, List<MarkdownNode>>(100)

    fun get(key: String): List<MarkdownNode>? {
        return cache.get(key)
    }

    fun put(key: String, value: List<MarkdownNode>) {
        cache.put(key, value)
    }
}

/** 高性能静态Markdown渲染组件 接受一个完整的字符串，一次性解析和渲染，适用于静态内容显示。 */
@Composable
fun StreamMarkdownRenderer(
        content: String,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() },
        state: StreamMarkdownRendererState? = null,
        fillMaxWidth: Boolean = true
) {
    // 使用传入的state或创建新的state
    val rendererState = state ?: remember(content) { StreamMarkdownRendererState() }
    
    // 使用流式版本相同的渲染器ID生成逻辑
    val rendererId = remember(content) { 
        val id = "static-renderer-${content.hashCode()}"
        rendererState.updateRendererId(id)
        id
    }

    // 使用与流式版本相同的节点列表结构
    val nodes = rendererState.nodes
    val renderNodes = rendererState.renderNodes
    // 添加节点动画状态映射表，与流式版本保持一致
    val nodeAnimationStates = rendererState.nodeAnimationStates
    val scope = rememberCoroutineScope()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = rendererState.conversionCache

    // 当content字符串变化时，一次性完成解析
    LaunchedEffect(content) {
        // 先检查内容是否与流式渲染收集的内容一致，如果一致则跳过解析
        val collectedContentStr = rendererState.collectedContent.toString()
        if (collectedContentStr == content && nodes.isNotEmpty()) {
            // 内容一致且已有节点，跳过解析
            return@LaunchedEffect
        }
        
        // 移除时间计算相关变量
        val cachedNodes = MarkdownNodeCache.get(content)

        if (cachedNodes != null) {
            // 移除时间计算相关的日志
            // 移除时间计算变量
            nodes.clear()
            nodes.addAll(cachedNodes)
            renderNodes.clear()
            renderNodes.addAll(cachedNodes.map { it.toStableNode() })
            // 确保动画状态也被设置
            val newStates = mutableMapOf<String, Boolean>()
            cachedNodes.forEachIndexed { index, node ->
                val nodeKey = "static-node-$rendererId-$index"
                newStates[nodeKey] = true
            }
            nodeAnimationStates.putAll(newStates)
            // 移除应用缓存节点相关时间日志
            return@LaunchedEffect
        }

        launch(Dispatchers.IO) {
            try {
                val parsedNodes = mutableListOf<MarkdownNode>()
                content.stream().streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect {
                        blockGroup ->
                    val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

                    // 对于水平分割线，内容无关紧要，直接添加节点
                    if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                        parsedNodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                        return@collect
                    }

                    // 判断是否为LaTeX块，如果是，先作为文本节点处理
                    val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                    // 临时类型：如果是LaTeX块，先作为纯文本处理
                    val tempBlockType =
                            if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                    val isInlineContainer =
                            tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                                    tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                                    tempBlockType != MarkdownProcessorType.XML_BLOCK &&
                                    tempBlockType != MarkdownProcessorType.PLAN_EXECUTION
                    // 为新块创建并添加节点
                    val newNode = MarkdownNode(type = tempBlockType)
                    parsedNodes.add(newNode)
                    val nodeIndex = parsedNodes.lastIndex

                    // 移除内联处理的时间相关变量

                    if (isInlineContainer) {
                        // Stream-parse the block stream for inline elements
                        // 将 lastCharWasNewline 提升到这个作用域，以便跨 inlineGroup 保持换行符状态
                        var lastCharWasNewline = false
                        
                        blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                                .collect { inlineGroup ->
                                    val originalInlineType =
                                            NestedMarkdownProcessor.getTypeForPlugin(
                                                    inlineGroup.tag
                                            )
                                    val isInlineLatex =
                                            originalInlineType ==
                                                    MarkdownProcessorType.INLINE_LATEX
                                    val tempInlineType =
                                            if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT
                                            else originalInlineType

                                    var childNode: MarkdownNode? = null

                                    inlineGroup.stream.collect { str ->
                                        // 检查是否为空白内容
                                        val isCurrentCharNewline = str == "\n" || str == "\r\n" || str == "\r"

                                        // 处理连续换行符逻辑
                                        if (isCurrentCharNewline) {
                                            lastCharWasNewline = true
                                            return@collect
                                        }

                                        if (childNode == null) {
                                            childNode = MarkdownNode(type = tempInlineType)
                                            newNode.children.add(childNode!!)
                                        }

                                        if (lastCharWasNewline) {
                                            // 更新父节点和子节点内容
                                            if (newNode.content.isNotEmpty()) {
                                                newNode.content + ("\n" + str)
                                                childNode!!.content + ("\n" + str)
                                            } else {
                                                newNode.content + str
                                                childNode!!.content + str
                                            }
                                            lastCharWasNewline = false
                                        } else {
                                            newNode.content + str
                                            childNode!!.content + str
                                        }

                                        // 更新lastCharWasNewline状态
                                        lastCharWasNewline = isCurrentCharNewline
                                    }

                                    // 如果是内联LaTeX，在收集完内容后，将节点替换为INLINE_LATEX类型
                                    if (isInlineLatex && childNode != null) {
                                        val latexContent = childNode!!.content.toString()
                                        val latexChildNode =
                                                MarkdownNode(
                                                        type = MarkdownProcessorType.INLINE_LATEX,
                                                        initialContent = latexContent
                                                )
                                        val childIndex = newNode.children.lastIndexOf(childNode)
                                        if (childIndex != -1) {
                                            newNode.children[childIndex] = latexChildNode
                                        }
                                    }

                                    // 优化：如果子节点内容经过trim后为空，则移除该子节点
                                    if (childNode != null &&
                                                    childNode!!.content.toString().trimAll().isEmpty() &&
                                                    originalInlineType ==
                                                            MarkdownProcessorType.PLAIN_TEXT
                                    ) {
                                        val lastIndex = newNode.children.lastIndex
                                        if (lastIndex >= 0 &&
                                                        newNode.children[lastIndex] == childNode
                                        ) {
                                            newNode.children.removeAt(lastIndex)
                                        }
                                    }
                                }

                        // 移除内联处理耗时相关日志
                    } else {
                        // 对于没有内联格式的代码块，直接流式传输内容。
                        blockGroup.stream.collect { contentChunk ->
                            newNode.content + contentChunk
                        }
                    }

                    // 如果原始类型是LaTeX块，现在收集完毕，将其转换回LaTeX节点
                    if (isLatexBlock) {
                        val latexContent = newNode.content.toString()
                        // 创建新的LaTeX节点
                        val latexNode =
                                MarkdownNode(
                                        type = MarkdownProcessorType.BLOCK_LATEX,
                                        initialContent = latexContent
                                )
                        // 原地替换节点，以保持索引的稳定性，避免不必要的重组
                        parsedNodes[nodeIndex] = latexNode
                    }
                }

                // 移除解析耗时相关日志

                // 将解析完成的节点添加到节点列表，并更新动画状态
                withContext(Dispatchers.Main) {
                    // 保存到缓存，这样下次渲染同样内容时可以直接使用
                    MarkdownNodeCache.put(content, parsedNodes)

                    // 更新UI状态
                    // 清除现有节点
                    nodes.clear()
                    // 批量添加所有节点以减少UI重组次数
                    nodes.addAll(parsedNodes)
                    renderNodes.clear()
                    renderNodes.addAll(parsedNodes.map { it.toStableNode() })
                    // 清理转换缓存，因为内容已完全改变
                    conversionCache.clear()

                    // 更新所有节点的动画状态为可见
                    val newStates = mutableMapOf<String, Boolean>()
                    parsedNodes.forEachIndexed { index, node ->
                        val nodeKey = "static-node-$rendererId-$index"
                        newStates[nodeKey] = true
                    }
                    nodeAnimationStates.putAll(newStates)

                    // 移除UI更新时间相关日志
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "【静态渲染】解析Markdown内容出错: ${e.message}", e)
            }
        }
    }

    // 渲染Markdown内容 - 使用统一的Canvas渲染器
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        key(rendererId) {
            UnifiedMarkdownCanvas(
                nodes = renderNodes,
                rendererId = rendererId,
                nodeAnimationStates = nodeAnimationStates,
                textColor = textColor,
                onLinkClick = onLinkClick,
                xmlRenderer = xmlRenderer,
                modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                fillMaxWidth = fillMaxWidth
            )
        }
    }
}

/**
 * 统一的Markdown Canvas渲染器
 * 真正在一个大Canvas中批量绘制所有节点
 * 
 * 优势：
 * - 使用单个Canvas绘制所有内容，大幅减少Composable数量
 * - 批量绘制，避免为每个节点创建独立的组件
 * - 更高效的流式渲染体验
 * - 只有复杂组件（代码块、表格等）才单独渲染
 */
/**
 * 独立的动画节点组件 - 隔离 alpha 动画状态，避免触发父组件重组
 * 
 * 关键优化：
 * - alpha 动画状态被隔离在这个组件内部
 * - 动画状态变化不会触发外部 Column 重组
 * - 使用 graphicsLayer 避免触发内容重组
 */
@Composable
private fun AnimatedNode(
    nodeKey: String,
    node: MarkdownNodeStable,
    index: Int,
    isVisible: Boolean,
    textColor: Color,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    fillMaxWidth: Boolean,
    isLastNode: Boolean = false
) {
    // alpha 动画状态在这里，变化只影响这个 Composable 的作用域
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
        label = "fadeIn-$nodeKey"
    )
    // 使用 graphicsLayer 代替 alpha modifier
    // graphicsLayer 只影响绘制层，不会触发内容的 recompose
    // CanvasMarkdownNodeRenderer 内部已经使用 key 来控制重组
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        
        // 所有节点都使用CanvasMarkdownNodeRenderer
        // 它内部已经实现了Canvas绘制优化和基于内容长度的 key 控制
        CanvasMarkdownNodeRenderer(
            nodeKey = nodeKey,
            node = node,
            textColor = textColor,
            modifier = Modifier,
            onLinkClick = onLinkClick,
            index = index,
            xmlRenderer = xmlRenderer,
            fillMaxWidth = fillMaxWidth,
            isLastNode = isLastNode
        )
    }
}

@Composable
private fun UnifiedMarkdownCanvas(
    nodes: List<MarkdownNodeStable>,
    rendererId: String,
    nodeAnimationStates: Map<String, Boolean>,
    textColor: Color,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true
) {
    
    val density = LocalDensity.current
    val typography = MaterialTheme.typography

    val lastRenderableIndex = run {
        val idx = nodes.indexOfLast { it.content.isNotEmpty() || it.children.isNotEmpty() }
        if (idx >= 0) idx else nodes.lastIndex
    }
    
    // 缓存字体大小
    val fontSizes = remember(typography) {
        object {
            val bodyMedium = typography.bodyMedium.fontSize
            val headlineLarge = typography.headlineLarge.fontSize
            val headlineMedium = typography.headlineMedium.fontSize
            val headlineSmall = typography.headlineSmall.fontSize
            val titleLarge = typography.titleLarge.fontSize
            val titleMedium = typography.titleMedium.fontSize
            val titleSmall = typography.titleSmall.fontSize
        }
    }
    
    Column(modifier = modifier) {
        
        nodes.forEachIndexed { index, node ->
            val nodeKey = if (rendererId.startsWith("static-")) {
                "static-node-$rendererId-$index"
            } else {
                "node-$rendererId-$index"
            }
            
            key(nodeKey) {
                // 使用独立的 Composable 来隔离 alpha 动画状态
                AnimatedNode(
                    nodeKey = nodeKey,
                    node = node,
                    index = index,
                    isVisible = nodeAnimationStates[nodeKey] ?: true,
                    textColor = textColor,
                    onLinkClick = onLinkClick,
                    xmlRenderer = xmlRenderer,
                    fillMaxWidth = fillMaxWidth,
                    isLastNode = index == lastRenderableIndex  // 判断是否是最后一个可渲染节点
                )
            }
        }
    }
}

/** 批量节点更新器 - 负责将原始节点列表的更新批量应用到渲染节点列表 */
private class BatchNodeUpdater(
        private val nodes: SnapshotStateList<MarkdownNode>,
        private val renderNodes: SnapshotStateList<MarkdownNodeStable>,
        private val conversionCache: MutableMap<Int, Pair<Int, MarkdownNodeStable>>,
        private val nodeAnimationStates: MutableMap<String, Boolean>,
        private val rendererId: String,
        private val isInterceptedStream: Stream<Char>,
        private val scope: CoroutineScope
) {
    private var updateJob: Job? = null

    private var lastLoggedNodesSize: Int = -1
    private var lastLoggedRenderNodesSize: Int = -1
    private var lastLoggedLastSourceLen: Int = -1
    private var lastLoggedLastRenderLen: Int = -1
    private var lastLoggedLastRenderType: MarkdownProcessorType? = null

    fun startBatchUpdates() {
        if (updateJob?.isActive == true) {
            return
        }

        // 创建新的更新任务
        updateJob =
                scope.launch {
                    isInterceptedStream.lock()
                    delay(RENDER_INTERVAL_MS)
                    isInterceptedStream.unlock()

                    performBatchUpdate()
                    updateJob = null
                }
    }

    private fun performBatchUpdate() {
        // 使用synchronizeRenderNodes函数进行节点同步
        synchronizeRenderNodes(nodes, renderNodes, conversionCache, nodeAnimationStates, rendererId, scope)

        val currentNodesSize = nodes.size
        val currentRenderNodesSize = renderNodes.size
        val lastSource = nodes.lastOrNull()
        val lastRender = renderNodes.lastOrNull()
        val lastSourceLen = lastSource?.content?.length ?: -1
        val lastRenderLen = lastRender?.content?.length ?: -1
        val lastRenderType = lastRender?.type

        val shouldLog =
            currentNodesSize != lastLoggedNodesSize ||
                currentRenderNodesSize != lastLoggedRenderNodesSize ||
                lastSourceLen != lastLoggedLastSourceLen ||
                lastRenderLen != lastLoggedLastRenderLen ||
                lastRenderType != lastLoggedLastRenderType

        val hasRegression =
            (lastLoggedNodesSize != -1 && currentNodesSize < lastLoggedNodesSize) ||
                (lastLoggedRenderNodesSize != -1 && currentRenderNodesSize < lastLoggedRenderNodesSize) ||
                (lastLoggedLastSourceLen != -1 && lastSourceLen != -1 && lastSourceLen < lastLoggedLastSourceLen) ||
                (lastLoggedLastRenderLen != -1 && lastRenderLen != -1 && lastRenderLen < lastLoggedLastRenderLen)

        if (shouldLog || hasRegression) {
            val nodeKey = if (currentRenderNodesSize > 0) {
                "node-$rendererId-${currentRenderNodesSize - 1}"
            } else {
                "node-$rendererId--1"
            }
            val renderTail = lastRender?.content?.takeLast(24)
                ?.replace("\n", "\\n")
                ?.replace("\r", "\\r")
                ?.replace("\t", "\\t")
            val sourceTail = lastSource?.content?.toString()?.takeLast(24)
                ?.replace("\n", "\\n")
                ?.replace("\r", "\\r")
                ?.replace("\t", "\\t")

            val msg =
                "[md-batch] rendererId=$rendererId nodeKey=$nodeKey nodesSize=$currentNodesSize renderSize=$currentRenderNodesSize " +
                    "lastSourceLen=$lastSourceLen lastRenderLen=$lastRenderLen lastRenderType=${lastRenderType?.name} " +
                    "sourceTail=\"${sourceTail ?: ""}\" renderTail=\"${renderTail ?: ""}\""

            if (hasRegression) {
                AppLogger.w(TAG, msg)
            }

            lastLoggedNodesSize = currentNodesSize
            lastLoggedRenderNodesSize = currentRenderNodesSize
            lastLoggedLastSourceLen = lastSourceLen
            lastLoggedLastRenderLen = lastRenderLen
            lastLoggedLastRenderType = lastRenderType
        }
    }
}

/** 同步渲染节点 - 确保所有节点都被渲染 在流处理完成或出现异常时调用，确保最终状态一致 */
private fun synchronizeRenderNodes(
    nodes: SnapshotStateList<MarkdownNode>,
    renderNodes: SnapshotStateList<MarkdownNodeStable>,
    conversionCache: MutableMap<Int, Pair<Int, MarkdownNodeStable>>,
    nodeAnimationStates: MutableMap<String, Boolean>,
    rendererId: String,
    scope: CoroutineScope
) {
    val keysToAnimate = mutableListOf<String>()

    // 1. 更新现有节点并添加新节点
    nodes.forEachIndexed { i, sourceNode ->
        val contentLength = sourceNode.content.length
        val cached = conversionCache[i]

        val stableNode = if (cached != null && cached.first == contentLength) {
            cached.second
        } else {
            sourceNode.toStableNode().also {
                conversionCache[i] = contentLength to it
            }
        }

        if (i < renderNodes.size) {
            // 如果节点内容发生变化，则更新
            if (renderNodes[i] != stableNode) {
                val oldNode = renderNodes[i]
                val oldLen = oldNode.content.length
                val newLen = stableNode.content.length
                val oldType = oldNode.type
                val newType = stableNode.type
                val oldChildren = oldNode.children.size
                val newChildren = stableNode.children.size

                if (newLen < oldLen) {
                    val oldTail = oldNode.content.takeLast(24)
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    val newTail = stableNode.content.takeLast(24)
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    AppLogger.w(
                        TAG,
                        "[md-sync] rendererId=$rendererId index=$i nodeKey=node-$rendererId-$i contentLen decreased $oldLen -> $newLen type=$oldType->$newType children=$oldChildren->$newChildren oldTail=\"$oldTail\" newTail=\"$newTail\""
                    )
                }

                renderNodes[i] = stableNode
                // AppLogger.d(TAG, "【渲染性能】最终同步：替换节点 at index $i")
            }
        } else {
            // 添加新节点
            renderNodes.add(stableNode)
            val nodeKey = "node-$rendererId-$i"
            nodeAnimationStates[nodeKey] = false // 准备播放动画
            keysToAnimate.add(nodeKey)
        }
    }

    // 2. 如果源列表变小，则移除多余的节点
    if (renderNodes.size > nodes.size) {
        AppLogger.w(
            TAG,
            "[md-sync] rendererId=$rendererId renderNodes shrinks ${renderNodes.size} -> ${nodes.size}"
        )
    }
    while (renderNodes.size > nodes.size) {
        renderNodes.removeLast()
    }

    // 3. 清理多余的缓存条目
    if (nodes.size < conversionCache.size) {
        (nodes.size until conversionCache.size).forEach {
            conversionCache.remove(it)
        }
    }


    // 启动所有新标记节点的动画
    if (keysToAnimate.isNotEmpty()) {
        scope.launch {
            // 等待下一帧，让 isVisible = false 的状态先生效
            delay(16.milliseconds)
            keysToAnimate.forEach { key ->
                // 检查以防万一节点在此期间被移除
                if (nodeAnimationStates.containsKey(key)) {
                    nodeAnimationStates[key] = true
                }
            }
        }
    }
}

/** 从链接Markdown中提取链接文本 例如：从 [链接文本](https://example.com) 中提取 "链接文本" */
internal fun extractLinkText(linkContent: String): String {
    val startBracket = linkContent.indexOf('[')
    val endBracket = linkContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                linkContent.substring(startBracket + 1, endBracket)
            } else {
                linkContent
            }
    return result
}

/** 从链接Markdown中提取链接URL 例如：从 [链接文本](https://example.com) 中提取 "https://example.com" */
internal fun extractLinkUrl(linkContent: String): String {
    val startParenthesis = linkContent.indexOf('(')
    val endParenthesis = linkContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                linkContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    return result
}

