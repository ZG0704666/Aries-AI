package com.ai.phoneagent.updates

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
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

        val req = DownloadManager.Request(Uri.parse(resolvedUrl))
        req.setTitle("下载 ${entry.versionTag}")
        req.setDescription(entry.title)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setMimeType("application/vnd.android.package-archive")

        val fileName = "${UpdateConfig.REPO_NAME}-${entry.versionTag}.apk".replace("/", "_")
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

        runCatching {
            dm.enqueue(req)
            Toast.makeText(context, "开始下载：$fileName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "下载失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
