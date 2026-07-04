package com.ai.phoneagent.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * PathGuard 单元测试。
 *
 * 使用纯 JVM 测试（不依赖 Robolectric / Android Context），
 * 通过传入 [File] 列表直接验证 [PathGuard.canonicalizeWithin] 的核心语义。
 */
class PathGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesRoot: File   // 模拟 Context.filesDir
    private lateinit var cacheRoot: File   // 模拟 Context.cacheDir

    /**
     * 每个测试前创建两个独立的允许根目录，模拟应用私有的 filesDir / cacheDir。
     */
    @org.junit.Before
    fun setUp() {
        filesRoot = tempFolder.newFolder("files")
        cacheRoot = tempFolder.newFolder("cache")
    }

    private fun allowedRoots(): List<File> = listOf(filesRoot, cacheRoot)

    // ========== 合法路径 ==========

    @Test
    fun `合法路径_filesDir子文件_应通过`() {
        val dataFile = File(filesRoot, "data.txt").apply { writeText("hello") }

        val resolved = PathGuard.canonicalizeWithin(allowedRoots(), dataFile.absolutePath)

        assertEquals(dataFile.canonicalFile, resolved)
    }

    @Test
    fun `合法路径_cacheDir子文件_应通过`() {
        val cacheFile = File(cacheRoot, "tmp.bin").apply { writeText("x") }

        val resolved = PathGuard.canonicalizeWithin(allowedRoots(), cacheFile.absolutePath)

        assertEquals(cacheFile.canonicalFile, resolved)
    }

    // ========== 路径穿越 ==========

    @Test
    fun `路径穿越_应抛SecurityException`() {
        // allowedRoot/../outside.txt 应解析到 allowedRoot 的父目录之外
        val traversalPath = File(filesRoot, "..${File.separator}outside.txt").path

        assertThrows(SecurityException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), traversalPath)
        }
    }

    @Test
    fun `符号链接越界_应抛SecurityException`() {
        // 在 allowedRoot 内创建符号链接指向外部文件
        val outsideTarget = File(tempFolder.root, "outside_target.txt").apply { writeText("secret") }
        val link = File(filesRoot, "evil_link")

        val supported = try {
            Files.createSymbolicLink(link.toPath(), outsideTarget.toPath())
            true
        } catch (e: Exception) {
            // Windows 无权限 / 文件系统不支持符号链接时跳过
            false
        }
        assumeTrue("当前环境不支持创建符号链接，跳过本用例", supported)

        // 验证 canonicalFile 确实解析到了外部目标；某些 Windows/文件系统下符号链接
        // 不会被 java.io.File.getCanonicalFile() 解析到目标路径，此时跳过断言。
        val resolvedTarget = link.canonicalFile
        assumeTrue(
            "当前 JVM 的 File.getCanonicalFile() 未将符号链接解析到目标路径，跳过本用例",
            resolvedTarget.absolutePath == outsideTarget.canonicalFile.absolutePath
        )

        assertThrows(SecurityException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), link.absolutePath)
        }
    }

    @Test
    fun `绝对路径越界_应抛SecurityException`() {
        val outside = outsideAbsolutePath()

        assertThrows(SecurityException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), outside)
        }
    }

    @Test
    fun `前缀冲突_应抛SecurityException`() {
        // 构造一个名为 filesSibling 的目录，其路径前缀与 filesRoot 相似但不以 separator 边界结束。
        // 例如 filesRoot = .../files，构造 .../filesEvil/file.txt，
        // 必须因 separator 边界检查被拒绝，而不是被误判为 filesRoot 的子文件。
        val evilSibling = File(filesRoot.parentFile, "${filesRoot.name}Evil").apply { mkdirs() }
        val evilFile = File(evilSibling, "file.txt").apply { writeText("bad") }

        assertThrows(SecurityException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), evilFile.absolutePath)
        }
    }

    // ========== 非法输入 ==========

    @Test
    fun `空路径_应抛IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), "")
        }
    }

    @Test
    fun `null路径_应抛IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), null)
        }
    }

    @Test
    fun `空白路径_应抛IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathGuard.canonicalizeWithin(allowedRoots(), "   ")
        }
    }

    @Test
    fun `allowedRoots为空_应抛IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathGuard.canonicalizeWithin(emptyList(), "data.txt")
        }
    }

    // ========== 相对路径 ==========

    @Test
    fun `相对路径_应相对于首个allowedRoot解析`() {
        val resolved = PathGuard.canonicalizeWithin(allowedRoots(), "subdir${File.separator}data.txt")

        val expected = File(filesRoot, "subdir${File.separator}data.txt").canonicalFile
        assertEquals(expected, resolved)
        assertTrue(
            "解析结果应位于 filesRoot 内",
            resolved.canonicalPath.startsWith(filesRoot.canonicalPath + File.separator)
        )
    }

    @Test
    fun `相对路径filesDir_应解析到filesRoot`() {
        // 传入纯文件名 "data.txt"，应解析到 filesRoot/data.txt
        val resolved = PathGuard.canonicalizeWithin(allowedRoots(), "data.txt")

        assertTrue(resolved.canonicalPath.startsWith(filesRoot.canonicalPath + File.separator))
        assertEquals("data.txt", resolved.name)
    }

    // ========== 辅助 ==========

    /**
     * 返回一个在当前操作系统上保证为绝对路径、且位于任意临时目录之外的系统路径。
     * 在 Unix 类系统上使用 `/etc/passwd`；在 Windows 上使用系统 hosts 文件路径。
     */
    private fun outsideAbsolutePath(): String {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        return if (osName.contains("win")) {
            "C:\\Windows\\System32\\drivers\\etc\\hosts"
        } else {
            "/etc/passwd"
        }
    }
}
