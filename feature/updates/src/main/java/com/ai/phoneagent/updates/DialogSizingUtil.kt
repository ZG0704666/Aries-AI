package com.ai.phoneagent.updates

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.WindowManager

/** 更新弹窗的尺寸计算工具，按屏幕高度自适应卡片与内容区高度。 */
object DialogSizingUtil {

    /** 返回屏幕高度（像素）。 */
    private fun screenHeightPx(context: Context): Int {
        val dm = context.resources.displayMetrics
        return dm.heightPixels
    }

    /** 将 dp 值转换为像素。 */
    private fun dp(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics,
        ).toInt()
    }

    /**
     * 按屏幕比例设置卡片、滚动正文与列表的高度约束。
     *
     * 含列表时整体更紧凑；正文与列表高度均做上下限裁剪，避免过小或溢出。
     */
    fun applyCompactSizing(
        context: Context,
        cardView: View,
        scrollBody: View?,
        listView: View?,
        hasList: Boolean,
    ) {
        val screenH = screenHeightPx(context)

        val maxCardH = (screenH * (if (hasList) 0.72f else 0.78f)).toInt()
        val minCardH = dp(context, 360f)

        cardView.layoutParams = cardView.layoutParams.apply {
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        cardView.minimumHeight = minCardH

        if (scrollBody != null) {
            val bodyH = if (hasList) (screenH * 0.18f).toInt() else (screenH * 0.46f).toInt()
            scrollBody.layoutParams = scrollBody.layoutParams.apply {
                height = bodyH.coerceAtLeast(dp(context, 120f)).coerceAtMost(dp(context, 420f))
            }
        }

        if (listView != null) {
            val listH = if (hasList) (screenH * 0.28f).toInt() else 0
            if (listH > 0) {
                listView.layoutParams = listView.layoutParams.apply {
                    height = listH.coerceAtLeast(dp(context, 180f)).coerceAtMost(dp(context, 520f))
                }
            }
        }

        cardView.post {
            val lp = cardView.layoutParams
            if (lp.height == WindowManager.LayoutParams.WRAP_CONTENT) {
                if (cardView.height > maxCardH) {
                    lp.height = maxCardH
                    cardView.layoutParams = lp
                }
            }
        }
    }
}
