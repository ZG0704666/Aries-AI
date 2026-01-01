package com.ai.assistance.operit.core.avatar.common.control

import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import kotlinx.coroutines.flow.StateFlow

/**
 * A universal interface for controlling an avatar's state and behavior.
 * This abstracts away the specific implementation details of the rendering engine.
 */
interface AvatarController {

    /** A flow representing the current state of the avatar. UI components can collect this flow to react to state changes. */
    val state: StateFlow<AvatarState>

    /** For skeletal models, this provides a list of all available animation names. For other types, it may be empty. */
    val availableAnimations: List<String>

    /**
     * Sets the avatar's emotional state.
     * The controller's implementation is responsible for selecting and playing an appropriate animation
     * that corresponds to this emotion.
     *
     * @param newEmotion The new emotional state to set.
     */
    fun setEmotion(newEmotion: AvatarEmotion)

    /**
     * Directly plays a specific animation by name.
     * This is primarily useful for skeletal animation systems that have a rich set of named animations.
     *
     * @param animationName The name of the animation to play.
     * @param loop The number of times to loop the animation. Use 0 for infinite looping.
     */
    fun playAnimation(animationName: String, loop: Int = 1)

    /**
     * Instructs the avatar to look at a specific point on the screen.
     * This is an advanced feature that may only be supported by certain avatar types (e.g., Live2D).
     * Implementations for unsupported types should do nothing.
     *
     * @param x The normalized x-coordinate (-1 to 1).
     * @param y The normalized y-coordinate (-1 to 1).
     */
    fun lookAt(x: Float, y: Float)
    
    /**
     * Updates avatar-specific settings, such as scale or position.
     * Each controller implementation should handle the settings relevant to it.
     *
     * @param settings A map of setting keys to values.
     */
    fun updateSettings(settings: Map<String, Any>) {}
} 