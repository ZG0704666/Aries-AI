package com.ai.phoneagent.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.ai.phoneagent.VirtualDisplayConfig
import com.ai.phoneagent.data.preferences.AutomationResultsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FeedbackLogExporter {
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phoneRegex = Regex("""(?<!\d)(?:\+?86[- ]?)?1[3-9]\d{9}(?!\d)""")
    private val bearerRegex = Regex("""(?i)(bearer\s+)([A-Za-z0-9._\-+/=]+)""")
    private val headerSecretRegex = Regex(
        """(?im)\b(authorization|cookie|set-cookie|x-api-key|api-key|token|access-token|refresh-token|password|secret)\b\s*[:=]\s*([^\r\n]+)""",
    )
    private val querySecretRegex = Regex(
        """(?i)([?&](?:token|access_token|refresh_token|api_key|apikey|secret|password)=)([^&\s]+)""",
    )
    private val jsonSecretRegex = Regex(
        """(?i)("?(?:apiKey|api_key|token|accessToken|access_token|refreshToken|refresh_token|password|secret|authorization|cookie)"?\s*[:=]\s*"?)([^",\s}]+)""",
    )

    suspend fun exportSanitizedBundle(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "feedback_exports").apply { mkdirs() }
            pruneOldBundles(exportDir)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val bundleFile = File(exportDir, "aries_feedback_$timestamp.zip")
            val lastResult = AutomationResultsRepository(context).getLastResultBlocking()
            val metadata = buildMetadata(context, lastResult)
            val lastResultText = formatLastResult(lastResult)
            val processLogcat = collectCurrentProcessLogcat()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(bundleFile))).use { zip ->
                writeEntry(zip, "metadata.txt", redactSensitiveText(metadata))
                writeEntry(zip, "automation_last_result.txt", redactSensitiveText(lastResultText))
                writeEntry(
                    zip,
                    "logcat_current_process.txt",
                    redactSensitiveText(processLogcat.ifBlank { "未采集到当前进程日志。" }),
                )
            }

            bundleFile
        }
    }

    fun shareBundle(
        context: Context,
        file: File,
        chooserTitle: String,
        subject: String,
    ): Result<Unit> = runCatching {
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun buildMetadata(
        context: Context,
        lastResult: AutomationResultsRepository.LastResult,
    ): String {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val lastRunTime =
            if (lastResult.time > 0L) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastResult.time))
            } else {
                "无"
            }
        return buildString {
            appendLine("exported_at=$exportTime")
            appendLine("package_name=${context.packageName}")
            appendLine("app_version=${packageInfo.versionName.orEmpty()}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("background_execution=${VirtualDisplayConfig.getUseVirtualDisplay(context)}")
            appendLine("shizuku_interaction=${VirtualDisplayConfig.getUseShizukuInteraction(context)}")
            appendLine("auto_approve=${VirtualDisplayConfig.getAutoApproveAutomation(context)}")
            appendLine("last_result_time=$lastRunTime")
            appendLine("redaction=email,phone,token,api_key,cookie,authorization,password")
        }
    }

    private fun formatLastResult(lastResult: AutomationResultsRepository.LastResult): String {
        if (lastResult.time <= 0L) {
            return "暂无最近一次自动化结果。"
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastResult.time))
        return buildString {
            appendLine("time=$time")
            appendLine("success=${lastResult.success}")
            appendLine("steps=${lastResult.steps}")
            appendLine("message=${lastResult.message}")
            appendLine()
            appendLine("log=")
            append(lastResult.log)
        }
    }

    private fun collectCurrentProcessLogcat(): String {
        val processId = Process.myPid().toString()
        return runCatching {
            ProcessBuilder(
                "logcat",
                "-d",
                "-v",
                "threadtime",
                "--pid=$processId",
            ).redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrElse { error ->
            "采集当前进程 logcat 失败: ${error.message.orEmpty()}"
        }
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        text: String,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun pruneOldBundles(exportDir: File) {
        exportDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { it.delete() }
    }

    private fun redactSensitiveText(raw: String): String {
        var sanitized = raw
        sanitized = bearerRegex.replace(sanitized) { match ->
            match.groupValues[1] + maskSecret(match.groupValues[2])
        }
        sanitized = headerSecretRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}: ${maskSecret(match.groupValues[2].trim())}"
        }
        sanitized = querySecretRegex.replace(sanitized) { match ->
            match.groupValues[1] + maskSecret(match.groupValues[2])
        }
        sanitized = jsonSecretRegex.replace(sanitized) { match ->
            match.groupValues[1] + maskSecret(match.groupValues[2])
        }
        sanitized = emailRegex.replace(sanitized) { match ->
            maskEmail(match.value)
        }
        sanitized = phoneRegex.replace(sanitized) { match ->
            maskPhone(match.value)
        }
        return sanitized
    }

    private fun maskSecret(value: String): String {
        val trimmed = value.trim().trim('"')
        if (trimmed.isBlank()) return "***"
        return if (trimmed.length <= 6) {
            "***"
        } else {
            trimmed.take(3) + "***" + trimmed.takeLast(2)
        }
    }

    private fun maskEmail(value: String): String {
        val parts = value.split('@')
        if (parts.size != 2) return "***"
        val name = parts[0]
        val domain = parts[1]
        val maskedName = if (name.length <= 2) "**" else name.take(1) + "***" + name.takeLast(1)
        return "$maskedName@$domain"
    }

    private fun maskPhone(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.length < 7) return "***"
        return digits.take(3) + "****" + digits.takeLast(4)
    }
}