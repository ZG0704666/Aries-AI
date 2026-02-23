/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under the AGPL-3.0. See LICENSE for details.
 */
package com.ai.phoneagent

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/**
 * ShizukuBridge
 *
 * - Executes shell command via Shizuku binder
 * - Provides unified process result wrapper
 */
object ShizukuBridge {

    private const val TAG = "AriesShizuku"
    private const val DEFAULT_UI_DUMP_PATH = "/data/local/tmp/aries_ui_dump.xml"

    data class ExecResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray,
    ) {
        fun stdoutText(): String {
            return try {
                stdout.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        fun stderrText(): String {
            return try {
                stderr.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun newProcess(command: Array<String>): Any {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null)
    }

    @JvmStatic
    fun pingBinder(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun execBytes(command: String): ByteArray {
        val r = execResult(command)
        return if (r.exitCode == 0) r.stdout else ByteArray(0)
    }

    @JvmStatic
    fun execBytesArgs(args: List<String>): ByteArray {
        val r = execResultArgs(args)
        return if (r.exitCode == 0) r.stdout else ByteArray(0)
    }

    @JvmStatic
    fun execText(command: String): String {
        val bytes = execBytes(command)
        if (bytes.isEmpty()) return ""
        return try {
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun execResult(command: String): ExecResult {
        return execResultInternal(arrayOf("sh", "-c", command), command)
    }

    @JvmStatic
    fun execResultArgs(args: List<String>): ExecResult {
        if (args.isEmpty()) return ExecResult(exitCode = -3, stdout = ByteArray(0), stderr = ByteArray(0))
        val printable = args.joinToString(" ") { part ->
            if (part.any { it.isWhitespace() }) "\"$part\"" else part
        }
        return execResultInternal(args.toTypedArray(), printable)
    }

    private fun execResultInternal(processArgs: Array<String>, commandLabel: String): ExecResult {
        return try {
            if (!pingBinder()) return ExecResult(exitCode = -1, stdout = ByteArray(0), stderr = ByteArray(0))
            if (!hasPermission()) return ExecResult(exitCode = -2, stdout = ByteArray(0), stderr = ByteArray(0))

            val process = newProcess(processArgs)
            val cls = process.javaClass
            val inputStream = cls.getMethod("getInputStream").invoke(process) as java.io.InputStream
            val errorStream = cls.getMethod("getErrorStream").invoke(process) as java.io.InputStream

            val outBytes = readAllBytes(inputStream)
            val errBytes = readAllBytes(errorStream)
            val exitCode = cls.getMethod("waitFor").invoke(process) as Int

            if (exitCode != 0) {
                val stderrText = try {
                    errBytes.toString(Charsets.UTF_8).trim()
                } catch (_: Exception) {
                    ""
                }
                val stdoutText = try {
                    outBytes.toString(Charsets.UTF_8).trim()
                } catch (_: Exception) {
                    ""
                }
                Log.w(TAG, "Shizuku exec failed: exitCode=$exitCode cmd=$commandLabel stderr=${stderrText.take(500)} stdout=${stdoutText.take(500)}")
            }

            ExecResult(exitCode = exitCode, stdout = outBytes, stderr = errBytes)
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku exec exception: ${t.message}")
            ExecResult(exitCode = -3, stdout = ByteArray(0), stderr = ByteArray(0))
        }
    }

    private fun readAllBytes(input: java.io.InputStream): ByteArray {
        return try {
            input.use { ins ->
                ByteArrayOutputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            }
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    /**
     * Dump current window hierarchy xml by uiautomator.
     */
    @JvmStatic
    fun dumpUiHierarchyXml(
            outputPath: String = DEFAULT_UI_DUMP_PATH,
            retries: Int = 2
    ): String? {
        if (retries <= 0) return null
        var lastMessage = ""

        repeat(retries) { attempt ->
            if (!isShizukuAvailable()) {
                Log.w(TAG, "Shizuku unavailable when dumping ui hierarchy, attempt=${attempt + 1}")
                return null
            }

            // Ensure temp file is clean before dump.
            execResult("rm -f '$outputPath'")

            var dumpResult = execResult("uiautomator dump '$outputPath'")
            if (dumpResult.exitCode != 0) {
                // 某些机型在默认 dump 路径上更容易触发异常，尝试 compressed 兜底。
                dumpResult = execResult("uiautomator dump --compressed '$outputPath'")
            }
            if (dumpResult.exitCode != 0) {
                lastMessage = "dump cmd exit=${dumpResult.exitCode}"
                return@repeat
            }

            val dumpOutput = buildString {
                append(dumpResult.stdoutText())
                val err = dumpResult.stderrText()
                if (err.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(err)
                }
            }.trim()
            val resolvedPath = parseDumpPath(dumpOutput).ifBlank { outputPath }
            val raw = execResult("cat '$resolvedPath'").stdoutText()
            val xml = raw.replace("\u0000", "").trim()
            if (xml.contains("<hierarchy") || xml.contains("<node")) {
                return xml
            }

            // Some ROMs print path text but fail to return valid XML on first read.
            val fallbackRaw =
                    if (resolvedPath != outputPath) execResult("cat '$outputPath'").stdoutText() else ""
            val fallbackXml = fallbackRaw.replace("\u0000", "").trim()
            if (fallbackXml.contains("<hierarchy") || fallbackXml.contains("<node")) {
                return fallbackXml
            }

            lastMessage = "invalid ui hierarchy output, dumpOutput=${dumpOutput.take(200)}"
        }

        Log.w(TAG, "dumpUiHierarchyXml failed after $retries attempts: $lastMessage")
        return null
    }

    private fun parseDumpPath(output: String): String {
        val markers =
                listOf(
                        "UI hierarchy dumped to:",
                        "UI hierchary dumped to:", // Android built-in output uses this typo on many builds.
                        "UI层次结构已转储到:",
                        "UI 层次结构已转储到:",
                )
        for (marker in markers) {
            if (output.contains(marker)) {
                return output.substringAfter(marker).lineSequence().firstOrNull()?.trim().orEmpty()
            }
        }
        val pathRegex = Regex("""(/[^\s'"]+\.xml)""")
        return pathRegex.find(output)?.groupValues?.get(1).orEmpty()
    }

    /**
     * Check if Shizuku binder and permission are available.
     */
    @JvmStatic
    fun isShizukuAvailable(): Boolean {
        return pingBinder() && hasPermission()
    }
}
