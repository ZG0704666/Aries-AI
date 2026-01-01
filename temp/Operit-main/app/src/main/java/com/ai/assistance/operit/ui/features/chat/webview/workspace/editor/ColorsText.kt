package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.HashMap
import com.ai.assistance.operit.util.AppLogger

/**
 * 彩色文本编辑器，支持语法高亮和自定义绘制
 */
open class ColorsText : AppCompatEditText {
    companion object {
        const val TAG = "ColorsText_DEBUG"
    }
    private val colorLock = Any()
    
    // 代码高亮颜色数组
    private var codeColors: IntArray? = null
    
    // 滑动组件
    private var scrollView: View? = null
    
    // 最大行号：根据\n数量得到
    private var mMaxLineNumber = 0
    
    // 行号内边距
    private var mNumberPadding = 0
    private var mTextPadding = 0
    private var mLineNumberBgStrokeWidth = 0
    
    // 颜色配置
    private var defaultTextColor = 0xffffffff.toInt()
    private var lineNumberColor = 0x99ffffff.toInt()
    private var lineNumberBackgroundColor = 0x99ffffff.toInt()
    private var lineNumberSplitColor = 0x99ffffff.toInt()
    private var lineNumberSplitWidth = 1
    private var cursorColor = 0xffffffff.toInt()
    private var selectBackgroundColor = 0x33ffffff.toInt()
    
    // 范围绘制标志
    private var rangDraw = false
    private var lastLineRang: IntArray? = null
    
    // 语法检查
    protected var check: GrammarCheck.Check = object : GrammarCheck.Check {
        override fun publicTest(msg: String) {}
        override fun getError(): GrammarCheck.ShowError? = null
        override fun prepare(complete: Boolean) {}
        override fun setPaint(s: String, fontWidths: Float, mPaint: Paint) {}
        override fun nextLine(xOffset: Float, lbaseline: Float) {}
    }
    
    // 检查一次间隔
    private val delay = TimeDelayer(200)
    
    // 错误定位
    private var errorShow: GrammarCheck.ShowError? = null
    private var errorShowSafe: GrammarCheck.ShowError? = null
    
    // JSON路径
    var jsonPath: String? = null
    
    // private val text = StringBuilderMemory() // 移除这个错误的属性
    private var textSize = 0f
    private var lineNumMap: Map<Int, String>? = null
    
    // 固定尺寸
    protected var fixedHeight = 0
    protected var fixedWidth = 0
    
    // 行数缓存
    private var lineCountCache = 0
    private var visualFirstLine = 0
    private var visualLastLine = 0
    private var mlineCountCache = 0
    
    /**
     * 设置错误显示
     */
    fun setErrorShow(errorShow: GrammarCheck.ShowError?) {
        this.errorShow = errorShow
    }
    
    /**
     * 获取错误显示
     */
    fun getErrorShow(): GrammarCheck.ShowError? {
        return errorShow
    }
    
    /**
     * 设置行号颜色
     */
    fun setLineNumberColor(lineNumberColor: Int) {
        this.lineNumberColor = lineNumberColor
    }
    
    /**
     * 设置行号背景颜色
     */
    fun setLineNumberBackgroundColor(lineNumberBackgroundColor: Int) {
        this.lineNumberBackgroundColor = lineNumberBackgroundColor
    }
    
    /**
     * 设置行号分割线颜色
     */
    fun setLineNumberSplitColor(lineNumberSplitColor: Int) {
        this.lineNumberSplitColor = lineNumberSplitColor
    }
    
    /**
     * 设置选择背景颜色
     */
    fun setSelectBackgroundColor(selectBackgroundColor: Int) {
        this.selectBackgroundColor = selectBackgroundColor
    }
    
    /**
     * 设置默认文本颜色
     */
    fun setDefaultTextColor(defaultTextColor: Int) {
        this.defaultTextColor = defaultTextColor
    }
    
    /**
     * 移除范围绘制
     */
    fun removeRangDraw() {
        rangDraw = false
    }
    
    /**
     * 添加范围绘制
     */
    fun addRangDraw() {
        rangDraw = true
    }
    
    constructor(context: Context) : super(context) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    
    /**
     * 设置光标颜色
     */
    fun setCursorColor(cursorColor: Int) {
        this.cursorColor = cursorColor
    }
    
