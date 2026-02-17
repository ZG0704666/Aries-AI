package com.ai.phoneagent.updates

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import com.ai.phoneagent.core.utils.DisplayUtils

object DialogSizingUtil {

    private fun screenHeightPx(context: Context): Int {
        val dm = context.resources.displayMetrics
        return dm.heightPixels
    }

    // 使用 DisplayUtils.dp 替代私有方法
    private fun dp(context: Context, dp: Float): Int = DisplayUtils.dp(context, dp.toInt())

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
