package com.ai.phoneagent.core.tools

import com.ai.phoneagent.AppPackageMapping
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [AppPackageMapping] 单元测试。
 *
 * 由于单元测试环境无 [android.content.Context]（项目未引入 Robolectric），测试通过
 * [AppPackageMapping.parseJson]（纯函数）与 [AppPackageMapping.loadFromJson]
 * （解析+缓存）间接覆盖 [AppPackageMapping.load] 的核心路径。
 */
class AppPackageMappingTest {

    private val sampleJson =
            """
            {
              "version": 1,
              "mappings": [
                {"appName": "微信", "packageName": "com.tencent.mm"},
                {"appName": "QQ", "packageName": "com.tencent.mobileqq"},
                {"appName": "Android System Settings", "packageName": "com.android.settings"}
              ]
            }
            """.trimIndent()

    @Before
    fun setUp() {
        AppPackageMapping.resetCache()
    }

    @After
    fun tearDown() {
        AppPackageMapping.resetCache()
    }

    // ===== parseJson 纯函数测试 =====

    @Test
    fun `parseJson_有效JSON_返回映射`() {
        val map = AppPackageMapping.parseJson(sampleJson)
        assertEquals(3, map.size)
        assertEquals("com.tencent.mm", map["微信"])
        assertEquals("com.tencent.mobileqq", map["QQ"])
        assertEquals("com.android.settings", map["Android System Settings"])
    }

    @Test
    fun `parseJson_空字符串_抛IOException`() {
        try {
            AppPackageMapping.parseJson("")
            fail("Expected IOException for blank JSON")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Empty") == true)
        }
    }

    @Test
    fun `parseJson_格式错误_抛IOException`() {
        // 缺少右花括号，明显语法错误，Gson 必抛 JsonSyntaxException
        val brokenJson = """{"version": 1, "mappings": [{"appName": "微信", "packageName": "com.tencent.mm"}"""
        try {
            AppPackageMapping.parseJson(brokenJson)
            fail("Expected IOException for malformed JSON")
        } catch (e: IOException) {
            // JsonSyntaxException 被包装为 IOException
            assertTrue(e.message?.contains("Invalid JSON format") == true)
        }
    }

    @Test
    fun `parseJson_非JSON文本_抛IOException`() {
        val notJson = "this is definitely not json"
        try {
            AppPackageMapping.parseJson(notJson)
            fail("Expected IOException for non-JSON text")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Invalid JSON format") == true)
        }
    }

    @Test
    fun `parseJson_空mappings_返回空Map`() {
        val emptyMappingsJson = """{"version": 1, "mappings": []}"""
        val map = AppPackageMapping.parseJson(emptyMappingsJson)
        assertTrue(map.isEmpty())
    }

    // ===== loadFromJson（等价于 load 的解析+缓存路径）测试 =====

    @Test
    fun `load_从json读取_返回映射`() {
        val map = AppPackageMapping.loadFromJson(sampleJson)
        assertEquals(3, map.size)
        assertEquals("com.tencent.mm", map["微信"])
        assertEquals("com.tencent.mobileqq", map["QQ"])
    }

    @Test
    fun `load_重复调用_返回缓存实例`() {
        val first = AppPackageMapping.loadFromJson(sampleJson)
        // 第二次传入不同 JSON，但应返回首次缓存实例
        val secondJson =
                """
                {
                  "version": 2,
                  "mappings": [
                    {"appName": "抖音", "packageName": "com.ss.android.ugc.aweme"}
                  ]
                }
                """.trimIndent()
        val second = AppPackageMapping.loadFromJson(secondJson)

        // 验证 lazy/单次加载语义：返回同一实例
        assertSame("重复 load 应返回同一缓存实例", first, second)
        // 内容仍为首次解析的数据
        assertEquals("com.tencent.mm", first["微信"])
        assertEquals(3, second.size)
    }

    // ===== getPackageFor / resolve 测试 =====

    @Test
    fun `getPackageFor_已知应用_返回包名`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertEquals("com.tencent.mm", AppPackageMapping.getPackageFor("微信"))
        assertEquals("com.tencent.mobileqq", AppPackageMapping.getPackageFor("QQ"))
    }

    @Test
    fun `getPackageFor_别名归一化生效`() {
        // sampleJson 中 "Android System Settings" → com.android.settings
        // 归一化会去除空格与连字符，故 "Android-System-Settings" 也应命中
        AppPackageMapping.loadFromJson(sampleJson)
        assertEquals(
                "com.android.settings",
                AppPackageMapping.getPackageFor("Android-System-Settings"),
        )
        assertEquals("com.android.settings", AppPackageMapping.getPackageFor("android system settings"))
    }

    @Test
    fun `getPackageFor_未知应用_返回null`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertNull(AppPackageMapping.getPackageFor("不存在的应用"))
        assertNull(AppPackageMapping.getPackageFor("NonExistentApp"))
    }

    @Test
    fun `getPackageFor_未加载_返回null`() {
        // 不调用 load，直接查询应返回 null（不会崩溃）
        assertNull(AppPackageMapping.getPackageFor("微信"))
    }

    @Test
    fun `getPackageFor_空字符串_返回null`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertNull(AppPackageMapping.getPackageFor(""))
        assertNull(AppPackageMapping.getPackageFor("   "))
    }

    @Test
    fun `resolve_与getPackageFor_行为一致`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertEquals(AppPackageMapping.resolve("微信"), AppPackageMapping.getPackageFor("微信"))
        assertEquals(AppPackageMapping.resolve("未知"), AppPackageMapping.getPackageFor("未知"))
    }

    // ===== bestMatchInText 测试 =====

    @Test
    fun `bestMatchInText_命中已知应用_返回匹配`() {
        AppPackageMapping.loadFromJson(sampleJson)
        val match = AppPackageMapping.bestMatchInText("帮我打开微信给张三发消息")
        assertNotNull(match)
        assertEquals("微信", match!!.appLabel)
        assertEquals("com.tencent.mm", match.packageName)
        assertEquals(4, match.start)
        assertEquals(6, match.end)
    }

    @Test
    fun `bestMatchInText_无匹配_返回null`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertNull(AppPackageMapping.bestMatchInText("今天天气不错"))
    }

    @Test
    fun `bestMatchInText_空文本_返回null`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertNull(AppPackageMapping.bestMatchInText(""))
    }

    @Test
    fun `bestMatchInText_未加载_返回null`() {
        assertNull(AppPackageMapping.bestMatchInText("微信"))
    }

    // ===== resetCache 测试 =====

    @Test
    fun `resetCache_清空后查询返回null`() {
        AppPackageMapping.loadFromJson(sampleJson)
        assertEquals("com.tencent.mm", AppPackageMapping.getPackageFor("微信"))
        AppPackageMapping.resetCache()
        assertNull(AppPackageMapping.getPackageFor("微信"))
    }
}
