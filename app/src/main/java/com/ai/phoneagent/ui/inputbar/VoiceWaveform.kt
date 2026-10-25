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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R
import kotlin.math.abs
import kotlin.random.Random

/**
 * 语音波形动画组件：24 根 spring 驱动的平滑声波柱。
 *
 * @param amplitude 音量振幅 0.0~1.0（由录音识别层每帧更新）
 * @param solidColor 非空时整条波形使用该颜色（如取消态转错误色），默认使用主题渐变
 */
@Composable
fun VoiceWaveform(
    amplitude: Float, // 0.0 ~ 1.0 - 音量振幅
    modifier: Modifier = Modifier,
    solidColor: Color? = null,
) {
    val barCount = 24
    val barWidth = dimensionResource(R.dimen.m3t_voice_wave_bar_width)
    val barSpacing = dimensionResource(R.dimen.m3t_voice_wave_bar_width)
    val maxHeight = dimensionResource(R.dimen.m3t_voice_wave_bar_max_height)
    val minHeight = dimensionResource(R.dimen.m3t_voice_wave_bar_min_height)
    val waveStart = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_voice_wave_start)
    val waveMid = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_voice_wave_mid)
    val waveEnd = colorResource(id = com.ai.phoneagent.core.designsystem.R.color.m3t_voice_wave_end)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            // 中间高两边低的分布 + 少量随机抖动（随振幅每次重组取新值，弹簧平滑过渡）
            val centerWeight = 1f - abs(index - barCount / 2f) / (barCount / 2f)
            val randomFactor = remember(amplitude, index) { Random.nextFloat() * 0.4f + 0.6f }

            val targetHeight = remember(amplitude, index) {
                minHeight + (maxHeight - minHeight) * amplitude * centerWeight * randomFactor
            }

            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "bar_$index"
            )

            // 渐变颜色 - 蓝→紫→粉；solidColor 优先（取消态）
            val colorPosition = index.toFloat() / barCount
            val barColor = solidColor ?: lerp(
                lerp(waveStart, waveMid, (colorPosition * 2f).coerceAtMost(1f)),
                waveEnd,
                (colorPosition - 0.5f).coerceAtLeast(0f) * 2f
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
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
