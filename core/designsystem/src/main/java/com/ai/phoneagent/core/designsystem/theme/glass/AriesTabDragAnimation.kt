package com.ai.phoneagent.core.designsystem.theme.glass

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    private val onDragStopped: suspend AriesTabDragAnimation.(settledIndex: Int) -> Unit,
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
    // 最近一次提交的目标页（点选/拖拽松手时同步更新）：仲裁方以它为准。
    // Animatable 的 targetValue/isAnimating 要等弹簧协程真正被调度后才更新，
    // 快速连点时协程排队会让它们滞后于点击意图，用它判断会提前交还 pager。
    var committedTargetIndex: Int = initialValue.fastRoundToInt()
        private set

    // 拖拽锚点：按下瞬间的显示位置，拖拽帧在其上累积位移（见 [dragBy]）。
    private var dragAnchorValue = initialValue

    /** 最近一次改点（retarget）时继承的飞行速度（页签/秒），供测试与调参观察。 */
    var lastRetargetVelocity: Float = 0f
        private set

    // 飞行期间的即时速度估计（页签/秒），由逐帧采样维护，提交时被捕获。
    private var sampledFlightVelocity = 0f

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
            // 在抬起事件的同步点捕获「手指此刻看到的档位」作为吸附目标：
            // 拖拽打断点选弹簧时，从当前显示位置吸附并取消仍在飞行的旧目标，
            // 而不是按滞后的 targetValue 继续飞向旧目标。
            val settled = value
                .fastRoundToInt()
                .fastCoerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
            animationScope.launch {
                onDragStopped(settled)
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
        // 按住即冻结：把滑块吸附到当前显示位置，取消任何仍在飞行的旧点选弹簧——
        // 拖拽从「手指看到的当前位置」起步，避免按住期间滑块从指下滑走。
        // 同时作为拖拽锚点：后续拖拽帧在该位置上累积位移。
        dragAnchorValue = valueAnimation.value
        animationScope.launch { snapToValue(dragAnchorValue) }
        _isPressed = true
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        // 位置立即交给 catchUp/pager，形变则像参考实现一样等滑块接近吸附点再恢复。
        _isPressed = false
        schedulePressRelease()
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    /**
     * 拖拽帧：在拖拽锚点（按下瞬间的显示位置）上累积 [delta]（页签单位）并让
     * 滑块弹簧追赶。用「按下位置 + 累积位移」而非滞后的 targetValue 计算目标，
     * 拖拽打断点选弹簧时不会被旧目标拽走，连续拖动的目标也只由手指位移决定。
     */
    fun dragBy(delta: Float) {
        dragAnchorValue = (dragAnchorValue + delta).coerceIn(valueRange)
        updateValue(dragAnchorValue)
    }

    /**
     * 点选/拖拽松手提交目标页：同步记录意图后再启动弹簧（对齐参考
     * `DampedDragAnimation.animateToValue`，含按压→到位→松开的形变编排）。
     * 同步记录是关键：仲裁方读 [committedTargetIndex] 不受协程排队延迟影响，
     * 消除快速连点时「弹簧尚未起飞就被判空闲、滑块被 snap 回 pager 旧进度」的竞态。
     */
    fun commitTo(index: Int) {
        val target = index.coerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
        committedTargetIndex = target
        animateToValue(target.toFloat())
    }

    fun animateToValue(value: Float) {
        animationScope.launch { animateToValueAwait(value) }
    }

    /**
     * 等待动画结束的版本：供「松手后与 pager tween 同步追赶」使用，
     * 调用方可在此期间保留本地位置驱动，动画结束后再交还 pager。
     */
    suspend fun animateToValueAwait(
        value: Float,
        animationSpec: AnimationSpec<Float> = valueAnimationSpec,
    ) {
        // 在 mutex 内 await 动画：提前释放会让后续 snapToValue 撞上还在飞行的弹簧。
        // 被更新的提交抢占时本协程整体取消，形变恢复交给新的提交处理。
        mutatorMutex.mutate {
            val target = value.coerceIn(valueRange)
            // 改点（retarget）时记录被打断飞行的即时速度：Animatable 会把中断速度
            // 继承进新动画，弹簧「换目标不停车」；测试用它断言速度被保留。
            val inheritedVelocity = sampledFlightVelocity
            if (inheritedVelocity != 0f) lastRetargetVelocity = inheritedVelocity
            sampledFlightVelocity = 0f
            // 逐帧采样飞行速度，供下一次改点继承。
            val velocitySampler = animationScope.launch {
                var lastValue = valueAnimation.value
                var lastNanos = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val current = valueAnimation.value
                        if (lastNanos > 0L && now > lastNanos) {
                            val dt = (now - lastNanos) / 1e9f
                            sampledFlightVelocity = (current - lastValue) / dt
                        }
                        lastValue = current
                        lastNanos = now
                    }
                }
            }
            try {
                // 对齐参考 animateToValue：点选先进入按压形变（滑块放大、lens/高光亮起），
                // 弹簧到位后再恢复——「按下去→弹过去→到位松开」的液态手感。
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
                }
                // 程序吸附不再制造新的速度挤压；与参考一致，只让拖动残留速度衰减到 0。
                if (velocity != 0f) {
                    animationScope.launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                valueAnimation.animateTo(target, animationSpec)
            } finally {
                velocitySampler.cancel()
            }
        }
        // 到位后恢复形变；不占用 mutex——等待期间新的提交可以随时抢占。
        schedulePressRelease()
    }

    /**
     * 形变恢复：等滑块逼近目标（范围 2.5% 内）再回弹，与参考 release() 一致；
     * 有界等待避免 pager 交接异常时出现永久按住或形变不释放。
     */
    private fun schedulePressRelease() {
        animationScope.launch {
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                withTimeoutOrNull(500L) {
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - valueAnimation.targetValue) < threshold }
                        .first()
                }
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
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