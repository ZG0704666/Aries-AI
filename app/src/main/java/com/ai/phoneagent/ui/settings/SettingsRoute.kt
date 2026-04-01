package com.ai.phoneagent.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.feature.settings.R as SettingsR
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.ui.AboutScreen
import com.ai.phoneagent.ui.automation.AutomationControlScreen
import com.ai.phoneagent.viewmodel.AboutViewModel
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshLocalModelState()
                    viewModel.restoreSettings()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isSubPage = viewModel.currentPage != SettingsViewModel.SettingsPage.Home
    if (isSubPage) {
        BackHandler {
            viewModel.openHomePage()
        }
    }

    AnimatedContent(
        targetState = viewModel.currentPage,
        transitionSpec = {
            if (viewModel.pageTransitionForward) {
                slideInHorizontally(
                    animationSpec = tween(260),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(220)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(260),
                        targetOffsetX = { -it },
                    ) + fadeOut(animationSpec = tween(220))
            } else {
                slideInHorizontally(
                    animationSpec = tween(260),
                    initialOffsetX = { -it },
                ) + fadeIn(animationSpec = tween(220)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(260),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(220))
            }
        },
        label = "settingsPageTransition",
    ) { page ->
        when (page) {
            SettingsViewModel.SettingsPage.Home -> {
                DrawerSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Appearance) },
                    onOpenModelApi = { viewModel.openModelApiPage() },
                    onOpenAutomation = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Automation) },
                    onOpenAbout = { viewModel.navigateTo(SettingsViewModel.SettingsPage.About) },
                )
            }

            SettingsViewModel.SettingsPage.ModelApi -> {
                DrawerModelApiConfigScreen(
                    apiInput = viewModel.apiInputText,
                    useThirdPartyApi = viewModel.useThirdPartyApi,
                    useLocalModel = viewModel.useLocalModel,
                    apiBaseUrl = viewModel.apiBaseUrlText,
                    apiModel = viewModel.apiModelText,
                    apiStatus = viewModel.apiStatusText,
                    apiStatusPositive = viewModel.apiStatusPositive,
                    qwenButtonText = viewModel.qwenButtonText,
                    qwenButtonEnabled = viewModel.qwenButtonEnabled,
                    onBack = { viewModel.openHomePage() },
                    onApiInputChange = { value -> viewModel.onApiInputChanged(value) },
                    onPasteApi = {
                        viewModel.pasteApiKey(context) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenApiKeyPage = { viewModel.openApiKeyPage(context) },
                    onUseThirdPartyChange = { checked -> viewModel.onUseThirdPartyChange(checked) },
                    onApiBaseUrlChange = { value -> viewModel.onApiBaseUrlChange(value) },
                    onApiModelChange = { value -> viewModel.onApiModelChange(value) },
                    onUseLocalModelChange = { checked ->
                        viewModel.onUseLocalModelChange(checked) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onCheckApi = {
                        viewModel.checkApiConnection { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onDownloadQwenModel = {
                        viewModel.enqueueQwenDownloads { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

            SettingsViewModel.SettingsPage.Appearance -> {
                AppearanceScreen(
                    onNavigateBack = { viewModel.openHomePage() }
                )
            }

            SettingsViewModel.SettingsPage.About -> {
                SettingsAboutContent(
                    onBack = { viewModel.openHomePage() },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }

            SettingsViewModel.SettingsPage.Automation -> {
                SettingsAutomationContent(
                    onBack = { viewModel.openHomePage() },
                )
            }
        }
    }
}

// ── About content embedded in Settings ───────────────────────────────────────

@Composable
private fun SettingsAboutContent(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val vibrateLight = {
        try {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        } catch (_: Throwable) {}
    }

    AboutScreen(
        appVersionText = uiState.appVersionText,
        promptVersionText = uiState.promptVersionText,
        checkUpdateButtonText = uiState.checkUpdateButtonText,
        onBack = {
            vibrateLight()
            onBack()
        },
        onCheckUpdate = {
            vibrateLight()
            viewModel.checkForUpdates()
        },
        onOpenChangelog = {
            vibrateLight()
            onNavigate(Routes.UpdateHistory.route)
        },
        onOpenUserAgreement = {
            vibrateLight()
            onNavigate(Routes.UserAgreement.route)
        },
        onOpenLicenses = {
            vibrateLight()
            onNavigate(Routes.Licenses.route)
        },
        onOpenWebsite = {
            vibrateLight()
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(context.getString(SettingsR.string.about_website_url)),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(context, SettingsR.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onOpenSourceCode = {
            vibrateLight()
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(context.getString(SettingsR.string.about_source_code_url)),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(context, SettingsR.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onCopyContact = {
            vibrateLight()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("text", "zhangyongqi@njit.edu.cn"))
            Toast.makeText(context, SettingsR.string.about_contact_copied, Toast.LENGTH_SHORT).show()
        },
        onDeveloperTap = {
            vibrateLight()
            viewModel.handleDeveloperTap()
        },
    )

    uiState.updateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(
                    stringResource(SettingsR.string.m3t_updates_found) + " ${state.entry.versionTag}",
                )
            },
            text = {
                Text(state.entry.body.ifBlank { stringResource(SettingsR.string.m3t_updates_no_changelog) })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        viewModel.handleDownload(state.entry)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_check_updates))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.errorDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            title = { Text(stringResource(SettingsR.string.about_check_failed)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                    Text(stringResource(SettingsR.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissErrorDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.upToDateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpToDateDialog() },
            title = { Text(stringResource(SettingsR.string.about_up_to_date)) },
            text = {
                Text(
                    stringResource(SettingsR.string.about_current_version_format, state.currentVersion),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpToDateDialog() }) {
                    Text(stringResource(SettingsR.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpToDateDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.downloadOptionsDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadOptionsDialog() },
            title = { Text(stringResource(SettingsR.string.m3t_updates_choose_source)) },
            text = {
                Column {
                    state.options.forEach { option ->
                        TextButton(
                            onClick = {
                                viewModel.dismissDownloadOptionsDialog()
                                viewModel.openReleaseUrlWithFeedback(option.second)
                            },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        ) {
                            Text(option.first)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDownloadOptionsDialog() }) {
                    Text(stringResource(SettingsR.string.action_cancel))
                }
            },
        )
    }
}

// ── Automation content embedded in Settings ───────────────────────────────────

@Composable
private fun SettingsAutomationContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = remember(context) {
        var ctx: Context = context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }
    // Scope to Activity so it shares the same instance as AutomationScreen
    val activityOwner = hostActivity as? androidx.lifecycle.ViewModelStoreOwner
    val viewModel: AutomationViewModel = koinViewModel(
        viewModelStoreOwner = activityOwner
            ?: androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
    )

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onAudioPermissionResult(granted)
        }

    DisposableEffect(hostActivity) {
        viewModel.attachHostActivity(hostActivity)
        onDispose {
            viewModel.attachHostActivity(null)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                    Lifecycle.Event.ON_STOP -> viewModel.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AutomationControlScreen(
        statusText = viewModel.statusText,
        statusTone = viewModel.statusTone(),
        isBackgroundMode = viewModel.isBackgroundMode,
        virtualDisplayStatus = viewModel.virtualDisplayStatus,
        useShizukuInteraction = viewModel.useShizukuInteraction,
        autoApprove = viewModel.autoApprove,
        isListening = viewModel.isListening,
        taskText = viewModel.taskText,
        taskHint = viewModel.taskHint,
        recommendText = viewModel.recommendText,
        logText = viewModel.logText,
        showShizukuAuthorize = viewModel.showShizukuAuthorize,
        startButtonText = viewModel.startButtonText,
        startButtonEnabled = viewModel.startButtonEnabled,
        startButtonTerminateStyle = viewModel.startButtonTerminateStyle,
        pauseButtonText = viewModel.pauseButtonText,
        pauseButtonEnabled = viewModel.pauseButtonEnabled,
        stopButtonEnabled = viewModel.stopButtonEnabled,
        onBack = onBack,
        onOpenAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onAuthorizeShizuku = { viewModel.authorizeShizukuAndAccessibility() },
        onRefreshStatus = { viewModel.onRefreshStatus() },
        onExecutionModeChange = { viewModel.onExecutionModeChange(it) },
        onShizukuModeChange = { viewModel.onShizukuModeChange(it) },
        onAutoApproveChange = { viewModel.onAutoApproveChange(it) },
        onTaskChange = { viewModel.onTaskChange(it) },
        onVoiceTask = {
            val needPermission = viewModel.onVoiceTaskClick(viewModel.hasRecordAudioPermission())
            if (needPermission) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onUseRecommendTask = { viewModel.onUseRecommendTask() },
        onStart = { viewModel.onStartOrTerminateClick() },
        onPause = { viewModel.onPauseClick() },
        onStop = { viewModel.onStopClick() },
        onCopyLog = { viewModel.copyLog() },
    )
}
