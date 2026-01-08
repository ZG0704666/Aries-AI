package com.ai.phoneagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.ai.phoneagent.AutomationActivityNew
import com.ai.phoneagent.R

/**
 * UI自动化进度显示Overlay
 * 在屏幕底部显示进度、状态和控制按钮
 */
class UIAutomationProgressOverlay private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UIAutomationProgressOverlay"

        @Volatile
        private var instance: UIAutomationProgressOverlay? = null

        fun getInstance(context: Context): UIAutomationProgressOverlay {
            return instance ?: synchronized(this) {
                instance ?: UIAutomationProgressOverlay(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    // UI组件
    private var tvProgress: TextView? = null
    private var tvStatus: TextView? = null
    private var btnPause: Button? = null
    private var btnCancel: Button? = null
    private var progressBar: ProgressBar? = null

    // 回调
    private var cancelCallback: (() -> Unit)? = null
    private var pauseToggleCallback: ((Boolean) -> Unit)? = null
    private var isPaused = false

    // 进度信息
    private var currentStep = 0
    private var totalSteps = 0

    /**
     * 显示进度Overlay
     */
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(
        totalSteps: Int,
        initialStatus: String,
        onCancel: () -> Unit,
        onTogglePause: (Boolean) -> Unit
    ) {
        runOnMainThread {
            if (overlayView != null) {
                hide()
            }

            this.totalSteps = totalSteps
            this.currentStep = 0
            this.cancelCallback = onCancel
            this.pauseToggleCallback = onTogglePause
            this.isPaused = false

            // 创建WindowManager
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // 创建布局参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 100 // 距离底部100像素
            }

            // 创建Overlay视图
            overlayView = LayoutInflater.from(context).inflate(
                R.layout.overlay_automation_progress,
                null
            )

            // 绑定UI组件
            overlayView?.let { view ->
                tvProgress = view.findViewById(R.id.tvProgress)
                tvStatus = view.findViewById(R.id.tvStatus)
                btnPause = view.findViewById(R.id.btnPause)
                btnCancel = view.findViewById(R.id.btnCancel)
                progressBar = view.findViewById(R.id.progressBar)

                view.setOnClickListener {
                    val i = Intent(context, AutomationActivityNew::class.java)
                    i.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    context.startActivity(i)
                }

                // 设置初始值
                updateProgress(0, initialStatus)

                // 设置按钮事件
                btnPause?.setOnClickListener {
                    togglePause()
                }

                btnCancel?.setOnClickListener {
                    cancelCallback?.invoke()
                    hide()
                }

                // 添加拖拽支持
                setupDragging(view, params)
            }

            // 添加到WindowManager
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to show overlay", e)
            }
        }
    }

    /**
     * 更新进度（支持百分比显示，未知总步数时自动计算假进度）
     */
    fun updateProgress(step: Int, status: String) {
        runOnMainThread {
            currentStep = step
            // 计算百分比：总步数已知时精确显示，否则按指数曲线假进度（0-90%）
            val estimatedPercentage = if (totalSteps > 0) {
                ((step.toFloat() / totalSteps) * 100).toInt()
            } else {
                // 假的百分比计算：按指数曲线上升到90%，模拟任务进度
                val fakeProgress = (step.toFloat() / 20f).coerceAtMost(1f)
                (fakeProgress * fakeProgress * 90).toInt() // 缓缓上升到90%
            }
            
            tvProgress?.text = "步骤: $step | 进度: $estimatedPercentage%"
            tvStatus?.text = status
            
            // 更新进度条（固定100%刻度）
            progressBar?.apply {
                max = 100
                progress = estimatedPercentage
            }
        }
    }

    /**
     * 更新状态文本
     */
    fun updateStatus(status: String) {
        runOnMainThread {
            tvStatus?.text = status
        }
    }

    /**
     * 切换暂停状态
     */
    private fun togglePause() {
        isPaused = !isPaused
        btnPause?.text = if (isPaused) "继续" else "暂停"
        pauseToggleCallback?.invoke(isPaused)
    }

    /**
     * 隐藏Overlay
     */
    fun hide() {
        runOnMainThread {
            try {
                overlayView?.let {
                    windowManager?.removeView(it)
                }
                overlayView = null
                windowManager = null
                tvProgress = null
                tvStatus = null
                btnPause = null
                btnCancel = null
                progressBar = null
                cancelCallback = null
                pauseToggleCallback = null
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to hide overlay", e)
            }
        }
    }

    fun isShowing(): Boolean = overlayView != null && windowManager != null

    fun setOverlayVisible(visible: Boolean) {
        runOnMainThread {
            overlayView?.let { v ->
                v.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                v.alpha = if (visible) 1f else 0f
            }
        }
    }

    /**
     * 设置拖拽功能
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragging(view: View, params: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop

        fun isTouchInside(child: View?, rawX: Float, rawY: Float): Boolean {
            if (child == null || !child.isShown) return false
            val loc = IntArray(2)
            child.getLocationOnScreen(loc)
            val left = loc[0]
            val top = loc[1]
            val right = left + child.width
            val bottom = top + child.height
            return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
        }

        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var dragging = false
        var handling = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val rawX = event.rawX
                    val rawY = event.rawY
                    handling =
                            !(isTouchInside(btnPause, rawX, rawY) ||
                                    isTouchInside(btnCancel, rawX, rawY))
                    if (!handling) {
                        return@setOnTouchListener false
                    }

                    downRawX = rawX
                    downRawY = rawY
                    downX = params.x
                    downY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!handling) {
                        return@setOnTouchListener false
                    }
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (!dragging) {
                        if (kotlin.math.abs(dx) >= slop || kotlin.math.abs(dy) >= slop) {
                            dragging = true
                        }
                    }

                    if (dragging) {
                        params.x = downX + dx.toInt()
                        params.y = downY - dy.toInt()
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!handling) {
                        return@setOnTouchListener false
                    }
                    handling = false
                    if (!dragging) {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (!handling) {
                        return@setOnTouchListener false
                    }
                    handling = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 在主线程执行
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post {
                try {
                    action()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error in main thread", e)
                }
            }
        }
    }
}
