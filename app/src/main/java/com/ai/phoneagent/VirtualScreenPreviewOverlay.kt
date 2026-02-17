/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 */
package com.ai.phoneagent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ai.phoneagent.core.utils.DisplayUtils
import com.ai.phoneagent.input.InputHelper
import com.ai.phoneagent.vdiso.ShizukuVirtualDisplayEngine

/**
 * 虚拟屏预览悬浮窗 — 让用户实时观看虚拟屏执行过程
 *
 * 功能特性（对齐 autoglm_KY FloatingStatusService）：
 * - 实时渲染虚拟屏画面到悬浮窗 TextureView
 * - 底部控制栏：虚拟返回 / 虚拟Home / 暂停恢复 / 停止任务
 * - 右上角减号 = 最小化到后台，显示后台操作进度悬浮窗
 * - 支持拖拽移动
 * - 触摸注入层：支持用户在预览窗直接触摸操作虚拟屏（通过 InputHelper 队列化注入）
 *
 * 参考：autoglm_KY FloatingStatusService 工具箱按钮行
 */
object VirtualScreenPreviewOverlay {

    private const val TAG = "VdPreview"
    const val ACTION_STOP_AUTOMATION = "com.ai.phoneagent.ACTION_STOP_AUTOMATION"
    const val ACTION_PAUSE_TOGGLE = "com.ai.phoneagent.ACTION_PAUSE_TOGGLE"

    // 预览窗宽高（展开状态，dp）
    private const val EXPANDED_WIDTH_DP = 240
    private const val EXPANDED_HEIGHT_DP = 427 // 16:9 竖屏
    // 标题栏与底部控制栏高度
    private const val HEADER_HEIGHT_DP = 28
    private const val CONTROL_BAR_HEIGHT_DP = 40
    // 最小化缩略图
    private const val MINI_SIZE_DP = 48

    @Volatile private var wm: WindowManager? = null
    @Volatile private var containerView: PreviewContainer? = null
    @Volatile private var miniView: MiniThumb? = null
    @Volatile private var bgProgressView: BackgroundProgressView? = null // 后台操作进度悬浮窗
    @Volatile private var isExpanded: Boolean = true
    @Volatile private var isBound: Boolean = false
    @Volatile private var isPaused: Boolean = false

    // ════════════════════════════════════════════
    //  公开 API
    // ════════════════════════════════════════════

    /** 显示虚拟屏预览悬浮窗 */
    fun show(context: Context) {
        hide()

        val appCtx = context.applicationContext
        val svc = PhoneAgentAccessibilityService.instance
        val windowCtx = svc ?: appCtx
        val w = windowCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = appCtx.resources.displayMetrics

        val view = PreviewContainer(windowCtx)

        // 根据虚拟屏实际比例计算预览窗宽高
        val (contentW, contentH) = VirtualDisplayController.getContentSizeBestEffort(appCtx)
        val overlayW: Int
        val previewH: Int
        if (contentW > 0 && contentH > 0) {
            overlayW = DisplayUtils.dp(appCtx, EXPANDED_WIDTH_DP)
            val ratio = contentH.toFloat() / contentW.toFloat()
            previewH = (overlayW * ratio).toInt()
        } else {
            overlayW = DisplayUtils.dp(appCtx, EXPANDED_WIDTH_DP)
            previewH = DisplayUtils.dp(appCtx, EXPANDED_HEIGHT_DP)
        }
        val overlayH = previewH + DisplayUtils.dp(appCtx, HEADER_HEIGHT_DP) + DisplayUtils.dp(appCtx, CONTROL_BAR_HEIGHT_DP)

        val flags = DisplayUtils.createOverlayFlags()

        val baseX = DisplayUtils.dp(appCtx, 12)
        val baseY = dm.heightPixels - overlayH - DisplayUtils.dp(appCtx, 80)

        val typeCandidates = DisplayUtils.getOverlayTypeCandidates()

        var lastErr: Throwable? = null
        for (type in typeCandidates.distinct()) {
            val lp =
                    WindowManager.LayoutParams(
                            overlayW,
                            overlayH,
                            type,
                            flags,
                            PixelFormat.TRANSLUCENT
                    )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = baseX
            lp.y = baseY
            view.attachWindowManager(w, lp)
            val ok =
                    runCatching {
                        w.addView(view, lp)
                        true
                    }
                            .getOrElse {
                                lastErr = it
                                false
                            }
            if (ok) {
                wm = w
                containerView = view
                isExpanded = true
                isPaused = false
                Log.i(TAG, "Preview overlay shown: ${overlayW}x${overlayH}")
                bindPreviewWhenReady(view)
                return
            }
        }
        Log.e(TAG, "Failed to show preview overlay", lastErr)
    }

