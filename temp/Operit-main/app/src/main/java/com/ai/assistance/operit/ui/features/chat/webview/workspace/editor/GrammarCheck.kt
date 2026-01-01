package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Paint

/**
 * 语法检查接口
 */
interface GrammarCheck {
    /**
     * 错误显示类
     */
    class ShowError {
        var point: Int = 0
        var message: String = ""
    }

    /**
     * 语法检查接口
     */
    interface Check {
        /**
         * 进行语法检查
         */
        fun publicTest(msg: String)

        /**
         * 获取错误信息
         */
        fun getError(): ShowError?

        /**
         * 准备绘制
         */
        fun prepare(complete: Boolean)

        /**
         * 设置绘制参数
         */
        fun setPaint(s: String, fontWidths: Float, mPaint: Paint)

        /**
         * 移动到下一行
         */
        fun nextLine(xOffset: Float, lbaseline: Float)
    }

    /**
     * JSON语法检查接口
     */
    interface JSON : Check {
        /**
         * 获取当前位置的JSON路径
         */
        fun getSurface(text: CharSequence): String
    }
} 