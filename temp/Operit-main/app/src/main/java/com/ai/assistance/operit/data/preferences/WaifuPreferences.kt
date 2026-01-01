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

private val Context.waifuDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "waifu_settings")

class WaifuPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: WaifuPreferences? = null

        fun getInstance(context: Context): WaifuPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = WaifuPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Keys for Waifu Mode
        val ENABLE_WAIFU_MODE = booleanPreferencesKey("enable_waifu_mode")
        val WAIFU_CHAR_DELAY = intPreferencesKey("waifu_char_delay") // 每字符延迟（毫秒）
        val WAIFU_REMOVE_PUNCTUATION = booleanPreferencesKey("waifu_remove_punctuation") // 是否移除标点符号
        val WAIFU_DISABLE_ACTIONS = booleanPreferencesKey("waifu_disable_actions") // 是否禁止动作表情
        val WAIFU_ENABLE_EMOTICONS = booleanPreferencesKey("waifu_enable_emoticons") // 是否启用表情包
        val WAIFU_ENABLE_SELFIE = booleanPreferencesKey("waifu_enable_selfie") // 是否启用自拍功能
        val WAIFU_SELFIE_PROMPT = stringPreferencesKey("waifu_selfie_prompt") // 自拍功能的外貌提示词

        // Default value for Waifu Mode
        const val DEFAULT_ENABLE_WAIFU_MODE = false
        const val DEFAULT_WAIFU_CHAR_DELAY = 500 // 500ms per character (2 chars per second)
        const val DEFAULT_WAIFU_REMOVE_PUNCTUATION = false // 默认保留标点符号
        const val DEFAULT_WAIFU_DISABLE_ACTIONS = false // 默认允许动作表情
        const val DEFAULT_WAIFU_ENABLE_EMOTICONS = false // 默认不启用表情包
        const val DEFAULT_WAIFU_ENABLE_SELFIE = false // 默认不启用自拍功能
        const val DEFAULT_WAIFU_SELFIE_PROMPT = "kipfel vrchat, long hair, Matcha color hair, purple eyes, sweater vest, black skirt, black necktie, collared shirt, long sleeves, black headwear, beanie, pleated skirt, hair bun, white shirt, hair ribbon, hairclip, hair between eyes, black footwear, blush, hair ornament, cat hat, very long hair, sweater, animal ear headwear, bag, bandaid on leg, socks" // 默认外貌提示词
    }

    // Flow for Waifu Mode
    val enableWaifuModeFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[ENABLE_WAIFU_MODE] ?: DEFAULT_ENABLE_WAIFU_MODE
        }

    val waifuCharDelayFlow: Flow<Int> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_CHAR_DELAY] ?: DEFAULT_WAIFU_CHAR_DELAY
        }

    val waifuRemovePunctuationFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] ?: DEFAULT_WAIFU_REMOVE_PUNCTUATION
        }

    val waifuDisableActionsFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_DISABLE_ACTIONS] ?: DEFAULT_WAIFU_DISABLE_ACTIONS
        }

    val waifuEnableEmoticonsFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] ?: DEFAULT_WAIFU_ENABLE_EMOTICONS
        }

    val waifuEnableSelfieFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] ?: DEFAULT_WAIFU_ENABLE_SELFIE
        }

    val waifuSelfiePromptFlow: Flow<String> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] ?: DEFAULT_WAIFU_SELFIE_PROMPT
        }

    // Save Waifu Mode setting
    suspend fun saveEnableWaifuMode(isEnabled: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[ENABLE_WAIFU_MODE] = isEnabled
        }
    }

    suspend fun saveWaifuCharDelay(delayMs: Int) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_CHAR_DELAY] = delayMs
        }
    }

    suspend fun saveWaifuRemovePunctuation(removePunctuation: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] = removePunctuation
        }
    }

    suspend fun saveWaifuDisableActions(disableActions: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_DISABLE_ACTIONS] = disableActions
        }
    }

    suspend fun saveWaifuEnableEmoticons(enableEmoticons: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] = enableEmoticons
        }
    }

    suspend fun saveWaifuEnableSelfie(enableSelfie: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] = enableSelfie
        }
    }

    suspend fun saveWaifuSelfiePrompt(prompt: String) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] = prompt
        }
    }

    // ========== Waifu模式角色卡绑定功能 ==========

    private fun getCharacterCardWaifuPrefix(characterCardId: String): String {
        return "character_card_waifu_${characterCardId}_"
    }

    private fun getAllBooleanWaifuKeys(): List<Preferences.Key<Boolean>> {
        return listOf(
            ENABLE_WAIFU_MODE,
            WAIFU_REMOVE_PUNCTUATION,
            WAIFU_DISABLE_ACTIONS,
            WAIFU_ENABLE_EMOTICONS,
            WAIFU_ENABLE_SELFIE
        )
    }

    private fun getAllIntWaifuKeys(): List<Preferences.Key<Int>> {
        return listOf(
            WAIFU_CHAR_DELAY
        )
    }

    private fun getAllStringWaifuKeys(): List<Preferences.Key<String>> {
        return listOf(
            WAIFU_SELFIE_PROMPT
        )
    }

    /**
     * 将当前Waifu模式配置复制到指定角色卡
     */
    suspend fun copyCurrentWaifuSettingsToCharacterCard(characterCardId: String) {
        context.waifuDataStore.edit { preferences ->
            val prefix = getCharacterCardWaifuPrefix(characterCardId)

            getAllBooleanWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[booleanPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllIntWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[intPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllStringWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[stringPreferencesKey("${prefix}${key.name}")] = value
                }
            }
        }
    }

    suspend fun cloneWaifuSettingsBetweenCharacterCards(sourceCharacterCardId: String, targetCharacterCardId: String) {
        context.waifuDataStore.edit { preferences ->
            val sourcePrefix = getCharacterCardWaifuPrefix(sourceCharacterCardId)
            val targetPrefix = getCharacterCardWaifuPrefix(targetCharacterCardId)

            getAllBooleanWaifuKeys().forEach { key ->
                val sourceKey = booleanPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = booleanPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllIntWaifuKeys().forEach { key ->
                val sourceKey = intPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = intPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllStringWaifuKeys().forEach { key ->
                val sourceKey = stringPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = stringPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }
        }
    }

    /**
     * 切换到指定角色卡的Waifu模式配置
     */
    suspend fun switchToCharacterCardWaifuSettings(characterCardId: String) {
        context.waifuDataStore.edit { preferences ->
            val prefix = getCharacterCardWaifuPrefix(characterCardId)

            getAllBooleanWaifuKeys().forEach { key ->
                val cardKey = booleanPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllIntWaifuKeys().forEach { key ->
                val cardKey = intPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllStringWaifuKeys().forEach { key ->
                val cardKey = stringPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
        }
    }

    /**
     * 保存当前Waifu配置到指定角色卡
     */
    suspend fun saveCurrentWaifuSettingsToCharacterCard(characterCardId: String) {
        copyCurrentWaifuSettingsToCharacterCard(characterCardId)
    }

    /**
     * 删除指定角色卡的Waifu配置
     */
    suspend fun deleteCharacterCardWaifuSettings(characterCardId: String) {
        context.waifuDataStore.edit { preferences ->
            val prefix = getCharacterCardWaifuPrefix(characterCardId)

            getAllBooleanWaifuKeys().forEach { key ->
                preferences.remove(booleanPreferencesKey("${prefix}${key.name}"))
            }
            getAllIntWaifuKeys().forEach { key ->
                preferences.remove(intPreferencesKey("${prefix}${key.name}"))
            }
            getAllStringWaifuKeys().forEach { key ->
                preferences.remove(stringPreferencesKey("${prefix}${key.name}"))
            }
        }
    }

    /**
     * 检查指定角色卡是否有Waifu配置
     */
    suspend fun hasCharacterCardWaifuSettings(characterCardId: String): Boolean {
        val preferences = context.waifuDataStore.data.first()
        val prefix = getCharacterCardWaifuPrefix(characterCardId)

        return getAllBooleanWaifuKeys().any { key -> preferences.contains(booleanPreferencesKey("${prefix}${key.name}")) } ||
                getAllIntWaifuKeys().any { key -> preferences.contains(intPreferencesKey("${prefix}${key.name}")) } ||
                getAllStringWaifuKeys().any { key -> preferences.contains(stringPreferencesKey("${prefix}${key.name}")) }
    }
}
