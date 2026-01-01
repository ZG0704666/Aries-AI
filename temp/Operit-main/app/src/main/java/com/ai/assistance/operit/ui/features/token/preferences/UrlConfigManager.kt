package com.ai.assistance.operit.ui.features.token.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.ui.features.token.model.TabConfig
import com.ai.assistance.operit.ui.features.token.model.UrlConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.urlConfigDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "url_config")

class UrlConfigManager(private val context: Context) {
    companion object {
        private val URL_CONFIG_KEY = stringPreferencesKey("url_config")
        
        // 预设配置
        val PRESET_CONFIGS = listOf(
            UrlConfig(
                name = "Claude",
                signInUrl = "https://claude.ai/login",
                tabs = listOf(
                    TabConfig("聊天", "https://claude.ai/chats"),
                    TabConfig("项目", "https://claude.ai/projects"),
                    TabConfig("工件", "https://claude.ai/artifacts"),
                    TabConfig("设置", "https://claude.ai/settings")
                )
            ),
            UrlConfig(
                name = "ChatGPT",
                signInUrl = "https://chat.openai.com/auth/login",
                tabs = listOf(
                    TabConfig("聊天", "https://chat.openai.com/"),
                    TabConfig("GPTs", "https://chat.openai.com/gpts"),
                    TabConfig("设置", "https://chat.openai.com/settings"),
                    TabConfig("账户", "https://platform.openai.com/account")
                )
            ),
            UrlConfig(
                name = "Gemini",
                signInUrl = "https://gemini.google.com/",
                tabs = listOf(
                    TabConfig("聊天", "https://gemini.google.com/app"),
                    TabConfig("历史", "https://gemini.google.com/history"),
                    TabConfig("设置", "https://gemini.google.com/settings"),
                    TabConfig("帮助", "https://support.google.com/gemini")
                )
            ),
            UrlConfig(
                name = "Poe",
                signInUrl = "https://poe.com/login",
                tabs = listOf(
                    TabConfig("聊天", "https://poe.com/"),
                    TabConfig("探索", "https://poe.com/explore"),
                    TabConfig("创建", "https://poe.com/create"),
                    TabConfig("设置", "https://poe.com/settings")
                )
            )
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 获取URL配置的Flow
    val urlConfigFlow: Flow<UrlConfig> = context.urlConfigDataStore.data.map { preferences ->
        val configJson = preferences[URL_CONFIG_KEY]
        if (configJson != null) {
            try {
                json.decodeFromString<UrlConfig>(configJson)
            } catch (e: Exception) {
                UrlConfig() // 返回默认配置
            }
        } else {
            UrlConfig() // 返回默认配置
        }
    }

    // 保存URL配置
    suspend fun saveUrlConfig(urlConfig: UrlConfig) {
        context.urlConfigDataStore.edit { preferences ->
            preferences[URL_CONFIG_KEY] = json.encodeToString(urlConfig)
        }
    }

    // 重置为默认配置
    suspend fun resetToDefault() {
        saveUrlConfig(UrlConfig())
    }
} 