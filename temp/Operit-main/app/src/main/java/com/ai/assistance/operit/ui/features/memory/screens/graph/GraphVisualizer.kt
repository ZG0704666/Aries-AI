package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Rect
import com.ai.assistance.operit.util.AppLogger

// 辅助函数：判断两个矩形是否相交
private fun Rect.intersects(other: Rect): Boolean {
    return left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphVisualizer(
    graph: Graph,
    modifier: Modifier = Modifier,
    selectedNodeId: String? = null,
    boxSelectedNodeIds: Set<String> = emptySet(),
    isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
    linkingNodeIds: List<String> = emptyList(),
    selectedEdgeId: Long? = null,
    onNodeClick: (Node) -> Unit,
    onEdgeClick: (Edge) -> Unit,
    onNodesSelected: (Set<String>) -> Unit // 新增：框选完成后的回调
) {
    AppLogger.d("GraphVisualizer", "Recomposing. isBoxSelectionMode: $isBoxSelectionMode")
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) } // 用于绘制选择框

    // 当退出框选模式时，确保清除选框
    LaunchedEffect(isBoxSelectionMode) {
        if (!isBoxSelectionMode) {
            selectionRect = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // 计算可见区域（世界坐标）
        // 将屏幕坐标转换为世界坐标：worldPos = (screenPos - offset) / scale
        val visibleWorldRect = remember(scale, offset, width, height) {
            val left = -offset.x / scale
            val top = -offset.y / scale
            val right = (width - offset.x) / scale
            val bottom = (height - offset.y) / scale
            // 扩展可见区域，以便计算稍微超出屏幕的节点（扩展1.5倍）
            val padding = max(width, height) / scale * 0.5f
            Rect(
                left = left - padding,
                top = top - padding,
                right = right + padding,
                bottom = bottom + padding
            )
        }

        // Force-directed layout simulation
        LaunchedEffect(graph.nodes, width, height) {
            if (graph.nodes.isNotEmpty()) {
                // 智能地更新位置：保留现有节点位置，只为新节点分配位置
                val currentPositions = nodePositions
                val newPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)

                graph.nodes.forEach { node ->
                    // 如果节点已经存在，则保留其位置；否则，在中心附近随机放置新节点
                    newPositions[node.id] = currentPositions[node.id] ?: Offset(
                        (center.x + (Math.random() * 200 - 100)).toFloat(),
                        (center.y + (Math.random() * 200 - 100)).toFloat()
                    )
                }

                withContext(Dispatchers.Main) {
                    nodePositions = newPositions
                }

                // Run simulation in a background coroutine
                launch(Dispatchers.Default) {
                    val positions = newPositions.toMutableMap()
                    val velocities = mutableMapOf<String, Offset>()
                    graph.nodes.forEach { node -> velocities[node.id] = Offset.Zero }

                    val nodeArray = graph.nodes.toTypedArray() // 转换为数组，提高访问速度
                    val nodeCount = graph.nodes.size

                    // Tuned parameters for a more stable and visually pleasing layout
                    // 根据节点数量动态调整迭代次数，避免节点过多时计算时间过长
                    val iterations = when {
                        nodeCount > 200 -> 150  // 大量节点时减少迭代次数
                        nodeCount > 100 -> 200  // 中等数量节点
                        nodeCount > 50 -> 250   // 少量节点
                        else -> 300             // 很少节点，使用完整迭代
                    }
                    val repulsionStrength = 150000f  // Stronger repulsion to prevent clumping
                    val attractionStrength = 0.15f   // Increased spring force to pull connected nodes closer
                    val idealEdgeLength = 200f       // Shorter ideal distance for edges to bring connected nodes closer
                    val gravityStrength = 0.02f      // Weaker gravity to allow graph to spread out
                    val maxSpeed = 50f
                    val damping = 0.95f
                    
                    // 空间分区参数：网格大小（只计算网格内和相邻网格的节点）
                    val gridCellSize = idealEdgeLength * 2.5f // 网格大小约为理想边长的2.5倍
                    val maxRepulsionDistance = idealEdgeLength * 3f // 超过此距离的节点不计算排斥力

                    for (i in 0 until iterations) {
                        // 所有节点都参与计算
                        val forces = mutableMapOf<String, Offset>()
                        // 为所有节点初始化力
                        graph.nodes.forEach { node -> forces[node.id] = Offset.Zero }

                        // 空间分区优化：使用网格系统计算附近节点的排斥力
                        // 1. 构建网格：将所有节点分配到网格单元中
                        val grid = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
                        for (idx in nodeArray.indices) {
                            val pos = positions[nodeArray[idx].id] ?: continue
                            val gridX = (pos.x / gridCellSize).toInt()
                            val gridY = (pos.y / gridCellSize).toInt()
                            val key = gridX to gridY
                            grid.getOrPut(key) { mutableListOf() }.add(idx)
                        }

                        // 2. 只计算同一网格和相邻网格中的节点对
                        for ((gridKey, nodeIndices) in grid) {
                            val (gridX, gridY) = gridKey
                            
                            // 检查当前网格和8个相邻网格（3x3区域）
                            for (dx in -1..1) {
                                for (dy in -1..1) {
                                    val neighborKey = (gridX + dx) to (gridY + dy)
                                    val neighborIndices = grid[neighborKey] ?: continue
                                    
                                    // 计算当前网格和相邻网格中节点对的排斥力
                                    for (idx1 in nodeIndices) {
                                        val n1 = nodeArray[idx1]
                                        val p1 = positions[n1.id] ?: continue
                                        
                                        for (idx2 in neighborIndices) {
                                            // 避免重复计算和自比较：只处理 idx1 < idx2 的情况
                                            // 这样可以确保每对节点只计算一次
                                            if (idx1 >= idx2) continue
                                            
                                            val n2 = nodeArray[idx2]
                                            val p2 = positions[n2.id] ?: continue
                                            
                                            // 使用曼哈顿距离快速筛选：如果曼哈顿距离太远，跳过
                                            val deltaX = kotlin.math.abs(p1.x - p2.x)
                                            val deltaY = kotlin.math.abs(p1.y - p2.y)
                                            val manhattanDistance = deltaX + deltaY
                                            
                                            // 快速跳过：如果曼哈顿距离超过阈值，跳过精确计算
                                            if (manhattanDistance > maxRepulsionDistance * 1.5f) continue
                                            
                                            // 对于需要计算的节点，使用欧几里得距离（更精确）
                                            val delta = p1 - p2
                                            val distanceSq = deltaX * deltaX + deltaY * deltaY
                                            val distance = kotlin.math.sqrt(distanceSq).coerceAtLeast(1f)
                                            
                                            // 如果距离太远，跳过（使用距离平方比较，避免开方）
                                            if (distanceSq > maxRepulsionDistance * maxRepulsionDistance) continue
                                            
                                            val force = repulsionStrength / distanceSq
                                            val invDistance = 1f / distance
                                            val forceX = (p1.x - p2.x) * invDistance * force
                                            val forceY = (p1.y - p2.y) * invDistance * force
                                            
                                            // 排斥力是相互的，方向相反
                                            forces[n1.id] = forces[n1.id]!! + Offset(forceX, forceY)
                                            forces[n2.id] = forces[n2.id]!! - Offset(forceX, forceY)
                                        }
                                    }
                                }
                            }
                        }

                        // Attractive forces (spring model) along edges
                        // 计算所有边的吸引力
                        for (edge in graph.edges) {
                            val sourcePos = positions[edge.sourceId]
                            val targetPos = positions[edge.targetId]
                            if (sourcePos != null && targetPos != null) {
                                val delta = targetPos - sourcePos
                                val deltaX = delta.x
                                val deltaY = delta.y
                                val distanceSq = deltaX * deltaX + deltaY * deltaY
                                val distance = kotlin.math.sqrt(distanceSq).coerceAtLeast(1f)
                                
                                // Spring force: k * (x - ideal_length)
                                // Weight-based attraction: stronger edges pull nodes closer
                                val weightMultiplier = 1f + edge.weight * 0.5f // Increase attraction for stronger edges
                                val adjustedAttraction = attractionStrength * weightMultiplier
                                val force = adjustedAttraction * (distance - idealEdgeLength)
                                val invDistance = 1f / distance
                                val forceX = deltaX * invDistance * force
                                val forceY = deltaY * invDistance * force
                                
                                forces[edge.sourceId] = forces[edge.sourceId]!! + Offset(forceX, forceY)
                                forces[edge.targetId] = forces[edge.targetId]!! - Offset(forceX, forceY)
                            }
                        }
                        
                        // Gravity force towards the center
                        // 对所有节点计算重力
                        for (node in graph.nodes) {
                            val p = positions[node.id] ?: continue
                            val delta = center - p
                            // 重力使用简单的线性力，不需要归一化方向
                            forces[node.id] = forces[node.id]!! + delta * gravityStrength
                        }

                        // Update positions based on forces (更新所有节点的位置)
                        for (node in graph.nodes) {
                            val nodeForce = forces[node.id] ?: Offset.Zero
                            var nodeVelocity = velocities[node.id]!!
                            nodeVelocity = (nodeVelocity + nodeForce) * damping

                            val speed = nodeVelocity.getDistance()
                            if (speed > maxSpeed) {
                                nodeVelocity = nodeVelocity * (maxSpeed / speed)
                            }

                            velocities[node.id] = nodeVelocity
                            positions[node.id] = positions[node.id]!! + nodeVelocity
                        }

                        // Update UI
                        withContext(Dispatchers.Main) {
                            nodePositions = positions.toMap()
                        }
                        delay(16)
                    }
                }
            } else {
                 withContext(Dispatchers.Main) {
                    nodePositions = emptyMap()
                }
            }
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isBoxSelectionMode) { // KEY CHANGE: Relaunch gestures when mode changes
                AppLogger.d("GraphVisualizer", "pointerInput recomposed/restarted. NEW MODE: ${if (isBoxSelectionMode) "BoxSelect" else "Normal"}")
                coroutineScope {
                    if (isBoxSelectionMode) {
                        AppLogger.d("GraphVisualizer", "Setting up GESTURES FOR BOX SELECTION mode.")
                        // --- 框选模式下的手势 ---
                        // 1. 拖拽框选 (排他性，禁用平移/缩放)
                        launch {
                            var dragStart: Offset? = null
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragStart")
                                    dragStart = startOffset
                                    selectionRect = createNormalizedRect(startOffset, startOffset)
                                },
                                onDrag = { change, _ ->
                                    dragStart?.let { start ->
                                        selectionRect =
                                            createNormalizedRect(start, change.position)
                                    }
                                },
                                onDragEnd = {
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragEnd")
                                    selectionRect?.let { rect ->
                                        val selectedIds = nodePositions.filter { (_, pos) ->
                                            val viewPos = pos * scale + offset
                                            rect.contains(viewPos)
                                        }.keys
                                        onNodesSelected(selectedIds)
                                    }
                                    selectionRect = null
                                    dragStart = null
                                },
                                onDragCancel = {
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragCancel")
                                    selectionRect = null
                                    dragStart = null
                                }
                            )
                        }
                        // 2. 点击单选/取消
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                AppLogger.d("GraphVisualizer", "BoxSelect: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                }
                            })
                        }
                    } else {
                        AppLogger.d("GraphVisualizer", "Setting up GESTURES FOR NORMAL mode.")
                        // --- 普通模式下的手势 ---
                        // 1. 平移和缩放
                        launch {
                            AppLogger.d("GraphVisualizer", "Launching detectTransformGestures (Pan/Zoom).")
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                                offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                                scale = newScale
                            }
                        }
                        // 2. 点击
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                AppLogger.d("GraphVisualizer", "Normal Mode: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                val clickedEdge = if (clickedNode == null) {
                                    graph.edges.find { edge ->
                                        val sourcePos = nodePositions[edge.sourceId]
                                        val targetPos = nodePositions[edge.targetId]
                                        if (sourcePos != null && targetPos != null) {
                                            val nodeRadius = 50f * scale
                                            val sourceCenter = sourcePos * scale + offset
                                            val targetCenter = targetPos * scale + offset
                                            
                                            // 计算从源节点中心到目标节点中心的方向
                                            val delta = targetCenter - sourceCenter
                                            val distance = delta.getDistance().coerceAtLeast(1f)
                                            val direction = delta / distance
                                            
                                            // 计算起点和终点：在从中心到中心的直线上，找到节点边缘的交点
                                            val start = sourceCenter + direction * nodeRadius
                                            val end = targetCenter - direction * nodeRadius
                                            
                                            distanceToSegment(tapOffset, start, end) < 20f
                                        } else false
                                    }
                                } else null

                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                } else if (clickedEdge != null) {
                                    onEdgeClick(clickedEdge)
                                }
                            })
                        }
                    }
                }
            }
        ) {
            // 计算屏幕可见区域（屏幕坐标）
            val screenVisibleRect = Rect(0f, 0f, size.width, size.height)
            val nodeRadius = 50f * scale
            
            // 绘制选框
            selectionRect?.let { rect ->
                drawRect(
                    color = colorScheme.primary.copy(alpha = 0.3f),
                    topLeft = rect.topLeft,
                    size = rect.size
                )
                drawRect(
                    color = colorScheme.primary,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 6f)
                )
            }

            // Edges - 只渲染至少有一个端点在可见区域内的边
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    // 计算屏幕坐标
                    val sourceCenter = sourcePos * scale + offset
                    val targetCenter = targetPos * scale + offset
                    
                    // 检查是否至少有一个端点在可见区域内（考虑节点半径）
                    val sourceVisible = screenVisibleRect.intersects(
                        Rect(
                            left = sourceCenter.x - nodeRadius,
                            top = sourceCenter.y - nodeRadius,
                            right = sourceCenter.x + nodeRadius,
                            bottom = sourceCenter.y + nodeRadius
                        )
                    )
                    val targetVisible = screenVisibleRect.intersects(
                        Rect(
                            left = targetCenter.x - nodeRadius,
                            top = targetCenter.y - nodeRadius,
                            right = targetCenter.x + nodeRadius,
                            bottom = targetCenter.y + nodeRadius
                        )
                    )
                    
                    // 如果两个端点都不在可见区域内，跳过渲染
                    if (!sourceVisible && !targetVisible) return@forEach
                    
                    val sourceRadius = nodeRadius
                    val targetRadius = nodeRadius
                    
                    // 计算从源节点中心到目标节点中心的方向
                    val delta = targetCenter - sourceCenter
                    val distance = delta.getDistance().coerceAtLeast(1f)
                    val direction = delta / distance
                    
                    // 计算起点和终点：在从中心到中心的直线上，找到节点边缘的交点
                    val start = sourceCenter + direction * sourceRadius
                    val end = targetCenter - direction * targetRadius
                    
                    val strokeWidth = (edge.weight * 3f).coerceIn(1f, 12f)
                    
                    // 如果是跨文件夹连接，使用虚线绘制
                    if (edge.isCrossFolderLink) {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    } else {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth
                        )
                    }
                    
                    edge.label?.let { label ->
                        val center = (start + end) / 2f
                        // 只渲染标签中心在可见区域内的标签
                        if (screenVisibleRect.contains(center)) {
                            val angle = atan2(end.y - start.y, end.x - start.x)
                            
                            val textLayoutResult = textMeasurer.measure(
                                text = AnnotatedString(label),
                                style = TextStyle(fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                            )
                            
                            // Simple collision avoidance for label, could be improved
                            val labelOffset = if (angle > -Math.PI / 2 && angle < Math.PI / 2) -textLayoutResult.size.height.toFloat() else 0f
                            
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    x = center.x - textLayoutResult.size.width / 2,
                                    y = center.y + labelOffset
                                )
                            )
                        }
                    }
                }
            }

            // Nodes - 只渲染可见区域内的节点
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val screenPosition = position * scale + offset
                    
                    // 检查节点是否在可见区域内（考虑节点半径）
                    val nodeRect = Rect(
                        left = screenPosition.x - nodeRadius,
                        top = screenPosition.y - nodeRadius,
                        right = screenPosition.x + nodeRadius,
                        bottom = screenPosition.y + nodeRadius
                    )
                    
                    // 如果节点不在可见区域内，跳过渲染
                    if (!screenVisibleRect.intersects(nodeRect)) return@forEach
                    
                    val isSelected = node.id == selectedNodeId
                    val isLinkingCandidate = node.id in linkingNodeIds
                    val isBoxSelected = node.id in boxSelectedNodeIds // 新增：检查是否被框选
                    drawNode(
                        node = node,
                        position = screenPosition,
                        radius = nodeRadius,
                        textMeasurer = textMeasurer,
                        colorScheme = colorScheme,
                        isSelected = isSelected,
                        isLinkingCandidate = isLinkingCandidate,
                        isBoxSelected = isBoxSelected // 新增：传递框选状态
                    )
                }
            }
        }
    }
}

