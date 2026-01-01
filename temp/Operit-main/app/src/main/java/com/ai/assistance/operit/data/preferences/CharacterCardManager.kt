package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.model.TavernCharacterCard
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID
import com.ai.assistance.operit.util.AppLogger
import android.util.Base64
import java.io.InputStream
import java.io.ByteArrayOutputStream

private val Context.characterCardDataStore by preferencesDataStore(
    name = "character_cards"
)

/**
 * 角色卡管理器
 */
class CharacterCardManager private constructor(private val context: Context) {
    
    private val dataStore = context.characterCardDataStore
    private val tagManager = PromptTagManager.getInstance(context)
    // 添加UserPreferencesManager引用用于主题管理
    private val userPreferencesManager = UserPreferencesManager.getInstance(context)
    // 添加WaifuPreferences引用用于Waifu模式配置管理
    private val waifuPreferences = WaifuPreferences.getInstance(context)
    
    companion object {
        private val CHARACTER_CARD_LIST = stringSetPreferencesKey("character_card_list")
        private val ACTIVE_CHARACTER_CARD_ID = stringPreferencesKey("active_character_card_id")
        
        // 系统标签的固定ID
        const val SYSTEM_CHAT_TAG_ID = "system_chat_tag"
        const val SYSTEM_VOICE_TAG_ID = "system_voice_tag"
        const val SYSTEM_DESKTOP_PET_TAG_ID = "system_desktop_pet_tag"
        
        // 默认角色卡ID
        const val DEFAULT_CHARACTER_CARD_ID = "default_character"

        const val DEFAULT_CHARACTER_NAME = "Operit"
        const val DEFAULT_CHARACTER_DESCRIPTION = "系统默认的角色卡配置"
        const val DEFAULT_CHARACTER_SETTING = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。"
        const val DEFAULT_CHARACTER_OTHER_CONTENT = "保持有帮助的语气，并清楚地传达限制。"
        
        @Volatile
        private var INSTANCE: CharacterCardManager? = null
        
        /**
         * 获取全局单例实例
         */
        fun getInstance(context: Context): CharacterCardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CharacterCardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 角色卡列表流
    val characterCardListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[CHARACTER_CARD_LIST]?.toList() ?: listOf(DEFAULT_CHARACTER_CARD_ID)
    }
    