    /** 隐藏并清理所有悬浮窗 */
    fun hide() {
        unbindPreview()
        removeView(containerView)
        removeView(miniView)
        removeView(bgProgressView)
        containerView = null
        miniView = null
        bgProgressView = null
        wm = null
        isPaused = false
    }

    fun isShowing(): Boolean = (containerView != null || miniView != null) && wm != null

    /** 更新状态文字 */
    fun updateStatus(text: String) {
        containerView?.post { containerView?.setStatusText(text) }
    }

    /** 更新暂停状态（供 Activity 同步调用） */
    fun setPausedState(paused: Boolean) {
        isPaused = paused
        containerView?.post { containerView?.refreshPauseButton(paused) }
    }

    // ─── 最小化 / 恢复 ───

    /** 仅隐藏预览窗（不清理虚拟屏），不显示任何小悬浮窗 */
    private fun minimize(context: Context) {
        val w = wm ?: return
        val appCtx = context.applicationContext

        unbindPreview()
        removeView(containerView)
        containerView = null

        // 不再显示任何小悬浮窗（MiniThumb 和 BackgroundProgressView）
        // 用户点击缩小按钮后直接隐藏虚拟屏预览

        isExpanded = false
    }

    /** 显示后台操作进度悬浮窗 */
    private fun showBackgroundProgress(context: Context, w: WindowManager, type: Int, flags: Int) {
        val appCtx = context.applicationContext
        val bgProgress = BackgroundProgressView(appCtx)

        val width = DisplayUtils.dp(appCtx, 160)
        val height = DisplayUtils.dp(appCtx, 44)

        val lp = WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = DisplayUtils.dp(appCtx, 12)
        lp.y = DisplayUtils.dp(appCtx, 48)

        bgProgress.attachWindowManager(w, lp)
        bgProgress.onClose = { hide() }
        runCatching { w.addView(bgProgress, lp) }
        bgProgressView = bgProgress
    }

    /** 更新后台操作进度状态 */
    fun updateBackgroundProgress(text: String, progress: Int = -1) {
        bgProgressView?.post { bgProgressView?.updateProgress(text, progress) }
    }

    /** 从小缩略图恢复到完整预览 */
    private fun restore(context: Context) {
        removeView(miniView)
        removeView(bgProgressView)
        miniView = null
        bgProgressView = null
        show(context)
    }

    // ─── 预览绑定 ───

    private fun bindPreviewWhenReady(container: PreviewContainer) {
        container.onTextureAvailable = { surfaceTexture ->
            // 设置 SurfaceTexture 缓冲区大小为虚拟屏实际内容尺寸，确保预览清晰
            val (contentW, contentH) = VirtualDisplayController.getContentSizeBestEffort()
            if (contentW > 0 && contentH > 0) {
                surfaceTexture.setDefaultBufferSize(contentW, contentH)
                Log.i(TAG, "Preview SurfaceTexture buffer size set to: ${contentW}x${contentH}")
            }
            val surface = Surface(surfaceTexture)
            val result = ShizukuVirtualDisplayEngine.setOutputSurface(surface)
            if (result.isSuccess) {
                isBound = true
                Log.i(TAG, "Preview surface bound successfully")
            } else {
                Log.w(TAG, "Failed to bind preview surface", result.exceptionOrNull())
            }
        }
        container.onTextureDestroyed = { unbindPreview() }
    }

    private fun unbindPreview() {
        if (isBound) {
            runCatching { ShizukuVirtualDisplayEngine.restoreOutputSurfaceToImageReader() }
            isBound = false
        }
    }

    // ─── 工具 ───

