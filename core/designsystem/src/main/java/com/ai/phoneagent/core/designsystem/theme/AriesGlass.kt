package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R

/** Transparent glass with edge reflections. No opaque highlight plate or shadow inside the lens. */
@Composable
fun Modifier.ariesGlass(shape: Shape, selected: Boolean = false): Modifier {
    val dark = LocalAriesBackdrop.current.dark
    val base = colorResource(if (dark) R.color.aries_glass_dark else R.color.aries_glass_light)
    val highlight = colorResource(if (dark) R.color.aries_glass_highlight_dark else R.color.aries_glass_highlight_light)
    val edge = colorResource(if (dark) R.color.aries_glass_edge_dark else R.color.aries_glass_edge_light)
    val shade = colorResource(if (dark) R.color.aries_glass_shadow_dark else R.color.aries_glass_shadow_light)
    val selection = colorResource(if (dark) R.color.aries_glass_selected_dark else R.color.aries_glass_selected_light)
    val rim = dimensionResource(R.dimen.aries_hairline)
    val spread = dimensionResource(R.dimen.aries_glass_shadow_spread)
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val lens = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val reflection = Brush.linearGradient(
            0f to highlight, .22f to edge, .48f to edge.copy(alpha = edge.alpha * .22f),
            .73f to highlight.copy(alpha = highlight.alpha * .65f), 1f to edge,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        onDrawBehind {
            // Elevation shadows fill transparent outlines on some Android renderers.
            // Draw feathered rings strictly OUTSIDE the actual lens instead.
            clipPath(lens, ClipOp.Difference) {
                for (layer in 8 downTo 1) {
                    drawPath(lens, shade.copy(alpha = shade.alpha * (9 - layer) / 36f),
                        style = Stroke(width = spread.toPx() * layer / 4f))
                }
            }
            drawPath(lens, base)
            if (selected) drawPath(lens, selection)
            // Clip the half-stroke to the lens so the border never changes layout size.
            clipPath(lens) { drawPath(lens, reflection, style = Stroke(width = rim.toPx() * 2)) }
        }
    }
}
