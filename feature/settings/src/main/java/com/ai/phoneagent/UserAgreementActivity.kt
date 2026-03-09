package com.ai.phoneagent

import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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

        fun createOnboardingIntent(context: android.content.Context) =
            android.content.Intent(context, UserAgreementActivity::class.java)
                .putExtra(EXTRA_FLOW, FLOW_ONBOARDING)

        fun createViewIntent(context: android.content.Context) =
            android.content.Intent(context, UserAgreementActivity::class.java)
                .putExtra(EXTRA_FLOW, FLOW_VIEW_ONLY)

        fun createPermissionIntent(context: android.content.Context) =
            android.content.Intent(context, UserAgreementActivity::class.java)
                .putExtra(EXTRA_FLOW, FLOW_PERMISSION_ONLY)
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
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
        btnAcc.setOnClickListener { PermissionSetupSupport.openAccessibilitySettings(this) }
        btnOverlay.setOnClickListener { PermissionSetupSupport.openOverlaySettings(this) }
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
        val previousView = currentStep?.let { pageFor(it) }
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
        applyMaterialCloseTransition()
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val pageColor = ContextCompat.getColor(this, DesignSystemR.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(DesignSystemR.bool.m3t_light_system_bars)
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

    private fun requestMicPermission() {
        PermissionSetupSupport.requestMicPermission(this, REQ_RECORD_AUDIO) { updatePermissionUi() }
    }

    private fun guideAll() {
        PermissionSetupSupport.guideAll(
            activity = this,
            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
            requestMicPermission = { requestMicPermission() },
            onReady = { finishWithSlideBack() },
            onUiRefresh = { updatePermissionUi() },
        )
    }

    private fun updatePermissionUi() {
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
