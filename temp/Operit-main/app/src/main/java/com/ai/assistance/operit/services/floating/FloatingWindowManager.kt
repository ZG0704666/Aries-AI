package com.ai.assistance.operit.services.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.AppLogger
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingChatWindow
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.floating.FloatingWindowTheme

enum class StatusIndicatorStyle {
    FULLSCREEN_RAINBOW,
    TOP_BAR
}

interface FloatingWindowCallback {
    fun onClose()
    fun onSendMessage(message: String, promptType: PromptFunctionType = PromptFunctionType.CHAT)
    fun onCancelMessage()
    fun onAttachmentRequest(request: String)
    fun onRemoveAttachment(filePath: String)
    fun getMessages(): List<ChatMessage>
    fun getAttachments(): List<AttachmentInfo>
    fun saveState()
    fun getColorScheme(): ColorScheme?
    fun getTypography(): Typography?
    fun getInputProcessingState(): State<InputProcessingState>
    fun getStatusIndicatorStyle(): StatusIndicatorStyle
}

class FloatingWindowManager(
        private val context: Context,
        private val state: FloatingWindowState,
        private val lifecycleOwner: LifecycleOwner,
        private val viewModelStoreOwner: ViewModelStoreOwner,
        private val savedStateRegistryOwner: SavedStateRegistryOwner,
        private val callback: FloatingWindowCallback
) {
    private val TAG = "FloatingWindowManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var statusIndicatorView: ComposeView? = null
    private var isViewAdded = false
    private var isIndicatorAdded = false
    private var sizeAnimator: ValueAnimator? = null
    private var windowDisplayEnabled: Boolean = true
    private var windowPersistentHidden: Boolean = false
    private var indicatorDisplayEnabled: Boolean = true
    private var indicatorPersistentEnabled: Boolean = false

    private fun cancelFocusBeforeExit() {
        val view = composeView ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            view.clearFocus()
        } catch (_: Exception) {
        }
        try {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {
        }
        updateViewLayout { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
    }

    fun prepareForExit() {
        cancelFocusBeforeExit()
    }

    companion object {
        // Private flag to disable window move animations
        private const val PRIVATE_FLAG_NO_MOVE_ANIMATION = 0x00000040
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isViewAdded) return

        try {
            composeView =
                    ComposeView(context).apply {
                        setViewTreeLifecycleOwner(lifecycleOwner)
                        setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

                        setContent {
                            FloatingWindowTheme(
                                    colorScheme = callback.getColorScheme(),
                                    typography = callback.getTypography()
                            ) { FloatingChatUi() }
                        }
                    }

            val params = createLayoutParams()
            windowManager.addView(composeView, params)
            isViewAdded = true
            AppLogger.d(TAG, "Floating view added at (${params.x}, ${params.y})")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating floating view", e)
        }
    }

    fun destroy() {
        hideStatusIndicator()
        if (isViewAdded) {
            composeView?.let {
                cancelFocusBeforeExit()
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing view", e)
                }
                composeView = null
                isViewAdded = false
            }
        }
    }

    @Composable
    private fun FloatingChatUi() {
        FloatingChatWindow(
                messages = callback.getMessages(),
                width = state.windowWidth.value,
                height = state.windowHeight.value,
                windowScale = state.windowScale.value,
                onScaleChange = { newScale ->
                    state.windowScale.value = newScale.coerceIn(0.5f, 1.0f)
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                onClose = {
                    cancelFocusBeforeExit()
                    callback.onClose()
                },
                onResize = { newWidth, newHeight ->
                    state.windowWidth.value = newWidth
                    state.windowHeight.value = newHeight
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                currentMode = state.currentMode.value,
                previousMode = state.previousMode,
                ballSize = state.ballSize.value,
                onModeChange = { newMode -> switchMode(newMode) },
                onMove = { dx, dy, scale -> onMove(dx, dy, scale) },
                saveWindowState = { callback.saveState() },
                onSendMessage = { message, promptType ->
                    callback.onSendMessage(message, promptType)
                },
                onCancelMessage = { callback.onCancelMessage() },
                onInputFocusRequest = { setFocusable(it) },
                attachments = callback.getAttachments(),
                onAttachmentRequest = { callback.onAttachmentRequest(it) },
                onRemoveAttachment = { callback.onRemoveAttachment(it) },
                chatService = context as? FloatingChatService,
                windowState = state,
                inputProcessingState = callback.getInputProcessingState()
        )
    }

    fun setFloatingWindowVisible(visible: Boolean) {
        windowDisplayEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Floating window visibility set to: $visible.")
    }

    fun setFloatingWindowPersistentHidden(hidden: Boolean) {
        windowPersistentHidden = hidden
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Floating window persistent hidden set to: $hidden.")
    }

    fun setStatusIndicatorVisible(visible: Boolean) {
        indicatorDisplayEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Status indicator visibility set to: $visible.")
    }

    fun setStatusIndicatorPersistentVisible(visible: Boolean) {
        indicatorPersistentEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Status indicator persistent visibility set to: $visible.")
    }

    private fun refreshWindowAndIndicatorVisibility() {
        val currentMode = state.currentMode.value
        val view = composeView

        val windowVisible = !windowPersistentHidden && windowDisplayEnabled

        view?.let { v ->
            v.visibility = if (windowVisible) View.VISIBLE else View.GONE
            if (windowVisible) {
                updateViewLayout { params ->
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            }
        }

        val indicatorShouldShow = when {
            !indicatorDisplayEnabled && !indicatorPersistentEnabled -> false
            indicatorPersistentEnabled -> true
            else -> !windowVisible &&
                    (currentMode == FloatingMode.FULLSCREEN || currentMode == FloatingMode.WINDOW)
        }

        if (indicatorShouldShow) {
            showStatusIndicator()
        } else {
            hideStatusIndicator()
        }
    }

    @Composable
    private fun TopBarStatusIndicator() {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentSize(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = context.getString(R.string.ui_automation_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    private fun showStatusIndicator() {
        if (isIndicatorAdded) return
        val style = callback.getStatusIndicatorStyle()
        statusIndicatorView = ComposeView(context).apply {
            // Set the necessary owners for the ComposeView to work correctly.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                FloatingWindowTheme(
                    colorScheme = callback.getColorScheme(),
                    typography = callback.getTypography()
                ) {
                    when (style) {
                        StatusIndicatorStyle.FULLSCREEN_RAINBOW -> FullscreenRainbowStatusIndicator()
                        StatusIndicatorStyle.TOP_BAR -> TopBarStatusIndicator()
                    }
                }
            }
        }
        val params = when (style) {
            StatusIndicatorStyle.FULLSCREEN_RAINBOW -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            StatusIndicatorStyle.TOP_BAR -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (context.resources.displayMetrics.density * 16).toInt()
            }
        }
        windowManager.addView(statusIndicatorView, params)
        isIndicatorAdded = true
        AppLogger.d(TAG, "Status indicator shown.")
    }

    private fun hideStatusIndicator() {
        if (isIndicatorAdded) {
            statusIndicatorView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing status indicator view", e)
                }
            }
            statusIndicatorView = null
            isIndicatorAdded = false
            AppLogger.d(TAG, "Status indicator hidden.")
        }
    }

    fun setStatusIndicatorAlpha(alpha: Float) {
        val view = statusIndicatorView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.alpha = alpha
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                statusIndicatorView?.alpha = alpha
                latch.countDown()
            }
            try {
                latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                AppLogger.e(TAG, "setStatusIndicatorAlpha interrupted", e)
                Thread.currentThread().interrupt()
            }
        }
    }

    @Composable
    private fun FullscreenRainbowStatusIndicator() {
        val infiniteTransition = rememberInfiniteTransition(label = "status_indicator_rainbow")
        val animatedProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "status_indicator_progress"
        )

        val rainbowColors = listOf(
            Color(0xFFFF5F6D),
            Color(0xFFFFC371),
            Color(0xFF47CF73),
            Color(0xFF00C6FF),
            Color(0xFF845EF7),
            Color(0xFFFF5F6D)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.025f
                val innerCornerRadius = CornerRadius(strokeWidth * 1.5f, strokeWidth * 1.5f)

                // Animated colorful border brush (moves back and forth smoothly)
                val phase = animatedProgress * size.maxDimension
                val borderBrush = Brush.linearGradient(
                    colors = rainbowColors,
                    start = Offset(-phase, 0f),
                    end = Offset(size.width - phase, size.height)
                )

                // Inner rounded rectangle used for the inner edge of the ring
                val innerRoundRect = RoundRect(
                    left = strokeWidth,
                    top = strokeWidth,
                    right = size.width - strokeWidth,
                    bottom = size.height - strokeWidth,
                    cornerRadius = innerCornerRadius
                )
                val innerPath = Path().apply {
                    addRoundRect(innerRoundRect)
                }

                // Create a path for the ring shape (outer square, inner round)
                val outerPath = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                }
                val ringPath = Path().apply {
                    op(outerPath, innerPath, PathOperation.Difference)
                }

                // Fill inside the inner edge with bands that shrink toward the center and fade to transparent,
                // keeping each band's edge as a rounded rectangle.
                clipPath(innerPath) {
                    val bandSteps = 5
                    val innerBandWidth = strokeWidth * 3f
                    val singleBandWidth = innerBandWidth / bandSteps
                    val maxAlpha = 0.32f

                    for (i in 0 until bandSteps) {
                        val t = i / (bandSteps - 1).coerceAtLeast(1).toFloat()
                        val alpha = (1f - t) * maxAlpha

                        // Each band is an inset rounded rectangle, shrinking toward the center
                        val inset = i * singleBandWidth + singleBandWidth / 2f

                        val bandLeft = innerRoundRect.left + inset
                        val bandTop = innerRoundRect.top + inset
                        val bandRight = innerRoundRect.right - inset
                        val bandBottom = innerRoundRect.bottom - inset
                        if (bandRight <= bandLeft || bandBottom <= bandTop) break

                        val bandCornerRadius = CornerRadius(
                            (innerCornerRadius.x - inset).coerceAtLeast(0f),
                            (innerCornerRadius.y - inset).coerceAtLeast(0f)
                        )

                        drawRoundRect(
                            brush = borderBrush,
                            topLeft = Offset(bandLeft, bandTop),
                            size = Size(bandRight - bandLeft, bandBottom - bandTop),
                            cornerRadius = bandCornerRadius,
                            style = Stroke(width = singleBandWidth),
                            alpha = alpha
                        )
                    }
                }

                // Draw the outer ring on top to keep the edge crisp
                drawPath(
                    path = ringPath,
                    brush = borderBrush,
                    alpha = 0.7f
                )
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val params =
                WindowManager.LayoutParams(
                        0, // width
                        0, // height
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        0, // flags
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.START

        // Disable system move animations to allow custom animations to take full control
        setPrivateFlag(params, PRIVATE_FLAG_NO_MOVE_ANIMATION)

        when (state.currentMode.value) {
            FloatingMode.FULLSCREEN, FloatingMode.SCREEN_OCR -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.flags = 0 // Focusable
                state.x = 0
                state.y = 0
            }
            FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                params.width = ballSizeInPx
                params.height = ballSizeInPx
                params.flags =
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                val safeMargin = (16 * density).toInt()
                val minVisible = ballSizeInPx / 2
                state.x =
                        state.x.coerceIn(
                                -ballSizeInPx + minVisible + safeMargin,
                                screenWidth - minVisible - safeMargin
                        )
                state.y = state.y.coerceIn(safeMargin, screenHeight - minVisible - safeMargin)
            }
            FloatingMode.WINDOW -> {
                val scale = state.windowScale.value
                val windowWidthDp = state.windowWidth.value
                val windowHeightDp = state.windowHeight.value
                params.width = (windowWidthDp.value * density * scale).toInt()
                params.height = (windowHeightDp.value * density * scale).toInt()
                params.flags =
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                val minVisibleWidth = (params.width * 2 / 3)
                val safeMargin = (20 * density).toInt()
                state.x =
                        state.x.coerceIn(
                                -(params.width - minVisibleWidth) + safeMargin,
                                screenWidth - minVisibleWidth - safeMargin
                        )
                state.y =
                        state.y.coerceIn(
                                safeMargin,
                                screenHeight - (params.height / 2) - safeMargin
                        )
            }
            FloatingMode.RESULT_DISPLAY -> {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                
                // 保持位置逻辑与球体类似，确保可见
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                val minVisible = ballSizeInPx / 2
                state.x = state.x.coerceIn(-ballSizeInPx + minVisible, screenWidth - minVisible)
                state.y = state.y.coerceIn(0, screenHeight - minVisible)
            }
        }

        params.x = state.x
        params.y = state.y

        state.isAtEdge.value = isAtEdge(params.x, params.width)

        return params
    }

    private fun setPrivateFlag(params: WindowManager.LayoutParams, flags: Int) {
        try {
            val field = params.javaClass.getField("privateFlags")
            field.setInt(params, field.getInt(params) or flags)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to set privateFlags", e)
        }
    }

    private fun isAtEdge(x: Int, width: Int): Boolean {
        val screenWidth = context.resources.displayMetrics.widthPixels
        // A small tolerance to account for rounding errors or slight offsets
        val tolerance = 5 
        return x <= tolerance || x >= screenWidth - width - tolerance
    }

    private fun updateWindowSizeInLayoutParams() {
        updateViewLayout { params ->
            val density = context.resources.displayMetrics.density
            val scale = state.windowScale.value
            val widthDp = state.windowWidth.value
            val heightDp = state.windowHeight.value
            params.width = (widthDp.value * density * scale).toInt()
            params.height = (heightDp.value * density * scale).toInt()
        }
    }

    private fun updateViewLayout(configure: (WindowManager.LayoutParams) -> Unit = {}) {
        composeView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            configure(params)
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun calculateCenteredPosition(
            fromX: Int,
            fromY: Int,
            fromWidth: Int,
            fromHeight: Int,
            toWidth: Int,
            toHeight: Int
    ): Pair<Int, Int> {
        val centerX = fromX + fromWidth / 2
        val centerY = fromY + fromHeight / 2
        val newX = centerX - toWidth / 2
        val newY = centerY - toHeight / 2
        return Pair(newX, newY)
    }

    private fun switchMode(newMode: FloatingMode) {
        if (state.isTransitioning || state.currentMode.value == newMode) return
        state.isTransitioning = true

        if (newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL) {
            cancelFocusBeforeExit()
        }

        // 取消之前的动画
        sizeAnimator?.cancel()

        val view = composeView ?: return
        val currentParams = view.layoutParams as WindowManager.LayoutParams

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val startWidth = currentParams.width
        val startHeight = currentParams.height
        val startX = currentParams.x
        val startY = currentParams.y
        
        com.ai.assistance.operit.util.AppLogger.d("FloatingWindowManager", 
            "switchMode: from=${state.currentMode.value} to=$newMode, " +
            "startPos=($startX,$startY), startSize=($startWidth,$startHeight), " +
            "screenSize=($screenWidth,$screenHeight)")

        // Logic for leaving a mode
        state.previousMode = state.currentMode.value
        when (state.currentMode.value) {
            FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                state.lastBallPositionX = currentParams.x
                state.lastBallPositionY = currentParams.y
            }
            FloatingMode.WINDOW -> {
                state.lastWindowPositionX = currentParams.x
                state.lastWindowPositionY = currentParams.y
                state.lastWindowScale = state.windowScale.value
            }
            FloatingMode.FULLSCREEN -> {
                // Leaving fullscreen, no special state to save
            }
            FloatingMode.SCREEN_OCR -> {
                // Leaving screen ocr, no special state to save
            }
            FloatingMode.RESULT_DISPLAY -> {
                // Leaving result display, no special state to save
            }
        }

        state.currentMode.value = newMode
        callback.saveState()

        // 计算目标尺寸和位置
        data class TargetParams(
            val width: Int,
            val height: Int,
            val x: Int,
            val y: Int,
            val flags: Int,
            val gravity: Int = Gravity.TOP or Gravity.START
        )

        val target = when (newMode) {
                FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                
                // 如果从全屏模式切换，球应该出现在屏幕右侧中间位置
                val (newX, newY) = if (state.previousMode == FloatingMode.FULLSCREEN) {
                    // 球出现在屏幕右侧，垂直居中
                    val rightX = screenWidth - ballSizeInPx
                    val centerY = (screenHeight - ballSizeInPx) / 2
                    Pair(rightX, centerY)
                } else if (state.previousMode == FloatingMode.RESULT_DISPLAY) {
                    // 从结果展示模式切回时，直接恢复到原来的位置
                    Pair(state.lastBallPositionX, state.lastBallPositionY)
                } else {
                    // 处理 MATCH_PARENT (-1) 的情况，使用实际屏幕尺寸
                    val actualStartWidth = if (startWidth == WindowManager.LayoutParams.MATCH_PARENT) {
                        screenWidth
                    } else {
                        startWidth
                    }
                    val actualStartHeight = if (startHeight == WindowManager.LayoutParams.MATCH_PARENT) {
                        screenHeight
                    } else {
                        startHeight
                    }
                    
                    calculateCenteredPosition(
                        startX, startY, actualStartWidth, actualStartHeight,
                        ballSizeInPx, ballSizeInPx
                    )
                }
                
                com.ai.assistance.operit.util.AppLogger.d("FloatingWindowManager", 
                    "Ball target before coerce: newPos=($newX,$newY), ballSize=$ballSizeInPx")
                    val minVisible = ballSizeInPx / 2
                val finalX = newX.coerceIn(-ballSizeInPx + minVisible, screenWidth - minVisible)
                val finalY = newY.coerceIn(0, screenHeight - minVisible)
                com.ai.assistance.operit.util.AppLogger.d("FloatingWindowManager", 
                    "Ball target after coerce: finalPos=($finalX,$finalY)")
                TargetParams(ballSizeInPx, ballSizeInPx, finalX, finalY, flags)
                }
                FloatingMode.WINDOW -> {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                val width = (state.windowWidth.value.value * density * state.lastWindowScale).toInt()
                val height = (state.windowHeight.value.value * density * state.lastWindowScale).toInt()
                
                val isFromBall = state.previousMode == FloatingMode.BALL || 
                                state.previousMode == FloatingMode.VOICE_BALL

                val (tempX, tempY) = if (isFromBall) {
                                calculateCenteredPosition(
                        startX, startY, startWidth, startHeight,
                        width, height
                    )
                    } else {
                    Pair(state.lastWindowPositionX, state.lastWindowPositionY)
                    }
                    state.windowScale.value = state.lastWindowScale

                    // Coerce position to be within screen bounds for window mode
                val finalX: Int
                val finalY: Int
                
                if (isFromBall) {
                    // Limit strictly within screen when expanding from ball
                    val maxX = (screenWidth - width).coerceAtLeast(0)
                    val maxY = (screenHeight - height).coerceAtLeast(0)
                    finalX = tempX.coerceIn(0, maxX)
                    finalY = tempY.coerceIn(0, maxY)
                } else {
                    val minVisibleWidth = (width * 2 / 3)
                    val minVisibleHeight = (height * 2 / 3)
                    finalX = tempX.coerceIn(
                        -(width - minVisibleWidth),
                        screenWidth - minVisibleWidth / 2
                    )
                    finalY = tempY.coerceIn(0, screenHeight - minVisibleHeight)
                }
                
                TargetParams(width, height, finalX, finalY, flags)
                }
                FloatingMode.FULLSCREEN, FloatingMode.SCREEN_OCR -> {
                val flags = 0 // Remove all flags, making it focusable
                TargetParams(screenWidth, screenHeight, 0, 0, flags)
            }
            FloatingMode.RESULT_DISPLAY -> {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                val ballCenter = startX + ballSizeInPx / 2
                
                val finalGravity: Int
                val finalX: Int
                
                if (ballCenter > screenWidth / 2) {
                    // 球在右半屏，结果显示在球左侧（右对齐）
                    finalGravity = Gravity.TOP or Gravity.END
                    // x 是距离右边的距离
                    finalX = screenWidth - (startX + ballSizeInPx)
                } else {
                    // 球在左半屏，结果显示在球右侧（左对齐）
                    finalGravity = Gravity.TOP or Gravity.START
                    finalX = startX
                }

                TargetParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, 
                    WindowManager.LayoutParams.WRAP_CONTENT, 
                    finalX, 
                    startY, 
                    flags,
                    finalGravity
                )
            }
        }

        // 判断是否在球模式和其他模式之间切换
        val isBallTransition = (state.previousMode == FloatingMode.BALL || 
                                state.previousMode == FloatingMode.VOICE_BALL) ||
                               (newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL)
        
        if (isBallTransition) {
            // 球模式切换：需要与 Compose AnimatedContent 动画同步
            val isToBall = newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL
            val isFromBall = state.previousMode == FloatingMode.BALL || state.previousMode == FloatingMode.VOICE_BALL
            
            if (isToBall && !isFromBall) {
                // 其他模式 -> 球模式
                // AnimatedContent: 旧内容在 150ms 内 fadeOut + scaleOut，新内容延迟 150ms 后用 350ms fadeIn + scaleIn
                // 策略：延迟 150ms 后再改变窗口物理尺寸，这样旧内容先消失，然后窗口变小，球再出现
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateViewLayout { params ->
                        params.width = target.width
                        params.height = target.height
                        params.x = target.x
                        params.y = target.y
                        params.flags = target.flags
                        params.gravity = target.gravity
                        
                        // Sync state with params
                        state.x = params.x
                        state.y = params.y
                    }
                }, 150) // 与 fadeOut/scaleOut 的时长匹配
                
            } else if (isFromBall && !isToBall) {
                // 球模式 -> 其他模式：触发淡出动画，球平滑消失
                // 1. 触发淡出动画（100ms）
                state.ballExploding.value = true
                
                // 2. 延迟 100ms 后改变窗口尺寸（此时球已经淡出消失）
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateViewLayout { params ->
                        params.width = target.width
                        params.height = target.height
                        params.x = target.x
                        params.y = target.y
                        params.flags = target.flags
                        params.gravity = target.gravity
                        
                        // Sync state with params
                        state.x = params.x
                        state.y = params.y
                    }
                    
                    // 重置淡出状态
                    state.ballExploding.value = false
                }, 100) // 与淡出动画时长匹配
            } else {
                // 球模式之间切换：立即更新窗口尺寸
                updateViewLayout { params ->
                    params.width = target.width
                    params.height = target.height
                    params.x = target.x
                    params.y = target.y
                    params.flags = target.flags
                    params.gravity = target.gravity
                    
                    // Sync state with params
                    state.x = params.x
                    state.y = params.y
                }
            }
            
            // 延迟标记过渡完成，与 AnimatedContent 动画时长匹配
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                state.isTransitioning = false
            }, 500) // 匹配 AnimatedContent 的最长动画时长
        } else {
            // 非球模式切换（如窗口↔全屏）：立即改变窗口尺寸
            updateViewLayout { params ->
                params.width = target.width
                params.height = target.height
                params.x = target.x
                params.y = target.y
                params.flags = target.flags
                params.gravity = target.gravity

                // Sync state with params
                state.x = params.x
                state.y = params.y
            }

            // 立即标记过渡完成
            state.isTransitioning = false
        }
    }

    private fun onMove(dx: Float, dy: Float, scale: Float) {
        if (state.currentMode.value == FloatingMode.FULLSCREEN) return // Disable move in fullscreen

        updateViewLayout { params ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            state.windowScale.value = scale

            val sensitivity =
                    if (state.currentMode.value == FloatingMode.BALL ||
                                    state.currentMode.value == FloatingMode.VOICE_BALL
                    )
                            1.0f
                    else scale
            params.x += (dx * sensitivity).toInt()
            params.y += (dy * sensitivity).toInt()

            if (state.currentMode.value == FloatingMode.BALL ||
                            state.currentMode.value == FloatingMode.VOICE_BALL
            ) {
                val ballSize = (state.ballSize.value.value * density).toInt()
                val minVisible = ballSize / 2
                params.x = params.x.coerceIn(-ballSize + minVisible, screenWidth - minVisible)
                params.y = params.y.coerceIn(0, screenHeight - minVisible)
            } else {
                val windowWidth = (state.windowWidth.value.value * density * scale).toInt()
                val windowHeight = (state.windowHeight.value.value * density * scale).toInt()
                val minVisibleWidth = (windowWidth * 2 / 3)
                val minVisibleHeight = (windowHeight * 2 / 3)
                params.x =
                        params.x.coerceIn(
                                -(windowWidth - minVisibleWidth),
                                screenWidth - minVisibleWidth / 2
                        )
                params.y = params.y.coerceIn(0, screenHeight - minVisibleHeight)
            }
            state.x = params.x
            state.y = params.y
        }
    }

    private fun setFocusable(needsFocus: Boolean) {
        val view = composeView ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if (needsFocus) {
            // Step 1: 更新窗口参数使其可获取焦点
            updateViewLayout { params ->
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

                // 为全屏模式特殊处理软键盘，以避免遮挡UI
                if (state.currentMode.value == FloatingMode.FULLSCREEN) {
                    @Suppress("DEPRECATION")
                    params.softInputMode =
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                }
            }

            // Step 2: 延迟请求焦点并显示键盘
            // 延迟是必要的，以确保WindowManager有足够的时间处理窗口标志的变更
            view.postDelayed(
                    {
                        view.requestFocus()
                        imm.showSoftInput(view.findFocus(), InputMethodManager.SHOW_IMPLICIT)
                    },
                    200
            )
        } else {
            // Step 1: 立即隐藏键盘
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            // Step 2: 延迟恢复窗口的不可聚焦状态（全屏模式除外）
            view.postDelayed(
                    {
                        updateViewLayout { params ->
                            // 在非全屏模式下，恢复FLAG_NOT_FOCUSABLE，以便与窗口下的内容交互
                            if (state.currentMode.value != FloatingMode.FULLSCREEN && state.currentMode.value != FloatingMode.SCREEN_OCR) {
                                params.flags =
                                        params.flags or
                                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            }
                            // 重置软键盘模式
                            params.softInputMode =
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                        }
                    },
                    100
            )
        }
    }

    /**
     * 获取当前使用的ComposeView实例
     * @return View? 当前的ComposeView实例，如果未创建则返回null
     */
    fun getComposeView(): View? {
        return composeView
    }
}
