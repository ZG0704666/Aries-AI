package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

/**
 * 字符串构建器缓存类，用于高效处理字符串构建
 */
class StringBuilderMemory {
    private val stringBuilder = StringBuilder()
    
    /**
     * 清空构建器
     */
    fun clear() {
        stringBuilder.setLength(0)
    }
    
    /**
     * 追加字符串
     */
    fun append(str: String) {
        stringBuilder.append(str)
    }
    
    /**
     * 追加字符
     */
    fun append(c: Char) {
        stringBuilder.append(c)
    }
    
    /**
     * 获取当前长度
     */
    fun length(): Int {
        return stringBuilder.length
    }
    
    /**
     * 获取构建的字符串
     */
    override fun toString(): String {
        return stringBuilder.toString()
    }
} 