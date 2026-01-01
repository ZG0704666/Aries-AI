package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 向后兼容的Shell命令执行工具类 通过权限级别委托到相应的Shell执行器 */
class AndroidShellExecutor {
    companion object {
        private const val TAG = "AndroidShellExecutor"
        private var context: Context? = null

        /**
         * 设置全局上下文引用
         * @param appContext 应用上下文
         */
        fun setContext(appContext: Context) {
            context = appContext.applicationContext
        }
        /**
         * 封装执行命令的函数
         * @param command 要执行的命令
         * @return 命令执行结果
         */
        suspend fun executeShellCommand(command: String): CommandResult {
            return executeShellCommand(command, null)
        }

        suspend fun executeShellCommand(command: String, identityOverride: ShellIdentity?): CommandResult {
            val ctx = context ?: return CommandResult(false, "", "Context not initialized")

            // 如果调用方显式指定了身份，就直接向下传递；否则使用默认身份
            val identity = identityOverride ?: ShellIdentity.DEFAULT

            val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
            AppLogger.d(TAG, "Using preferred permission level: $preferredLevel, identity=$identity")

            val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD

            val preferredExecutor = ShellExecutorFactory.getExecutor(ctx, actualLevel)
            val permStatus = preferredExecutor.hasPermission()

            if (preferredExecutor.isAvailable() && permStatus.granted) {
                val result = preferredExecutor.executeCommand(command, identity)
                return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
            }

            AppLogger.d(
                TAG,
                "Preferred executor not available (${permStatus.reason}), trying highest available executor"
            )

            val (executor, executorStatus) = ShellExecutorFactory.getHighestAvailableExecutor(ctx)

            if (!executorStatus.granted) {
                return CommandResult(
                    false,
                    "",
                    "No suitable shell executor available: ${executorStatus.reason}",
                    -1
                )
            }

            AppLogger.d(TAG, "Using executor with permission level: ${executor.getPermissionLevel()}")

            val result = executor.executeCommand(command, identity)
            return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
        }

        suspend fun startShellProcess(command: String): ShellProcess {
            val ctx = context ?: throw IllegalStateException("Context not initialized")

            val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
            AppLogger.d(TAG, "Starting process with preferred permission level: $preferredLevel")

            val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD
            val preferredExecutor = ShellExecutorFactory.getExecutor(ctx, actualLevel)
            val permStatus = preferredExecutor.hasPermission()

            if (preferredExecutor.isAvailable() && permStatus.granted) {
                return preferredExecutor.startProcess(command)
            }

            AppLogger.d(
                TAG,
                "Preferred executor not available (${permStatus.reason}), trying highest available executor"
            )

            val (executor, executorStatus) = ShellExecutorFactory.getHighestAvailableExecutor(ctx)

            if (!executorStatus.granted) {
                throw SecurityException(
                    "No suitable shell executor available: ${executorStatus.reason}"
                )
            }

            AppLogger.d(TAG, "Starting process with permission level: ${executor.getPermissionLevel()}")
            return executor.startProcess(command)
        }
    }

    /** 命令执行结果数据类 */
    data class CommandResult(
            val success: Boolean,
            val stdout: String,
            val stderr: String = "",
            val exitCode: Int = -1
    )
}

enum class ShellIdentity {
    DEFAULT,
    APP,
    ROOT,
    SHELL
}
