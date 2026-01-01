package com.ai.phoneagent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class PermissionBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "PermissionBottomSheet"
        private const val REQ_RECORD_AUDIO = 101
    }

    private var tvAccStatus: TextView? = null
    private var tvOverlayStatus: TextView? = null
    private var tvMicStatus: TextView? = null

    private var btnAcc: MaterialButton? = null
    private var btnOverlay: MaterialButton? = null
    private var btnMic: MaterialButton? = null
    private var btnGuide: MaterialButton? = null
    private var btnDone: MaterialButton? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.sheet_permissions, container, false)

        tvAccStatus = v.findViewById(R.id.tvPermAccStatus)
        tvOverlayStatus = v.findViewById(R.id.tvPermOverlayStatus)
        tvMicStatus = v.findViewById(R.id.tvPermMicStatus)

        btnAcc = v.findViewById(R.id.btnPermAcc)
        btnOverlay = v.findViewById(R.id.btnPermOverlay)
        btnMic = v.findViewById(R.id.btnPermMic)
        btnGuide = v.findViewById(R.id.btnPermGuide)
        btnDone = v.findViewById(R.id.btnPermDone)

        btnAcc?.setOnClickListener { openAccessibilitySettings() }
        btnOverlay?.setOnClickListener { openOverlaySettings() }
        btnMic?.setOnClickListener { requestMicPermission() }
        btnGuide?.setOnClickListener { guideAll() }
        btnDone?.setOnClickListener { dismissAllowingStateLoss() }

        updateUi()

        return v
    }

    override fun onResume() {
        super.onResume()
        updateUi()
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

    private fun updateUi() {
        val ctx = context ?: return

        val accOk = isAccessibilityEnabled(ctx)
        tvAccStatus?.text = if (accOk) "状态：已开启" else "状态：未开启"
        btnAcc?.isEnabled = !accOk
        btnAcc?.text = if (accOk) "已开启" else "去开启"

        val overlayOk = AutomationOverlay.canDrawOverlays(ctx)
        tvOverlayStatus?.text = if (overlayOk) "状态：已开启" else "状态：未开启"
        btnOverlay?.isEnabled = !overlayOk
        btnOverlay?.text = if (overlayOk) "已开启" else "去设置"

        val micOk =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
        tvMicStatus?.text = if (micOk) "状态：已授权" else "状态：未授权"
        btnMic?.isEnabled = !micOk
        btnMic?.text = if (micOk) "已授权" else "授权"
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
                )
                        ?: return false
        val serviceId = "${context.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
        return setting.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent =
                Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                )
        startActivity(intent)
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

        if (!isAccessibilityEnabled(ctx)) {
            openAccessibilitySettings()
            return
        }

        if (!AutomationOverlay.canDrawOverlays(ctx)) {
            openOverlaySettings()
            return
        }

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }

        dismissAllowingStateLoss()
    }
}
