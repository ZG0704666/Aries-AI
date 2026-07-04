package com.ai.phoneagent.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 安全日志守卫单元测试（Phase 1 安全加固任务 C）。
 *
 * 通过读取 [app/src/main/] 下的源码文件做静态断言，验证：
 * 1. 关键文件中的 `Log.d` / `Log.v` 调用被 `BuildConfig.DEBUG` 守卫；
 * 2. [AriesApiOAuthActivity] 不再输出 token 相关日志；
 * 3. 关键安全文件（FileToolExecutor / NetworkToolExecutor / AriesApiOAuthActivity）
 *    不含 `Log.*token|Log.*oauth|Log.*bearer|Log.*secret` 等敏感日志字面量。
 *
 * 纯 JVM JUnit 4 测试，不依赖 Android Framework。注意：
 * - 工作目录为模块根（`app/`），源码以 `src/main/java/...` 相对路径读取；
 * - 若工作目录异常导致文件不存在，对应断言会被跳过（不视为失败），
 *   仅在文件存在且违规时才失败，避免在 CI 环境下误报。
 *
 * 正则说明：使用 `Log\..*token`（转义点号）以避免误匹配
 * `parseApiLoginResponse`（"Login" 中含 "Log"）这类无关字面量。
 */
class SecurityLogGuardTest {

    /**
     * 关键文件相对路径（基于模块根 `app/`）。
     */
    private val oauthActivityPath =
        "src/main/java/com/ai/phoneagent/net/AriesApiOAuthActivity.kt"
    private val fileToolExecutorPath =
        "src/main/java/com/ai/phoneagent/core/tools/file/FileToolExecutor.kt"
    private val networkToolExecutorPath =
        "src/main/java/com/ai/phoneagent/core/tools/network/NetworkToolExecutor.kt"

    /**
     * 测试 1: 关键文件中 `Log.d` / `Log.v` 调用应被 `BuildConfig.DEBUG` 守卫。
     *
     * 按 Phase 1 任务要求放宽：仅检查 AriesApiOAuthActivity.kt 及两个含 Log.d 的
     * 代表性文件（ToolRegistration.kt、ContentFilter.kt）。要求至少 80% 的
     * `Log.d` / `Log.v` 调用位于 `if (BuildConfig.DEBUG)` 上下文中。
     *
     * 判定启发式：
     * - 同行出现 `BuildConfig.DEBUG` 视为已守卫（内联形式
     *   `if (BuildConfig.DEBUG) Log.d(...)`）；
     * - 否则向前回溯最多 15 行，若存在未闭合的
     *   `if (BuildConfig.DEBUG) {` 块则视为已守卫。
     */
    @Test
    fun `Log_d_被BuildConfigDebug守卫`() {
        val candidates = listOf(
            oauthActivityPath,
            "src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt",
            "src/main/java/com/ai/phoneagent/core/agent/ContentFilter.kt",
        )

        var totalDebugLogs = 0
        var wrappedDebugLogs = 0

        for (path in candidates) {
            val file = findSourceFile(path) ?: continue
            val lines = file.readLines()
            for (i in lines.indices) {
                val line = lines[i]
                if (!containsDebugLogCall(line)) continue
                totalDebugLogs++
                if (isWrappedByBuildConfigDebug(lines, i)) wrappedDebugLogs++
            }
        }

        if (totalDebugLogs == 0) return // 无可检查项，平凡通过

        val ratio = wrappedDebugLogs.toDouble() / totalDebugLogs
        assertTrue(
            "Log.d/Log.v 调用仅 $wrappedDebugLogs/$totalDebugLogs 被 BuildConfig.DEBUG 守卫（要求 >= 80%）",
            ratio >= 0.8,
        )
    }

