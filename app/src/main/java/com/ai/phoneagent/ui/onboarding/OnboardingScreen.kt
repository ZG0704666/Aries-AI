package com.ai.phoneagent.ui.onboarding

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.ai.phoneagent.PermissionSetupSupport
import com.ai.phoneagent.R
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
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
    val scope = rememberCoroutineScope()
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                when (currentStep) {
                                    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
                                    OnboardingStep.AGREEMENT -> R.string.user_agreement_title
                                    OnboardingStep.PERMISSION -> R.string.perm_sheet_title
                                },
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                modifier = Modifier.statusBarsPadding(),
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
                                    activity?.let {
                                        it.requestPermissions(
                                            arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                            101,
                                        )
                                        permissionUiState = readPermissionUiState(context)
                                    }
                                },
                                onGuideAll = {
                                    activity?.let { hostActivity ->
                                        PermissionSetupSupport.guideAll(
                                            activity = hostActivity,
                                            requestShizukuPermissionCode = REQ_SHIZUKU_PERMISSION,
                                            requestMicPermission = {
                                                hostActivity.requestPermissions(
                                                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                                    101,
                                                )
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

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacingLg),
                verticalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Text(
                    text = "${currentStep.ordinal + 1}/${OnboardingStep.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.m3t_spacing_xxl)))
        }
    }
}

@Composable
private fun WelcomePanel(
    onNext: () -> Unit,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)

    Card(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacingLg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacingMd, alignment = Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onAccept: () -> Unit,
    actionLabelRes: Int,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)
    val agreementHtml = stringResource(R.string.user_agreement_content)
    val agreementTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val agreementContainerColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    Card(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Text(
                text = stringResource(R.string.user_agreement_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.user_agreement_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val horizontalPadding = ctx.resources.getDimensionPixelSize(R.dimen.m3t_spacing_xl)
                        val verticalPadding = ctx.resources.getDimensionPixelSize(R.dimen.m3t_spacing_lg)
                        ScrollView(ctx).apply {
                            isFillViewport = true
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            setBackgroundColor(agreementContainerColor)
                            addView(
                                TextView(ctx).apply {
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
                Text(stringResource(actionLabelRes))
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    flowMode: OnboardingFlowMode,
    permissionUiState: PermissionUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenMic: () -> Unit,
    onGuideAll: () -> Unit,
    onDone: () -> Unit,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)

    Card(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacingXl, vertical = spacingLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            item {
                Text(
                    text = stringResource(R.string.perm_sheet_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacingLg),
                        horizontalArrangement = Arrangement.spacedBy(spacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Box(
                                modifier = Modifier.padding(dimensionResource(R.dimen.m3t_spacing_sm)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Lucide.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm))) {
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
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PermissionRow(
                            icon = Lucide.Smartphone,
                            title = stringResource(R.string.perm_sheet_accessibility_title),
                            description = stringResource(R.string.perm_sheet_accessibility_desc),
                            ready = permissionUiState.accessibilityReady,
                            pendingAction = stringResource(R.string.perm_sheet_action_enable),
                            onAction = onOpenAccessibility,
                            buttonHeight = compactButtonHeight,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PermissionRow(
                            icon = Lucide.ExternalLink,
                            title = stringResource(R.string.perm_sheet_overlay_title),
                            description = stringResource(R.string.perm_sheet_overlay_desc),
                            ready = permissionUiState.overlayReady,
                            pendingAction = stringResource(R.string.perm_sheet_action_settings),
                            onAction = onOpenOverlay,
                            buttonHeight = compactButtonHeight,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PermissionRow(
                            icon = Lucide.Mic,
                            title = stringResource(R.string.perm_sheet_microphone_title),
                            description = stringResource(R.string.perm_sheet_microphone_desc),
                            ready = permissionUiState.microphoneReady,
                            pendingAction = stringResource(R.string.perm_sheet_action_grant),
                            onAction = onOpenMic,
                            buttonHeight = compactButtonHeight,
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onGuideAll,
                    modifier = Modifier.fillMaxWidth().height(buttonHeight),
                ) {
                    Text(
                        stringResource(
                            if (permissionUiState.allReady) {
                                R.string.perm_sheet_primary_action_ready
                            } else {
                                R.string.perm_sheet_primary_action
                            },
                        ),
                    )
                }
            }

            if (!permissionUiState.allReady || flowMode == OnboardingFlowMode.PERMISSION_ONLY) {
                item {
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
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
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
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                shape = MaterialTheme.shapes.large,
            ) {
                Box(
                    modifier = Modifier.padding(spacingSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            if (ready) {
                                R.string.perm_sheet_status_ready
                            } else {
                                R.string.perm_sheet_status_pending
                            },
                        ),
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
            Text(
                if (ready) {
                    stringResource(R.string.perm_sheet_action_ready)
                } else {
                    pendingAction
                },
            )
        }
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
