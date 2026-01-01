package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.ui.common.WaveVisualizer
import kotlinx.coroutines.flow.StateFlow

/**
 * 波浪可视化和头像组件
 */
@Composable
fun WaveVisualizerSection(
    isWaveActive: Boolean,
    isRecording: Boolean,
    volumeLevelFlow: StateFlow<Float>?,
    aiAvatarUri: String?,
    avatarShape: Shape = CircleShape,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 动画尺寸
    val waveSize by animateDpAsState(
        targetValue = if (isWaveActive) 300.dp else 120.dp,
        animationSpec = tween(500), label = "waveSize"
    )
    
    val waveOffsetY by animateDpAsState(
        targetValue = if (isWaveActive) 0.dp else (-100).dp,
        animationSpec = tween(500), label = "waveOffsetY"
    )
    
    val avatarSize by animateDpAsState(
        targetValue = if (isWaveActive) 120.dp else 80.dp,
        animationSpec = tween(500), label = "avatarSize"
    )
    
    Box(
        modifier = modifier
            .offset(y = waveOffsetY),
        contentAlignment = Alignment.Center
    ) {
        // 波浪可视化器
        WaveVisualizer(
            modifier = Modifier.size(waveSize),
            isActive = isWaveActive,
            volumeFlow = if (isWaveActive && isRecording) volumeLevelFlow else null,
            waveColor = Color.White.copy(alpha = 0.7f),
            activeWaveColor = MaterialTheme.colorScheme.primary,
            onToggleActive = onToggleActive
        )

        // AI 头像
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(avatarShape)
                .clickable { onToggleActive() }
        ) {
            if (aiAvatarUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(aiAvatarUri)),
                    contentDescription = "AI Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 默认图标
                Icon(
                    imageVector = Icons.Default.Assistant,
                    contentDescription = "AI Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

