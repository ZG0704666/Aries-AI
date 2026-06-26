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
import java.io.File

/**
 * 文件路径安全校验器
 * 限制文件操作只能在应用私有目录内进行，防止路径穿越攻击
 */
object FilePathValidator {

    private lateinit var allowedRootDirs: List<File>

    fun init(context: Context) {
        allowedRootDirs = listOf(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null) ?: context.filesDir
        )
    }

    /**
     * 校验路径是否在允许的目录范围内
     * @param path 待校验的路径
     * @return 校验结果，true 表示路径合法
     */
    fun isPathAllowed(path: String): Boolean {
        if (!::allowedRootDirs.isInitialized) return false
        if (path.isBlank()) return false

        return try {
            val targetFile = File(path).canonicalFile
            allowedRootDirs.any { rootDir ->
                val canonicalRoot = rootDir.canonicalFile
                targetFile == canonicalRoot || targetFile.path.startsWith(canonicalRoot.path + File.separator)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 校验路径，如果不合法则返回错误信息
     * @param path 待校验的路径
     * @return null 表示合法，非 null 表示错误信息
     */
    fun validatePath(path: String): String? {
        if (path.isBlank()) return "路径不能为空"
        if (!isPathAllowed(path)) return "路径不在允许的范围内: $path"
        return null
    }

    /**
     * 校验多个路径（如 source 和 destination）
     */
    fun validatePaths(vararg paths: String): String? {
        for (path in paths) {
            val error = validatePath(path)
            if (error != null) return error
        }
        return null
    }
}
