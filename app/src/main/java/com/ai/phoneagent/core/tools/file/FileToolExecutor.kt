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

import android.content.Context
import android.util.Log
import com.ai.phoneagent.BuildConfig
import com.ai.phoneagent.core.security.PathGuard
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.data.model.AITool
import com.ai.phoneagent.data.model.StringResultData
import com.ai.phoneagent.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件系统工具执行器
 * 提供文件读写、目录操作、压缩等功能
 */
class FileToolExecutor(private val appContext: Context) {

    private val TAG = "FileTools"

    /**
     * 读取文件内容
     */
    suspend fun readFile(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("read_file", "缺少 path 参数")

        val safePath = try {
            PathGuard.canonicalizeWithin(appContext, path)
        } catch (e: SecurityException) {
            return@withContext error("read_file", "路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("read_file", "非法路径: ${e.message}")
        }

        try {
            val file = safePath
            if (!file.exists()) {
                return@withContext error("read_file", "文件不存在: ${file.absolutePath}")
            }

            if (file.isDirectory) {
                return@withContext error("read_file", "路径是目录不是文件: ${file.absolutePath}")
            }

            val maxSize = tool.parameters.find { it.name == "max_size" }?.value?.toLongOrNull() ?: 1024 * 1024
            val content = if (file.length() > maxSize) {
                file.inputStream().use { it.bufferedReader().readLines().take(100).joinToString("\n") }
            } else {
                file.readText()
            }

            success("read_file", "读取成功 (${file.length()} bytes): ${content.take(200)}")
        } catch (e: Exception) {
            error("read_file", "读取失败: ${e.message}")
        }
    }

    /**
     * 写入文件内容
     */
    suspend fun writeFile(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("write_file", "缺少 path 参数")

        val safePath = try {
            PathGuard.canonicalizeWithin(appContext, path)
        } catch (e: SecurityException) {
            return@withContext error("write_file", "路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("write_file", "非法路径: ${e.message}")
        }

        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBooleanStrictOrNull() ?: false

        try {
            val file = safePath

            // 确保父目录存在
            file.parentFile?.mkdirs()

            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }

            success("write_file", "写入成功: ${file.absolutePath} (${content.length} chars)")
        } catch (e: Exception) {
            error("write_file", "写入失败: ${e.message}")
        }
    }

    /**
     * 删除文件或目录
     */
    suspend fun delete(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("delete", "缺少 path 参数")
        val file = resolvePath("delete", path) ?: return@withContext pathError("delete", path)
        val target = file.toPath()

        try {
            if (!Files.exists(target)) {
                return@withContext error("delete", "文件不存在: ${file.absolutePath}")
            }

            if (Files.isDirectory(target)) {
                deleteTreeWithoutFollowingLinks(target)
            } else {
                Files.delete(target)
            }

            success("delete", "删除成功: ${file.absolutePath}")
        } catch (e: Exception) {
            error("delete", "删除失败: ${e.message}")
        }
    }
    /**
     * 列出目录内容
     */
    suspend fun listDir(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("list_dir", "缺少 path 参数")
        val dir = resolvePath("list_dir", path) ?: return@withContext pathError("list_dir", path)

        try {
            if (!dir.exists()) {
                return@withContext error("list_dir", "目录不存在: ${dir.absolutePath}")
            }

            if (!dir.isDirectory) {
                return@withContext error("list_dir", "路径不是目录: ${dir.absolutePath}")
            }

            val files = dir.listFiles()?.map {
                val type = if (it.isDirectory) "DIR" else "FILE"
                val size = if (it.isFile) " (${it.length()} bytes)" else ""
                "$type ${it.name}$size"
            }?.joinToString("\n") ?: ""

            val resultMessage = if (files.isNotEmpty()) {
                "目录内容 (${dir.absolutePath}):\n$files"
            } else {
                "目录为空: ${dir.absolutePath}"
            }

            success("list_dir", resultMessage)
        } catch (e: Exception) {
            error("list_dir", "列出目录失败: ${e.message}")
        }
    }

    /**
     * 创建目录
     */
    suspend fun createDir(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("create_dir", "缺少 path 参数")
        val dir = resolvePath("create_dir", path) ?: return@withContext pathError("create_dir", path)

        try {
            val created = dir.mkdirs()

            if (created) {
                success("create_dir", "创建目录成功: ${dir.absolutePath}")
            } else {
                if (dir.exists()) {
                    success("create_dir", "目录已存在: ${dir.absolutePath}")
                } else {
                    error("create_dir", "创建目录失败: ${dir.absolutePath}")
                }
            }
        } catch (e: Exception) {
            error("create_dir", "创建目录失败: ${e.message}")
        }
    }

    /**
     * 判断文件/目录是否存在
     */
    suspend fun exists(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("exists", "缺少 path 参数")
        val file = resolvePath("exists", path) ?: return@withContext pathError("exists", path)

        try {
            val exists = file.exists()
            val type = when {
                !exists -> "不存在"
                file.isDirectory -> "目录"
                else -> "文件"
            }

            success("exists", "${file.absolutePath} ($type)")
        } catch (e: Exception) {
            error("exists", "检查失败: ${e.message}")
        }
    }

    /**
     * 复制文件
     */
    suspend fun copy(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val source = tool.parameters.find { it.name == "source" }?.value
            ?: return@withContext error("copy", "缺少 source 参数")

        val dest = tool.parameters.find { it.name == "destination" }?.value
            ?: return@withContext error("copy", "缺少 destination 参数")

        val safeSource = try {
            PathGuard.canonicalizeWithin(appContext, source)
        } catch (e: SecurityException) {
            return@withContext error("copy", "源路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("copy", "非法源路径: ${e.message}")
        }

        val safeDest = try {
            PathGuard.canonicalizeWithin(appContext, dest)
        } catch (e: SecurityException) {
            return@withContext error("copy", "目标路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("copy", "非法目标路径: ${e.message}")
        }

        try {
            val srcFile = safeSource
            val destFile = safeDest

            if (!srcFile.exists()) {
                return@withContext error("copy", "源文件不存在: ${srcFile.absolutePath}")
            }

            if (srcFile.isDirectory) {
                return@withContext error("copy", "不支持复制目录: ${srcFile.absolutePath}")
            }

            destFile.parentFile?.mkdirs()
            srcFile.copyTo(destFile, overwrite = true)

            success("copy", "复制成功: ${srcFile.absolutePath} -> ${destFile.absolutePath}")
        } catch (e: Exception) {
            error("copy", "复制失败: ${e.message}")
        }
    }

    /**
     * 移动/重命名文件
     */
    suspend fun move(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val source = tool.parameters.find { it.name == "source" }?.value
            ?: return@withContext error("move", "缺少 source 参数")

        val dest = tool.parameters.find { it.name == "destination" }?.value
            ?: return@withContext error("move", "缺少 destination 参数")
        val srcFile = resolvePath("move", source, "源") ?: return@withContext pathError("move", source, "源")
        val destFile = resolvePath("move", dest, "目标") ?: return@withContext pathError("move", dest, "目标")

        try {
            if (!srcFile.exists()) {
                return@withContext error("move", "源文件不存在: ${srcFile.absolutePath}")
            }

            destFile.parentFile?.mkdirs()
            val moved = srcFile.renameTo(destFile)

            if (moved) {
                success("move", "移动成功: ${srcFile.absolutePath} -> ${destFile.absolutePath}")
            } else {
                error("move", "移动失败: ${srcFile.absolutePath} -> ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            error("move", "移动失败: ${e.message}")
        }
    }

    /**
     * 获取文件信息
     */
    suspend fun fileInfo(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value
            ?: return@withContext error("file_info", "缺少 path 参数")
        val file = resolvePath("file_info", path) ?: return@withContext pathError("file_info", path)

        try {
            if (!file.exists()) {
                return@withContext error("file_info", "文件不存在: ${file.absolutePath}")
            }

            val info = buildString {
                appendLine("文件: ${file.name}")
                appendLine("路径: ${file.absolutePath}")
                appendLine("大小: ${file.length()} bytes")
                appendLine("可读: ${file.canRead()}")
                appendLine("可写: ${file.canWrite()}")
                appendLine("可执行: ${file.canExecute()}")
                appendLine("是目录: ${file.isDirectory}")
                appendLine("是文件: ${file.isFile}")
                appendLine("最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(file.lastModified())}")
            }

            success("file_info", info.trim())
        } catch (e: Exception) {
            error("file_info", "获取信息失败: ${e.message}")
        }
    }

    /**
     * 创建压缩文件 (ZIP)
     */
    suspend fun compress(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val source = tool.parameters.find { it.name == "source" }?.value
            ?: return@withContext error("compress", "缺少 source 参数")

        val dest = tool.parameters.find { it.name == "destination" }?.value
            ?: return@withContext error("compress", "缺少 destination 参数")

        val safeSource = try {
            PathGuard.canonicalizeWithin(appContext, source)
        } catch (e: SecurityException) {
            return@withContext error("compress", "源路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("compress", "非法源路径: ${e.message}")
        }

        val safeDest = try {
            PathGuard.canonicalizeWithin(appContext, dest)
        } catch (e: SecurityException) {
            return@withContext error("compress", "目标路径越界被拒绝: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return@withContext error("compress", "非法目标路径: ${e.message}")
        }

        val sourcePath = safeSource.toPath()
        val destinationPath = safeDest.toPath()
        var tempPath: Path? = null

        try {
            if (!Files.exists(sourcePath)) {
                return@withContext error("compress", "源文件不存在: ${safeSource.absolutePath}")
            }
            if (Files.isDirectory(sourcePath) && destinationPath.startsWith(sourcePath)) {
                return@withContext error("compress", "目标压缩文件不能位于源目录内部")
            }

            val destinationParent = destinationPath.parent
                ?: return@withContext error("compress", "目标路径缺少父目录")
            Files.createDirectories(destinationParent)
            val outputPath = Files.createTempFile(destinationParent, "compress-", ".tmp")
            tempPath = outputPath

            ZipOutputStream(Files.newOutputStream(outputPath)).use { zos ->
                if (Files.isDirectory(sourcePath)) {
                    addDirectoryToZip(sourcePath, zos)
                } else {
                    rejectSymbolicLink(sourcePath)
                    addFileToZip(sourcePath, safeSource.name, zos)
                }
            }

            replaceAtomically(outputPath, destinationPath)
            tempPath = null
            success("compress", "压缩成功: ${safeSource.absolutePath} -> ${safeDest.absolutePath}")
        } catch (e: Exception) {
            error("compress", "压缩失败: ${e.message}")
        } finally {
            tempPath?.let { Files.deleteIfExists(it) }
        }
    }

    private fun deleteTreeWithoutFollowingLinks(root: Path) {
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                    error?.let { throw it }
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun addDirectoryToZip(sourceRoot: Path, zos: ZipOutputStream) {
        Files.walkFileTree(
            sourceRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    rejectSymbolicLink(dir)
                    if (dir != sourceRoot) {
                        val entryName = zipEntryName(sourceRoot.relativize(dir)) + "/"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    rejectSymbolicLink(file)
                    addFileToZip(file, zipEntryName(sourceRoot.relativize(file)), zos)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun addFileToZip(file: Path, entryName: String, zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry(entryName))
        Files.newInputStream(file).use { input -> input.copyTo(zos) }
        zos.closeEntry()
    }

    private fun rejectSymbolicLink(path: Path) {
        if (Files.isSymbolicLink(path)) {
            throw SecurityException("压缩源包含符号链接: $path")
        }
    }

    private fun zipEntryName(path: Path): String =
        path.joinToString("/") { segment -> segment.toString() }

    private fun replaceAtomically(source: Path, destination: Path) {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ============ 辅助函数 ============

    private fun resolvePath(toolName: String, path: String, role: String = ""): File? {
        return try {
            PathGuard.canonicalizeWithin(appContext, path)
        } catch (e: SecurityException) {
            logPathFailure(toolName, role, path, e)
            null
        } catch (e: IllegalArgumentException) {
            logPathFailure(toolName, role, path, e)
            null
        }
    }

    private fun pathError(toolName: String, path: String, role: String = ""): ToolResult {
        val prefix = if (role.isBlank()) "" else "$role"
        return error(toolName, "${prefix}路径越界或非法，被拒绝: $path")
    }

    private fun logPathFailure(toolName: String, role: String, path: String, error: Exception) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "$toolName ${role}路径被拒绝: $path", error)
        }
    }

    private fun success(toolName: String, message: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(message),
            error = ""
        )
    }

    private fun error(toolName: String, error: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error = error
        )
    }

}

/**
 * 注册文件系统工具到 AIToolHandler
 */
fun registerFileTools(handler: AIToolHandler, executor: FileToolExecutor) {
    // Read File
    handler.registerTool(
        name = "read_file",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "读取文件: $path"
        },
        executor = { tool ->
            executor.readFile(tool)
        }
    )

    // Write File
    handler.registerTool(
        name = "write_file",
        dangerCheck = { true }, // 危险操作：写入文件系统
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "写入文件: $path"
        },
        executor = { tool ->
            executor.writeFile(tool)
        }
    )

    // Delete
    handler.registerTool(
        name = "delete",
        dangerCheck = { true }, // 危险操作
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "删除: $path"
        },
        executor = { tool ->
            executor.delete(tool)
        }
    )

    // List Dir
    handler.registerTool(
        name = "list_dir",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "列出目录: $path"
        },
        executor = { tool ->
            executor.listDir(tool)
        }
    )

    // Create Dir
    handler.registerTool(
        name = "create_dir",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "创建目录: $path"
        },
        executor = { tool ->
            executor.createDir(tool)
        }
    )

    // Exists
    handler.registerTool(
        name = "exists",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "检查存在: $path"
        },
        executor = { tool ->
            executor.exists(tool)
        }
    )

