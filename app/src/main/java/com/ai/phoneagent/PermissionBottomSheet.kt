package com.ai.phoneagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku

class PermissionBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "PermissionBottomSheet"
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026
    }

    private var tvAccStatus: TextView? = null
    private var tvOverlayStatus: TextView? = null
    private var tvMicStatus: TextView? = null

    private var btnAcc: MaterialButton? = null
    private var btnOverlay: MaterialButton? = null
    private var btnMic: MaterialButton? = null
    private var btnGuide: MaterialButton? = null
    private var btnDone: MaterialButton? = null

    private var headerContainer: View? = null
    private var actionContainer: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.RoundedBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerContainer = view.findViewById(R.id.permissionSheetHeader)
        actionContainer = view.findViewById(R.id.permissionSheetActions)

        tvAccStatus = view.findViewById(R.id.tvPermAccStatus)
        tvOverlayStatus = view.findViewById(R.id.tvPermOverlayStatus)
        tvMicStatus = view.findViewById(R.id.tvPermMicStatus)

        btnAcc = view.findViewById(R.id.btnPermAcc)
        btnOverlay = view.findViewById(R.id.btnPermOverlay)
        btnMic = view.findViewById(R.id.btnPermMic)
        btnGuide = view.findViewById(R.id.btnPermGuide)
        btnDone = view.findViewById(R.id.btnPermDone)


        btnAcc?.setOnClickListener { openAccessibilitySettings() }
        btnOverlay?.setOnClickListener { openOverlaySettings() }
        btnMic?.setOnClickListener { requestMicPermission() }
        btnGuide?.setOnClickListener { guideAll() }
        btnDone?.setOnClickListener { dismissAllowingStateLoss() }

        applyWindowInsets(view)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onStart() {
        super.onStart()
        configureFullscreenSheet()
    }

    override fun onDestroyView() {
        tvAccStatus = null
        tvOverlayStatus = null
        tvMicStatus = null
        btnAcc = null
        btnOverlay = null
        btnMic = null
        btnGuide = null
        btnDone = null
        headerContainer = null
        actionContainer = null
        super.onDestroyView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            updateUi()
        }
    }

    private fun configureFullscreenSheet() {
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val window = sheetDialog.window ?: return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val systemBarColor = ContextCompat.getColor(requireContext(), R.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }

        val bottomSheet =
            sheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        bottomSheet.requestLayout()

        sheetDialog.behavior.apply {
            skipCollapsed = true
            isHideable = true
            isDraggable = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun applyWindowInsets(root: View) {
        val header = headerContainer ?: return
        val actions = actionContainer ?: return

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

    private fun hasOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private fun allPermissionsReady(context: Context): Boolean {
        val micOk =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        return isAccessibilityEnabled(context) && hasOverlayPermission(context) && micOk
    }

    private fun updateUi() {
        val ctx = context ?: return

        val accOk = isAccessibilityEnabled(ctx)
        updatePermissionRow(tvAccStatus, btnAcc, accOk, R.string.perm_sheet_action_enable)

        val overlayOk = hasOverlayPermission(ctx)
        updatePermissionRow(tvOverlayStatus, btnOverlay, overlayOk, R.string.perm_sheet_action_settings)

        val micOk =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        updatePermissionRow(tvMicStatus, btnMic, micOk, R.string.perm_sheet_action_grant)

        val allOk = accOk && overlayOk && micOk
        btnGuide?.text =
            getString(
                if (allOk) {
                    R.string.perm_sheet_primary_action_ready
                } else {
                    R.string.perm_sheet_primary_action
                }
            )
        btnDone?.isVisible = !allOk
    }

    private fun updatePermissionRow(
        statusView: TextView?,
        actionButton: MaterialButton?,
        ready: Boolean,
        @StringRes pendingActionText: Int
    ) {
        val ctx = context ?: return
        statusView?.text =
            getString(
                if (ready) {
                    R.string.perm_sheet_status_ready
                } else {
                    R.string.perm_sheet_status_pending
                }
            )
        statusView?.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (ready) {
                    R.color.blue_glass_primary
                } else {
                    R.color.blue_glass_text_dim
                }
            )
        )
        actionButton?.isEnabled = !ready
        actionButton?.text =
            getString(
                if (ready) {
                    R.string.perm_sheet_action_ready
                } else {
                    pendingActionText
                }
            )
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
        if (enabled != 1) return false
        val setting =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
        val serviceId = "${context.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
        return setting.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        val ctx = context ?: return
        val componentName = ComponentName(ctx, PhoneAgentAccessibilityService::class.java)
        val actionAccessibilityDetailsSettings = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        val extraAccessibilityServiceComponentName =
            "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

        fun tryStart(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intentWithComponent = Intent(actionAccessibilityDetailsSettings).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(extraAccessibilityServiceComponentName, componentName)
            }
            if (tryStart(intentWithComponent)) return

            val intentWithFlattenedName = Intent(actionAccessibilityDetailsSettings).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(extraAccessibilityServiceComponentName, componentName.flattenToString())
            }
            if (tryStart(intentWithFlattenedName)) return
        }

        tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
        )
    }

    private fun requestMicPermission() {
        val ctx = context ?: return
        val granted =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            updateUi()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun guideAll() {
        val ctx = context ?: return

        if (allPermissionsReady(ctx)) {
            dismissAllowingStateLoss()
            return
        }

        if (ShizukuBridge.pingBinder() && !ShizukuBridge.hasPermission()) {
            runCatching { Shizuku.requestPermission(REQ_SHIZUKU_PERMISSION) }
            Toast.makeText(
                ctx,
                getString(R.string.automation_shizuku_permission_requested),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (ShizukuBridge.isShizukuAvailable()) {
            val autoGranted = grantPermissionsViaShizuku(ctx)
            if (autoGranted) {
                Toast.makeText(ctx, getString(R.string.perm_sheet_shizuku_success), Toast.LENGTH_SHORT)
                    .show()
                dismissAllowingStateLoss()
                return
            }
            updateUi()
        }

        if (!isAccessibilityEnabled(ctx)) {
            openAccessibilitySettings()
            return
        }

        if (!hasOverlayPermission(ctx)) {
            openOverlaySettings()
            return
        }

        if (
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }

        dismissAllowingStateLoss()
    }

    private fun grantPermissionsViaShizuku(context: Context): Boolean {
        val accessibilityGranted = grantAccessibilityServiceViaShizuku(context)
        if (!accessibilityGranted) {
            return false
        }

        val overlayGranted = grantOverlayPermissionViaShizuku(context)
        if (!overlayGranted) {
            return false
        }

        return grantMicrophonePermissionViaShizuku(context)
    }

    private fun grantAccessibilityServiceViaShizuku(context: Context): Boolean {
        if (isAccessibilityEnabled(context)) return true

        val serviceId = "${context.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
        val existing =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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
                    listOf("settings", "put", "secure", "enabled_accessibility_services", enableList)
                )
            }.getOrNull()
        val enableServiceResult =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("settings", "put", "secure", "accessibility_enabled", "1")
                )
            }.getOrNull()

        if (setServicesResult == null || setServicesResult.exitCode != 0) {
            return false
        }
        if (enableServiceResult == null || enableServiceResult.exitCode != 0) {
            return false
        }

        return isAccessibilityEnabled(context)
    }

    private fun grantOverlayPermissionViaShizuku(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (hasOverlayPermission(context)) return true

        runCatching {
            ShizukuBridge.execResultArgs(
                listOf("appops", "set", context.packageName, "SYSTEM_ALERT_WINDOW", "allow")
            )
        }
        runCatching {
            ShizukuBridge.execResultArgs(
                listOf("appops", "set", context.packageName, "android:system_alert_window", "allow")
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
                listOf("pm", "grant", context.packageName, Manifest.permission.RECORD_AUDIO)
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
                    listOf("appops", "set", context.packageName, "RECORD_AUDIO", "allow")
                )
            }.getOrNull()

        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED || (fallback != null && fallback.exitCode == 0)
    }
}
