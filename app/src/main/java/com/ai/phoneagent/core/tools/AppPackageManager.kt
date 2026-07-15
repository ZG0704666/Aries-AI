package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.core.cache.MillisClock
import com.ai.phoneagent.core.cache.ThreadSafeLruCache
import com.ai.phoneagent.core.tools.extended.ExtendedAppMapping
import java.util.concurrent.atomic.AtomicReference

/**
 * Caches installed applications and resolves user-facing app names to package names.
 *
 * Readers always observe one immutable snapshot containing the installed-app list,
 * lookup indexes, and refresh timestamp from the same generation.
 */
class AppPackageManager(
    private val extendedAppMapping: ExtendedAppMapping,
    private val clock: MillisClock = MillisClock.SYSTEM,
) {
    private data class AppSnapshot(
        val installedApps: List<Pair<String, String>>,
        val installedByPackage: Map<String, String>,
        val nameToPackage: Map<String, String>,
        val updatedAtMillis: Long,
    ) {
        companion object {
            val EMPTY =
                AppSnapshot(
                    installedApps = emptyList(),
                    installedByPackage = emptyMap(),
                    nameToPackage = emptyMap(),
                    updatedAtMillis = 0L,
                )
        }
    }

    private data class ResolvedPackage(
        val snapshot: AppSnapshot,
        val packageName: String,
    )

    private val snapshotRef = AtomicReference(AppSnapshot.EMPTY)
    private val refreshLock = Any()
    private val resolveCache =
        ThreadSafeLruCache<String, ResolvedPackage>(
            maxSize = RESOLVE_CACHE_MAX_ENTRIES,
            ttlMillis = RESOLVE_CACHE_TTL_MS,
            clock = clock,
        )

    private val highPriorityKeywords =
        mapOf(
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
            "芒果TV" to "com.hunantv.imgo.activity",
        )

    fun initializeCache(context: Context) {
        val now = clock.nowMillis()
        if (isSnapshotFresh(snapshotRef.get(), now)) return

        synchronized(refreshLock) {
            val refreshNow = clock.nowMillis()
            if (isSnapshotFresh(snapshotRef.get(), refreshNow)) return

            val newSnapshot =
                runCatching { buildSnapshot(context.applicationContext, refreshNow) }
                    .onFailure { Log.e(TAG, "应用缓存刷新失败", it) }
                    .getOrNull()
                    ?: return

            snapshotRef.set(newSnapshot)
            resolveCache.clear()
            Log.d(
                TAG,
                "应用缓存初始化完成: 已安装应用=${newSnapshot.installedApps.size}, " +
                    "总映射=${newSnapshot.nameToPackage.size}",
            )
        }
    }

    private fun isSnapshotFresh(snapshot: AppSnapshot, now: Long): Boolean {
        return snapshot.installedApps.isNotEmpty() &&
            now - snapshot.updatedAtMillis < CACHE_VALIDITY_MS
    }

    private fun buildSnapshot(context: Context, updatedAtMillis: Long): AppSnapshot {
        val installedApps = ArrayList<Pair<String, String>>()
        val installedByPackage = LinkedHashMap<String, String>()
        val nameToPackage = LinkedHashMap<String, String>()

        highPriorityKeywords.forEach { (name, packageName) ->
            nameToPackage[name.lowercase()] = packageName
            nameToPackage[name] = packageName
            nameToPackage[packageName.lowercase()] = packageName
        }

        extendedAppMapping.getAllMappings().forEach { (name, packageName) ->
            nameToPackage.putIfAbsent(name.lowercase(), packageName)
            nameToPackage.putIfAbsent(packageName.lowercase(), packageName)
        }

        val packageManager = context.packageManager
        val applications =
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        applications.forEach { app ->
            if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                isImportantSystemApp(app.packageName)
            ) {
                val appName = packageManager.getApplicationLabel(app).toString()
                installedApps += app.packageName to appName
                installedByPackage[app.packageName] = appName
                nameToPackage[appName.lowercase()] = app.packageName
                nameToPackage[app.packageName.lowercase()] = app.packageName
            }
        }

        return AppSnapshot(
            installedApps = installedApps.toList(),
            installedByPackage = installedByPackage.toMap(),
            nameToPackage = nameToPackage.toMap(),
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun isImportantSystemApp(packageName: String): Boolean {
        return packageName in
            setOf(
                "com.android.settings",
                "com.android.chrome",
                "com.google.android.gms",
                "com.android.dialer",
                "com.android.phone",
                "com.android.contacts",
                "com.android.messaging",
            )
    }

    fun resolvePackageName(query: String?): String? {
        if (query.isNullOrBlank()) return null

        val trimmedQuery = query.trim()
        val lowerQuery = trimmedQuery.lowercase()
        val snapshot = snapshotRef.get()
        resolveCache.get(lowerQuery)
            ?.takeIf { it.snapshot === snapshot }
            ?.let { return it.packageName }

        fun record(packageName: String): String {
            resolveCache.put(lowerQuery, ResolvedPackage(snapshot, packageName))
            return packageName
        }

        snapshot.nameToPackage[lowerQuery]?.let { return record(it) }
        snapshot.nameToPackage[trimmedQuery]?.let { return record(it) }

        highPriorityKeywords.keys
            .firstOrNull { keyword ->
                lowerQuery.contains(keyword.lowercase()) ||
                    keyword.lowercase().contains(lowerQuery)
            }
            ?.let { keyword -> highPriorityKeywords[keyword]?.let { return record(it) } }

        highPriorityKeywords.entries
            .firstOrNull { (keyword, _) ->
                keyword.lowercase().contains(lowerQuery) && keyword.length > lowerQuery.length
            }
            ?.value
            ?.let { return record(it) }

        if (lowerQuery.contains(" ") || lowerQuery.length >= 4) {
            snapshot.nameToPackage.entries
                .firstOrNull { (name, _) -> isWordBoundaryMatch(lowerQuery, name) }
                ?.value
                ?.let { return record(it) }
        }

        snapshot.installedApps
            .firstOrNull { (_, appName) ->
                val lowerAppName = appName.lowercase()
                lowerQuery.length >= 2 &&
                    (lowerAppName == lowerQuery ||
                        lowerAppName.contains(lowerQuery) ||
                        isCompleteWordMatch(lowerQuery, lowerAppName))
            }
            ?.first
            ?.let { return record(it) }

        snapshot.nameToPackage.entries
            .firstOrNull { (name, _) ->
                name.length > lowerQuery.length &&
                    name.contains(lowerQuery) &&
                    !name.startsWith(".") &&
                    !lowerQuery.startsWith("com")
            }
            ?.value
            ?.let { return record(it) }

        return if (isValidPackageName(trimmedQuery)) record(trimmedQuery) else null
    }

    private fun isWordBoundaryMatch(query: String, name: String): Boolean {
        val queryWords = query.split(" ", "，", ",", "·", "•")
        val nameWords = name.split(" ", "，", ",", "·", "•")
        return queryWords.all { word ->
            nameWords.any { nameWord -> nameWord.contains(word) || word.contains(nameWord) }
        }
    }

    private fun isCompleteWordMatch(query: String, text: String): Boolean {
        return text.split(Regex("[\\s_\\-]")).any { word ->
            word.equals(query, ignoreCase = true) ||
                word.startsWith(query, ignoreCase = true)
        }
    }

    private fun isValidPackageName(name: String): Boolean {
        return name.matches(
            Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"),
        )
    }

    fun resolvePackageByLabel(
        service: PhoneAgentAccessibilityService,
        appName: String,
    ): String? {
        return resolvePackageName(appName)
    }

    fun getAppName(packageName: String): String {
        return snapshotRef.get().installedByPackage[packageName] ?: packageName
    }

    fun getAllInstalledApps(): List<Pair<String, String>> {
        return snapshotRef.get().installedApps
    }

    fun clearCache() {
        synchronized(refreshLock) {
            snapshotRef.set(AppSnapshot.EMPTY)
            resolveCache.clear()
        }
    }

    fun getStats(): Map<String, Any> {
        val snapshot = snapshotRef.get()
        return mapOf(
            "totalMappings" to snapshot.nameToPackage.size,
            "installedApps" to snapshot.installedApps.size,
            "highPriorityKeywords" to highPriorityKeywords.size,
            "extendedMappings" to extendedAppMapping.getAllMappings().size,
            "lastUpdateTime" to snapshot.updatedAtMillis,
        )
    }

    companion object {
        private const val TAG = "AppPackageManager"
        private const val RESOLVE_CACHE_TTL_MS = 300_000L
        private const val RESOLVE_CACHE_MAX_ENTRIES = 256
        private const val CACHE_VALIDITY_MS = 300_000L
    }
}
