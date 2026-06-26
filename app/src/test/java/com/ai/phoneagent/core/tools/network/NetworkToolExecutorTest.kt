/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ai.phoneagent.core.tools.network

import org.junit.Assert.*
import org.junit.Test

class NetworkToolExecutorTest {

    @Test
    fun `validateDownloadSavePath allows blank path so default filename can be used`() {
        assertNull(NetworkToolExecutor.validateDownloadSavePath(null))
        assertNull(NetworkToolExecutor.validateDownloadSavePath(""))
        assertNull(NetworkToolExecutor.validateDownloadSavePath("   "))
    }

    @Test
    fun `validateDownloadSavePath allows simple filenames`() {
        assertNull(NetworkToolExecutor.validateDownloadSavePath("report.pdf"))
        assertNull(NetworkToolExecutor.validateDownloadSavePath("image_2026-06-26.png"))
    }

    @Test
    fun `validateDownloadSavePath rejects path traversal`() {
        val error = NetworkToolExecutor.validateDownloadSavePath("../secret.txt")
        assertNotNull(error)
        assertTrue(error!!.contains("保存路径只能是文件名"))
    }

    @Test
    fun `validateDownloadSavePath rejects nested and absolute paths`() {
        val invalidPaths = listOf(
            "nested/file.txt",
            "nested\\file.txt",
            "/tmp/file.txt",
            "C:\\tmp\\file.txt"
        )

        for (path in invalidPaths) {
            val error = NetworkToolExecutor.validateDownloadSavePath(path)
            assertNotNull("应拒绝路径: $path", error)
        }
    }

    @Test
    fun `validateDownloadSavePath rejects blank or unsafe filename segments`() {
        val invalidPaths = listOf(".", "..", "file\u0000.txt")
        for (path in invalidPaths) {
            val error = NetworkToolExecutor.validateDownloadSavePath(path)
            assertNotNull("应拒绝路径: $path", error)
        }
    }
}
