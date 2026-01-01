package com.ai.assistance.operit.core.avatar.impl.factory

import com.ai.assistance.operit.core.avatar.common.factory.AvatarModelFactory
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.impl.dragonbones.model.DragonBonesAvatarModel
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel
import com.ai.assistance.operit.data.model.DragonBonesModel

/**
 * A concrete implementation of [AvatarModelFactory] that can create virtual avatar models
 * from various data sources and configurations.
 */
class AvatarModelFactoryImpl : AvatarModelFactory {

    override fun createModel(
        id: String,
        name: String,
        type: AvatarType,
        data: Map<String, Any>
    ): AvatarModel? {
        return when (type) {
            AvatarType.DRAGONBONES -> createDragonBonesModel(id, name, data)
            AvatarType.WEBP -> createWebPModel(id, name, data)
            AvatarType.LIVE2D -> {
                // TODO: Implement Live2D model creation when available
                null
            }
            AvatarType.MMD -> {
                // TODO: Implement MMD model creation when available
                null
            }
        }
    }

    override fun createModelFromData(dataModel: Any): AvatarModel? {
        return when (dataModel) {
            is DragonBonesModel -> {
                DragonBonesAvatarModel(dataModel)
            }
            else -> {
                // Try to extract data if it's a map-like structure
                if (dataModel is Map<*, *>) {
                    val dataMap = dataModel as? Map<String, Any> ?: return null
                    val id = dataMap["id"] as? String ?: return null
                    val name = dataMap["name"] as? String ?: return null
                    val typeStr = dataMap["type"] as? String ?: return null
                    val type = try {
                        AvatarType.valueOf(typeStr)
                    } catch (e: IllegalArgumentException) {
                        return null
                    }
                    return createModel(id, name, type, dataMap)
                }
                null
            }
        }
    }

    override fun createDefaultModel(type: AvatarType, baseName: String): AvatarModel? {
        return when (type) {
                         AvatarType.DRAGONBONES -> {
                 // Create a default DragonBones virtual avatar model with placeholder paths
                val defaultData = mapOf(
                    "folderPath" to "assets/avatars/default",
                    "skeletonFile" to "default_ske.json",
                    "textureJsonFile" to "default_tex.json",
                    "textureImageFile" to "default_tex.png",
                    "isBuiltIn" to true
                )
                createDragonBonesModel("default_dragonbones", baseName, defaultData)
            }
                         AvatarType.WEBP -> {
                 // Create a default WebP virtual avatar model with standard emotion mapping
                WebPAvatarModel.createStandard(
                    id = "default_webp",
                    name = baseName,
                    basePath = "assets/avatars/default"
                )
            }
                         AvatarType.LIVE2D -> {
                 // TODO: Implement default Live2D virtual avatar model when available
                null
            }
                         AvatarType.MMD -> {
                 // TODO: Implement default MMD virtual avatar model when available
                null
            }
        }
    }

    override fun validateData(type: AvatarType, data: Map<String, Any>): Boolean {
        return when (type) {
            AvatarType.DRAGONBONES -> {
                val requiredKeys = getRequiredDataKeys(type)
                requiredKeys.all { key -> data.containsKey(key) && data[key] != null }
            }
            AvatarType.WEBP -> {
                val requiredKeys = getRequiredDataKeys(type)
                requiredKeys.all { key -> data.containsKey(key) && data[key] != null }
            }
            else -> false // Unsupported types
        }
    }

    override val supportedTypes: List<AvatarType>
        get() = listOf(AvatarType.DRAGONBONES, AvatarType.WEBP)

    override fun getRequiredDataKeys(type: AvatarType): List<String> {
        return when (type) {
            AvatarType.DRAGONBONES -> listOf(
                "folderPath",
                "skeletonFile",
                "textureJsonFile",
                "textureImageFile"
            )
            AvatarType.WEBP -> listOf(
                "basePath"
            )
            else -> emptyList()
        }
    }

    /**
     * Creates a DragonBones virtual avatar model from the provided data.
     */
    private fun createDragonBonesModel(id: String, name: String, data: Map<String, Any>): AvatarModel? {
        return try {
            val folderPath = data["folderPath"] as? String ?: return null
            val skeletonFile = data["skeletonFile"] as? String ?: return null
            val textureJsonFile = data["textureJsonFile"] as? String ?: return null
            val textureImageFile = data["textureImageFile"] as? String ?: return null
            val isBuiltIn = data["isBuiltIn"] as? Boolean ?: false

            val dataModel = DragonBonesModel(
                id = id,
                name = name,
                folderPath = folderPath,
                skeletonFile = skeletonFile,
                textureJsonFile = textureJsonFile,
                textureImageFile = textureImageFile,
                isBuiltIn = isBuiltIn
            )

            DragonBonesAvatarModel(dataModel)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a WebP virtual avatar model from the provided data.
     */
    private fun createWebPModel(id: String, name: String, data: Map<String, Any>): AvatarModel? {
        return try {
            val basePath = data["basePath"] as? String ?: return null
            val emotionMapData = data["emotionToFileMap"] as? Map<String, String>

            if (emotionMapData != null) {
                // Convert string keys to AvatarEmotion enum
                val emotionMap = emotionMapData.mapNotNull { (emotionStr, fileName) ->
                    try {
                        val emotion = AvatarEmotion.valueOf(emotionStr.uppercase())
                        emotion to fileName
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }.toMap()

                val currentEmotionStr = data["currentEmotion"] as? String
                val currentEmotion = if (currentEmotionStr != null) {
                    try {
                        AvatarEmotion.valueOf(currentEmotionStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        AvatarEmotion.IDLE
                    }
                } else {
                    AvatarEmotion.IDLE
                }

                WebPAvatarModel(
                    id = id,
                    name = name,
                    basePath = basePath,
                    emotionToFileMap = emotionMap,
                    currentEmotion = currentEmotion
                )
            } else {
                // Use standard emotion mapping
                WebPAvatarModel.createStandard(
                    id = id,
                    name = name,
                    basePath = basePath
                )
            }
        } catch (e: Exception) {
            null
        }
    }
} 