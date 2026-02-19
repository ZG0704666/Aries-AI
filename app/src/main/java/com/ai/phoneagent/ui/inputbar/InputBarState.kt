package com.ai.phoneagent.ui.inputbar

/**
 * 底部输入栏状态管理
 * 对应 InputState 密封类，用于定义输入栏的不同交互状态
 */
sealed class InputState {
    object Idle : InputState()                    // 默认状态（文本输入模式）
    object VoiceIdle : InputState()               // 语音模式等待触发（显示“按住说话”）
    object Typing : InputState()                  // 输入中
    data class VoiceRecording(val isCancelling: Boolean = false) : InputState() // 语音录制中，带取消状态
    object VoiceRecognizing : InputState()        // 语音识别中
    object Sending : InputState()                 // 发送中
    object Disabled : InputState()                // 禁用状态（AI回复中）
}
