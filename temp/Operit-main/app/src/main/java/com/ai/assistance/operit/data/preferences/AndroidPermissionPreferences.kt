package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.androidPermissionDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "android_permission_preferences")

/** 全局单例实例 */
lateinit var androidPermissionPreferences: AndroidPermissionPreferences
    private set

/** 初始化Android权限偏好管理器 */
fun initAndroidPermissionPreferences(context: Context) {
    androidPermissionPreferences = AndroidPermissionPreferences(context)
}

/** Android权限偏好管理器 负责管理应用全局的权限级别偏好设置 */
class AndroidPermissionPreferences(private val context: Context) {
    companion object {
        private const val TAG = "AndroidPermissionPrefs"

        // 权限相关键
        private val PREFERRED_PERMISSION_LEVEL = stringPreferencesKey("preferred_permission_level")
    }

    /** 首选权限级别Flow 返回用户配置的首选Android权限级别，如果未设置则返回null */
    val preferredPermissionLevelFlow: Flow<AndroidPermissionLevel?> =
            context.androidPermissionDataStore.data.map { preferences ->
                val levelString = preferences[PREFERRED_PERMISSION_LEVEL]
                if (levelString != null) AndroidPermissionLevel.fromString(levelString) else null
            }

    /**
     * 保存首选权限级别
     * @param permissionLevel 要设置的权限级别
     */
    suspend fun savePreferredPermissionLevel(permissionLevel: AndroidPermissionLevel) {
        AppLogger.d(TAG, "Saving preferred permission level: $permissionLevel")
        context.androidPermissionDataStore.edit { preferences ->
            preferences[PREFERRED_PERMISSION_LEVEL] = permissionLevel.name
        }
    }

    /**
     * 获取当前首选的权限级别 这是一个阻塞调用，应在非UI线程使用或谨慎使用
     * @return 当前配置的首选权限级别，如果未设置则返回null
     */
    fun getPreferredPermissionLevel(): AndroidPermissionLevel? {
        return runBlocking {
            try {
                preferredPermissionLevelFlow.first()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting preferred permission level", e)
                null
            }
        }
    }

    /**
     * 检查是否已设置权限级别
     * @return 是否已设置权限级别
     */
    fun isPermissionLevelSet(): Boolean {
        return runBlocking {
            try {
                preferredPermissionLevelFlow.first() != null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking if permission level is set", e)
                false
            }
        }
    }

    /** 重置权限级别（清除设置） */
    suspend fun resetPermissionLevel() {
        AppLogger.d(TAG, "Resetting permission level")
        context.androidPermissionDataStore.edit { preferences ->
            preferences.remove(PREFERRED_PERMISSION_LEVEL)
        }
    }
}
