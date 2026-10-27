package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastCoerceIn
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.LocalAriesBackdrop
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 当前窗口背景录制层。
 *
 * 液态玻璃的所有折射、模糊与增艳都基于这一层真实像素，而不是绘制时伪造的渐变边缘。
 * 由 [com.ai.phoneagent.core.designsystem.theme.AriesBackdrop] 在主题根部提供；
 * 页面希望内容从玻璃下方穿过时，可额外录制一层内容 backdrop 后与主题 backdrop 组合。
 */
val LocalAriesGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * 液态玻璃参数档位，与参考实现（io.github.kyant0:backdrop 2.0.1 的 catalog）一一对应：
 * - [Pill]：48dp 胶囊按钮 —— vibrancy + blur(2dp) + lens(12dp, 24dp)
 * - [Bar]：导航胶囊容器 —— vibrancy + blur(8dp) + lens(24dp, 24dp)
 * - [Card]：卡片/对话框 —— colorControls + blur(16dp) + lens(24dp, 48dp, depthEffect)
 * - [Chip]：静态小标签 —— blur(8dp) + lens(8dp, 16dp)，不参与按压
 */
enum class AriesGlassTier { Pill, Bar, Card, Chip }

/** 一档液态玻璃的渲染参数（像素换算在各绘制阶段完成）。 */
@Immutable
data class AriesGlassSpec(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val vibrancy: Boolean,
    val brightness: Float,
    val saturation: Float,
    val depthEffect: Boolean,
)

fun BackdropEffectScope.applyAriesGlassSpec(spec: AriesGlassSpec) {
    with(spec) {
        if (vibrancy) vibrancy()
        if (brightness != 0f || saturation != 1f) {
            colorControls(brightness = brightness, saturation = saturation)
        }
        blur(blur.toPx())
        lens(lensHeight.toPx(), lensAmount.toPx(), depthEffect = depthEffect)
    }
}

/** 按档位读取设计令牌，避免在调用点硬编码 dp。 */
@Composable
fun ariesGlassSpec(tier: AriesGlassTier): AriesGlassSpec {
    val dark = LocalAriesBackdrop.current.dark
    return when (tier) {
        AriesGlassTier.Pill -> AriesGlassSpec(
            blur = dimensionResource(R.dimen.aries_liquid_blur_pill),
            lensHeight = dimensionResource(R.dimen.aries_liquid_lens_pill_height),
            lensAmount = dimensionResource(R.dimen.aries_liquid_lens_pill_amount),
            vibrancy = true,
            brightness = 0f,
            saturation = 1f,
            depthEffect = false,
        )

        AriesGlassTier.Bar -> AriesGlassSpec(
            blur = dimensionResource(R.dimen.aries_liquid_blur_bar),
            lensHeight = dimensionResource(R.dimen.aries_liquid_lens_bar_height),
            lensAmount = dimensionResource(R.dimen.aries_liquid_lens_bar_amount),
            vibrancy = true,
            brightness = 0f,
            saturation = 1f,
            depthEffect = false,
        )

        AriesGlassTier.Card -> AriesGlassSpec(
            blur = dimensionResource(R.dimen.aries_liquid_blur_card),
            lensHeight = dimensionResource(R.dimen.aries_liquid_lens_card_height),
            lensAmount = dimensionResource(R.dimen.aries_liquid_lens_card_amount),
            vibrancy = false,
            brightness = if (dark) 0f else 0.2f,
            saturation = if (dark) 1.5f else 1f,
            depthEffect = true,
        )

        AriesGlassTier.Chip -> AriesGlassSpec(
            blur = dimensionResource(R.dimen.aries_liquid_blur_bar),
            lensHeight = dimensionResource(R.dimen.aries_liquid_lens_card_height),
            lensAmount = dimensionResource(R.dimen.aries_liquid_lens_pill_amount),
            vibrancy = true,
            brightness = 0f,
            saturation = 1f,
            depthEffect = false,
        )
    }
}

/** 玻璃表面底色：透过玻璃看到的染色层，保证在纯黑壁纸/无模糊设备上依然可读。 */
@Composable
fun ariesGlassSurfaceColor(tier: AriesGlassTier): Color {
    val dark = LocalAriesBackdrop.current.dark
    val res = when (tier) {
        AriesGlassTier.Pill -> if (dark) R.color.aries_liquid_surface_dark else R.color.aries_liquid_surface_light
        AriesGlassTier.Bar -> if (dark) R.color.aries_liquid_bar_dark else R.color.aries_liquid_bar_light
        AriesGlassTier.Card -> if (dark) R.color.aries_liquid_card_dark else R.color.aries_liquid_card_light
        AriesGlassTier.Chip -> if (dark) R.color.aries_liquid_chip_dark else R.color.aries_liquid_chip_light
    }
    return colorResource(res)
}

@Composable
fun ariesGlassSelectionTint(): Color {
    val dark = LocalAriesBackdrop.current.dark
    return colorResource(
        if (dark) R.color.aries_liquid_selected_dark else R.color.aries_liquid_selected_light
    )
}

/** 高光样式：胶囊/导航使用方向高光，卡片使用纯色描边（与参考实现一致）。 */
fun ariesGlassHighlight(tier: AriesGlassTier): Highlight =
    if (tier == AriesGlassTier.Card) Highlight.Plain else Highlight.Default

