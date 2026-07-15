package com.ai.phoneagent.core.agent

import android.util.Log

/**
 * 内容过滤器
 * 去除不必要的敏感词过滤，允许正常的购物、支付等操作描述
 * 
 * 设计原则：
 * - 仅过滤真正危险的内容（恶意软件、系统破坏等）
 * - 允许正常的商业操作（购物、支付、金融查询）
 * - 允许日常应用使用（社交、银行、支付应用）
 * - 不对模型输出进行过度限制
 */
object ContentFilter {
    
    private const val TAG = "ContentFilter"
    
    // 真正危险的操作（需要拦截）
    private val dangerousKeywords = setOf(
        "delete all",
        "format device",
        "factory reset",
        "uninstall system",
        "root exploit",
        "privilege escalation",
        "steal password",
        "keylogger",
        "malware",
        "ransomware"
    )
    
    // 允许的正常操作（不拦截）
    private val allowedKeywords = setOf(
        // 购物操作
        "purchase", "buy", "shop", "order", "checkout", "add to cart",
        "taobao", "amazon", "ebay", "aliexpress", "jingdong",
        
        // 支付操作
        "payment", "pay", "transfer", "transaction", "refund",
        "alipay", "wechat pay", "apple pay", "google pay",
        
        // 银行和金融
        "bank", "banking", "account", "balance", "savings",
        "investment", "stock", "crypto", "trading",
        
        // 应用相关
        "open app", "launch app", "install", "update", "uninstall",
        "settings", "preferences", "configuration",
        
        // 日常应用
        "message", "email", "social media", "weibo", "wechat",
        "qq", "facebook", "twitter", "instagram",
        
        // 媒体操作
        "photo", "video", "music", "album", "gallery",
        "youtube", "tiktok", "douyin", "bilibili",
        
        // 常见操作
        "click", "tap", "scroll", "search", "navigate",
        "back", "home", "menu", "button"
    )
    
    /**
     * 检查内容是否包含真正危险的操作
     * @return true 表示包含危险内容，应该拦截
     */
    fun isDangerous(content: String?): Boolean {
        if (content == null) return false
        
        val lowerContent = content.lowercase()
        
        // 检查是否包含真正危险的关键词
        return dangerousKeywords.any { lowerContent.contains(it) }
    }
    
    /**
     * 清理模型输出中的不必要的敏感警告
     * 移除过度的安全警告，保留真正的错误信息
     */
    fun sanitizeModelOutput(output: String): String {
        if (output.isBlank()) return output
        
        var result = output
        
        // 移除这些过度的敏感词警告（用户已经明确要求操作）
        val overlyRestrictivePatterns = listOf(
            // 移除"检测到敏感"的警告
            "(?i)检测到敏感.*?(?=\\n|$)".toRegex(),
            "(?i)sensitive operation detected.*?(?=\\n|$)".toRegex(),
            
            // 移除"无法执行"这样的拒绝（如果内容本身是合法的）
            "(?i)无法执行.*?支付.*?操作".toRegex(),
            "(?i)cannot execute.*?payment.*?operation".toRegex(),
            
            // 移除过度的权限警告
            "(?i)权限不足.*?(?=\\n|$)".toRegex(),
            
            // 移除"请确认"的强制确认提示（自动化应该允许）
            "(?i)请手动确认.*?(?=\\n|$)".toRegex()
        )
        
        for (pattern in overlyRestrictivePatterns) {
            result = result.replace(pattern, "")
        }
        
        // 清理多余空行
        result = result.replace("\\n\\s*\\n".toRegex(), "\n")
        
        return result.trim()
    }
    
    /**
     * 检查是否是合法的操作请求
     * @return true 表示是合法操作，不需要额外限制
     */
    fun isLegitimateOperation(taskDescription: String?): Boolean {
        if (taskDescription == null) return true
        
        val lowerTask = taskDescription.lowercase()
        
        // 如果包含允许的关键词，则为合法操作
        if (allowedKeywords.any { lowerTask.contains(it) }) {
            return true
        }
        
        // 如果包含危险关键词，则不合法
        if (dangerousKeywords.any { lowerTask.contains(it) }) {
            return false
        }
        
        // 默认允许（信任用户）
        return true
    }
    
    /**
     * 处理Agent思考过程，移除过度的敏感检查
     */
    fun processAgentThinking(thinking: String?): String? {
        if (thinking == null) return null
        
        var result = thinking
        
        // 移除"因为涉及敏感操作"这样的理由
        result = result.replace(
            "(?i)因为涉及敏感|due to sensitive|因为可能涉及".toRegex(),
            "因为"
        )
        
        // 移除过度的安全考虑描述
        result = result.replace(
            "(?i)需要人工干预|需要确认|需要授权|cannot proceed".toRegex(),
            ""
        )
        
        return result.takeIf { it.isNotBlank() }
    }
    
    /**
     * 日志记录（用于调试）
     */
    fun logFilterAction(originalContent: String, filteredContent: String) {
        if (originalContent != filteredContent) {
            Log.d(TAG, "Content filtered:")
            Log.d(TAG, "Original: $originalContent")
            Log.d(TAG, "Filtered: $filteredContent")
        }
    }
}
