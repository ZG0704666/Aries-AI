package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context

/**
 * 尺寸转换工具类
 */
object DpiUtils {
    /**
     * 将dp转换为像素
     */
    fun dip2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 将像素转换为dp
     */
    fun px2dip(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }
} 