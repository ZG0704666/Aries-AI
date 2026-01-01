package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.PromptProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.promptPreferencesDataStore by preferencesDataStore(
    name = "prompt_preferences"
)

/**
 * Manager for handling prompt profiles preferences
 */
class PromptPreferencesManager(private val context: Context) {

    private val dataStore = context.promptPreferencesDataStore

    // Keys
    companion object {
        private val PROMPT_PROFILE_LIST = stringSetPreferencesKey("prompt_profile_list")
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_prompt_profile_id")
        
        // Helper function to create profile-specific keys
        private fun profileNameKey(id: String) = stringPreferencesKey("prompt_profile_${id}_name")
        private fun profileIntroPromptKey(id: String) = stringPreferencesKey("prompt_profile_${id}_intro_prompt")
        private fun profileTonePromptKey(id: String) = stringPreferencesKey("prompt_profile_${id}_tone_prompt")
        private fun profileIsDefaultKey(id: String) = booleanPreferencesKey("prompt_profile_${id}_is_default")
        
        // 固定ID，用于特定功能的默认提示词配置
        const val DEFAULT_CHAT_PROFILE_ID = "default_chat"
        const val DEFAULT_VOICE_PROFILE_ID = "default_voice"
        const val DEFAULT_DESKTOP_PET_PROFILE_ID = "default_desktop_pet"
    }

    // Default prompt values for standard usage
    val defaultIntroPrompt = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
    val defaultTonePrompt = "保持有帮助的语气，并清楚地传达限制。使用问题库根据用户的风格、偏好和过去的信息个性化响应。"
    
    // Default prompt values for chat function
    val defaultChatIntroPrompt = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
    val defaultChatTonePrompt = "保持有帮助的语气，并清楚地传达限制。使用问题库根据用户的风格、偏好和过去的信息个性化响应。"
    
    // Default prompt values for voice function
    val defaultVoiceIntroPrompt = "你是Operit语音助手。你的所有回答都将通过语音播出，所以你必须只说那些听起来自然的话。你的核心任务是进行流畅、自然的口语对话。"
    val defaultVoiceTonePrompt = "你的回答必须非常简短、口语化，像日常聊天一样。严禁使用任何形式的列表、分点（例如'第一'、'第二'或'首先'、'其次'）和Markdown标记（例如`*`、`#`、`**`）。你的回答就是纯文本的、可以直接朗读的对话。总是直接回答问题，不要有多余的客套话和引导语。"
    
    // Default prompt values for desktop pet function
    val defaultDesktopPetIntroPrompt = "你是Operit桌宠，一个可爱、活泼、充满活力的桌面伙伴。你的主要任务是陪伴用户，提供温暖和快乐，同时也可以帮助用户完成简单任务。"
    val defaultDesktopPetTonePrompt = "你的回答必须非常简短、口语化，像日常聊天一样。严禁使用任何形式的列表、分点（例如'第一'、'第二'或'首先'、'其次'）和Markdown标记（例如`*`、`#`、`**`）。使用可爱、亲切、活泼的语气，经常使用表情符号增加互动感。表现得像一个真正的朋友，而不仅仅是工具。可以适当撒娇、卖萌，让用户感受到温暖和陪伴。"

