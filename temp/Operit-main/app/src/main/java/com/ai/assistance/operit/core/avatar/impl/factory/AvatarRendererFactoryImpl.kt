package com.ai.assistance.operit.core.avatar.impl.factory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.model.IFrameSequenceAvatarModel
import com.ai.assistance.operit.core.avatar.common.model.ISkeletalAvatarModel
import com.ai.assistance.operit.core.avatar.common.factory.AvatarRendererFactory
import com.ai.assistance.operit.core.avatar.impl.dragonbones.view.DragonBonesRenderer
import com.ai.assistance.operit.core.avatar.impl.webp.view.WebPRenderer

/**
 * A concrete implementation of the [AvatarRendererFactory].
 * This factory knows about the specific renderers available in the `impl` layer.
 * It determines which renderer to provide based on the [AvatarModel]'s type.
 */
class AvatarRendererFactoryImpl : AvatarRendererFactory {

    @Composable
    override fun createRenderer(model: AvatarModel): @Composable ((modifier: Modifier, controller: AvatarController) -> Unit)? {
        return when (model.type) {
            AvatarType.DRAGONBONES -> {
                // Ensure the model is of the correct subtype for the renderer.
                val skeletalModel = model as? ISkeletalAvatarModel
                if (skeletalModel != null) {
                    // Return a lambda that correctly invokes the DragonBonesRenderer.
                    { modifier, controller ->
                        DragonBonesRenderer(
                            modifier = modifier,
                            model = skeletalModel,
                            controller = controller,
                            onError = { /* Handle error appropriately */ }
                        )
                    }
                } else {
                    null // Model is DRAGONBONES type but doesn't implement the required interface.
                }
            }
            AvatarType.WEBP -> {
                // Ensure the model is of the correct subtype for the renderer.
                val frameSequenceModel = model as? IFrameSequenceAvatarModel
                if (frameSequenceModel != null) {
                    // Return a lambda that correctly invokes the WebPRenderer.
                    { modifier, controller ->
                        WebPRenderer(
                            modifier = modifier,
                            model = frameSequenceModel,
                            controller = controller,
                            onError = { /* Handle error appropriately */ }
                        )
                    }
                } else {
                    null // Model is WEBP type but doesn't implement the required interface.
                }
            }
            // Other avatar types like LIVE2D, etc., would be handled here.
            else -> null // This factory doesn't support other model types yet.
        }
    }
} 