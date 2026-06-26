package com.ai.phoneagent.permissions

import android.content.Context
import com.ai.phoneagent.data.preferences.ToolPermissionsRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * ToolPermissionSystem 单元测试
 *
 * 说明：ToolPermissionSystem 使用 private constructor + getInstance(context) 单例模式，
 * 构造函数依赖 Context 和 ToolPermissionsRepository（后者基于 DataStore，
 * 需要真实 Android Context 才能初始化）。因此在纯 JVM 单元测试中无法通过 getInstance
 * 或反射构造完整可用的实例。
 *
 * checkPermission / getMasterPermissionLevel / getToolPermissionLevel 等方法依赖
 * ToolPermissionsRepository（DataStore），需要在 Android 插桩测试或 Robolectric 环境下
 * 进行完整测试。
 *
 * 本测试聚焦于可验证的部分：
 * 1. PermissionLevel 枚举的完整性（四个值）和顺序
 * 2. PermissionLevel 枚举的 valueOf / name / ordinal 行为
 * 3. ToolPermissionSystem 类结构（通过反射验证 API 表面）
 */
class ToolPermissionSystemTest {

    // ─── PermissionLevel 枚举验证 ─────────────────────────────────────────────

    @Test
    fun `PermissionLevel contains exactly four values`() {
        val values = ToolPermissionSystem.PermissionLevel.values()
        assertEquals(4, values.size)
    }

    @Test
    fun `PermissionLevel contains ALLOW`() {
        val values = ToolPermissionSystem.PermissionLevel.values().map { it.name }
        assertTrue("ALLOW" in values)
    }

    @Test
    fun `PermissionLevel contains CAUTION`() {
        val values = ToolPermissionSystem.PermissionLevel.values().map { it.name }
        assertTrue("CAUTION" in values)
    }

    @Test
    fun `PermissionLevel contains ASK`() {
        val values = ToolPermissionSystem.PermissionLevel.values().map { it.name }
        assertTrue("ASK" in values)
    }

    @Test
    fun `PermissionLevel contains FORBID`() {
        val values = ToolPermissionSystem.PermissionLevel.values().map { it.name }
        assertTrue("FORBID" in values)
    }

    @Test
    fun `PermissionLevel values are in expected order ALLOW CAUTION ASK FORBID`() {
        val values = ToolPermissionSystem.PermissionLevel.values()
        // 验证枚举声明顺序：ALLOW(0), CAUTION(1), ASK(2), FORBID(3)
        assertEquals(ToolPermissionSystem.PermissionLevel.ALLOW, values[0])
        assertEquals(ToolPermissionSystem.PermissionLevel.CAUTION, values[1])
        assertEquals(ToolPermissionSystem.PermissionLevel.ASK, values[2])
        assertEquals(ToolPermissionSystem.PermissionLevel.FORBID, values[3])
    }

    @Test
    fun `PermissionLevel ordinal values are sequential`() {
        assertEquals(0, ToolPermissionSystem.PermissionLevel.ALLOW.ordinal)
        assertEquals(1, ToolPermissionSystem.PermissionLevel.CAUTION.ordinal)
        assertEquals(2, ToolPermissionSystem.PermissionLevel.ASK.ordinal)
        assertEquals(3, ToolPermissionSystem.PermissionLevel.FORBID.ordinal)
    }

    @Test
    fun `PermissionLevel valueOf resolves each name`() {
        assertEquals(
            ToolPermissionSystem.PermissionLevel.ALLOW,
            ToolPermissionSystem.PermissionLevel.valueOf("ALLOW")
        )
        assertEquals(
            ToolPermissionSystem.PermissionLevel.CAUTION,
            ToolPermissionSystem.PermissionLevel.valueOf("CAUTION")
        )
        assertEquals(
            ToolPermissionSystem.PermissionLevel.ASK,
            ToolPermissionSystem.PermissionLevel.valueOf("ASK")
        )
        assertEquals(
            ToolPermissionSystem.PermissionLevel.FORBID,
            ToolPermissionSystem.PermissionLevel.valueOf("FORBID")
        )
    }

    @Test
    fun `PermissionLevel valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolPermissionSystem.PermissionLevel.valueOf("INVALID_LEVEL")
        }
    }

    @Test
    fun `PermissionLevel name property matches declaration`() {
        assertEquals("ALLOW", ToolPermissionSystem.PermissionLevel.ALLOW.name)
        assertEquals("CAUTION", ToolPermissionSystem.PermissionLevel.CAUTION.name)
        assertEquals("ASK", ToolPermissionSystem.PermissionLevel.ASK.name)
        assertEquals("FORBID", ToolPermissionSystem.PermissionLevel.FORBID.name)
    }

    // ─── ToolPermissionSystem 类结构验证（反射） ──────────────────────────────

    @Test
    fun `ToolPermissionSystem class exists and is loadable`() {
        val clazz = ToolPermissionSystem::class
        assertNotNull(clazz)
        assertEquals("ToolPermissionSystem", clazz.simpleName)
    }

    @Test
    fun `ToolPermissionSystem private constructor takes Context and ToolPermissionsRepository`() {
        val constructors = ToolPermissionSystem::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        val constructor = constructors.first()
        val paramTypes = constructor.parameterTypes.map { it.simpleName }
        assertTrue(
            "Expected at least 2 params but got ${paramTypes.size}: $paramTypes",
            paramTypes.size >= 2
        )
        assertTrue("Context" in paramTypes)
        assertTrue("ToolPermissionsRepository" in paramTypes)
    }

    @Test
    fun `ToolPermissionSystem has getInstance companion method`() {
        val methods = ToolPermissionSystem.Companion::class.java.declaredMethods
            .map { it.name }
            .toSet()
        assertTrue("getInstance" in methods)
    }

    @Test
    fun `ToolPermissionSystem has expected public methods`() {
        val methods = ToolPermissionSystem::class.java.declaredMethods.map { it.name }.toSet()
        // 权限级别读写（JUnit assertTrue 签名为 message 在前、condition 在后）
        assertTrue("Missing getMasterPermissionLevel", "getMasterPermissionLevel" in methods)
        assertTrue("Missing setMasterPermissionLevel", "setMasterPermissionLevel" in methods)
        assertTrue("Missing getToolPermissionLevel", "getToolPermissionLevel" in methods)
        assertTrue("Missing setToolPermissionLevel", "setToolPermissionLevel" in methods)
        // 权限检查（suspend 函数编译后会带 Continuation 参数）
        assertTrue("Missing checkPermission", "checkPermission" in methods)
    }

    @Test
    fun `ToolPermissionSystem references ToolPermissionsRepository import`() {
        // 验证 ToolPermissionsRepository 类可加载（被 ToolPermissionSystem 依赖）
        val repoClass = ToolPermissionsRepository::class
        assertNotNull(repoClass)
        assertEquals("ToolPermissionsRepository", repoClass.simpleName)
    }

    @Test
    fun `ToolPermissionsRepository constructor takes Context parameter`() {
        val constructor = ToolPermissionsRepository::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes.map { it.simpleName }
        assertTrue("ToolPermissionsRepository should take Context, got: $paramTypes", "Context" in paramTypes)
    }
}
