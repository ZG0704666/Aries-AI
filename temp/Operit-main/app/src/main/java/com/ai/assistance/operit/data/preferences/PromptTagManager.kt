package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.promptTagDataStore by preferencesDataStore(
    name = "prompt_tags"
)

/**
 * 提示词标签管理器
 */
class PromptTagManager private constructor(private val context: Context) {
    
    private val dataStore = context.promptTagDataStore
    
    companion object {
        private val PROMPT_TAG_LIST = stringSetPreferencesKey("prompt_tag_list")
        
        // 系统标签的固定ID
        const val SYSTEM_CHAT_TAG_ID = "system_chat_tag"
        const val SYSTEM_VOICE_TAG_ID = "system_voice_tag"
        const val SYSTEM_DESKTOP_PET_TAG_ID = "system_desktop_pet_tag"
        
        @Volatile
        private var INSTANCE: PromptTagManager? = null
        
        /**
         * 获取全局单例实例
         */
        fun getInstance(context: Context): PromptTagManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PromptTagManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 标签列表流
    val tagListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[PROMPT_TAG_LIST]?.toList() ?: emptyList()
    }

    val allTagsFlow: Flow<List<PromptTag>> = dataStore.data.map { preferences ->
        val tagIds = preferences[PROMPT_TAG_LIST]?.toList() ?: emptyList()
        tagIds.map { id ->
            getPromptTagFromPreferences(preferences, id)
        }.sortedByDescending { it.updatedAt }
    }
    
    // 获取标签流
    fun getPromptTagFlow(id: String): Flow<PromptTag> = dataStore.data.map { preferences ->
        getPromptTagFromPreferences(preferences, id)
    }
    
    // 从Preferences中获取标签
    private fun getPromptTagFromPreferences(preferences: Preferences, id: String): PromptTag {
        val nameKey = stringPreferencesKey("prompt_tag_${id}_name")
        val descriptionKey = stringPreferencesKey("prompt_tag_${id}_description")
        val promptContentKey = stringPreferencesKey("prompt_tag_${id}_prompt_content")
        val tagTypeKey = stringPreferencesKey("prompt_tag_${id}_tag_type")
        val isSystemTagKey = booleanPreferencesKey("prompt_tag_${id}_is_system_tag")
        val createdAtKey = longPreferencesKey("prompt_tag_${id}_created_at")
        val updatedAtKey = longPreferencesKey("prompt_tag_${id}_updated_at")
        
        return PromptTag(
            id = id,
            name = preferences[nameKey] ?: "未命名标签",
            description = preferences[descriptionKey] ?: "",
            promptContent = preferences[promptContentKey] ?: "",
            tagType = try {
                TagType.valueOf(preferences[tagTypeKey] ?: TagType.CUSTOM.name)
            } catch (e: IllegalArgumentException) {
                TagType.CUSTOM
            },
            isSystemTag = preferences[isSystemTagKey] ?: false,
            createdAt = preferences[createdAtKey] ?: System.currentTimeMillis(),
            updatedAt = preferences[updatedAtKey] ?: System.currentTimeMillis()
        )
    }
    
