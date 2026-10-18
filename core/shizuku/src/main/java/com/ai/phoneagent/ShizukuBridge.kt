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
 * 基于 Shizuku binder 的 Shell 执行桥接。
 *
 * 统一封装命令执行结果，供上层在获得授权后执行 uiautomator、文件读写等特权操作。
 */
object ShizukuBridge {

    private const val TAG = "AriesShizuku"
    private const val DEFAULT_UI_DUMP_PATH = "/data/local/tmp/aries_ui_dump.xml"

    /**
     * 命令执行结果。
     *
     * @property exitCode 进程退出码，负数表示桥接自身的前置校验失败。
     * @property stdout 标准输出原始字节。
     * @property stderr 标准错误原始字节。
     */
    data class ExecResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray,
    ) {
        /** 以 UTF-8 解码标准输出，解码异常时返回空串。 */
        fun stdoutText(): String {
            return try {
                stdout.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        /** 以 UTF-8 解码标准错误，解码异常时返回空串。 */
        fun stderrText(): String {
            return try {
                stderr.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    /** 通过反射调用 Shizuku 的 newProcess 创建子进程句柄。 */
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

    /** 探测 Shizuku binder 是否存活。 */
    @JvmStatic
    fun pingBinder(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /** 判断本应用是否已被授予 Shizuku 权限。 */
    @JvmStatic
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /** 发起 Shizuku 权限申请；调用成功仅代表请求已发出。 */
    @JvmStatic
    fun requestPermission(requestCode: Int): Boolean {
        return try {
            Shizuku.requestPermission(requestCode)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 执行 shell 命令，仅返回成功时的标准输出字节。 */
    @JvmStatic
    fun execBytes(command: String): ByteArray {
        val r = execResult(command)
        return if (r.exitCode == 0) r.stdout else ByteArray(0)
    }

    /** 以参数列表方式执行命令，仅返回成功时的标准输出字节。 */
    @JvmStatic
    fun execBytesArgs(args: List<String>): ByteArray {
        val r = execResultArgs(args)
        return if (r.exitCode == 0) r.stdout else ByteArray(0)
    }

    /** 执行 shell 命令并以 UTF-8 文本返回标准输出。 */
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

    /** 以 `sh -c` 执行命令并返回完整执行结果。 */
    @JvmStatic
    fun execResult(command: String): ExecResult {
        return execResultInternal(arrayOf("sh", "-c", command), command)
    }

    /** 以参数列表方式执行命令并返回完整执行结果；空参数直接失败。 */
    @JvmStatic
    fun execResultArgs(args: List<String>): ExecResult {
        if (args.isEmpty()) return ExecResult(exitCode = -3, stdout = ByteArray(0), stderr = ByteArray(0))
        val printable = args.joinToString(" ") { part ->
            if (part.any { it.isWhitespace() }) "\"$part\"" else part
        }
        return execResultInternal(args.toTypedArray(), printable)
    }

    /**
     * 执行命令的核心实现。
     *
     * 先校验 binder 与权限，再读取进程输出并等待退出；失败路径返回负数退出码便于上层区分原因。
     */
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

    /** 完整读取输入流字节，读取异常时返回空数组。 */
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
     * 通过 uiautomator 导出当前窗口层级 XML。
     *
     * 失败时会在同一次调用内重试（含 `--compressed` 兜底）；全部尝试失败返回 null。
     *
     * @param outputPath 设备侧的临时导出路径。
     * @param retries 最大尝试次数。
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

            // dump 前先清理旧临时文件，避免读到上次的残留结果。
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

            // 个别 ROM 会先打印路径文本却读不到有效 XML，改为读取默认路径兜底。
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

    /** 从 uiautomator 输出中解析实际导出路径，兼容多语言提示与路径正则。 */
    private fun parseDumpPath(output: String): String {
        val markers =
                listOf(
                        "UI hierarchy dumped to:",
                        "UI hierchary dumped to:", // Android 原生输出在多数构建上保留了该拼写错误。
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

    /** 同时满足 binder 存活与已授权时才视为可用。 */
    @JvmStatic
    fun isShizukuAvailable(): Boolean {
        return pingBinder() && hasPermission()
    }
}
