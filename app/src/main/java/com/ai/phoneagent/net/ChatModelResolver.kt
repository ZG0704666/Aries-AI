package com.ai.phoneagent.net

/** 为单次请求解析模型；图像与自动化能力保持显式判断。 */
object ChatModelResolver {
    /**
     * 按优先级解析本次请求应使用的模型：
     * 本地模型 > Aries（有图片用视觉模型，否则用所选/默认）> 第三方自定义 > 默认。
     */
    fun resolve(local: Boolean, aries: Boolean, thirdParty: Boolean, selected: String, custom: String, hasImages: Boolean = false): String = when {
        local -> ModelScopeModelDownloader.QWEN35_MODEL_NAME
        aries && hasImages -> AriesApiClient.ARIES_VISION_MODEL
        aries -> selected.trim().ifBlank { AriesApiClient.ARIES_CHAT_MODEL }
        thirdParty -> custom.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
        else -> AutoGlmClient.DEFAULT_MODEL
    }
}
