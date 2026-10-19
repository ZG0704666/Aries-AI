package com.ai.phoneagent.navigation

import com.ai.phoneagent.core.automation.TaskSnapshot

/** 判断是否应打开新任务页：任务存在、runId 变化且尚未进入终态。 */
fun shouldOpenNewTask(previousRunId: String?, task: TaskSnapshot?): Boolean =
    task != null && task.runId != previousRunId && !task.status.isTerminal
