package com.ai.assistance.operit.data.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.AppLogger
import java.io.File

class PatchInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PatchUpdateInstaller.ACTION_INSTALL_PATCH) return

        val path = intent.getStringExtra(PatchUpdateInstaller.EXTRA_APK_PATH) ?: return
        val file = File(path)
        if (!file.exists()) {
            AppLogger.w("PatchInstallReceiver", "apk not found: $path")
            return
        }

        PatchUpdateInstaller.installApk(context, file)
    }
}
