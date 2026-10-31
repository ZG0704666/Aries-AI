package com.ai.phoneagent.ui.inputbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R
import kotlin.math.abs

/**
 * 语音波形动画组件：稳定分布的 spring 驱动声波柱，振幅直接映射麦克风音量。
 *
 * @param amplitude 音量振幅 0.0~1.0（由录音识别层每帧更新）
 * @param solidColor 非空时整条波形使用指定颜色；默认使用浅蓝录音色。
 * @param spread 为 true 时声波柱均匀铺满整个宽度（录音胶囊内的白色全宽波形）；
 *               为 false 时保持紧凑排布（玻璃胶囊内的局部波形）。
 * @param barCount 声波柱数量；默认随 spread 取值（36/18），可覆盖（如识别态 3 根小波形）。
 */
@Composable
fun VoiceWaveform(
    amplitude: Float, // 0.0 ~ 1.0 - 音量振幅
    modifier: Modifier = Modifier,
    solidColor: Color? = null,
    spread: Boolean = false,
    barCount: Int = if (spread) 36 else 18,
) {
    val barWidth = dimensionResource(R.dimen.m3t_voice_wave_bar_width)
    val barSpacing = dimensionResource(R.dimen.m3t_voice_wave_bar_width)
    val maxHeight = dimensionResource(R.dimen.m3t_voice_wave_bar_max_height)
    val minHeight = dimensionResource(R.dimen.m3t_voice_wave_bar_min_height)
    val activeColor = colorResource(id = R.color.m3t_voice_wave_active)

    Row(
        modifier = modifier,
        horizontalArrangement = if (spread) Arrangement.Start else Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            // 中间高两边低的分布 + 稳定的 per-bar seed，避免振幅更新时波形抖动。
            val centerWeight = 1f - abs(index - barCount / 2f) / (barCount / 2f)
            val seed = remember { ((index * 37) % 11) / 10f }
            val targetHeight = minHeight + (maxHeight - minHeight) * amplitude * centerWeight * (0.8f + seed * 0.2f)

            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "bar_$index"
            )

            val barColor = solidColor ?: activeColor

            val bar = @Composable {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(animatedHeight)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(barColor)
                )
            }

            if (spread) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    bar()
                }
            } else {
                bar()
            }
        }
    }
}

@Preview
@Composable
fun PreviewVoiceWaveform() {
    MaterialTheme {
        VoiceWaveform(amplitude = 0.5f)
    }
}
