package com.ai.phoneagent

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
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

    private fun updateUi() {
        val hostActivity = activity as? AppCompatActivity ?: return
        val ctx = context ?: return

        val accOk = PermissionSetupSupport.isAccessibilityEnabled(ctx)
        updatePermissionRow(tvAccStatus, btnAcc, accOk, R.string.perm_sheet_action_enable)

        val overlayOk = PermissionSetupSupport.hasOverlayPermission(ctx)
        updatePermissionRow(tvOverlayStatus, btnOverlay, overlayOk, R.string.perm_sheet_action_settings)

        val micOk =
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
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
        val hostActivity = activity as? AppCompatActivity ?: return
        if (statusView == null || actionButton == null) return
        PermissionSetupSupport.updatePermissionRow(
            activity = hostActivity,
            statusView = statusView,
            actionButton = actionButton,
            ready = ready,
            pendingActionText = pendingActionText,
        )
    }

    private fun openAccessibilitySettings() {
        val hostActivity = activity as? AppCompatActivity ?: return
        PermissionSetupSupport.openAccessibilitySettings(hostActivity)
    }

    private fun openOverlaySettings() {
        val hostActivity = activity as? AppCompatActivity ?: return
        PermissionSetupSupport.openOverlaySettings(hostActivity)
    }

    private fun requestMicPermission() {
        val ctx = context ?: return
        val granted =
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            updateUi()
            return
        }
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun guideAll() {
        val hostActivity = activity as? AppCompatActivity ?: return
        val ctx = context ?: return

        PermissionSetupSupport.guideAll(
            activity = hostActivity,
            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
            requestMicPermission = { requestMicPermission() },
            onReady = { dismissAllowingStateLoss() },
            onUiRefresh = { updateUi() },
        )
    }
}