    private fun init() {
        mNumberPadding = DpiUtils.dip2px(context, 5f)
        mTextPadding = DpiUtils.dip2px(context, 4f)
        mLineNumberBgStrokeWidth = DpiUtils.dip2px(context, 2f)
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams = params
        gravity = Gravity.START
        
        // 设置背景透明
        setBackgroundColor(Color.TRANSPARENT)
        
        // 设置字体大小
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        
        // 设置字体颜色(透明是为了兼容不能反射绘制光标以及选择文字背景的情况)
        setTextColor(Color.TRANSPARENT)
        setTypeface(Typeface.MONOSPACE)
        setPadding(
            0,
            DpiUtils.dip2px(context, 2f),
            DpiUtils.dip2px(context, 8f),
            DpiUtils.dip2px(context, 48f)
        )
        
        // 禁用水平滚动，启用自动换行
        setHorizontallyScrolling(false)
    }
    
    /**
     * 生成每行文字对应的行号，如果行首为换行符则需要显示行号
     */
    fun getLineNumbers(): Map<Int, String> {
        if (lineNumMap != null && lineCount == mlineCountCache) {
            return lineNumMap!!
        } else {
            mlineCountCache = lineCountCache
            val maps = HashMap<Int, String>()
            val layout = layout
            if (layout == null) {
                return maps
            }
            var lineNumber = 1
            maps[0] = lineNumber.toString()
            val lineCount = lineCount
            mMaxLineNumber = 1
            
            val currentText = text
            for (i in 1 until lineCount) {
                val charPos = layout.getLineStart(i)
                // 添加安全检查，确保charPos > 0且小于文本长度
                // Directly access Editable which is a CharSequence, much more efficient
                if (charPos > 0 && !currentText.isNullOrEmpty() && charPos - 1 < currentText.length && currentText[charPos - 1] == '\n') {
                    lineNumber++
                    maps[i] = lineNumber.toString()
                    mMaxLineNumber = lineNumber
                }
            }
            lineNumMap = maps
            return maps
        }
    }
    
    /**
     * 获取可视范围的文字首行与尾行
     */
    fun getLineRangeForDraw(rect: Rect, ret: IntArray) {
        if (rangDraw) {
            ret[0] = lastLineRang?.get(0) ?: 0
            ret[1] = lastLineRang?.get(1) ?: 0
        } else {
            val layout = layout
            val top = rect.top.coerceAtLeast(0)
            val bottom = (layout.getLineTop(layout.lineCount)).coerceAtMost(rect.bottom)
            if (top >= bottom) {
                ret[0] = -1
                ret[1] = -1
                return
            }
            ret[0] = layout.getLineForVertical(top)
            ret[1] = layout.getLineForVertical(bottom)
            lastLineRang = ret
        }
    }
    
    /**
     * 计算可视范围文字的首字位置
     */
    fun getLineFirstCharPosForDraw(widths: FloatArray): FloatArray {
        val max = (viewScrollX - paddingLeft).toFloat()
        var count = 0
        var size = 0f
        for (i in widths.indices) {
            if (size + widths[i] >= max) {
                break
            }
            size += widths[i]
            count++
        }
        return floatArrayOf(count.toFloat(), size)
    }
    
    private var getUpdatedHighlightPathMethod: Method? = null
    
    /**
     * 获取绘制选择文字背景的Path路径
     */
    @SuppressLint("SoonBlockedPrivateApi")
    @Throws(Exception::class)
    fun getUpdatedHighlightPath(): Path? {
        if (getUpdatedHighlightPathMethod == null) {
            getUpdatedHighlightPathMethod = TextView::class.java
                .getDeclaredMethod("getUpdatedHighlightPath")
            getUpdatedHighlightPathMethod!!.isAccessible = true
        }
        return getUpdatedHighlightPathMethod!!.invoke(this) as Path?
    }
    
    private var mHighlightPaintField: Field? = null
    
    /**
     * 获取绘制选择文字背景的Paint画笔
     */
    @Throws(Exception::class)
    fun getHighlightPaint(): Paint {
        if (mHighlightPaintField == null) {
            mHighlightPaintField = TextView::class.java
                .getDeclaredField("mHighlightPaint")
            mHighlightPaintField!!.isAccessible = true
        }
        return mHighlightPaintField!!.get(this) as Paint
    }
    
    private var mEditorField: Field? = null
    
    /**
     * 获取用于编辑的Editor
     */
    @Throws(Exception::class)
    fun getEditor(): Any {
        if (mEditorField == null) {
            mEditorField = TextView::class.java.getDeclaredField("mEditor")
            mEditorField!!.isAccessible = true
        }
        return mEditorField!!.get(this)
    }
    
