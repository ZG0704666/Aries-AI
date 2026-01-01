package com.ai.assistance.operit.core.avatar.impl.webp.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A concrete implementation of [AvatarController] for WebP avatars.
 * This controller manages the state and emotion changes for frame-based animated avatars.
 *
 * @param model The WebP avatar model that contains emotion-to-file mappings.
 */
class WebPAvatarController(
    private var model: WebPAvatarModel
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _currentModel = MutableStateFlow(model)
    val currentModel: StateFlow<WebPAvatarModel> = _currentModel.asStateFlow()

    // Transform properties for scaling and positioning
    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()
    
    private val _translateX = MutableStateFlow(0.0f)
    val translateX: StateFlow<Float> = _translateX.asStateFlow()
    
    private val _translateY = MutableStateFlow(0.0f)
    val translateY: StateFlow<Float> = _translateY.asStateFlow()

    override val availableAnimations: List<String>
        get() = model.emotionToFileMap.values.toList()

    override fun setEmotion(newEmotion: AvatarEmotion) {
        if (model.availableEmotions.contains(newEmotion)) {
            val newModel = model.withEmotion(newEmotion)
            model = newModel
            _currentModel.value = newModel
            
            _state.value = _state.value.copy(
                emotion = newEmotion,
                currentAnimation = newModel.animationPath,
                isLooping = newModel.shouldLoop
            )
        }
    }

    override fun playAnimation(animationName: String, loop: Int) {
        // For WebP avatars, we look up the emotion that corresponds to this animation file
        val emotion = model.emotionToFileMap.entries
            .find { it.value == animationName }
            ?.key ?: return

        setEmotion(emotion)
    }

    override fun lookAt(x: Float, y: Float) {
        // WebP avatars don't support lookAt functionality
        // This is a no-op for frame-based animations
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings["scale"]?.let { if (it is Number) _scale.value = it.toFloat() }
        settings["translateX"]?.let { if (it is Number) _translateX.value = it.toFloat() }
        settings["translateY"]?.let { if (it is Number) _translateY.value = it.toFloat() }
    }

    /**
     * Updates the avatar model. This is useful when switching between different WebP avatar sets.
     */
    fun updateModel(newModel: WebPAvatarModel) {
        model = newModel
        _currentModel.value = newModel
        
        // Reset to current emotion with new model
        setEmotion(_state.value.emotion)
    }

    /**
     * Gets the current animation path based on the active emotion.
     */
    val currentAnimationPath: String
        get() = model.animationPath
}

/**
 * A Composable function to create and remember a [WebPAvatarController].
 * This follows the standard pattern for creating controllers in Jetpack Compose.
 *
 * @param model The WebP avatar model to control.
 * @return An instance of [WebPAvatarController].
 */
@Composable
fun rememberWebPAvatarController(model: WebPAvatarModel): WebPAvatarController {
    return remember(model.id) { WebPAvatarController(model) }
} 