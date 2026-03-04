package com.alibaba.mnnllm.android

object MNN {
    external fun nativeGetVersion(): String

    fun getVersion(): String = nativeGetVersion()

    init {
        runCatching { System.loadLibrary("MNN") }
        System.loadLibrary("mnnllmapp")
    }
}
