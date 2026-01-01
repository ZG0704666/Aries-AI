package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.FunctionType
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.util.concurrent.*

class FileBindingService(context: Context) {

    companion object {
        private const val TAG = "FileBindingService"
        private val EDIT_BLOCK_REGEX =
            """\[START-(REPLACE|DELETE)\]\s*\n(.*?)\[END-\1\]""".toRegex(
                RegexOption.DOT_MATCHES_ALL
            )
        private const val PARALLEL_MIN_ITERATIONS = 2000
    }

    private enum class EditAction {
        REPLACE,
        DELETE
    }

    private data class EditOperation(
            val action: EditAction,
            val oldContent: String,
            val newContent: String
    )

    private data class MatchSearchResult(
            val bestScore: Double,
            val startLine: Int,
            val endLine: Int,
            val windows: Int,
            val lcsCalculations: Int
    )

    /**
     * Processes file binding by applying structured edit blocks.
     * This new approach abandons line numbers and sub-agents in favor of fuzzy content matching.
     *
     * 1.  Parses `[START-REPLACE]` or `[START-DELETE]` blocks from the AI-generated code.
     * 2.  For each block, it uses the content of the `[OLD]` section as a search pattern.
     * 3.  It performs a fuzzy match of the `[OLD]` content against the `originalContent` to find the
     *     exact lines to be modified, ignoring whitespace and newlines.
     * 4.  Once the correct range is identified, it applies the `REPLACE` or `DELETE` operation.
     * 5.  If no structured blocks are found, it defaults to a full file replacement.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code, containing either edit blocks or full content.
     * @return A Pair containing the final merged content and a diff string.
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            onProgress: ((Float, String) -> Unit)? = null
    ): Pair<String, String> {
        if (originalContent.isNotEmpty() && !aiGeneratedCode.contains("[START-")) {
            val errorMsg =
                "如果你想覆盖这个文件，请删除文件后再写入;如果你想修改文件，请严格使用OLD/NEW的格式进行替换或者使用DELETE进行删除部分。" +
                "所有补丁内容都必须写在 apply_file 的 content 参数里，并使用 [START-REPLACE]/[START-DELETE] + [OLD]/[NEW] 这样的结构化块，而不是直接输出整个文件的新内容进行覆盖。"
            AppLogger.w(TAG, "Refusing full overwrite for existing content without structured edit blocks. $errorMsg")
            return Pair(originalContent, errorMsg)
        }

        if (aiGeneratedCode.contains("[START-")) {
            onProgress?.invoke(0f, "Parsing patch...")
            AppLogger.d(TAG, "Structured edit blocks detected. Attempting fuzzy patch.")
            try {
                val (success, resultString) = applyFuzzyPatch(originalContent, aiGeneratedCode, onProgress)
                if (success) {
                    onProgress?.invoke(1f, "Patch applied")
                    AppLogger.d(TAG, "Fuzzy patch succeeded.")
                    val diffString = generateDiff(originalContent.replace("\r\n", "\n"), resultString)
                    return Pair(resultString, diffString)
                } else {
                    AppLogger.w(TAG, "Fuzzy patch application failed. Reason: $resultString")
                    return Pair(originalContent, "Error: Could not apply patch. Reason: $resultString")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during fuzzy patch process.", e)
                return Pair(originalContent, "Error: An unexpected exception occurred during the patching process: ${e.message}")
            }
        }

        // Default to full file replacement if no special instructions are found
        AppLogger.d(TAG, "No structured blocks found. Assuming full file replacement.")
        val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
        val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()
        val diffString = generateDiff(normalizedOriginalContent, normalizedAiGeneratedCode)
        return Pair(normalizedAiGeneratedCode, diffString)
    }

    private fun generateDiff(original: String, modified: String): String {
        return generateUnifiedDiff(original, modified)
    }

    /**
     * Generates a unified diff string with line numbers and change indicators (+, -).
     * This is a public utility that can be used by other services.
     *
     * @param original The original text content.
     * @param modified The modified text content.
     * @return A formatted string representing the unified diff.
     */
    fun generateUnifiedDiff(original: String, modified: String): String {
        val originalLines = if (original.isEmpty()) emptyList() else original.lines()
        val modifiedLines = if (modified.isEmpty()) emptyList() else modified.lines()
        val patch = DiffUtils.diff(originalLines, modifiedLines)

        if (patch.deltas.isEmpty()) {
            return "No changes detected (files are identical)"
        }

        // First, calculate stats
        val sb = StringBuilder()
        var additions = 0
        var deletions = 0
        patch.deltas.forEach { delta ->
            when (delta.type) {
                com.github.difflib.patch.DeltaType.INSERT -> additions += delta.target.lines.size
                com.github.difflib.patch.DeltaType.DELETE -> deletions += delta.source.lines.size
                com.github.difflib.patch.DeltaType.CHANGE -> {
                    additions += delta.target.lines.size
                    deletions += delta.source.lines.size
                }
                else -> {}
            }
        }
        sb.appendLine("Changes: +$additions -$deletions lines")
        // sb.appendLine()

        // Generate a standard unified diff to process
        val unifiedDiffLines = UnifiedDiffUtils.generateUnifiedDiff(
            "a/file",
            "b/file",
            originalLines,
            patch,
            3 // Context lines
        )

        val resultLines = mutableListOf<String>()
        var origLineNum = 0
        var newLineNum = 0
        val hunkHeaderRegex = """^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*$""".toRegex()

        for (line in unifiedDiffLines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> continue // Skip header lines
                line.startsWith("@@") -> {
                    resultLines.add(line)
                    hunkHeaderRegex.find(line)?.let {
                        origLineNum = it.groupValues[1].toInt()
                        newLineNum = it.groupValues[3].toInt()
                    }
                }
                line.startsWith("-") -> {
                    resultLines.add("-${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                }
                line.startsWith("+") -> {
                    resultLines.add("+${newLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    newLineNum++
                }
                line.startsWith(" ") -> {
                    resultLines.add(" ${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                    newLineNum++
                }
            }
        }

        sb.append(resultLines.joinToString("\n"))
        return sb.toString()
    }

    /**
     * Applies a patch based on fuzzy matching of the `[OLD]` content block.
     * This method is the core of the new, more robust patching mechanism.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content, or failure
     * and a detailed error message.
     */
    private fun applyFuzzyPatch(
        originalContent: String,
        aiPatchCode: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): Pair<Boolean, String> {
        try {
            val operations = parseEditOperations(aiPatchCode)
            if (operations.isEmpty()) {
                return Pair(false, "No valid edit operations found in the patch code.")
            }

            onProgress?.invoke(0f, "Searching match...")

            val originalLines = originalContent.lines().toMutableList()
            val enrichedOps = mutableListOf<Triple<EditOperation, Int, Int>>()

            val totalOps = operations.size.coerceAtLeast(1)
            val matchPhaseWeight = 0.8f
            val applyPhaseWeight = 0.2f

            for ((index, op) in operations.withIndex()) {
                val (start, end) = findBestMatchRange(originalLines, op.oldContent) { p, msg ->
                    val overall = (matchPhaseWeight * ((index.toFloat() + p) / totalOps.toFloat()))
                        .coerceIn(0f, 0.99f)
                    onProgress?.invoke(overall, "Matching ${index + 1}/$totalOps: $msg")
                }
                if (start == -1) {
                    AppLogger.w(TAG, "Could not find a suitable match for OLD block: ${op.oldContent.take(100)}...")
                    return Pair(false, "Could not find a match for an OLD block. The file may have changed too much.")
                }
                if (hasMultiplePerfectMatches(originalContent, op.oldContent)) {
                    AppLogger.w(TAG, "Multiple perfect matches found for OLD block; aborting to avoid ambiguous replacement.")
                    return Pair(false, "Found multiple perfect matches for an OLD block in the target file. Please refine the patch so it only matches a single location.")
                }
                enrichedOps.add(Triple(op, start, end))
            }

            // Sort operations by start line in descending order to apply from the bottom up
            enrichedOps.sortByDescending { it.second }

            var applied = 0
            for ((op, start, end) in enrichedOps) {
                AppLogger.d(TAG, "Applying ${op.action} at lines ${start + 1}-${end + 1}")

                // Capture original segment before removal so we can preserve indentation if needed
                val originalSegment = originalLines.subList(start, end + 1).toList()

                // Remove the old lines
                for (i in end downTo start) {
                    originalLines.removeAt(i)
                }

                // If it's a REPLACE, add the new lines
                if (op.action == EditAction.REPLACE) {

                    val newLinesRaw = op.newContent.lines()

                    // For simple single-line replacements, inherit the indentation of the original line
                    val newLines = if (originalSegment.isNotEmpty() &&
                        start == end &&
                        newLinesRaw.size == 1
                    ) {
                        val originalFirstLine = originalSegment.first()
                        val indentPrefix = originalFirstLine.takeWhile { it == ' ' || it == '\t' }
                        val newLine = newLinesRaw.first()

                        if (indentPrefix.isNotEmpty() &&
                            !newLine.startsWith(" ") &&
                            !newLine.startsWith("\t")
                        ) {
                            listOf(indentPrefix + newLine)
                        } else {
                            newLinesRaw
                        }
                    } else {
                        newLinesRaw
                    }

                    originalLines.addAll(start, newLines)
                }

                applied++
                val overall = (matchPhaseWeight + (applyPhaseWeight * (applied.toFloat() / totalOps.toFloat())))
                    .coerceIn(0f, 0.99f)
                onProgress?.invoke(overall, "Applying ${applied}/$totalOps")
            }

            return Pair(true, originalLines.joinToString("\n"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to apply fuzzy patch", e)
            return Pair(false, "Failed to apply fuzzy patch due to an exception: ${e.message}")
        }
    }

    private fun parseEditOperations(patchCode: String): List<EditOperation> {
        val operations = mutableListOf<EditOperation>()
        val lines = patchCode.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("[START-")) {
                val header = line
                val actionStr = header.substringAfter("[START-").substringBefore("]")

                val action = try {
                    EditAction.valueOf(actionStr)
                } catch (e: IllegalArgumentException) {
                    i++
                    continue // Skip invalid action
                }

                var oldContent = ""
                var newContent = ""
                var inBlock: String? = null
                i++ // Move to content

                while (i < lines.size && !lines[i].trim().startsWith("[END-$actionStr]")) {
                    val currentLine = lines[i]
                    val trimmedLine = currentLine.trim()

                    if (trimmedLine.startsWith("[OLD]")) inBlock = "OLD"
                    else if (trimmedLine.startsWith("[NEW]")) inBlock = "NEW"
                    else if (trimmedLine.startsWith("[/OLD]")) inBlock = null
                    else if (trimmedLine.startsWith("[/NEW]")) inBlock = null
                    else {
                        when (inBlock) {
                            "OLD" -> oldContent += currentLine + "\n"
                            "NEW" -> newContent += currentLine + "\n"
                        }
                    }
                    i++
                }

                val normalizedOld = oldContent.removeSuffix("\n").removeSuffix("\r")
                val normalizedNew = newContent.removeSuffix("\n").removeSuffix("\r")

                // Basic validation
                if ((action == EditAction.REPLACE || action == EditAction.DELETE) && normalizedOld.isBlank()) {
                    i++
                    continue // Skip invalid operation
                }
                if (action == EditAction.REPLACE && normalizedNew.isBlank()) {
                    i++
                    continue // Skip invalid operation
                }

                operations.add(EditOperation(action, normalizedOld, normalizedNew))
            }
            i++
        }
        return operations
    }

    private fun findBestMatchRange(
        originalLines: List<String>,
        oldContent: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): Pair<Int, Int> {
        val oldContentLines = oldContent.lines()
        val numOldLines = oldContentLines.size
        if (numOldLines == 0) return -1 to -1
        if (originalLines.isEmpty()) return -1 to -1

        AppLogger.d(TAG, "开始查找最佳匹配范围，原始文件行数: ${originalLines.size}, 目标块行数: $numOldLines")
        val startTime = System.currentTimeMillis()
        var totalWindows = 0
        var lcsCalculations = 0

        // --- 优化1：预计算与规范化 ---
        AppLogger.d(TAG, "开始预计算与规范化...")
        val normalizedOldContent = oldContent.replace(Regex("\\s+"), "")
        val baseNgrams = buildNgrams(normalizedOldContent)
        if (baseNgrams.isEmpty()) {
            AppLogger.w(TAG, "OLD 块在去空白后过短，无法构建 n-gram，放弃匹配。")
            return -1 to -1
        }

        val lineStartIndices = mutableListOf<Int>()
        val normalizedOriginalContent = buildString {
            originalLines.forEachIndexed { index, line ->
                if (index % 1000 == 0 && index > 0) {
                    AppLogger.d(TAG, "正在预处理行: $index/${originalLines.size}")
                }
                lineStartIndices.add(length)
                append(line.replace(Regex("\\s+"), ""))
            }
            lineStartIndices.add(length) // 添加一个末尾索引，方便计算最后一行
        }
        AppLogger.d(TAG, "预计算完成，规范化后字符数: ${normalizedOriginalContent.length}")

        // --- 阶段一：计算目标窗口尺寸范围 ---
        val delta = (numOldLines * 0.2).toInt() + 2 // 扩大到20%的容错范围，并确保至少有2行的浮动
        val targetSizes = (maxOf(1, numOldLines - delta))..(numOldLines + delta)

        var bestMatchScore = 0.0
        var bestMatchRange = -1 to -1

        // --- 阶段二：并行滑动窗口搜索 ---
        val totalIterations = originalLines.size.toLong() * targetSizes.count().toLong()
        AppLogger.d(TAG, "开始滑动窗口匹配（并行），总迭代次数: $totalIterations")

        val processedWindows = java.util.concurrent.atomic.AtomicLong(0L)
        val lastProgressEmitMs = java.util.concurrent.atomic.AtomicLong(0L)

        fun maybeEmitProgress(processed: Long) {
            if (onProgress == null) return
            val total = totalIterations.coerceAtLeast(1L)
            val p = (processed.toDouble() / total.toDouble()).coerceIn(0.0, 0.99)
            val now = System.currentTimeMillis()
            val last = lastProgressEmitMs.get()
            if (now - last < 200L) return
            if (!lastProgressEmitMs.compareAndSet(last, now)) return
            onProgress.invoke(p.toFloat(), "Searching... ${(p * 100).toInt()}%")
        }

        val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val threadCount = minOf(availableCores, originalLines.size)
        val segmentSize = (originalLines.size + threadCount - 1) / threadCount
        val foundPerfectMatch = java.util.concurrent.atomic.AtomicBoolean(false)

        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        try {
            val tasks = mutableListOf<java.util.concurrent.Future<MatchSearchResult>>()

            for (threadIndex in 0 until threadCount) {
                val startLine = threadIndex * segmentSize
                val endExclusive = minOf(originalLines.size, startLine + segmentSize)
                if (startLine >= endExclusive) continue

                val task = java.util.concurrent.Callable {
                    var localBestScore = 0.0
                    var localBestStart = -1
                    var localBestEnd = -1
                    var localWindows = 0
                    var localLcs = 0
                    var localChunk = 0L

                    for (i in startLine until endExclusive) {
                        if (foundPerfectMatch.get()) {
                            break
                        }

                        for (size in targetSizes) {
                            if (foundPerfectMatch.get()) {
                                break
                            }

                            val endLine = i + size
                            if (endLine > originalLines.size) {
                                break
                            }

                            localWindows++
                            localChunk++
                            if (localChunk >= 2048L) {
                                val processed = processedWindows.addAndGet(localChunk)
                                localChunk = 0L
                                maybeEmitProgress(processed)
                            }

                            val startCharIndex = lineStartIndices[i]
                            val endCharIndex = lineStartIndices[endLine]
                            val normalizedWindow =
                                normalizedOriginalContent.substring(startCharIndex, endCharIndex)

                            localLcs++
                            val score = ngramSimilarity(baseNgrams, normalizedWindow)

                            if (score > localBestScore) {
                                localBestScore = score
                                localBestStart = i
                                localBestEnd = endLine - 1
                                val matchPercentage = (localBestScore * 100).toInt()
                                AppLogger.d(
                                    TAG,
                                    "并行块[$threadIndex] 发现更佳匹配: 行 ${i + 1}-$endLine, 相似度: $matchPercentage%"
                                )

                                if (localBestScore == 1.0) {
                                    foundPerfectMatch.set(true)
                                    AppLogger.d(TAG, "并行块[$threadIndex] 已找到100%匹配，提前结束该块搜索。")
                                    return@Callable MatchSearchResult(
                                        localBestScore,
                                        localBestStart,
                                        localBestEnd,
                                        localWindows,
                                        localLcs
                                    )
                                }
                            }
                        }
                    }

                    if (localChunk > 0L) {
                        val processed = processedWindows.addAndGet(localChunk)
                        maybeEmitProgress(processed)
                    }

                    MatchSearchResult(localBestScore, localBestStart, localBestEnd, localWindows, localLcs)
                }

                tasks.add(executor.submit(task))
            }

            for (future in tasks) {
                try {
                    val result = future.get()
                    totalWindows += result.windows
                    lcsCalculations += result.lcsCalculations

                    if (result.bestScore > bestMatchScore) {
                        bestMatchScore = result.bestScore
                        bestMatchRange = result.startLine to result.endLine
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting file binding search result", e)
                }
            }
        } finally {
            executor.shutdown()
        }

        // 记录最终结果
        val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        val result = if (bestMatchScore > 0.9) {

            val (start, end) = bestMatchRange
            AppLogger.d(
                TAG,
                "匹配完成! 最佳匹配: 行 ${start + 1}-${end + 1}, 相似度: ${(bestMatchScore * 100).toInt()}%, " +
                        "总耗时: ${String.format("%.2f", totalTime)}s, " +
                        "总窗口数: $totalWindows, 总LCS计算: $lcsCalculations"
            )
            bestMatchRange
        } else {
            AppLogger.w(TAG, "未找到足够好的匹配 (最高相似度: ${(bestMatchScore * 100).toInt()}% < 90%)")
            -1 to -1
        }

        onProgress?.invoke(1f, "Search done")

        return result
    }

    private fun buildNgrams(s: String, n: Int = 3): Set<String> {
        if (s.length < n) return emptySet()
        return s.windowed(n, 1).toSet()
    }

    private fun ngramSimilarity(baseNgrams: Set<String>, s2: String, n: Int = 3): Double {
        if (baseNgrams.isEmpty() || s2.isEmpty()) return 0.0
        if (s2.length < n) return 0.0

        val ngrams2 = s2.windowed(n, 1).toSet()
        if (ngrams2.isEmpty()) return 0.0

        val intersection = baseNgrams.intersect(ngrams2).size
        val union = baseNgrams.size + ngrams2.size - intersection

        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    private fun hasMultiplePerfectMatches(originalContent: String, oldContent: String): Boolean {
        val normalizedOld = oldContent.replace(Regex("\\s+"), "")
        if (normalizedOld.isEmpty()) return false

        val normalizedOriginal = originalContent.replace(Regex("\\s+"), "")
        var count = 0
        var index = normalizedOriginal.indexOf(normalizedOld)
        while (index >= 0) {
            count++
            if (count > 1) {
                return true
            }
            index = normalizedOriginal.indexOf(normalizedOld, index + normalizedOld.length)
        }
        return false
    }

    private fun String.trimTrailingNewline(): String = this.trimEnd('\n', '\r')
}