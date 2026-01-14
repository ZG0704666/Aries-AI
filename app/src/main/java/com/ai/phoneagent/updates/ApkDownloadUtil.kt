package com.ai.phoneagent.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.webkit.CookieManager
import com.ai.phoneagent.BuildConfig

object ApkDownloadUtil {

    private fun buildAuthHeader(token: String): String? {
        val t = token.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("github_pat_")) {
            "Bearer $t"
        } else {
            "token $t"
        }
    }

    fun enqueueDownloadUrl(
        context: Context,
        url: String,
        fileName: String,
        title: String,
        description: String,
        mimeType: String? = null,
    ) {
        val req = DownloadManager.Request(Uri.parse(url))
        req.setTitle(title)
        req.setDescription(description)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (!mimeType.isNullOrBlank()) {
            req.setMimeType(mimeType)
        }

        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        req.addRequestHeader("User-Agent", "PhoneAgent")

        // Operit 的 WebView 下载会把 Cookie 一并带上，部分镜像/跳转站点需要 Cookie 才能真正开始下载。
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        if (!cookie.isNullOrBlank()) {
            req.addRequestHeader("Cookie", cookie)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            Toast.makeText(context, "无法获取下载服务", Toast.LENGTH_SHORT).show()
            return
        }

        val appContext = context.applicationContext
        val downloadId =
            runCatching {
                dm.enqueue(req)
            }.getOrElse {
                Toast.makeText(appContext, "下载失败：${it.message}", Toast.LENGTH_LONG).show()
                return
            }

        Toast.makeText(appContext, "开始下载：$fileName", Toast.LENGTH_SHORT).show()

        // 一次性监听下载完成，若失败则给出 reason，便于定位“私有仓库/鉴权/镜像站中转页”等问题。
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != downloadId) return

                    runCatching {
                        appContext.unregisterReceiver(this)
                    }

                    val q = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(q)
                    cursor.use {
                        if (it == null || !it.moveToFirst()) return
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx < 0) return
                        val status = it.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_FAILED) {
                            val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIdx >= 0) it.getInt(reasonIdx) else -1
                            Toast.makeText(appContext, "下载失败（reason=$reason）", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

        runCatching {
            appContext.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    fun enqueueApkDownload(context: Context, entry: ReleaseEntry) {
        val resolvedUrl =
            if (BuildConfig.GITHUB_TOKEN.isNotBlank() && entry.apkAssetId != null) {
                "https://api.github.com/repos/${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}/releases/assets/${entry.apkAssetId}"
            } else {
                entry.apkUrl
            }

        if (resolvedUrl.isNullOrBlank()) {
            ReleaseUiUtil.openUrl(context, entry.releaseUrl)
            return
        }
        val fileName = "${UpdateConfig.REPO_NAME}-${entry.versionTag}.apk".replace("/", "_")

        // 私有仓库 + token：强烈建议走 assets API，并附带 Authorization + Accept。
        // 这里仍使用 DownloadManager，但把关键 header 保留。
        val req = DownloadManager.Request(Uri.parse(resolvedUrl))
        req.setTitle("下载 ${entry.versionTag}")
        req.setDescription(entry.title)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setMimeType("application/vnd.android.package-archive")
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        req.addRequestHeader("User-Agent", "PhoneAgent")
        buildAuthHeader(BuildConfig.GITHUB_TOKEN)?.let { req.addRequestHeader("Authorization", it) }
        if (BuildConfig.GITHUB_TOKEN.isNotBlank() && entry.apkAssetId != null) {
            req.addRequestHeader("Accept", "application/octet-stream")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            Toast.makeText(context, "无法获取下载服务", Toast.LENGTH_SHORT).show()
            return
        }

        val appContext = context.applicationContext
        val downloadId =
            runCatching {
                dm.enqueue(req)
            }.getOrElse {
                Toast.makeText(appContext, "下载失败：${it.message}", Toast.LENGTH_LONG).show()
                return
            }
        Toast.makeText(appContext, "开始下载：$fileName", Toast.LENGTH_SHORT).show()

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != downloadId) return

                    runCatching {
                        appContext.unregisterReceiver(this)
                    }

                    val q = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(q)
                    cursor.use {
                        if (it == null || !it.moveToFirst()) return
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx < 0) return
                        val status = it.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_FAILED) {
                            val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIdx >= 0) it.getInt(reasonIdx) else -1
                            Toast.makeText(appContext, "下载失败（reason=$reason）", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

        runCatching {
            appContext.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }
}
