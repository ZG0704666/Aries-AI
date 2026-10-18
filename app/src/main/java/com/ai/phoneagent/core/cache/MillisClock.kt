package com.ai.phoneagent.core.cache

/** 毫秒级时钟抽象，便于测试注入假时钟。 */
fun interface MillisClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = MillisClock(System::currentTimeMillis)
    }
}
