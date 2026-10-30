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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    // 主页导航要“透明液态玻璃”：比通用 Bar 表面更透，让壁纸/内容透上来。
    val containerColor = colorResource(
        if (dark) R.color.aries_nav_surface_dark else R.color.aries_nav_surface_light
    )
    val restTint = colorResource(
        if (dark) R.color.aries_liquid_selected_dark else R.color.aries_liquid_selected_light
    )
    val selectedLensHeight = dimensionResource(R.dimen.aries_liquid_selected_lens_height)
    val selectedLensAmount = dimensionResource(R.dimen.aries_liquid_selected_lens_amount)
    val selectedInnerShadowRadius = dimensionResource(R.dimen.aries_liquid_selected_inner_shadow_radius)
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
        val insetPx = with(density) { inset.toPx() }
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
        // 松手后与 pager tween 同步追赶的短暂窗口：期间仍由 drag.value 驱动滑块。
        var catchUp by remember { mutableStateOf(false) }
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
                    if (target != currentSettledIndex) currentOnSelect(target)
                    // 松手后让滑块从手指位置平滑追到目标页中心：统一用参考实现的
                    // 弹簧 spring(1f,1000f)（DampedDragAnimation.valueAnimationSpec），
                    // 与拖拽跟手同源。catchUp 的解除不在这里立刻发生：弹簧通常比
                    // pager 的 360ms tween 先到，若此刻交还 pager 进度，滑块会被
                    // 半程进度拉回去再追一次，表现为松手后“回弹一下”。
                    // 解除时机由下方 snapshotFlow 统一裁决。
                    scope.launch {
                        catchUp = true
                        animateToValueAwait(target.toFloat())
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

        // 低频率同步 drag.value：pager 停稳后把本地拖拽值对齐到真实进度，
        // 避免下一次按住胶囊时从上一次停留的旧位置起跳。
        // 逐帧位置不经过这里，滑块在 graphicsLayer 内直接读取 pager 进度。
        // catchUp 的解除也在这里统一裁决：本地弹簧已结束（isAnimating=false）
        // 且 pager 已落定在同一目标页时，才把位置交还 pager 进度——
        // 三个条件缺一不可，消除松手吸附后“先到目标再被拉回”的回弹。
        LaunchedEffect(drag) {
            snapshotFlow { Triple(currentPagerScrolling(), drag.isAnimating, drag.isPressed) }
                .collectLatest { (scrolling, animating, pressed) ->
                    if (pressed || scrolling) return@collectLatest
                    if (catchUp) {
                        if (!animating && currentSettledIndex == drag.targetValue.fastRoundToInt()) {
                            catchUp = false
                            drag.snapToValue(
                                currentProgress().fastCoerceIn(0f, (tabCount - 1).toFloat())
                            )
                        }
                    } else {
                        drag.snapToValue(
                            currentProgress().fastCoerceIn(0f, (tabCount - 1).toFloat())
                        )
                    }
                }
        }

        val press = rememberAriesPressState(
            position = { size, _ ->
                Offset(
                    x = insetPx + (drag.value + 0.5f) * tabWidthPx + panelOffset,
                    y = size.height / 2f,
                )
            }
        )
        // 手指按住或松手追赶时由本地值驱动；其余时间直接贴 pager 分数，
        // 不做 snapshotFlow/mutex 中转，保证左右滑动逐帧跟手。
        val sliderPosition: () -> Float = {
            if (drag.isPressed || catchUp) drag.value
            else currentProgress().fastCoerceIn(0f, (tabCount - 1).toFloat())
        }

        // 1) 胶囊容器 + 可见内容（对齐参考 LiquidBottomTabs:161-188 的单层结构）：
        //    内容直接画在玻璃层里，表面色 40% alpha 盖在上面，内容仍透出可读。
        CompositionLocalProvider(LocalAriesTabScale provides {
            lerp(1f, 1.2f, drag.pressProgress)
        }) {
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
                        onClick = {
                            // 对齐参考 LiquidBottomTabs:139-146 的点击动效：点按页签时
                            // 滑块以参考弹簧 spring(1f,1000f) 直达目标页，领先于 pager
                            // 的 360ms tween；catchUp 仍由上方 snapshotFlow 统一裁决交还，
                            // 与拖拽松手共用同一套防回弹交接。
                            if (index != currentSettledIndex) {
                                catchUp = true
                                scope.launch { drag.animateToValueAwait(index.toFloat()) }
                            }
                            currentOnSelect(index)
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        content(index)
                    }
                }
            }
        }
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

        // 3) 选中滑块（玻璃折射层，位于可见内容之上，对齐参考 LiquidBottomTabs:231-286 的层级）：
        //    静止时 highlight/shadow/lens 全为 0，只剩 10% tint；按压/拖动时 lens 全量
        //    折射副本层，不透明采样盖住下方真实文字，避免双层文字叠加。
        Box(
            Modifier
                .padding(horizontal = inset)
                .graphicsLayer { translationX = sliderPosition() * tabWidthPx + panelOffset }
                // 手势直接挂在滑块层上，与参考 LiquidBottomTabs:239-240 一致。
                .then(press.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { shape },
                    effects = {
                        // 对齐参考 LiquidBottomTabs:244-250：lens 随按压进度从 0 增到
                        // 10/14dp 全量并常开色散；静止时无折射，仅剩 10% tint。
                        val p = drag.pressProgress
                        lens(
                            selectedLensHeight.toPx() * p,
                            selectedLensAmount.toPx() * p,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = drag.pressProgress)
                    },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = selectedInnerShadowRadius * drag.pressProgress,
                            alpha = drag.pressProgress,
                        )
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
                        // 对齐参考 LiquidBottomTabs:274-281：静止 tint 10%，按压时淡出。
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