    // Copy
    handler.registerTool(
        name = "copy",
        dangerCheck = { true }, // 危险操作：写入文件系统
        descriptionGenerator = { tool ->
            val source = tool.parameters.find { it.name == "source" }?.value ?: ""
            "复制文件: $source"
        },
        executor = { tool ->
            executor.copy(tool)
        }
    )

    // Move
    handler.registerTool(
        name = "move",
        dangerCheck = { true }, // 危险操作
        descriptionGenerator = { tool ->
            val source = tool.parameters.find { it.name == "source" }?.value ?: ""
            "移动文件: $source"
        },
        executor = { tool ->
            executor.move(tool)
        }
    )

    // File Info
    handler.registerTool(
        name = "file_info",
        dangerCheck = { false },
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            "文件信息: $path"
        },
        executor = { tool ->
            executor.fileInfo(tool)
        }
    )

    // Compress
    handler.registerTool(
        name = "compress",
        dangerCheck = { true }, // 危险操作：写入文件系统
        descriptionGenerator = { tool ->
            val source = tool.parameters.find { it.name == "source" }?.value ?: ""
            "压缩文件: $source"
        },
        executor = { tool ->
            executor.compress(tool)
        }
    )

    if (BuildConfig.DEBUG) Log.d("FileTools", "文件系统工具注册完成")
}
