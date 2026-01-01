package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.plan.ExecutionGraph
import com.ai.assistance.operit.api.chat.plan.PlanParser
import com.ai.assistance.operit.api.chat.plan.TaskNode
import com.google.gson.Gson
import java.util.regex.Pattern

private enum class TaskStatus {
    TODO, IN_PROGRESS, COMPLETED, FAILED
}

private data class PlanExecutionState(
    val graph: ExecutionGraph?,
    val taskStatuses: Map<String, TaskStatus>,
    val logs: List<String>,
    val summary: String?,
    val error: String?
)

private fun parsePlanStream(content: String): PlanExecutionState {
    val graphRegex = "<graph><!\\[CDATA\\[(.*?)]]></graph>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val updateRegex = """<update id="([^"]+)" status="([^"]+)"/>""".toRegex()
    val logRegex = "<log>(.*?)</log>".toRegex()
    val summaryRegex = "<summary>(.*?)</summary>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val errorRegex = "<error>(.*?)</error>".toRegex()

    var graph: ExecutionGraph? = null
    graphRegex.find(content)?.let {
        val json = it.groupValues[1]
        try {
            graph = Gson().fromJson(json, ExecutionGraph::class.java)
        } catch (e: Exception) {
            // Ignore parsing errors for now
        }
    }

    val taskStatuses = mutableMapOf<String, TaskStatus>()
    updateRegex.findAll(content).forEach {
        val id = it.groupValues[1]
        val status = when (it.groupValues[2]) {
            "IN_PROGRESS" -> TaskStatus.IN_PROGRESS
            "COMPLETED" -> TaskStatus.COMPLETED
            "FAILED" -> TaskStatus.FAILED
            else -> TaskStatus.TODO
        }
        taskStatuses[id] = status
    }

    val logs = logRegex.findAll(content).map { it.groupValues[1] }.toList()
    val summary = summaryRegex.find(content)?.groupValues?.get(1)
    val error = errorRegex.find(content)?.groupValues?.get(1)

    return PlanExecutionState(graph, taskStatuses, logs, summary, error)
}

@Composable
fun PlanExecutionRenderer(
    modifier: Modifier = Modifier,
    content: String
) {
    val state by remember(content) {
        derivedStateOf { parsePlanStream(content) }
    }

    if (state.graph != null) {
        ExecutionGraphDisplay(
            graph = state.graph!!,
            taskStatuses = state.taskStatuses,
            logs = state.logs,
            summary = state.summary,
            modifier = modifier
        )
    } else if (state.error != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Error: ${state.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Preparing execution plan...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ExecutionGraphDisplay(
    graph: ExecutionGraph,
    taskStatuses: Map<String, TaskStatus>,
    logs: List<String>,
    summary: String?,
    modifier: Modifier = Modifier
) {
    val planGraphDesc = stringResource(R.string.plan_execution_graph)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = planGraphDesc },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            Text(
                text = "Execution Plan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            }

            // Compact graph area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .padding(4.dp)
            ) {
                WorkflowGraph(
                    graph = graph,
                    taskStatuses = taskStatuses,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Summary section
            if (summary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                        )
                    }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Target: ${graph.finalSummaryInstruction}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Compact logs
            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.height(36.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs.takeLast(3)) { log ->
                        Text(
                            text = log.removePrefix("<log>").removeSuffix("</log>"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
}

private data class NodePositionData(
    val taskNode: TaskNode,
    val position: Offset,
    val size: Size
)

@Composable
private fun WorkflowGraph(
    graph: ExecutionGraph,
    taskStatuses: Map<String, TaskStatus>,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()

    val nodeAppearance = remember { Animatable(0f) }

    LaunchedEffect(graph.tasks) {
        nodeAppearance.snapTo(0f)
        nodeAppearance.animateTo(1f, animationSpec = tween(600))
    }

    val sortedTasks = remember(graph) {
        try {
            PlanParser.topologicalSort(graph)
        } catch (e: Exception) {
            graph.tasks // fallback
        }
    }

    val nodePositions = remember(sortedTasks, canvasSize) {
        calculateNodePositions(sortedTasks, graph, canvasSize)
    }

    // Enhanced color scheme
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    // More professional and semantic color scheme
    val todoColor = Color(0xFF9CA3AF)       // Cool Gray 400 - Neutral for pending tasks
    val inProgressColor = Color(0xFF3B82F6)  // Blue 500 - Active and standard for progress
    val completedColor = Color(0xFF22C55E)   // Green 500 - Clear success state
    val failedColor = Color(0xFFEF4444)      // Red 500 - Unmistakable error state
    val connectionColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 2f)
                    offset += pan
                }
            }
    ) {
        translate(offset.x, offset.y) {
            scale(scale, pivot = center) {
                // Draw connections with subtle styling
                drawConnections(nodePositions, connectionColor)

                // Draw nodes
                nodePositions.forEach { nodeData ->
                    scale(
                        nodeAppearance.value,
                        pivot = nodeData.position + Offset(
                            nodeData.size.width / 2,
                            nodeData.size.height / 2
                        )
                    ) {
                        drawTaskNode(
                            nodeData,
                            textMeasurer,
                            backgroundColor = surfaceColor,
                            textColor = onSurfaceColor,
                            status = taskStatuses[nodeData.taskNode.id] ?: TaskStatus.TODO,
                            todoColor = todoColor,
                            inProgressColor = inProgressColor,
                            completedColor = completedColor,
                            failedColor = failedColor,
                            pulse = pulseAnimation.value
                        )
                    }
                }
            }
        }
    }
}

private fun calculateNodePositions(
    sortedTasks: List<TaskNode>,
    graph: ExecutionGraph,
    canvasSize: IntSize
): List<NodePositionData> {
    if (canvasSize.width <= 0 || canvasSize.height <= 0 || sortedTasks.isEmpty()) {
        return emptyList()
    }

    val levels = mutableMapOf<String, Int>()
    for (task in sortedTasks) {
            val maxDepLevel = task.dependencies.map { levels[it] ?: -1 }.maxOrNull() ?: -1
            levels[task.id] = maxDepLevel + 1
        }

    // Much larger, more spacious nodes
    val nodeWidth = 300f
    val nodeHeight = 120f
    val horizontalSpacing = 50f
    val verticalSpacing = 60f

    val nodesByLevel = levels.entries.groupBy({ it.value }) { it.key }

    val result = mutableListOf<NodePositionData>()
    val totalLevels = (nodesByLevel.keys.maxOrNull() ?: 0) + 1
    val totalHeight = totalLevels * nodeHeight + (totalLevels - 1).coerceAtLeast(0) * verticalSpacing
    val initialY = (canvasSize.height - totalHeight) / 2f

    nodesByLevel.forEach { (level, taskIds) ->
        val levelY = initialY + level * (nodeHeight + verticalSpacing)
        val levelTotalWidth = taskIds.size * nodeWidth + (taskIds.size - 1).coerceAtLeast(0) * horizontalSpacing
        var currentX = (canvasSize.width - levelTotalWidth) / 2f

        taskIds.forEach { taskId ->
            val task = graph.tasks.first { it.id == taskId }
            result.add(
                NodePositionData(
                    task,
                    Offset(currentX, levelY),
                    Size(nodeWidth, nodeHeight)
                )
            )
            currentX += nodeWidth + horizontalSpacing
        }
    }

    return result
}

private fun DrawScope.drawConnections(
    nodes: List<NodePositionData>,
    connectionColor: Color
) {
    val nodeMap = nodes.associateBy { it.taskNode.id }
    val arrowWidth = 8f
    val arrowHeight = 12f

    nodes.forEach { toNodeData ->
        toNodeData.taskNode.dependencies.forEach { fromNodeId ->
            val fromNodeData = nodeMap[fromNodeId]
            if (fromNodeData != null) {
                val start = Offset(
                    fromNodeData.position.x + fromNodeData.size.width / 2,
                    fromNodeData.position.y + fromNodeData.size.height
                )
                val end = Offset(
                    toNodeData.position.x + toNodeData.size.width / 2,
                    toNodeData.position.y
                )

                val controlPoint1 = Offset(start.x, start.y + (end.y - start.y) * 0.4f)
                val controlPoint2 = Offset(end.x, end.y - (end.y - start.y) * 0.4f)

                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(
                        controlPoint1.x, controlPoint1.y,
                        controlPoint2.x, controlPoint2.y,
                        end.x, end.y
                    )
                }

                drawPath(path, color = connectionColor, style = Stroke(width = 1.5f))

                // Smaller, cleaner arrow
                val tangent = end - controlPoint2
                val angle = Math.toDegrees(kotlin.math.atan2(tangent.y.toDouble(), tangent.x.toDouble())).toFloat()

                translate(end.x, end.y) {
                    rotate(angle - 90f) {
                        val arrowPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(-arrowWidth / 2, -arrowHeight)
                            lineTo(arrowWidth / 2, -arrowHeight)
                            close()
                        }
                        drawPath(arrowPath, color = connectionColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawTaskNode(
    nodeData: NodePositionData,
    textMeasurer: TextMeasurer,
    backgroundColor: Color,
    textColor: Color,
    status: TaskStatus,
    todoColor: Color,
    inProgressColor: Color,
    completedColor: Color,
    failedColor: Color,
    pulse: Float
) {
    val (task, position, size) = nodeData
    val cornerRadius = CornerRadius(8f, 8f)

    val (accentColor, statusIcon) = when (status) {
        TaskStatus.TODO -> todoColor to "⏳"
        TaskStatus.IN_PROGRESS -> inProgressColor to "▶"
        TaskStatus.COMPLETED -> completedColor to "✓"
        TaskStatus.FAILED -> failedColor to "✗"
    }
    
    val borderWidth = 2f
    val padding = 6f

    // Modern shadow effect
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.05f),
        topLeft = position + Offset(2f, 2f),
        size = size,
        cornerRadius = cornerRadius
    )

    // Background with subtle gradient
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            backgroundColor,
            backgroundColor.copy(alpha = 0.95f)
        )
    )
    
    drawRoundRect(
        brush = bgBrush,
        topLeft = position,
        size = size,
        cornerRadius = cornerRadius
    )

    // Enhanced pulsing effect for IN_PROGRESS
    if (status == TaskStatus.IN_PROGRESS) {
        drawRoundRect(
            color = accentColor.copy(alpha = 0.2f),
            topLeft = position - Offset(pulse * 0.5f, pulse * 0.5f),
            size = Size(size.width + pulse, size.height + pulse),
            cornerRadius = CornerRadius(cornerRadius.x + pulse * 0.5f, cornerRadius.y + pulse * 0.5f),
            style = Stroke(width = borderWidth + pulse * 0.3f)
        )
    }

    // Main border
    drawRoundRect(
        color = accentColor,
        topLeft = position,
        size = size,
        cornerRadius = cornerRadius,
        style = Stroke(width = borderWidth)
    )

    // Status indicator - small colored circle in top-right
    val indicatorRadius = 4f
    val indicatorOffset = Offset(
        position.x + size.width - indicatorRadius - 4f,
        position.y + indicatorRadius + 4f
    )
    
    drawCircle(
        color = accentColor,
        radius = indicatorRadius,
        center = indicatorOffset
    )

    val textWidth = size.width - 2 * padding

    // Task name - smaller and more compact
    val taskNameLayout = textMeasurer.measure(
        text = AnnotatedString(task.name),
        style = TextStyle(
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints.fixedWidth((textWidth - 16f).toInt()) // Leave space for status indicator
    )

    drawText(
        textLayoutResult = taskNameLayout,
        topLeft = position + Offset(padding, padding)
    )

    // Task ID - very small and subtle
    val taskIdLayout = textMeasurer.measure(
        text = AnnotatedString(task.id),
        style = TextStyle(
            color = textColor.copy(alpha = 0.6f),
            fontSize = 7.sp,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints.fixedWidth(textWidth.toInt())
    )

    drawText(
        textLayoutResult = taskIdLayout,
        topLeft = position + Offset(padding, padding + taskNameLayout.size.height + 2f)
    )

    // Instruction - compact
    val instructionTop = padding + taskNameLayout.size.height + taskIdLayout.size.height + 4f
    val instructionTextHeight = size.height - instructionTop - padding

    if (instructionTextHeight > 10f) { // Only show if there's enough space
        val instructionLayout = textMeasurer.measure(
            text = AnnotatedString(task.instruction),
            style = TextStyle(
                color = textColor.copy(alpha = 0.8f),
                fontSize = 8.sp,
            ),
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(
                maxWidth = textWidth.toInt(),
                maxHeight = instructionTextHeight.toInt()
            )
        )

        drawText(
            textLayoutResult = instructionLayout,
            topLeft = position + Offset(padding, instructionTop)
        )
    }
} 