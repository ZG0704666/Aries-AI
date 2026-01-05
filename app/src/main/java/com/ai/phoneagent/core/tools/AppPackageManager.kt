package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 应用包名管理器
 * 负责缓存和快速查询已安装应用列表
 */
object AppPackageManager {
    
    // 应用缓存（包名 -> 应用名）
    private val appCache = mutableMapOf<String, String>()
    
    // 反向映射（应用名 -> 包名）
    private val appNameToPackage = mutableMapOf<String, String>()
    
    private var lastUpdateTime = 0L
    private const val CACHE_VALIDITY_MS = 300000 // 5分钟缓存时间
    
    /**
     * 初始化应用列表缓存
     */
    fun initializeCache(context: Context) {
        val currentTime = System.currentTimeMillis()
        
        // 如果缓存有效，直接返回
        if (currentTime - lastUpdateTime < CACHE_VALIDITY_MS && appCache.isNotEmpty()) {
            return
        }
        
        appCache.clear()
        appNameToPackage.clear()
        
        try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (app in installedPackages) {
                // 只缓存用户安装的应用（非系统应用）
                if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isImportantSystemApp(app.packageName)) {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    appCache[app.packageName] = appName
                    appNameToPackage[appName.lowercase()] = app.packageName
                    
                    // 也缓存包名本身（以防用户直接用包名）
                    appNameToPackage[app.packageName.lowercase()] = app.packageName
                }
            }
            
            lastUpdateTime = currentTime
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 是否为重要系统应用（保留）
     */
    private fun isImportantSystemApp(packageName: String): Boolean {
        val importantApps = setOf(
            "com.android.settings",           // 设置
            "com.android.chrome",              // Chrome
            "com.google.android.gms",          // Google服务
            "com.android.dialer",              // 拨号
            "com.android.phone",               // 电话
            "com.android.contacts",            // 联系人
            "com.android.messaging"            // 短信
        )
        return importantApps.contains(packageName)
    }
    
    /**
     * 根据应用名或包名解析包名
     * @param query 应用名或包名
     * @return 包名，未找到返回null
     */
    fun resolvePackageName(query: String?): String? {
        if (query.isNullOrBlank()) return null
        
        val trimmedQuery = query.trim()
        
        // 首先尝试直接匹配（包名或精确应用名）
        appNameToPackage[trimmedQuery.lowercase()]?.let { return it }
        appNameToPackage[trimmedQuery]?.let { return it }
        
        // 模糊匹配：查找包含该关键字的应用
        val keyword = trimmedQuery.lowercase()
        appNameToPackage.entries.firstOrNull { (name, _) ->
            name.contains(keyword) && !name.startsWith(".")
        }?.value?.let { return it }
        
        // 反向查找：查找应用名包含关键字
        appCache.entries.firstOrNull { (_, appName) ->
            appName.lowercase().contains(keyword)
        }?.key?.let { return it }
        
        return null
    }
    
    /**
     * 获取应用名
     */
    fun getAppName(packageName: String): String {
        return appCache[packageName] ?: packageName
    }
    
    /**
     * 获取所有已安装应用列表（用于显示）
     */
    fun getAllInstalledApps(): List<Pair<String, String>> {
        return appCache.map { (packageName, appName) ->
            packageName to appName
        }
    }
    
    /**
     * 清除缓存（手动刷新）
     */
    fun clearCache() {
        appCache.clear()
        appNameToPackage.clear()
        lastUpdateTime = 0L
    }
}