    // Flow of prompt profile list
    val profileListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[PROMPT_PROFILE_LIST]?.toList() ?: listOf("default")
    }

    // Flow of active profile ID
    val activeProfileIdFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PROFILE_ID] ?: "default"
    }

    // Get prompt profile by ID
    fun getPromptProfileFlow(profileId: String): Flow<PromptProfile> = dataStore.data.map { preferences ->
        val name = preferences[profileNameKey(profileId)] ?: "默认提示词"
        val introPrompt = preferences[profileIntroPromptKey(profileId)] ?: defaultIntroPrompt
        val tonePrompt = preferences[profileTonePromptKey(profileId)] ?: defaultTonePrompt
        val isDefault = preferences[profileIsDefaultKey(profileId)] ?: (profileId == "default")
        val isActive = preferences[ACTIVE_PROFILE_ID] == profileId

        PromptProfile(
            id = profileId,
            name = name,
            introPrompt = introPrompt,
            tonePrompt = tonePrompt,
            isActive = isActive,
            isDefault = isDefault
        )
    }

    // Create a new prompt profile
    suspend fun createProfile(
        name: String,
        introPrompt: String? = null,
        tonePrompt: String? = null,
        isDefault: Boolean = false
    ): String {
        val id = if (isDefault) "default" else UUID.randomUUID().toString()

        dataStore.edit { preferences ->
            // Add to profile list if not default (default is always in the list)
            val currentList = preferences[PROMPT_PROFILE_LIST]?.toMutableSet() ?: mutableSetOf("default")
            if (!currentList.contains(id)) {
                currentList.add(id)
                preferences[PROMPT_PROFILE_LIST] = currentList
            }

            // Set profile data
            preferences[profileNameKey(id)] = name
            preferences[profileIntroPromptKey(id)] = introPrompt ?: defaultIntroPrompt
            preferences[profileTonePromptKey(id)] = tonePrompt ?: defaultTonePrompt
            preferences[profileIsDefaultKey(id)] = isDefault

            // If this is the first profile or is default, make it active
            if (isDefault || preferences[ACTIVE_PROFILE_ID] == null) {
                preferences[ACTIVE_PROFILE_ID] = id
            }
        }

        return id
    }

    // Delete a profile
    suspend fun deleteProfile(profileId: String) {
        // Don't allow deleting the default profile
        if (profileId == "default") return

        dataStore.edit { preferences ->
            // Remove from list
            val currentList = preferences[PROMPT_PROFILE_LIST]?.toMutableSet() ?: mutableSetOf("default")
            currentList.remove(profileId)
            preferences[PROMPT_PROFILE_LIST] = currentList

            // Clear profile data
            preferences.remove(profileNameKey(profileId))
            preferences.remove(profileIntroPromptKey(profileId))
            preferences.remove(profileTonePromptKey(profileId))
            preferences.remove(profileIsDefaultKey(profileId))

            // If this was the active profile, switch to default
            if (preferences[ACTIVE_PROFILE_ID] == profileId) {
                preferences[ACTIVE_PROFILE_ID] = "default"
            }
        }
    }

    // Set active profile
    suspend fun setActiveProfile(profileId: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = profileId
        }
    }

    // Update prompt profile
    suspend fun updatePromptProfile(
        profileId: String,
        name: String? = null,
        introPrompt: String? = null,
        tonePrompt: String? = null
    ) {
        dataStore.edit { preferences ->
            name?.let { preferences[profileNameKey(profileId)] = it }
            introPrompt?.let { preferences[profileIntroPromptKey(profileId)] = it }
            tonePrompt?.let { preferences[profileTonePromptKey(profileId)] = it }
        }
    }

    // Initialize with default profiles if needed
    suspend fun initializeIfNeeded() {
        dataStore.edit { preferences ->
            val profileListKey = PROMPT_PROFILE_LIST
            val currentList = preferences[profileListKey]?.toMutableSet()

            if (currentList == null) {
                // --- Fresh Install ---
                val defaultProfiles = setOf(
                    "default",
                    DEFAULT_CHAT_PROFILE_ID,
                    DEFAULT_VOICE_PROFILE_ID,
                    DEFAULT_DESKTOP_PET_PROFILE_ID
                )
                preferences[profileListKey] = defaultProfiles
                preferences[ACTIVE_PROFILE_ID] = "default"

                // Set up all default profiles
                setupDefaultProfile(preferences, "default", "默认提示词", defaultIntroPrompt, defaultTonePrompt, true)
                setupDefaultProfile(preferences, DEFAULT_CHAT_PROFILE_ID, "默认聊天提示词", defaultChatIntroPrompt, defaultChatTonePrompt)
                setupDefaultProfile(preferences, DEFAULT_VOICE_PROFILE_ID, "默认语音提示词", defaultVoiceIntroPrompt, defaultVoiceTonePrompt)
                setupDefaultProfile(preferences, DEFAULT_DESKTOP_PET_PROFILE_ID, "默认桌宠提示词", defaultDesktopPetIntroPrompt, defaultDesktopPetTonePrompt)

            } else {
                // --- Migration for existing users ---
                var listModified = false
                val profilesToAdd = mapOf(
                    DEFAULT_CHAT_PROFILE_ID to Triple("默认聊天提示词", defaultChatIntroPrompt, defaultChatTonePrompt),
                    DEFAULT_VOICE_PROFILE_ID to Triple("默认语音提示词", defaultVoiceIntroPrompt, defaultVoiceTonePrompt),
                    DEFAULT_DESKTOP_PET_PROFILE_ID to Triple("默认桌宠提示词", defaultDesktopPetIntroPrompt, defaultDesktopPetTonePrompt)
                )

                profilesToAdd.forEach { (id, details) ->
                    if (!currentList.contains(id)) {
                        currentList.add(id)
                        setupDefaultProfile(preferences, id, details.first, details.second, details.third)
                        listModified = true
                    }
                }

                if (listModified) {
                    preferences[profileListKey] = currentList
                }
            }
        }
    }
    
    // Helper function to set up a default profile's data
    private fun setupDefaultProfile(
        preferences: MutablePreferences,
        id: String,
        name: String,
        introPrompt: String,
        tonePrompt: String,
        isDefault: Boolean = false
    ) {
        preferences[profileNameKey(id)] = name
        preferences[profileIntroPromptKey(id)] = introPrompt
        preferences[profileTonePromptKey(id)] = tonePrompt
        preferences[profileIsDefaultKey(id)] = isDefault
    }
} 