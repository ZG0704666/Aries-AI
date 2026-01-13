package com.ai.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Matrix
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

object AutomationOverlay {

    private var wm: WindowManager? = null
    private var container: OverlayContainer? = null

    private var maxSteps: Int = 1
    
    // 【优化】预估的总步骤数（从模型思考中解析）
    private var estimatedTotalSteps: Int = 0
    // 【优化】是否已解析过预估步骤数
    private var hasEstimatedSteps: Boolean = false
    // 【优化新增】流式思考显示
    private var isShowingThinking: Boolean = false
    private var thinkingText: String = ""

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
        // 【优化】重置预估步骤数
        this.estimatedTotalSteps = 0
        this.hasEstimatedSteps = false

        val appCtx = context.applicationContext
        val svc = PhoneAgentAccessibilityService.instance
        val windowCtx = svc ?: appCtx
        val w = windowCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val view = OverlayContainer(windowCtx)
        view.setTexts(title, subtitle)
        view.setProgress(0f)
        view.setOnClickListener {
            val i = Intent(appCtx, AutomationActivityNew::class.java)
            i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            appCtx.startActivity(i)
        }

        val overlayW = dp(appCtx, 108)
        val overlayH = dp(appCtx, 108)

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

    fun setOverlayVisible(visible: Boolean) {
        val v = container ?: return
        v.post {
            v.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            v.alpha = if (visible) 1f else 0f
        }
    }
    
    /**
     * 临时隐藏悬浮窗（执行点击操作时使用，防止点击到悬浮窗）
     */
    fun temporaryHide() {
        val v = container ?: return
        v.post { v.visibility = View.GONE }
    }
    
    /**
     * 恢复悬浮窗显示
     */
    fun restoreVisibility() {
        val v = container ?: return
        v.post { v.visibility = View.VISIBLE }
    }
    
    /**
     * 更新预估总步骤数（用于动态进度显示）
     * 只在第一次解析出有效预估值时设置，后续不再更新
     */
    fun updateEstimatedSteps(estimated: Int) {
        if (estimated > 0 && !hasEstimatedSteps) {
            this.estimatedTotalSteps = estimated
            this.hasEstimatedSteps = true
            Log.d("AutomationOverlay", "设置预估总步骤数: $estimated")
        }
    }
    
    /**
     * 开始流式思考显示
     */
    fun startThinking() {
        val v = container ?: return
        isShowingThinking = true
        thinkingText = ""
        v.setTexts("思考中", "正在分析问题...")
    }
    
    /**
     * 更新流式思考内容
     * @param delta 新增的思考内容片段
     */
    fun updateThinking(delta: String) {
        if (!isShowingThinking) return
        val v = container ?: return
        
        thinkingText += delta
        
        // 提取有意义的关键词显示
        val meaningfulText = extractMeaningfulThinking(thinkingText)
        val displayText = if (meaningfulText.isNotBlank()) {
            meaningfulText.take(30) + if (meaningfulText.length > 30) "..." else ""
        } else {
            "正在思考..."
        }
        
        v.setTexts("思考中", displayText)
    }
    
    /**
     * 结束流式思考显示
     */
    fun stopThinking() {
        isShowingThinking = false
        thinkingText = ""
    }
    
