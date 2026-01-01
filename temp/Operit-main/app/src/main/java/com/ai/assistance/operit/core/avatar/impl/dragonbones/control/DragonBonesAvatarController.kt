package com.ai.assistance.operit.core.avatar.impl.dragonbones.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dragonbones.DragonBonesController as DragonBonesLibController

/**
 * A concrete implementation of [AvatarController] for DragonBones avatars.
 * It wraps the library-specific [DragonBonesLibController] to expose
 * a standardized API for avatar control.
 *
 * @param libController The underlying controller from the DragonBones rendering library.
 */
class DragonBonesAvatarController(
    val libController: DragonBonesLibController
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    override val availableAnimations: List<String>
        get() = libController.animationNames

    override fun setEmotion(newEmotion: AvatarEmotion) {
        // Simple mapping from emotion to animation name.
        // This could be made more sophisticated (e.g., using a map from the model).
        val animationName = when (newEmotion) {
            AvatarEmotion.IDLE -> "idle"
            AvatarEmotion.LISTENING -> "idle" // Assuming 'idle' is the listening anim for now
            AvatarEmotion.THINKING -> "idle_2" // Example
            AvatarEmotion.HAPPY -> "happy"
            AvatarEmotion.SAD -> "sad"
            else -> "idle" // Fallback
        }

        if (availableAnimations.contains(animationName)) {
            libController.playAnimation(animationName, 0f) // Loop indefinitely
            _state.value = _state.value.copy(
                emotion = newEmotion,
                currentAnimation = animationName,
                isLooping = true
            )
        }
    }

    override fun playAnimation(animationName: String, loop: Int) {
        if (availableAnimations.contains(animationName)) {
            libController.playAnimation(animationName, loop.toFloat())
            _state.value = _state.value.copy(
                currentAnimation = animationName,
                isLooping = loop == 0
            )
        }
    }

    // `lookAt` is not supported by the DragonBones implementation.
    override fun lookAt(x: Float, y: Float) {
        // No-op
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings["scale"]?.let { if (it is Number) libController.scale = it.toFloat() }
        settings["translateX"]?.let { if (it is Number) libController.translationX = it.toFloat() }
        settings["translateY"]?.let { if (it is Number) libController.translationY = it.toFloat() }
    }
}

/**
 * A Composable function to create and remember a [DragonBonesAvatarController].
 * This follows the standard pattern for creating controllers in Jetpack Compose.
 * It ensures the controller instance is preserved across recompositions.
 *
 * @return An instance of [DragonBonesAvatarController].
 */
@Composable
fun rememberDragonBonesAvatarController(): DragonBonesAvatarController {
    val libController = com.dragonbones.rememberDragonBonesController()
    return remember { DragonBonesAvatarController(libController) }
} 