package com.ai.phoneagent.updates

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.common.VersionComparator
import com.ai.phoneagent.feature.updates.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 启动时的静默更新检查协调器。
 *
 * 先依据缓存提示，再按间隔限制发起后台检查，发现更高版本时落库并提醒。
 */
object UpdateStartupCoordinator {

    /** 静默检查的最小间隔（6 小时）。 */
    private const val SILENT_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    /** 应用启动时执行一次静默检查（受间隔限制）。 */
    fun silentCheckOnLaunch(activity: AppCompatActivity) {
        val currentVersion =
            runCatching {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
            }.getOrDefault("")

        notifyFromCacheIfNeeded(activity, currentVersion)

        val now = System.currentTimeMillis()
        if (!UpdateStore.shouldSilentCheck(activity, nowMs = now, intervalMs = SILENT_CHECK_INTERVAL_MS)) {
            return
        }
        UpdateStore.markSilentChecked(activity, nowMs = now)

        activity.lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    ReleaseRepository().fetchLatestReleaseResilient(includePrerelease = false)
                }

            result
                .onSuccess { latest ->
                    if (latest == null) return@onSuccess
                    if (VersionComparator.compare(latest.version, currentVersion) <= 0) return@onSuccess

                    UpdateStore.saveLatest(activity, latest)
                    notifyIfNeeded(activity, latest)
                }
                .onFailure {
                    // 静默更新检查失败不应打断启动流程。
                }
        }
    }

    /** 若缓存版本高于当前版本则直接提示，无需等待网络。 */
    private fun notifyFromCacheIfNeeded(context: Context, currentVersion: String) {
        val cached = UpdateStore.loadLatest(context) ?: return
        if (VersionComparator.compare(cached.version, currentVersion) <= 0) return
        notifyIfNeeded(context, cached)
    }

    /** 对同一版本仅提醒一次；通知权限缺失时改用 Toast 提示。 */
    private fun notifyIfNeeded(context: Context, entry: ReleaseEntry) {
        if (!UpdateStore.shouldNotify(context, entry.versionTag)) return

        val posted = UpdateNotificationUtil.notifyNewVersion(context, entry)
        if (posted) {
            UpdateStore.markNotified(context, entry.versionTag)
            return
        }

        Toast.makeText(
            context,
            context.getString(R.string.update_notification_permission_missing_format, entry.versionTag),
            Toast.LENGTH_LONG,
        ).show()
    }
}
