package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.LocalAriesBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * 顶部滑动胶囊导航（液态玻璃版）。
 *
 * 结构与参考实现 `LiquidBottomTabs` 完全一致：
 * 1. 胶囊容器：vibrancy + blur(8dp) + lens(24dp, 24dp) + 表面色；
 * 2. 透明副本层：把同样的图标/文字以强调色渲染进 `tabsBackdrop`，供滑块折射；
 * 3. 滑块：跟随 pager 位置，按压缩放 + 速度挤压 + 色散折射 + 内阴影；
 * 4. 按压高光点跟随手指，松手回弹。
 *
 * @param progress 当前页位置（支持小数，来自 pager 的 `currentPage + currentPageOffsetFraction`）。
 * @param pagerScrolling pager 是否正在滚动（含用户拖动与程序 tween）。
 */
@Composable
fun AriesLiquidTabs(
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    progress: () -> Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 3,
    pagerScrolling: () -> Boolean = { false },
    onEdgeHaptic: () -> Unit = {},
    content: @Composable (index: Int) -> Unit,
) {
    val dark = LocalAriesBackdrop.current.dark
    val accent = MaterialTheme.colorScheme.primary
    val containerColor = ariesGlassSurfaceColor(AriesGlassTier.Bar)
    val restTint = colorResource(
        if (dark) R.color.aries_liquid_selected_dark else R.color.aries_liquid_selected_light
    )
    val inset = dimensionResource(R.dimen.aries_nav_inset_x)
    val barHeight = dimensionResource(R.dimen.aries_nav_height)
    val barBlur = dimensionResource(R.dimen.aries_liquid_blur_bar)
    val barLensHeight = dimensionResource(R.dimen.aries_liquid_lens_bar_height)
    val barLensAmount = dimensionResource(R.dimen.aries_liquid_lens_bar_amount)
    val pressGrow = dimensionResource(R.dimen.aries_liquid_bar_press_grow)
    val shape = remember { Capsule() }

    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { (constraints.maxWidth.toFloat() - inset.toPx() * 2f) / tabCount }
        val scope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val currentOnSelect by rememberUpdatedState(onSelect)
        val currentSettledIndex by rememberUpdatedState(selectedIndex)
        val currentPagerScrolling by rememberUpdatedState(pagerScrolling)
        val currentProgress by rememberUpdatedState(progress)
        // 松手后的三态：0 静止驱动 / 1 已请求切页等 pager 接管 / 2 pager 滚动中贴进度。
        var releasePhase by remember { mutableIntStateOf(0) }
        var edgeHit by remember { mutableIntStateOf(0) }
        val drag = remember(scope) {
            AriesTabDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStopped = {
                    val target = value.fastRoundToInt().fastCoerceIn(0, tabCount - 1)
                    edgeHit = 0
                    // 整条页签带拖动时的 4dp 惯性偏移，松手必须回弹归零。
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    if (target == currentSettledIndex) {
                        // 未跨页签（或原页签内小幅挪动后松手）：直接吸附回页签中心。
                        snapToValue(target.toFloat())
                    } else {
                        // 跨页签：胶囊原地待命，pager tween 起飞后以进度接管（pager 为唯一驱动）。
                        releasePhase = 1
                        currentOnSelect(target)
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx).fastCoerceIn(0f, (tabCount - 1).toFloat())
                    )
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    val edge = when {
                        targetValue <= 0f -> -1
                        targetValue >= tabCount - 1 -> 1
                        else -> 0
                    }
                    if (edge != 0 && edge != edgeHit) onEdgeHaptic()
                    edgeHit = edge
                },
            )
        }

        // 唯一驱动源：pager 进度。手指按住胶囊时 pager 进程值不接管；
        // pager 滚动中逐帧贴进度；静止且胶囊离位时弹簧归位（吸附自愈）。
        LaunchedEffect(drag) {
            snapshotFlow { currentProgress() }
                .collectLatest { p ->
                    val clamped = p.fastCoerceIn(0f, (tabCount - 1).toFloat())
                    if (drag.isPressed) return@collectLatest
                    if (releasePhase == 2 || currentPagerScrolling()) {
                        // 从松手位置追上轨道（弹簧追赶），对齐后逐帧 glued-in。
                        if (releasePhase == 2 && abs(drag.value - clamped) > 0.12f) {
                            drag.updateValue(clamped)
                        } else {
                            drag.snapToValue(clamped)
                        }
                        return@collectLatest
                    }
                    if (releasePhase == 0 && !drag.isAnimating) {
                        val target = clamped.fastRoundToInt().fastCoerceIn(0, tabCount - 1)
                        if (abs(clamped - target) < 0.01f && abs(drag.value - target) > 0.01f) {
                            drag.animateToValue(target.toFloat())
                        }
                    }
                }
        }
        // 松手状态机：等 pager 接管或超时自愈；滚动结束回归静态驱动。
        LaunchedEffect(drag) {
            snapshotFlow { releasePhase }
                .collectLatest { phase ->
                    when (phase) {
                        1 -> {
                            val started = withTimeoutOrNull(180L) {
                                snapshotFlow { currentPagerScrolling() }.first { it }
                            }
                            if (started == null && !drag.isPressed) {
                                drag.snapToValue(currentSettledIndex.toFloat())
                            }
                            releasePhase = 0
                        }
                        2 -> {
                            snapshotFlow { currentPagerScrolling() }.first { !it }
                            // 残余亚像素偏差交给静态自愈分支吸附回中心。
                            if (!drag.isPressed) releasePhase = 0
                        }
                    }
                }
        }

        val press = rememberAriesPressState(
            position = { size, _ ->
                Offset(x = (drag.value + 0.5f) * tabWidthPx, y = size.height / 2f)
            }
        )

        // 1) 胶囊容器（含可见内容）
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(barBlur.toPx())
                        lens(barLensHeight.toPx(), barLensAmount.toPx())
                    },
                    layerBlock = {
                        val scale = lerp(1f, 1f + pressGrow.toPx() / size.width, drag.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        // onDrawSurface 不带 shape 裁剪，必须自己 clipPath 再画表面色。
                        clipPath(ariesShapePath(shape)) { drawRect(containerColor) }
                    },
                )
                // 容器的按压高光按胶囊裁剪，避免直角矩形亮块。
                .clip(shape)
                .then(press.highlightModifier(shape))
                .height(barHeight)
                .fillMaxWidth()
                .padding(inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabCount) { index ->
                TabSlot(
                    onClick = { currentOnSelect(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    content(index)
                }
            }
        }

        // 2) 透明副本层：供滑块折射出强调色的图标与文字
        CompositionLocalProvider(LocalAriesTabScale provides {
            lerp(1f, 1.2f, drag.pressProgress)
        }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            val p = drag.pressProgress
                            vibrancy()
                            blur(barBlur.toPx())
                            lens(barLensHeight.toPx() * p, barLensAmount.toPx() * p)
                        },
                        highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                        onDrawSurface = {
                            // onDrawSurface 不带 shape 裁剪，必须自己 clipPath 再画表面色。
                            clipPath(ariesShapePath(shape)) { drawRect(containerColor) }
                        },
                    )
                    .height(barHeight - inset * 2)
                    .fillMaxWidth()
                    .padding(horizontal = inset)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(tabCount) { index ->
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(index) }
                }
            }
        }

        // 3) 选中滑块（绘制在内容之上，透过滑块仍能看到图标）
        Box(
            Modifier
                .padding(horizontal = inset)
                .graphicsLayer { translationX = drag.value * tabWidthPx + panelOffset }
                .then(press.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { shape },
                    effects = {
                        // 玻璃高光/折射随速度渐显：飞行中有折射带与色散，落位后回到静息。
                        val flight = min(abs(drag.velocity) * 0.35f, 1f)
                        val p = max(drag.pressProgress, flight)
                        lens(
                            barLensHeight.toPx() * 0.42f * p,
                            barLensAmount.toPx() * 0.58f * p,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val flight = min(abs(drag.velocity) * 0.35f, 1f)
                        Highlight.Default.copy(alpha = max(drag.pressProgress, flight))
                    },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = {
                        InnerShadow(radius = 8.dp * drag.pressProgress, alpha = drag.pressProgress)
                    },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val velocity = drag.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        // 滑块表面色同样要按胶囊裁剪（按压缩放 1.39x 时会泄出矩形）。
                        clipPath(ariesShapePath(Capsule())) {
                            drawRect(restTint, alpha = 1f - drag.pressProgress)
                            drawRect(Color.Black.copy(alpha = 0.03f * drag.pressProgress))
                        }
                    },
                )
                .height(barHeight - inset * 2)
                .fillMaxWidth(1f / tabCount),
        )
    }
}

/** 可见 tab 槽位：点击 + 按压放大。 */
@Composable
private fun TabSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale = LocalAriesTabScale.current
    Box(
        modifier
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 文字/图标的按压放大比例，由 [AriesLiquidTabs] 提供。 */
val LocalAriesTabScale = staticCompositionLocalOf { { 1f } }