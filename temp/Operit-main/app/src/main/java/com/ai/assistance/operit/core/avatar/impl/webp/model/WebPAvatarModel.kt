package com.ai.assistance.operit.core.avatar.impl.webp.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.model.IFrameSequenceAvatarModel
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion

/**
 * A concrete implementation of [IFrameSequenceAvatarModel] for WebP avatars.
 * This class represents a WebP-based avatar that supports frame sequence animation.
 *
 * @property id The unique identifier for this avatar.
 * @property name The display name of the avatar.
 * @property basePath The base path in assets where WebP files are stored.
 * @property emotionToFileMap A mapping from emotions to corresponding WebP file names.
 * @property currentEmotion The currently active emotion (used to determine animation path).
 */
data class WebPAvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val emotionToFileMap: Map<AvatarEmotion, String>,
    val currentEmotion: AvatarEmotion = AvatarEmotion.IDLE
) : IFrameSequenceAvatarModel {

    override val type: AvatarType
        get() = AvatarType.WEBP

    override val animationPath: String
        get() {
            val fileName = emotionToFileMap[currentEmotion] ?: emotionToFileMap[AvatarEmotion.IDLE] ?: ""
            return if (fileName.isNotEmpty()) {
                "$basePath/$fileName"
            } else {
                ""
            }
        }

    override val shouldLoop: Boolean
        get() = true

    override val repeatCount: Int
        get() = 0 // Infinite loop

    /**
     * Creates a new instance with the specified emotion.
     */
    fun withEmotion(emotion: AvatarEmotion): WebPAvatarModel {
        return copy(currentEmotion = emotion)
    }

    /**
     * Gets all available emotions for this avatar.
     */
    val availableEmotions: Set<AvatarEmotion>
        get() = emotionToFileMap.keys

    companion object {
        /**
         * Creates a WebP avatar model with a standard emotion-to-file mapping.
         * This assumes a conventional file naming scheme.
         */
        fun createStandard(
            id: String,
            name: String,
            basePath: String,
            fileExtension: String = "webp"
        ): WebPAvatarModel {
            val emotionMap = mapOf(
                AvatarEmotion.IDLE to "idle.$fileExtension",
                AvatarEmotion.LISTENING to "listening.$fileExtension",
                AvatarEmotion.THINKING to "thinking.$fileExtension",
                AvatarEmotion.HAPPY to "happy.$fileExtension",
                AvatarEmotion.SAD to "sad.$fileExtension",
                AvatarEmotion.CONFUSED to "confused.$fileExtension",
                AvatarEmotion.SURPRISED to "surprised.$fileExtension"
            )
            return WebPAvatarModel(
                id = id,
                name = name,
                basePath = basePath,
                emotionToFileMap = emotionMap
            )
        }
    }
} 