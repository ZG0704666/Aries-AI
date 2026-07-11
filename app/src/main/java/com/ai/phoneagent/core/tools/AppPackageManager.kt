package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.core.cache.ThreadSafeLruCache
import com.ai.phoneagent.core.tools.extended.ExtendedAppMapping

/**
 * 应用包名管理器
 * 负责缓存和快速查询已安装应用列表
 *
 * 匹配优先级：
 * 1. 精确匹配（包名、应用名完全相等）
 * 2. 别名匹配（预设的应用别名）
 * 3. 单词边界匹配（避免"云"匹配"阿里云盘"和"移动云"）
 * 4. 智能模糊匹配（作为最后手段）
 */
class AppPackageManager {

    // 应用缓存（包名 -> 应用名）
    // TTL 设为 Long.MAX_VALUE：appCache 由 initializeCache() 整体刷新，无需单条目过期
    private val appCache = ThreadSafeLruCache<String, String>(
        maxSize = APP_CACHE_MAX_ENTRIES,
        ttlMillis = Long.MAX_VALUE
    )

    // 反向映射（应用名/别名 -> 包名）
    // 容量需容纳 ExtendedAppMapping(565+)×2 + highPriorityKeywords×3 + 已安装应用×2，
    // 256 会过度淘汰导致映射丢失，故使用 2048
    private val appNameToPackage = ThreadSafeLruCache<String, String>(
        maxSize = APP_NAME_CACHE_MAX_ENTRIES,
        ttlMillis = Long.MAX_VALUE
    )

    // 高优先级关键词映射（用于区分容易混淆的应用）
    private val highPriorityKeywords = mapOf(
        // 云服务区分
        "移动云" to "com.chinamobile.cmcccloud",
        "移动云手机" to "com.cmcc.pocophone",
        "阿里云盘" to "com.alicloud.infocloud",
        "天翼云盘" to "com.ctc.wsyd",
        "百度网盘" to "com.baidu.netdisk",
        "腾讯微云" to "com.tencent.wecloud",
        "坚果云" to "com.jianguoyun",

        // 手机品牌区分
        "华为手机" to "com.huawei.system",
        "小米手机" to "com.xiaomi.misettings",
        "OPPO手机" to "com.coloros.safecenter",
        "vivo手机" to "com.iqoo.secure",
        "荣耀手机" to "com.hihonor.system",

        // 银行类区分
        "招商银行" to "cmb.pb",
        "工商银行" to "com.icbc",
        "建设银行" to "com.ccb.ccbnetpay",
        "农业银行" to "com.abchina",
        "中国银行" to "com.chinamobile.boc",
        "邮储银行" to "com.psbc",

        // 视频类区分
        "腾讯视频" to "com.tencent.qqlive",
        "爱奇艺视频" to "com.qiyi.video",
        "优酷视频" to "com.youku.phone",
        "芒果TV" to "com.hunantv.imgo.activity",
    )

    // 解析结果缓存（带 TTL，复用 ThreadSafeLruCache 内置过期与 LRU）
    private val resolveCache = ThreadSafeLruCache<String, String>(
        maxSize = RESOLVE_CACHE_MAX_ENTRIES,
        ttlMillis = RESOLVE_CACHE_TTL_MS
    )

    private var lastUpdateTime = 0L

    /**
     * 加载所有映射到缓存
     */
    private fun loadAllMappings() {
        // 1. 加载高优先级关键词（最先加载，优先匹配）
        highPriorityKeywords.forEach { (name, pkg) ->
            val lowerName = name.lowercase()
            appNameToPackage.put(lowerName, pkg)
            // 保留原始名称作为精确匹配键
            appNameToPackage.put(name, pkg)
            appNameToPackage.put(pkg.lowercase(), pkg)
        }

        // 2. 加载扩展映射表 (250+ 应用)
        ExtendedAppMapping.getAllMappings().forEach { (name, pkg) ->
            val lowerName = name.lowercase()
            appNameToPackage.put(lowerName, pkg)
            appNameToPackage.put(pkg.lowercase(), pkg)
        }

        // 3. 计算统计信息
        logMappingStats()
    }

