package com.ai.phoneagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.feature.settings.R
import com.google.android.material.button.MaterialButton

object PermissionSetupSupport {

    private const val ACCESSIBILITY_SERVICE_CLASS = "com.ai.phoneagent.PhoneAgentAccessibilityService"
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME =
        "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

    fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun allPermissionsReady(context: Context): Boolean {
        val micOk =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        return isAccessibilityEnabled(context) && hasOverlayPermission(context) && micOk
    }

    fun updatePermissionRow(
        activity: AppCompatActivity,
        statusView: TextView,
        actionButton: MaterialButton,
        ready: Boolean,
        @StringRes pendingActionText: Int,
    ) {
        statusView.text =
            activity.getString(
                if (ready) {
                    R.string.perm_sheet_status_ready
                } else {
                    R.string.perm_sheet_status_pending
                },
            )
        statusView.setTextColor(
            ContextCompat.getColor(
                activity,
                if (ready) {
                    DesignSystemR.color.blue_glass_primary
                } else {
                    DesignSystemR.color.blue_glass_text_dim
                },
            ),
        )
        actionButton.isEnabled = !ready
        actionButton.text =
            activity.getString(
                if (ready) {
                    R.string.perm_sheet_action_ready
                } else {
                    pendingActionText
                },
            )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            )
        if (enabled != 1) return false

        val setting =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

        val serviceId = "${context.packageName}/$ACCESSIBILITY_SERVICE_CLASS"
        return setting.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }

    fun openAccessibilitySettings(activity: AppCompatActivity) {
        val componentName = ComponentName(activity.packageName, ACCESSIBILITY_SERVICE_CLASS)

        fun tryStart(intent: Intent): Boolean = runCatching { activity.startActivity(intent) }.isSuccess

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intentWithComponent =
                Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                    putExtra(EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME, componentName)
                }
            if (tryStart(intentWithComponent)) return

            val intentWithFlattenedName =
                Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                    putExtra(EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME, componentName.flattenToString())
                }
            if (tryStart(intentWithFlattenedName)) return
        }

        tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlaySettings(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
    }

    fun requestMicPermission(
        activity: AppCompatActivity,
        requestCode: Int,
        onAlreadyGranted: (() -> Unit)? = null,
    ) {
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onAlreadyGranted?.invoke()
            return
        }
        activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
    }

    fun guideAll(
        activity: AppCompatActivity,
        requestShizukuPermissionCode: Int,
        requestMicPermission: () -> Unit,
        onReady: () -> Unit,
        onUiRefresh: () -> Unit,
    ) {
        if (allPermissionsReady(activity)) {
            onReady()
            return
        }

        if (ShizukuBridge.pingBinder() && !ShizukuBridge.hasPermission()) {
            if (ShizukuBridge.requestPermission(requestShizukuPermissionCode)) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.automation_shizuku_permission_requested),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }

        if (ShizukuBridge.isShizukuAvailable()) {
            val autoGranted = grantPermissionsViaShizuku(activity)
            if (autoGranted) {
                Toast.makeText(activity, activity.getString(R.string.perm_sheet_shizuku_success), Toast.LENGTH_SHORT)
                    .show()
                onReady()
                return
            }
            onUiRefresh()
        }

        if (!isAccessibilityEnabled(activity)) {
            openAccessibilitySettings(activity)
            return
        }

        if (!hasOverlayPermission(activity)) {
            openOverlaySettings(activity)
            return
        }

        if (
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }

        onReady()
    }

    private fun grantPermissionsViaShizuku(context: Context): Boolean {
        val accessibilityGranted = grantAccessibilityServiceViaShizuku(context)
        if (!accessibilityGranted) return false

        val overlayGranted = grantOverlayPermissionViaShizuku(context)
        if (!overlayGranted) return false

        return grantMicrophonePermissionViaShizuku(context)
    }

    private fun grantAccessibilityServiceViaShizuku(context: Context): Boolean {
        if (isAccessibilityEnabled(context)) return true

        val serviceId = "${context.packageName}/$ACCESSIBILITY_SERVICE_CLASS"
        val existing =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
        val serviceSet =
            existing.split(':')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

        if (!serviceSet.any { it.equals(serviceId, ignoreCase = true) }) {
            serviceSet.add(serviceId)
        }

        val enableList = serviceSet.joinToString(":")
        val setServicesResult =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("settings", "put", "secure", "enabled_accessibility_services", enableList),
                )
            }.getOrNull()
        val enableServiceResult =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("settings", "put", "secure", "accessibility_enabled", "1"),
                )
            }.getOrNull()

        if (setServicesResult == null || setServicesResult.exitCode != 0) return false
        if (enableServiceResult == null || enableServiceResult.exitCode != 0) return false

        return isAccessibilityEnabled(context)
    }

    private fun grantOverlayPermissionViaShizuku(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (hasOverlayPermission(context)) return true

        runCatching {
            ShizukuBridge.execResultArgs(
                listOf("appops", "set", context.packageName, "SYSTEM_ALERT_WINDOW", "allow"),
            )
        }
        runCatching {
            ShizukuBridge.execResultArgs(
                listOf("appops", "set", context.packageName, "android:system_alert_window", "allow"),
            )
        }

        return hasOverlayPermission(context)
    }

    private fun grantMicrophonePermissionViaShizuku(context: Context): Boolean {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        runCatching {
            ShizukuBridge.execResultArgs(
                listOf("pm", "grant", context.packageName, Manifest.permission.RECORD_AUDIO),
            )
        }

        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        val fallback =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("appops", "set", context.packageName, "RECORD_AUDIO", "allow"),
                )
            }.getOrNull()

        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED || (fallback != null && fallback.exitCode == 0)
    }
}
