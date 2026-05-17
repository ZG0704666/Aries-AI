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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        AriesMaterialTheme {
            if (overlayVisible) {
                MainOnboardingOverlayScreen(
                    flowMode = flowMode,
                    currentStep = currentStep,
                    agreementHtml = activity.getString(R.string.user_agreement_content),
                    permissionUiState = permissionUiState,
                    onNext = {
                        when (currentStep) {
                            Step.WELCOME -> currentStep = Step.AGREEMENT
                            Step.AGREEMENT -> {
                                appPrefs.setUserAgreementAcceptedBlocking(true)
                                markPermissionGuideShown()
                                refreshPermissionUi()
                                currentStep = Step.PERMISSION
                            }
                            Step.PERMISSION -> Unit
                        }
                    },
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onOpenOverlay = { openOverlaySettings() },
                    onOpenMic = { requestMicPermission() },
                    onGuideAll = { guideAll() },
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
        val pageColor = ContextCompat.getColor(activity, R.color.m3t_drawer_background)
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

@Composable
private fun MainOnboardingOverlayScreen(
    flowMode: MainOnboardingOverlay.FlowMode,
    currentStep: MainOnboardingOverlay.Step,
    agreementHtml: String,
    permissionUiState: MainOnboardingOverlay.PermissionUiState,
    onNext: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenMic: () -> Unit,
    onGuideAll: () -> Unit,
    onDone: () -> Unit,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXxl = dimensionResource(R.dimen.m3t_spacing_xxl)
    val progress = (currentStep.ordinal + 1) / MainOnboardingOverlay.Step.entries.size.toFloat()
    val animatedProgress by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "onboardingProgress",
        )

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        slideInHorizontally(initialOffsetX = { it / 5 }) + fadeIn() togetherWith
                            slideOutHorizontally(targetOffsetX = { -it / 6 }) + fadeOut()
                    } else {
                        slideInHorizontally(initialOffsetX = { -it / 5 }) + fadeIn() togetherWith
                            slideOutHorizontally(targetOffsetX = { it / 6 }) + fadeOut()
                    }
                },
                label = "onboardingStepContent",
            ) { step ->
                when (step) {
                    MainOnboardingOverlay.Step.WELCOME ->
                        WelcomePanel(
                            modifier = Modifier.fillMaxSize(),
                            onNext = onNext,
                        )

                    MainOnboardingOverlay.Step.AGREEMENT ->
                        AgreementPanel(
                            modifier = Modifier.fillMaxSize(),
                            agreementHtml = agreementHtml,
                            onAccept = onNext,
                        )

                    MainOnboardingOverlay.Step.PERMISSION ->
                        PermissionPanel(
                            modifier = Modifier.fillMaxSize(),
                            flowMode = flowMode,
                            permissionUiState = permissionUiState,
                            onOpenAccessibility = onOpenAccessibility,
                            onOpenOverlay = onOpenOverlay,
                            onOpenMic = onOpenMic,
                            onGuideAll = onGuideAll,
                            onDone = onDone,
                        )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Text(
                text = "${currentStep.ordinal + 1}/${MainOnboardingOverlay.Step.entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(spacingXxl))
    }
}

@Composable
private fun WelcomePanel(
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
) {
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)

    Card(
        modifier = modifier.padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingLg),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(buttonHeight),
            ) {
                Text(stringResource(R.string.onboarding_welcome_action))
            }
        }
    }
}

@Composable
private fun AgreementPanel(
    modifier: Modifier = Modifier,
    agreementHtml: String,
    onAccept: () -> Unit,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)
    val agreementTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val agreementContainerColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    Card(
        modifier = modifier.padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Text(
                text = stringResource(R.string.user_agreement_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.m3t_spacing_xl)
                        val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.m3t_spacing_lg)
                        ScrollView(context).apply {
                            isFillViewport = true
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            setBackgroundColor(agreementContainerColor)
                            addView(
                                TextView(context).apply {
                                    setTextColor(agreementTextColor)
                                    textSize = 15f
                                    setLineSpacing(0f, 1.35f)
                                    gravity = Gravity.START
                                    setBackgroundColor(agreementContainerColor)
                                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                                },
                            )
                        }
                    },
                    update = { scrollView ->
                        val textView = scrollView.getChildAt(0) as? TextView ?: return@AndroidView
                        textView.text =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Html.fromHtml(agreementHtml, Html.FROM_HTML_MODE_COMPACT)
                            } else {
                                @Suppress("DEPRECATION")
                                Html.fromHtml(agreementHtml)
                            }
                    },
                )
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(buttonHeight),
            ) {
                Text(stringResource(R.string.user_agreement_agree))
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    modifier: Modifier = Modifier,
    flowMode: MainOnboardingOverlay.FlowMode,
    permissionUiState: MainOnboardingOverlay.PermissionUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenMic: () -> Unit,
    onGuideAll: () -> Unit,
    onDone: () -> Unit,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)

    Card(
        modifier = modifier.padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Text(
                text = stringResource(R.string.perm_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacingLg),
                    horizontalArrangement = Arrangement.spacedBy(spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = CircleShape) {
                        Box(modifier = Modifier.padding(spacingSm), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                        Text(
                            text = stringResource(R.string.perm_sheet_shizuku_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.perm_sheet_shizuku_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PermissionRow(Lucide.Smartphone, stringResource(R.string.perm_sheet_accessibility_title), stringResource(R.string.perm_sheet_accessibility_desc), permissionUiState.accessibilityReady, stringResource(R.string.perm_sheet_action_enable), onOpenAccessibility, compactButtonHeight)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(Lucide.ExternalLink, stringResource(R.string.perm_sheet_overlay_title), stringResource(R.string.perm_sheet_overlay_desc), permissionUiState.overlayReady, stringResource(R.string.perm_sheet_action_settings), onOpenOverlay, compactButtonHeight)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(Lucide.Mic, stringResource(R.string.perm_sheet_microphone_title), stringResource(R.string.perm_sheet_microphone_desc), permissionUiState.microphoneReady, stringResource(R.string.perm_sheet_action_grant), onOpenMic, compactButtonHeight)
                }
            }

            Button(
                onClick = onGuideAll,
                modifier = Modifier.fillMaxWidth().height(buttonHeight),
            ) {
                Text(stringResource(if (permissionUiState.allReady) R.string.perm_sheet_primary_action_ready else R.string.perm_sheet_primary_action))
            }

            if (!permissionUiState.allReady || flowMode == MainOnboardingOverlay.FlowMode.PERMISSION_ONLY) {
                FilledTonalButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(buttonHeight),
                ) {
                    Text(stringResource(R.string.perm_sheet_secondary_action))
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    ready: Boolean,
    pendingAction: String,
    onAction: () -> Unit,
    buttonHeight: Dp,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    Column(
        modifier = Modifier.fillMaxWidth().padding(spacingLg),
        verticalArrangement = Arrangement.spacedBy(spacingMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), shape = CircleShape) {
                Box(modifier = Modifier.padding(spacingSm), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(if (ready) R.string.perm_sheet_status_ready else R.string.perm_sheet_status_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalButton(
            onClick = onAction,
            enabled = !ready,
            modifier = Modifier.fillMaxWidth().height(buttonHeight),
        ) {
            Text(if (ready) stringResource(R.string.perm_sheet_action_ready) else pendingAction)
        }
    }
}
