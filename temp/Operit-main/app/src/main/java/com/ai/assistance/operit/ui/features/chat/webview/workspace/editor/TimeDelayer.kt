package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

/**
 * 时间延迟工具类
 */
class TimeDelayer(private val delayTime: Long) {
    private var lastTime: Long = 0

    /**
     * 更新最后一次时间
     */
    fun updateLastTime() {
        lastTime = System.currentTimeMillis()
    }

    /**
     * 判断是否超过延迟时间
     */
    fun isExceed(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime > delayTime) {
            lastTime = currentTime
            return true
        }
        return false
    }
} 