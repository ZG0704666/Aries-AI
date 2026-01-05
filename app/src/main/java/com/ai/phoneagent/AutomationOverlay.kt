package com.ai.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.TextView
import androidx.core.content.ContextCompat

object AutomationOverlay {

    private var wm: WindowManager? = null
    private var container: OverlayContainer? = null

    private var maxSteps: Int = 1

    fun canDrawOverlays(context: Context): Boolean {
        if (PhoneAgentAccessibilityService.instance != null) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

        fun show(
            context: Context,
            title: String,
            subtitle: String,
            maxSteps: Int,
            activity: Activity? = null,
        ): Boolean {
        hide()

        this.maxSteps = maxSteps.coerceAtLeast(1)

        val appCtx = context.applicationContext
        val svc = PhoneAgentAccessibilityService.instance
        val windowCtx = svc ?: appCtx
        val w = windowCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val view = OverlayContainer(windowCtx)
        view.setTexts(title, subtitle)
        view.setProgress(0f)
        view.setOnClickListener {
            val i = Intent(appCtx, AutomationActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(i)
        }

        val overlayW = dp(appCtx, 148)
        val overlayH = dp(appCtx, 148)

        val flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val dm = appCtx.resources.displayMetrics
        val baseX = (dm.widthPixels - overlayW - dp(appCtx, 14)).coerceAtLeast(0)
        val baseY = dp(appCtx, 88).coerceAtLeast(0)

        val typeCandidates = buildList {
            if (svc != null) add(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)

            val overlayPermOk =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(appCtx)
                    } else {
                        true
                    }
            if (overlayPermOk) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    add(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    @Suppress("DEPRECATION") add(WindowManager.LayoutParams.TYPE_PHONE)
                }
            }
        }

        var lastError: Throwable? = null
        for (type in typeCandidates.distinct()) {
            val lp =
                    WindowManager.LayoutParams(
                            overlayW,
                            overlayH,
                            type,
                            flags,
                            android.graphics.PixelFormat.TRANSLUCENT
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
                                lastError = it
                                false
                            }
            if (ok) {
                wm = w
                container = view
                view.startSpinner()
                return true
            }
        }

        wm = null
        container = null

        if (lastError != null) {
            Log.e("AutomationOverlay", "Overlay addView failed", lastError)
            Toast.makeText(
                            appCtx,
                            "悬浮窗显示失败：${lastError?.javaClass?.simpleName}",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
        return false
    }

    fun isShowing(): Boolean = container != null && wm != null

    fun updateStep(step: Int, maxSteps: Int? = null, subtitle: String? = null) {
        val v = container ?: return
        if (maxSteps != null) {
            this.maxSteps = maxSteps.coerceAtLeast(1)
        }
        val s = step.coerceAtLeast(0)
        val frac = s.toFloat() / this.maxSteps.toFloat()
        val percent = (frac.coerceIn(0f, 1f) * 100).toInt()
        v.setProgress(frac.coerceIn(0f, 1f))
        val sub = subtitle?.trim().orEmpty()
        val title = "执行中（${percent}%）"
        if (sub.isNotBlank()) {
            v.setTexts(title, sub.take(34))
        } else {
            v.setTexts(title, v.subtitleText().take(34))
        }
    }

    fun updateFromLogLine(line: String) {
        val v = container ?: return

        val trimmed = simplifyLine(line).trim()
        if (trimmed.isNotBlank()) v.setTexts(v.titleText(), trimmed.take(34))
    }

    fun complete(message: String) {
        val v = container ?: return
        v.setProgress(1f)
        v.setTexts("已完成", message.take(34))
        v.playCompleteEffect {
            hide()
        }
    }

    fun hide() {
        val w = wm
        val v = container
        if (w != null && v != null) {
            runCatching { w.removeView(v) }
        }
        container = null
        wm = null
    }

    private fun parseStep(line: String): Int? {
        val idx = line.indexOf("[Step")
        if (idx < 0) return null
        val m = Regex("\\[Step\\s+(\\d+)\\]").find(line) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun simplifyLine(line: String): String {
        val raw = line.trim()
        val m = Regex("\\[Step\\s+\\d+\\]\\s*").find(raw)
        return if (m != null && m.range.first == 0) {
            raw.substring(m.range.last + 1).trimStart()
        } else {
            raw
        }
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        context.resources.displayMetrics
                )
                .toInt()
    }

    private class OverlayContainer(context: Context) : FrameLayout(context) {

        private val ring = RingView(context)
        private val title = TextView(context)
        private val subtitle = TextView(context)

        private var lp: WindowManager.LayoutParams? = null
        private var wm: WindowManager? = null

        private var downRawX = 0f
        private var downRawY = 0f
        private var downX = 0
        private var downY = 0
        private var dragging = false

        init {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18).toFloat()
                        setColor(Color.parseColor("#FFFFFFFF"))
                    }
            elevation = dp(10).toFloat()
            clipToPadding = false

            addView(
                    ring,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            val textBox = FrameLayout(context)
            addView(
                    textBox,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            title.setTextColor(Color.parseColor("#1B2B3D"))
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            title.gravity = Gravity.CENTER_HORIZONTAL

            subtitle.setTextColor(Color.parseColor("#4A6FAE"))
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            subtitle.gravity = Gravity.CENTER_HORIZONTAL

            val titleLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            titleLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            titleLp.topMargin = dp(-8)

            val subLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            subLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            subLp.topMargin = dp(16)

            textBox.addView(title, titleLp)
            textBox.addView(subtitle, subLp)

            isClickable = true
            isFocusable = false
        }

        fun attachWindowManager(wm: WindowManager, lp: WindowManager.LayoutParams) {
            this.wm = wm
            this.lp = lp
        }

        fun titleText(): String = title.text?.toString().orEmpty()

        fun subtitleText(): String = subtitle.text?.toString().orEmpty()

        fun setTexts(t: String, s: String) {
            title.text = t
            subtitle.text = s
        }

        fun setProgress(p: Float) {
            ring.setProgress(p)
        }

        fun startSpinner() {
            ring.start()
        }

        fun playCompleteEffect(onEnd: () -> Unit) {
            ring.playCompleteEffect(onEnd)
        }

        private fun dp(v: Int): Int {
            return TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            v.toFloat(),
                            resources.displayMetrics
                    )
                    .toInt()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = wm
            val layoutParams = lp
            if (w == null || layoutParams == null) return super.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = layoutParams.x
                    downY = layoutParams.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging) {
                        val slop = dp(6).toFloat()
                        if (kotlin.math.abs(dx) >= slop || kotlin.math.abs(dy) >= slop) {
                            dragging = true
                        }
                    }

                    if (dragging) {
                        val dm = resources.displayMetrics
                        val maxX = (dm.widthPixels - layoutParams.width).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - layoutParams.height).coerceAtLeast(0)
                        layoutParams.x = (downX + dx.toInt()).coerceIn(0, maxX)
                        layoutParams.y = (downY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { w.updateViewLayout(this, layoutParams) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    return if (dragging) {
                        true
                    } else {
                        performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return true
                }
            }

            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private class RingView(context: Context) : View(context) {

        private val ringPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

        private val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#6BA8FF")
                }

        private var progress: Float = 0f
        private var angle = 0f
        private var shader: SweepGradient? = null
        private var shaderCx = Float.NaN
        private var shaderCy = Float.NaN
        private val shaderMatrix = Matrix()

        private val spinner = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1600L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
        }

        private var completeAnim: android.animation.ValueAnimator? = null

        fun setProgress(p: Float) {
            progress = p
            invalidate()
        }

        fun start() {
            if (!spinner.isStarted) spinner.start()
        }

        fun playCompleteEffect(onEnd: () -> Unit) {
            completeAnim?.cancel()
            val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
            completeAnim = anim
            anim.duration = 900L
            anim.interpolator = LinearInterpolator()
            anim.addUpdateListener { invalidate() }
            anim.addListener(
                    object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationCancel(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            postDelayed({ onEnd() }, 900L)
                        }
                    }
            )
            anim.start()
        }

        override fun onDetachedFromWindow() {
            runCatching { spinner.cancel() }
            completeAnim?.cancel()
            completeAnim = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val stroke = w.coerceAtMost(h) * 0.09f
            ringPaint.strokeWidth = stroke
            glowPaint.strokeWidth = stroke * 1.35f

            val pad = stroke * 1.7f
            val rect = RectF(pad, pad, w - pad, h - pad)
            val cx = rect.centerX()
            val cy = rect.centerY()

            if (shader == null || cx != shaderCx || cy != shaderCy) {
                shader =
                        SweepGradient(
                                cx,
                                cy,
                                intArrayOf(
                                        Color.parseColor("#6BA8FF"),
                                        Color.parseColor("#6FF2FF"),
                                        Color.parseColor("#6BA8FF"),
                                        Color.parseColor("#FF7AD9"),
                                        Color.parseColor("#6BA8FF")
                                ),
                                floatArrayOf(0f, 0.35f, 0.58f, 0.82f, 1f)
                        )
                shaderCx = cx
                shaderCy = cy
            }

            shaderMatrix.reset()
            shaderMatrix.setRotate(angle, cx, cy)
            shader?.setLocalMatrix(shaderMatrix)
            ringPaint.shader = shader

            val startAngle = -90f
            val sweep = 360f * progress.coerceIn(0f, 1f)

            val glowA = (completeAnim?.animatedValue as? Float) ?: 0f
            if (glowA > 0f) {
                val a = (40 + (180 * glowA)).toInt().coerceIn(0, 255)
                glowPaint.color = Color.argb(a, 107, 168, 255)
                canvas.drawArc(rect, 0f, 360f, false, glowPaint)
            }

            canvas.drawArc(rect, startAngle, sweep, false, ringPaint)
        }
    }
}
