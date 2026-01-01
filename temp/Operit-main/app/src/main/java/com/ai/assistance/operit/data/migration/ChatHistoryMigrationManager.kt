package com.ai.assistance.operit.data.migration

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// 保留对旧数据存储的访问
private val Context.chatHistoryDataStore by preferencesDataStore(name = "chat_histories")

// 保存迁移版本号的DataStore
private val Context.migrationVersionDataStore by preferencesDataStore(name = "migration_version")

/** 聊天历史数据迁移管理器 用于从旧版本的DataStore迁移数据到新的Room数据库 */
class ChatHistoryMigrationManager(private val context: Context) {
    private val TAG = "ChatHistoryMigration"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    // 当前支持的迁移版本
    private val CURRENT_MIGRATION_VERSION = 1

    // 旧版DataStore的键
    private object OldPreferencesKeys {
        val CHAT_HISTORIES = stringPreferencesKey("chat_histories")
    }

    // 迁移版本DataStore的键
    private object MigrationKeys {
        val VERSION = stringPreferencesKey("version")
    }

    /**
     * 检查是否需要迁移数据
     * @return 如果需要迁移则返回true，否则返回false
     */
    suspend fun needsMigration(): Boolean {
        // 检查迁移版本
        val migrationVersion = getMigrationVersion()
        if (migrationVersion != null &&
                        migrationVersion.toIntOrNull() ?: 0 >= CURRENT_MIGRATION_VERSION
        ) {
            AppLogger.d(TAG, "数据已迁移到版本 $migrationVersion，不需要迁移")
            return false
        }

        // 检查旧数据是否存在
        val hasOldData = checkOldDataExists()
        AppLogger.d(TAG, "旧版数据存在: $hasOldData")

        return hasOldData
    }

    /** 检查旧数据是否存在 */
    private suspend fun checkOldDataExists(): Boolean {
        val historiesJson =
                context.chatHistoryDataStore
                        .data
                        .catch { exception ->
                            if (exception is IOException) {
                                emit(emptyPreferences())
                            } else {
                                throw exception
                            }
                        }
                        .map { preferences ->
                            preferences[OldPreferencesKeys.CHAT_HISTORIES] ?: "[]"
                        }
                        .first()

        if (historiesJson == "[]") {
            return false
        }

        try {
            val histories = json.decodeFromString<List<ChatHistory>>(historiesJson)
            return histories.isNotEmpty()
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查旧数据失败", e)
            return false
        }
    }

    /** 获取当前迁移版本 */
    private suspend fun getMigrationVersion(): String? {
        return context.migrationVersionDataStore
                .data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map { preferences -> preferences[MigrationKeys.VERSION] }
                .first()
    }

    /**
     * 执行数据迁移
     * @return 迁移的聊天记录数量，如果失败则返回-1
     */
    suspend fun migrateData(): Int {
        val chatHistoryManager = ChatHistoryManager.getInstance(context)
        var migratedCount = 0

        try {
            // 从旧的DataStore读取聊天历史
            val historiesJson =
                    context.chatHistoryDataStore
                            .data
                            .map { preferences ->
                                preferences[OldPreferencesKeys.CHAT_HISTORIES] ?: "[]"
                            }
                            .first()

            // 解析JSON
            val histories = json.decodeFromString<List<ChatHistory>>(historiesJson)

            if (histories.isEmpty()) {
                AppLogger.d(TAG, "没有找到需要迁移的聊天记录")
                return 0
            }

            AppLogger.d(TAG, "开始迁移 ${histories.size} 条聊天记录")

            // 将每个聊天历史保存到新的Room数据库
            for (history in histories) {
                chatHistoryManager.saveChatHistory(history)
                migratedCount++
            }

            // 更新迁移版本号
            updateMigrationVersion(CURRENT_MIGRATION_VERSION)

            // 创建迁移完成标记文件
            createMigrationCompletedFile(migratedCount)

            AppLogger.d(TAG, "成功迁移 $migratedCount 条聊天记录")
            return migratedCount
        } catch (e: Exception) {
            AppLogger.e(TAG, "迁移聊天历史失败", e)
            return -1
        }
    }

