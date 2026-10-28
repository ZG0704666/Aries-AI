package com.ai.phoneagent.core.designsystem.theme.glass

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 与参考实现一致的手势观察器：只观察按下/移动/抬起，不消费事件，
 * 因此可以与 clickable、draggable、pager 手势共存。
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null

    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change

        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}

/**
 * 选中胶囊的拖动动画：位置、按压缩放、速度挤压。
 * 移植自参考实现 `DampedDragAnimation`（仅 Android 单平台，去掉 expect/actual）。
 */
class AriesTabDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    private val visibilityThreshold: Float = 0.001f,
    private val initialScale: Float = 1f,
    private val pressedScale: Float,
    private val onDragStopped: suspend AriesTabDragAnimation.() -> Unit,
    private val onDrag: AriesTabDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    // 手指是否按在胶囊上（含拖动）；按住期间 pager 进度不得接管胶囊位置。
    private var _isPressed by mutableStateOf(false)

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    /** 当前是否处于弹簧飞行中（静止自愈时为真，pager 进度暂不接管滑块）。 */
    val isAnimating: Boolean get() = valueAnimation.isRunning
    val isPressed: Boolean get() = _isPressed

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        // awaitEachGesture 的受限协程不能直接调任意 suspend lambda，
        // 因此在启动的协程内顺序执行：先结算（吸附/请求切页），再恢复静息形变。
        fun stopGesture() {
            animationScope.launch {
                onDragStopped()
                release()
            }
        }
        inspectDragGestures(
            onDragStart = { press() },
            onDragEnd = { stopGesture() },
            onDragCancel = { stopGesture() },
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        _isPressed = true
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        _isPressed = false
        // 直接回弹：旧的“等弹簧接近目标再恢复”的等待（snapshotFlow.first）在
        // pager 接管驱动的新状态机下可能永远等不到，造成胶囊按住不松的死态。
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            // 在 mutex 内 await 动画：提前释放会让后续 snapToValue 撞上还在飞行的弹簧。
            mutatorMutex.mutate {
                val target = value.coerceIn(valueRange)
                valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
            }
        }
    }

    /**
     * 外部（pager 滚动）驱动时使用：立即对齐到给定位置，不做弹簧动画。
     * 以 MutatePriority.UserInput 抢占 mutex——取消任何进行中的弹簧飞行，
     * 保证逐帧贴进度不被旧动画覆盖。
     */
    suspend fun snapToValue(value: Float) {
        mutatorMutex.mutate(MutatePriority.UserInput) {
            valueAnimation.snapTo(value.coerceIn(valueRange))
            velocityAnimation.snapTo(0f)
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            SystemClock.uptimeMillis(),
            Offset(value, 0f),
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}