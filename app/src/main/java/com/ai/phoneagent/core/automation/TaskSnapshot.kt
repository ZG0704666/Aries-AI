package com.ai.phoneagent.core.automation

enum class TaskStatus {
    RUNNING, PAUSE_REQUESTED, PAUSED, SUCCEEDED, FAILED, CANCELLED;
    val isTerminal: Boolean get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

data class TaskSnapshot(
    val runId: String,
    val goal: String,
    val startedAt: Long,
    val status: TaskStatus = TaskStatus.RUNNING,
    val steps: Int = 0,
    val log: String = "",
    val message: String = "",
) {
    fun requestPause(paused: Boolean): TaskSnapshot =
        if (status.isTerminal) this else copy(status = if (paused) TaskStatus.PAUSE_REQUESTED else TaskStatus.RUNNING)

    fun acknowledgePause(paused: Boolean): TaskSnapshot =
        if (status.isTerminal) this else copy(status = if (paused) TaskStatus.PAUSED else TaskStatus.RUNNING)
}