    /**
     * 从思考文本中提取有意义的关键信息
     */
    private fun extractMeaningfulThinking(thinking: String): String {
        val text = thinking.trim()
        if (text.isBlank()) return ""
        
        // 匹配常见的行动关键词
        val actionPatterns = listOf(
            "需要点击", "点击", "输入", "滚动", "查找", "等待", 
            "打开", "关闭", "返回", "确认", "取消", "保存",
            "在", "找到", "看到", "应该", "可以", "正在"
        )
        
        // 尝试提取包含行动关键词的句子
        val sentences = text.split("[。！？\n]".toRegex())
        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.length in 10..50) { // 适中的句子长度
                for (pattern in actionPatterns) {
                    if (trimmed.contains(pattern)) {
                        return trimmed
                    }
                }
            }
        }
        
        // 如果没有找到合适的句子，返回前面的内容
        return text.take(40)
    }

    /**
     * 计算智能进度百分比
     * - 如果有预估步骤数：使用 step/estimated 计算真实百分比
     * - 如果没有预估：使用平滑的假进度曲线（0-90%渐进）
     */
    private fun calculateProgressPercent(step: Int): Int {
        return if (hasEstimatedSteps && estimatedTotalSteps > 0) {
            // 有预估步骤数时，使用真实计算
            // 允许超过100%（任务可能比预估更长），但显示时限制在100%
            val realPercent = (step.toFloat() / estimatedTotalSteps * 100).toInt()
            realPercent.coerceIn(0, 100)
        } else {
            // 无预估时，使用平滑的假进度曲线
            // 使用对数曲线：快速上升到60%，然后缓慢增长到90%
            val normalized = step.toFloat() / 15f // 假设15步达到较高进度
            val fakeProgress = when {
                normalized < 0.3f -> normalized * 2f // 0-30% 快速增长
                normalized < 0.6f -> 0.6f + (normalized - 0.3f) * 0.8f // 60-84%
                else -> 0.84f + kotlin.math.min((normalized - 0.6f) * 0.15f, 0.06f) // 84-90%
            }
            (fakeProgress * 100).toInt().coerceIn(0, 90)
        }
    }

    private fun calculateProgressFraction(step: Int, phaseInStep: Float): Float {
        val s = step.coerceAtLeast(0)
        val curr = calculateProgressPercent(s)
        val next = calculateProgressPercent(s + 1)
        val t = phaseInStep.coerceIn(0f, 1f)
        val percent = curr + ((next - curr) * t)
        return (percent / 100f).coerceIn(0f, 1f)
    }

    fun updateStep(step: Int, maxSteps: Int? = null, subtitle: String? = null) {
        updateProgress(step = step, phaseInStep = 0f, maxSteps = maxSteps, subtitle = subtitle)
    }

    fun updateProgress(step: Int, phaseInStep: Float, maxSteps: Int? = null, subtitle: String? = null) {
        val v = container ?: return
        // 【优化】不再用 maxSteps 参数覆盖预估值
        // maxSteps 参数现在仅作为配置上限参考，不参与进度计算
        if (maxSteps != null && !hasEstimatedSteps) {
            this.maxSteps = maxSteps.coerceAtLeast(1)
        }

        val frac = calculateProgressFraction(step = step, phaseInStep = phaseInStep)
        v.setProgress(frac)

        val sub = subtitle?.trim().orEmpty()
        val title = "执行中"
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

        private val ring = EdgeFlowView(context)
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
            setPadding(0, 0, 0, 0)
            background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#FFFFFFFF"))
                    }
            elevation = dp(8).toFloat()
            clipToPadding = false

            addView(
                    ring,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            val textBox = FrameLayout(context)
            textBox.setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                    textBox,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            title.setTextColor(Color.parseColor("#1B2B3D"))
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            title.gravity = Gravity.CENTER_HORIZONTAL
            title.maxLines = 2
            title.ellipsize = android.text.TextUtils.TruncateAt.END

            subtitle.setTextColor(Color.parseColor("#4A6FAE"))
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            subtitle.gravity = Gravity.CENTER_HORIZONTAL
            subtitle.maxLines = 2
            subtitle.ellipsize = android.text.TextUtils.TruncateAt.END

            val titleLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            titleLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            titleLp.topMargin = dp(-10)

            val subLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            subLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            subLp.topMargin = dp(14)

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

    private class EdgeFlowView(context: Context) : View(context) {

        private val trackPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#1F6BA8FF")
                }

        private val progressPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#6BA8FF")
                }

        private val headGlowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#6FF2FF")
                }

        private val outerGlowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#4CC8FF")
                }

        private val sparkOuterPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(120, 111, 242, 255)
                }

        private val sparkCorePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(230, 255, 255, 255)
                }

        private var progress: Float = 0f

        private val borderPath = Path()
        private val segmentPath = Path()
        private val headPath = Path()
        private var measure: PathMeasure? = null
        private var length: Float = 0f

        private var shader: Shader? = null
        private val shaderMatrix = Matrix()
        private var phase: Float = 0f

        private val spinner = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
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

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildPath()
        }

        private fun rebuildPath() {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val stroke = (w.coerceAtMost(h) * 0.026f).coerceIn(dp(1.2f), dp(2.0f))
            val pad = (stroke * 0.5f) + dp(0.8f)
            val rect = RectF(pad, pad, w - pad, h - pad)
            val baseR = dp(14f)
            val r = (baseR - pad).coerceAtLeast(0f)
                    .coerceAtMost(rect.width() / 2f)
                    .coerceAtMost(rect.height() / 2f)

            trackPaint.strokeWidth = stroke * 0.85f
            progressPaint.strokeWidth = stroke
            headGlowPaint.strokeWidth = stroke * 1.05f
            outerGlowPaint.strokeWidth = stroke * 1.55f

            trackPaint.alpha = 34
            progressPaint.alpha = 170

            borderPath.reset()
            borderPath.addRoundRect(rect, r, r, Path.Direction.CW)
            measure = PathMeasure(borderPath, true)
            length = measure?.length ?: 0f

            shader =
                    LinearGradient(
                            -w,
                            0f,
                            w * 2f,
                            h,
                            intArrayOf(
                                    Color.parseColor("#1B8CFF"),
                                    Color.parseColor("#6FF2FF"),
                                    Color.parseColor("#E8FBFF"),
                                    Color.parseColor("#1B8CFF")
                            ),
                            floatArrayOf(0f, 0.48f, 0.74f, 1f),
                            Shader.TileMode.CLAMP
                    )
        }

        private fun dp(v: Float): Float {
            return TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            v,
                            resources.displayMetrics
                    )
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (measure == null || length <= 0f) {
                rebuildPath()
            }
            val pm = measure ?: return
            val total = length
            if (total <= 0f) return

            canvas.drawPath(borderPath, trackPaint)

            val p = progress.coerceIn(0f, 1f)
            val end = total * p
            val headLen = (total * 0.045f).coerceIn(dp(6f), dp(12f))

            if (end <= dp(0.5f)) {
                // 进度几乎为 0：显示一段沿边缘流动的细流光（不填充进度）
                val center = total * phase
                val start = center - (headLen * 0.55f)
                val stop = center + (headLen * 0.45f)

                fun drawGlowSeg(from: Float, to: Float) {
                    headPath.reset()
                    pm.getSegment(from.coerceIn(0f, total), to.coerceIn(0f, total), headPath, true)
                    val pulse =
                            (0.55f +
                                            0.45f *
                                                    kotlin.math.abs(
                                                            kotlin.math.sin(phase * 6.2831853f)
                                                    ))
                                    .coerceIn(0f, 1f)
                    headGlowPaint.alpha = (120 + 120 * pulse).toInt().coerceIn(0, 255)
                    outerGlowPaint.alpha = (35 + 55 * pulse).toInt().coerceIn(0, 120)
                    shader?.let {
                        shaderMatrix.reset()
                        shaderMatrix.setTranslate(w * (phase - 0.5f), h * (0.5f - phase))
                        it.setLocalMatrix(shaderMatrix)
                        outerGlowPaint.shader = it
                        headGlowPaint.shader = it
                    }
                    canvas.drawPath(headPath, outerGlowPaint)
                    canvas.drawPath(headPath, headGlowPaint)
                    headGlowPaint.shader = null
                    outerGlowPaint.shader = null
                }

                if (start >= 0f && stop <= total) {
                    drawGlowSeg(start, stop)
                } else {
                    val s = (start % total + total) % total
                    val t = (stop % total + total) % total
                    if (s <= total) drawGlowSeg(s, total)
                    if (t >= 0f) drawGlowSeg(0f, t)
                }

                val sparkD = ((center % total) + total) % total
                val pos = FloatArray(2)
                pm.getPosTan(sparkD, pos, null)
                val pulse =
                        (0.55f +
                                        0.45f *
                                                kotlin.math.abs(
                                                        kotlin.math.sin(phase * 6.2831853f)
                                                ))
                                .coerceIn(0f, 1f)
                sparkOuterPaint.alpha = (55 + (85 * pulse)).toInt().coerceIn(0, 160)
                sparkCorePaint.alpha = (190 + (65 * pulse)).toInt().coerceIn(0, 255)
                canvas.drawCircle(pos[0], pos[1], dp(2.2f), sparkOuterPaint)
                canvas.drawCircle(pos[0], pos[1], dp(1.1f), sparkCorePaint)
            } else {
                segmentPath.reset()
                pm.getSegment(0f, end, segmentPath, true)
                shader?.let {
                    shaderMatrix.reset()
                    shaderMatrix.setTranslate(w * (phase - 0.5f) * 1.1f, h * (0.5f - phase) * 1.1f)
                    it.setLocalMatrix(shaderMatrix)
                    progressPaint.shader = it
                }
                canvas.drawPath(segmentPath, progressPaint)
                progressPaint.shader = null

                val headStart = (end - headLen).coerceAtLeast(0f)
                headPath.reset()
                pm.getSegment(headStart, end, headPath, true)

                val glowA = (completeAnim?.animatedValue as? Float) ?: 0f
                val pulse =
                        (0.55f +
                                        0.45f *
                                                kotlin.math.abs(
                                                        kotlin.math.sin(phase * 6.2831853f)
                                                ))
                                .coerceIn(0f, 1f)
                val alphaBase = (85 + 160 * pulse).toInt().coerceIn(0, 255)
                val coreA = (alphaBase + (70 * glowA).toInt()).coerceIn(0, 255)
                val outerA = (30 + (70 * pulse)).toInt().coerceIn(0, 120)

                outerGlowPaint.alpha = outerA
                headGlowPaint.alpha = coreA
                shader?.let {
                    shaderMatrix.reset()
                    shaderMatrix.setTranslate(w * (phase - 0.5f), h * (0.5f - phase))
                    it.setLocalMatrix(shaderMatrix)
                    outerGlowPaint.shader = it
                    headGlowPaint.shader = it
                }
                canvas.drawPath(headPath, outerGlowPaint)
                canvas.drawPath(headPath, headGlowPaint)
                headGlowPaint.shader = null
                outerGlowPaint.shader = null

                val pos = FloatArray(2)
                pm.getPosTan(end.coerceIn(0f, total), pos, null)
                sparkOuterPaint.alpha = (70 + (90 * pulse)).toInt().coerceIn(0, 170)
                sparkCorePaint.alpha = (205 + (50 * pulse)).toInt().coerceIn(0, 255)
                canvas.drawCircle(pos[0], pos[1], dp(2.2f), sparkOuterPaint)
                canvas.drawCircle(pos[0], pos[1], dp(1.1f), sparkCorePaint)
            }

            val ca = (completeAnim?.animatedValue as? Float) ?: 0f
            if (ca > 0f) {
                val a = (40 + (170 * ca)).toInt().coerceIn(0, 255)
                headGlowPaint.alpha = a
                canvas.drawPath(borderPath, headGlowPaint)
            }
        }
    }
}
