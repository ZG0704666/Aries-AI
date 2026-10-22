package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ai.phoneagent.core.designsystem.R
import com.kyant.backdrop.backdrops.LayerBackdrop
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

/** 背景层的渲染模式。 */
enum class AriesBackdropMode { Real, Static }

/** 当前背景层的渲染模式，可在局部改用 [AriesBackdropMode.Static]。 */
val LocalAriesBackdropMode = staticCompositionLocalOf { AriesBackdropMode.Real }

/** 当前可采样的背景层；为 null 时玻璃回退到静态绘制。 */
val LocalAriesLayerBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 背景层：绘制底色与径向光晕，并把这一层录制成 backdrop 供玻璃采样。
 *
 * 背景与 [content] 是兄弟节点，因此默认只采样渐变底色；需要模糊前景内容时，
 * 可在 [content] 内再包一层 [layerBackdrop]，并通过 [LocalAriesLayerBackdrop] 提供。
 */
@Composable
fun AriesBackdrop(
    modifier: Modifier = Modifier,
    mode: AriesBackdropMode = LocalAriesBackdropMode.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalAriesBackdrop.current
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val green = colorResource(R.color.aries_glow_green)
    val purple = colorResource(R.color.aries_glow_purple)
    val backdrop = rememberLayerBackdrop()
    val real = mode == AriesBackdropMode.Real
    CompositionLocalProvider(LocalAriesLayerBackdrop provides if (real) backdrop else null) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (real) Modifier.layerBackdrop(backdrop) else Modifier)
                    .drawWithCache {
                        // 亮色下背景几乎是纯白，玻璃没有可折射的层次会与背景糊成一片，
                        // 因此同样绘制极淡的光晕，只保留很轻的色彩与明度变化。
                        val topAlpha = if (style.dark) .34f else .12f
                        val bottomAlpha = if (style.dark) .28f else .10f
                        val top = Brush.radialGradient(
                            listOf((if (style.defaultAccent) green else primary).copy(alpha = topAlpha), Color.Transparent),
                            center = Offset(size.width * .18f, size.height * .08f), radius = size.width * 1.25f,
                        )
                        val bottom = Brush.radialGradient(
                            listOf((if (style.defaultAccent) purple else tertiary).copy(alpha = bottomAlpha), Color.Transparent),
                            center = Offset(size.width * .82f, size.height * .96f), radius = size.width * 1.2f,
                        )
                        onDrawBehind {
                            drawRect(background)
                            if (style.dark && !style.amoled) { drawRect(top); drawRect(bottom) }
                            if (!style.dark) { drawRect(top); drawRect(bottom) }
                        }
                    },
            )
            content()
        }
    }
}
