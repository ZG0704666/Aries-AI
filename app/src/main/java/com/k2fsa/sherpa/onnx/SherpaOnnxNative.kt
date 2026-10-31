/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.k2fsa.sherpa.onnx

import android.util.Log

/**
 * Aries 项目扩展：sherpa-onnx native 库可用性探测与错误描述。
 *
 * 与 com.k2fsa.sherpa.ncnn.SherpaNcnn 的同名能力对齐：
 * [com.k2fsa.sherpa.onnx.OnlineRecognizer] 的 companion init 会直接
 * System.loadLibrary("sherpa-onnx-jni")，加载失败抛 UnsatisfiedLinkError；
 * 初始化路径必须先经过本探测（[isNativeLibraryReady]），避免异常逃逸到调用方，
 * 并把失败原因经 [nativeLibraryErrorMessage] 透出为可定位的用户提示（R-08 语义）。
 */
object SherpaOnnxNative {

    private const val TAG = "SherpaOnnxNative"

    @Volatile
    private var nativeLoaded = false

    @Volatile
    private var nativeLoadError: Throwable? = null

    @JvmStatic
    fun isNativeLibraryReady(): Boolean = ensureNativeLoaded()

    @JvmStatic
    fun nativeLibraryErrorMessage(): String? = nativeLoadError?.message

    @Synchronized
    private fun ensureNativeLoaded(): Boolean {
        if (nativeLoaded) return true
        if (nativeLoadError != null) return false

        return try {
            // libsherpa-onnx-jni.so 的 DT_NEEDED 携带 libonnxruntime.so，
            // 动态链接器会自动先加载依赖；此处显式先试 onnxruntime，
            // 与 SherpaNcnn 探测逻辑一致，规避个别机型对加载顺序的敏感性。
            runCatching { System.loadLibrary("onnxruntime") }
            System.loadLibrary("sherpa-onnx-jni")
            nativeLoaded = true
            true
        } catch (t: Throwable) {
            nativeLoadError = t
            Log.e(TAG, "Failed to load sherpa-onnx native libs", t)
            false
        }
    }
}
