package com.ai.phoneagent.ui.inputbar

/**
 * 语音输入会话状态机的纯逻辑控制器（无 Android/协程依赖，可 JVM 单测）。
 *
 * 作为会话事实的唯一来源：持有会话 id、当前 InputState 与"松手=发送"标记。
 * MainActivity 负责副作用（停止录音、看门狗、Toast、恢复文本），只在
 * 控制器返回肯定决策时执行。
 *
 * 状态迁移：
 * ```
 * Idle/无会话 --begin--> VoiceRecording --onPartialResult--> VoiceRecognizing
 * VoiceRecording/VoiceRecognizing --onVoiceEnd--> VoiceFinalizing
 * 任意语音态 --final/error/timeout/watchdog/resetToIdle--> Idle
 * ```
 */
class VoiceSessionController {

    var state: InputState = InputState.Idle
        private set
    var activeSessionId: Long = 0L
        private set
    private var pendingSendAfterVoice: Boolean = false

    /** 新会话开始（权限已授予后才调用，避免权限弹窗期间留下悬挂会话）。 */
    fun begin(sessionId: Long) {
        activeSessionId = sessionId
        pendingSendAfterVoice = false
        state = InputState.VoiceRecording()
    }

    fun isActive(sessionId: Long): Boolean =
        sessionId > 0L && activeSessionId == sessionId

    /**
     * 收到 partial 识别结果：仅在仍处于录音态时推进到识别态。
     * @return 状态是否确实切换为 VoiceRecognizing（UI 据此更新胶囊）。
     */
    fun onPartialResult(sessionId: Long): Boolean {
        if (!isActive(sessionId)) return false
        val current = state
        if (current is InputState.VoiceRecording) {
            state = InputState.VoiceRecognizing(isCancelling = current.isCancelling)
            return true
        }
        return false
    }

    /** 按住期间更新上滑取消态；partial 到达后仍保留同一取消链路。 */
    fun onUpdateCancelState(isCancelling: Boolean): Boolean {
        val current = state
        state = when (current) {
            is InputState.VoiceRecording -> {
                if (current.isCancelling == isCancelling) return false
                current.copy(isCancelling = isCancelling)
            }
            is InputState.VoiceRecognizing -> {
                if (current.isCancelling == isCancelling) return false
                current.copy(isCancelling = isCancelling)
            }
            else -> return false
        }
        return true
    }

    /**
     * 松手抬手：无论是否收到过 partial（录音态或识别态）都进入收尾，
     * 并标记松手=发送。权限弹窗后迟到的抬手（无活动会话/状态已复位）不生效。
     * @return 是否进入 VoiceFinalizing（UI 据此启动看门狗并停止录音）。
     */
    fun onVoiceEnd(sessionId: Long): Boolean {
        if (!isActive(sessionId)) return false
        if (state is InputState.VoiceRecording || state is InputState.VoiceRecognizing) {
            state = InputState.VoiceFinalizing
            pendingSendAfterVoice = true
            return true
        }
        return false
    }

    /** 取消（上滑）：仅当会话活动时清除发送标记并把状态复位为 Idle。 */
    fun onVoiceCancel(sessionId: Long): Boolean {
        if (!isActive(sessionId)) return false
        state = InputState.Idle
        pendingSendAfterVoice = false
        return true
    }

    /**
     * 终态回调（final/error/timeout/看门狗）是否应把 UI 复位为 Idle：
     * 仅当会话仍活动且 UI 尚处于语音相关状态，避免覆盖用户已切换的其他模式。
     */
    fun shouldResetToIdle(sessionId: Long): Boolean =
        isActive(sessionId) && (
            state is InputState.VoiceRecording ||
                state is InputState.VoiceRecognizing ||
                state is InputState.VoiceFinalizing
            )

    /** 读取并清除"松手=发送"标记（终态回调中恰好消费一次）。 */
    fun consumePendingSend(): Boolean {
        val v = pendingSendAfterVoice
        pendingSendAfterVoice = false
        return v
    }

    /** 看门狗/终态兜底：复位状态与发送标记（sessionId 由 clearSession 清除）。 */
    fun resetToIdle() {
        state = InputState.Idle
        pendingSendAfterVoice = false
    }

    /** 会话结束：匹配（或不校验）时清除活动会话 id。 */
    fun clearSession(expectedSessionId: Long? = null) {
        if (expectedSessionId == null || activeSessionId == expectedSessionId) {
            activeSessionId = 0L
        }
    }
}
