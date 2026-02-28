package com.ai.phoneagent.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.widget.Toast
import com.ai.phoneagent.BuildConfig
import com.ai.phoneagent.R

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
    ): Boolean {
        val req = DownloadManager.Request(Uri.parse(url))
        req.setTitle(title)
        req.setDescription(description)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (!mimeType.isNullOrBlank()) {
            req.setMimeType(mimeType)
        }

        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        req.addRequestHeader("User-Agent", "PhoneAgent")

        // 在部分镜像站中，携带 Cookie 能显著提升 DownloadManager 拉起成功率。
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        if (!cookie.isNullOrBlank()) {
            req.addRequestHeader("Cookie", cookie)
        }

        return enqueueRequest(context, req, fileName)
    }

    fun enqueueApkDownload(context: Context, entry: ReleaseEntry): Boolean {
        val resolvedUrl =
            if (BuildConfig.GITHUB_TOKEN.isNotBlank() && entry.apkAssetId != null) {
                "https://api.github.com/repos/${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}/releases/assets/${entry.apkAssetId}"
            } else {
                entry.apkUrl
            }

        if (resolvedUrl.isNullOrBlank()) {
            val opened = ReleaseUiUtil.openUrl(context, entry.releaseUrl)
            if (!opened) {
                Toast.makeText(context, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
            return opened
        }

        val fileName = "${UpdateConfig.REPO_NAME}-${entry.versionTag}.apk".replace("/", "_")
        val req = DownloadManager.Request(Uri.parse(resolvedUrl))
        req.setTitle(context.getString(R.string.update_download_title_format, entry.versionTag))
        req.setDescription(entry.title)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setMimeType("application/vnd.android.package-archive")
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        req.addRequestHeader("User-Agent", "PhoneAgent")
        buildAuthHeader(BuildConfig.GITHUB_TOKEN)?.let { req.addRequestHeader("Authorization", it) }
        if (BuildConfig.GITHUB_TOKEN.isNotBlank() && entry.apkAssetId != null) {
            req.addRequestHeader("Accept", "application/octet-stream")
        }

        return enqueueRequest(context, req, fileName)
    }

    private fun enqueueRequest(
        context: Context,
        req: DownloadManager.Request,
        fileName: String,
    ): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            Toast.makeText(context, R.string.update_download_service_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }

        val appContext = context.applicationContext
        val downloadId =
            runCatching {
                dm.enqueue(req)
            }.getOrElse {
                val reason = it.message?.trim().orEmpty().ifBlank {
                    context.getString(R.string.update_download_failed_unknown)
                }
                Toast.makeText(
                    appContext,
                    context.getString(R.string.update_download_enqueue_failed_format, reason),
                    Toast.LENGTH_LONG,
                ).show()
                return false
            }

        Toast.makeText(
            appContext,
            context.getString(R.string.update_download_started_format, fileName),
            Toast.LENGTH_SHORT,
        ).show()

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
                            Toast.makeText(
                                appContext,
                                context.getString(R.string.update_download_failed_reason_format, reason),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }

        runCatching {
            appContext.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        return true
    }
}