    // 活跃角色卡ID流
    val activeCharacterCardIdFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[ACTIVE_CHARACTER_CARD_ID] ?: DEFAULT_CHARACTER_CARD_ID
    }
    
    // 获取角色卡流
    fun getCharacterCardFlow(id: String): Flow<CharacterCard> = dataStore.data.map { preferences ->
        getCharacterCardFromPreferences(preferences, id)
    }
    
    // 获取活跃角色卡流
    val activeCharacterCardFlow: Flow<CharacterCard> = dataStore.data.map { preferences ->
        val activeId = preferences[ACTIVE_CHARACTER_CARD_ID] ?: DEFAULT_CHARACTER_CARD_ID
        getCharacterCardFromPreferences(preferences, activeId)
    }
    
    // 从Preferences中获取角色卡
    private fun getCharacterCardFromPreferences(preferences: Preferences, id: String): CharacterCard {
        val nameKey = stringPreferencesKey("character_card_${id}_name")
        val descriptionKey = stringPreferencesKey("character_card_${id}_description")
        val characterSettingKey = stringPreferencesKey("character_card_${id}_character_setting")
        val openingStatementKey = stringPreferencesKey("character_card_${id}_opening_statement") // 新增
        val otherContentKey = stringPreferencesKey("character_card_${id}_other_content")
        val attachedTagIdsKey = stringSetPreferencesKey("character_card_${id}_attached_tag_ids")
        val advancedCustomPromptKey = stringPreferencesKey("character_card_${id}_advanced_custom_prompt")
        val marksKey = stringPreferencesKey("character_card_${id}_marks")
        val isDefaultKey = booleanPreferencesKey("character_card_${id}_is_default")
        val createdAtKey = longPreferencesKey("character_card_${id}_created_at")
        val updatedAtKey = longPreferencesKey("character_card_${id}_updated_at")
        
        return CharacterCard(
            id = id,
            name = preferences[nameKey] ?: "默认角色卡",
            description = preferences[descriptionKey] ?: "",
            characterSetting = preferences[characterSettingKey] ?: "",
            openingStatement = preferences[openingStatementKey] ?: "", // 新增
            otherContent = preferences[otherContentKey] ?: "",
            attachedTagIds = preferences[attachedTagIdsKey]?.toList() ?: emptyList(),
            advancedCustomPrompt = preferences[advancedCustomPromptKey] ?: "",
            marks = preferences[marksKey] ?: "",
            isDefault = preferences[isDefaultKey] ?: (id == DEFAULT_CHARACTER_CARD_ID),
            createdAt = preferences[createdAtKey] ?: System.currentTimeMillis(),
            updatedAt = preferences[updatedAtKey] ?: System.currentTimeMillis()
        )
    }
    
    // 获取角色卡快照
    suspend fun getCharacterCard(id: String): CharacterCard {
        val preferences = dataStore.data.first()
        return getCharacterCardFromPreferences(preferences, id)
    }

    /**
     * 为角色卡创建默认主题配置
     */
    private suspend fun createDefaultThemeForCharacterCard(characterCardId: String) {
        // 获取当前默认主题配置作为新角色卡的主题基础
        userPreferencesManager.copyCurrentThemeToCharacterCard(characterCardId)
        // 同时也复制当前Waifu模式配置
        waifuPreferences.copyCurrentWaifuSettingsToCharacterCard(characterCardId)
    }


    suspend fun cloneBindingsFromCharacterCard(sourceCharacterCardId: String, targetCharacterCardId: String) {
        try {
            userPreferencesManager.cloneThemeBetweenCharacterCards(sourceCharacterCardId, targetCharacterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "克隆角色卡主题配置失败", e)
        }

        try {
            waifuPreferences.cloneWaifuSettingsBetweenCharacterCards(sourceCharacterCardId, targetCharacterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "克隆角色卡Waifu配置失败", e)
        }
    }

    // 创建角色卡
    suspend fun createCharacterCard(card: CharacterCard): String {
        val id = if (card.isDefault) DEFAULT_CHARACTER_CARD_ID else UUID.randomUUID().toString()
        val newCard = card.copy(id = id)

        dataStore.edit { preferences ->
            // 添加到角色卡列表
            val currentList = preferences[CHARACTER_CARD_LIST]?.toMutableSet() ?: mutableSetOf(DEFAULT_CHARACTER_CARD_ID)
            if (!currentList.contains(id)) {
                currentList.add(id)
                preferences[CHARACTER_CARD_LIST] = currentList
            }
            
            // 设置角色卡数据
            preferences[stringPreferencesKey("character_card_${id}_name")] = newCard.name
            preferences[stringPreferencesKey("character_card_${id}_description")] = newCard.description
            preferences[stringPreferencesKey("character_card_${id}_character_setting")] = newCard.characterSetting
            preferences[stringPreferencesKey("character_card_${id}_opening_statement")] = newCard.openingStatement // 新增
            preferences[stringPreferencesKey("character_card_${id}_other_content")] = newCard.otherContent
            preferences[stringSetPreferencesKey("character_card_${id}_attached_tag_ids")] = newCard.attachedTagIds.toSet()
            preferences[stringPreferencesKey("character_card_${id}_advanced_custom_prompt")] = newCard.advancedCustomPrompt
            preferences[stringPreferencesKey("character_card_${id}_marks")] = newCard.marks
            preferences[booleanPreferencesKey("character_card_${id}_is_default")] = newCard.isDefault
            preferences[longPreferencesKey("character_card_${id}_created_at")] = newCard.createdAt
            preferences[longPreferencesKey("character_card_${id}_updated_at")] = newCard.updatedAt
            
            // 如果是第一个角色卡或设为默认，设为活跃
            if (newCard.isDefault || preferences[ACTIVE_CHARACTER_CARD_ID] == null) {
                preferences[ACTIVE_CHARACTER_CARD_ID] = id
            }
        }

        // 为新角色卡创建默认主题配置
        if (!newCard.isDefault) {
            createDefaultThemeForCharacterCard(id)
        }
        
        return id
    }
    
    // 更新角色卡
    suspend fun updateCharacterCard(card: CharacterCard) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("character_card_${card.id}_name")] = card.name
            preferences[stringPreferencesKey("character_card_${card.id}_description")] = card.description
            preferences[stringPreferencesKey("character_card_${card.id}_character_setting")] = card.characterSetting
            preferences[stringPreferencesKey("character_card_${card.id}_opening_statement")] = card.openingStatement // 新增
            preferences[stringPreferencesKey("character_card_${card.id}_other_content")] = card.otherContent
            preferences[stringSetPreferencesKey("character_card_${card.id}_attached_tag_ids")] = card.attachedTagIds.toSet()
            preferences[stringPreferencesKey("character_card_${card.id}_advanced_custom_prompt")] = card.advancedCustomPrompt
            preferences[stringPreferencesKey("character_card_${card.id}_marks")] = card.marks
            
            // 更新修改时间
            preferences[longPreferencesKey("character_card_${card.id}_updated_at")] = System.currentTimeMillis()
        }
    }
    
    // 删除角色卡
    suspend fun deleteCharacterCard(id: String) {
        if (id == DEFAULT_CHARACTER_CARD_ID) return
        
        dataStore.edit { preferences ->
            // 从列表中移除
            val currentList = preferences[CHARACTER_CARD_LIST]?.toMutableSet() ?: mutableSetOf(DEFAULT_CHARACTER_CARD_ID)
            currentList.remove(id)
            preferences[CHARACTER_CARD_LIST] = currentList
            
            // 清除角色卡数据
            val keysToRemove = listOf(
                "character_card_${id}_name",
                "character_card_${id}_description",
                "character_card_${id}_character_setting",
                "character_card_${id}_opening_statement", // 新增
                "character_card_${id}_other_content",
                "character_card_${id}_attached_tag_ids",
                "character_card_${id}_advanced_custom_prompt",
                "character_card_${id}_marks",
                "character_card_${id}_is_default",
                "character_card_${id}_created_at",
                "character_card_${id}_updated_at"
            )
            
            keysToRemove.forEach { key ->
                when {
                    key.endsWith("_attached_tag_ids") -> preferences.remove(stringSetPreferencesKey(key))
                    key.endsWith("_is_default") -> preferences.remove(booleanPreferencesKey(key))
                    key.endsWith("_created_at") || key.endsWith("_updated_at") -> preferences.remove(longPreferencesKey(key))
                    else -> preferences.remove(stringPreferencesKey(key))
                }
            }
            
            // 如果这是活跃角色卡，切换到默认
            if (preferences[ACTIVE_CHARACTER_CARD_ID] == id) {
                preferences[ACTIVE_CHARACTER_CARD_ID] = DEFAULT_CHARACTER_CARD_ID
            }
        }

        // 删除角色卡对应的主题配置
        userPreferencesManager.deleteCharacterCardTheme(id)
        // 删除角色卡对应的Waifu模式配置
        waifuPreferences.deleteCharacterCardWaifuSettings(id)
        
    }
    
    // 设置活跃角色卡
    suspend fun setActiveCharacterCard(id: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_CHARACTER_CARD_ID] = id
        }
        
        // 切换到对应角色卡的主题
        switchToCharacterCardTheme(id)
        // 切换到对应角色卡的Waifu模式配置
        switchToCharacterCardWaifuSettings(id)
    }
    
    // 组合提示词（角色设定 + 其他内容 + 标签 + 高级自定义）
    suspend fun combinePrompts(
        characterCardId: String,
        additionalTagIds: List<String> = emptyList()
    ): String {
        val characterCard = getCharacterCardFlow(characterCardId).first()
        val allTagIds = (characterCard.attachedTagIds + additionalTagIds).distinct()
        val attachedTags = allTagIds.mapNotNull { tagId ->
            try {
                tagManager.getPromptTagFlow(tagId).first()
            } catch (e: Exception) {
                // Log or handle error if a tag ID is invalid
                null
            }
        }
        
        val combinedPrompt = buildString {
            if (characterCard.characterSetting.isNotBlank()) {
                append(characterCard.characterSetting)
                append("\n\n")
            }
            
            if (characterCard.otherContent.isNotBlank()) {
                append(characterCard.otherContent)
                append("\n\n")
            }
            
            attachedTags.forEach { tag ->
                if (tag.promptContent.isNotBlank()) {
                    append(tag.promptContent)
                    append("\n\n")
                }
            }
            
            if (characterCard.advancedCustomPrompt.isNotBlank()) {
                append(characterCard.advancedCustomPrompt)
                append("\n\n")
            }
        }
        
        return combinedPrompt.trim()
    }
    
    // 初始化默认角色卡和系统标签
    suspend fun initializeIfNeeded() {
        var isInitialized = false
        dataStore.edit { preferences ->
            val cardListKey = CHARACTER_CARD_LIST
            val currentList = preferences[cardListKey]?.toMutableSet()
            
            if (currentList == null || currentList.isEmpty()) {
                isInitialized = true
                // 首次安装，创建默认角色卡
                val defaultCardId = DEFAULT_CHARACTER_CARD_ID
                preferences[cardListKey] = setOf(defaultCardId)
                preferences[ACTIVE_CHARACTER_CARD_ID] = defaultCardId
                
                // 设置默认角色卡数据
                setupDefaultCharacterCard(preferences, defaultCardId)
            }
        }

        if (isInitialized) {
            // This is a new installation or an old user updating.
            // We should migrate their existing theme settings to the default character card.
            AppLogger.d("CharacterCardManager", "First initialization detected. Migrating current theme to default character card.")
            userPreferencesManager.copyCurrentThemeToCharacterCard(DEFAULT_CHARACTER_CARD_ID)
            userPreferencesManager.saveAiAvatarForCharacterCard(DEFAULT_CHARACTER_CARD_ID, "file:///android_asset/operit.png")
        }
        
        // 确保系统标签存在
        tagManager.initializeSystemTags()
    }

    // 重置默认角色卡
    suspend fun resetDefaultCharacterCard() {
        dataStore.edit { preferences ->
            setupDefaultCharacterCard(preferences, DEFAULT_CHARACTER_CARD_ID)
        }
        // 同时也重置头像和主题
        userPreferencesManager.saveAiAvatarForCharacterCard(DEFAULT_CHARACTER_CARD_ID, "file:///android_asset/operit.png")
    }
    
    private fun setupDefaultCharacterCard(preferences: MutablePreferences, id: String) {
        val nameKey = stringPreferencesKey("character_card_${id}_name")
        val descriptionKey = stringPreferencesKey("character_card_${id}_description")
        val characterSettingKey = stringPreferencesKey("character_card_${id}_character_setting")
        val openingStatementKey = stringPreferencesKey("character_card_${id}_opening_statement") // 新增
        val otherContentKey = stringPreferencesKey("character_card_${id}_other_content")
        val attachedTagIdsKey = stringSetPreferencesKey("character_card_${id}_attached_tag_ids")
        val advancedCustomPromptKey = stringPreferencesKey("character_card_${id}_advanced_custom_prompt")
        val marksKey = stringPreferencesKey("character_card_${id}_marks")
        val isDefaultKey = booleanPreferencesKey("character_card_${id}_is_default")
        val createdAtKey = longPreferencesKey("character_card_${id}_created_at")
        val updatedAtKey = longPreferencesKey("character_card_${id}_updated_at")
        
        preferences[nameKey] = DEFAULT_CHARACTER_NAME
        preferences[descriptionKey] = DEFAULT_CHARACTER_DESCRIPTION
        preferences[characterSettingKey] = DEFAULT_CHARACTER_SETTING
        preferences[openingStatementKey] = ""
        preferences[otherContentKey] = DEFAULT_CHARACTER_OTHER_CONTENT
        preferences[attachedTagIdsKey] = setOf<String>()
        preferences[advancedCustomPromptKey] = ""
        preferences[marksKey] = ""
        preferences[isDefaultKey] = true
        preferences[createdAtKey] = System.currentTimeMillis()
        preferences[updatedAtKey] = System.currentTimeMillis()
    }
    
    // 获取所有角色卡
    suspend fun getAllCharacterCards(): List<CharacterCard> {
        val cardIds = characterCardListFlow.first()
        return cardIds.mapNotNull { id ->
            try {
                getCharacterCardFlow(id).first()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // 根据角色名查找角色卡
    suspend fun findCharacterCardByName(name: String): CharacterCard? {
        val allCards = getAllCharacterCards()
        return allCards.find { it.name == name }
    }
    
    /**
     * 从酒馆角色卡JSON字符串创建角色卡
     */
    suspend fun createCharacterCardFromTavernJson(jsonString: String): Result<String> {
        return try {
            val gson = Gson()
            val tavernCard = gson.fromJson(jsonString, TavernCharacterCard::class.java)
            
            if (tavernCard.data.name.isBlank()) {
                return Result.failure(Exception("角色卡名称不能为空"))
            }

            // --- New logic for Character Book ---
            var worldBookTagId: String? = null
            tavernCard.data.character_book?.let { book ->
                if (book.entries.isNotEmpty()) {
                    val worldBookContent = buildString {
                        book.entries.forEach { entry ->
                            if (entry.content.isNotBlank()) {
                                // 使用更结构化的格式作为提示词
                                append("[${entry.name}]\n${entry.content}\n\n")
                            }
                        }
                    }.trim()

                    if (worldBookContent.isNotBlank()) {
                        worldBookTagId = tagManager.createOrReusePromptTag(
                            name = "世界书: ${tavernCard.data.name}",
                            description = "为角色'${tavernCard.data.name}'自动生成的世界书。",
                            promptContent = worldBookContent,
                            tagType = TagType.FUNCTION
                        )
                    }
                }
            }

            val characterCard = convertTavernCardToCharacterCard(tavernCard)

            val finalCard = worldBookTagId?.let {
                characterCard.copy(attachedTagIds = characterCard.attachedTagIds + it)
            } ?: characterCard

            val id = createCharacterCard(finalCard)
            Result.success(id)
        } catch (e: JsonSyntaxException) {
            Result.failure(Exception("JSON格式错误: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("解析失败: ${e.message}"))
        }
    }
    
    /**
     * 从PNG图片文件中提取酒馆角色卡数据
     */
    suspend fun createCharacterCardFromTavernPng(inputStream: InputStream): Result<String> {
        return try {
            val jsonString = extractJsonFromPng(inputStream)
            createCharacterCardFromTavernJson(jsonString)
        } catch (e: Exception) {
            Result.failure(Exception("PNG解析失败: ${e.message}"))
        }
    }
    
    /**
     * 从PNG图片的tEXt块中提取JSON数据
     */
    private fun extractJsonFromPng(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        
        // PNG文件头检查
        if (bytes.size < 8 || !isPngHeader(bytes)) {
            throw Exception("不是有效的PNG文件")
        }
        
        var offset = 8 // 跳过PNG头
        
        while (offset < bytes.size - 12) { // 确保有足够的字节读取块头
            // 读取块长度
            val chunkLength = readUInt32BigEndian(bytes, offset)
            offset += 4
            
            // 读取块类型
            val chunkType = String(bytes.sliceArray(offset until offset + 4), Charsets.ISO_8859_1)
            offset += 4
            
            // 如果是tEXt块
            if (chunkType == "tEXt") {
                val chunkData = bytes.sliceArray(offset until offset + chunkLength.toInt())
                val textData = String(chunkData, Charsets.ISO_8859_1)
                
                // 查找关键字"chara"
                val nullIndex = textData.indexOf('\u0000')
                if (nullIndex > 0) {
                    val keyword = textData.substring(0, nullIndex)
                    if (keyword == "chara") {
                        val base64Data = textData.substring(nullIndex + 1)
                        return decodeBase64ToJson(base64Data)
                    }
                }
            }
            
            // 跳到下一个块 (数据长度 + 4字节CRC)
            offset += chunkLength.toInt() + 4
        }
        
        throw Exception("在PNG文件中未找到角色卡数据")
    }
    
    /**
     * 检查PNG文件头
     */
    private fun isPngHeader(bytes: ByteArray): Boolean {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        return bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(pngSignature)
    }
    
    /**
     * 从字节数组中读取大端序的32位无符号整数
     */
    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Long {
        return (((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
               (bytes[offset + 3].toInt() and 0xFF)).toLong()
    }
    
    /**
     * 解码Base64数据为JSON字符串
     */
    private fun decodeBase64ToJson(base64Data: String): String {
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            return String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Base64解码失败: ${e.message}")
        }
    }
    
    /**
     * 将酒馆角色卡转换为本地角色卡格式
     */
    private suspend fun convertTavernCardToCharacterCard(tavernCard: TavernCharacterCard): CharacterCard {
        val data = tavernCard.data
        
        // 组合角色设定
        val characterSetting = buildString {
            if (data.description.isNotBlank()) {
                append("角色描述：\n${data.description}\n\n")
            }
            if (data.personality.isNotBlank()) {
                append("性格特征：\n${data.personality}\n\n")
            }
            if (data.scenario.isNotBlank()) {
                append("场景设定：\n${data.scenario}\n\n")
            }
        }.trim()
        
        // 组合其他内容
        val otherContent = buildString {
            if (data.mes_example.isNotBlank()) {
                append("对话示例：\n${data.mes_example}\n\n")
            }
            if (data.system_prompt.isNotBlank()) {
                append("系统提示词：\n${data.system_prompt}\n\n")
            }
            if (data.post_history_instructions.isNotBlank()) {
                append("历史指令：\n${data.post_history_instructions}\n\n")
            }
            
            // 添加备用问候语
            if (data.alternate_greetings.isNotEmpty()) {
                append("备用问候语：\n")
                data.alternate_greetings.forEachIndexed { index, greeting ->
                    append("${index + 1}. $greeting\n")
                }
                append("\n")
            }
        }.trim()
        
        // 组合高级自定义提示词
        val advancedCustomPrompt = buildString {
            
            
            data.extensions?.depth_prompt?.let { depthPrompt ->
                if (depthPrompt.prompt.isNotBlank()) {
                    append("深度提示词：\n${depthPrompt.prompt}\n\n")
                }
            }
        }.trim()
        
        // 生成描述（简化版，不包含作者信息）
        val description = if (data.tags.isNotEmpty()) {
            "标签：${data.tags.take(5).joinToString(", ")}" + 
            if (data.tags.size > 5) "等${data.tags.size}个" else ""
        } else ""
        
        // 生成备注信息
        val marks = buildString {
            append("来源：酒馆角色卡\n")
            if (data.creator.isNotBlank()) {
                append("作者：${data.creator}\n")
            }
            if (data.creator_notes.isNotBlank()) {
                append("作者备注：\n${data.creator_notes}\n\n")
            }
            if (data.character_version.isNotBlank()) {
                append("版本：${data.character_version}\n")
            }
            if (data.tags.isNotEmpty()) {
                append("原始标签：${data.tags.joinToString(", ")}\n")
            }
            if (tavernCard.spec.isNotBlank()) {
                append("格式：${tavernCard.spec}")
                if (tavernCard.spec_version.isNotBlank()) {
                    append(" v${tavernCard.spec_version}")
                }
                append("\n")
            }
        }.trim()
        
        return CharacterCard(
            id = "", // 将在createCharacterCard中生成
            name = data.name,
            description = description,
            characterSetting = characterSetting,
            openingStatement = data.first_mes, // 从first_mes获取开场白
            otherContent = otherContent,
            attachedTagIds = emptyList(), // 可以后续根据tags创建标签
            advancedCustomPrompt = advancedCustomPrompt,
            marks = marks,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    // ========== 角色卡主题相关方法 ==========

    /**
     * 切换到指定角色卡的主题配置
     */
    private suspend fun switchToCharacterCardTheme(characterCardId: String) {
        try {
            // 检查角色卡是否有专属主题配置
            if (userPreferencesManager.hasCharacterCardTheme(characterCardId)) {
                userPreferencesManager.switchToCharacterCardTheme(characterCardId)
                AppLogger.d("CharacterCardManager", "已切换到角色卡 $characterCardId 的专属主题")
            } else {
                AppLogger.d("CharacterCardManager", "角色卡 $characterCardId 没有专属主题配置，保持当前主题")
            }
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "切换角色卡主题失败", e)
        }
    }

    /**
     * 为当前活跃角色卡保存主题配置
     */
    suspend fun saveThemeForActiveCharacterCard() {
        try {
            val activeCardId = activeCharacterCardFlow.first().id
            userPreferencesManager.saveCurrentThemeToCharacterCard(activeCardId)
            AppLogger.d("CharacterCardManager", "已为角色卡 $activeCardId 保存主题配置")
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "为活跃角色卡保存主题失败", e)
        }
    }

    /**
     * 删除指定角色卡的主题配置
     */
    suspend fun deleteThemeForCharacterCard(characterCardId: String) {
        try {
            userPreferencesManager.deleteCharacterCardTheme(characterCardId)
            AppLogger.d("CharacterCardManager", "已删除角色卡 $characterCardId 的主题配置")
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "删除角色卡主题配置失败", e)
        }
    }

    /**
     * 检查指定角色卡是否有专属主题配置
     */
    suspend fun hasThemeForCharacterCard(characterCardId: String): Boolean {
        return try {
            userPreferencesManager.hasCharacterCardTheme(characterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "检查角色卡主题配置失败", e)
            false
        }
    }

    /**
     * 切换到指定角色卡的Waifu模式配置
     */
    private suspend fun switchToCharacterCardWaifuSettings(characterCardId: String) {
        try {
            // 始终调用切换方法，即使角色卡没有配置也会清空当前配置，避免保留上一个角色卡的设置
            waifuPreferences.switchToCharacterCardWaifuSettings(characterCardId)
            
            if (waifuPreferences.hasCharacterCardWaifuSettings(characterCardId)) {
                AppLogger.d("CharacterCardManager", "已切换到角色卡 $characterCardId 的Waifu模式配置")
            } else {
                AppLogger.d("CharacterCardManager", "角色卡 $characterCardId 没有Waifu模式配置，已清空当前配置")
            }
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "切换角色卡Waifu模式配置失败", e)
        }
    }

    /**
     * 为当前活跃角色卡保存Waifu模式配置
     */
    suspend fun saveWaifuSettingsForActiveCharacterCard() {
        try {
            val activeCardId = activeCharacterCardFlow.first().id
            waifuPreferences.saveCurrentWaifuSettingsToCharacterCard(activeCardId)
            AppLogger.d("CharacterCardManager", "已为角色卡 $activeCardId 保存Waifu模式配置")
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "为活跃角色卡保存Waifu配置失败", e)
        }
    }
} 