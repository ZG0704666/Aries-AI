package com.ai.phoneagent

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.io.IOException

/**
 * 应用名 → 包名映射（运行时从 `assets/app_package_mapping.json` 加载）。
 *
 * 历史版本在源码中硬编码了 294 条映射（含 79 条重复键值），无法在不发版的情况下扩展应用支持。
 * 现改为运行时从 assets 中的 JSON 文件加载，并通过 [load] 缓存解析结果。
 *
 * 用法：
 * ```
 * AppPackageMapping.load(context)           // 在应用启动时调用一次（如 Application.onCreate）
 * AppPackageMapping.resolve("微信")         // 返回 "com.tencent.mm"
 * AppPackageMapping.getPackageFor("微信")   // resolve 的别名
 * AppPackageMapping.bestMatchInText("帮我打开微信给张三发消息")
 * ```
 */
object AppPackageMapping {

    /** assets 中映射文件的路径。 */
    private const val ASSET_PATH = "app_package_mapping.json"

    data class PackageMappingEntry(
            @SerializedName("appName") val appName: String,
            @SerializedName("packageName") val packageName: String,
    )

    data class PackageMappingFile(
            @SerializedName("version") val version: Int,
            @SerializedName("mappings") val mappings: List<PackageMappingEntry>,
    )

    data class Match(
            val appLabel: String,
            val packageName: String,
            val start: Int,
            val end: Int,
    )

    private val gson by lazy { Gson() }

    // 缓存使用 @Volatile + @Synchronized 实现「单次加载」语义（等价于 by lazy{}，
    // 但因为 load(context) 需要外部参数，无法直接使用 lazy 委托）。
    @Volatile private var cachedRaw: Map<String, String>? = null

    @Volatile private var cachedNormalized: Map<String, String>? = null

    /**
     * 同步加载 assets 中的映射文件并缓存。重复调用返回同一缓存实例。
     *
     * @return 已加载的原始（未归一化）映射 Map。
     * @throws IOException 当 assets 读取失败或 JSON 格式非法时抛出。
     */
    @Synchronized
    fun load(context: Context): Map<String, String> {
        cachedRaw?.let { return it }
        val json =
                context.assets.open(ASSET_PATH).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
        return cacheFromJson(json)
    }

    /**
     * 解析 JSON 字符串为映射 Map（纯函数，无副作用，便于单测）。
     *
     * @throws IOException 当 JSON 为空、解析失败或格式非法时抛出。
     */
    fun parseJson(jsonString: String): Map<String, String> {
        if (jsonString.isBlank()) {
            throw IOException("Empty JSON content for app package mapping")
        }
        return try {
            val parsed: PackageMappingFile =
                    gson.fromJson(jsonString, PackageMappingFile::class.java)
                            ?: throw IOException("Parsed app package mapping JSON was null")
            parsed.mappings.associate { it.appName to it.packageName }
        } catch (e: JsonSyntaxException) {
            throw IOException("Invalid JSON format for app package mapping", e)
        }
    }

    /**
     * 解析 JSON 并缓存结果。用于不依赖 [Context] 的场景（如测试或自定义加载源）。
     * 重复调用返回首次解析的缓存实例。
     */
    @Synchronized
    fun loadFromJson(jsonString: String): Map<String, String> {
        cachedRaw?.let { return it }
        return cacheFromJson(jsonString)
    }

    @Synchronized
    private fun cacheFromJson(jsonString: String): Map<String, String> {
        val raw = parseJson(jsonString)
        cachedRaw = raw
        cachedNormalized = raw.entries.associate { normalize(it.key) to it.value }
        return raw
    }

    /** 重置缓存（仅用于测试，避免单例状态污染）。 */
    @Synchronized
    fun resetCache() {
        cachedRaw = null
        cachedNormalized = null
    }

    private fun normalize(s: String): String {
        return s.trim().lowercase().replace("\\s+".toRegex(), "").replace("-", "")
    }

    /**
     * 根据应用名（或别名）解析包名。返回 null 表示未匹配。
     *
     * 注意：调用前需先调用 [load] 或 [loadFromJson]；否则返回 null。
     */
    fun resolve(appName: String): String? {
        val normalized = cachedNormalized ?: return null
        val key = normalize(appName)
        if (key.isBlank()) return null
        return normalized[key]
    }

    /**
     * [resolve] 的别名，提供更直观的方法名。
     */
    fun getPackageFor(appName: String): String? = resolve(appName)

    /**
     * 在文本中查找最佳匹配的应用名。匹配优先级：
     * 1. 出现位置靠前（start 较小）；
     * 2. 起点相同时，标签较长者优先。
     *
     * 注意：调用前需先调用 [load] 或 [loadFromJson]；否则返回 null。
     */
    fun bestMatchInText(text: String): Match? {
        val raw = cachedRaw ?: return null
        val t = text
        if (t.isBlank()) return null

        var best: Match? = null
        for ((label, pkg) in raw) {
            if (label.isBlank()) continue
            val idx = t.indexOf(label, ignoreCase = true)
            if (idx < 0) continue
            val candidate = Match(label, pkg, idx, idx + label.length)
            val cur = best
            best =
                    when {
                        cur == null -> candidate
                        candidate.start < cur.start -> candidate
                        candidate.start == cur.start && (candidate.end - candidate.start) >
                                (cur.end - cur.start) -> candidate
                        else -> cur
                    }
        }
        return best
    }
}
