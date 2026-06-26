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
package com.ai.phoneagent.core.tools.file

import org.junit.Assert.*
import org.junit.Test

/**
 * 文件路径安全校验器单元测试。
 *
 * 注意：[FilePathValidator.init] 需要 Android [android.content.Context]，
 * 在纯 JVM 单元测试环境中无法初始化。未初始化时 [FilePathValidator.isPathAllowed]
 * 统一返回 false，因此本测试聚焦：
 * 1) 路径穿越 / 非法字符 / 边界输入不导致崩溃；
 * 2) [FilePathValidator.validatePath] 对各类攻击形态返回明确的错误信息；
 * 3) [FilePathValidator.validatePaths] 的批量校验与短路行为。
 *
 * 已初始化的白名单匹配行为需在 Robolectric / 插桩测试中验证。
 */
class FilePathValidatorTest {

    @Test
    fun `blank path is rejected`() {
        assertFalse(FilePathValidator.isPathAllowed(""))
        assertFalse(FilePathValidator.isPathAllowed("   "))
    }

    @Test
    fun `path traversal with dot-dot is rejected when not initialized`() {
        // 未初始化时所有路径都应被拒绝
        assertFalse(FilePathValidator.isPathAllowed("/etc/passwd"))
        assertFalse(FilePathValidator.isPathAllowed("/data/system/sensitive.db"))
    }

    @Test
    fun `validatePath returns error for blank path`() {
        val error = FilePathValidator.validatePath("")
        assertNotNull(error)
        assertTrue(error!!.contains("路径不能为空"))
    }

    @Test
    fun `validatePath returns error when not initialized`() {
        val error = FilePathValidator.validatePath("/some/path")
        assertNotNull(error)
        assertTrue(error!!.contains("路径不在允许的范围内"))
    }

    @Test
    fun `validatePaths checks all paths`() {
        val error = FilePathValidator.validatePaths("/path1", "/path2")
        assertNotNull(error)
    }

    // ========== 路径穿越攻击场景 ==========

    @Test
    fun `relative path traversal to etc passwd is rejected`() {
        val attackVectors = listOf(
            "../../etc/passwd",
            "../../../etc/passwd",
            "../../../../etc/shadow",
            "./../../etc/passwd",
            "data/../../etc/passwd",
            "/data/../etc/passwd"
        )
        for (path in attackVectors) {
            assertFalse("路径穿越应被拒绝: $path", FilePathValidator.isPathAllowed(path))
        }
    }

    @Test
    fun `path traversal targeting system sensitive files is rejected`() {
        val sensitiveTargets = listOf(
            "/system/build.prop",
            "/data/system/packages.xml",
            "/proc/self/environ",
            "/sys/class/net",
            "/dev/block/mmcblk0",
            "/sdcard/../system/build.prop"
        )
        for (path in sensitiveTargets) {
            assertFalse("系统敏感路径应被拒绝: $path", FilePathValidator.isPathAllowed(path))
        }
    }

    @Test
    fun `validatePath reports rejection for traversal path`() {
        val error = FilePathValidator.validatePath("../../etc/passwd")
        assertNotNull(error)
        assertTrue(error!!.contains("路径不在允许的范围内"))
    }

    // ========== 符号链接 / 等价路径场景 ==========

    @Test
    fun `symbolic-link-like paths do not crash validator`() {
        // canonicalFile 解析需要真实文件系统；未初始化时直接返回 false，
        // 这里确保各种符号链接风格的路径不会抛异常。
        val linkLikePaths = listOf(
            "/data/local/tmp/link",
            "/proc/self/fd/0",
            "/dev/stdin",
            "/var/run/socket",
            "/sdcard/Movies/../DCIM"
        )
        for (path in linkLikePaths) {
            // 不应抛异常，统一返回 false（未初始化）
            val allowed = FilePathValidator.isPathAllowed(path)
            assertFalse(allowed)
        }
    }

    // ========== 空路径 / 边界输入 ==========

    @Test
    fun `empty and whitespace-only paths return blank error`() {
        val blanks = listOf("", "   ", "\t", "\n", " \t\n ")
        for (path in blanks) {
            val error = FilePathValidator.validatePath(path)
            assertNotNull("空路径应返回错误: [$path]", error)
            assertTrue(error!!.contains("路径不能为空"))
        }
    }

    @Test
    fun `isPathAllowed returns false for all blank variants`() {
        assertFalse(FilePathValidator.isPathAllowed(""))
        assertFalse(FilePathValidator.isPathAllowed("   "))
        assertFalse(FilePathValidator.isPathAllowed("\t\n"))
    }

    // ========== 非法字符路径 ==========

    @Test
    fun `paths with null bytes are rejected without crash`() {
        // null 字节是常见注入手段，确保不会导致异常或误判为合法
        // Kotlin 字符串不支持 \0 转义，统一使用 \u0000 表示 null 字节
        val malicious = listOf(
            "file\u0000.txt",
            "/data/files\u0000/../../etc/passwd",
            "a\u0000b"
        )
        for (path in malicious) {
            val allowed = FilePathValidator.isPathAllowed(path)
            assertFalse(allowed)
        }
    }

    @Test
    fun `paths with special characters are rejected gracefully`() {
        val special = listOf(
            "file name with spaces.txt",
            "file|name",
            "file<name>",
            "file:name",
            "file\"name",
            "file\\name",
            "file*name?",
            "file;rm -rf /"
        )
        for (path in special) {
            // 任何路径在未初始化时都应被拒绝，且不抛异常
            assertFalse(FilePathValidator.isPathAllowed(path))
        }
    }

    @Test
    fun `extremely long path is rejected without crash`() {
        val longPath = "a".repeat(10000)
        assertFalse(FilePathValidator.isPathAllowed(longPath))
    }

    // ========== validatePaths 批量校验 ==========

    @Test
    fun `validatePaths returns error on first invalid path`() {
        // 未初始化时第一个路径即非法，应直接返回错误
        val error = FilePathValidator.validatePaths("/first", "/second", "/third")
        assertNotNull(error)
        // 第一个路径被拒绝
        assertTrue(error!!.contains("路径不在允许的范围内"))
    }

    @Test
    fun `validatePaths short-circuits on blank path`() {
        // 空路径优先返回“路径不能为空”
        val error = FilePathValidator.validatePaths("", "/second")
        assertNotNull(error)
        assertTrue(error!!.contains("路径不能为空"))
    }

    @Test
    fun `validatePaths with no arguments returns null`() {
        // 无参数时应通过（无路径需校验）
        assertNull(FilePathValidator.validatePaths())
    }

    // ========== 多次调用一致性 ==========

    @Test
    fun `repeated calls are consistent`() {
        val path = "/data/data/com.ai.phoneagent/files/test.txt"
        val first = FilePathValidator.isPathAllowed(path)
        val second = FilePathValidator.isPathAllowed(path)
        assertEquals(first, second)
        assertFalse(first)
    }
}
