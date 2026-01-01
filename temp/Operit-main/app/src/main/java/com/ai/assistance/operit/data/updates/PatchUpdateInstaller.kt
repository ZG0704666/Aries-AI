package com.ai.assistance.operit.data.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.math.min

object PatchUpdateInstaller {
    private const val TAG = "PatchUpdateInstaller"

    private const val DOWNLOAD_CHANNEL_ID = "PATCH_UPDATE_DOWNLOAD"
    private const val DOWNLOAD_CHANNEL_NAME = "Patch Update"
    private const val DOWNLOAD_NOTIFICATION_ID = 71001

    private const val INSTALL_CHANNEL_ID = "PATCH_UPDATE_INSTALL"
    private const val INSTALL_CHANNEL_NAME = "Patch Update Ready"
    private const val INSTALL_NOTIFICATION_ID = 71002

    const val ACTION_INSTALL_PATCH = "com.ai.assistance.operit.action.INSTALL_PATCH_APK"
    const val EXTRA_APK_PATH = "extra_apk_path"
    const val EXTRA_PATCH_VERSION = "extra_patch_version"

    private const val PATCH_OWNER = "AAswordman"
    private const val PATCH_REPO = "OperitNightlyRelease"

    private const val PROBE_TIMEOUT_MS = 2500

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class AutoPatchResult(
        val apkFile: File,
        val finalVersion: String
    )

    suspend fun autoPreparePatchAndNotifyInstallResult(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        newVersion: String
    ): AutoPatchResult = withContext(Dispatchers.IO) {
        val key = pickFastestMirrorKey(patchUrl = patchUrl, metaUrl = metaUrl)

        AppLogger.d(TAG, "autoPreparePatchAndNotifyInstallResult(): targetVersion=$newVersion mirror=$key")
        val result = downloadAndPreparePatchUpdateAutoResult(context, mirrorKey = key)
        val shownVersion = result.finalVersion.ifBlank { newVersion }
        notifyInstallReady(context, result.apkFile, shownVersion)
        AutoPatchResult(apkFile = result.apkFile, finalVersion = shownVersion)
    }

    suspend fun autoPreparePatchAndStartInstallResult(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        newVersion: String
    ): AutoPatchResult = withContext(Dispatchers.IO) {
        val key = pickFastestMirrorKey(patchUrl = patchUrl, metaUrl = metaUrl)

        AppLogger.d(TAG, "autoPreparePatchAndStartInstallResult(): targetVersion=$newVersion mirror=$key")
        val result = downloadAndPreparePatchUpdateAutoResult(context, mirrorKey = key)
        val shownVersion = result.finalVersion.ifBlank { newVersion }

        try {
            withContext(Dispatchers.Main) {
                installApk(context, result.apkFile)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "auto start install failed", e)
        }

        notifyInstallReady(context, result.apkFile, shownVersion)
        AutoPatchResult(apkFile = result.apkFile, finalVersion = shownVersion)
    }

