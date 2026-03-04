package com.alibaba.mnnllm.android.llm

interface GenerateProgressListener {
    fun onProgress(progress: String?): Boolean
}
