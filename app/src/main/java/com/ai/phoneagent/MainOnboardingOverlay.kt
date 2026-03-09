package com.ai.phoneagent

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton

class MainOnboardingOverlay(
    private val activity: AppCompatActivity,
) {
    private enum class FlowMode {
        ONBOARDING,
        PERMISSION_ONLY,
    }

    private enum class Step {
        WELCOME,
        AGREEMENT,
        PERMISSION,
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_USER_AGREEMENT_ACCEPTED = "user_agreement_accepted"
        private const val KEY_PERMISSION_GUIDE_SHOWN = "perm_guide_shown"
    }

    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawerLayout)

    private val hostRoot = activity.findViewById<View>(R.id.onboardingHost)
    private val welcomePage = activity.findViewById<View>(R.id.pageWelcome)
    private val agreementPage = activity.findViewById<View>(R.id.pageAgreement)
    private val permissionPage = activity.findViewById<View>(R.id.pagePermission)

    private val btnWelcomeNext = welcomePage.findViewById<MaterialButton>(R.id.btnWelcomeNext)
    private val btnAgreementAgree = agreementPage.findViewById<MaterialButton>(R.id.btnAgreementAgree)

    private val permissionHeader = permissionPage.findViewById<View>(R.id.permissionSheetHeader)
    private val permissionActions = permissionPage.findViewById<View>(R.id.permissionSheetActions)
    private val tvAccStatus = permissionPage.findViewById<TextView>(R.id.tvPermAccStatus)
    private val tvOverlayStatus = permissionPage.findViewById<TextView>(R.id.tvPermOverlayStatus)
    private val tvMicStatus = permissionPage.findViewById<TextView>(R.id.tvPermMicStatus)
    private val btnAcc = permissionPage.findViewById<MaterialButton>(R.id.btnPermAcc)
    private val btnOverlay = permissionPage.findViewById<MaterialButton>(R.id.btnPermOverlay)
    private val btnMic = permissionPage.findViewById<MaterialButton>(R.id.btnPermMic)
    private val btnGuide = permissionPage.findViewById<MaterialButton>(R.id.btnPermGuide)
    private val btnDone = permissionPage.findViewById<MaterialButton>(R.id.btnPermDone)

    private var flowMode = FlowMode.ONBOARDING
    private var currentStep: Step? = null
    private var isTransitionRunning = false

    init {
        configureAgreementPage()
        configurePermissionPage()
        applyWindowInsets()
        hostRoot.setOnClickListener { }
        hostRoot.isClickable = false
        hostRoot.isFocusable = false
        hostRoot.isFocusableInTouchMode = false
        hostRoot.isVisible = false
        setupBackBehavior()
    }

    fun showOnboarding() {
        if (hostRoot.isVisible && flowMode == FlowMode.ONBOARDING) return
        flowMode = FlowMode.ONBOARDING
        btnAgreementAgree.text = activity.getString(R.string.user_agreement_action_next)
        showOverlay(Step.WELCOME)
    }

    fun showPermissionOnlyIfNeeded() {
        if (!prefs.getBoolean(KEY_USER_AGREEMENT_ACCEPTED, false)) return
        if (prefs.getBoolean(KEY_PERMISSION_GUIDE_SHOWN, false)) return
        flowMode = FlowMode.PERMISSION_ONLY
        showOverlay(Step.PERMISSION)
    }

    fun onResume() {
        if (hostRoot.isVisible && currentStep == Step.PERMISSION) {
            updatePermissionUi()
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != REQ_RECORD_AUDIO) return false
        updatePermissionUi()
        return true
    }

    fun isShowing(): Boolean = hostRoot.isVisible

    private fun showOverlay(initialStep: Step) {
        closeDrawerIfOpen(immediate = true)
        hostRoot.isClickable = true
        hostRoot.isFocusable = true
        hostRoot.isFocusableInTouchMode = true
        hostRoot.isVisible = true
        hostRoot.bringToFront()
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        applyOverlaySystemBars()
        showStep(initialStep, forward = true, animate = false)
    }

    private fun hideOverlay() {
        hostRoot.clearFocus()
        hostRoot.isClickable = false
        hostRoot.isFocusable = false
        hostRoot.isFocusableInTouchMode = false
        hostRoot.isVisible = false
        isTransitionRunning = false
        currentStep = null
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        closeDrawerIfOpen(immediate = true)
        restoreMainSystemBars()
    }

    private fun configureAgreementPage() {
        val contentView = agreementPage.findViewById<TextView>(R.id.tvAgreementContent)
        val content = activity.getString(R.string.user_agreement_content)
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
        btnAgreementAgree.setOnClickListener {
            prefs.edit().putBoolean(KEY_USER_AGREEMENT_ACCEPTED, true).apply()
            showStep(Step.PERMISSION, forward = true, animate = true)
        }
    }

    private fun configurePermissionPage() {
        btnAcc.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnMic.setOnClickListener { requestMicPermission() }
        btnGuide.setOnClickListener { guideAll() }
        btnDone.setOnClickListener { hideOverlay() }
    }

    private fun setupBackBehavior() {
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (closeDrawerIfOpen()) return
                    if (!hostRoot.isVisible) {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                        return
                    }
                    if (isTransitionRunning) return

                    when (flowMode) {
                        FlowMode.PERMISSION_ONLY -> hideOverlay()
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

    private fun closeDrawerIfOpen(immediate: Boolean = false): Boolean {
        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) return false
        drawerLayout.closeDrawer(GravityCompat.START, !immediate)
        return true
    }

    private fun showStep(target: Step, forward: Boolean, animate: Boolean) {
        if (currentStep == target || isTransitionRunning) return

        val targetView = pageFor(target)
        val previousView = currentStep?.let(::pageFor)

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
            prefs.edit().putBoolean(KEY_PERMISSION_GUIDE_SHOWN, true).apply()
            updatePermissionUi()
        }
    }
    private fun pageFor(step: Step): View =
        when (step) {
            Step.WELCOME -> welcomePage
            Step.AGREEMENT -> agreementPage
            Step.PERMISSION -> permissionPage
        }

    private fun applyOverlaySystemBars() {
        val pageColor = ContextCompat.getColor(activity, R.color.m3t_drawer_background)
        val useLightSystemBarIcons = activity.resources.getBoolean(R.bool.m3t_light_system_bars)
        activity.window.statusBarColor = pageColor
        activity.window.navigationBarColor = pageColor
        activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun restoreMainSystemBars() {
        val useLightSystemBarIcons = activity.resources.getBoolean(R.bool.m3t_light_system_bars)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let {
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

    private fun updatePermissionUi() {
        val accOk = PermissionSetupSupport.isAccessibilityEnabled(activity)
        updatePermissionRow(tvAccStatus, btnAcc, accOk, R.string.perm_sheet_action_enable)

        val overlayOk = PermissionSetupSupport.hasOverlayPermission(activity)
        updatePermissionRow(tvOverlayStatus, btnOverlay, overlayOk, R.string.perm_sheet_action_settings)

        val micOk =
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        updatePermissionRow(tvMicStatus, btnMic, micOk, R.string.perm_sheet_action_grant)

        val allOk = accOk && overlayOk && micOk
        btnGuide.text =
            activity.getString(
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
            activity = activity,
            statusView = statusView,
            actionButton = actionButton,
            ready = ready,
            pendingActionText = pendingActionText,
        )
    }

    private fun openAccessibilitySettings() {
        PermissionSetupSupport.openAccessibilitySettings(activity)
    }

    private fun openOverlaySettings() {
        PermissionSetupSupport.openOverlaySettings(activity)
    }

    private fun requestMicPermission() {
        val granted =
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            updatePermissionUi()
            return
        }
        activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun guideAll() {
        PermissionSetupSupport.guideAll(
            activity = activity,
            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
            requestMicPermission = { requestMicPermission() },
            onReady = { hideOverlay() },
            onUiRefresh = { updatePermissionUi() },
        )
    }
}
