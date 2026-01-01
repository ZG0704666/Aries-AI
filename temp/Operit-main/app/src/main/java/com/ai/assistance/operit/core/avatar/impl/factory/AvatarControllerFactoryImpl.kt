package com.ai.assistance.operit.core.avatar.impl.factory

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.factory.AvatarControllerFactory
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.model.ISkeletalAvatarModel
import com.ai.assistance.operit.core.avatar.impl.dragonbones.control.rememberDragonBonesAvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.control.rememberWebPAvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel

/**
 * A concrete implementation of [AvatarControllerFactory] that can create controllers
 * for the avatar types supported in the implementation layer.
 */
class AvatarControllerFactoryImpl : AvatarControllerFactory {
    
    @Composable
    override fun createController(model: AvatarModel): AvatarController? {
        return when (model.type) {
            AvatarType.DRAGONBONES -> {
                val skeletalModel = model as? ISkeletalAvatarModel
                if (skeletalModel != null) {
                    rememberDragonBonesAvatarController()
                } else {
                    null
                }
            }
            AvatarType.WEBP -> {
                val webpModel = model as? WebPAvatarModel
                if (webpModel != null) {
                    rememberWebPAvatarController(webpModel)
                } else {
                    null
                }
            }
            AvatarType.LIVE2D -> {
                // TODO: Implement Live2D controller when available
                null
            }
            AvatarType.MMD -> {
                // TODO: Implement MMD controller when available
                null
            }
        }
    }
    
    override fun canCreateController(model: AvatarModel): Boolean {
        return when (model.type) {
            AvatarType.DRAGONBONES -> model is ISkeletalAvatarModel
            AvatarType.WEBP -> model is WebPAvatarModel
            else -> false // LIVE2D and MMD not yet implemented
        }
    }
    
    override val supportedTypes: List<String>
        get() = listOf(
            AvatarType.DRAGONBONES.name,
            AvatarType.WEBP.name
        )
} 