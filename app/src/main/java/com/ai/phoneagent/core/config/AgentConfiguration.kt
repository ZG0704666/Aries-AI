/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.config

/**
 * Agent配置 - 统一的配置管理中心
 * 
 * 整合了原有分散的Config类，提供完整的配置管理
 * 所有参数都有合理的默认值，可按需覆盖。
 *
 * 设计原则：
 * 1) **默认可用**：不传入任何参数即可完成一次完整的端到端自动化任务。
 * 2) **可解释**：每个参数对应明确的“稳定性/性能/体验”目标，尽量避免魔法数。
 * 3) **可分层调参**：
 *    - 首先调“重试/修复/等待”以提升稳定性
 *    - 其次调“截图/上下文截断”以控制 token 与延迟
 *    - 最后调“动作延迟”以优化动画与观感
 *
 * 约定：
 * - 所有 `*Ms` 字段单位均为毫秒。
 * - `maxTokens/maxContextTokens` 等为“近似上限”，实际仍受模型与服务端限制影响。
 */
data class AgentConfiguration(
    // ========== 执行参数 ==========
    /**
     * 是否使用后台虚拟屏模式执行
     * 
     * true: 在后台虚拟屏执行自动化任务，不影响前台
     * false: 在前台执行，用户可以看到操作过程
     */
    val useBackgroundVirtualDisplay: Boolean = false,

        /**
         * 是否启用可选的 Shizuku 交互能力。
         * true: 在非虚拟屏模式下优先使用 Shizuku 模式（视图树读取、坐标点击与输入）；失败则直接报错，不回退到 Accessibility。
         * false：继续使用当前 Accessibility + AccessibilityService 截图链路。
         */
    val useShizukuInteraction: Boolean = false,

    /**
     * 最大执行步数。
     *
     * 作用：限制单次任务的最长链路，防止模型在错误 UI 上无限循环。
     * 建议：
     * - 任务简单（打开应用/点击两三次）可下调。
     * - 跨应用复杂流程可上调，但建议同时配合日志截断与上下文管理参数。
     */
    val maxSteps: Int = 100,
    
    /**
     * 每步之间的基础延迟 (ms)。
     *
     * 作用：给系统与无障碍事件队列“喘息时间”，减少因 UI 未刷新导致的误判。
     * 取值越大：更稳但更慢；越小：更快但更容易出现“点太快/读到旧界面”。
     */
    val stepDelayMs: Long = 160L,
    
    /**
     * 执行动作后的额外等待时间 (ms)。
     *
     * 作用：在动作执行完毕后追加一个统一的 settle delay，用于吸收动画/网络 loading 的尾巴。
     * 通常用于：点击进入新页面、关闭弹窗、返回上一级等。
     */
    val postActionDelayMs: Long = 120L,
    
    // ========== 模型调用参数 ==========
    /**
     * 模型调用最大重试次数。
     *
     * 覆盖场景：网络波动、服务端 5xx、超时、短暂限流等。
     */
    val maxModelRetries: Int = 3,
    
    /**
     * 重试基础延迟 (ms)。
     *
     * 建议与 `maxModelRetries` 配合使用（可做指数退避/线性退避的基准）。
     */
    val modelRetryBaseDelayMs: Long = 700L,
    
    /**
     * 解析修复最大次数。
     *
     * 当模型输出格式不符合预期（JSON/XML/自定义协议）时，允许进行“修复提示/重试解析”。
     * 值越大：对模型输出波动更鲁棒，但可能增加额外 token 和等待时间。
     */
    val maxParseRepairs: Int = 2,
    
    /**
     * 动作执行修复最大次数。
     *
     * 用于处理执行层失败：元素找不到、点击无效、窗口未切换、权限弹窗等。
     * 通常会触发“重截屏/重取 UI 树/让模型重新规划”。
     */
    val maxActionRepairs: Int = 1,
    
    // ========== 模型参数 ==========
    /**
     * 温度参数。
     *
     * 自动化任务通常希望确定性更强，因此默认接近 0。
     */
    val temperature: Float? = 0.0f,
    
    /**
     * top_p 参数（nucleus sampling）。
     *
     * 对自动化类任务：用于控制输出多样性，过高可能更“发散”。
     */
    val topP: Float? = 0.85f,
    
    /**
     * 频率惩罚（frequency penalty）。
     *
     * 用于减少重复 token（如重复输出同一指令/同一句话）。
     */
    val frequencyPenalty: Float? = 0.2f,
    
    /**
     * 最大 token 数。
     *
     * 注意：这通常是"单次回复上限"，并不等价于"上下文窗口"。
     * 增加此值以确保模型有足够空间输出完整的思考过程和动作指令。
     */
    val maxTokens: Int? = 4096,
    
    // ========== 上下文管理参数 ==========
    /**
     * 最大上下文 token 数。
     *
     * 用于做本地的"上下文裁剪/摘要"策略（避免请求过大导致延迟与费用上升）。
     * 适当增加以容纳更长的对话历史。
     */
    val maxContextTokens: Int = 30000,
    
    /**
     * UI 树最大字符数。
     *
     * UI 树一般来自无障碍节点 dump。过长会显著增加 token 消耗。
     * 超过阈值时通常需要截断/摘要（见 UI 树截断参数）。
     */
    val maxUiTreeChars: Int = 3000,
    
    /**
     * 最多保留对话轮数。
     *
     * 轮数过多会导致上下文爆炸。自动化一般更关注最近几轮的状态与用户指令。
     */
    val maxHistoryTurns: Int = 6,
    
    // ========== 性能优化参数 ==========
    /**
     * 是否启用“流式输出 + 早停”。
     *
     * 作用：模型在输出到达“可执行动作”时即可提前结束等待，降低整体延迟。
     */
    val useStreamingWithEarlyStop: Boolean = true,

    /**
     * 是否并行获取截图与 UI 树。
     *
     * 作用：减少每步采集状态的总耗时，但在少数机型上可能提升瞬时负载。
     */
    val parallelScreenshotAndUi: Boolean = true,
    
    // ========== 截图优化参数 ==========
    /** 是否启用截图缓存（重复页面可复用截图，降低频繁截屏开销） */
    val enableScreenshotCache: Boolean = true,

    /** 是否启用截图节流（限制最小截屏间隔，避免高频截屏拖慢系统） */
    val enableScreenshotThrottle: Boolean = true,

    /** 截图压缩质量（0-100）。值越大越清晰但体积更大 */
    val screenshotCompressionQuality: Int = 85,

    /** 截图目标最大体积（KB）。超过时会降低质量或缩放 */
    val screenshotMaxSizeKB: Int = 150,

    /** 截图缓存最大张数。通常 2-4 即可覆盖“当前页/上一步页” */
    val screenshotCacheMaxSize: Int = 3,

    /** 截图缓存 TTL（ms）。短 TTL 可避免 UI 变化时复用到旧截图 */
    val screenshotCacheTtlMs: Long = 2000L,

    /** 截图最小间隔（ms）。过小可能造成卡顿/发热 */
    val screenshotThrottleMinIntervalMs: Long = 1100L,
    
    // ========== 动作延迟参数 ==========
    val launchActionDelayMs: Long = 1050L,
    val typeActionDelayMs: Long = 260L,
    val tapActionDelayMs: Long = 320L,
    val swipeActionDelayMs: Long = 420L,
    val backActionDelayMs: Long = 220L,
    val homeActionDelayMs: Long = 420L,
    val waitActionDelayMs: Long = 650L,
    val defaultActionDelayMs: Long = 240L,
    
    // ========== 窗口事件等待参数 ==========
    val launchAwaitWindowTimeoutMs: Long = 2200L,
    val backAwaitWindowTimeoutMs: Long = 1400L,
    val homeAwaitWindowTimeoutMs: Long = 1800L,
    val tapAwaitWindowTimeoutMs: Long = 1400L,
    val swipeAwaitWindowTimeoutMs: Long = 1600L,
    val typeAwaitWindowTimeoutMs: Long = 1200L,
    val defaultAwaitWindowTimeoutMs: Long = 1500L,
    
    // ========== UI树参数 ==========
    /** UI 树最多保留节点数（用于摘要/精简）。越大信息越全但 token 越多 */
    val uiTreeMaxNodes: Int = 30,

    /** Shizuku UI 树摘要节点上限（结构化精简专用） */
    val shizukuUiTreeMaxNodes: Int = 40,

    /** Shizuku UI 树摘要最大字符数（与 maxUiTreeChars 分离，便于单独调优） */
    val shizukuUiTreeMaxChars: Int = 3200,

    /** Shizuku UI 树摘要详情等级：minimal / summary / full */
    val shizukuUiTreeDetail: String = "summary",

    /** Shizuku 输入自动聚焦策略：true=仅首轮 Type 前自动聚焦一次 */
    val shizukuAutoFocusFirstTypeOnly: Boolean = true,
    
    // ========== Tap+Type合并执行参数 ==========
    val tapTypeCombineKeyboardWaitMs: Long = 400L,
    val tapTypeCombineSecondSetTextWaitMs: Long = 250L,
    val tapTypeCombineStrategy3WaitMs: Long = 200L,
    
    // ========== UI树截断参数 ==========
    /**
     * UI 树截断：保留头部比例。
     *
     * 一般“头部”更可能包含当前可交互节点、可见区域与关键控件。
     */
    val uiTreeTruncateHeadRatio: Float = 0.6f,

    /**
     * UI 树截断：最小尾部保留长度。
     *
     * 用于保留结尾处可能包含的弹窗/底部按钮等信息。
     */
    val uiTreeTruncateMinTailLength: Int = 100,
    
    // ========== 敏感内容检测 ==========
    /**
     * 敏感内容关键词（用于 UI 文本、模型输出、用户输入的风险提示/拦截）。
     *
     * 与 `dangerousOperationKeywords` 的区别：
     * - `sensitiveKeywords` 更偏“账户/隐私/验证码”
     * - `dangerousOperationKeywords` 更偏“可能造成资金风险的动作意图”
     */
    val sensitiveKeywords: List<String> = listOf(
        "支付密码", "银行卡", "信用卡", "卡号", "cvv", "安全码",
        "验证码", "短信验证码", "otp", "一次性密码", "动态口令",
        "输入密码", "请输入密码", "确认支付", "确认付款"
    ),
    
    // ========== 应用启动参数 ==========
    /** 应用启动等待超时（ms）。超过该时间仍未检测到窗口变化则视为失败 */
    val appLaunchWaitTimeoutMs: Long = 2200L,

    /** 应用启动后追加等待（ms）。用于吸收首屏加载/动画 */
    val appLaunchExtraDelayMs: Long = 500L,
    
    // ========== 流式思考参数 ==========
    val thinkingDisplayKeywords: List<String> = listOf(
        "需要", "应该", "点击", "输入", "滚动", "等待", "打开", "找到", "看到",
        "验证", "检查", "分析", "确认", "选择", "搜索", "返回", "关闭", "长按"
    ),
    /** 从“思考文本”提取显示时的最大长度（字符） */
    val thinkingExtractMaxLength: Int = 30,
    
    // ========== 预估步骤计算参数 ==========
    val estimatedStepsBaseSteps: Int = 15,
    val estimatedStepsFastGrowthThreshold: Float = 0.3f,
    val estimatedStepsMediumGrowthThreshold: Float = 0.6f,
    
    // ========== Token估算参数 ==========
    /** 单张图片 token 粗略估算值（用于本地预算/裁剪策略） */
    val imageTokenEstimate: Int = 1500,

    /** token 估算倍率（不同模型/压缩策略下可校准） */
    val tokenEstimateMultiplier: Float = 0.6f,
    
    // ========== 日志截断参数 ==========
    val logStepTruncateLength: Int = 240,
    val logThinkingTruncateLength: Int = 180,
    val logAnswerTruncateLength: Int = 220,
    val subtitleMaxLength: Int = 34,
    val logInputTextTruncateLength: Int = 40,
    val logCombineInputTextTruncateLength: Int = 20,
    val confirmMessageMaxLength: Int = 320,
    
    // ========== 元素查找参数 ==========
    /** 元素查找遍历上限（避免在异常 UI 树上 O(n) 过大） */
    val elementFindMaxNodes: Int = 2000,

    /** 元素查找保护阈值（在复杂页面上做提前终止/降级策略） */
    val elementFindGuardValue: Int = 600,
    
    // ========== UI树属性最大长度 ==========
    val uiTreeAttrMaxLenMinimal: Int = 40,
    val uiTreeAttrMaxLenSummary: Int = 80,
    val uiTreeAttrMaxLenFull: Int = 140,
    val uiTreeAttrMaxLenDefault: Int = 120,
    
    // ========== 滚动参数 ==========
    val scrollDistanceRatio: Float = 0.3f,
    val scrollDurationMs: Long = 400L,
    val scrollWaitDelayMs: Long = 500L,
    val defaultScrollDirection: String = "down",
    
    // ========== 点击参数 ==========
    val clickDurationMs: Long = 60L,
    val longPressDurationMs: Long = 520L,
    val doubleTapIntervalMs: Long = 90L,
    
    // ========== 等待参数 ==========
    val pauseCheckIntervalMs: Long = 220L,
    val windowEventPollIntervalMs: Long = 60L,
    
    // ========== 截图参数 ==========
    /** 截图缩放比例（百分比）。缩小可以降低体积与 token，但可能损失小字细节 */
    val screenshotScalePercent: Int = 80,

    /** 截图编码质量（0-100）。与 `screenshotCompressionQuality` 类似，保留用于兼容旧调用 */
    val screenshotQuality: Int = 85,

    /** 截图前隐藏 overlay 的延迟（ms），防止悬浮窗/遮罩被截入画面 */
    val screenshotOverlayHideDelayMs: Long = 80L,
    
    // ========== 危险操作关键词 ==========
    /**
     * 危险操作关键词：用于判断当前文本是否可能触发资金/账号风险。
     *
     * 典型用途：当模型准备执行包含这些关键词的动作时，要求用户二次确认。
     */
    val dangerousOperationKeywords: List<String> = listOf(
        "支付", "密码", "银行卡", "信用卡", "cvv", "安全码",
        "验证码", "确认支付", "确认付款"
    )
) {
    companion object {
        /** 默认配置：线上/日常使用基线 */
        val DEFAULT = AgentConfiguration()

        /**
         * 测试配置：用于单元测试或快速回归。
         * 特点：更少步骤、更短延迟、更少重试，以便快速失败并定位问题。
         */
        val TEST = AgentConfiguration(
            maxSteps = 10,
            stepDelayMs = 50L,
            maxModelRetries = 1,
                                                                                                          screenshotThrottleMinIntervalMs = 500L,
            screenshotCacheTtlMs = 1000L,
        )
    }
    
    /**
     * 根据动作名称映射动作延迟。
     *
     * 注意：`actionName` 可能来自模型输出，因此这里做了归一化（去空格、转小写）。
     */
    fun getActionDelayMs(actionName: String): Long {
        val normalized = actionName.replace(" ", "").lowercase()
        return when (normalized) {
            "launch", "open_app", "start_app" -> launchActionDelayMs
            "type", "input", "text", "type_name" -> typeActionDelayMs
            "tap", "click", "press", "doubletap", "double_tap", "longpress", "long_press" -> tapActionDelayMs
            "swipe", "scroll" -> swipeActionDelayMs
            "back" -> backActionDelayMs
            "home" -> homeActionDelayMs
            "wait" -> waitActionDelayMs
            else -> defaultActionDelayMs
        }
    }
    
    /**
     * 根据动作名称映射窗口事件等待超时。
     *
     * 作用：动作发出后，等待 Accessibility window event / UI 变化。
     * 如果超时：执行层可选择重试、回退、或触发修复流程。
     */
    fun getAwaitWindowTimeoutMs(actionName: String): Long {
        val normalized = actionName.replace(" ", "").lowercase()
        return when (normalized) {
            "launch", "open_app", "start_app" -> launchAwaitWindowTimeoutMs
            "back" -> backAwaitWindowTimeoutMs
            "home" -> homeAwaitWindowTimeoutMs
            "tap", "click", "press", "longpress", "long_press", "doubletap", "double_tap" -> tapAwaitWindowTimeoutMs
            "swipe", "scroll" -> swipeAwaitWindowTimeoutMs
            "type", "input", "text" -> typeAwaitWindowTimeoutMs
            else -> defaultAwaitWindowTimeoutMs
        }
    }
    
    /**
     * 判断文本是否包含危险关键词。
     *
     * 用于 UI 文本 / 用户输入 / 模型输出的风险分级。
     */
    fun isDangerousKeyword(text: String): Boolean {
        return dangerousOperationKeywords.any { text.contains(it, ignoreCase = true) }
    }
}

