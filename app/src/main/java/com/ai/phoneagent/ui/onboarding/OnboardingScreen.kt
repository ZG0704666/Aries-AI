package com.ai.phoneagent.ui.onboarding

import com.ai.phoneagent.ui.components.AriesPageTopBar
import com.ai.phoneagent.core.designsystem.theme.AriesGlassCard
import com.ai.phoneagent.core.designsystem.theme.AriesGlassButton
import com.ai.phoneagent.core.designsystem.R as DesignR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.AlarmClock
import com.composables.icons.lucide.LockKeyhole

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.PermissionSetupSupport
import com.ai.phoneagent.R
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Smartphone
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val FLOW_ONBOARDING = "onboarding"
private const val FLOW_VIEW_ONLY = "view_only"
private const val FLOW_PERMISSION_ONLY = "permission_only"
private const val REQ_SHIZUKU_PERMISSION = 2026

enum class OnboardingFlowMode {
    ONBOARDING,
    VIEW_ONLY,
    PERMISSION_ONLY,
}

private enum class OnboardingStep {
    WELCOME,
    AGREEMENT,
    PERMISSION,
}

private data class PermissionUiState(
    val accessibilityReady: Boolean,
    val overlayReady: Boolean,
    val microphoneReady: Boolean,
) {
    val allReady: Boolean
        get() = accessibilityReady && overlayReady && microphoneReady
}

fun parseOnboardingFlowMode(raw: String?): OnboardingFlowMode =
    when (raw) {
        FLOW_VIEW_ONLY -> OnboardingFlowMode.VIEW_ONLY
        FLOW_PERMISSION_ONLY -> OnboardingFlowMode.PERMISSION_ONLY
        else -> OnboardingFlowMode.ONBOARDING
    }

