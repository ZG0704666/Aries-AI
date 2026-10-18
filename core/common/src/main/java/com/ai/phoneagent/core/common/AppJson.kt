package com.ai.phoneagent.core.common

import kotlinx.serialization.json.Json

/**
 * 全项目共享的 JSON 解析/序列化配置。
 *
 * 宽容处理未知字段与宽松语法，便于兼容服务端返回；序列化时省略默认值以精简报文。
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    isLenient = true
}
