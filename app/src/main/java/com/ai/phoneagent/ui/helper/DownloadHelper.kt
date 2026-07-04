package com.ai.phoneagent.ui.helper

import android.app.DownloadManager
import android.content.Context

/**
 * 文件下载查询 helper。
 *
 * 抽取自 MainActivity，仅做职责拆分，不改变原有逻辑。
 * 通过 [Context] 获取系统 DownloadManager 服务以查询下载状态。
 */
class DownloadHelper(private val context: Context) {

    fun queryDownloadStatus(downloadId: Long): Pair<Int, Int> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) return DownloadManager.STATUS_FAILED to -1
        val query = DownloadManager.Query().setFilterById(downloadId)
        return runCatching {
            dm.query(query).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    return@use DownloadManager.STATUS_FAILED to -1
                }
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                status to reason
            }
        }.getOrDefault(DownloadManager.STATUS_FAILED to -1)
    }
}