    // 创建标签
    suspend fun createPromptTag(
        name: String,
        description: String = "",
        promptContent: String = "",
        tagType: TagType = TagType.CUSTOM,
        isSystemTag: Boolean = false
    ): String {
        val id = UUID.randomUUID().toString()
        
        dataStore.edit { preferences ->
            // 添加到标签列表
            val currentList = preferences[PROMPT_TAG_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.add(id)
            preferences[PROMPT_TAG_LIST] = currentList
            
            // 设置标签数据
            val nameKey = stringPreferencesKey("prompt_tag_${id}_name")
            val descriptionKey = stringPreferencesKey("prompt_tag_${id}_description")
            val promptContentKey = stringPreferencesKey("prompt_tag_${id}_prompt_content")
            val tagTypeKey = stringPreferencesKey("prompt_tag_${id}_tag_type")
            val isSystemTagKey = booleanPreferencesKey("prompt_tag_${id}_is_system_tag")
            val createdAtKey = longPreferencesKey("prompt_tag_${id}_created_at")
            val updatedAtKey = longPreferencesKey("prompt_tag_${id}_updated_at")
            
            preferences[nameKey] = name
            preferences[descriptionKey] = description
            preferences[promptContentKey] = promptContent
            preferences[tagTypeKey] = tagType.name
            preferences[isSystemTagKey] = isSystemTag
            preferences[createdAtKey] = System.currentTimeMillis()
            preferences[updatedAtKey] = System.currentTimeMillis()
        }
        
        return id
    }
    
    // 更新标签
    suspend fun updatePromptTag(
        id: String,
        name: String? = null,
        description: String? = null,
        promptContent: String? = null,
        tagType: TagType? = null
    ) {
        // 不允许修改系统标签的类型
        if (isSystemTag(id)) return
        
        dataStore.edit { preferences ->
            name?.let { preferences[stringPreferencesKey("prompt_tag_${id}_name")] = it }
            description?.let { preferences[stringPreferencesKey("prompt_tag_${id}_description")] = it }
            promptContent?.let { preferences[stringPreferencesKey("prompt_tag_${id}_prompt_content")] = it }
            tagType?.let { preferences[stringPreferencesKey("prompt_tag_${id}_tag_type")] = it.name }
            
            // 更新修改时间
            preferences[longPreferencesKey("prompt_tag_${id}_updated_at")] = System.currentTimeMillis()
        }
    }
    
    // 删除标签
    suspend fun deletePromptTag(id: String) {
        // 不允许删除系统标签
        if (isSystemTag(id)) return
        
        dataStore.edit { preferences ->
            // 从列表中移除
            val currentList = preferences[PROMPT_TAG_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.remove(id)
            preferences[PROMPT_TAG_LIST] = currentList
            
            // 清除标签数据
            val keysToRemove = listOf(
                "prompt_tag_${id}_name",
                "prompt_tag_${id}_description",
                "prompt_tag_${id}_prompt_content",
                "prompt_tag_${id}_tag_type",
                "prompt_tag_${id}_is_system_tag",
                "prompt_tag_${id}_created_at",
                "prompt_tag_${id}_updated_at"
            )
            
            keysToRemove.forEach { key ->
                when {
                    key.endsWith("_is_system_tag") -> preferences.remove(booleanPreferencesKey(key))
                    key.endsWith("_created_at") || key.endsWith("_updated_at") -> preferences.remove(longPreferencesKey(key))
                    else -> preferences.remove(stringPreferencesKey(key))
                }
            }
        }
    }
    
    // 检查是否为系统标签
    private suspend fun isSystemTag(id: String): Boolean {
        return try {
            getPromptTagFlow(id).first().isSystemTag
        } catch (e: Exception) {
            false
        }
    }
    
    // 获取所有标签
    suspend fun getAllTags(): List<PromptTag> {
        val tagIds = tagListFlow.first()
        return tagIds.mapNotNull { id ->
            try {
                getPromptTagFlow(id).first()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // 获取系统标签
    suspend fun getSystemTags(): List<PromptTag> {
        return getAllTags().filter { it.isSystemTag }
    }
    
    // 获取自定义标签
    suspend fun getCustomTags(): List<PromptTag> {
        return getAllTags().filter { !it.isSystemTag }
    }
    
    // 根据类型获取标签
    suspend fun getTagsByType(tagType: TagType): List<PromptTag> {
        return getAllTags().filter { it.tagType == tagType }
    }
    
    // 查找具有相同内容的标签（不包括标签标题）
    suspend fun findTagWithSameContent(promptContent: String): PromptTag? {
        return getAllTags().find { tag ->
            tag.promptContent.trim() == promptContent.trim()
        }
    }
    
    // 创建或复用标签（如果内容相同则复用现有标签）
    suspend fun createOrReusePromptTag(
        name: String,
        description: String = "",
        promptContent: String = "",
        tagType: TagType = TagType.CUSTOM,
        isSystemTag: Boolean = false
    ): String {
        // 首先查找是否存在相同内容的标签
        val existingTag = findTagWithSameContent(promptContent)
        
        return if (existingTag != null) {
            // 如果找到相同内容的标签，返回现有标签的ID
            existingTag.id
        } else {
            // 如果没有找到，创建新标签
            createPromptTag(name, description, promptContent, tagType, isSystemTag)
        }
    }
    
    // 初始化系统标签
    suspend fun initializeSystemTags() {
        dataStore.edit { preferences ->
            val tagListKey = PROMPT_TAG_LIST
            val currentList = preferences[tagListKey]?.toMutableSet() ?: mutableSetOf()
            var listModified = false
            
            // 检查并创建系统标签
            val systemTags = mapOf(
                SYSTEM_CHAT_TAG_ID to Triple(
                    "通用聊天",
                    "适用于聊天功能的通用提示词",
                    "使用问题库根据用户的风格、偏好和过去的信息个性化响应。"
                ),
                SYSTEM_VOICE_TAG_ID to Triple(
                    "通用语音",
                    "适用于语音功能的通用提示词",
                    "你的回答必须非常简短、口语化，像日常聊天一样。严禁使用任何形式的列表、分点（例如'第一'、'第二'或'首先'、'其次'）和Markdown标记（例如`*`、`#`、`**`）。你的回答就是纯文本的、可以直接朗读的对话。总是直接回答问题，不要有多余的客套话和引导语。"
                ),
                SYSTEM_DESKTOP_PET_TAG_ID to Triple(
                    "通用桌宠",
                    "适用于桌宠功能的通用提示词",
                    "你的回答必须非常简短、口语化，像日常聊天一样。严禁使用任何形式的列表、分点（例如'第一'、'第二'或'首先'、'其次'）和Markdown标记（例如`*`、`#`、`**`）。使用可爱、亲切、活泼的语气，经常使用表情符号增加互动感。表现得像一个真正的朋友，而不仅仅是工具。可以适当撒娇、卖萌，让用户感受到温暖和陪伴。"
                )
            )
            
            systemTags.forEach { (id, details) ->
                if (!currentList.contains(id)) {
                    currentList.add(id)
                    setupSystemTag(preferences, id, details.first, details.second, details.third)
                    listModified = true
                }
            }
            
            if (listModified) {
                preferences[tagListKey] = currentList
            }
        }
    }
    
    private fun setupSystemTag(
        preferences: MutablePreferences,
        id: String,
        name: String,
        description: String,
        promptContent: String
    ) {
        val nameKey = stringPreferencesKey("prompt_tag_${id}_name")
        val descriptionKey = stringPreferencesKey("prompt_tag_${id}_description")
        val promptContentKey = stringPreferencesKey("prompt_tag_${id}_prompt_content")
        val tagTypeKey = stringPreferencesKey("prompt_tag_${id}_tag_type")
        val isSystemTagKey = booleanPreferencesKey("prompt_tag_${id}_is_system_tag")
        val createdAtKey = longPreferencesKey("prompt_tag_${id}_created_at")
        val updatedAtKey = longPreferencesKey("prompt_tag_${id}_updated_at")
        
        val tagType = when (id) {
            SYSTEM_CHAT_TAG_ID -> TagType.SYSTEM_CHAT
            SYSTEM_VOICE_TAG_ID -> TagType.SYSTEM_VOICE
            SYSTEM_DESKTOP_PET_TAG_ID -> TagType.SYSTEM_DESKTOP_PET
            else -> TagType.CUSTOM
        }
        
        preferences[nameKey] = name
        preferences[descriptionKey] = description
        preferences[promptContentKey] = promptContent
        preferences[tagTypeKey] = tagType.name
        preferences[isSystemTagKey] = true
        preferences[createdAtKey] = System.currentTimeMillis()
        preferences[updatedAtKey] = System.currentTimeMillis()
    }
} 