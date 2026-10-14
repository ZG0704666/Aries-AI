package com.ai.phoneagent

import android.os.Build
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.data.preferences.AppPreferencesRepository

class MainOnboardingOverlay(
    private val activity: AppCompatActivity,
    private val appPrefs: AppPreferencesRepository,
) {
    enum class FlowMode {
        ONBOARDING,
        PERMISSION_ONLY,
    }

    enum class Step {
        WELCOME,
        AGREEMENT,
        PERMISSION,
    }

    data class PermissionUiState(
        val accessibilityReady: Boolean,
        val overlayReady: Boolean,
        val microphoneReady: Boolean,
    ) {
        val allReady: Boolean
            get() = accessibilityReady && overlayReady && microphoneReady
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 101
        private const val REQ_SHIZUKU_PERMISSION = 2026
    }

    private var overlayVisible by mutableStateOf(false)
    private var flowMode by mutableStateOf(FlowMode.ONBOARDING)
    private var currentStep by mutableStateOf(Step.WELCOME)
    private var permissionUiState by mutableStateOf(readPermissionUiState())

    init { setupBackBehavior() }

    @Composable
    fun Render() {
        if (overlayVisible) {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                com.ai.phoneagent.ui.onboarding.OnboardingScreen(
                    flowMode = if (flowMode == FlowMode.ONBOARDING)
                        com.ai.phoneagent.ui.onboarding.OnboardingFlowMode.ONBOARDING
                    else com.ai.phoneagent.ui.onboarding.OnboardingFlowMode.PERMISSION_ONLY,
                    appPreferencesRepository = appPrefs,
                    onBack = { if (flowMode == FlowMode.PERMISSION_ONLY) hideOverlay() },
                    onDone = { hideOverlay() },
                )
            }
        }
    }

    fun showOnboarding() {
        if (overlayVisible && flowMode == FlowMode.ONBOARDING) return
        flowMode = FlowMode.ONBOARDING
        showOverlay(Step.WELCOME)
    }

    fun showPermissionOnlyIfNeeded() {
        if (!appPrefs.getUserAgreementAcceptedBlocking()) return
        if (appPrefs.getPermGuideShownBlocking()) return
        flowMode = FlowMode.PERMISSION_ONLY
        showOverlay(Step.PERMISSION)
    }

    fun onResume() {
        if (overlayVisible) refreshPermissionUi()
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != REQ_RECORD_AUDIO) return false
        refreshPermissionUi()
        PermissionSetupSupport.handleMicPermissionResult(activity, grantResults)
        return true
    }

    fun isShowing(): Boolean = overlayVisible

    private fun showOverlay(initialStep: Step) {
        overlayVisible = true
        currentStep = initialStep
        applyOverlaySystemBars()
        if (initialStep == Step.PERMISSION) {
            markPermissionGuideShown()
            refreshPermissionUi()
        }
    }

    private fun hideOverlay() {
        overlayVisible = false
        restoreMainSystemBars()
    }

    private fun markPermissionGuideShown() {
        appPrefs.setPermGuideShownBlocking(true)
    }

    private fun refreshPermissionUi() {
        permissionUiState = readPermissionUiState()
    }

    private fun readPermissionUiState(): PermissionUiState {
        val accessibilityReady = PermissionSetupSupport.isAccessibilityEnabled(activity)
        val overlayReady = PermissionSetupSupport.hasOverlayPermission(activity)
        val microphoneReady =
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return PermissionUiState(accessibilityReady, overlayReady, microphoneReady)
    }

    private fun setupBackBehavior() {
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!overlayVisible) {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                        return
                    }

                    when (flowMode) {
                        FlowMode.PERMISSION_ONLY -> hideOverlay()
                        FlowMode.ONBOARDING -> {
                            currentStep =
                                when (currentStep) {
                                    Step.PERMISSION -> Step.AGREEMENT
                                    Step.AGREEMENT -> Step.WELCOME
                                    Step.WELCOME -> Step.WELCOME
                                }
                        }
                    }
                }
            },
        )
    }

    private fun applyOverlaySystemBars() {
        val pageColor = ContextCompat.getColor(activity, com.ai.phoneagent.core.designsystem.R.color.m3t_drawer_background)
        val useLightSystemBarIcons = activity.resources.getBoolean(R.bool.m3t_light_system_bars)
        activity.window.statusBarColor = pageColor
        activity.window.navigationBarColor = pageColor
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun restoreMainSystemBars() {
        val useLightSystemBarIcons = activity.resources.getBoolean(R.bool.m3t_light_system_bars)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun openAccessibilitySettings() {
        PermissionSetupSupport.openAccessibilitySettings(activity)
    }

    private fun openOverlaySettings() {
        PermissionSetupSupport.openOverlaySettings(activity)
    }

    private fun requestMicPermission() {
        PermissionSetupSupport.requestMicPermission(activity, REQ_RECORD_AUDIO) {
            refreshPermissionUi()
        }
    }

    private fun guideAll() {
        PermissionSetupSupport.guideAll(
            activity = activity,
            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
            requestMicPermission = { requestMicPermission() },
            onReady = {
                refreshPermissionUi()
                hideOverlay()
            },
            onUiRefresh = { refreshPermissionUi() },
        )
    }
}
