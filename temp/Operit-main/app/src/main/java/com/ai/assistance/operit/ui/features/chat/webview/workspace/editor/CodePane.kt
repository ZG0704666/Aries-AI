package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import com.ai.assistance.operit.util.AppLogger

/**
 * 代码编辑器容器，处理缩放和滚动
 */
class CodePane : HVScrollView, View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
    companion object {
        const val TAG = "CodePane_DEBUG"
        const val SCALE_MAX = 40.0f
        const val SCALE_MID = 20f
    }
    
    // 初始化时的缩放比例
    private var initScale = 5f
    
    // 缩放手势检测器
    private lateinit var mScaleGestureDetector: ScaleGestureDetector
    private var mScaleMatrix = SCALE_MID
    
    // 代码文本编辑器
    lateinit var mCodeText: CodeText
    private var mCodeTextMinHeight = 0
    private var mCodeTextMinWidth = 0
    
    // 延迟处理器
    private lateinit var delayer: TimeDelayer
    
    constructor(context: Context) : super(context) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    
    private fun init() {
        // 启用垂直滚动条
        isVerticalScrollBarEnabled = true
        
        // 设置布局参数
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // 设置背景颜色
        setBackgroundColor(0xFF333333.toInt())
        
        // 创建代码文本编辑器
        mCodeText = CodeText(context)
        mCodeText.setScrollView(this)
        addView(mCodeText)
        
        // 初始化延迟处理器
        delayer = TimeDelayer(0)
        
        // 设置缩放
        setOnTouchListener(this)
        mScaleGestureDetector = ScaleGestureDetector(context, this)
        setFlexible(false)
        setScale(mScaleMatrix)
        
        // 设置代码文本编辑器的父视图尺寸
        mCodeText.setPWidth(getScreenWidth())
        mCodeText.setPHeight(getScreenHeight())
        
        // 允许在子视图外部滚动
        setScrollableOutsideChile(true)
    }
    
    /**
     * 获取屏幕宽度
     */
    private fun getScreenWidth(): Int {
        return context.resources.displayMetrics.widthPixels
    }
    
    /**
     * 获取屏幕高度
     */
    private fun getScreenHeight(): Int {
        return context.resources.displayMetrics.heightPixels
    }
    
    override fun onDraw(canvas: Canvas) {
        // 计算CodeText宽高
        val codeWidth = width - paddingLeft - paddingRight
        val codeHeight = height - paddingTop - paddingBottom

        if (mCodeTextMinHeight != codeHeight || mCodeTextMinWidth != codeWidth) {
            mCodeTextMinWidth = codeWidth
            mCodeTextMinHeight = codeHeight
            mCodeText.minimumWidth = mCodeTextMinWidth
            mCodeText.minimumHeight = mCodeTextMinHeight
            postInvalidate()
        }

        super.onDraw(canvas)
    }
    
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // 滑动的时候，通知CodeText绘制高亮
        mCodeText.postInvalidate()
    }
    
    /**
     * 获取代码文本编辑器
     */
    fun getCodeText(): CodeText {
        return mCodeText
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun setLanguage(language: String) {
        mCodeText.setLanguage(language)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // 处理缩放手势
        mScaleGestureDetector.onTouchEvent(event)
        
        // 返回false，让事件继续传递给HVScrollView的onTouchEvent处理滚动
        return false
    }
    
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val oldScale = getScale()
        val scaleFactor = detector.scaleFactor

        if ((oldScale >= SCALE_MAX && scaleFactor > 1.0f) || (oldScale <= initScale && scaleFactor < 1.0f)) {
            return true
        }
        
        var newScale = oldScale * scaleFactor
        newScale = newScale.coerceIn(initScale, SCALE_MAX)

        if (newScale == oldScale) {
            return true
        }

        mScaleMatrix = newScale
        setScale(newScale)

        val effectiveScaleFactor = newScale / oldScale
        val focusX = detector.focusX
        val focusY = detector.focusY

        val newScrollX = ((scrollX + focusX) * effectiveScaleFactor - focusX).toInt()
        val newScrollY = ((scrollY + focusY) * effectiveScaleFactor - focusY).toInt()

        scrollTo(newScrollX, newScrollY)

        return true
    }
    
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        delayer.updateLastTime()
        mCodeText.addRangDraw()
        return true
    }
    
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        mCodeText.removeRangDraw()
    }
    
    /**
     * 设置缩放比例
     */
    fun setScale(scale: Float = mScaleMatrix) {
        mScaleMatrix = scale
        val textSize = scale / 2.0f
        mCodeText.textSize = textSize
    }
    
    /**
     * 获取当前缩放比例
     */
    fun getScale(): Float {
        return mScaleMatrix
    }
} 