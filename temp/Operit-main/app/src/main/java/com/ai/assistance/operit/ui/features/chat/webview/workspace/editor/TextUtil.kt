package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Paint
import java.util.HashMap

/**
 * 文本工具类，用于测量文本宽度并缓存结果
 */
object TextUtil {
    private val fontMaps = HashMap<String, HashMap<Float, Float>>()
    
    /**
     * 测量文本宽度并缓存结果
     */
    fun measureText(paint: Paint, c: String): Float {
        var map = fontMaps[c]
        if (map == null) {
            map = HashMap()
            fontMaps[c] = map
        }
        
        val textSize = paint.textSize
        if (map.containsKey(textSize)) {
            return map[textSize]!!
        } else {
            val len = paint.measureText(c)
            map[textSize] = len
            return len
        }
    }
} 