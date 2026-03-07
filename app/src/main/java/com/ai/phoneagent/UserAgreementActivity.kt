package com.ai.phoneagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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

class UserAgreementActivity : AppCompatActivity() {

    private enum class FlowMode {
        ONBOARDING,
        VIEW_ONLY,
        PERMISSION_ONLY,
    }

    private enum class Step {
        WELCOME,
        AGREEMENT,
        PERMISSION,
    }

    companion object {
        private const val EXTRA_FLOW = "flow"
        private const val FLOW_ONBOARDING = "onboarding"
        private const val FLOW_VIEW_ONLY = "view_only"
        private const val FLOW_PERMISSION_ONLY = "permission_only"
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026

        fun createOnboardingIntent(context: Context): Intent =
            Intent(context, UserAgreementActivity::class.java).putExtra(EXTRA_FLOW, FLOW_ONBOARDING)

        fun createViewIntent(context: Context): Intent =
            Intent(context, UserAgreementActivity::class.java).putExtra(EXTRA_FLOW, FLOW_VIEW_ONLY)

        fun createPermissionIntent(context: Context): Intent =
            Intent(context, UserAgreementActivity::class.java).putExtra(EXTRA_FLOW, FLOW_PERMISSION_ONLY)
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var flowMode: FlowMode

    private lateinit var hostRoot: View
    private lateinit var welcomePage: View
    private lateinit var agreementPage: View
    private lateinit var permissionPage: View

    private lateinit var btnWelcomeNext: MaterialButton
    private lateinit var btnAgreementAgree: MaterialButton

    private lateinit var tvAccStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var btnAcc: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnMic: MaterialButton
    private lateinit var btnGuide: MaterialButton
    private lateinit var btnDone: MaterialButton
    private lateinit var permissionHeader: View
    private lateinit var permissionActions: View

    private var currentStep: Step? = null
    private var isTransitionRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_agreement_flow)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        flowMode =
            when (intent.getStringExtra(EXTRA_FLOW)) {
                FLOW_VIEW_ONLY -> FlowMode.VIEW_ONLY
                FLOW_PERMISSION_ONLY -> FlowMode.PERMISSION_ONLY
                else -> FlowMode.ONBOARDING
            }

        configureEdgeToEdge()
        bindViews()
        setupAgreementPage()
        setupPermissionPage()
        applyWindowInsets()
        setupBackBehavior()

