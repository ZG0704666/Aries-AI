package com.ai.assistance.operit.core.tools.skill

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class SkillManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SkillManager"

        @Volatile private var INSTANCE: SkillManager? = null

        fun getInstance(context: Context): SkillManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: SkillManager(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    private val availableSkills = mutableMapOf<String, SkillPackage>()

    private fun getSkillsRootDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, "Operit")
        val skillsDir = File(operitDir, "skills")
        if (!skillsDir.exists()) {
            skillsDir.mkdirs()
        }
        return skillsDir
    }

    fun getSkillsDirectoryPath(): String {
        return getSkillsRootDir().absolutePath
    }

    fun refreshAvailableSkills() {
        availableSkills.clear()

        val skillsDir = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            return
        }

        if (!skillsDir.exists() || !skillsDir.isDirectory) {
            return
        }

        val children = skillsDir.listFiles() ?: emptyArray()
        for (child in children) {
            if (!child.isDirectory) continue

            val skillFile = File(child, "SKILL.md").let { primary ->
                if (primary.exists()) primary else File(child, "skill.md")
            }

            if (!skillFile.exists() || !skillFile.isFile) continue

            try {
                val (name, description) = parseSkillMetadata(skillFile)
                val skillName = name.ifBlank { child.name }
                val skillDesc = description.ifBlank { "" }

                if (availableSkills.containsKey(skillName)) continue

                availableSkills[skillName] = SkillPackage(
                    name = skillName,
                    description = skillDesc,
                    directory = child,
                    skillFile = skillFile
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error loading skill from ${skillFile.absolutePath}", e)
            }
        }
    }

    private fun parseSkillMetadata(skillFile: File): Pair<String, String> {
        val lines = skillFile.bufferedReader().use { it.readLines() }

        var name = ""
        var description = ""

        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIndex >= 0) {
                val frontmatter = lines.subList(1, endIndex + 1)
                frontmatter.forEach { lineRaw ->
                    val line = lineRaw.trim()
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim()
                    val value = unquote(line.substring(idx + 1).trim())
                    when (key.lowercase()) {
                        "name" -> if (name.isBlank()) name = value
                        "description" -> if (description.isBlank()) description = value
                    }
                }
            }
        }

        if (name.isBlank() || description.isBlank()) {
            lines.take(40).forEach { lineRaw ->
                val line = lineRaw.trim()
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = unquote(line.substring(idx + 1).trim())
                when (key.lowercase()) {
                    "name" -> if (name.isBlank()) name = value
                    "description" -> if (description.isBlank()) description = value
                }
            }
        }

        return Pair(name, description)
    }

    private fun unquote(valueRaw: String): String {
        var value = valueRaw
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
            if (value.length >= 2) value = value.substring(1, value.length - 1)
        }
        return value
    }

    fun getAvailableSkills(): Map<String, SkillPackage> {
        refreshAvailableSkills()
        return availableSkills.toMap()
    }

    fun readSkillContent(skillName: String): String? {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return null
        return try {
            skill.skillFile.readText()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read SKILL.md for $skillName", e)
            null
        }
    }

    fun deleteSkill(skillName: String): Boolean {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return false
        return try {
            val ok = skill.directory.deleteRecursively()
            if (ok) {
                availableSkills.remove(skillName)
            }
            ok
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete skill $skillName", e)
            false
        }
    }

    fun getSkillSystemPrompt(skillName: String): String? {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return null
        val content = try {
            skill.skillFile.readText()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read skill content: ${skill.skillFile.absolutePath}", e)
            ""
        }

        val sb = StringBuilder()
        sb.appendLine("Using package (Skill): ${skill.name}")
        sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
        if (skill.description.isNotBlank()) {
            sb.appendLine("Description: ${skill.description}")
        }
        sb.appendLine("SKILL.md path: ${skill.skillFile.absolutePath}")
        sb.appendLine("Skill directory: ${skill.directory.absolutePath}")
        sb.appendLine("Subdirectories:")
        sb.appendLine(buildSubdirectoryTreeText(skill.directory))
        sb.appendLine()
        sb.appendLine("SKILL.md:")
        sb.appendLine(content)

        return sb.toString()
    }

    private fun buildSubdirectoryTreeText(
        rootDir: File,
        maxDepth: Int = 3,
        maxEntries: Int = 120
    ): String {
        val sb = StringBuilder()
        var count = 0
        var truncated = false

        fun walk(dir: File, depth: Int, indent: String) {
            if (depth > maxDepth || count >= maxEntries) {
                if (count >= maxEntries) truncated = true
                return
            }

            val children = dir.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                ?: emptyList()

            for (child in children) {
                if (count >= maxEntries) {
                    truncated = true
                    break
                }
                count++
                sb.append(indent)
                sb.append("- ")
                sb.append(child.name)
                sb.appendLine("/")
                walk(child, depth + 1, indent + "  ")
            }
        }

        walk(rootDir, depth = 1, indent = "")

        if (count == 0) {
            return "(no subdirectories)"
        }
        if (truncated) {
            sb.appendLine("... (truncated)")
        }
        return sb.toString().trimEnd()
    }

    fun importSkillFromZip(zipFile: File): String {
        if (!zipFile.exists() || !zipFile.canRead()) {
            return "无法读取文件: ${zipFile.absolutePath}"
        }
        if (!zipFile.name.endsWith(".zip", ignoreCase = true)) {
            return "仅支持 .zip 文件"
        }

        val skillsRoot = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            return "无法访问 skills 目录: ${e.message}"
        }

        val tmpDir = File(skillsRoot, ".import_tmp_${System.currentTimeMillis()}")
        if (!tmpDir.mkdirs()) {
            return "创建临时目录失败: ${tmpDir.absolutePath}"
        }

        fun cleanupTmp() {
            try {
                tmpDir.deleteRecursively()
            } catch (_: Exception) {
            }
        }

        try {
            unzipToDirectory(zipFile, tmpDir)

            val skillMdCandidates = tmpDir.walkTopDown()
                .filter { it.isFile && (it.name.equals("SKILL.md", ignoreCase = true) || it.name.equals("skill.md", ignoreCase = true)) }
                .take(10)
                .toList()

            if (skillMdCandidates.isEmpty()) {
                cleanupTmp()
                return "导入失败：zip 内未找到 SKILL.md"
            }

            val selectedSkillFile = skillMdCandidates.first()
            val selectedSkillDir = selectedSkillFile.parentFile ?: run {
                cleanupTmp()
                return "导入失败：SKILL.md 路径异常"
            }

            val (metaName, metaDesc) = parseSkillMetadata(selectedSkillFile)
            val baseName = metaName.ifBlank {
                selectedSkillDir.name.ifBlank { zipFile.nameWithoutExtension }
            }
            val finalDir = File(skillsRoot, baseName.trim().ifBlank { "skill" })

            if (finalDir.exists()) {
                cleanupTmp()
                return "导入失败：已存在同名 Skill：${finalDir.name}"
            }

            // Copy the detected skill directory to final location
            selectedSkillDir.copyRecursively(finalDir, overwrite = false)
            cleanupTmp()

            // refresh cache
            refreshAvailableSkills()

            val desc = metaDesc.ifBlank { "" }
            return if (desc.isNotBlank()) {
                "已导入 Skill: ${finalDir.name}（$desc）"
            } else {
                "已导入 Skill: ${finalDir.name}"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import skill from zip", e)
            cleanupTmp()
            return "导入失败: ${e.message}"
        }
    }


    private fun unzipToDirectory(zipFile: File, destinationDir: File) {
        val destCanonical = destinationDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break

                val outFile = File(destinationDir, entry.name)
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                    zis.closeEntry()
                    throw IllegalArgumentException("Zip entry is outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                    zis.closeEntry()
                    continue
                }

                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    while (true) {
                        val read = zis.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                    }
                }
                zis.closeEntry()
            }
        }
    }
}
