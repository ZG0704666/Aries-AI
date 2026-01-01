package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.Scroller
import androidx.customview.view.AbsSavedState as BaseSavedState
import com.ai.assistance.operit.util.AppLogger

/**
 * 具有弹性效果的全方向ScrollView,参考ScrollView与HorizontalScrollView源码
 */
open class HVScrollView : FrameLayout {
    companion object {
        const val TAG = "HVScrollView_DEBUG"
        const val ANIMATED_SCROLL_GAP = 250
        const val MAX_SCROLL_FACTOR = 0.5f
        private const val INVALID_POINTER = -1
    }

    private var mLastScroll: Long = 0
    private val mTempRect = Rect()
    private lateinit var mScroller: Scroller

    /**
     * 标志我们正在移动焦点。这样观察此ScrollView之外发起的焦点变化的代码
     * 知道它不必做任何事情。
     */
    private var mScrollViewMovedFocus = false

    /**
     * 最后一个运动事件的位置。
     */
    private var mLastMotionY = 0f
    private var mLastMotionX = 0f

    /**
     * 当布局已更改但遍历尚未完成时为true。
     * 理想情况下，视图层次结构会为我们跟踪这一点。
     */
    private var mIsLayoutDirty = true

    /**
     * 在布局脏的情况下，子视图请求焦点时要给予焦点的子视图。
     * 这可以防止在请求焦点之前尚未布局子视图时滚动出错。
     */
    private var mChildToScrollTo: View? = null

    /**
     * 如果用户当前正在拖动此ScrollView，则为true。这与"正在抛掷"不同，
     * 可以通过mScroller.isFinished()检查（抛掷在用户抬起手指时开始）。
     */
    private var mIsBeingDragged = false

    /**
     * 触摸滚动期间确定速度
     */
    private var mVelocityTracker: VelocityTracker? = null

    /**
     * 当设置为true时，滚动视图测量其子视图以填充当前可见区域。
     */
    private var mFillViewport = false

    /**
     * 箭头滚动是否有动画。
     */
    private var mSmoothScrollingEnabled = true

    private var mTouchSlop = 0
    private var mMinimumVelocity = 0
    private var mMaximumVelocity = 0

    /**
     * 活动指针的ID。这用于在使用多个指针时保持一致性
     * 拖动/抛掷期间。
     */
    private var mActivePointerId = INVALID_POINTER

    private var mFlingEnabled = true
    
    /**
     * scrollview内容与属性记录
     */
    private var inner: View? = null
    private val normal = Rect()
    
    /**
     * 是否可以在子视图外部滚动
     */
    private var scrollableOutsideTouch = false
    
    /**
     * 是否有弹性效果
     */
    private var flexible = true
    
    private var lastEvenTime: Long = 0

    constructor(context: Context) : super(context) {
        initScrollView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 16842880) {
        initScrollView()
    }

    private fun initScrollView() {
        mScroller = Scroller(context)
        isFocusable = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        setWillNotDraw(false)
        val configuration = ViewConfiguration.get(context)
        mTouchSlop = configuration.scaledTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity * 2
    }
    
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        AppLogger.d(TAG, "requestDisallowInterceptTouchEvent(disallow: $disallowIntercept)")
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * 设置scroller是否可以滑动内容当触屏事件在chileview之外 default:false
     */
    fun setScrollableOutsideChile(b: Boolean) {
        scrollableOutsideTouch = b
    }
    
    /**
     * 设置是否可有弹性效果
     */
    fun setFlexible(b: Boolean) {
        flexible = b
    }
    
