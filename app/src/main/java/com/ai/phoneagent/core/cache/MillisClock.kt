package com.ai.phoneagent.core.cache

fun interface MillisClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = MillisClock(System::currentTimeMillis)
    }
}