    private fun logMappingStats() {
        val totalMappings = appNameToPackage.size()
        val highPriorityCount = highPriorityKeywords.size
        val extendedCount = ExtendedAppMapping.getAllMappings().size

        android.util.Log.d(TAG, "映射加载完成: 总数=$totalMappings, " +
                "高优先级=$highPriorityCount, 扩展=$extendedCount")
    }

    private fun cacheResolution(key: String, packageName: String) {
        // TTL 与 LRU 淘汰由 ThreadSafeLruCache 内部处理
        resolveCache.put(key, packageName)
    }

    /**
     * 初始化应用列表缓存
     */
    fun initializeCache(context: Context) {
        val currentTime = System.currentTimeMillis()

        // 如果缓存有效，直接返回
        if (currentTime - lastUpdateTime < CACHE_VALIDITY_MS && appCache.size() > 0) {
            return
        }

        appCache.clear()
        appNameToPackage.clear()
        resolveCache.clear()

        // 加载所有预设映射
        loadAllMappings()

        try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedPackages) {
                // 只缓存用户安装的应用（非系统应用）
                if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isImportantSystemApp(app.packageName)) {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    appCache.put(app.packageName, appName)

                    // 缓存应用名（小写）用于模糊匹配
                    appNameToPackage.put(appName.lowercase(), app.packageName)
                    appNameToPackage.put(app.packageName.lowercase(), app.packageName)
                }
            }

            lastUpdateTime = currentTime

            android.util.Log.d(TAG, "应用缓存初始化完成: 已安装应用=${appCache.size()}, 总映射=${appNameToPackage.size()}")
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
     * 智能解析包名（防止误匹配的核心逻辑）
     * @param query 应用名或包名
     * @return 包名，未找到返回null
     */
    fun resolvePackageName(query: String?): String? {
        if (query.isNullOrBlank()) return null

        val trimmedQuery = query.trim()
        val lowerQuery = trimmedQuery.lowercase()

        // 命中LRU缓存（TTL 由 ThreadSafeLruCache 内部判定）
        resolveCache.get(lowerQuery)?.let { return it }

        fun record(pkg: String): String {
            cacheResolution(lowerQuery, pkg)
            return pkg
        }

        // ===== 优先级1: 精确匹配 =====
        // 1.1 包名直接匹配
        appNameToPackage.get(lowerQuery)?.let { return record(it) }
        appNameToPackage.get(trimmedQuery)?.let { return record(it) }

        // 1.2 应用名精确匹配（不区分大小写）
        appNameToPackage.snapshot().firstOrNull { (name, _) ->
            name.equals(lowerQuery) || name.equals(trimmedQuery)
        }?.second?.let { return record(it) }

        // ===== 优先级2: 高优先级关键词匹配（防止误匹配的关键） =====
        // 2.1 完整关键词匹配（优先匹配完整的关键词如"移动云手机"）
        highPriorityKeywords.keys.firstOrNull { keyword ->
            lowerQuery.contains(keyword.lowercase()) ||
            keyword.lowercase().contains(lowerQuery)
        }?.let { keyword ->
            highPriorityKeywords[keyword]?.let { return record(it) }
        }

        // 2.2 反向检查：如果查询词是某个高优先级词的子串，优先返回该高优先级包
        highPriorityKeywords.entries.firstOrNull { (keyword, _) ->
            keyword.lowercase().contains(lowerQuery) &&
            keyword.length > lowerQuery.length
        }?.value?.let { return record(it) }

        // ===== 优先级3: 单词边界匹配 =====
        // 避免"云"匹配到"阿里云盘"和"移动云"
        // 只有当查询包含空格或足够长时才进行单词边界匹配
        if (lowerQuery.contains(" ") || lowerQuery.length >= 4) {
            appNameToPackage.snapshot().firstOrNull { (name, _) ->
                // 检查是否是单词边界匹配
                isWordBoundaryMatch(lowerQuery, name)
            }?.second?.let { return record(it) }
        }

        // ===== 优先级4: 智能模糊匹配（最后手段） =====
        // 4.1 反向查找：已安装应用中，应用名包含查询词
        val installedMatch = appCache.snapshot().firstOrNull { (_, appName) ->
            val lowerAppName = appName.lowercase()
            // 避免太短的匹配，至少匹配2个字符
            lowerQuery.length >= 2 && (
                lowerAppName == lowerQuery ||
                lowerAppName.contains(lowerQuery) ||
                // 检查是否是完整单词匹配
                isCompleteWordMatch(lowerQuery, lowerAppName)
            )
        }?.first

        if (installedMatch != null) {
            return record(installedMatch)
        }

        // 4.2 预设映射中的模糊匹配
        val mappedMatch = appNameToPackage.snapshot().firstOrNull { (name, _) ->
            name.length > lowerQuery.length && // 避免匹配太短的名称
            name.contains(lowerQuery) &&
            !name.startsWith(".") && // 排除包名片段
            !lowerQuery.startsWith("com") // 如果查询不是以com开头，避免匹配包名
        }?.second

        if (mappedMatch != null) {
            return record(mappedMatch)
        }

        // 4.3 如果是有效的包名格式，直接返回（作为最后手段）
        if (isValidPackageName(trimmedQuery)) {
            return record(trimmedQuery)
        }

        return null
    }

    /**
     * 检查是否是单词边界匹配
     * 例如："云手机" 应该匹配 "移动云手机" 而不是单独的"云"
     */
    private fun isWordBoundaryMatch(query: String, name: String): Boolean {
        val queryWords = query.split(" ", "，", ",", "·", "•")
        val nameWords = name.split(" ", "，", ",", "·", "•")

        // 检查查询词是否全部包含在名称中
        return queryWords.all { word ->
            nameWords.any { nameWord ->
                nameWord.contains(word) || word.contains(nameWord)
            }
        }
    }

    /**
     * 检查是否是完整单词匹配
     */
    private fun isCompleteWordMatch(query: String, text: String): Boolean {
        val words = text.split(Regex("[\\s_\\-]"))
        return words.any { word ->
            word.equals(query, ignoreCase = true) ||
            word.startsWith(query, ignoreCase = true)
        }
    }

    /**
     * 检查是否是有效的Android包名格式
     */
    private fun isValidPackageName(name: String): Boolean {
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"))
    }

    /**
     * 根据应用标签解析包名（兼容旧API）
     */
    fun resolvePackageByLabel(
        service: PhoneAgentAccessibilityService,
        appName: String
    ): String? {
        return resolvePackageName(appName)
    }

    /**
     * 获取应用名
     */
    fun getAppName(packageName: String): String {
        return appCache.get(packageName) ?: packageName
    }

    /**
     * 获取所有已安装应用列表（用于显示）
     */
    fun getAllInstalledApps(): List<Pair<String, String>> {
        return appCache.snapshot()
    }

    /**
     * 清除缓存（手动刷新）
     */
    fun clearCache() {
        appCache.clear()
        appNameToPackage.clear()
        resolveCache.clear()
        lastUpdateTime = 0L
    }

    /**
     * 获取映射统计信息
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalMappings" to appNameToPackage.size(),
            "installedApps" to appCache.size(),
            "highPriorityKeywords" to highPriorityKeywords.size,
            "extendedMappings" to ExtendedAppMapping.getAllMappings().size
        )
    }

    companion object {
        private const val TAG = "AppPackageManager"
        private const val RESOLVE_CACHE_TTL_MS = 300_000L // 5分钟
        private const val RESOLVE_CACHE_MAX_ENTRIES = 256
        private const val APP_CACHE_MAX_ENTRIES = 256
        private const val APP_NAME_CACHE_MAX_ENTRIES = 2048
        private const val CACHE_VALIDITY_MS = 300000L // 5分钟缓存时间

        @Volatile private var fallbackInstance: AppPackageManager? = null

        private fun getInstance(): AppPackageManager =
            runCatching {
                org.koin.core.context.GlobalContext.get().get<AppPackageManager>()
            }.getOrNull() ?: (fallbackInstance ?: AppPackageManager().also { fallbackInstance = it })

        fun initializeCache(context: Context) = getInstance().initializeCache(context)
        fun resolvePackageName(query: String?): String? = getInstance().resolvePackageName(query)
        fun resolvePackageByLabel(
            service: PhoneAgentAccessibilityService,
            appName: String
        ): String? = getInstance().resolvePackageByLabel(service, appName)
        fun getAppName(packageName: String): String = getInstance().getAppName(packageName)
        fun getAllInstalledApps(): List<Pair<String, String>> = getInstance().getAllInstalledApps()
        fun clearCache() = getInstance().clearCache()
        fun getStats(): Map<String, Any> = getInstance().getStats()
    }
}
