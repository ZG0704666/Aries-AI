package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.glass.LocalAriesGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 背景光晕样式。
 *
 * @property dark 是否为深色模式。
 * @property amoled 是否为 AMOLED 纯黑模式（此时不绘制光晕）。
 * @property defaultAccent 是否使用默认强调色（决定光晕取绿色/紫色还是主题主色）。
 */
data class AriesBackdropStyle(val dark: Boolean, val amoled: Boolean, val defaultAccent: Boolean)

/** 当前背景样式，由 [AriesMaterialTheme] 向下提供。 */
val LocalAriesBackdrop = staticCompositionLocalOf { AriesBackdropStyle(false, false, true) }

/**
 * 背景宿主：把光晕绘制进一个 GraphicsLayer，并通过 [LocalAriesGlassBackdrop] 下发给玻璃元件。
 *
 * 这一层是「真实背景」的唯一来源：玻璃元件用它做模糊、增艳与折射，
 * 因此背景色彩越丰富，液态玻璃的折射越明显。
 */
@Composable
fun AriesBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = LocalAriesBackdrop.current
    val backdrop = rememberLayerBackdrop()
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val green = colorResource(if (style.dark) R.color.aries_glow_green else R.color.aries_glow_green_light)
    val purple = colorResource(if (style.dark) R.color.aries_glow_purple else R.color.aries_glow_purple_light)
    val topColor = if (style.defaultAccent) green else primary
    val bottomColor = if (style.defaultAccent) purple else tertiary
    val topAlpha = if (style.dark) .34f else .5f
    val bottomAlpha = if (style.dark) .28f else .42f

    CompositionLocalProvider(LocalAriesGlassBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                drawRect(background)
                if (!style.amoled) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(topColor.copy(alpha = topAlpha), Color.Transparent),
                            center = Offset(size.width * .18f, size.height * .08f),
                            radius = size.width * 1.25f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(bottomColor.copy(alpha = bottomAlpha), Color.Transparent),
                            center = Offset(size.width * .82f, size.height * .96f),
                            radius = size.width * 1.2f,
                        )
                    )
                }
            }
            content()
        }
    }
}