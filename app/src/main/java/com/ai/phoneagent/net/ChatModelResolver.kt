package com.ai.phoneagent.net

/** Resolves the model once for a request; image and automation capabilities stay explicit. */
object ChatModelResolver {
    fun resolve(local: Boolean, aries: Boolean, thirdParty: Boolean, selected: String, custom: String, hasImages: Boolean = false): String = when {
        local -> ModelScopeModelDownloader.QWEN35_MODEL_NAME
        aries && hasImages -> AriesApiClient.ARIES_VISION_MODEL
        aries -> selected.trim().ifBlank { AriesApiClient.ARIES_CHAT_MODEL }
        thirdParty -> custom.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
        else -> AutoGlmClient.DEFAULT_MODEL
    }
}