    /**
     * 绘制光标以及文字选择背景
     */
    @Throws(Exception::class)
    fun drawCursorAndSelectPath(canvas: Canvas) {
        val selectionStart = selectionStart
        val selectionEnd = selectionEnd
        val highlight = getUpdatedHighlightPath()
        val mHighlightPaint = getHighlightPaint()
        
        // 设置选择文字背景颜色
        if (selectBackgroundColor != 0) {
            mHighlightPaint.color = selectBackgroundColor
        }
        canvas.save()
        
        // getCompoundPaddingLeft获取真正的左内边距，getExtendedPaddingTop获取真正的上外边距
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        try {
            if (highlight != null) {
                if (selectionEnd == selectionStart) {
                    // 绘制光标
                    val paint = paint
                    paint.color = cursorColor
                    canvas.drawRect(cursorRect, paint)
                } else {
                    // 绘制选择文字阴影
                    canvas.drawPath(highlight, mHighlightPaint)
                }
            }
        } finally {
            canvas.restore()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        val paint = paint
        
        try {
            // 优化速度绘制光标以及选择文字背景
            drawCursorAndSelectPath(canvas)
        } catch (e: Exception) {
            // 反射调用失败，使用默认的方法绘制背景，会绘制文字
            super.onDraw(canvas)
        }
        
        // 画文字
        canvas.save()
        canvas.translate(0f, extendedPaddingTop.toFloat())
        
        synchronized(colorLock) {
            drawText(canvas)
        }
        
        canvas.restore()
        
        // 绘制分割线
        paint.strokeWidth = lineNumberSplitWidth.toFloat()
        paint.color = lineNumberSplitColor
        canvas.drawLine(
            (paddingLeft - mTextPadding).toFloat(),
            0f,
            (paddingLeft - mTextPadding).toFloat(),
            height.toFloat(),
            paint
        )
        
        // 根据行号计算左边距padding
        val max = mMaxLineNumber.toString()
        val lineNumberSize = paint.measureText(max) + mNumberPadding + mNumberPadding + mTextPadding
        
        if (paddingLeft != lineNumberSize.toInt()) {
            setPadding(lineNumberSize.toInt(), paddingTop, paddingRight, paddingBottom)
            invalidate()
        }
    }
    
    /**
     * 获取选择行，如果多于一行，返回-1
     */
    fun getSelectLine(): Int {
        val layout = layout
        return if (selectionStart != selectionEnd) {
            -1
        } else layout.getLineForOffset(selectionStart)
    }
    
    /**
     * 绘制文本着色
     */
    private fun drawText(canvas: Canvas) {
        val lineNumbers = getLineNumbers()
        val layout = layout
        val selectLine = getSelectLine()
        val range = IntArray(4)
        val currentText = text ?: return
        run {
            // 计算需要绘制的行号所需要的范围
            val clipLeft = 0
            val clipTop = if (scrollView!!.scrollY == 0) 0
                else extendedPaddingTop + scrollView!!.scrollY - scrollView!!.paddingTop
            val clipRight = width
            val clipBottom = clipTop + scrollView!!.height
            val rect = Rect(clipLeft, clipTop, clipRight, clipBottom)
            getLineRangeForDraw(rect, range)
        }
        var firstLine = range[0]
        var lastLine = range[1]
        
        if (rangDraw) {
            firstLine = 0.coerceAtLeast(firstLine - (lastLine - firstLine))
            lastLine += lastLine - firstLine
        }
        
        visualFirstLine = firstLine
        visualLastLine = lastLine
        
        if (firstLine < 0) {
            return
        }
        
        var previousLineBottom = layout.getLineTop(firstLine)
        var previousLineEnd = layout.getLineStart(firstLine)
        val lineCount = lineCount
        val paint = paint
        
        if (delay.isExceed()) {
            // 语法检查
            Thread {
                try {
                    check.publicTest(currentText.toString())
                } catch (e: Exception) {
                }
                errorShowSafe = check.getError()
            }.start()
            
            // 层数
            if (check is GrammarCheck.JSON) {
                Thread {
                    try {
                        jsonPath = (check as GrammarCheck.JSON).getSurface(currentText.toString().subSequence(0, selectionStart))
                    } catch (e: Exception) {
                    }
                }.start()
            }
        }
        
        check.prepare(false)
        
        for (lineNum in firstLine..lastLine.coerceAtMost(lineCount - 1)) {
            val start = previousLineEnd
            previousLineEnd = layout.getLineStart(lineNum + 1)
            
            val end = layout.getLineVisibleEnd(lineNum)
            val ltop = previousLineBottom
            val lbottom = layout.getLineTop(lineNum + 1)
            previousLineBottom = lbottom
            val lbaseline = lbottom - layout.getLineDescent(lineNum)
            val left = paddingLeft
            
            // 绘制选择行背景
            if (lineNum == selectLine) {
                paint.color = lineNumberBackgroundColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = mLineNumberBgStrokeWidth.toFloat()
                canvas.drawRect(
                    (paddingLeft - mLineNumberBgStrokeWidth).toFloat(),
                    ltop.toFloat(),
                    (right - paddingRight + mLineNumberBgStrokeWidth).toFloat(),
                    lbottom.toFloat(),
                    paint
                )
                paint.style = Paint.Style.FILL
            }
            
            // 绘制行号
            val lineNumberText = lineNumbers[lineNum]
            if (lineNumberText != null) {
                paint.color = lineNumberColor
                canvas.drawText(
                    lineNumberText,
                    0f,
                    lbaseline.toFloat(),
                    paint
                )
            }
            
            val textLength = currentText.length
            
            // 绘制缩进指引线
            if (start < textLength) {
                var indentCount = 0
                var charPos = start
                // 计算该行的缩进级别（每4个空格为一个级别）
                while (charPos < end && charPos < textLength) {
                    val char = currentText[charPos]
                    if (char == ' ') {
                        indentCount++
                        charPos++
                    } else {
                        break
                    }
                }
                
                // 为每个缩进级别绘制一条虚线
                val indentLevels = indentCount / 4
                if (indentLevels > 0) {
                    val spaceWidth = paint.measureText(" ")
                    paint.color = 0x80FFFFFF.toInt() // 增强透明度，使虚线更明显
                    paint.strokeWidth = 2f // 增加线宽
                    paint.style = Paint.Style.STROKE
                    
                    // 使用 PathEffect 创建虚线效果
                    val dashPath = Path()
                    for (level in 1..indentLevels) {
                        val x = left + (level * 4 * spaceWidth)
                        
                        // 绘制虚线：更长的线段，更短的间隔
                        var y = ltop.toFloat()
                        val dashLength = 8f // 增加虚线段长度
                        val gapLength = 3f // 减少间隔
                        
                        while (y < lbottom) {
                            canvas.drawLine(x, y, x, (y + dashLength).coerceAtMost(lbottom.toFloat()), paint)
                            y += dashLength + gapLength
                        }
                    }
                    
                    paint.style = Paint.Style.FILL
                }
            }
            
            // 绘制文字
            if (start < textLength) {
                // 计算需要绘制的文字位置
                // 获取该行所有文字宽度
                val widths = FloatArray(end - start + 1)
                paint.getTextWidths(currentText, start, end, widths)
                
                // 计算获取看到的文字第一个位置，和对应的偏移x
                val firstNeedDrawPos = getLineFirstCharPosForDraw(widths)
                val firstPos = firstNeedDrawPos[0].toInt()
                var offsetX = firstNeedDrawPos[1]
                val maxOffX = viewScrollX + visibleWidth
                
                // 移动行数
                check.nextLine(left + offsetX, lbaseline.toFloat())
                
                // 文字着色
                var i = start + firstPos
                while (i < end && i < textLength) {
                    if (offsetX > maxOffX) {
                        break
                    }
                    val color = getCodeColor(i)
                    run {
                        var fontWidths = widths[i - start]
                        var fontCount = 1
                        for (j in i + 1 until end.coerceAtMost(textLength)) {
                            if (color == getCodeColor(j)) {
                                fontCount++
                                fontWidths += widths[j - start]
                            } else {
                                break
                            }
                        }
                        val finalColor = if (color == 0) defaultTextColor else color
                        paint.color = finalColor
                        
                        // The subsequence's toString() is acceptable as it's a small string.
                        check.setPaint(currentText.subSequence(i, i + fontCount).toString(), fontWidths, paint)
                        errorShow = errorShowSafe
                        
                        if (errorShow != null) {
                            if (i <= errorShow!!.point && errorShow!!.point <= i + fontCount) {
                                paint.isUnderlineText = true
                                paint.color = Color.RED
                            } else {
                                paint.isUnderlineText = false
                            }
                        } else {
                            paint.isUnderlineText = false
                        }
                        
                        // Use the overload that accepts a CharSequence
                        canvas.drawText(
                            currentText,
                            i,
                            i + fontCount,
                            left + offsetX,
                            lbaseline.toFloat(),
                            paint
                        )
                        
                        i += fontCount
                        offsetX += fontWidths
                    }
                }
            }
        }
    }
    
    /**
     * 获取代码颜色
     */
    private fun getCodeColor(i: Int): Int {
        return if (codeColors != null && i < codeColors!!.size) {
            codeColors!![i]
        } else 0
    }
    
    /**
     * 获取代码颜色数组
     */
    fun getCodeColors(): IntArray {
        val textLength = length()
        if (codeColors == null) {
            codeColors = IntArray(textLength)
        }
        
        // 如果文字长度大于颜色长度，生成新的颜色数组
        if (textLength >= codeColors!!.size) {
            val newColors = IntArray(textLength + 500)
            for (i in codeColors!!.indices) {
                newColors[i] = codeColors!![i]
            }
            codeColors = newColors
        }
        return codeColors!!
    }
    
    /**
     * 获取视图滚动X位置
     */
    val viewScrollX: Int
        get() = scrollView!!.scrollX
    
    /**
     * 获取视图滚动Y位置
     */
    val viewScrollY: Int
        get() = scrollView!!.scrollY
    
    /**
     * 获取滚动视图
     */
    fun getScrollView(): View {
        return scrollView ?: this
    }
    
    /**
     * 设置滚动视图
     */
    fun setScrollView(scrollView: View?) {
        this.scrollView = scrollView
    }
    
    /**
     * 获取可见宽度
     */
    val visibleWidth: Int
        get() = width.coerceAtMost(scrollView!!.width)
    
    /**
     * 获取可见高度
     */
    val visibleHeight: Int
        get() = height.coerceAtMost(scrollView!!.height)
    
    /**
     * 获取光标矩形
     */
    val cursorRect: Rect
        get() {
            val layout = layout
            val offset = selectionStart
            val line = layout.getLineForOffset(offset)
            val top = layout.getLineTop(line)
            val bottom = layout.getLineTop(line + 1)
            val horizontal = layout.getSecondaryHorizontal(offset)
            val left = horizontal.toInt()
            return Rect(left, top, left + DpiUtils.dip2px(context, 1f), bottom)
        }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        return result
    }
    
