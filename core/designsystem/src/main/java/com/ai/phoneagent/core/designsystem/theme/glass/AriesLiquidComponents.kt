package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.core.designsystem.R
import com.kyant.shapes.Capsule

/**
 * 液态玻璃胶囊按钮：与参考实现 `LiquidButton` 对齐。
 *
 * 效果：vibrancy + blur(2dp) + lens(12dp, 24dp) + 方向高光 + 投影，
 * 按压时叠加白色高光点并按 4dp/高度 放大。
 */
@Composable
fun AriesLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = remember { Capsule() },
    tier: AriesGlassTier = AriesGlassTier.Pill,
    surfaceColor: Color = ariesGlassSurfaceColor(tier),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = dimensionResource(R.dimen.aries_liquid_button_padding)
    ),
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberAriesPressState()
    Row(
        modifier
            .ariesLiquidInteractive(
                shape = shape,
                press = press,
                tier = tier,
                surfaceColor = surfaceColor,
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .heightIn(min = dimensionResource(R.dimen.aries_liquid_button_height))
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** 液态玻璃圆形按钮：尺寸由调用方决定，效果与 [AriesLiquidButton] 相同。 */
@Composable
fun AriesLiquidCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = dimensionResource(R.dimen.aries_liquid_button_height),
    touchSize: Dp = maxOf(size, dimensionResource(R.dimen.aries_touch_target)),
    enabled: Boolean = true,
    tier: AriesGlassTier = AriesGlassTier.Pill,
    surfaceColor: Color = ariesGlassSurfaceColor(tier),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = rememberAriesPressState()
    val shape = remember { Capsule() }
    val buttonSizePx = with(LocalDensity.current) { size.toPx() }
    Box(
        modifier
            .size(touchSize)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(press.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .ariesLiquidGlass(
                    shape = shape,
                    tier = tier,
                    surfaceColor = surfaceColor,
                    layerBlock = {
                        val progress = press.pressProgress
                        val scale = 1f + 4.dp.toPx() / buttonSizePx.coerceAtLeast(1f) * progress
                        scaleX = scale
                        scaleY = scale
                    },
                )
                .then(press.drawModifier),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
    }
}