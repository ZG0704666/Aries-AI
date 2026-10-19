package com.ai.phoneagent.core.automation

/** 任务执行状态；isTerminal 表示已进入不再变化的终态。 */
enum class TaskStatus {
    RUNNING, PAUSE_REQUESTED, PAUSED, SUCCEEDED, FAILED, CANCELLED;
    val isTerminal: Boolean get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

/** 一次自动化任务运行的快照：目标、起止时间、状态、步数与日志。 */
data class TaskSnapshot(
    val runId: String,
    val goal: String,
    val startedAt: Long,
    val status: TaskStatus = TaskStatus.RUNNING,
    val steps: Int = 0,
    val log: String = "",
    val message: String = "",
) {
    /** 请求暂停/恢复；终态下保持原样。 */
    fun requestPause(paused: Boolean): TaskSnapshot =
        if (status.isTerminal) this else copy(status = if (paused) TaskStatus.PAUSE_REQUESTED else TaskStatus.RUNNING)

    /** 确认暂停/恢复已生效；终态下保持原样。 */
    fun acknowledgePause(paused: Boolean): TaskSnapshot =
        if (status.isTerminal) this else copy(status = if (paused) TaskStatus.PAUSED else TaskStatus.RUNNING)
}
