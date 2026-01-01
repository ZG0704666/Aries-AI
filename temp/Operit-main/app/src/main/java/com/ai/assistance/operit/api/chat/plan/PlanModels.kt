package com.ai.assistance.operit.api.chat.plan

import com.google.gson.annotations.SerializedName

/**
 * Represents a task in the execution plan.
 *
 * @property id A unique identifier for the task.
 * @property name A descriptive name for the task.
 * @property instruction The specific instruction or prompt for the AI to execute this task.
 * @property dependencies A list of task IDs that must be completed before this task can start.
 * @property type The type of the task, e.g., "chat", "data_processing".
 */
data class TaskNode(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("instruction") val instruction: String,
    @SerializedName("dependencies") val dependencies: List<String> = emptyList(),
    @SerializedName("type") val type: String = "chat" // Default to chat
)

/**
 * Represents the entire execution graph.
 *
 * @property tasks A list of all tasks in the plan.
 * @property finalSummaryInstruction The instruction for the final summarization task after all other tasks are complete.
 */
data class ExecutionGraph(
    @SerializedName("tasks") val tasks: List<TaskNode>,
    @SerializedName("final_summary_instruction") val finalSummaryInstruction: String
) 