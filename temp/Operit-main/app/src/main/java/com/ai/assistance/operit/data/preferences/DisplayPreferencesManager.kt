package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// DataStore 实例
private val Context.displayPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "display_preferences"
)

/**
 * DisplayPreferencesManager
 * 管理消息显示相关的偏好设置
 * 这些设置独立于角色卡和主题系统
 * 使用单例模式，避免重复创建实例
 */
class DisplayPreferencesManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DisplayPreferencesManager? = null

        fun getInstance(context: Context): DisplayPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DisplayPreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // 消息显示设置的 Key
        private val KEY_SHOW_MODEL_PROVIDER = booleanPreferencesKey("show_model_provider")
        private val KEY_SHOW_MODEL_NAME = booleanPreferencesKey("show_model_name")
        private val KEY_SHOW_ROLE_NAME = booleanPreferencesKey("show_role_name")
        private val KEY_SHOW_USER_NAME = booleanPreferencesKey("show_user_name")
        
        // 全局用户设置的 Key
        private val KEY_GLOBAL_USER_AVATAR_URI = stringPreferencesKey("global_user_avatar_uri")
        private val KEY_GLOBAL_USER_NAME = stringPreferencesKey("global_user_name")
        
        // 显示相关设置的 Key
        private val KEY_SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        private val KEY_ENABLE_REPLY_NOTIFICATION = booleanPreferencesKey("enable_reply_notification")

        // 自动化显示与行为相关设置的 Key
        private val KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY =
            booleanPreferencesKey("enable_experimental_virtual_display")

        private val KEY_SCREENSHOT_FORMAT = stringPreferencesKey("screenshot_format")
        private val KEY_SCREENSHOT_QUALITY = intPreferencesKey("screenshot_quality")
        private val KEY_SCREENSHOT_SCALE_PERCENT = intPreferencesKey("screenshot_scale_percent")

        // 虚拟屏幕相关设置的 Key
        private val KEY_VIRTUAL_DISPLAY_BITRATE_KBPS = intPreferencesKey("virtual_display_bitrate_kbps")
    }

    /**
     * 是否显示模型供应商
     * 默认值：false
     */
    val showModelProvider: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MODEL_PROVIDER] ?: false
        }

    /**
     * 是否显示模型名称
     * 默认值：false
     */
    val showModelName: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MODEL_NAME] ?: false
        }

    /**
     * 是否显示角色卡名称
     * 默认值：false
     */
    val showRoleName: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_ROLE_NAME] ?: false
        }

    /**
     * 是否显示用户名字
     * 默认值：false
     */
    val showUserName: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_USER_NAME] ?: false
        }

    /**
     * 全局用户头像URI
     */
    val globalUserAvatarUri: Flow<String?> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_GLOBAL_USER_AVATAR_URI]
        }

    /**
     * 全局用户名称
     */
    val globalUserName: Flow<String?> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_GLOBAL_USER_NAME]
        }

    /**
     * 是否显示FPS计数器
     * 默认值：false
     */
    val showFpsCounter: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_FPS_COUNTER] ?: false
        }

    /**
     * 是否启用回复通知
     * 默认值：true
     */
    val enableReplyNotification: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_REPLY_NOTIFICATION] ?: true
        }

    val enableExperimentalVirtualDisplay: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] ?: true
        }

    val screenshotFormat: Flow<String> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_FORMAT] ?: "PNG"
        }

    val screenshotQuality: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_QUALITY] ?: 90
        }

    val screenshotScalePercent: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_SCALE_PERCENT] ?: 100
        }

    val virtualDisplayBitrateKbps: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_VIRTUAL_DISPLAY_BITRATE_KBPS] ?: 3000
        }

    /**
     * 保存显示设置
     */
    suspend fun saveDisplaySettings(
        showModelProvider: Boolean? = null,
        showModelName: Boolean? = null,
        showRoleName: Boolean? = null,
        showUserName: Boolean? = null,
        globalUserAvatarUri: String? = null,
        globalUserName: String? = null,
        showFpsCounter: Boolean? = null,
        enableReplyNotification: Boolean? = null,
        enableExperimentalVirtualDisplay: Boolean? = null,
        screenshotFormat: String? = null,
        screenshotQuality: Int? = null,
        screenshotScalePercent: Int? = null,
        virtualDisplayBitrateKbps: Int? = null
    ) {
        context.displayPreferencesDataStore.edit { preferences ->
            showModelProvider?.let { preferences[KEY_SHOW_MODEL_PROVIDER] = it }
            showModelName?.let { preferences[KEY_SHOW_MODEL_NAME] = it }
            showRoleName?.let { preferences[KEY_SHOW_ROLE_NAME] = it }
            showUserName?.let { preferences[KEY_SHOW_USER_NAME] = it }
            globalUserAvatarUri?.let { preferences[KEY_GLOBAL_USER_AVATAR_URI] = it }
            globalUserName?.let { preferences[KEY_GLOBAL_USER_NAME] = it }
            showFpsCounter?.let { preferences[KEY_SHOW_FPS_COUNTER] = it }
            enableReplyNotification?.let { preferences[KEY_ENABLE_REPLY_NOTIFICATION] = it }
            enableExperimentalVirtualDisplay?.let {
                preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] = it
            }
            screenshotFormat?.let { preferences[KEY_SCREENSHOT_FORMAT] = it }
            screenshotQuality?.let { preferences[KEY_SCREENSHOT_QUALITY] = it }
            screenshotScalePercent?.let { preferences[KEY_SCREENSHOT_SCALE_PERCENT] = it }
            virtualDisplayBitrateKbps?.let { preferences[KEY_VIRTUAL_DISPLAY_BITRATE_KBPS] = it }
        }
    }

    fun isExperimentalVirtualDisplayEnabled(): Boolean {
        return runBlocking {
            enableExperimentalVirtualDisplay.first()
        }
    }

    fun getScreenshotFormat(): String {
        return runBlocking {
            screenshotFormat.first()
        }
    }

    fun getScreenshotQuality(): Int {
        return runBlocking {
            screenshotQuality.first()
        }
    }

    fun getScreenshotScalePercent(): Int {
        return runBlocking {
            screenshotScalePercent.first()
        }
    }

    fun getVirtualDisplayBitrateKbps(): Int {
        return runBlocking {
            virtualDisplayBitrateKbps.first()
        }
    }

    /**
     * 重置所有显示设置为默认值
     */
    suspend fun resetDisplaySettings() {
        context.displayPreferencesDataStore.edit { preferences ->
            preferences[KEY_SHOW_MODEL_PROVIDER] = false
            preferences[KEY_SHOW_MODEL_NAME] = false
            preferences[KEY_SHOW_ROLE_NAME] = false
            preferences[KEY_SHOW_USER_NAME] = false
            preferences.remove(KEY_GLOBAL_USER_AVATAR_URI)
            preferences.remove(KEY_GLOBAL_USER_NAME)
            preferences[KEY_SHOW_FPS_COUNTER] = false
            preferences[KEY_ENABLE_REPLY_NOTIFICATION] = true
            preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] = true
            preferences.remove(KEY_SCREENSHOT_FORMAT)
            preferences.remove(KEY_SCREENSHOT_QUALITY)
            preferences.remove(KEY_SCREENSHOT_SCALE_PERCENT)
            preferences.remove(KEY_VIRTUAL_DISPLAY_BITRATE_KBPS)
        }
    }
}