    suspend fun downloadAndPreparePatchUpdate(
        context: Context,
        patchUrl: String,
        metaUrl: String
    ): File = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "patch_update").apply { mkdirs() }
        val metaFile = File(workDir, "patch_meta.json")
        val patchFile = File(workDir, "patch.zip")
        val outApk = File(workDir, "rebuilt.apk")

        downloadToFile(context, metaUrl, metaFile, title = "Downloading patch meta")
        val metaJson = JSONObject(metaFile.readText())

        val format = metaJson.optString("format", "")
        if (format != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $format")
        }

        val baseApk = File(context.applicationInfo.sourceDir)
        val baseShaExpected = metaJson.optString("baseSha256", "")
        if (baseShaExpected.isNotBlank()) {
            val baseShaActual = sha256Hex(baseApk)
            if (!baseShaActual.equals(baseShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Base sha256 mismatch")
            }
        }

        downloadToFile(context, patchUrl, patchFile, title = "Downloading patch")

        val patchShaExpected = metaJson.optString("patchSha256", "")
        if (patchShaExpected.isNotBlank()) {
            val patchShaActual = sha256Hex(patchFile)
            if (!patchShaActual.equals(patchShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Patch sha256 mismatch")
            }
        }

        applyApkrawPatch(baseApk, patchFile, metaJson, outApk)

        val targetShaExpected = metaJson.optString("targetSha256", "")
        if (targetShaExpected.isNotBlank()) {
            val targetShaActual = sha256Hex(outApk)
            if (!targetShaActual.equals(targetShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Target sha256 mismatch")
            }
        }

        outApk
    }

    suspend fun autoPreparePatchAndNotifyInstall(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        newVersion: String
    ): File = withContext(Dispatchers.IO) {
        autoPreparePatchAndNotifyInstallResult(
            context = context,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            newVersion = newVersion
        ).apkFile
    }

    suspend fun downloadAndPreparePatchUpdateAuto(
        context: Context,
        mirrorKey: String
    ): File {
        return downloadAndPreparePatchUpdateAutoResult(context, mirrorKey).apkFile
    }

    suspend fun downloadAndPreparePatchUpdateAutoResult(
        context: Context,
        mirrorKey: String
    ): AutoPatchResult = withContext(Dispatchers.IO) {
        val api = GitHubApiService(context)
        val candidates = mutableListOf<PatchCandidate>()

        val installedVersionName = getInstalledVersionName(context)
        val installedBase = baseVersionOf(installedVersionName)
        val hasInstalledVersion = installedVersionName.isNotBlank() && installedBase.isNotBlank()

        for (page in 1..2) {
            val res = api.getRepositoryReleases(owner = PATCH_OWNER, repo = PATCH_REPO, page = page, perPage = 100)
            val releases = res.getOrNull() ?: break
            if (releases.isEmpty()) break

            for (r in releases) {
                if (r.draft) continue
                val tag = r.tag_name
                val version = tag.removePrefix("v")

                if (hasInstalledVersion) {
                    val base = baseVersionOf(version)
                    if (base != installedBase) {
                        continue
                    }
                    if (UpdateManager.compareVersions(version, installedVersionName) <= 0) {
                        continue
                    }
                }

                val summary = r.body?.let { body -> runCatching { JSONObject(body) }.getOrNull() }

                val summaryFormat = summary?.optString("format", "") ?: ""
                val baseShaFromBody = summary?.optString("baseSha256", "") ?: ""
                val targetShaFromBody = summary?.optString("targetSha256", "") ?: ""
                val patchShaFromBody = summary?.optString("patchSha256", "") ?: ""
                val metaFileName = summary?.optString("metaFile", "")?.takeIf { it.isNotBlank() }
                val patchFileName = summary?.optString("patchFile", "")?.takeIf { it.isNotBlank() }

                val metaAsset =
                    metaFileName?.let { n -> r.assets.firstOrNull { it.name == n } }
                        ?: r.assets.firstOrNull { it.name.startsWith("patch_") && it.name.endsWith(".json") }
                        ?: r.assets.firstOrNull { it.name.endsWith(".json") }
                val patchAsset =
                    patchFileName?.let { n -> r.assets.firstOrNull { it.name == n } }
                        ?: r.assets.firstOrNull { it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip") }
                        ?: r.assets.firstOrNull { it.name.endsWith(".zip") }

                if (metaAsset == null || patchAsset == null) continue

                val baseSha = if (summaryFormat == "apkraw-1") baseShaFromBody else ""
                val targetSha = if (summaryFormat == "apkraw-1") targetShaFromBody else ""
                val patchSha = if (summaryFormat == "apkraw-1") patchShaFromBody else ""

                candidates.add(
                    PatchCandidate(
                        version = version,
                        tag = tag,
                        metaUrl = metaAsset.browser_download_url,
                        patchUrl = patchAsset.browser_download_url,
                        baseSha256 = baseSha,
                        targetSha256 = targetSha,
                        patchSha256 = patchSha
                    )
                )
            }
        }

        val workDir = File(context.cacheDir, "patch_update").apply { mkdirs() }
        val workApk = File(workDir, "_work.apk")
        val tmpApk = File(workDir, "_work.tmp.apk")

        val installedApk = File(context.applicationInfo.sourceDir)
        if (workApk.exists()) workApk.delete()
        installedApk.inputStream().use { ins ->
            FileOutputStream(workApk).use { out ->
                ins.copyTo(out)
            }
        }

        var currentSha = sha256Hex(workApk)
        var applied = 0
        var lastAppliedVersion = ""

        val metaCache = mutableMapOf<String, JSONObject>()
        fun getMeta(candidate: PatchCandidate): JSONObject {
            return metaCache.getOrPut(candidate.tag) {
                val metaFile = File(workDir, "meta_${candidate.tag}.json")
                if (!metaFile.exists() || metaFile.length() == 0L) {
                    downloadToFile(
                        context,
                        selectUrl(candidate.metaUrl, mirrorKey),
                        metaFile,
                        title = "Downloading patch meta"
                    )
                }
                JSONObject(metaFile.readText())
            }
        }

        while (true) {
            val applicableByBodySha = candidates
                .asSequence()
                .filter { it.baseSha256.isNotBlank() }
                .filter { it.baseSha256.equals(currentSha, ignoreCase = true) }
                .toList()

            val chosenAndMeta: Pair<PatchCandidate, JSONObject>? =
                if (applicableByBodySha.isNotEmpty()) {
                    val sorted = applicableByBodySha.sortedWith { a, b ->
                        UpdateManager.compareVersions(b.version, a.version)
                    }
                    var found: Pair<PatchCandidate, JSONObject>? = null
                    for (c in sorted) {
                        val m = runCatching { getMeta(c) }.getOrNull() ?: continue
                        found = c to m
                        break
                    }
                    found
                } else {
                    val unknown = candidates.filter { it.baseSha256.isBlank() }
                    val applicable = mutableListOf<Pair<PatchCandidate, JSONObject>>()
                    for (c in unknown) {
                        val m = runCatching { getMeta(c) }.getOrNull() ?: continue
                        val format = m.optString("format", "")
                        if (format != "apkraw-1") continue
                        val baseSha = m.optString("baseSha256", "")
                        if (baseSha.equals(currentSha, ignoreCase = true)) {
                            applicable.add(c to m)
                        }
                    }
                    applicable.maxWithOrNull { a, b ->
                        UpdateManager.compareVersions(a.first.version, b.first.version)
                    }
                }

            val chosen = chosenAndMeta?.first ?: break
            val metaJson = chosenAndMeta.second

            val patchFile = File(workDir, "patch_${applied}.zip")

            val format = metaJson.optString("format", "")
            if (format != "apkraw-1") {
                throw IllegalStateException("Unsupported patch format: $format")
            }
            val patchShaExpected = metaJson.optString("patchSha256", "")
            val baseShaExpected = metaJson.optString("baseSha256", "")
            val targetShaExpected = metaJson.optString("targetSha256", "")
            val patchFileName = metaJson.optString("patchFile", "")

            if (baseShaExpected.isNotBlank() && !baseShaExpected.equals(currentSha, ignoreCase = true)) {
                break
            }

            downloadToFile(
                context,
                selectUrl(chosen.patchUrl, mirrorKey),
                patchFile,
                title = "Downloading patch (${applied + 1})"
            )
            if (patchShaExpected.isNotBlank()) {
                val patchShaActual = sha256Hex(patchFile)
                if (!patchShaActual.equals(patchShaExpected, ignoreCase = true)) {
                    throw IllegalStateException("Patch sha256 mismatch")
                }
            }

            if (tmpApk.exists()) tmpApk.delete()
            applyApkrawPatch(workApk, patchFile, metaJson, tmpApk)
            val newSha = sha256Hex(tmpApk)
            if (targetShaExpected.isNotBlank() && !newSha.equals(targetShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Target sha256 mismatch")
            }

            // Promote
            if (workApk.exists()) workApk.delete()
            tmpApk.renameTo(workApk)
            currentSha = newSha
            applied += 1
            lastAppliedVersion = chosen.version
            if (applied >= 20) break

            AppLogger.d(TAG, "applied patch ${chosen.tag} -> $currentSha (patchFile=$patchFileName)")
        }

        if (applied == 0) {
            throw IllegalStateException("No applicable patch found for current APK")
        }

        val outApk = File(workDir, "rebuilt.apk")
        if (outApk.exists()) outApk.delete()
        workApk.renameTo(outApk)
        AutoPatchResult(
            apkFile = outApk,
            finalVersion = lastAppliedVersion
        )
    }

    private fun selectUrl(originalUrl: String, mirrorKey: String): String {
        if (mirrorKey == "GitHub") return originalUrl
        val mirrors = GithubReleaseUtil.getMirroredUrls(originalUrl)
        return mirrors[mirrorKey] ?: originalUrl
    }

    private fun selectMirroredUrl(originalUrl: String, mirrorKey: String): String {
        return selectUrl(originalUrl, mirrorKey)
    }

    private suspend fun pickFastestMirrorKey(patchUrl: String, metaUrl: String): String {
        val patchMirrors = GithubReleaseUtil.getMirroredUrls(patchUrl)
        val metaMirrors = GithubReleaseUtil.getMirroredUrls(metaUrl)

        val keys = LinkedHashSet<String>().apply {
            addAll(patchMirrors.keys)
            addAll(metaMirrors.keys)
            add("GitHub")
        }

        val patchProbeUrls = buildMap {
            putAll(patchMirrors)
            put("GitHub", patchUrl)
        }
        val metaProbeUrls = buildMap {
            putAll(metaMirrors)
            put("GitHub", metaUrl)
        }

        val patchRes = GithubReleaseUtil.probeMirrorUrls(patchProbeUrls, timeoutMs = PROBE_TIMEOUT_MS)
        val metaRes = GithubReleaseUtil.probeMirrorUrls(metaProbeUrls, timeoutMs = PROBE_TIMEOUT_MS)

        var bestKey = "GitHub"
        var bestLatency = Long.MAX_VALUE

        for (k in keys) {
            val p = patchRes[k] ?: continue
            val m = metaRes[k] ?: continue
            if (!p.ok || !m.ok) continue
            val latency = when {
                p.latencyMs != null && m.latencyMs != null -> maxOf(p.latencyMs, m.latencyMs)
                p.latencyMs != null -> p.latencyMs
                m.latencyMs != null -> m.latencyMs
                else -> null
            } ?: continue

            if (latency < bestLatency) {
                bestLatency = latency
                bestKey = k
            }
        }

        return bestKey
    }

    private data class PatchCandidate(
        val version: String,
        val tag: String,
        val metaUrl: String,
        val patchUrl: String,
        val baseSha256: String,
        val targetSha256: String,
        val patchSha256: String
    )

    private fun baseVersionOf(version: String): String {
        if (version.isBlank()) return ""
        return version.substringBefore('+')
    }

    private fun getInstalledVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(intent)
    }

    private fun ensureDownloadChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID)
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        DOWNLOAD_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun ensureInstallChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(INSTALL_CHANNEL_ID)
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        INSTALL_CHANNEL_ID,
                        INSTALL_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }

    private fun notifyInstallReady(context: Context, apkFile: File, version: String) {
        try {
            ensureInstallChannel(context)

            val intent = Intent(ACTION_INSTALL_PATCH).apply {
                setClass(context, PatchInstallReceiver::class.java)
                putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
                putExtra(EXTRA_PATCH_VERSION, version)
            }

            val pi = PendingIntent.getBroadcast(
                context,
                INSTALL_NOTIFICATION_ID,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val builder = NotificationCompat.Builder(context, INSTALL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Patch ready")
                .setContentText("$version")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            NotificationManagerCompat.from(context)
                .notify(INSTALL_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun notifyDownloadProgress(
        context: Context,
        title: String,
        text: String,
        progress: Int?,
        indeterminate: Boolean
    ) {
        try {
            ensureDownloadChannel(context)
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (indeterminate) {
                builder.setProgress(100, 0, true)
            } else if (progress != null) {
                builder.setProgress(100, progress, false)
            }

            NotificationManagerCompat.from(context)
                .notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun notifyDownloadDone(context: Context, title: String) {
        try {
            ensureDownloadChannel(context)
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText("Done")
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            NotificationManagerCompat.from(context)
                .notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun notifyDownloadFailed(context: Context, title: String, message: String) {
        try {
            ensureDownloadChannel(context)
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            NotificationManagerCompat.from(context)
                .notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun downloadToFile(context: Context, url: String, out: File, title: String) {
        val req = Request.Builder().url(url).build()
        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
                }
                val body = resp.body ?: throw IllegalStateException("Empty response body")
                val total = body.contentLength().takeIf { it > 0 }
                out.parentFile?.mkdirs()

                val buf = ByteArray(128 * 1024)
                var readTotal = 0L
                var lastProgress = -1
                var lastNotifyAt = 0L

                notifyDownloadProgress(
                    context = context,
                    title = title,
                    text = if (total != null) "0%" else "Downloading...",
                    progress = 0,
                    indeterminate = total == null
                )

                FileOutputStream(out).use { fos ->
                    body.byteStream().use { ins ->
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            readTotal += n.toLong()

                            val now = System.currentTimeMillis()
                            if (total != null) {
                                val p = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                                if (p != lastProgress && (now - lastNotifyAt >= 300)) {
                                    lastProgress = p
                                    lastNotifyAt = now
                                    notifyDownloadProgress(
                                        context = context,
                                        title = title,
                                        text = "$p%",
                                        progress = p,
                                        indeterminate = false
                                    )
                                }
                            } else {
                                if (now - lastNotifyAt >= 800) {
                                    lastNotifyAt = now
                                    notifyDownloadProgress(
                                        context = context,
                                        title = title,
                                        text = "Downloading...",
                                        progress = null,
                                        indeterminate = true
                                    )
                                }
                            }
                        }
                    }
                }

                notifyDownloadDone(context, title)
            }
        } catch (e: Exception) {
            notifyDownloadFailed(context, title, e.message ?: "download failed")
            throw e
        }
    }

    private fun applyApkrawPatch(baseApk: File, patchZip: File, meta: JSONObject, outApk: File) {
        val entriesJson = meta.optJSONArray("apkRawEntries") ?: JSONArray()
        val tailName = meta.optString("apkRawTailFile", "tail.bin")

        val baseMap = RandomAccessFile(baseApk, "r").use { raf ->
            readCentralDirectory(raf)
        }

        if (outApk.exists()) outApk.delete()
        outApk.parentFile?.mkdirs()

        val md = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(BufferedOutputStream(FileOutputStream(outApk)), md).use { dout ->
            RandomAccessFile(baseApk, "r").use { raf ->
                ZipFile(patchZip).use { pz ->
                    for (i in 0 until entriesJson.length()) {
                        val ent = entriesJson.getJSONObject(i)
                        val name = ent.optString("name", "")
                        val mode = ent.optString("mode", "")
                        if (name.isBlank()) throw IllegalStateException("Bad apkRawEntries")

                        if (mode == "copy") {
                            val cd = baseMap[name] ?: throw IllegalStateException("Base apk missing entry: $name")
                            copyLocalRecord(raf, cd, dout)
                        } else if (mode == "add") {
                            val recordPath = ent.optString("recordPath", "")
                            if (recordPath.isBlank()) throw IllegalStateException("apkraw add missing recordPath")
                            val ze = pz.getEntry(recordPath) ?: throw IllegalStateException("patch zip missing $recordPath")
                            pz.getInputStream(ze).use { ins ->
                                ins.copyTo(dout)
                            }
                        } else {
                            throw IllegalStateException("Bad apkraw mode: $mode")
                        }
                    }

                    val tailEntry = pz.getEntry(tailName) ?: throw IllegalStateException("patch zip missing $tailName")
                    pz.getInputStream(tailEntry).use { ins ->
                        ins.copyTo(dout)
                    }
                }
            }
            dout.flush()
        }
    }

    private data class CdEntry(
        val localHeaderOffset: Long,
        val compressedSize: Long
    )

    private fun readCentralDirectory(raf: RandomAccessFile): Map<String, CdEntry> {
        val fileLen = raf.length()
        val readLen = min(65557L, fileLen).toInt()
        val buf = ByteArray(readLen)
        raf.seek(fileLen - readLen)
        raf.readFully(buf)

        var eocdIndex = -1
        for (i in readLen - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() &&
                buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() &&
                buf[i + 3] == 0x06.toByte()
            ) {
                eocdIndex = i
                break
            }
        }
        if (eocdIndex < 0) throw IllegalStateException("EOCD not found")

        val eocdOffset = (fileLen - readLen + eocdIndex).toLong()
        raf.seek(eocdOffset)
        val eocd = ByteArray(22)
        raf.readFully(eocd)

        val totalEntries = readUShortLE(eocd, 10)
        val cdOffset = readUIntLE(eocd, 16)

        raf.seek(cdOffset)
        val out = LinkedHashMap<String, CdEntry>(totalEntries)
        repeat(totalEntries) {
            val hdr = ByteArray(46)
            raf.readFully(hdr)
            val sig = readUIntLE(hdr, 0)
            if (sig != 0x02014b50L) throw IllegalStateException("Bad central directory signature")

            val flags = readUShortLE(hdr, 8)
            val compressedSize = readUIntLE(hdr, 20)
            val fileNameLen = readUShortLE(hdr, 28)
            val extraLen = readUShortLE(hdr, 30)
            val commentLen = readUShortLE(hdr, 32)
            val localHeaderOffset = readUIntLE(hdr, 42)

            val nameBytes = ByteArray(fileNameLen)
            raf.readFully(nameBytes)
            val charset = if ((flags and 0x800) != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
            val name = String(nameBytes, charset)

            if (extraLen > 0) raf.skipBytes(extraLen)
            if (commentLen > 0) raf.skipBytes(commentLen)

            if (!name.endsWith("/")) {
                out[name] = CdEntry(localHeaderOffset = localHeaderOffset, compressedSize = compressedSize)
            }
        }

        return out
    }

    private fun copyLocalRecord(raf: RandomAccessFile, cd: CdEntry, out: java.io.OutputStream) {
        raf.seek(cd.localHeaderOffset)

        val lfh = ByteArray(30)
        raf.readFully(lfh)
        val sig = readUIntLE(lfh, 0)
        if (sig != 0x04034b50L) throw IllegalStateException("Bad local header signature")

        val flags = readUShortLE(lfh, 6)
        val fileNameLen = readUShortLE(lfh, 26)
        val extraLen = readUShortLE(lfh, 28)

        out.write(lfh)

        if (fileNameLen + extraLen > 0) {
            val nameExtra = ByteArray(fileNameLen + extraLen)
            raf.readFully(nameExtra)
            out.write(nameExtra)
        }

        copyBytes(raf, out, cd.compressedSize)

        if ((flags and 0x08) != 0) {
            val first4 = ByteArray(4)
            raf.readFully(first4)
            out.write(first4)
            val ddSig = readUIntLE(first4, 0)
            if (ddSig == 0x08074b50L) {
                val rest = ByteArray(12)
                raf.readFully(rest)
                out.write(rest)
            } else {
                val rest = ByteArray(8)
                raf.readFully(rest)
                out.write(rest)
            }
        }
    }

    private fun copyBytes(raf: RandomAccessFile, out: java.io.OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(1024 * 1024)
        while (remaining > 0) {
            val toRead = min(remaining, buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) throw IllegalStateException("Unexpected EOF")
            out.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun readUShortLE(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xff) or ((buf[off + 1].toInt() and 0xff) shl 8)
    }

    private fun readUIntLE(buf: ByteArray, off: Int): Long {
        return (buf[off].toLong() and 0xff) or
            ((buf[off + 1].toLong() and 0xff) shl 8) or
            ((buf[off + 2].toLong() and 0xff) shl 16) or
            ((buf[off + 3].toLong() and 0xff) shl 24)
    }
}
