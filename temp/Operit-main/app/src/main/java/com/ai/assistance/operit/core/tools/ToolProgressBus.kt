package com.ai.assistance.operit.core.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToolProgressEvent(
    val toolName: String,
    val progress: Float,
    val message: String = ""
)

object ToolProgressBus {
    private val _progress = MutableStateFlow<ToolProgressEvent?>(null)
    val progress: StateFlow<ToolProgressEvent?> = _progress.asStateFlow()

    fun update(toolName: String, progress: Float, message: String = "") {
        _progress.value = ToolProgressEvent(toolName = toolName, progress = progress, message = message)
    }

    fun clear() {
        _progress.value = null
    }
}
