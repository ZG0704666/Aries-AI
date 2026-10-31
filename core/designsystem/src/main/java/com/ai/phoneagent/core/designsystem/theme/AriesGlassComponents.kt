package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.glass.AriesGlassTier
import com.ai.phoneagent.core.designsystem.theme.glass.AriesLiquidButton
import com.ai.phoneagent.core.designsystem.theme.glass.ariesLiquidInteractive
import com.ai.phoneagent.core.designsystem.theme.glass.rememberAriesPressState
import com.kyant.shapes.RoundedRectangle

/** 玻璃卡片容器：统一圆角、内边距与纵向间距。
 *
 * Material B 标准内容材质：静态玻璃（零实时模糊/背景采样），用于滚动内容面板；
 * 液态 Material A 仅保留给导航胶囊、输入框、悬浮操作按钮等重点交互控件。
 */
@Composable
fun AriesGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val radius = dimensionResource(R.dimen.aries_card_radius)
    val shape = remember(radius) { RoundedRectangle(radius) }
    Surface(modifier = modifier.ariesGlass(shape), shape = shape,
        color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(dimensionResource(R.dimen.aries_setting_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm)), content = content)
    }
}

/**
 * 柔和渐变细分隔线：两端透明、中间淡出的 hairline。
 * 替代生硬的通栏分割线（HorizontalDivider），在不打断视觉连续性的前提下区分层级。
 */
@Composable
fun AriesSoftHairline(modifier: Modifier = Modifier) {
    val hairline = dimensionResource(R.dimen.aries_hairline)
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .height(hairline)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to color.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                )
            ),
    )
}

/** 玻璃按钮：液态玻璃胶囊 + 按压高光（与参考实现 LiquidButton 一致）。 */
@Composable
fun AriesGlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable RowScope.() -> Unit) {
    val radius = dimensionResource(R.dimen.aries_action_radius)
    val shape = remember(radius) { RoundedRectangle(radius) }
    AriesLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentColor = contentColor,
        content = content,
    )
}

/**
 * 圆形液态玻璃图标按钮：Material A 交互玻璃 + 按压高光/缩放，
 * 用于顶栏返回按钮、菜单按钮等图标操作。亮暗色下各自 token 自适应，
 * API 30 自动降级为静态玻璃描边。触控热区与玻璃视觉直径一致。
 */
@Composable
fun AriesGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = rememberAriesPressState()
    Box(
        modifier = modifier
            .size(dimensionResource(R.dimen.aries_touch_target))
            .ariesLiquidInteractive(
                shape = CircleShape,
                press = press,
                tier = AriesGlassTier.Bar,
            )
            // 双保险：按压高光必须裁进圆形，避免直角矩形遮罩。
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}