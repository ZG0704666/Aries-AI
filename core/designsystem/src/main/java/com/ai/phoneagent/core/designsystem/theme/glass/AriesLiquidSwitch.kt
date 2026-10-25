package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.LocalAriesBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 液态玻璃开关（LiquidToggle）。
 *
 * 与参考实现 `LiquidToggle`（Kyant0/AndroidLiquidGlass catalog）一致：
 * 1. 轨道：64×28 纯色胶囊底，checked 时按动画进度向强调色过渡（轨道不挂玻璃特效）；
 * 2. 滑钮：40×24 玻璃胶囊，rest 状态 blur(8dp)，按压时 lens(5dp,10dp)+色散 渐显；
 * 3. 按压时把轨道以 scale(2/3→0.75, 0→0.75) 形变后作为折射源 —— 玻璃透出
 *    “被按进轨道”的凹陷感；滑钮按压缩放 1.5 倍并随速度挤压；
 * 4. 支持横向拖动（rubber band 收束到 0/1）与点击翻转，spring(1, 1000) 飞行落位。
 *
 * @param checked 开关当前状态；状态翻转后由调用方立即更新为相反值。
 * @param enabled 关闭时不安装手势，整体降为不可交互的静态外观。
 */
@Composable
fun AriesLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalAriesGlassBackdrop.current,
) {
    if (!isRenderEffectSupported()) {
        // API 30 降级：无 RenderEffect（真实折射不可用），静态胶囊 + 弹簧滑钮。
        AriesLiquidSwitchStatic(checked, onCheckedChange, enabled, modifier)
        return
    }

    val dark = LocalAriesBackdrop.current.dark
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = colorResource(
        if (dark) R.color.aries_liquid_switch_track_dark else R.color.aries_liquid_switch_track_light
    )
    val thumbColor = colorResource(R.color.aries_liquid_switch_thumb)
    val shadowRadius = dimensionResource(R.dimen.aries_liquid_switch_shadow)
    val disabledTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) {
        dimensionResource(R.dimen.aries_liquid_switch_drag_width).toPx()
    }
    val paddingPx = with(density) {
        dimensionResource(R.dimen.aries_liquid_switch_padding).toPx()
    }
    val blurPx = with(density) { dimensionResource(R.dimen.aries_liquid_blur_bar).toPx() }
    val lensHeightPx = with(density) { dimensionResource(R.dimen.aries_liquid_lens_switch_height).toPx() }
    val lensAmountPx = with(density) { dimensionResource(R.dimen.aries_liquid_lens_switch_amount).toPx() }

    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    // 最近一次手势决策目标：快速连点折叠为同一目标，避免重复翻转。
    var pendingTarget by remember { mutableStateOf<Boolean?>(null) }

    val drag = remember(animationScope) {
        AriesTabDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStopped = {
                // 统一决策：拖动按当前落位收束到 0/1，点按翻转；快速连点折叠为同一目标。
                val isDrag = didDrag
                didDrag = false
                val decisionTarget = if (isDrag) {
                    targetValue >= 0.5f
                } else if (pendingTarget != null) {
                    !pendingTarget!!
                } else {
                    !currentChecked
                }
                pendingTarget = decisionTarget
                fraction = if (decisionTarget) 1f else 0f
                currentOnCheckedChange(decisionTarget)
                animationScope.launch {
                    // 状态若被上层拒绝（guard），checked 不会到达决策目标 → 等待窗口过后回滚视觉。
                    withTimeoutOrNull(300) {
                        snapshotFlow { currentChecked }.first { it == decisionTarget }
                    }
                    if (pendingTarget == decisionTarget) pendingTarget = null
                    if (currentChecked != decisionTarget) {
                        // guard 拒绝：滑钮回滚到真实状态；fraction 变更由 collector 走弹簧飞行。
                        fraction = if (currentChecked) 1f else 0f
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) {
                    (fraction + delta).fastCoerceIn(0f, 1f)
                } else {
                    (fraction - delta).fastCoerceIn(0f, 1f)
                }
            },
        )
    }

    // 用户拖动/点击写入 fraction → 驱动滑钮弹簧跟手。
    LaunchedEffect(drag) {
        snapshotFlow { fraction }.collectLatest { drag.updateValue(it) }
    }
    // 外部状态变化（行点击翻转 / 预设恢复）→ 弹簧飞行到目标位。
    LaunchedEffect(drag) {
        snapshotFlow { currentChecked }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                drag.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier
            .semantics {
                role = Role.Switch
                if (enabled) {
                    toggleableState = ToggleableState(checked)
                    onClick {
                        currentOnCheckedChange(!checked)
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道：纯色胶囊（动画期间按进度从灰过渡到强调色），不挂玻璃特效。
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(
                        if (enabled) lerp(trackColor, accentColor, drag.value)
                        else disabledTrackColor
                    )
                }
                .size(
                    dimensionResource(R.dimen.aries_liquid_switch_track_width),
                    dimensionResource(R.dimen.aries_liquid_switch_track_height),
                ),
        )

        // 滑钮：玻璃胶囊，折射源 = 窗口背景 + 被按压缩放的轨道。
        Box(
            Modifier
                .graphicsLayer {
                    translationX = if (isLtr) {
                        lerp(paddingPx, paddingPx + dragWidth, drag.value)
                    } else {
                        lerp(-paddingPx, -(paddingPx + dragWidth), drag.value)
                    }
                }
                .then(if (enabled) drag.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = drag.pressProgress
                            val squishX = lerp(2f / 3f, 0.75f, progress)
                            val squishY = lerp(0f, 0.75f, progress)
                            scale(squishX, squishY) { drawBackdrop() }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = drag.pressProgress
                        blur(blurPx * (1f - progress))
                        lens(
                            lensHeightPx * progress,
                            lensAmountPx * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val progress = drag.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = {
                        Shadow(radius = shadowRadius, color = Color.Black.copy(alpha = 0.05f))
                    },
                    innerShadow = {
                        val progress = drag.pressProgress
                        InnerShadow(radius = shadowRadius * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val velocity = drag.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(thumbColor, alpha = 1f - drag.pressProgress)
                    },
                )
                .size(
                    dimensionResource(R.dimen.aries_liquid_switch_thumb_width),
                    dimensionResource(R.dimen.aries_liquid_switch_thumb_height),
                )
                .then(if (enabled) Modifier else Modifier.alpha(0.38f)),
        )
    }
}

/** 无 RenderEffect（API 30）降级：纯色轨道 + 静态描边滑钮，整块可点切换。 */
@Composable
private fun AriesLiquidSwitchStatic(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = LocalAriesBackdrop.current.dark
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = colorResource(
        if (dark) R.color.aries_liquid_switch_track_dark else R.color.aries_liquid_switch_track_light
    )
    val thumbColor = colorResource(R.color.aries_liquid_switch_thumb)
    val edgeColor = colorResource(if (dark) R.color.aries_glass_edge_dark else R.color.aries_glass_edge_light)
    val hairline = dimensionResource(R.dimen.aries_hairline)
    val disabledTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val density = LocalDensity.current
    val paddingPx = with(density) { dimensionResource(R.dimen.aries_liquid_switch_padding).toPx() }
    val dragWidthPx = with(density) { dimensionResource(R.dimen.aries_liquid_switch_drag_width).toPx() }
    val thumbOffset by animateFloatAsState(
        targetValue = paddingPx + if (checked) dragWidthPx else 0f,
        label = "ariesSwitchStaticThumb",
    )
    // M3 惯例：禁用时整体降为 38% 透明度。
    val groupAlpha = if (enabled) 1f else 0.38f

    Box(
        modifier
            .alpha(groupAlpha)
            .let { base ->
                if (enabled) base.clickable { onCheckedChange(!checked) } else base
            }
            .semantics {
                role = Role.Switch
                if (enabled) {
                    toggleableState = ToggleableState(checked)
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道
        Box(
            Modifier
                .clip(Capsule())
                .drawBehind {
                    drawRect(
                        if (!enabled) disabledTrackColor
                        else if (checked) accentColor else trackColor
                    )
                }
                .size(
                    dimensionResource(R.dimen.aries_liquid_switch_track_width),
                    dimensionResource(R.dimen.aries_liquid_switch_track_height),
                ),
        )
        // 滑钮：静态表面 + 极细描边（与 Material B 静态描边策略一致）
        Box(
            Modifier
                .graphicsLayer { translationX = thumbOffset }
                .clip(Capsule())
                .drawBehind { drawRect(thumbColor) }
                .border(width = hairline, color = edgeColor, shape = Capsule())
                .size(
                    dimensionResource(R.dimen.aries_liquid_switch_thumb_width),
                    dimensionResource(R.dimen.aries_liquid_switch_thumb_height),
                ),
        )
    }
}