    /**
     * 导出旧版聊天记录到外部文件
     * @return 导出文件路径，如果失败则返回null
     */
    suspend fun exportOldChatHistory(): String? {
        try {
            // 从旧的DataStore读取聊天历史
            val historiesJson =
                    context.chatHistoryDataStore
                            .data
                            .map { preferences ->
                                preferences[OldPreferencesKeys.CHAT_HISTORIES] ?: "[]"
                            }
                            .first()

            // 如果没有数据，返回null
            if (historiesJson == "[]") {
                AppLogger.d(TAG, "没有找到需要导出的聊天记录")
                return null
            }

            // 创建备份目录
            val backupDir = File(context.getExternalFilesDir(null), "backup")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // 创建带时间戳的备份文件
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val backupFile = File(backupDir, "chat_backup_$timestamp.json")

            // 写入数据到备份文件
            backupFile.writeText(historiesJson)

            AppLogger.d(TAG, "成功导出聊天记录到: ${backupFile.absolutePath}")
            return backupFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "导出聊天记录失败", e)
            return null
        }
    }

    /**
     * 从外部文件导入聊天记录到数据库
     * @param filePath 备份文件路径
     * @return 导入的聊天记录数量，如果失败则返回-1
     */
    suspend fun importChatHistoryFromBackup(filePath: String): Int {
        val chatHistoryManager = ChatHistoryManager.getInstance(context)
        var importedCount = 0

        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                AppLogger.e(TAG, "备份文件不存在或无法读取: $filePath")
                return -1
            }

            // 读取备份文件内容
            val historiesJson = file.readText()

            // 解析JSON
            val histories = json.decodeFromString<List<ChatHistory>>(historiesJson)

            if (histories.isEmpty()) {
                AppLogger.d(TAG, "备份文件中没有聊天记录")
                return 0
            }

            AppLogger.d(TAG, "开始导入 ${histories.size} 条聊天记录")

            // 将每个聊天历史保存到Room数据库
            for (history in histories) {
                chatHistoryManager.saveChatHistory(history)
                importedCount++
            }

            // 更新迁移版本号
            updateMigrationVersion(CURRENT_MIGRATION_VERSION)

            // 创建导入完成标记文件
            val migrationDir = File(context.filesDir, "migration")
            if (!migrationDir.exists()) {
                migrationDir.mkdirs()
            }

            val importFile = File(migrationDir, "import_info.txt")
            importFile.writeText(
                    """
                聊天记录导入信息
                导入时间：${System.currentTimeMillis()}
                导入记录数：$importedCount
                导入文件：$filePath
            """.trimIndent()
            )

            AppLogger.d(TAG, "成功导入 $importedCount 条聊天记录")
            return importedCount
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入聊天记录失败", e)
            return -1
        }
    }

    /** 更新迁移版本号 */
    private suspend fun updateMigrationVersion(version: Int) {
        context.migrationVersionDataStore.edit { preferences ->
            preferences[MigrationKeys.VERSION] = version.toString()
        }
    }

    /** 创建迁移完成标记文件 */
    private fun createMigrationCompletedFile(migratedCount: Int) {
        try {
            val migrationDir = File(context.filesDir, "migration")
            if (!migrationDir.exists()) {
                migrationDir.mkdirs()
            }

            val migrationFile = File(migrationDir, "migration_info.txt")
            migrationFile.writeText(
                    """
                聊天记录迁移信息
                迁移版本：$CURRENT_MIGRATION_VERSION
                迁移时间：${System.currentTimeMillis()}
                迁移记录数：$migratedCount
            """.trimIndent()
            )

            AppLogger.d(TAG, "已创建迁移完成标记文件: ${migrationFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建迁移标记文件失败", e)
        }
    }
}
