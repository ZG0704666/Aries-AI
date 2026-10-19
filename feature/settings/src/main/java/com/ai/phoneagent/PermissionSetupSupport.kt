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

/**
 * 权限引导与状态检查辅助。
 *
 * 统一封装无障碍、悬浮窗、麦克风及 Shizuku 权限的检查、跳转与（Shizuku 可用时的）自动授予逻辑。
 */
object PermissionSetupSupport {

    private const val ACCESSIBILITY_SERVICE_CLASS = "com.ai.phoneagent.PhoneAgentAccessibilityService"
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME =
        "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

    /** 是否已获得悬浮窗权限（Android 6.0 以下默认具备）。 */
    fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** 无障碍、悬浮窗、麦克风三项权限是否全部就绪。 */
    fun allPermissionsReady(context: Context): Boolean {
        val micOk =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        return isAccessibilityEnabled(context) && hasOverlayPermission(context) && micOk
    }

    /**
     * 刷新权限条目的状态文案、颜色与按钮可用性。
     *
     * @param ready 权限是否已就绪。
     * @param pendingActionText 未就绪时按钮显示的操作文案资源。
     */
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

    /** 判断本应用的无障碍服务是否在系统已启用列表中。 */
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

    /** 跳转到无障碍设置；Android 10+ 优先直达本服务详情页，失败时回退到列表页。 */
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

    /** 跳转到本应用的悬浮窗授权页。 */
    fun openOverlaySettings(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
    }

    /** 跳转到本应用的系统详情设置页。 */
    fun openAppDetailsSettings(activity: AppCompatActivity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
    }

    /**
     * 请求麦克风权限；已授权时直接回调 [onAlreadyGranted]。
     */
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

    /**
     * 处理麦克风权限申请结果。
     *
     * 被永久拒绝（不再显示系统弹窗）时提示并跳转到应用详情页引导手动开启。
     */
    fun handleMicPermissionResult(
        activity: AppCompatActivity,
        grantResults: IntArray,
    ) {
        val granted =
            grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (!shouldOpenMicPermissionSettings(activity)) return

        Toast.makeText(
            activity,
            activity.getString(R.string.perm_sheet_microphone_settings_hint),
            Toast.LENGTH_SHORT,
        ).show()
        openAppDetailsSettings(activity)
    }

    /**
     * 权限引导主流程：按 Shizuku 授权 → 无障碍 → 悬浮窗 → 麦克风的顺序依次处理。
     *
     * Shizuku 可用时尝试一次性自动授予；否则逐个跳转系统页面，最终由 [onReady] 通知完成。
     */
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

    /** 麦克风权限被永久拒绝时需要跳转设置页，此方法判定该场景。 */
    private fun shouldOpenMicPermissionSettings(activity: AppCompatActivity): Boolean {
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) return false
        return !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    /** 通过 Shizuku 依次授予无障碍、悬浮窗、麦克风权限，全部成功才返回 true。 */
    private fun grantPermissionsViaShizuku(context: Context): Boolean {
        val accessibilityGranted = grantAccessibilityServiceViaShizuku(context)
        if (!accessibilityGranted) return false

        val overlayGranted = grantOverlayPermissionViaShizuku(context)
        if (!overlayGranted) return false

        return grantMicrophonePermissionViaShizuku(context)
    }

    /** 通过 `settings put` 将本服务写入已启用无障碍列表，并开启无障碍总开关。 */
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

    /** 通过 `appops` 授予悬浮窗权限，兼容新旧两种 op 名称。 */
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

    /** 先尝试 `pm grant` 授予录音权限，失败则回退到 `appops`。 */
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