    override fun setTextSize(size: Float) {
        super.setTextSize(size)
    }
    
    override fun getTextSize(): Float {
        return super.getTextSize()
    }
    
    // 原EditText不合理的onMeasure在这里重新写
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specSizeW = getParentWidth()  // 父容器宽度（屏幕宽度）
        val specSizeH = getParentHeight() // 父容器高度（屏幕高度）
        
        // 如果父容器尺寸还未初始化，使用默认测量
        if (specSizeW <= 0 || specSizeH <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        
        // 计算内容高度（行数 * 行高）+ padding，确保可以滚动到底部
        fixedHeight = (lineCount + 1) * lineHeight + paddingTop + paddingBottom
        // 固定宽度为父容器宽度，禁用横向滚动
        fixedWidth = specSizeW
        fixedHeight = fixedHeight.coerceAtLeast(specSizeH)
        
        // 初始化可视行范围
        if (visualFirstLine == 0 && visualLastLine == 0) {
            visualFirstLine = 0
            visualLastLine = lineCount
        }
        
        setMeasuredDimension(fixedWidth, fixedHeight)
    }
    
    /**
     * 获取父视图宽度
     */
    protected open fun getParentWidth(): Int {
        return 0
    }
    
    /**
     * 获取父视图高度
     */
    protected open fun getParentHeight(): Int {
        return 0
    }
    
    /**
     * 获取行最大宽度
     */
    private fun getLineMaxWidth(layout: Layout?, startLine: Int, endLine: Int): Int {
        if (layout == null) return 2000
        val n = endLine
        
        var max = 0f
        try {
            for (i in startLine until n) {
                max = max.coerceAtLeast(layout.getLineWidth(i))
            }
        } catch (e: Exception) {
            return 0
        }
        return Math.ceil(max.toDouble()).toInt()
    }
    
    override fun getLineCount(): Int {
        if (layout != null) {
            lineCountCache = super.getLineCount()
        }
        return lineCountCache
    }
} 