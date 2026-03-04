package com.alibaba.mnnllm.android.llm

import android.util.Pair as AndroidPair
import java.io.File
import org.json.JSONObject

class LlmSession(
    private val configPath: String,
    private val keepHistory: Boolean = true,
) {
    @Volatile private var nativePtr: Long = 0L
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (loaded && nativePtr != 0L) return

        val configFile = File(configPath)
        require(configFile.exists() && configFile.isFile) {
            "Missing model config file: $configPath"
        }

        val mergedConfig =
            runCatching { configFile.readText(Charsets.UTF_8) }.getOrDefault("{}")
        val extraConfig =
            JSONObject()
                .put("keep_history", keepHistory)
                .put("mmap_dir", "")
                .toString()

        val ptr =
            initNative(
                configPath = configPath,
                history = null,
                mergedConfigStr = mergedConfig,
                configJsonStr = extraConfig,
            )

        check(ptr != 0L) { "LlmSession initNative failed: native ptr is 0" }

        nativePtr = ptr
        loaded = true
    }

    @Synchronized
    fun submitFullHistory(
        history: List<kotlin.Pair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any> {
        ensureLoaded()
        val nativeHistory = ArrayList<AndroidPair<String, String>>(history.size)
        for ((role, content) in history) {
            nativeHistory += AndroidPair(role, content)
        }
        return submitFullHistoryNative(nativePtr, nativeHistory, progressListener)
    }

    @Synchronized
    fun reset() {
        if (nativePtr == 0L) return
        resetNative(nativePtr)
    }

    @Synchronized
    fun updateMaxNewTokens(maxNewTokens: Int) {
        if (nativePtr == 0L) return
        if (maxNewTokens <= 0) return
        updateMaxNewTokensNative(nativePtr, maxNewTokens)
    }

    @Synchronized
    fun release() {
        if (nativePtr == 0L) return
        releaseNative(nativePtr)
        nativePtr = 0L
        loaded = false
    }

    private fun ensureLoaded() {
        check(loaded && nativePtr != 0L) {
            "LlmSession is not loaded yet"
        }
    }

    private external fun initNative(
        configPath: String?,
        history: List<String>?,
        mergedConfigStr: String?,
        configJsonStr: String?
    ): Long

    private external fun submitFullHistoryNative(
        nativePtr: Long,
        history: List<AndroidPair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any>

    private external fun resetNative(nativePtr: Long)

    private external fun updateMaxNewTokensNative(nativePtr: Long, maxNewTokens: Int)

    private external fun releaseNative(nativePtr: Long)

    companion object {
        init {
            runCatching { System.loadLibrary("MNN") }
            System.loadLibrary("mnnllmapp")
        }
    }
}
