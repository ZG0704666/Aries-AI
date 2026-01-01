package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatingMode

class FloatingWindowState(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
    private val screenWidthDp: Dp
    private val screenHeightDp: Dp

    // Window position
    var x: Int = 200
    var y: Int = 200

    // Window size
    val windowWidth = mutableStateOf(300.dp)
    val windowHeight = mutableStateOf(400.dp)
    val windowScale = mutableStateOf(0.8f)
    var lastWindowScale: Float = 0.8f

    // Mode state
    val currentMode = mutableStateOf(FloatingMode.WINDOW)
    var previousMode: FloatingMode = FloatingMode.WINDOW
    val ballSize = mutableStateOf(60.dp)
    val isAtEdge = mutableStateOf(false)

    // DragonBones pet mode lock state
    var isPetModeLocked = mutableStateOf(false)

    // Transition state
    var lastWindowPositionX: Int = 0
    var lastWindowPositionY: Int = 0
    var lastBallPositionX: Int = 0
    var lastBallPositionY: Int = 0
    var isTransitioning = false
    val transitionDebounceTime = 500L // 防抖时间
    
    // Ball explosion animation state
    val ballExploding = mutableStateOf(false)

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).dp
        screenHeightDp = (displayMetrics.heightPixels / displayMetrics.density).dp
        restoreState()
    }

    fun saveState() {
        prefs.edit().apply {
            putInt("window_x", x)
            putInt("window_y", y)
            putFloat("window_width", windowWidth.value.value.coerceAtLeast(200f))
            putFloat("window_height", windowHeight.value.value.coerceAtLeast(250f))
            putString("current_mode", currentMode.value.name)
            putString("previous_mode", previousMode.name)
            putFloat("window_scale", windowScale.value.coerceIn(0.3f, 1.0f))
            putFloat("last_window_scale", lastWindowScale.coerceIn(0.3f, 1.0f))
            apply()
        }
    }

    fun restoreState() {
        // 始终使用默认值，不再读取保存的状态（除了高度）
        x = 200
        y = 200

        // 宽度使用屏幕宽度，高度默认为屏幕高度的一半
        windowWidth.value = screenWidthDp
        windowHeight.value = screenHeightDp / 2

        // 使用默认模式
        currentMode.value = FloatingMode.WINDOW
        previousMode = FloatingMode.WINDOW

        // 使用默认缩放值
        windowScale.value = 0.8f
        lastWindowScale = 0.8f
    }
} 