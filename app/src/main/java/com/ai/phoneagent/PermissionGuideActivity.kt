package com.ai.phoneagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku

class PermissionGuideActivity : AppCompatActivity() {

    companion object {
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026

        fun createIntent(context: Context): Intent =
            Intent(context, PermissionGuideActivity::class.java)
    }

    private lateinit var tvAccStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var btnAcc: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnMic: MaterialButton
    private lateinit var btnGuide: MaterialButton
    private lateinit var btnDone: MaterialButton
    private lateinit var headerContainer: View
    private lateinit var actionContainer: View
    private lateinit var rootView: View

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.m3t_slide_in_left, R.anim.m3t_slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_permissions)

        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("perm_guide_shown", true)
            .apply()

        configureEdgeToEdge()
        bindViews()

        btnAcc.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnMic.setOnClickListener { requestMicPermission() }
        btnGuide.setOnClickListener { guideAll() }
        btnDone.setOnClickListener { finish() }

        applyWindowInsets(rootView)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            updateUi()
        }
    }

    private fun bindViews() {
        rootView = findViewById(R.id.permissionSheetRoot)
        headerContainer = findViewById(R.id.permissionSheetHeader)
        actionContainer = findViewById(R.id.permissionSheetActions)

        tvAccStatus = findViewById(R.id.tvPermAccStatus)
        tvOverlayStatus = findViewById(R.id.tvPermOverlayStatus)
        tvMicStatus = findViewById(R.id.tvPermMicStatus)

        btnAcc = findViewById(R.id.btnPermAcc)
        btnOverlay = findViewById(R.id.btnPermOverlay)
        btnMic = findViewById(R.id.btnPermMic)
        btnGuide = findViewById(R.id.btnPermGuide)
        btnDone = findViewById(R.id.btnPermDone)
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val pageColor = ContextCompat.getColor(this, R.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun applyWindowInsets(root: View) {
        val header = headerContainer
        val actions = actionContainer
        val rootStart = root.paddingStart
        val rootEnd = root.paddingEnd
        val headerTop = header.paddingTop
        val actionsBottom = actions.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(left = rootStart + systemBars.left, right = rootEnd + systemBars.right)
            header.updatePadding(top = headerTop + systemBars.top)
            actions.updatePadding(bottom = actionsBottom + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun allPermissionsReady(context: Context): Boolean {
        val micOk =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        return isAccessibilityEnabled(context) && hasOverlayPermission(context) && micOk
    }

    private fun updateUi() {
        val accOk = isAccessibilityEnabled(this)
        updatePermissionRow(tvAccStatus, btnAcc, accOk, R.string.perm_sheet_action_enable)

        val overlayOk = hasOverlayPermission(this)
        updatePermissionRow(tvOverlayStatus, btnOverlay, overlayOk, R.string.perm_sheet_action_settings)

        val micOk =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        updatePermissionRow(tvMicStatus, btnMic, micOk, R.string.perm_sheet_action_grant)

        val allOk = accOk && overlayOk && micOk
        btnGuide.text =
            getString(
                if (allOk) {
                    R.string.perm_sheet_primary_action_ready
                } else {
                    R.string.perm_sheet_primary_action
                },
            )
        btnDone.isVisible = !allOk
    }

    private fun updatePermissionRow(
        statusView: TextView,
        actionButton: MaterialButton,
        ready: Boolean,
        @StringRes pendingActionText: Int,
    ) {
        statusView.text =
            getString(
                if (ready) {
                    R.string.perm_sheet_status_ready
                } else {
                    R.string.perm_sheet_status_pending
                },
            )
        statusView.setTextColor(
            ContextCompat.getColor(
                this,
                if (ready) {
                    R.color.blue_glass_primary
                } else {
                    R.color.blue_glass_text_dim
                },
            ),
        )
        actionButton.isEnabled = !ready
        actionButton.text =
            getString(
                if (ready) {
                    R.string.perm_sheet_action_ready
                } else {
                    pendingActionText
                },
            )
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
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
        val serviceId = "${context.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
        return setting.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        val componentName = ComponentName(this, PhoneAgentAccessibilityService::class.java)
        val actionAccessibilityDetailsSettings = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        val extraAccessibilityServiceComponentName =
            "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

        fun tryStart(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intentWithComponent =
                Intent(actionAccessibilityDetailsSettings).apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                    putExtra(extraAccessibilityServiceComponentName, componentName)
                }
            if (tryStart(intentWithComponent)) return

            val intentWithFlattenedName =
                Intent(actionAccessibilityDetailsSettings).apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                    putExtra(extraAccessibilityServiceComponentName, componentName.flattenToString())
                }
            if (tryStart(intentWithFlattenedName)) return
        }

        tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestMicPermission() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            updateUi()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun guideAll() {
        if (allPermissionsReady(this)) {
            finish()
            return
        }

        if (ShizukuBridge.pingBinder() && !ShizukuBridge.hasPermission()) {
            runCatching { Shizuku.requestPermission(REQ_SHIZUKU_PERMISSION) }
            Toast.makeText(
                this,
                getString(R.string.automation_shizuku_permission_requested),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        if (ShizukuBridge.isShizukuAvailable()) {
            val autoGranted = grantPermissionsViaShizuku(this)
            if (autoGranted) {
                Toast.makeText(this, getString(R.string.perm_sheet_shizuku_success), Toast.LENGTH_SHORT)
                    .show()
                finish()
                return
            }
            updateUi()
        }

        if (!isAccessibilityEnabled(this)) {
            openAccessibilitySettings()
            return
        }

        if (!hasOverlayPermission(this)) {
            openOverlaySettings()
            return
        }

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }

        finish()
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

        val serviceId = "${context.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
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

        val result =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("appops", "set", context.packageName, "SYSTEM_ALERT_WINDOW", "allow"),
                )
            }.getOrNull()

        return hasOverlayPermission(context) || (result != null && result.exitCode == 0)
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