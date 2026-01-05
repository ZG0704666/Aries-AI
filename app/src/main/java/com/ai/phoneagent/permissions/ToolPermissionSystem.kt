package com.ai.phoneagent.permissions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.data.model.AITool

/**
 * 工具权限系统
 * 简化版实现，只支持危险操作确认
 */
class ToolPermissionSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ToolPermissionSystem"
        private const val PREFS_NAME = "tool_permissions"
        private const val KEY_MASTER_SWITCH = "master_switch"

        @Volatile
        private var INSTANCE: ToolPermissionSystem? = null

        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolPermissionSystem(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val toolHandler = AIToolHandler.getInstance(context)

    /**
     * 权限级别
     */
    enum class PermissionLevel {
        ALLOW,      // 自动允许所有操作
        CAUTION,    // 危险操作需确认，普通操作自动允许
        ASK,        // 所有操作都需确认
        FORBID      // 禁止所有操作
    }

    /**
     * 获取主开关权限级别
     */
    fun getMasterPermissionLevel(): PermissionLevel {
        val value = prefs.getString(KEY_MASTER_SWITCH, PermissionLevel.CAUTION.name)
        return try {
            PermissionLevel.valueOf(value ?: PermissionLevel.CAUTION.name)
        } catch (e: Exception) {
            PermissionLevel.CAUTION
        }
    }

    /**
     * 设置主开关权限级别
     */
    fun setMasterPermissionLevel(level: PermissionLevel) {
        prefs.edit().putString(KEY_MASTER_SWITCH, level.name).apply()
    }

    /**
     * 获取工具的权限级别
     */
    fun getToolPermissionLevel(toolName: String): PermissionLevel {
        val key = "tool_$toolName"
        val value = prefs.getString(key, null)
        return if (value != null) {
            try {
                PermissionLevel.valueOf(value)
            } catch (e: Exception) {
                getMasterPermissionLevel()
            }
        } else {
            getMasterPermissionLevel()
        }
    }

    /**
     * 设置工具的权限级别
     */
    fun setToolPermissionLevel(toolName: String, level: PermissionLevel) {
        val key = "tool_$toolName"
        prefs.edit().putString(key, level.name).apply()
    }

    /**
     * 检查是否需要用户确认
     * @return true 如果允许执行，false 如果拒绝
     */
    suspend fun checkPermission(tool: AITool, onNeedConfirm: suspend (String) -> Boolean): Boolean {
        val toolLevel = getToolPermissionLevel(tool.name)

        // 如果禁止，直接拒绝
        if (toolLevel == PermissionLevel.FORBID) {
            Log.d(TAG, "Tool ${tool.name} is forbidden")
            return false
        }

        // 如果允许，直接通过
        if (toolLevel == PermissionLevel.ALLOW) {
            Log.d(TAG, "Tool ${tool.name} is allowed")
            return true
        }

        // 如果是谨慎模式，检查是否危险
        if (toolLevel == PermissionLevel.CAUTION) {
            val isDangerous = toolHandler.isDangerousOperation(tool)
            if (!isDangerous) {
                Log.d(TAG, "Tool ${tool.name} is not dangerous, auto allow")
                return true
            }
        }

        // 需要用户确认
        val description = toolHandler.getOperationDescription(tool)
        Log.d(TAG, "Tool ${tool.name} needs confirmation: $description")
        return onNeedConfirm(description)
    }
}
