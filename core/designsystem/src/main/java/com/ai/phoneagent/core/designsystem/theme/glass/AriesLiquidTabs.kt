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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
 */
@Composable
fun AriesLiquidTabs(
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    progress: () -> Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 3,
    userDragging: () -> Boolean = { false },
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
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
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
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabCount - 1)
                    edgeHit = 0
                    currentIndex = target
                    // 无条件重新吸附：即使页码没变（本页签内小幅挪动后松手），
                    // 胶囊也必须回到页签中心，不能停留在松手位置。
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
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

        // 点击切页：选中层以弹簧飞行到目标位（带按压形变与速度挤压），
        // 与参考实现 LiquidBottomTabs 一致；onSelect 立即触发，不阻塞页面跳转。
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    drag.animateToValue(index.toFloat())
                    currentOnSelect(index)
                }
        }
        // 外部选中项变化（pager 落页 / 从抽屉跳转）时保持同步。
        LaunchedEffect(selectedIndex) {
            snapshotFlow { selectedIndex }.collectLatest { index ->
                if (currentIndex != index) currentIndex = index
            }
        }
        // slider 仅在用户手指拖动 pager 时逐帧跟随进度（glued-in）；
        // 点击触发的弹簧飞行与 pager tween 期间不接管，确保动画全程不被覆盖。
        val currentUserDragging by rememberUpdatedState(userDragging)
        val currentProgress by rememberUpdatedState(progress)
        LaunchedEffect(drag) {
            snapshotFlow { currentProgress() }
                .collectLatest { p ->
                    if (currentUserDragging()) {
                        // 手指拖动 pager：胶囊逐帧 glued-in。snapTo 会取消进行中的
                        // 弹簧飞行，手指永远优先。
                        drag.snapToValue(p.fastCoerceIn(0f, (tabCount - 1).toFloat()))
                        return@collectLatest
                    }
                    // 吸附自愈：pager 已静止（进度贴合整数页）且胶囊没有弹簧在飞、
                    // 也不在目标位时，强制弹簧归位。覆盖“中点前松手、pager 回弹回
                    // 原页”等页码不变、因而没有任何事件驱动胶囊归位的残留场景。
                    if (!drag.isAnimating) {
                        val target = p.fastRoundToInt().fastCoerceIn(0, tabCount - 1)
                        if (abs(drag.value - target) > 0.01f && abs(p - target) < 0.01f) {
                            drag.animateToValue(target.toFloat())
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
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(press.drawModifier)
                .height(barHeight)
                .fillMaxWidth()
                .padding(inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabCount) { index ->
                TabSlot(
                    onClick = { currentIndex = index },
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
                        onDrawSurface = { drawRect(containerColor) },
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
                .graphicsLayer {
                    translationX = drag.value * tabWidthPx + panelOffset
                }
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
                        drawRect(restTint, alpha = 1f - drag.pressProgress)
                        drawRect(Color.Black.copy(alpha = 0.03f * drag.pressProgress))
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