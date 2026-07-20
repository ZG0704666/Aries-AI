/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21A: FileToolExecutor 行为测试。
 *
 * 测试策略：
 * - 通过 MockK 构造 relaxed mock Context，将其 filesDir/cacheDir 指向
 *   TemporaryFolder 中的真实目录，然后显式构造 FileToolExecutor。
 * - 所有文件操作统一通过 PathGuard 解析允许根内的路径，可在真实文件系统上验证
 *   绝对路径越界、路径穿越和符号链接逃逸；move 同时验证源与目标。
 *
 * PathGuard 的底层语义由 PathGuardTest 覆盖，本测试聚焦于 FileToolExecutor 的行为集成。
 */
package com.ai.phoneagent.core.tools.file

import android.content.Context
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolParameter
import com.ai.phoneagent.data.model.ToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * [FileToolExecutor] 行为测试。
 *
 * 覆盖 write_file / read_file / copy / compress / list_dir / file_info / exists
 * 的正向路径与错误路径，验证 PathGuard 集成与文件操作语义。
 */
class FileToolExecutorBehaviorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesRoot: File
    private lateinit var cacheRoot: File
    private lateinit var executor: FileToolExecutor

    @Before
    fun setUp() {
        filesRoot = tempFolder.newFolder("files")
        cacheRoot = tempFolder.newFolder("cache")

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns filesRoot
        every { mockContext.cacheDir } returns cacheRoot
        every { mockContext.getExternalFilesDir(any()) } returns null
        executor = FileToolExecutor(mockContext)
    }

    private fun tool(name: String, vararg params: Pair<String, String>): AITool =
        AITool(
            name = name,
            parameters = params.map { ToolParameter(it.first, it.second) }
        )

    private fun text(result: ToolResult): String =
        (result.result as? StringResultData)?.data ?: ""

    // ===== write_file =====

    @Test
    fun `write_file_合法路径_写入成功`() = runBlocking {
        val result = executor.writeFile(
            tool("write_file", "path" to "test.txt", "content" to "hello world")
        )

        assertTrue("写入应成功: ${result.error}", result.success)
        val written = File(filesRoot, "test.txt")
        assertTrue("文件应存在", written.exists())
        assertEquals("hello world", written.readText())
    }

    @Test
    fun `write_file_路径越界_被PathGuard拦截`() = runBlocking {
        // 越界绝对路径：PathGuard.canonicalizeWithin 应抛 SecurityException，
        // writeFile 捕获后返回 success=false 的 ToolResult。
        val outsidePath = outsideAbsolutePath()

        val result = executor.writeFile(
            tool("write_file", "path" to outsidePath, "content" to "evil")
        )

        assertFalse("越界路径不应写入成功", result.success)
        assertTrue(
            "错误应包含越界提示: ${result.error}",
            result.error.contains("越界") || result.error.contains("rejected")
        )
    }

    @Test
    fun `write_file_路径穿越_被PathGuard拦截`() = runBlocking {
        // ../outside.txt 相对于 filesRoot 解析后位于 allowed roots 之外。
        val traversalPath = "..${File.separator}outside.txt"

        val result = executor.writeFile(
            tool("write_file", "path" to traversalPath, "content" to "evil")
        )

        assertFalse("穿越路径不应写入成功", result.success)
        assertTrue(
            "错误应包含越界提示: ${result.error}",
            result.error.contains("越界") || result.error.contains("rejected")
        )
    }

    @Test
    fun `write_file_append模式_追加内容`() = runBlocking {
        executor.writeFile(
            tool("write_file", "path" to "log.txt", "content" to "line1\n", "append" to "false")
        )
        executor.writeFile(
            tool("write_file", "path" to "log.txt", "content" to "line2\n", "append" to "true")
        )

        val content = File(filesRoot, "log.txt").readText()
        assertEquals("line1\nline2\n", content)
    }

    // ===== read_file =====

    @Test
    fun `read_file_合法路径_读取成功`() = runBlocking {
        File(filesRoot, "input.txt").writeText("read me please")

        val result = executor.readFile(
            tool("read_file", "path" to "input.txt")
        )

        assertTrue("读取应成功: ${result.error}", result.success)
        assertTrue(
            "内容应包含原文: ${text(result)}",
            text(result).contains("read me please")
        )
    }

    @Test
    fun `read_file_不存在文件_返回错误`() = runBlocking {
        val result = executor.readFile(
            tool("read_file", "path" to "nonexistent.txt")
        )

        assertFalse("不存在的文件应返回失败", result.success)
        assertTrue(
            "错误应包含不存在提示: ${result.error}",
            result.error.contains("不存在") || result.error.contains("not exist")
        )
    }

    // ===== copy =====

    @Test
    fun `copy_源文件存在_复制成功`() = runBlocking {
        File(filesRoot, "source.txt").writeText("copy me")

        val result = executor.copy(
            tool("copy", "source" to "source.txt", "destination" to "dest.txt")
        )

        assertTrue("复制应成功: ${result.error}", result.success)
        val dest = File(filesRoot, "dest.txt")
        assertTrue("目标文件应存在", dest.exists())
        assertEquals("copy me", dest.readText())
    }

    @Test
    fun `copy_源文件不存在_返回错误`() = runBlocking {
        val result = executor.copy(
            tool("copy", "source" to "missing.txt", "destination" to "dest.txt")
        )

        assertFalse("源文件不存在应返回失败", result.success)
        assertTrue(
            "错误应包含不存在提示: ${result.error}",
            result.error.contains("不存在") || result.error.contains("not exist")
        )
    }

    // ===== compress =====

    @Test
    fun `compress_合法路径_压缩成功`() = runBlocking {
        File(filesRoot, "to_zip.txt").writeText("compress me compress me compress me")

        val result = executor.compress(
            tool("compress", "source" to "to_zip.txt", "destination" to "out.zip")
        )

        assertTrue("压缩应成功: ${result.error}", result.success)
        val zipFile = File(filesRoot, "out.zip")
        assertTrue("ZIP 文件应存在", zipFile.exists())
        assertTrue("ZIP 文件应非空", zipFile.length() > 0)

        // 验证 ZIP 内包含原始文件名
        val entryNames = mutableListOf<String>()
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                entryNames.add(entries.nextElement().name)
            }
        }
        assertTrue("ZIP 应包含 to_zip.txt: $entryNames", entryNames.contains("to_zip.txt"))
    }


    @Test
    fun `compress_目录含外部文件符号链接_拒绝且不保留ZIP`() = runBlocking {
        val sourceDir = File(filesRoot, "source-with-file-link").apply { mkdirs() }
        val outsideFile = File(tempFolder.root, "outside-secret.txt").apply { writeText("secret") }
        val link = File(sourceDir, "secret-link.txt")
        val supported = try {
            Files.createSymbolicLink(link.toPath(), outsideFile.toPath())
            true
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue("当前环境不支持符号链接", supported)

        val result = executor.compress(
            tool("compress", "source" to sourceDir.absolutePath, "destination" to "blocked-file-link.zip")
        )

        assertFalse(result.success)
        assertFalse(File(filesRoot, "blocked-file-link.zip").exists())
        assertTrue(outsideFile.exists())
    }

    @Test
    fun `compress_目录含外部目录符号链接_拒绝且不保留ZIP`() = runBlocking {
        val sourceDir = File(filesRoot, "source-with-dir-link").apply { mkdirs() }
        val outsideDir = tempFolder.newFolder("outside-directory").apply {
            resolve("secret.txt").writeText("secret")
        }
        val link = File(sourceDir, "outside-dir-link")
        val supported = try {
            Files.createSymbolicLink(link.toPath(), outsideDir.toPath())
            true
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue("当前环境不支持符号链接", supported)

        val result = executor.compress(
            tool("compress", "source" to sourceDir.absolutePath, "destination" to "blocked-dir-link.zip")
        )

        assertFalse(result.success)
        assertFalse(File(filesRoot, "blocked-dir-link.zip").exists())
        assertTrue(File(outsideDir, "secret.txt").exists())
    }

    @Test
    fun `compress_目标位于源目录内部_拒绝且不创建ZIP`() = runBlocking {
        val sourceDir = File(filesRoot, "recursive-source").apply { mkdirs() }
        File(sourceDir, "payload.txt").writeText("payload")
        val destination = File(sourceDir, "archive.zip")

        val result = executor.compress(
            tool(
                "compress",
                "source" to sourceDir.absolutePath,
                "destination" to destination.absolutePath,
            )
        )

        assertFalse(result.success)
        assertFalse(destination.exists())
    }

    // ===== delete =====

    @Test
    fun `delete_根目录内文件_删除成功`() = runBlocking {
        File(filesRoot, "delete.txt").writeText("remove")

        val result = executor.delete(tool("delete", "path" to "delete.txt"))

        assertTrue("删除应成功: ${result.error}", result.success)
        assertFalse(File(filesRoot, "delete.txt").exists())
    }

    @Test
    fun `delete_绝对路径越界_被拒绝`() = runBlocking {
        val result = executor.delete(tool("delete", "path" to outsideAbsolutePath()))

        assertFalse(result.success)
        assertTrue(result.error.contains("拒绝") || result.error.contains("越界"))
    }


    @Test
    fun `delete_目录含外部目录符号链接_只删除链接且保留外部目标`() = runBlocking {
        val sourceDir = File(filesRoot, "delete-tree").apply { mkdirs() }
        File(sourceDir, "local.txt").writeText("local")
        val outsideDir = tempFolder.newFolder("delete-outside").apply {
            resolve("keep.txt").writeText("keep")
        }
        val link = File(sourceDir, "outside-link")
        val supported = try {
            Files.createSymbolicLink(link.toPath(), outsideDir.toPath())
            true
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue("当前环境不支持符号链接", supported)

        val result = executor.delete(tool("delete", "path" to sourceDir.absolutePath))

        assertTrue("目录删除应成功: ${result.error}", result.success)
        assertFalse(sourceDir.exists())
        assertTrue(outsideDir.exists())
        assertEquals("keep", File(outsideDir, "keep.txt").readText())
    }

    // ===== list_dir =====

    @Test
    fun `list_dir_目录存在_返回列表`() = runBlocking {
        val dir = File(filesRoot, "listdir").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")

        val result = executor.listDir(
            tool("list_dir", "path" to "listdir")
        )

        assertTrue("列出目录应成功: ${result.error}", result.success)
        val output = text(result)
        assertTrue("应包含 a.txt: $output", output.contains("a.txt"))
        assertTrue("应包含 b.txt: $output", output.contains("b.txt"))
    }

    @Test
    fun `list_dir_空目录_返回空提示`() = runBlocking {
        File(filesRoot, "emptydir").mkdirs()

        val result = executor.listDir(
            tool("list_dir", "path" to "emptydir")
        )

        assertTrue(result.success)
        assertTrue(
            "应提示目录为空: ${text(result)}",
            text(result).contains("空") || text(result).contains("empty")
        )
    }

    @Test
    fun `list_dir_父目录穿越_被拒绝`() = runBlocking {
        val result = executor.listDir(
            tool("list_dir", "path" to "..${File.separator}outside")
        )

        assertFalse(result.success)
    }

    // ===== create_dir =====

    @Test
    fun `create_dir_根目录内_创建成功`() = runBlocking {
        val result = executor.createDir(
            tool("create_dir", "path" to "new${File.separator}nested")
        )

        assertTrue("创建目录应成功: ${result.error}", result.success)
        assertTrue(File(filesRoot, "new${File.separator}nested").isDirectory)
    }

    @Test
    fun `create_dir_绝对路径越界_被拒绝`() = runBlocking {
        val outside = File(tempFolder.root, "outside-create").absolutePath
        val result = executor.createDir(tool("create_dir", "path" to outside))

        assertFalse(result.success)
        assertFalse(File(outside).exists())
    }

    // ===== file_info =====

    @Test
    fun `file_info_文件存在_返回元数据`() = runBlocking {
        val file = File(filesRoot, "info.txt").apply { createNewFile() }
        file.writeText("metadata content")

        val result = executor.fileInfo(
            tool("file_info", "path" to "info.txt")
        )

        assertTrue("file_info 应成功: ${result.error}", result.success)
        val output = text(result)
        assertTrue("应包含文件名: $output", output.contains("info.txt"))
        assertTrue(
            "应包含大小信息: $output",
            output.contains("大小") || output.contains("bytes")
        )
        assertTrue(
            "应包含是文件标记: $output",
            output.contains("是文件") || output.contains("true")
        )
    }

    @Test
    fun `file_info_文件不存在_返回错误`() = runBlocking {
        val result = executor.fileInfo(
            tool("file_info", "path" to "nope.txt")
        )

        assertFalse(result.success)
        assertTrue("错误应包含不存在: ${result.error}", result.error.contains("不存在"))
    }

    @Test
    fun `file_info_绝对路径越界_被拒绝`() = runBlocking {
        val result = executor.fileInfo(
            tool("file_info", "path" to outsideAbsolutePath())
        )

        assertFalse(result.success)
    }

    // ===== exists（success 恒为 true，存在性通过消息区分）=====

    @Test
    fun `exists_文件存在_返回文件类型`() = runBlocking {
        File(filesRoot, "exists.txt").createNewFile()

        val result = executor.exists(
            tool("exists", "path" to "exists.txt")
        )

        assertTrue(result.success)
        assertTrue(
            "消息应标记为文件: ${text(result)}",
            text(result).contains("文件")
        )
    }

    @Test
    fun `exists_文件不存在_返回不存在`() = runBlocking {
        val result = executor.exists(
            tool("exists", "path" to "nope.txt")
        )

        assertTrue(result.success)
        assertTrue(
            "消息应标记为不存在: ${text(result)}",
            text(result).contains("不存在")
        )
    }

    @Test
    fun `exists_符号链接祖先越界_被拒绝`() = runBlocking {
        val outsideDir = tempFolder.newFolder("outside-symlink")
        File(outsideDir, "secret.txt").writeText("secret")
        val link = File(filesRoot, "escape")
        val supported = try {
            Files.createSymbolicLink(link.toPath(), outsideDir.toPath())
            true
        } catch (e: Exception) {
            false
        }
        org.junit.Assume.assumeTrue("当前环境不支持符号链接", supported)

        val result = executor.exists(
            tool("exists", "path" to "escape${File.separator}secret.txt")
        )

        assertFalse(result.success)
    }

    // ===== move =====

    @Test
    fun `move_根目录内双端_移动成功`() = runBlocking {
        File(filesRoot, "move-source.txt").writeText("move")

        val result = executor.move(
            tool("move", "source" to "move-source.txt", "destination" to "nested/moved.txt")
        )

        assertTrue("移动应成功: ${result.error}", result.success)
        assertFalse(File(filesRoot, "move-source.txt").exists())
        assertEquals("move", File(filesRoot, "nested/moved.txt").readText())
    }

    @Test
    fun `move_源路径越界_被拒绝且目标未创建`() = runBlocking {
        val result = executor.move(
            tool("move", "source" to outsideAbsolutePath(), "destination" to "target.txt")
        )

        assertFalse(result.success)
        assertFalse(File(filesRoot, "target.txt").exists())
    }

    @Test
    fun `move_目标路径越界_被拒绝且源保留`() = runBlocking {
        val source = File(filesRoot, "keep.txt").apply { writeText("keep") }
        val result = executor.move(
            tool(
                "move",
                "source" to "keep.txt",
                "destination" to File(tempFolder.root, "outside-target.txt").absolutePath,
            )
        )

        assertFalse(result.success)
        assertTrue(source.exists())
    }

    // ===== 辅助 =====

    /**
     * 返回一个在当前 OS 上保证位于 allowed roots 之外的绝对路径。
     * Unix: /etc/passwd；Windows: 系统 hosts 文件路径。
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
