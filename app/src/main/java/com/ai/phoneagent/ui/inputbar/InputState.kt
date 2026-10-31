package com.ai.phoneagent.ui.inputbar

/**
 * 底部输入栏状态管理
 */
sealed class InputState {
    object Idle : InputState()                    // 默认状态（文本输入模式）
    object VoiceIdle : InputState()               // 语音模式等待触发（显示"按住说话"）
    object Typing : InputState()                  // 输入中
    data class VoiceRecording(val isCancelling: Boolean = false) : InputState() // 语音录制中，带取消状态
    data class VoiceRecognizing(val isCancelling: Boolean = false) : InputState() // 按住期间的流式识别，仍允许上滑取消
    object VoiceFinalizing : InputState()         // 松手后等待最终识别结果
    object Sending : InputState()                 // 发送中
    object Generating : InputState()              // AI生成中（显示停止按钮）
    object Disabled : InputState()                // 禁用状态（AI回复中）
}

internal enum class VoicePresentation {
    Idle,
    Listening,
    Cancelling,
    Recognizing,
    AwaitingRelease,
    Finalizing,
}

internal fun voicePresentation(state: InputState): VoicePresentation = when (state) {
    InputState.VoiceIdle -> VoicePresentation.Idle
    is InputState.VoiceRecording ->
        if (state.isCancelling) VoicePresentation.Cancelling else VoicePresentation.Listening
    is InputState.VoiceRecognizing ->
        if (state.isCancelling) VoicePresentation.Cancelling else VoicePresentation.Recognizing
    InputState.VoiceFinalizing -> VoicePresentation.Finalizing
    else -> VoicePresentation.Idle
}
