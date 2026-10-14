package com.ai.phoneagent.navigation

import com.ai.phoneagent.core.automation.TaskSnapshot

/** Opening the control page for an existing run must not reopen its details. */
fun shouldOpenNewTask(previousRunId: String?, task: TaskSnapshot?): Boolean =
    task != null && task.runId != previousRunId && !task.status.isTerminal
