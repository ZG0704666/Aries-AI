/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Phase 3 Task 21A: FileToolExecutor 行为测试。
 *
 * 测试策略：
 * - FileToolExecutor 是 object 单例，不能直接 mock。
 * - 通过 MockK 构造 relaxed mock Context，将其 filesDir/cacheDir 指向
 *   TemporaryFolder 中的真实目录，然后调用 FileToolExecutor.init(mockContext)。
 * - readFile/writeFile/copy/compress 内部通过 getContext() 调用
 *   PathGuard.canonicalizeWithin(Context, path)，可在真实文件系统上验证。
 * - delete/listDir/createDir/exists/fileInfo/move 不依赖 Context（直接 File(path)），
 *   直接传入 TemporaryFolder 的绝对路径测试。
 *
 * 注意：object 单例状态跨测试用例持久。每个 @Before 调用 init() 覆写 Context，
 * 保证各用例隔离。PathGuard 的底层语义由 PathGuardTest 覆盖，本测试聚焦于
 * FileToolExecutor 的行为集成。
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

    @Before
    fun setUp() {
        filesRoot = tempFolder.newFolder("files")
        cacheRoot = tempFolder.newFolder("cache")

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns filesRoot
        every { mockContext.cacheDir } returns cacheRoot
        every { mockContext.getExternalFilesDir(any()) } returns null
        FileToolExecutor.init(mockContext)
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
        val result = FileToolExecutor.writeFile(
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

        val result = FileToolExecutor.writeFile(
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

        val result = FileToolExecutor.writeFile(
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
        FileToolExecutor.writeFile(
            tool("write_file", "path" to "log.txt", "content" to "line1\n", "append" to "false")
        )
        FileToolExecutor.writeFile(
            tool("write_file", "path" to "log.txt", "content" to "line2\n", "append" to "true")
        )

        val content = File(filesRoot, "log.txt").readText()
        assertEquals("line1\nline2\n", content)
    }

    // ===== read_file =====

    @Test
    fun `read_file_合法路径_读取成功`() = runBlocking {
        File(filesRoot, "input.txt").writeText("read me please")

        val result = FileToolExecutor.readFile(
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
        val result = FileToolExecutor.readFile(
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

        val result = FileToolExecutor.copy(
            tool("copy", "source" to "source.txt", "destination" to "dest.txt")
        )

        assertTrue("复制应成功: ${result.error}", result.success)
        val dest = File(filesRoot, "dest.txt")
        assertTrue("目标文件应存在", dest.exists())
        assertEquals("copy me", dest.readText())
    }

    @Test
    fun `copy_源文件不存在_返回错误`() = runBlocking {
        val result = FileToolExecutor.copy(
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

        val result = FileToolExecutor.compress(
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

    // ===== list_dir（不依赖 Context，直接使用 File(path)）=====

    @Test
    fun `list_dir_目录存在_返回列表`() = runBlocking {
        val dir = tempFolder.newFolder("listdir")
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")

        val result = FileToolExecutor.listDir(
            tool("list_dir", "path" to dir.absolutePath)
        )

        assertTrue("列出目录应成功: ${result.error}", result.success)
        val output = text(result)
        assertTrue("应包含 a.txt: $output", output.contains("a.txt"))
        assertTrue("应包含 b.txt: $output", output.contains("b.txt"))
    }

    @Test
    fun `list_dir_空目录_返回空提示`() = runBlocking {
        val dir = tempFolder.newFolder("emptydir")

        val result = FileToolExecutor.listDir(
            tool("list_dir", "path" to dir.absolutePath)
        )

        assertTrue(result.success)
        assertTrue(
            "应提示目录为空: ${text(result)}",
            text(result).contains("空") || text(result).contains("empty")
        )
    }

    // ===== file_info（不依赖 Context）=====

    @Test
    fun `file_info_文件存在_返回元数据`() = runBlocking {
        val file = tempFolder.newFile("info.txt")
        file.writeText("metadata content")

        val result = FileToolExecutor.fileInfo(
            tool("file_info", "path" to file.absolutePath)
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
        val result = FileToolExecutor.fileInfo(
            tool("file_info", "path" to File(tempFolder.root, "nope.txt").absolutePath)
        )

        assertFalse(result.success)
        assertTrue("错误应包含不存在: ${result.error}", result.error.contains("不存在"))
    }

    // ===== exists（不依赖 Context；success 恒为 true，存在性通过消息区分）=====

    @Test
    fun `exists_文件存在_返回文件类型`() = runBlocking {
        val file = tempFolder.newFile("exists.txt")

        val result = FileToolExecutor.exists(
            tool("exists", "path" to file.absolutePath)
        )

        assertTrue(result.success)
        assertTrue(
            "消息应标记为文件: ${text(result)}",
            text(result).contains("文件")
        )
    }

    @Test
    fun `exists_文件不存在_返回不存在`() = runBlocking {
        val result = FileToolExecutor.exists(
            tool("exists", "path" to File(tempFolder.root, "nope.txt").absolutePath)
        )

        assertTrue(result.success)
        assertTrue(
            "消息应标记为不存在: ${text(result)}",
            text(result).contains("不存在")
        )
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
