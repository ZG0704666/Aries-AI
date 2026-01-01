package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.util.AppLogger

private const val TAG = "JsAssetLoader"

/**
 * 加载 UINode.js 文件 从 assets 目录读取并返回 JS 代码
 *
 * @param context Android上下文
 * @return UINode.js 代码内容字符串
 */
fun loadUINodeJs(context: Context): String {
    return try {
        val inputStream = context.assets.open("js/UINode.js")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        String(buffer)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error loading UINode.js: ${e.message}", e)
        // 如果加载失败，返回空字符串
        ""
    }
}

/**
 * 加载 AndroidUtils.js 文件 从 assets 目录读取并返回 JS 代码
 *
 * @param context Android上下文
 * @return AndroidUtils.js 代码内容字符串
 */
fun loadAndroidUtilsJs(context: Context): String {
    return try {
        val inputStream = context.assets.open("js/AndroidUtils.js")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        String(buffer)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error loading AndroidUtils.js: ${e.message}", e)
        // 如果加载失败，返回空字符串
        ""
    }
}

/**
 * 加载 OkHttp3.js 文件 从 assets 目录读取并返回 JS 代码
 *
 * @param context Android上下文
 * @return OkHttp3.js 代码内容字符串
 */
fun loadOkHttp3Js(context: Context): String {
    return try {
        val inputStream = context.assets.open("js/OkHttp3.js")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        String(buffer)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error loading OkHttp3.js: ${e.message}", e)
        // 如果加载失败，返回空字符串
        ""
    }
}
