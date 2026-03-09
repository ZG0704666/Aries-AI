package com.ai.phoneagent

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.feature.settings.R
import com.ai.phoneagent.system.applyMaterialCloseTransition
import com.google.android.material.button.MaterialButton

class PermissionGuideActivity : AppCompatActivity() {

    companion object {
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026

        fun createIntent(context: android.content.Context) =
            android.content.Intent(context, PermissionGuideActivity::class.java)
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
        applyMaterialCloseTransition()
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
        bindActions()
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

    private fun bindActions() {
        btnAcc.setOnClickListener { PermissionSetupSupport.openAccessibilitySettings(this) }
        btnOverlay.setOnClickListener { PermissionSetupSupport.openOverlaySettings(this) }
        btnMic.setOnClickListener { requestMicPermission() }
        btnGuide.setOnClickListener { guideAll() }
        btnDone.setOnClickListener { finish() }
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val pageColor = ContextCompat.getColor(this, DesignSystemR.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(DesignSystemR.bool.m3t_light_system_bars)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun applyWindowInsets(root: View) {
        val rootStart = root.paddingStart
        val rootEnd = root.paddingEnd
        val headerTop = headerContainer.paddingTop
        val actionsBottom = actionContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(left = rootStart + systemBars.left, right = rootEnd + systemBars.right)
            headerContainer.updatePadding(top = headerTop + systemBars.top)
            actionContainer.updatePadding(bottom = actionsBottom + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun requestMicPermission() {
        PermissionSetupSupport.requestMicPermission(this, REQ_RECORD_AUDIO) { updateUi() }
    }

    private fun guideAll() {
        PermissionSetupSupport.guideAll(
            activity = this,
            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
            requestMicPermission = { requestMicPermission() },
            onReady = { finish() },
            onUiRefresh = { updateUi() },
        )
    }

    private fun updateUi() {
        val accOk = PermissionSetupSupport.isAccessibilityEnabled(this)
        updatePermissionRow(tvAccStatus, btnAcc, accOk, R.string.perm_sheet_action_enable)

        val overlayOk = PermissionSetupSupport.hasOverlayPermission(this)
        updatePermissionRow(tvOverlayStatus, btnOverlay, overlayOk, R.string.perm_sheet_action_settings)

        val micOk =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
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
        PermissionSetupSupport.updatePermissionRow(
            activity = this,
            statusView = statusView,
            actionButton = actionButton,
            ready = ready,
            pendingActionText = pendingActionText,
        )
    }
}
