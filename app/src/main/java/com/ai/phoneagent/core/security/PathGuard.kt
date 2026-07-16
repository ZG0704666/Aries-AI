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
package com.ai.phoneagent.core.security

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 路径安全护栏。
 *
 * 负责将 AI 工具传入的文件路径规范化到一组受信任的根目录之内，
 * 拒绝符号链接越界、`../` 路径穿越以及任意绝对路径访问。
 *
 * 所有写入 / 复制 / 压缩 / 读取类工具在操作真实文件系统前，
 * 都应先调用 [canonicalizeWithin] 取得受信任的 [File] 引用。
 */
object PathGuard {

    /**
     * 将 [path] 规范化到 [allowedRoots] 中的某一个根目录之内。
     *
     * 实现要点：
     * 1. 拒绝 null / 空白路径。
     * 2. 若 [path] 是相对路径，则相对于 [allowedRoots] 的首个根目录解析。
     * 3. 调用 [File.canonicalFile] 解析 `../` 与符号链接。
     * 4. 要求最终的 canonicalPath 必须以某个 allowedRoot 的 canonicalPath 为前缀，
     *    且严格通过 `File.separator` 边界匹配，避免 `/data/data/com.foo` 误匹配
     *    `/data/data/com.foobar` 这类前缀冲突。
     *
     * @param allowedRoots 允许访问的根目录列表（不可为空）
     * @param path 请求访问的路径，可以是绝对路径或相对路径
     * @return 规范化后且确认位于某个 allowedRoot 内的 [File]
     * @throws IllegalArgumentException 当 path 为 null/空白，或 allowedRoots 为空时
     * @throws SecurityException 当规范化后的路径不在任何 allowedRoot 之内时
     */
    fun canonicalizeWithin(allowedRoots: List<File>, path: String?): File {
        if (path.isNullOrBlank()) {
            throw IllegalArgumentException("Path must not be null or blank")
        }
        if (allowedRoots.isEmpty()) {
            throw IllegalArgumentException("allowedRoots must not be empty")
        }

        val requestedPath = try {
            Paths.get(path)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid path: ${e.message}", e)
        }
        if (requestedPath.any { it.toString() == ".." }) {
            throw SecurityException("Parent path traversal is not allowed")
        }

        val canonicalRoots = allowedRoots.map { root ->
            require(root.exists()) { "Allowed root does not exist: ${root.absolutePath}" }
            root.toPath().toRealPath()
        }
        val resolvedPath = if (requestedPath.isAbsolute) {
            requestedPath
        } else {
            canonicalRoots.first().resolve(requestedPath)
        }
        val normalizedTarget = resolvedPath.toAbsolutePath().normalize()
        val realTarget = resolveThroughNearestExistingAncestor(normalizedTarget)
        if (canonicalRoots.none { realTarget.startsWith(it) }) {
            throw SecurityException("Path '$path' resolves outside allowed roots")
        }
        return realTarget.toFile()
    }

    /**
     * 便捷重载：以应用私有的 [Context.filesDir]、[Context.cacheDir] 与
     * [Context.getExternalFilesDir] 作为允许的根目录集合。
     *
     * 任意一项不可用时（如 getExternalFilesDir 返回 null）将跳过该根，
     * 但至少需要一个有效根目录，否则抛出 [IllegalArgumentException]。
     *
     * @param appContext 应用上下文
     * @param path 请求访问的路径
     * @return 规范化后且位于受信任根目录之内的 [File]
     */
    fun canonicalizeWithin(appContext: Context, path: String?): File {
        val roots = buildList {
            appContext.filesDir?.let { add(it) }
            appContext.cacheDir?.let { add(it) }
            appContext.getExternalFilesDir(null)?.let { add(it) }
        }
        require(roots.isNotEmpty()) { "No available app private directories" }
        return canonicalizeWithin(roots, path)
    }

    private fun resolveThroughNearestExistingAncestor(target: Path): Path {
        var ancestor: Path? = target
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.parent
        }
        if (ancestor == null) {
            throw SecurityException("Path has no existing ancestor")
        }

        val realAncestor = ancestor.toRealPath()
        val missingSuffix = ancestor.relativize(target)
        return realAncestor.resolve(missingSuffix).normalize()
    }
}
