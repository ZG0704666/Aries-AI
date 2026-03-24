package com.ai.phoneagent.core.common

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    isLenient = true
}
