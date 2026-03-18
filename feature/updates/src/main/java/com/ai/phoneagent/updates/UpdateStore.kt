package com.ai.phoneagent.updates

import android.content.Context

object UpdateStore {

    private const val PREFS = "update_prefs"

    private const val KEY_LAST_CHECK_AT = "update_last_check_at"
    private const val KEY_LAST_NOTIFIED_VERSION = "update_last_notified_version"

    private const val KEY_VERSION_TAG = "update_latest_version_tag"
    private const val KEY_VERSION = "update_latest_version"
    private const val KEY_TITLE = "update_latest_title"
    private const val KEY_DATE = "update_latest_date"
    private const val KEY_BODY = "update_latest_body"
    private const val KEY_RELEASE_URL = "update_latest_release_url"
    private const val KEY_APK_URL = "update_latest_apk_url"

    fun shouldSilentCheck(context: Context, nowMs: Long, intervalMs: Long): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getLong(KEY_LAST_CHECK_AT, 0L)
        return nowMs - last >= intervalMs
    }

    fun markSilentChecked(context: Context, nowMs: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putLong(KEY_LAST_CHECK_AT, nowMs).apply()
    }

    fun shouldNotify(context: Context, versionTag: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getString(KEY_LAST_NOTIFIED_VERSION, null)
        return last != versionTag
    }

    fun markNotified(context: Context, versionTag: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString(KEY_LAST_NOTIFIED_VERSION, versionTag).apply()
    }

    fun saveLatest(context: Context, entry: ReleaseEntry) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putString(KEY_VERSION_TAG, entry.versionTag)
            .putString(KEY_VERSION, entry.version)
            .putString(KEY_TITLE, entry.title)
            .putString(KEY_DATE, entry.date)
            .putString(KEY_BODY, entry.body)
            .putString(KEY_RELEASE_URL, entry.releaseUrl)
            .putString(KEY_APK_URL, entry.apkUrl)
            .apply()
    }

    fun loadLatest(context: Context): ReleaseEntry? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versionTag = p.getString(KEY_VERSION_TAG, null) ?: return null
        val version = p.getString(KEY_VERSION, "") ?: ""
        val title = p.getString(KEY_TITLE, "版本更新") ?: "版本更新"
        val date = p.getString(KEY_DATE, "") ?: ""
        val body = p.getString(KEY_BODY, "") ?: ""
        val releaseUrl = p.getString(KEY_RELEASE_URL, "") ?: ""
        val apkUrl = p.getString(KEY_APK_URL, null)

        return ReleaseEntry(
            versionTag = versionTag,
            version = version,
            title = title,
            date = date,
            isPrerelease = false,
            body = body,
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            apkAssetId = null,
        )
    }
}