@Composable
fun OnboardingRoute(
    navController: NavController,
    flow: String?,
    appPreferencesRepository: AppPreferencesRepository = koinInject(),
) {
    OnboardingScreen(
        flowMode = parseOnboardingFlowMode(flow),
        appPreferencesRepository = appPreferencesRepository,
        onBack = { navController.popBackStack() },
        onDone = { navController.popBackStack() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    flowMode: OnboardingFlowMode,
    appPreferencesRepository: AppPreferencesRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findAppCompatActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)

    var currentStep by
        rememberSaveable(flowMode) {
            mutableStateOf(
                when (flowMode) {
                    OnboardingFlowMode.ONBOARDING -> OnboardingStep.WELCOME
                    OnboardingFlowMode.VIEW_ONLY -> OnboardingStep.AGREEMENT
                    OnboardingFlowMode.PERMISSION_ONLY -> OnboardingStep.PERMISSION
                },
            )
        }
    var permissionUiState by remember { mutableStateOf(readPermissionUiState(context)) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        permissionUiState = readPermissionUiState(context)
        if (!isGranted) {
            activity?.let {
                PermissionSetupSupport.handleMicPermissionResult(
                    activity = it,
                    grantResults = intArrayOf(android.content.pm.PackageManager.PERMISSION_DENIED),
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner, currentStep, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentStep == OnboardingStep.PERMISSION) {
                permissionUiState = readPermissionUiState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentStep, flowMode) {
        if (currentStep == OnboardingStep.PERMISSION && flowMode != OnboardingFlowMode.VIEW_ONLY) {
            appPreferencesRepository.setPermGuideShown(true)
            permissionUiState = readPermissionUiState(context)
        }
    }

    BackHandler {
        when (flowMode) {
            OnboardingFlowMode.VIEW_ONLY,
            OnboardingFlowMode.PERMISSION_ONLY,
            -> onBack()

            OnboardingFlowMode.ONBOARDING -> {
                currentStep =
                    when (currentStep) {
                        OnboardingStep.PERMISSION -> OnboardingStep.AGREEMENT
                        OnboardingStep.AGREEMENT -> OnboardingStep.WELCOME
                        OnboardingStep.WELCOME -> OnboardingStep.WELCOME
                    }
            }
        }
    }

    val progress = (currentStep.ordinal + 1) / OnboardingStep.entries.size.toFloat()
    val animatedProgress by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "onboardingProgress",
        )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AriesPageTopBar(
                title = stringResource(when (currentStep) {
                    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
                    OnboardingStep.AGREEMENT -> R.string.user_agreement_title
                    OnboardingStep.PERMISSION -> R.string.perm_sheet_title
                }),
                onBack = if (flowMode == OnboardingFlowMode.ONBOARDING && currentStep == OnboardingStep.WELCOME) {
                    null
                } else {
                    {
                        if (flowMode != OnboardingFlowMode.ONBOARDING) onBack()
                        else currentStep = OnboardingStep.entries[currentStep.ordinal - 1]
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            slideInHorizontally(initialOffsetX = { it / 5 }, animationSpec = tween(280)) +
                                fadeIn(animationSpec = tween(240)) togetherWith
                                slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = tween(240)) +
                                fadeOut(animationSpec = tween(220))
                        } else {
                            slideInHorizontally(initialOffsetX = { -it / 5 }, animationSpec = tween(280)) +
                                fadeIn(animationSpec = tween(240)) togetherWith
                                slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = tween(240)) +
                                fadeOut(animationSpec = tween(220))
                        }
                    },
                    label = "onboardingStepContent",
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> {
                            WelcomePanel(
                                onNext = { currentStep = OnboardingStep.AGREEMENT },
                            )
                        }

                        OnboardingStep.AGREEMENT -> {
                            AgreementPanel(
                                requireAcceptance = flowMode != OnboardingFlowMode.VIEW_ONLY,
                                onAccept = {
                                    if (flowMode == OnboardingFlowMode.VIEW_ONLY) {
                                        onDone()
                                    } else {
                                        scope.launch {
                                            appPreferencesRepository.setUserAgreementAccepted(true)
                                            appPreferencesRepository.setPermGuideShown(true)
                                            permissionUiState = readPermissionUiState(context)
                                            currentStep = OnboardingStep.PERMISSION
                                        }
                                    }
                                },
                                actionLabelRes =
                                    if (flowMode == OnboardingFlowMode.VIEW_ONLY) {
                                        R.string.action_close
                                    } else {
                                        R.string.user_agreement_action_next
                                    },
                            )
                        }

                        OnboardingStep.PERMISSION -> {
                            PermissionPanel(
                                flowMode = flowMode,
                                permissionUiState = permissionUiState,
                                onOpenAccessibility = {
                                    activity?.let { PermissionSetupSupport.openAccessibilitySettings(it) }
                                },
                                onOpenOverlay = {
                                    activity?.let { PermissionSetupSupport.openOverlaySettings(it) }
                                },
                                onOpenMic = {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                },
                                onGuideAll = {
                                    activity?.let { hostActivity ->
                                        PermissionSetupSupport.guideAll(
                                            activity = hostActivity,
                                            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
                                            requestMicPermission = {
                                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            },
                                            onReady = {
                                                permissionUiState = readPermissionUiState(context)
                                                onDone()
                                            },
                                            onUiRefresh = {
                                                permissionUiState = readPermissionUiState(context)
                                            },
                                        )
                                    }
                                },
                                onDone = onDone,
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun OnboardingStepCaption(step: Int) {
    Text(stringResource(R.string.ui_v2_step_caption, step), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomePanel(onNext: () -> Unit) {
    val padding = dimensionResource(DesignR.dimen.aries_page_padding)
    val gap = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    val density = LocalDensity.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(padding)) {
        item {
            val shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_card_radius))
            Box(Modifier.padding(top = padding).size(dimensionResource(DesignR.dimen.aries_onboarding_brand))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), shape), contentAlignment = Alignment.Center) {
                Icon(Lucide.Sparkles, null, Modifier.size(dimensionResource(DesignR.dimen.aries_brand_icon)), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        item { Text(stringResource(R.string.ui_v2_welcome_heading), style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = with(density) { dimensionResource(DesignR.dimen.aries_type_headline).toSp() }, fontWeight = FontWeight.Bold)) }
        item { Text(stringResource(R.string.ui_v2_welcome_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            AriesGlassCard(Modifier.fillMaxWidth()) {
                listOf(Lucide.MessageCircle to R.string.ui_v2_welcome_chat, Lucide.ScanLine to R.string.ui_v2_welcome_automation,
                    Lucide.AlarmClock to R.string.ui_v2_welcome_control).forEach { (icon, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Icon(icon, null, Modifier.size(dimensionResource(DesignR.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { AriesGlassCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_v2_welcome_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(dimensionResource(DesignR.dimen.m3t_button_height)),
            shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius))) { Text(stringResource(R.string.onboarding_welcome_action)) } }
        item { OnboardingStepCaption(1) }
    }
}

@Composable
internal fun AgreementPanel(onAccept: () -> Unit, actionLabelRes: Int, requireAcceptance: Boolean = true) {
    val padding = dimensionResource(DesignR.dimen.aries_page_padding)
    val gap = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    var accepted by rememberSaveable { mutableStateOf(false) }
    var showFull by rememberSaveable { mutableStateOf(false) }
    val agreementHtml = stringResource(R.string.user_agreement_content)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    if (showFull) {
        AlertDialog(onDismissRequest = { showFull = false }, title = { Text(stringResource(R.string.user_agreement_title)) },
            text = {
                AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
                    ScrollView(ctx).apply {
                        addView(TextView(ctx).apply {
                            textSize = ctx.resources.getDimension(DesignR.dimen.aries_type_body) / ctx.resources.displayMetrics.scaledDensity
                            setLineSpacing(0f, 1.35f)
                            gravity = Gravity.START
                        })
                    }
                }, update = { scroll ->
                    (scroll.getChildAt(0) as TextView).apply {
                        setTextColor(textColor)
                        text = Html.fromHtml(agreementHtml, Html.FROM_HTML_MODE_COMPACT)
                    }
                })
            }, confirmButton = { TextButton(onClick = { showFull = false }) { Text(stringResource(R.string.action_close)) } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(padding)) {
        item { Text(stringResource(R.string.ui_v2_agreement_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.ui_v2_agreement_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        listOf(Triple(Lucide.Shield, R.string.user_agreement_title, R.string.ui_v2_agreement_rules),
            Triple(Lucide.LockKeyhole, R.string.ui_v2_privacy_title, R.string.ui_v2_privacy_description),
            Triple(Lucide.Sparkles, R.string.ui_v2_ai_content, R.string.ui_v2_ai_content_description)).forEach { (icon, title, desc) ->
            item {
                AriesGlassCard(Modifier.fillMaxWidth().clickable { showFull = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Icon(icon, null, Modifier.size(dimensionResource(DesignR.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { TextButton(onClick = { showFull = true }) { Text(stringResource(R.string.ui_v2_agreement_full)) } }
        if (requireAcceptance) item {
            AriesGlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { accepted = !accepted }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text(stringResource(R.string.ui_v2_agreement_checked), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Button(onClick = onAccept, enabled = !requireAcceptance || accepted,
            shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius)),
            modifier = Modifier.fillMaxWidth().height(dimensionResource(DesignR.dimen.m3t_button_height))) { Text(stringResource(actionLabelRes)) } }
        if (requireAcceptance) item { OnboardingStepCaption(2) }
    }
}

@Composable
private fun PermissionPanel(flowMode: OnboardingFlowMode, permissionUiState: PermissionUiState,
    onOpenAccessibility: () -> Unit, onOpenOverlay: () -> Unit, onOpenMic: () -> Unit, onGuideAll: () -> Unit, onDone: () -> Unit) {
    val padding = dimensionResource(DesignR.dimen.aries_page_padding)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignR.dimen.aries_setting_gap))) {
        item { Text(stringResource(R.string.ui_v2_permission_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.ui_v2_permission_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { PermissionRow(Lucide.Smartphone, stringResource(R.string.perm_sheet_accessibility_title), stringResource(R.string.perm_sheet_accessibility_desc), permissionUiState.accessibilityReady, stringResource(R.string.perm_sheet_action_enable), onOpenAccessibility) }
        item { PermissionRow(Lucide.ExternalLink, stringResource(R.string.perm_sheet_overlay_title), stringResource(R.string.perm_sheet_overlay_desc), permissionUiState.overlayReady, stringResource(R.string.perm_sheet_action_settings), onOpenOverlay) }
        item { PermissionRow(Lucide.Mic, stringResource(R.string.perm_sheet_microphone_title), stringResource(R.string.perm_sheet_microphone_desc), permissionUiState.microphoneReady, stringResource(R.string.perm_sheet_action_grant), onOpenMic) }
        item { AriesGlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.perm_sheet_shizuku_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.perm_sheet_shizuku_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { OnboardingButtonRow(
            flowMode = flowMode,
            allReady = permissionUiState.allReady,
            onGuideAll = onGuideAll,
            onDone = onDone,
        ) }
        item { OnboardingStepCaption(3) }
    }
}

/** 权限页底部按钮行：主/次按钮同宽同高；主按钮粉色实心，次按钮浅灰描边玻璃。 */
@Composable
private fun OnboardingButtonRow(flowMode: OnboardingFlowMode, allReady: Boolean, onGuideAll: () -> Unit, onDone: () -> Unit) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val buttonHeight = dimensionResource(DesignR.dimen.m3t_button_height)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onGuideAll,
            shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius)),
            modifier = Modifier.weight(1f).height(buttonHeight),
        ) {
            Text(stringResource(if (allReady) R.string.perm_sheet_primary_action_ready else R.string.perm_sheet_primary_action), maxLines = 1)
        }
        if (!allReady || flowMode == OnboardingFlowMode.PERMISSION_ONLY) {
            AriesGlassButton(onClick = onDone, modifier = Modifier.weight(1f).height(buttonHeight)) {
                Text(stringResource(R.string.perm_sheet_secondary_action), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, description: String, ready: Boolean, pendingAction: String, onAction: () -> Unit) {
    val gap = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    AriesGlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Icon(icon, null, Modifier.size(dimensionResource(DesignR.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(if (ready) R.string.perm_sheet_status_ready else R.string.perm_sheet_status_pending), style = MaterialTheme.typography.labelSmall,
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, enabled = !ready) { Text(if (ready) stringResource(R.string.perm_sheet_action_ready) else pendingAction) }
    }
}

private fun readPermissionUiState(context: Context): PermissionUiState {
    val accessibilityReady = PermissionSetupSupport.isAccessibilityEnabled(context)
    val overlayReady = PermissionSetupSupport.hasOverlayPermission(context)
    val microphoneReady =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    return PermissionUiState(
        accessibilityReady = accessibilityReady,
        overlayReady = overlayReady,
        microphoneReady = microphoneReady,
    )
}

private tailrec fun Context.findAppCompatActivity(): AppCompatActivity? =
    when (this) {
        is AppCompatActivity -> this
        is ContextWrapper -> baseContext.findAppCompatActivity()
        else -> null
    }
