package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Shell命令管理器 - 负责命令执行、历史记录管理等
 */
class ShellCommandManager(private val context: Context) {
    
    private val PREFS_NAME = "shell_executor_prefs"
    private val KEY_COMMAND_HISTORY = "command_history"
    private val MAX_HISTORY_SIZE = 100
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 获取预设命令列表
     */
    fun getPresetCommands(): List<PresetCommand> {
        return listOf(
            PresetCommand(
                name = "测试命令",
                command = "echo \"命令执行器测试成功\"",
                description = "一个简单的测试命令",
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Check
            ),
            PresetCommand(
                name = "系统信息",
                command = "uname -a",
                description = "显示系统内核信息",
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Info
            ),
            PresetCommand(
                name = "磁盘使用情况",
                command = "df -h",
                description = "显示磁盘空间使用情况",
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Storage
            ),
            PresetCommand(
                name = "内存信息",
                command = "cat /proc/meminfo",
                description = "显示内存使用详情",
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.Memory
            ),
            PresetCommand(
                name = "CPU信息",
                command = "cat /proc/cpuinfo",
                description = "显示CPU详细信息",
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.SettingsApplications
            ),
            PresetCommand(
                name = "网络接口",
                command = "ip addr",
                description = "显示网络接口信息",
                category = CommandCategory.NETWORK,
                icon = Icons.Default.Wifi
            ),
            PresetCommand(
                name = "路由表",
                command = "ip route",
                description = "显示路由表信息",
                category = CommandCategory.NETWORK,
                icon = Icons.Default.Router
            ),
            PresetCommand(
                name = "网络连接",
                command = "netstat -tunlp",
                description = "显示网络连接状态",
                category = CommandCategory.NETWORK,
                icon = Icons.Default.NetworkCheck
            ),
            PresetCommand(
                name = "已安装应用",
                command = "pm list packages",
                description = "列出所有已安装的应用包名",
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.Apps
            ),
            PresetCommand(
                name = "系统应用",
                command = "pm list packages -s",
                description = "列出所有系统应用",
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.Android
            ),
            PresetCommand(
                name = "第三方应用",
                command = "pm list packages -3",
                description = "列出所有第三方应用",
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.AppShortcut
            ),
            PresetCommand(
                name = "当前目录",
                command = "ls -la",
                description = "列出当前目录下所有文件",
                category = CommandCategory.FILE,
                icon = Icons.Default.Folder
            ),
            PresetCommand(
                name = "根目录",
                command = "ls -la /",
                description = "列出根目录下所有文件",
                category = CommandCategory.FILE,
                icon = Icons.Default.FolderOpen
            ),
            PresetCommand(
                name = "存储空间",
                command = "ls -la /sdcard/",
                description = "列出外部存储空间文件",
                category = CommandCategory.FILE,
                icon = Icons.Default.SdCard
            ),
            PresetCommand(
                name = "进程列表",
                command = "ps -ef",
                description = "显示系统进程列表",
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.ViewList
            ),
            PresetCommand(
                name = "系统属性",
                command = "getprop",
                description = "显示全部系统属性",
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Settings
            )
        )
    }
    
    /**
     * 添加自定义预设命令
     */
    suspend fun addCustomPresetCommand(command: PresetCommand) {
        // 实现保存自定义预设命令的逻辑
    }
    
    /**
     * 获取命令历史记录
     */
    fun getCommandHistory(): List<CommandRecord> {
        val historyJson = prefs.getString(KEY_COMMAND_HISTORY, null) ?: return emptyList()
        return try {
            // 在实际实现中，使用JSON解析库如Gson或Moshi来解析历史记录
            // 此处为简化，返回空列表
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 执行Shell命令
     */
    suspend fun executeCommand(command: String): CommandRecord {
        val result = withContext(Dispatchers.IO) {
            AndroidShellExecutor.executeShellCommand(command)
        }
        
        val record = CommandRecord(
            command = command,
            result = result,
            timestamp = System.currentTimeMillis()
        )
        
        // 保存到历史记录
        saveCommandToHistory(record)
        
        return record
    }
    
    /**
     * 保存命令到历史记录
     */
    private fun saveCommandToHistory(record: CommandRecord) {
        val history = getCommandHistory().toMutableList()
        
        // 如果已存在相同命令，移除旧记录
        history.removeAll { it.command == record.command }
        
        // 添加新记录到列表头部
        history.add(0, record)
        
        // 限制历史记录大小
        val trimmedHistory = history.take(MAX_HISTORY_SIZE)
        
        // 保存到SharedPreferences
        // 在实际实现中，使用JSON序列化库将历史记录转换为JSON字符串
        // prefs.edit().putString(KEY_COMMAND_HISTORY, jsonString).apply()
    }
    
    /**
     * 清除命令历史
     */
    fun clearCommandHistory() {
        prefs.edit().remove(KEY_COMMAND_HISTORY).apply()
    }
    
    /**
     * 从历史记录中移除指定命令
     */
    fun removeCommandFromHistory(command: String) {
        val history = getCommandHistory().toMutableList()
        history.removeAll { it.command == command }
        
        // 保存到SharedPreferences
        // 在实际实现中，使用JSON序列化库将历史记录转换为JSON字符串
        // prefs.edit().putString(KEY_COMMAND_HISTORY, jsonString).apply()
    }
    
    /**
     * 获取建议的命令列表（基于历史记录和输入的前缀）
     */
    fun getSuggestedCommands(prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        
        val history = getCommandHistory()
        return history
            .map { it.command }
            .distinct()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .take(5)
    }
} 