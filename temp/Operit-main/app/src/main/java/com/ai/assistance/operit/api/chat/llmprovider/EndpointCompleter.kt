package com.ai.assistance.operit.api.chat.llmprovider

import java.net.URL

/**
 * 用于自动补全API端点URL的工具类。
 */
object EndpointCompleter {

    /**
     * 为类似OpenAI的服务自动补全API端点URL。
     * - 如果端点是一个基础URL（例如 https://api.example.com），它会自动附加通用的路径 `/v1/chat/completions`。
     * - 如果端点路径以 `/v1` 结尾（例如 https://my-proxy/custom/v1），则会自动附加 `/chat/completions`。
     * 用户可以在URL末尾添加 '#' 来禁用此功能。
     *
     * @param endpoint 用户提供的端点URL。
     * @return 补全后的或原始的端点URL。
     */
    fun completeEndpoint(endpoint: String): String {
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.endsWith("#")) {
            return trimmedEndpoint.removeSuffix("#")
        }

        val endpointWithoutSlash = trimmedEndpoint.removeSuffix("/")

        // 尝试解析URL并判断它是否为一个需要补全的URL
        try {
            // 使用包含尾部斜杠的端点进行解析，以正确识别路径
            val url = URL(trimmedEndpoint)
            val path = url.path.removeSuffix("/")

            // 1. 如果路径为空 (e.g., https://api.example.com)，则补全为标准路径
            if (path.isNullOrEmpty()) {
                return "$endpointWithoutSlash/v1/chat/completions"
            }

            // 2. 如果路径以 /v1 结尾 (e.g., https://api.example.com/custom/v1)，则仅补全后续部分
            if (path.endsWith("/v1", ignoreCase = true)) {
                return "$endpointWithoutSlash/chat/completions"
            }
        } catch (e: Exception) {
            // 如果不是一个有效的URL，则不进行任何操作
        }
        
        // 如果不符合补全特征，则返回原始输入
        return endpoint
    }
} 