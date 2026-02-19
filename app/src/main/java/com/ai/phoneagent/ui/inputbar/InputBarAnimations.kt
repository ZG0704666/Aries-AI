package com.ai.phoneagent.ui.inputbar

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动画效果规范
 * 包含所有输入栏相关的动画参数配置
 */
object InputBarAnimations {

    // 键盘弹出/收起动画插值器 - Decelerate
    val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // 弹簧效果 - 用于按钮缩放
    val ButtonPressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 标准过渡动画 - 300ms
    val StandardTransitionSpec = tween<Float>(
        durationMillis = 300,
        easing = DecelerateEasing // 使用类似 DecelerateInterpolator 的效果
    )
    
    // 颜色过渡动画 - 200ms
    val ColorTransitionSpec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = 200
    )
    
    // 高度变化动画 - 300ms
    val HeightTransitionSpec = tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 300,
        easing = DecelerateEasing
    )
}
