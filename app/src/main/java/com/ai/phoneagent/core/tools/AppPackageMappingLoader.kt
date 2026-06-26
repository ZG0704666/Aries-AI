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
package com.ai.phoneagent.core.tools

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException

/**
 * 应用包名映射加载器
 * 从 assets/app_package_mapping.json 加载映射数据
 */
object AppPackageMappingLoader {

    private const val TAG = "AppPackageMappingLoader"
    private const val MAPPING_FILE = "app_package_mapping.json"

    private var highPriorityKeywords: Map<String, String> = emptyMap()
    private var initialized = false

    /**
     * 初始化，从 assets 加载映射文件
     */
    fun init(context: Context) {
        if (initialized) return

        try {
            val json = context.assets.open(MAPPING_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val keywordsObj = jsonObject.getJSONObject("highPriorityKeywords")

            val mutableMap = mutableMapOf<String, String>()
            val keys = keywordsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mutableMap[key] = keywordsObj.getString(key)
            }

            highPriorityKeywords = mutableMap.toMap()
            initialized = true
            Log.d(TAG, "映射加载完成: ${highPriorityKeywords.size} 条高优先级映射")
        } catch (e: IOException) {
            Log.e(TAG, "加载映射文件失败: ${e.message}")
            highPriorityKeywords = getFallbackMappings()
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "解析映射文件失败: ${e.message}")
            highPriorityKeywords = getFallbackMappings()
            initialized = true
        }
    }

    /**
     * 获取高优先级关键词映射
     */
    fun getHighPriorityKeywords(): Map<String, String> {
        return highPriorityKeywords
    }

    /**
     * Fallback 映射（JSON 加载失败时使用）
     */
    private fun getFallbackMappings(): Map<String, String> {
        return mapOf(
            "移动云" to "com.chinamobile.cmcccloud",
            "移动云手机" to "com.cmcc.pocophone",
            "阿里云盘" to "com.alicloud.infocloud",
            "天翼云盘" to "com.ctc.wsyd",
            "百度网盘" to "com.baidu.netdisk",
            "腾讯微云" to "com.tencent.wecloud",
            "坚果云" to "com.jianguoyun",
            "华为手机" to "com.huawei.system",
            "小米手机" to "com.xiaomi.misettings",
            "OPPO手机" to "com.coloros.safecenter",
            "vivo手机" to "com.iqoo.secure",
            "荣耀手机" to "com.hihonor.system",
            "招商银行" to "cmb.pb",
            "工商银行" to "com.icbc",
            "建设银行" to "com.ccb.ccbnetpay",
            "农业银行" to "com.abchina",
            "中国银行" to "com.chinamobile.boc",
            "邮储银行" to "com.psbc",
            "腾讯视频" to "com.tencent.qqlive",
            "爱奇艺视频" to "com.qiyi.video",
            "优酷视频" to "com.youku.phone",
            "芒果TV" to "com.hunantv.imgo.activity"
        )
    }
}