    override fun getTopFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = verticalFadingEdgeLength
        return if (scrollY < length) {
            scrollY.toFloat() / length
        } else 1.0f
    }

    override fun getLeftFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = horizontalFadingEdgeLength
        return if (scrollX < length) {
            scrollX.toFloat() / length
        } else 1.0f
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = horizontalFadingEdgeLength
        val rightEdge = width - paddingRight
        val span = getChildAt(0).right - scrollX - rightEdge
        return if (span < length) {
            span.toFloat() / length
        } else 1.0f
    }

    override fun getBottomFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = verticalFadingEdgeLength
        val bottomEdge = height - paddingBottom
        val span = getChildAt(0).bottom - scrollY - bottomEdge
        return if (span < length) {
            span.toFloat() / length
        } else 1.0f
    }

    /**
     * @return 此滚动视图将响应箭头事件而滚动的最大量。
     */
    fun getMaxScrollAmountV(): Int {
        return (MAX_SCROLL_FACTOR * (bottom - top)).toInt()
    }

    fun getMaxScrollAmountH(): Int {
        return (MAX_SCROLL_FACTOR * (right - left)).toInt()
    }
    
    /**
     * @return 如果此ScrollView可以滚动，则返回true
     */
    private fun canScrollV(): Boolean {
        val child = getChildAt(0)
        if (child != null) {
            val childHeight = child.height
            return height < childHeight + paddingTop + paddingBottom
        }
        return false
    }

    private fun canScrollH(): Boolean {
        val child = getChildAt(0)
        if (child != null) {
            val childWidth = child.width
            return width < childWidth + paddingLeft + paddingRight
        }
        return false
    }

    /**
     * 指示此ScrollView的内容是否拉伸以填充视口。
     *
     * @return 如果内容填充视口，则为true，否则为false。
     */
    fun isFillViewport(): Boolean {
        return mFillViewport
    }

    /**
     * 指示此ScrollView是否应将其内容高度拉伸以填充视口。
     *
     * @param fillViewport True表示将内容的高度拉伸到视口的边界，false表示不拉伸。
     */
    fun setFillViewport(fillViewport: Boolean) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport
            requestLayout()
        }
    }

    /**
     * @return 箭头滚动是否会为其过渡设置动画。
     */
    fun isSmoothScrollingEnabled(): Boolean {
        return mSmoothScrollingEnabled
    }

    /**
     * 设置箭头滚动是否会为其过渡设置动画。
     *
     * @param smoothScrollingEnabled 箭头滚动是否会为其过渡设置动画
     */
    fun setSmoothScrollingEnabled(smoothScrollingEnabled: Boolean) {
        mSmoothScrollingEnabled = smoothScrollingEnabled
    }
    
    fun isFlingEnabled(): Boolean {
        return mFlingEnabled
    }

    fun setFlingEnabled(flingEnabled: Boolean) {
        this.mFlingEnabled = flingEnabled
    }
    
    // 是否需要开启动画
    fun isNeedAnimation(): Boolean {
        return !normal.isEmpty
    }
    
    // 是否需要移动布局
    fun isNeedMove(): Boolean {
        if (inner != null) {
            val offsetX = inner!!.measuredWidth - width
            val scrollX = scrollX
            if (scrollX == 0 || scrollX == offsetX) {
                return true
            }

            val offsetY = inner!!.measuredHeight - height
            val scrollY = scrollY
            return scrollY == 0 || scrollY == offsetY
        }
        return false
    }
    
    // 开启动画移动
    fun animation() {
        // 开启移动动画
        val ta = TranslateAnimation(0f, -inner!!.left.toFloat(), 0f, -inner!!.top.toFloat())
        ta.duration = 200
        inner!!.startAnimation(ta)
        // 设置回到正常的布局位置
        Handler(Looper.getMainLooper()).postDelayed({
            inner!!.clearAnimation()
            inner!!.layout(normal.left, normal.top, normal.right, normal.bottom)
            normal.setEmpty()
        }, 200)
    }
    
    override fun addView(child: View) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }
        super.addView(child)
    }

    override fun addView(child: View, index: Int) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }
        super.addView(child, index)
    }

    override fun addView(child: View, params: ViewGroup.LayoutParams) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }
        super.addView(child, params)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }
        super.addView(child, index, params)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (!mFillViewport) {
            return
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        if (heightMode == MeasureSpec.UNSPECIFIED && widthMode == MeasureSpec.UNSPECIFIED) {
            return
        }

        if (childCount > 0) {
            val child = getChildAt(0)
            var height = measuredHeight
            var width = measuredWidth
            if (child.measuredHeight < height || child.measuredWidth < width) {
                width -= paddingLeft
                width -= paddingRight
                val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)

                height -= paddingTop
                height -= paddingBottom
                val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 0) {
            inner = getChildAt(0)
        }
    }
    
    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        val lp = child.layoutParams as MarginLayoutParams
        val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
            lp.leftMargin + lp.rightMargin,
            MeasureSpec.UNSPECIFIED
        )
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
            lp.topMargin + lp.bottomMargin,
            MeasureSpec.UNSPECIFIED
        )
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }
    
    private fun inChild(x: Int, y: Int): Boolean {
        if (childCount > 0) {
            val scrollX = scrollX
            val scrollY = scrollY
            val child = getChildAt(0)
            return !(y < child.top - scrollY || y >= child.bottom - scrollY
                    || x < child.left - scrollX || x >= child.right - scrollX)
        }
        return false
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        AppLogger.d(TAG, "onInterceptTouchEvent: received action=${ev.action}")
        // 这个方法只确定我们是否要拦截动作。
        // 如果我们返回true，onMotionEvent将被调用，我们在那里进行实际的滚动。

        // 快捷方式最常见的情况：用户处于拖动状态，他正在移动手指。
        // 我们想要拦截这个动作。
        val action = ev.action
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) {
            return true
        }

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_MOVE -> {
                // mIsBeingDragged == false，否则快捷方式会捕获它。
                // 检查用户是否已经从原始的向下触摸移动了足够远。
                val activePointerId = mActivePointerId
                if (activePointerId != INVALID_POINTER) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val y = ev.getY(pointerIndex)
                        val yDiff = Math.abs(y - mLastMotionY).toInt()
                        if (yDiff > mTouchSlop) {
                            mIsBeingDragged = true
                        }

                        val x = ev.getX(pointerIndex)
                        val xDiff = Math.abs(x - mLastMotionX).toInt()
                        if (xDiff > mTouchSlop) {
                            mIsBeingDragged = true
                        }
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                if (!inChild(x.toInt(), y.toInt())) {
                    mIsBeingDragged = false
                } else {
                    // 强制父视图不要拦截此手势序列
                    parent?.requestDisallowInterceptTouchEvent(true)
                    // 记住向下触摸的位置。ACTION_DOWN总是指向指针索引0。
                    mLastMotionY = y
                    mLastMotionX = x
                    mActivePointerId = ev.getPointerId(0)

                    // 如果正在抛掷并且用户触摸屏幕，启动拖动；否则不要。
                    // mScroller.isFinished在抛掷时应该为false。
                    mIsBeingDragged = !mScroller.isFinished
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // 释放拖动
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        // 我们只想在拖动模式下拦截动作事件。
        AppLogger.d(TAG, "onInterceptTouchEvent: returning mIsBeingDragged=$mIsBeingDragged for action=${ev.action}")
        return mIsBeingDragged
    }
    
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && ev.edgeFlags != 0) {
            // 不要立即处理边缘触摸 - 它们可能实际上属于我们的一个后代。
            return false
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        val action = ev.action

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y

                // Crucial logic from Java reference: assignment within the condition
                mIsBeingDragged = inChild(x.toInt(), y.toInt())
                if (!mIsBeingDragged && !scrollableOutsideTouch) {
                    return false
                }

                // 阻止测试人员暴力测试
                if (System.currentTimeMillis() - lastEvenTime < 200) {
                    ev.action = MotionEvent.ACTION_CANCEL
                }
                lastEvenTime = System.currentTimeMillis()
                
                // 如果正在抛掷并且用户触摸，停止抛掷。
                // isFinished在抛掷时将为false。
                if (!mScroller.isFinished) {
                    mScroller.abortAnimation()
                }

                // 记住动作事件开始的位置
                mLastMotionY = y
                mLastMotionX = x
                mActivePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mIsBeingDragged || scrollableOutsideTouch) {
                    // 滚动以跟随动作事件
                    val activePointerIndex = ev.findPointerIndex(mActivePointerId)
                    val y = ev.getY(activePointerIndex)
                    val deltaY = (mLastMotionY - y).toInt()
                    mLastMotionY = y

                    val x = ev.getX(activePointerIndex)
                    val deltaX = (mLastMotionX - x).toInt()
                    mLastMotionX = x
                    
                    // 全方向滚动
                    scrollBy(deltaX, deltaY)
                    
                    // 当滚动到边界时就不会再滚动，这时移动布局
                    if (isNeedMove() && flexible) {
                        if (normal.isEmpty) {
                            // 保存正常的布局属性
                            normal.set(inner!!.left, inner!!.top, inner!!.right, inner!!.bottom)
                        }
                        // 移动布局
                        inner!!.layout(
                            inner!!.left - deltaX / 2,
                            inner!!.top - deltaY / 2,
                            inner!!.right - deltaX / 2,
                            inner!!.bottom - deltaY / 2
                        )
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mIsBeingDragged || scrollableOutsideTouch) {
                    if (mFlingEnabled) {
                        val velocityTracker = mVelocityTracker
                        velocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                        val initialVelocitx = velocityTracker.getXVelocity(mActivePointerId).toInt()
                        val initialVelocity = velocityTracker.getYVelocity(mActivePointerId).toInt()

                        if (childCount > 0) {
                            if (Math.abs(initialVelocitx) > mMinimumVelocity
                                || Math.abs(initialVelocity) > mMinimumVelocity
                            ) {
                                fling(-initialVelocitx, -initialVelocity)
                            }
                        }
                    }
                    if (isNeedAnimation()) {
                        animation()
                    }
                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false

                    if (mVelocityTracker != null) {
                        mVelocityTracker!!.recycle()
                        mVelocityTracker = null
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged && childCount > 0) {
                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false
                    if (mVelocityTracker != null) {
                        mVelocityTracker!!.recycle()
                        mVelocityTracker = null
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }
        return true
    }
    
    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = (ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // 这是我们的活动指针上升。选择一个新的活动指针并相应地调整。
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mLastMotionX = ev.getX(newPointerIndex)
            mLastMotionY = ev.getY(newPointerIndex)
            mActivePointerId = ev.getPointerId(newPointerIndex)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }
    }
    
    /**
     * 抛掷滚动视图
     */
    fun fling(velocityX: Int, velocityY: Int) {
        if (childCount > 0) {
            val width = width - paddingRight - paddingLeft
            val right = getChildAt(0).width

            val height = height - paddingBottom - paddingTop
            val bottom = getChildAt(0).height
            
            mScroller.fling(
                scrollX, scrollY, velocityX, velocityY, 0,
                Math.max(0, right - width), 0, Math.max(0, bottom - height)
            )
            
            invalidate()
        }
    }
    
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            // 这是在绘制时由ViewGroup调用的。我们不想在这一点上重新显示滚动条，
            // 这是scrollTo将做的，所以我们在这里复制大部分scrollTo。
            //
            // 在绘图内部调用onScrollChanged有点奇怪。
            //
            // 它是，除非你记得computeScroll()用于动画滚动。
            // 所以除非我们想推迟onScrollChanged()直到动画滚动结束，
            // 我们真的没有选择。
            //
            // 我同意。另一种选择，我认为会更糟，是发布一些东西并告诉子类稍后。
            // 这很糟糕，因为会有一个窗口，其中mScrollX/Y与应用程序认为的不同。
            //
            val x = mScroller.currX
            val y = mScroller.currY

            if (childCount > 0) {
                val child = getChildAt(0)
                val clampedX = clamp(x, width - paddingRight - paddingLeft, child.width)
                val clampedY = clamp(y, height - paddingBottom - paddingTop, child.height)
                super.scrollTo(clampedX, clampedY)
            }
            
            awakenScrollBars()

            // 继续绘制，直到动画完成。
            postInvalidate()
        }
    }
    
    /**
     * 像{@link View#scrollBy}一样，但平滑滚动而不是立即滚动。
     */
    fun smoothScrollBy(dx: Int, dy: Int) {
        if (childCount == 0) {
            // 没有什么可做的。
            return
        }
        val duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll
        if (duration > ANIMATED_SCROLL_GAP) {
            val height = height - paddingBottom - paddingTop
            val bottom = getChildAt(0).height
            val maxY = Math.max(0, bottom - height)
            val scrollY = scrollY
            val newDy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY

            val width = width - paddingRight - paddingLeft
            val right = getChildAt(0).width
            val maxX = Math.max(0, right - width)
            val scrollX = scrollX
            val newDx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX

            mScroller.startScroll(scrollX, scrollY, newDx, newDy)
            invalidate()
        } else {
            if (!mScroller.isFinished) {
                mScroller.abortAnimation()
            }
            scrollBy(dx, dy)
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis()
    }
    
    /**
     * 像{@link #scrollTo}一样，但平滑滚动而不是立即滚动。
     */
    fun smoothScrollTo(x: Int, y: Int) {
        smoothScrollBy(x - scrollX, y - scrollY)
    }
    
    /**
     * 将n限制在我和子之间
     */
    private fun clamp(n: Int, my: Int, child: Int): Int {
        if (my >= child || n < 0) {
            return 0
        }
        return if (my + n > child) {
            child - my
        } else n
    }
    
    override fun scrollTo(x: Int, y: Int) {
        // 我们依赖于View.scrollBy调用scrollTo的事实。
        if (childCount > 0) {
            val child = getChildAt(0)
            val clampedX = clamp(x, width - paddingRight - paddingLeft, child.width)
            val clampedY = clamp(y, height - paddingBottom - paddingTop, child.height)
            if (clampedX != scrollX || clampedY != scrollY) {
                super.scrollTo(clampedX, clampedY)
            }
        }
    }
    
    /**
     * 处理箭头键事件。这个方法会处理箭头键移动焦点到其他视图，
     * 但如果焦点移动没有处理，则会尝试滚动视图本身。
     *
     * @param keyCode 按下的键的键码。
     * @param event 键事件。
     * @return 如果事件已处理，则为true，否则为false。
     */
    fun executeKeyEvent(event: KeyEvent): Boolean {
        mTempRect.setEmpty()

        if (!canScrollV()) {
            if (isFocused && event.keyCode != KeyEvent.KEYCODE_BACK) {
                var currentFocused = findFocus()
                if (currentFocused === this) currentFocused = null
                val nextFocused = FocusFinder.getInstance().findNextFocus(
                    this, currentFocused,
                    View.FOCUS_DOWN
                )
                return nextFocused != null
                        && nextFocused !== this
                        && nextFocused.requestFocus(View.FOCUS_DOWN)
            }
            return false
        }

        val handled = false
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!event.isAltPressed) {
                        return arrowScrollV(View.FOCUS_UP)
                    } else {
                        return fullScrollV(View.FOCUS_UP)
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!event.isAltPressed) {
                        return arrowScrollV(View.FOCUS_DOWN)
                    } else {
                        return fullScrollV(View.FOCUS_DOWN)
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!event.isAltPressed) {
                        return arrowScrollH(View.FOCUS_LEFT)
                    } else {
                        return fullScrollH(View.FOCUS_LEFT)
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!event.isAltPressed) {
                        return arrowScrollH(View.FOCUS_RIGHT)
                    } else {
                        return fullScrollH(View.FOCUS_RIGHT)
                    }
                }
                KeyEvent.KEYCODE_SPACE -> {
                    pageScrollV(if (event.isShiftPressed) View.FOCUS_UP else View.FOCUS_DOWN)
                    return true
                }
            }
        }

        return handled
    }
    
    /**
     * 处理焦点变化事件，确保焦点视图可见
     */
    override fun requestChildFocus(child: View, focused: View) {
        if (focused != null) {
            if (!mIsLayoutDirty) {
                scrollToChild(focused)
            } else {
                // 布局尚未发生，等待布局完成后再滚动到子视图
                mChildToScrollTo = focused
            }
        }
        super.requestChildFocus(child, focused)
    }

    /**
     * 当一个子视图请求焦点时，确保它可见
     */
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        // 偏移到视图坐标
        rectangle.offset(child.left - child.scrollX, child.top - child.scrollY)

        return scrollToChildRect(rectangle, immediate)
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (direction == View.FOCUS_FORWARD) {
            val direction2 = View.FOCUS_DOWN
            val focused = findFocus()
            val focusableRect = if (focused != null && focused !== this) {
                val focusedRect = mTempRect
                if (previouslyFocusedRect != null) {
                    focusedRect.set(previouslyFocusedRect)
                } else {
                    focusedRect.setEmpty()
                    focused.getFocusedRect(focusedRect)
                    offsetDescendantRectToMyCoords(focused, focusedRect)
                }
                focusedRect
            } else {
                previouslyFocusedRect
            }
            
            val nextFocused = FocusFinder.getInstance().findNextFocusFromRect(
                this, focusableRect, direction2
            )
            return nextFocused?.requestFocus(direction2, previouslyFocusedRect) ?: false
        } else {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
        }
    }
    
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 让视图层次结构首先处理键事件
        return super.dispatchKeyEvent(event) || executeKeyEvent(event)
    }
    
    /**
     * 将视图滚动到指定的子视图
     */
    private fun scrollToChild(child: View) {
        child.getDrawingRect(mTempRect)

        // 偏移矩形，使其相对于视图坐标，而不是本地坐标
        offsetDescendantRectToMyCoords(child, mTempRect)

        val scrollDeltaV = computeScrollDeltaToGetChildRectOnScreenV(mTempRect)
        val scrollDeltaH = computeScrollDeltaToGetChildRectOnScreenH(mTempRect)

        if (scrollDeltaV != 0 || scrollDeltaH != 0) {
            scrollBy(scrollDeltaH, scrollDeltaV)
        }
    }
    
    /**
     * 如果矩形不在屏幕上，则滚动视图以使其可见
     */
    private fun scrollToChildRect(rect: Rect, immediate: Boolean): Boolean {
        val deltaV = computeScrollDeltaToGetChildRectOnScreenV(rect)
        val deltaH = computeScrollDeltaToGetChildRectOnScreenH(rect)
        val scroll = deltaV != 0 || deltaH != 0

        if (scroll) {
            if (immediate) {
                scrollBy(deltaH, deltaV)
            } else {
                smoothScrollBy(deltaH, deltaV)
            }
        }

        return scroll
    }
    
    /**
     * 计算滚动量以使矩形的边缘在屏幕上（垂直方向）
     */
    private fun computeScrollDeltaToGetChildRectOnScreenV(rect: Rect): Int {
        if (childCount == 0) return 0

        val height = height
        val screenTop = scrollY
        val screenBottom = screenTop + height

        val fadingEdge = verticalFadingEdgeLength

        // 如果矩形的顶部在屏幕上方，或者整个高度小于屏幕高度，则向上滚动
        if (rect.top < screenTop) {
            // 顶部不在屏幕上
            return rect.top - screenTop
        }

        // 如果矩形的底部在屏幕下方，则向下滚动
        if (rect.bottom > screenBottom) {
            // 底部不在屏幕上
            return rect.bottom - screenBottom
        }

        return 0
    }

    /**
     * 计算滚动量以使矩形的边缘在屏幕上（水平方向）
     */
    private fun computeScrollDeltaToGetChildRectOnScreenH(rect: Rect): Int {
        if (childCount == 0) return 0

        val width = width
        val screenLeft = scrollX
        val screenRight = screenLeft + width

        val fadingEdge = horizontalFadingEdgeLength

        // 如果矩形的左侧在屏幕左侧，或者整个宽度小于屏幕宽度，则向左滚动
        if (rect.left < screenLeft) {
            // 左侧不在屏幕上
            return rect.left - screenLeft
        }

        // 如果矩形的右侧在屏幕右侧，则向右滚动
        if (rect.right > screenRight) {
            // 右侧不在屏幕上
            return rect.right - screenRight
        }

        return 0
    }
    
    /**
     * 向上或向下滚动以使下一个焦点视图可见
     */
    private fun arrowScrollV(direction: Int): Boolean {
        var currentFocused = findFocus()
        if (currentFocused === this) currentFocused = null

        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)

        val maxJump = getMaxScrollAmountV()

        if (nextFocused != null && isWithinDeltaOfScreenV(nextFocused, maxJump, height)) {
            nextFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(nextFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreenV(mTempRect)
            doScrollY(scrollDelta)
            nextFocused.requestFocus(direction)
        } else {
            // 没有下一个焦点，或者它太远了，所以只是滚动
            var scrollDelta = maxJump
            if (direction == View.FOCUS_UP && scrollY < scrollDelta) {
                scrollDelta = scrollY
            } else if (direction == View.FOCUS_DOWN) {
                if (childCount > 0) {
                    val child = getChildAt(0)
                    val daBottom = child.bottom
                    val screenBottom = scrollY + height
                    if (daBottom - screenBottom < maxJump) {
                        scrollDelta = daBottom - screenBottom
                    }
                }
            }
            if (scrollDelta == 0) {
                return false
            }
            doScrollY(if (direction == View.FOCUS_DOWN) scrollDelta else -scrollDelta)
        }

        if (currentFocused != null && currentFocused.isFocused && isOffScreenV(currentFocused)) {
            // 当前焦点不在屏幕上，所以我们需要将焦点移动到其他地方
            // 这是为了避免用户无法看到的焦点视图
            val descendantFocusability = descendantFocusability
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            requestFocus()
            setDescendantFocusability(descendantFocusability)
        }
        return true
    }
    
    /**
     * 向左或向右滚动以使下一个焦点视图可见
     */
    private fun arrowScrollH(direction: Int): Boolean {
        var currentFocused = findFocus()
        if (currentFocused === this) currentFocused = null

        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)

        val maxJump = getMaxScrollAmountH()

        if (nextFocused != null && isWithinDeltaOfScreenH(nextFocused, maxJump, width)) {
            nextFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(nextFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreenH(mTempRect)
            doScrollX(scrollDelta)
            nextFocused.requestFocus(direction)
        } else {
            // 没有下一个焦点，或者它太远了，所以只是滚动
            var scrollDelta = maxJump
            if (direction == View.FOCUS_LEFT && scrollX < scrollDelta) {
                scrollDelta = scrollX
            } else if (direction == View.FOCUS_RIGHT) {
                if (childCount > 0) {
                    val child = getChildAt(0)
                    val daRight = child.right
                    val screenRight = scrollX + width
                    if (daRight - screenRight < maxJump) {
                        scrollDelta = daRight - screenRight
                    }
                }
            }
            if (scrollDelta == 0) {
                return false
            }
            doScrollX(if (direction == View.FOCUS_RIGHT) scrollDelta else -scrollDelta)
        }

        if (currentFocused != null && currentFocused.isFocused && isOffScreenH(currentFocused)) {
            // 当前焦点不在屏幕上，所以我们需要将焦点移动到其他地方
            // 这是为了避免用户无法看到的焦点视图
            val descendantFocusability = descendantFocusability
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            requestFocus()
            setDescendantFocusability(descendantFocusability)
        }
        return true
    }
    
    /**
     * 检查视图是否在屏幕外（垂直方向）
     */
    private fun isOffScreenV(descendant: View): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)
        return mTempRect.bottom + 1 < scrollY || mTempRect.top - 1 > scrollY + height
    }
    
    /**
     * 检查视图是否在屏幕外（水平方向）
     */
    private fun isOffScreenH(descendant: View): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)
        return mTempRect.right + 1 < scrollX || mTempRect.left - 1 > scrollX + width
    }
    
    /**
     * 检查视图是否在屏幕的delta范围内（垂直方向）
     */
    private fun isWithinDeltaOfScreenV(descendant: View, delta: Int, height: Int): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)

        return (mTempRect.bottom + delta >= scrollY && mTempRect.top - delta <= scrollY + height)
    }
    
    /**
     * 检查视图是否在屏幕的delta范围内（水平方向）
     */
    private fun isWithinDeltaOfScreenH(descendant: View, delta: Int, width: Int): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)

        return (mTempRect.right + delta >= scrollX && mTempRect.left - delta <= scrollX + width)
    }
    
    /**
     * 滚动内容，使其位于顶部或底部
     */
    private fun fullScrollV(direction: Int): Boolean {
        val down = direction == View.FOCUS_DOWN
        val height = height

        mTempRect.top = 0
        mTempRect.bottom = height

        if (down) {
            val count = childCount
            if (count > 0) {
                val child = getChildAt(count - 1)
                mTempRect.bottom = child.bottom + paddingBottom
                mTempRect.top = mTempRect.bottom - height
            }
        }

        return scrollAndFocusV(direction, mTempRect.top, mTempRect.bottom)
    }
    
    /**
     * 滚动内容，使其位于左侧或右侧
     */
    private fun fullScrollH(direction: Int): Boolean {
        val right = direction == View.FOCUS_RIGHT
        val width = width

        mTempRect.left = 0
        mTempRect.right = width

        if (right) {
            val count = childCount
            if (count > 0) {
                val child = getChildAt(count - 1)
                mTempRect.right = child.right + paddingRight
                mTempRect.left = mTempRect.right - width
            }
        }

        return scrollAndFocusH(direction, mTempRect.left, mTempRect.right)
    }
    
    /**
     * 滚动内容并将焦点移动到合适的视图（垂直方向）
     */
    private fun scrollAndFocusV(direction: Int, top: Int, bottom: Int): Boolean {
        var handled = true

        val height = height
        val containerTop = scrollY
        val containerBottom = containerTop + height
        val up = direction == View.FOCUS_UP

        var newFocused: View? = findFocusableViewInBoundsV(up, top, bottom)
        if (newFocused == null) {
            newFocused = this
        }

        if (top >= containerTop && bottom <= containerBottom) {
            handled = false
        } else {
            val delta = if (up) top - containerTop else bottom - containerBottom
            doScrollY(delta)
        }

        if (newFocused !== findFocus()) newFocused?.requestFocus(direction)

        return handled
    }
    
    /**
     * 滚动内容并将焦点移动到合适的视图（水平方向）
     */
    private fun scrollAndFocusH(direction: Int, left: Int, right: Int): Boolean {
        var handled = true

        val width = width
        val containerLeft = scrollX
        val containerRight = containerLeft + width
        val goLeft = direction == View.FOCUS_LEFT

        var newFocused: View? = findFocusableViewInBoundsH(goLeft, left, right)
        if (newFocused == null) {
            newFocused = this
        }

        if (left >= containerLeft && right <= containerRight) {
            handled = false
        } else {
            val delta = if (goLeft) left - containerLeft else right - containerRight
            doScrollX(delta)
        }

        if (newFocused !== findFocus()) newFocused?.requestFocus(direction)

        return handled
    }
    
    /**
     * 在给定边界内查找可获取焦点的视图（垂直方向）
     */
    private fun findFocusableViewInBoundsV(up: Boolean, top: Int, bottom: Int): View? {
        val focusables = getFocusables(View.FOCUS_FORWARD)
        var focusCandidate: View? = null

        var foundFullyContainedFocusable = false

        for (view in focusables) {
            val viewTop = view.top
            val viewBottom = view.bottom

            if (top < viewBottom && viewTop < bottom) {
                val viewIsFullyContained = top < viewTop && viewBottom < bottom

                if (focusCandidate == null) {
                    // 第一个候选者
                    focusCandidate = view
                    foundFullyContainedFocusable = viewIsFullyContained
                } else {
                    val viewIsCloserToBoundary = (up && viewTop < focusCandidate.top) ||
                            (!up && viewBottom > focusCandidate.bottom)

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            // 找到了更好的完全包含的候选者
                            focusCandidate = view
                        }
                    } else {
                        if (viewIsFullyContained) {
                            // 找到了第一个完全包含的候选者
                            focusCandidate = view
                            foundFullyContainedFocusable = true
                        } else if (viewIsCloserToBoundary) {
                            // 找到了更靠近边界的候选者
                            focusCandidate = view
                        }
                    }
                }
            }
        }

        return focusCandidate
    }
    
    /**
     * 在给定边界内查找可获取焦点的视图（水平方向）
     */
    private fun findFocusableViewInBoundsH(leftFocus: Boolean, left: Int, right: Int): View? {
        val focusables = getFocusables(View.FOCUS_FORWARD)
        var focusCandidate: View? = null

        var foundFullyContainedFocusable = false

        for (view in focusables) {
            val viewLeft = view.left
            val viewRight = view.right

            if (left < viewRight && viewLeft < right) {
                val viewIsFullyContained = left < viewLeft && viewRight < right

                if (focusCandidate == null) {
                    // 第一个候选者
                    focusCandidate = view
                    foundFullyContainedFocusable = viewIsFullyContained
                } else {
                    val viewIsCloserToBoundary = (leftFocus && viewLeft < focusCandidate.left) ||
                            (!leftFocus && viewRight > focusCandidate.right)

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            // 找到了更好的完全包含的候选者
                            focusCandidate = view
                        }
                    } else {
                        if (viewIsFullyContained) {
                            // 找到了第一个完全包含的候选者
                            focusCandidate = view
                            foundFullyContainedFocusable = true
                        } else if (viewIsCloserToBoundary) {
                            // 找到了更靠近边界的候选者
                            focusCandidate = view
                        }
                    }
                }
            }
        }

        return focusCandidate
    }
    
    /**
     * 处理页面滚动
     */
    private fun pageScrollV(direction: Int) {
        val down = direction == View.FOCUS_DOWN
        val height = height

        if (down) {
            mTempRect.top = scrollY + height
            val count = childCount
            if (count > 0) {
                val child = getChildAt(count - 1)
                if (mTempRect.top + height > child.bottom) {
                    mTempRect.top = child.bottom - height
                }
            }
        } else {
            mTempRect.top = scrollY - height
            if (mTempRect.top < 0) {
                mTempRect.top = 0
            }
        }
        mTempRect.bottom = mTempRect.top + height

        scrollAndFocusV(direction, mTempRect.top, mTempRect.bottom)
    }
    
    /**
     * 执行垂直滚动
     */
    private fun doScrollY(delta: Int) {
        if (delta != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(0, delta)
            } else {
                scrollBy(0, delta)
            }
        }
    }
    
    /**
     * 执行水平滚动
     */
    private fun doScrollX(delta: Int) {
        if (delta != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(delta, 0)
            } else {
                scrollBy(delta, 0)
            }
        }
    }
    
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 布局后重置标志
        mIsLayoutDirty = false
        
        // 如果有一个子视图请求焦点，但我们延迟了滚动，现在是时候了
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo!!, this)) {
            scrollToChild(mChildToScrollTo!!)
        }
        mChildToScrollTo = null

        // 如果我们还没有布局，请不要在这里滚动
        if (!isLaidOut) {
            return
        }

        // 确保当前的滚动位置在有效范围内
        val scrollRange = getScrollRange()
        if (scrollY > scrollRange) {
            scrollTo(scrollX, scrollRange)
        } else if (scrollY < 0) {
            scrollTo(scrollX, 0)
        }
    }
    
    /**
     * 检查给定的视图是否是指定祖先的后代
     */
    private fun isViewDescendantOf(child: View, parent: View): Boolean {
        if (child === parent) {
            return true
        }

        val theParent = child.parent
        return (theParent is ViewGroup) && isViewDescendantOf(theParent as View, parent)
    }
    
    /**
     * 获取可滚动的范围
     */
    private fun getScrollRange(): Int {
        var scrollRange = 0
        if (childCount > 0) {
            val child = getChildAt(0)
            scrollRange = Math.max(
                0,
                child.height - (height - paddingBottom - paddingTop)
            )
        }
        return scrollRange
    }
    
    /**
     * 获取水平可滚动的范围
     */
    private fun getScrollRangeH(): Int {
        var scrollRange = 0
        if (childCount > 0) {
            val child = getChildAt(0)
            scrollRange = Math.max(
                0,
                child.width - (width - paddingRight - paddingLeft)
            )
        }
        return scrollRange
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val currentFocused = findFocus()
        if (null == currentFocused || this === currentFocused) {
            return
        }

        // 如果视图现在在窗口中，则无需做任何事情
        // 之前的逻辑是有缺陷的，因为它会根据整个视图的绘制矩形而不是光标位置错误地滚动。
        // EditText在尺寸变化时应自动请求将光标矩形显示在屏幕上。删除手动干预。
    }
    
    /**
     * 处理绘制事件
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
    
    /**
     * 处理保存状态
     */
    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.scrollPositionX = scrollX
        ss.scrollPositionY = scrollY
        return ss
    }
    
    /**
     * 处理恢复状态
     */
    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        scrollTo(state.scrollPositionX, state.scrollPositionY)
    }
    
    /**
     * 保存滚动视图的状态
     */
    class SavedState : BaseSavedState {
        var scrollPositionX = 0
        var scrollPositionY = 0

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            scrollPositionX = source.readInt()
            scrollPositionY = source.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(scrollPositionX)
            dest.writeInt(scrollPositionY)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
} 