    private fun removeView(view: View?) {
        if (view != null) runCatching { wm?.removeView(view) }
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        context.resources.displayMetrics
                )
                .toInt()
    }

    // ═══════════════════════════════════════════════════════
    //  MiniThumb - 最小化状态的圆形小悬浮按钮
    // ═══════════════════════════════════════════════════════

    class MiniThumb(context: Context) : FrameLayout(context) {

        private var currentWm: WindowManager? = null
        private var currentLp: WindowManager.LayoutParams? = null
        var onRestore: (() -> Unit)? = null

        private var isDragging = false
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f

        init {
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Color.argb(220, 40, 120, 220))
            bg.setStroke(dp(1), Color.argb(180, 255, 255, 255))
            background = bg
            elevation = dp(6).toFloat()

            val label =
                    TextView(context).apply {
                        text = "📺"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        layoutParams =
                                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    }
            addView(label)

            setOnClickListener { onRestore?.invoke() }
        }

        fun attachWindowManager(wm: WindowManager, lp: WindowManager.LayoutParams) {
            currentWm = wm
            currentLp = lp
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = currentLp ?: return super.onTouchEvent(event)
            val wm = currentWm ?: return super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    downX = event.rawX
                    downY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging &&
                                    (Math.abs(event.rawX - downX) > 10 ||
                                            Math.abs(event.rawY - downY) > 10)
                    )
                            isDragging = true
                    if (isDragging) {
                        lp.x += (event.rawX - lastX).toInt()
                        lp.y += (event.rawY - lastY).toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) performClick()
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dp(v: Int): Int =
                TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                v.toFloat(),
                                context.resources.displayMetrics
                        )
                        .toInt()
    }

    // ═══════════════════════════════════════════════════════
    //  BackgroundProgressView - 后台操作进度悬浮窗
    // ═══════════════════════════════════════════════════════

    class BackgroundProgressView(context: Context) : FrameLayout(context) {

        private var currentWm: WindowManager? = null
        private var currentLp: WindowManager.LayoutParams? = null
        var onClose: (() -> Unit)? = null

        private val statusText: TextView

        private var isDragging = false
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f

        init {
            // 整体背景
            val bg = GradientDrawable()
            bg.cornerRadius = dp(8).toFloat()
            bg.setColor(Color.argb(230, 40, 40, 50))
            bg.setStroke(dp(1), Color.argb(100, 100, 100, 150))
            background = bg
            elevation = dp(6).toFloat()

            // 左侧图标
            val icon =
                    TextView(context).apply {
                        text = "📱"
                        textSize = 16f
                        gravity = Gravity.CENTER
                        layoutParams = LayoutParams(dp(32), LayoutParams.MATCH_PARENT)
                    }
            addView(icon)

            // 中间状态文字
            statusText =
                    TextView(context).apply {
                        text = "后台运行中..."
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams =
                                LinearLayout.LayoutParams(
                                                0,
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                1f
                                        )
                                        .apply { marginStart = dp(4) }
                    }
            addView(statusText)

            // 右侧关闭按钮
            val closeBtn =
                    TextView(context).apply {
                        text = "✕"
                        setTextColor(Color.argb(180, 255, 255, 255))
                        textSize = 12f
                        gravity = Gravity.CENTER
                        layoutParams = LayoutParams(dp(24), LayoutParams.MATCH_PARENT)
                        setOnClickListener { onClose?.invoke() }
                    }
            addView(closeBtn)

            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        fun attachWindowManager(wm: WindowManager, lp: WindowManager.LayoutParams) {
            currentWm = wm
            currentLp = lp
        }

        fun updateProgress(text: String, progress: Int) {
            statusText.text = text
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = currentLp ?: return super.onTouchEvent(event)
            val wm = currentWm ?: return super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    downX = event.rawX
                    downY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging &&
                                    (Math.abs(event.rawX - downX) > 10 ||
                                            Math.abs(event.rawY - downY) > 10)
                    )
                            isDragging = true
                    if (isDragging) {
                        lp.x += (event.rawX - lastX).toInt()
                        lp.y += (event.rawY - lastY).toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dp(v: Int): Int =
                TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                v.toFloat(),
                                context.resources.displayMetrics
                        )
                        .toInt()
    }

    // ═══════════════════════════════════════════════════════
    //  PreviewContainer - 完整预览视图（标题栏 + 画面 + 控制栏）
    // ═══════════════════════════════════════════════════════

    class PreviewContainer(context: Context) : FrameLayout(context) {

        private var currentWm: WindowManager? = null
        private var currentLp: WindowManager.LayoutParams? = null

        private var textureView: TextureView? = null
        private var statusTextView: TextView? = null
        private var pauseBtn: TextView? = null

        // 拖拽
        private var isDragging = false
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var downX = 0f
        private var downY = 0f

        // 触摸注入状态
        private var touchDownTime = 0L
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var isTouchInjecting = false
        private val LONG_PRESS_THRESHOLD_MS = 450L
        private val TAP_SLOP_PX = 15

        // 回调
        var onTextureAvailable: ((SurfaceTexture) -> Unit)? = null
        var onTextureDestroyed: (() -> Unit)? = null

        init {
            buildViews()
        }

        fun attachWindowManager(wm: WindowManager, lp: WindowManager.LayoutParams) {
            currentWm = wm
            currentLp = lp
        }

        fun setStatusText(text: String) {
            statusTextView?.text = text
        }

        fun refreshPauseButton(paused: Boolean) {
            pauseBtn?.text = if (paused) "▶ 继续" else "⏸ 暂停"
        }

        // ─── 构建视图 ───

        private fun buildViews() {
            // 整体背景
            val bg = GradientDrawable()
            bg.cornerRadius = dp(12).toFloat()
            bg.setColor(Color.argb(230, 30, 30, 35))
            bg.setStroke(dp(1), Color.argb(100, 100, 100, 120))
            background = bg
            clipToOutline = true
            elevation = dp(8).toFloat()

            // === 标题栏 ===
            val header =
                    FrameLayout(context).apply {
                        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT_DP))
                        setBackgroundColor(Color.argb(200, 50, 50, 60))
                    }

            header.addView(
                    TextView(context).apply {
                        text = "虚拟屏预览"
                        setTextColor(Color.WHITE)
                        textSize = 11f
                        layoutParams =
                                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                                        .apply {
                                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                            marginStart = dp(8)
                                        }
                    }
            )

            // 右上角 - → 最小化到后台，显示后台操作进度悬浮窗
            header.addView(
                    TextView(context).apply {
                        text = "－"
                        setTextColor(Color.argb(200, 255, 255, 255))
                        textSize = 16f
                        gravity = Gravity.CENTER
                        layoutParams =
                                LayoutParams(dp(28), LayoutParams.MATCH_PARENT).apply {
                                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                                }
                        setOnClickListener { minimize(context) }
                    }
            )
            addView(header)

            // === TextureView 画面区域（支持触摸注入到虚拟屏） ===
            val tv =
                    TextureView(context).apply {
                        layoutParams =
                                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                                        .apply {
                                            topMargin = dp(HEADER_HEIGHT_DP)
                                            bottomMargin = dp(CONTROL_BAR_HEIGHT_DP)
                                        }
                        isOpaque = false
                    }
            tv.surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            Log.i(TAG, "TextureView available: ${w}x${h}")
                            onTextureAvailable?.invoke(st)
                        }
                        override fun onSurfaceTextureSizeChanged(
                                st: SurfaceTexture,
                                w: Int,
                                h: Int
                        ) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            onTextureDestroyed?.invoke()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
            // 触摸注入：将预览窗触摸映射到虚拟屏坐标并注入
            tv.setOnTouchListener { view, event ->
                val did = VirtualDisplayController.getDisplayId() ?: return@setOnTouchListener false
                val (contentW, contentH) = VirtualDisplayController.getContentSizeBestEffort()
                if (contentW <= 0 || contentH <= 0) return@setOnTouchListener false

                // 坐标映射：预览窗 → 虚拟屏
                val vx = (event.x / view.width * contentW).toInt().coerceIn(0, contentW - 1)
                val vy = (event.y / view.height * contentH).toInt().coerceIn(0, contentH - 1)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownTime = android.os.SystemClock.uptimeMillis()
                        touchDownX = event.x
                        touchDownY = event.y
                        isTouchInjecting = true
                        InputHelper.enqueueTouch(
                                did,
                                touchDownTime,
                                MotionEvent.ACTION_DOWN,
                                vx,
                                vy,
                                ensureFocus = true
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isTouchInjecting) {
                            InputHelper.enqueueTouch(
                                    did,
                                    touchDownTime,
                                    MotionEvent.ACTION_MOVE,
                                    vx,
                                    vy,
                                    ensureFocus = false
                            )
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isTouchInjecting) {
                            InputHelper.enqueueTouch(
                                    did,
                                    touchDownTime,
                                    MotionEvent.ACTION_UP,
                                    vx,
                                    vy,
                                    ensureFocus = false
                            )
                            isTouchInjecting = false
                        }
                        true
                    }
                    else -> false
                }
            }
            textureView = tv
            addView(tv)

            // === 状态条（悬浮在画面底部上方） ===
            val statusTv =
                    TextView(context).apply {
                        text = "执行中..."
                        setTextColor(Color.argb(200, 200, 200, 200))
                        textSize = 10f
                        setBackgroundColor(Color.argb(120, 0, 0, 0))
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        layoutParams =
                                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                                        .apply {
                                            gravity = Gravity.BOTTOM
                                            bottomMargin = dp(CONTROL_BAR_HEIGHT_DP)
                                        }
                    }
            statusTextView = statusTv
            addView(statusTv)

            // === 底部控制栏 ===
            val controlBar =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(Color.argb(220, 40, 40, 50))
                        layoutParams =
                                LayoutParams(LayoutParams.MATCH_PARENT, dp(CONTROL_BAR_HEIGHT_DP))
                                        .apply { gravity = Gravity.BOTTOM }
                        setPadding(dp(4), 0, dp(4), 0)
                    }

            // 控制按钮辅助函数
            fun makeCtrlBtn(label: String, flex: Float = 1f, onClick: () -> Unit): TextView {
                return TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    layoutParams =
                            LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    flex
                            )
                    setOnClickListener { onClick() }
                    isClickable = true
                    isFocusable = true
                    val pressedBg =
                            GradientDrawable().apply {
                                cornerRadius = dp(4).toFloat()
                                setColor(Color.argb(60, 255, 255, 255))
                            }
                    background = pressedBg
                }
            }

            // 1. 虚拟返回
            controlBar.addView(
                    makeCtrlBtn("◀ 返回") {
                        val did = VirtualDisplayController.getDisplayId() ?: return@makeCtrlBtn
                        VirtualDisplayController.injectBackBestEffort(did)
                    }
            )

            // 2. 虚拟 Home
            controlBar.addView(
                    makeCtrlBtn("● Home") {
                        val did = VirtualDisplayController.getDisplayId() ?: return@makeCtrlBtn
                        VirtualDisplayController.injectHomeBestEffort(did)
                    }
            )

            // 3. 暂停 / 继续
            val pBtn =
                    makeCtrlBtn("⏸ 暂停") {
                        val ctx = context.applicationContext
                        val intent =
                                Intent(ACTION_PAUSE_TOGGLE).apply { setPackage(ctx.packageName) }
                        runCatching { ctx.sendBroadcast(intent) }
                    }
            pauseBtn = pBtn
            controlBar.addView(pBtn)

            // 4. 停止任务
            controlBar.addView(
                    makeCtrlBtn("⏹ 停止") {
                        val ctx = context.applicationContext
                        VirtualDisplayController.cleanupAsync(ctx)
                        hide()
                        val intent =
                                Intent(ACTION_STOP_AUTOMATION).apply { setPackage(ctx.packageName) }
                        runCatching { ctx.sendBroadcast(intent) }
                    }
            )

            addView(controlBar)
        }

        // ─── 拖拽（标题栏区域：拖拽阈值检测，子 View 如最小化按钮正常点击） ───

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            // 标题栏区域 —— 仅在检测到拖拽手势时拦截，让子 View（减号按钮）的点击正常分发
            if (ev.y < dp(HEADER_HEIGHT_DP)) {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        downX = ev.rawX
                        downY = ev.rawY
                        lastTouchX = ev.rawX
                        lastTouchY = ev.rawY
                        return false // 不拦截 DOWN，让子 View（如最小化按钮）接收
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDragging &&
                                        (Math.abs(ev.rawX - downX) > 10 ||
                                                Math.abs(ev.rawY - downY) > 10)
                        ) {
                            isDragging = true
                        }
                        return isDragging // 仅拖拽时拦截
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = currentLp ?: return super.onTouchEvent(event)
            val wm = currentWm ?: return super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    downX = event.rawX
                    downY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging &&
                                    (Math.abs(event.rawX - downX) > 10 ||
                                            Math.abs(event.rawY - downY) > 10)
                    )
                            isDragging = true
                    if (isDragging) {
                        lp.x += (event.rawX - lastTouchX).toInt()
                        lp.y += (event.rawY - lastTouchY).toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dp(v: Int): Int =
                TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                v.toFloat(),
                                context.resources.displayMetrics
                        )
                        .toInt()
    }
}
