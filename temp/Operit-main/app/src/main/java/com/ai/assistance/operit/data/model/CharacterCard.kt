package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 角色卡数据模型
 */
@Entity(tableName = "character_cards")
data class CharacterCard(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val characterSetting: String = "", // 角色设定（引导词）
    val openingStatement: String = "", // 新增：开场白
    val otherContent: String = "", // 其他乱七八糟的东西（引导词）
    val attachedTagIds: List<String> = emptyList(), // 附着的标签ID列表
    val advancedCustomPrompt: String = "", // 高级设置的自定义（引导词）
    val marks: String = "", // 备注信息（不会被拼接到提示词中）
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 酒馆角色卡格式数据模型
 */
data class TavernCharacterCard(
    val spec: String = "",
    val spec_version: String = "",
    val data: TavernCharacterData
)

data class TavernCharacterData(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val first_mes: String = "",
    val avatar: String = "",
    val mes_example: String = "",
    val scenario: String = "",
    val creator_notes: String = "",
    val system_prompt: String = "",
    val post_history_instructions: String = "",
    val alternate_greetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val character_version: String = "",
    val extensions: TavernExtensions? = null,
    val character_book: TavernCharacterBook? = null
)

data class TavernExtensions(
    val chub: TavernChubExtension? = null,
    val depth_prompt: TavernDepthPrompt? = null
)

data class TavernChubExtension(
    val id: Long = 0,
    val preset: String? = null,
    val full_path: String = "",
    val extensions: List<String> = emptyList(),
    val expressions: String? = null,
    val alt_expressions: Map<String, String> = emptyMap(),
    val background_image: String? = null,
    val related_lorebooks: List<String> = emptyList()
)

data class TavernDepthPrompt(
    val role: String = "",
    val depth: Int = 0,
    val prompt: String = ""
)

data class TavernCharacterBook(
    val name: String = "",
    val description: String = "",
    val scan_depth: Int = 0,
    val token_budget: Int = 0,
    val recursive_scanning: Boolean = false,
    val extensions: Map<String, Any> = emptyMap(),
    val entries: List<TavernBookEntry> = emptyList()
)

data class TavernBookEntry(
    val name: String = "",
    val keys: List<String> = emptyList(),
    val secondary_keys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val insertion_order: Int = 0,
    val case_sensitive: Boolean = false,
    val priority: Int = 0,
    val id: Int = 0,
    val comment: String = "",
    val selective: Boolean = false,
    val constant: Boolean = false,
    val position: String = "",
    val extensions: Map<String, Any> = emptyMap(),
    val probability: Int = 100,
    val selectiveLogic: Int = 0
) 