package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.CustomEmoji
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Define the DataStore at the module level
private val Context.customEmojiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "custom_emoji_settings")

/**
 * 自定义表情 DataStore Preferences 管理类
 * 
 * 使用 DataStore 存储自定义表情的元数据（JSON格式）
 * 文件本身存储在 filesDir/custom_emoji/ 目录下
 */
class CustomEmojiPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CustomEmojiPreferences? = null

        fun getInstance(context: Context): CustomEmojiPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomEmojiPreferences(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private const val TAG = "CustomEmojiPreferences"
        
        // Preference keys
        private val CUSTOM_EMOJIS = stringPreferencesKey("custom_emojis")
        private val ALL_CATEGORIES = stringSetPreferencesKey("all_categories")
        private val BUILTIN_EMOJIS_INITIALIZED = booleanPreferencesKey("builtin_emojis_initialized")
        
        // 内置情绪类别
        val BUILTIN_EMOTIONS = listOf(
            "happy", "sad", "angry", "surprised", "confused",
            "crying", "like_you", "miss_you", "speechless"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 获取所有自定义表情的 Flow
     */
    fun getCustomEmojisFlow(): Flow<List<CustomEmoji>> {
        return context.customEmojiDataStore.data.map { preferences ->
            val jsonString = preferences[CUSTOM_EMOJIS] ?: "[]"
            try {
                json.decodeFromString<List<CustomEmoji>>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error decoding custom emojis", e)
                emptyList()
            }
        }
    }

    /**
     * 添加自定义表情
     */
    suspend fun addCustomEmoji(emoji: CustomEmoji) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = try {
                val jsonString = preferences[CUSTOM_EMOJIS] ?: "[]"
                json.decodeFromString<List<CustomEmoji>>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error decoding existing emojis", e)
                emptyList()
            }
            
            val updatedList = currentList + emoji
            preferences[CUSTOM_EMOJIS] = json.encodeToString(updatedList)
            AppLogger.d(TAG, "Added emoji: ${emoji.id} to category: ${emoji.emotionCategory}")
        }
    }

    /**
     * 删除指定的自定义表情
     */
    suspend fun deleteCustomEmoji(emojiId: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = try {
                val jsonString = preferences[CUSTOM_EMOJIS] ?: "[]"
                json.decodeFromString<List<CustomEmoji>>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error decoding existing emojis", e)
                emptyList()
            }
            
            val updatedList = currentList.filter { it.id != emojiId }
            preferences[CUSTOM_EMOJIS] = json.encodeToString(updatedList)
            AppLogger.d(TAG, "Deleted emoji: $emojiId")
        }
    }

    /**
     * 获取指定类别的表情 Flow
     */
    fun getEmojisForCategory(category: String): Flow<List<CustomEmoji>> {
        return getCustomEmojisFlow().map { emojis ->
            emojis.filter { it.emotionCategory == category }
        }
    }

    /**
     * 删除指定类别下的所有表情
     */
    suspend fun deleteCategory(category: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = try {
                val jsonString = preferences[CUSTOM_EMOJIS] ?: "[]"
                json.decodeFromString<List<CustomEmoji>>(jsonString)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error decoding existing emojis", e)
                emptyList()
            }
            
            val updatedList = currentList.filter { it.emotionCategory != category }
            preferences[CUSTOM_EMOJIS] = json.encodeToString(updatedList)

            // Also remove from the categories set
            val currentCategories = preferences[ALL_CATEGORIES] ?: emptySet()
            if (currentCategories.contains(category)) {
                preferences[ALL_CATEGORIES] = currentCategories - category
            }
            AppLogger.d(TAG, "Deleted category: $category")
        }
    }

    /**
     * 获取所有类别（内置 + 自定义）的 Flow
     */
    fun getAllCategories(): Flow<List<String>> {
        return context.customEmojiDataStore.data.map { preferences ->
            val storedCategories = preferences[ALL_CATEGORIES] ?: BUILTIN_EMOTIONS.toSet()

            // 内置类别优先，然后是自定义类别
            val builtin = BUILTIN_EMOTIONS.filter { it in storedCategories }
            val custom = storedCategories.filter { it !in BUILTIN_EMOTIONS }.sorted()

            builtin + custom
        }
    }

    /**
     * 添加一个新类别（即使是空的）
     */
    suspend fun addCategory(categoryName: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentCategories = preferences[ALL_CATEGORIES] ?: emptySet()
            if (!currentCategories.contains(categoryName)) {
                preferences[ALL_CATEGORIES] = currentCategories + categoryName
                AppLogger.d(TAG, "Added category: $categoryName")
            }
        }
    }

    /**
     * 批量添加类别
     */
    suspend fun addCategories(categoryNames: List<String>) {
        context.customEmojiDataStore.edit { preferences ->
            val currentCategories = preferences[ALL_CATEGORIES] ?: emptySet()
            val newCategories = categoryNames.filter { it !in currentCategories }
            if (newCategories.isNotEmpty()) {
                preferences[ALL_CATEGORIES] = currentCategories + newCategories
                AppLogger.d(TAG, "Added categories: $newCategories")
            }
        }
    }

    /**
     * 获取所有自定义类别（不包括内置类别）
     */
    fun getCustomCategories(): Flow<List<String>> {
        return getCustomEmojisFlow().map { emojis ->
            emojis
                .filter { !it.isBuiltInCategory }
                .map { it.emotionCategory }
                .distinct()
                .sorted()
        }
    }

    /**
     * 清空所有自定义表情
     */
    suspend fun clearAllEmojis() {
        context.customEmojiDataStore.edit { preferences ->
            preferences[CUSTOM_EMOJIS] = "[]"
            preferences.remove(ALL_CATEGORIES)
            AppLogger.d(TAG, "Cleared all custom emojis and categories")
        }
    }

    /**
     * 检查内置表情是否已初始化
     */
    fun isBuiltinEmojisInitialized(): Flow<Boolean> {
        return context.customEmojiDataStore.data.map { preferences ->
            preferences[BUILTIN_EMOJIS_INITIALIZED] ?: false
        }
    }

    /**
     * 标记内置表情已初始化
     */
    suspend fun setBuiltinEmojisInitialized(initialized: Boolean) {
        context.customEmojiDataStore.edit { preferences ->
            preferences[BUILTIN_EMOJIS_INITIALIZED] = initialized
        }
    }
}

