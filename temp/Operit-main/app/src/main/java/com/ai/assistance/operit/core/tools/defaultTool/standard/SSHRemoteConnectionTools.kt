package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager

/**
 * SSH远程文件连接工具
 * 
 * 提供简单的SSH登录/退出功能
 * 登录后，所有文件系统工具将使用SSH连接
 * 
 * 1. ssh_login - 登录SSH远程服务器
 * 2. ssh_exit - 退出SSH连接
 */
class SSHRemoteConnectionTools(private val context: Context) {
    
    companion object {
        private const val TAG = "SSHRemoteConnTools"
        private const val DEFAULT_CONNECTION_ID = "ssh_remote_files"
    }
    
    private val sshFileManager = SSHFileConnectionManager.getInstance(context)
    
    /**
     * 登录SSH远程服务器
     * 
     * 参数:
     * - host: SSH服务器地址 (必填)
     * - port: SSH端口 (可选，默认22)
     * - username: 用户名 (必填)
     * - password: 密码 (必填)
     * - enable_reverse_mount: 是否启用反向挂载 (可选，默认false)
     * 
     * 登录后，所有文件工具的environment="linux"将使用此SSH连接
     */
    suspend fun sshLogin(tool: AITool): ToolResult {
        val params = tool.parameters.associate { it.name to it.value }
        
        // 必填参数
        val host = params["host"]
        val username = params["username"]
        
        if (host.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: host"
            )
        }
        
        if (username.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: username"
            )
        }
        
        // 可选参数
        val port = params["port"]?.toIntOrNull() ?: 22
        val enableReverseMount = params["enable_reverse_mount"]?.toBoolean() ?: false
        val password = params["password"]
        
        if (password.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: password"
            )
        }
        
        return try {
            AppLogger.d(TAG, "Connecting to SSH: $username@$host:$port")
            
            // 先断开已有连接
            sshFileManager.disconnect(DEFAULT_CONNECTION_ID)
            
            // 构建连接参数（使用固定ID）
            val connectionParams = SSHFileConnectionManager.ConnectionParams(
                host = host,
                port = port,
                username = username,
                password = password,
                connectionId = DEFAULT_CONNECTION_ID,
                // 纯文件操作，默认不启用额外功能
                enablePortForwarding = false,
                enableReverseTunnel = enableReverseMount
            )
            
            // 建立连接
            val result = sshFileManager.connect(connectionParams)
            
            if (result.isSuccess) {
                AppLogger.d(TAG, "SSH login successful")
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(
                        """SSH login successful!
                        |
                        |Connected to: $username@$host:$port
                        |
                        |All file tools with environment="linux" will now use this SSH connection.
                        |Use ssh_exit to logout when done.
                        """.trimMargin()
                    )
                )
            } else {
                val error = result.exceptionOrNull()
                error?.let { ex ->
                    AppLogger.e(TAG, "SSH connection failed", ex)
                } ?: AppLogger.e(TAG, "SSH connection failed with unknown error")
                
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to connect to SSH server: ${error?.message ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "SSH connection error", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "SSH connection error: ${e.message}"
            )
        }
    }
    
    /**
     * 退出SSH连接
     * 
     * 退出后，文件工具将恢复使用本地文件系统
     */
    suspend fun sshExit(tool: AITool): ToolResult {
        return try {
            AppLogger.d(TAG, "Logging out from SSH")
            
            val result = sshFileManager.disconnect(DEFAULT_CONNECTION_ID)
            
            if (result.isSuccess) {
                AppLogger.d(TAG, "SSH logout successful")
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(
                        """SSH logout successful!
                        |
                        |File tools will now use local file system.
                        """.trimMargin()
                    )
                )
            } else {
                // 即使失败也返回成功（可能本来就没连接）
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("SSH logout completed (no active connection)")
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "SSH logout error", e)
            // 即使出错也返回成功
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("SSH logout completed")
            )
        }
    }
}
