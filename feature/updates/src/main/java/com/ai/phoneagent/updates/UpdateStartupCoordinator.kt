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

object UpdateStartupCoordinator {

    private const val SILENT_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

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
                    // Silent update checks should not interrupt the launch flow.
                }
        }
    }

    private fun notifyFromCacheIfNeeded(context: Context, currentVersion: String) {
        val cached = UpdateStore.loadLatest(context) ?: return
        if (VersionComparator.compare(cached.version, currentVersion) <= 0) return
        notifyIfNeeded(context, cached)
    }

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