/**
 * 液态玻璃表面。
 *
 * - API 31+：真实背景采样 + RenderEffect 模糊/增艳 + 边缘高光；
 * - API 33+：额外启用 AGSL 折射（lens）；
 * - API 30：自动降级为原有的静态玻璃描边，避免无 RenderEffect 时出现全透明。
 */
@Composable
fun Modifier.ariesLiquidGlass(
    shape: Shape,
    tier: AriesGlassTier = AriesGlassTier.Pill,
    backdrop: Backdrop = LocalAriesGlassBackdrop.current,
    surfaceColor: Color = ariesGlassSurfaceColor(tier),
    selected: Boolean = false,
    highlight: Highlight? = ariesGlassHighlight(tier),
    shadow: Shadow? = null,
    innerShadow: InnerShadow? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier {
    if (!isRenderEffectSupported()) return ariesGlass(shape, selected)

    val spec = ariesGlassSpec(tier)
    val shapeLambda = remember(shape) { { shape } }
    // 卡片档位补一圈边界描边（浅色可见、深色透明跳过），与静态玻璃卡片视觉一致。
    val borderColor = if (tier == AriesGlassTier.Card) {
        colorResource(
            if (LocalAriesBackdrop.current.dark) {
                R.color.aries_glass_border_dark
            } else {
                R.color.aries_glass_border_light
            }
        )
    } else {
        Color.Transparent
    }
    val rim = dimensionResource(R.dimen.aries_hairline)
    
    // 启用 RenderNode 缓存以优化性能
    return graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.drawBackdrop(
        backdrop = backdrop,
        shape = shapeLambda,
        effects = { applyAriesGlassSpec(spec) },
        highlight = highlight?.let { { it } },
        shadow = shadow?.let { { it } },
        innerShadow = innerShadow?.let { { it } },
        layerBlock = layerBlock,
        onDrawSurface = {
            drawRect(surfaceColor)
            if (borderColor.alpha > 0f) {
                // 与 ariesGlass 一致：Path + 细描边画边界（drawOutline 在低版本 compose-ui 不可用）
                val path = Path()
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rectangle -> path.addRect(outline.rect)
                    is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                    is Outline.Generic -> path.addPath(outline.path)
                }
                drawPath(path, borderColor, style = Stroke(width = rim.toPx() * 2))
            }
            onDrawSurface?.invoke(this)
        },
    )
}

/**
 * 按压交互状态：白色渐显 + 跟随手指的径向高光点 + 轻微缩放/挤压。
 * 与参考实现 `InteractiveHighlight` 一致（spring(0.5, 300)）。
 */
class AriesPressState internal constructor(
    private val scope: CoroutineScope,
    private val positionProvider: (size: Size, offset: Offset) -> Offset,
) {

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader: RuntimeShader? =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    /** 绘制层：先叠加高光，再绘制内容，保证图标文字始终在最上层。 */
    val drawModifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            val shader = shader
            if (shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val position = positionProvider(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(alpha = 0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        position.x.fastCoerceIn(0f, size.width),
                        position.y.fastCoerceIn(0f, size.height),
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                // API 31/32 没有 AGSL：退化为整体白色渐显。
                drawRect(Color.White.copy(alpha = 0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    /** 手势层：不消费事件，因此不会影响原有的 clickable / draggable。 */
    val gestureModifier: Modifier = Modifier.pointerInput(scope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                scope.launch {
                    launch { pressProgressAnimation.animateTo(1f, PressSpring) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            scope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        scope.launch {
            launch { pressProgressAnimation.animateTo(0f, PressSpring) }
            launch { positionAnimation.animateTo(startPosition, PositionSpring) }
        }
    }

    private companion object {
        val PressSpring = spring<Float>(0.5f, 300f, 0.001f)
        val PositionSpring = spring(0.5f, 300f, Offset.VisibilityThreshold)
    }
}

/** 记录按压状态；[position] 用于让高光点跟随拖动/选中位置。 */
@Composable
fun rememberAriesPressState(
    position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
): AriesPressState {
    val scope = rememberCoroutineScope()
    val currentPosition = rememberUpdatedState(position)
    return remember(scope) {
        AriesPressState(scope) { size, offset -> currentPosition.value(size, offset) }
    }
}

/** 组合「玻璃表面 + 按压高光 + 按压缩放」的标准修饰符链。 */
@Composable
fun Modifier.ariesLiquidInteractive(
    shape: Shape,
    press: AriesPressState,
    tier: AriesGlassTier = AriesGlassTier.Pill,
    backdrop: Backdrop = LocalAriesGlassBackdrop.current,
    surfaceColor: Color = ariesGlassSurfaceColor(tier),
    selected: Boolean = false,
    shadow: Shadow? = null,
): Modifier {
    val grow = dimensionResource(R.dimen.aries_liquid_press_grow)
    return this
        .ariesLiquidGlass(
            shape = shape,
            tier = tier,
            backdrop = backdrop,
            surfaceColor = surfaceColor,
            selected = selected,
            shadow = shadow,
            layerBlock = {
                val progress = press.pressProgress
                val scale = 1f + grow.toPx() / size.height.coerceAtLeast(1f) * progress
                val maxOffset = size.minDimension
                val offset = press.offset
                translationX = maxOffset * kotlin.math.tanh(0.05f * offset.x / maxOffset)
                translationY = maxOffset * kotlin.math.tanh(0.05f * offset.y / maxOffset)
                scaleX = scale
                scaleY = scale
            },
        )
        .then(press.drawModifier)
        .then(press.gestureModifier)
}