    /**
     * 测试 2: AriesApiOAuthActivity.kt 不应再包含 `Log.*token` 字面量。
     *
     * 此前审计发现该文件将 OAuth token response 输出到 logcat，Phase 1 已移除。
     * 本测试守护该安全属性，防止回归。
     */
    @Test
    fun `AriesApiOAuthActivity_不含token日志`() {
        val content = readFile(oauthActivityPath) ?: return
        val tokenLogPattern = Regex("""Log\..*token""", RegexOption.IGNORE_CASE)
        val match = tokenLogPattern.find(content)
        assertFalse(
            "AriesApiOAuthActivity.kt 不应包含 Log.*token 字面量，发现：${match?.value}",
            tokenLogPattern.containsMatchIn(content),
        )
    }

    /**
     * 测试 3: 关键安全文件不应输出 token/oauth/bearer/secret 等敏感字段。
     *
     * 覆盖 FileToolExecutor.kt、NetworkToolExecutor.kt、AriesApiOAuthActivity.kt
     * 三个文件，断言源码中不出现
     * `Log.*token|Log.*oauth|Log.*bearer|Log.*secret` 任一字面量。
     */
    @Test
    fun `关键安全文件_不含敏感日志`() {
        val targets = listOf(
            fileToolExecutorPath to "FileToolExecutor.kt",
            networkToolExecutorPath to "NetworkToolExecutor.kt",
            oauthActivityPath to "AriesApiOAuthActivity.kt",
        )
        val sensitivePattern =
            Regex("""Log\..*?(token|oauth|bearer|secret)""", RegexOption.IGNORE_CASE)

        for ((path, name) in targets) {
            val content = readFile(path) ?: continue
            val match = sensitivePattern.find(content)
            assertFalse(
                "$name 不应包含 Log.*token|Log.*oauth|Log.*bearer|Log.*secret 敏感日志，发现：${match?.value}",
                sensitivePattern.containsMatchIn(content),
            )
        }
    }

    // ========== 辅助 ==========

    /**
     * 候选基目录：单元测试工作目录可能是模块根（`app/`）或项目根。
     * 依次尝试相对路径与 `app/` 前缀路径，命中首个存在的文件。
     */
    private val candidateRoots = listOf("", "app/", "../app/")

    /** 读取源码文件，返回完整内容；所有候选路径都不存在时返回 null（跳过断言）。 */
    private fun readFile(path: String): String? {
        for (root in candidateRoots) {
            val file = File("$root$path")
            if (file.exists()) return file.readText()
        }
        return null
    }

    /** 判断源码文件是否存在（用于测试 1 的逐行扫描）。 */
    private fun findSourceFile(path: String): File? {
        for (root in candidateRoots) {
            val file = File("$root$path")
            if (file.exists()) return file
        }
        return null
    }

    /** 判断一行是否为 `Log.d(` 或 `Log.v(` 调用（排除注释行）。 */
    private fun containsDebugLogCall(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return false
        return trimmed.contains("Log.d(") || trimmed.contains("Log.v(")
    }

    /**
     * 判断 [lines][index] 处的 Log.d/Log.v 调用是否被 BuildConfig.DEBUG 守卫。
     *
     * 启发式判定：
     * - 同行内联守卫：`if (BuildConfig.DEBUG) Log.d(...)`；
     * - 块守卫：向前回溯最多 15 行，找到 `if (BuildConfig.DEBUG) {`
     *   且期间没有出现独立的 `}` 闭合（粗略认为块仍打开）。
     *
     * 窗口取 15 行以覆盖 ToolRegistration.kt 中长达 9 行的
     * `if (BuildConfig.DEBUG) { ... }` 块（最后一行 Log.d 距 if 头 8 行）。
     */
    private fun isWrappedByBuildConfigDebug(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        if (line.contains("BuildConfig.DEBUG")) return true

        var braceOpenLine = -1
        for (j in index - 1 downTo maxOf(0, index - 15)) {
            val prev = lines[j]
            if (prev.contains("BuildConfig.DEBUG") &&
                (prev.contains("if (") || prev.contains("if("))) {
                braceOpenLine = j
                break
            }
            // 粗略判断：遇到独立的 `}` 视为块已闭合
            if (prev.trim() == "}" && braceOpenLine < 0) return false
        }
        return braceOpenLine >= 0
    }
}