/**
 * 根据起始点和结束点创建标准化的矩形，确保left <= right, top <= bottom。
 */
private fun createNormalizedRect(start: Offset, end: Offset): Rect {
    return Rect(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y)
    )
}

private fun distanceToSegment(p: Offset, start: Offset, end: Offset): Float {
    val l2 = (start - end).getDistanceSquared()
    if (l2 == 0f) return (p - start).getDistance()
    val t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2
    val tClamped = t.coerceIn(0f, 1f)
    val projection = start + (end - start) * tClamped
    return (p - projection).getDistance()
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: Node,
    position: Offset,
    radius: Float,
    textMeasurer: TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme,
    isSelected: Boolean,
    isLinkingCandidate: Boolean,
    isBoxSelected: Boolean // 新增：接收框选状态
) {
    val color = if (isSelected) colorScheme.secondary else node.color
    drawCircle(
        color = color,
        radius = radius,
        center = position
    )

    // 框选高亮
    if (isBoxSelected) {
        drawCircle(
            color = colorScheme.tertiary, // 使用主题的第三色作为高亮
            radius = radius + 10f, // 比节点稍大
            center = position,
            style = Stroke(width = 8f)
        )
    }

    if (isLinkingCandidate) {
        drawCircle(
            color = colorScheme.error,
            radius = radius,
            center = position,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
    
    // 设置文本最大宽度，让文本可以自动换行
    // 先测量单个字符的宽度，确保每行至少有10个字符
    val textStyle = TextStyle(fontSize = 12.sp, color = colorScheme.onSurface)
    val singleCharWidth = textMeasurer.measure(
        text = AnnotatedString("中"), // 使用中文字符测量，因为中文通常比英文宽
        style = textStyle
    ).size.width.toFloat()
    val minLineWidth = singleCharWidth * 10 // 每行至少10个字符的宽度
    
    val maxTextWidth = maxOf(radius * 2.5f, minLineWidth) // 文本最大宽度，但至少能容纳10个字符
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(node.label),
        style = textStyle,
        constraints = Constraints(maxWidth = maxTextWidth.toInt())
    )
    
    val textPosition = Offset(
        x = position.x - textLayoutResult.size.width / 2,
        y = position.y - textLayoutResult.size.height / 2
    )
    
    drawText(textLayoutResult, topLeft = textPosition)
} 