        val initialStep =
            when (flowMode) {
                FlowMode.ONBOARDING -> Step.WELCOME
                FlowMode.VIEW_ONLY -> Step.AGREEMENT
                FlowMode.PERMISSION_ONLY -> Step.PERMISSION
            }
        showStep(initialStep, forward = true, animate = false)
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == Step.PERMISSION) {
            updatePermissionUi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            updatePermissionUi()
        }
    }

    private fun bindViews() {
        hostRoot = findViewById(R.id.onboardingHost)
        welcomePage = findViewById(R.id.pageWelcome)
        agreementPage = findViewById(R.id.pageAgreement)
        permissionPage = findViewById(R.id.pagePermission)

        btnWelcomeNext = welcomePage.findViewById(R.id.btnWelcomeNext)
        btnAgreementAgree = agreementPage.findViewById(R.id.btnAgreementAgree)

        permissionHeader = permissionPage.findViewById(R.id.permissionSheetHeader)
        permissionActions = permissionPage.findViewById(R.id.permissionSheetActions)
        tvAccStatus = permissionPage.findViewById(R.id.tvPermAccStatus)
        tvOverlayStatus = permissionPage.findViewById(R.id.tvPermOverlayStatus)
        tvMicStatus = permissionPage.findViewById(R.id.tvPermMicStatus)
        btnAcc = permissionPage.findViewById(R.id.btnPermAcc)
        btnOverlay = permissionPage.findViewById(R.id.btnPermOverlay)
        btnMic = permissionPage.findViewById(R.id.btnPermMic)
        btnGuide = permissionPage.findViewById(R.id.btnPermGuide)
        btnDone = permissionPage.findViewById(R.id.btnPermDone)
    }

    private fun setupAgreementPage() {
        val contentView = agreementPage.findViewById<TextView>(R.id.tvAgreementContent)
        val content = getString(R.string.user_agreement_content)
        contentView.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(content)
            }

        btnWelcomeNext.setOnClickListener {
            showStep(Step.AGREEMENT, forward = true, animate = true)
        }
        btnAgreementAgree.text =
            getString(
                if (flowMode == FlowMode.VIEW_ONLY) {
                    R.string.action_close
                } else {
                    R.string.user_agreement_action_next
                },
            )
        btnAgreementAgree.setOnClickListener {
            if (flowMode == FlowMode.VIEW_ONLY) {
                finishWithSlideBack()
                return@setOnClickListener
            }
            prefs.edit().putBoolean("user_agreement_accepted", true).apply()
            showStep(Step.PERMISSION, forward = true, animate = true)
        }
    }

    private fun setupPermissionPage() {
        btnAcc.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnMic.setOnClickListener { requestMicPermission() }
        btnGuide.setOnClickListener { guideAll() }
        btnDone.setOnClickListener { finishWithSlideBack() }
    }

    private fun setupBackBehavior() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isTransitionRunning) return
                    when (flowMode) {
                        FlowMode.VIEW_ONLY -> finishWithSlideBack()
                        FlowMode.PERMISSION_ONLY -> finishWithSlideBack()
                        FlowMode.ONBOARDING -> {
                            when (currentStep) {
                                Step.PERMISSION -> showStep(Step.AGREEMENT, forward = false, animate = true)
                                Step.AGREEMENT -> showStep(Step.WELCOME, forward = false, animate = true)
                                else -> Unit
                            }
                        }
                    }
                }
            },
        )
    }

    private fun showStep(target: Step, forward: Boolean, animate: Boolean) {
        if (currentStep == target || isTransitionRunning) return

        val targetView = pageFor(target)
        val previousStep = currentStep
        val previousView = previousStep?.let { pageFor(it) }

        if (!animate || previousView == null || hostRoot.width == 0) {
            listOf(welcomePage, agreementPage, permissionPage).forEach { page ->
                page.isVisible = page === targetView
                page.alpha = 1f
                page.translationX = 0f
            }
            currentStep = target
            onStepShown(target)
            return
        }

        isTransitionRunning = true
        val distance = hostRoot.width.toFloat().coerceAtLeast(1f)
        val enterFrom = if (forward) distance * 0.18f else -distance * 0.18f
        val exitTo = if (forward) -distance * 0.12f else distance * 0.12f

        targetView.isVisible = true
        targetView.alpha = 0f
        targetView.translationX = enterFrom

        previousView.animate()
            .translationX(exitTo)
            .alpha(0f)
            .setDuration(280)
            .withEndAction {
                previousView.isVisible = false
                previousView.translationX = 0f
                previousView.alpha = 1f
            }
            .start()

        targetView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(320)
            .withEndAction {
                currentStep = target
                isTransitionRunning = false
                onStepShown(target)
            }
            .start()
    }

    private fun onStepShown(step: Step) {
        if (step == Step.PERMISSION) {
            prefs.edit().putBoolean("perm_guide_shown", true).apply()
            updatePermissionUi()
        }
    }

    private fun pageFor(step: Step): View =
        when (step) {
            Step.WELCOME -> welcomePage
            Step.AGREEMENT -> agreementPage
            Step.PERMISSION -> permissionPage
        }

    private fun finishWithSlideBack() {
        super.finish()
        overridePendingTransition(R.anim.m3t_slide_in_left, R.anim.m3t_slide_out_right)
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val pageColor = ContextCompat.getColor(this, R.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun applyWindowInsets() {
        applyPageInsets(
            root = welcomePage,
            header = welcomePage.findViewById(R.id.welcomeHeader),
            actions = welcomePage.findViewById(R.id.welcomeActions),
        )
        applyPageInsets(
            root = agreementPage.findViewById(R.id.cardAgreement),
            header = agreementPage.findViewById(R.id.agreementHeader),
            actions = agreementPage.findViewById(R.id.agreementActions),
        )
        applyPageInsets(
            root = permissionPage,
            header = permissionHeader,
            actions = permissionActions,
        )
    }

    private fun applyPageInsets(root: View, header: View, actions: View) {
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

    private fun updatePermissionUi() {
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
            updatePermissionUi()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun guideAll() {
        if (allPermissionsReady(this)) {
            finishWithSlideBack()
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
                finishWithSlideBack()
                return
            }
            updatePermissionUi()
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

        finishWithSlideBack()
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