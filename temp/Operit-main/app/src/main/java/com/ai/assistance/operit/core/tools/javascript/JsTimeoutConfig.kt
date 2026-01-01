package com.ai.assistance.operit.core.tools.javascript

/**
 * JavaScript 引擎统一超时配置
 * 
 * 统一管理所有 JavaScript 相关的超时设置，避免分散在各个类中
 */
object JsTimeoutConfig {
    
    /**
     * 主超时时间（秒）
     * 用于 JavaScript 引擎的总体执行超时
     */
    const val MAIN_TIMEOUT_SECONDS = 1800L // 30分钟
    
    /**
     * 预警超时时间（秒）
     * 在主超时前 5 秒触发 JavaScript 端的超时保护
     */
    const val PRE_TIMEOUT_SECONDS = MAIN_TIMEOUT_SECONDS - 5L
    
    /**
     * Promise 异步超时时间（秒）
     * 比主超时稍短，确保异步操作能及时完成
     */
    const val ASYNC_PROMISE_TIMEOUT_SECONDS = MAIN_TIMEOUT_SECONDS - 10L
    
    /**
     * 脚本执行超时时间（毫秒）
     * 用于 JsToolManager 的脚本执行超时
     */
    const val SCRIPT_TIMEOUT_MS = MAIN_TIMEOUT_SECONDS * 1000L
    
    /**
     * 工具调用超时时间（毫秒）
     * 用于单个工具调用的超时限制
     */
    const val TOOL_CALL_TIMEOUT_MS = SCRIPT_TIMEOUT_MS
    
    // 便利方法：获取毫秒值
    val mainTimeoutMs: Long get() = MAIN_TIMEOUT_SECONDS * 1000L
    val preTimeoutMs: Long get() = PRE_TIMEOUT_SECONDS * 1000L
    val asyncPromiseTimeoutMs: Long get() = ASYNC_PROMISE_TIMEOUT_SECONDS * 1000L
    
    /**
     * 修改主超时时间
     * 注意：这会同时影响所有相关的超时设置
     */
    fun setMainTimeout(_seconds: Long) {
        // 由于使用了 const，这里只能通过反射或重新设计来实现
        // 暂时保留这个方法作为未来扩展的接口
        throw UnsupportedOperationException("Dynamic timeout modification not yet implemented")
    }
}
