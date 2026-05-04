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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.ai.phoneagent.core.designsystem.R as DesignR
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
                    onOpenAutomation = { navController.navigate(Routes.Automation.route) },
                    onOpenAbout = { viewModel.navigateTo(SettingsViewModel.SettingsPage.About) },
                )
            }

            SettingsViewModel.SettingsPage.ModelApi -> {
                DrawerModelApiConfigScreen(
                    currentApiMode = viewModel.currentApiMode,
                    apiInput = viewModel.apiInputText,
                    apiBaseUrl = viewModel.apiBaseUrlText,
                    apiModel = viewModel.apiModelText,
                    apiStatus = viewModel.apiStatusText,
                    apiStatusPositive = viewModel.apiStatusPositive,
                    qwenButtonText = viewModel.qwenButtonText,
                    qwenButtonEnabled = viewModel.qwenButtonEnabled,
                    showAriesApiSection = viewModel.showAriesApiSection,
                    ariesLoggedInUser = viewModel.ariesLoggedInUser,
                    ariesSelectedModel = viewModel.ariesSelectedModel,
                    onChangeAriesModel = { viewModel.openAriesModelSelectionDialog() },
                    onBack = { viewModel.openHomePage() },
                    onApiModeChange = { mode ->
                        viewModel.onApiModeChange(mode) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onApiInputChange = { value -> viewModel.onApiInputChanged(value) },
                    onPasteApi = {
                        viewModel.pasteApiKey(context) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenApiKeyPage = { viewModel.openApiKeyPage(context) },
                    onOpenMembership = { viewModel.openMembershipPage() },
                    onAriesLoginClick = { viewModel.openAriesLoginDialog() },
                    onAriesLogout = {
                        viewModel.ariesLogout()
                        Toast.makeText(context, context.getString(com.ai.phoneagent.R.string.aries_logout_success), Toast.LENGTH_SHORT).show()
                    },
                    onApiBaseUrlChange = { value -> viewModel.onApiBaseUrlChange(value) },
                    onApiModelChange = { value -> viewModel.onApiModelChange(value) },
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
                // Aries 模型选择对话框
                if (viewModel.showAriesModelDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissAriesModelDialog() },
                        title = { Text(stringResource(com.ai.phoneagent.R.string.aries_model_select_title)) },
                        text = {
                            LazyColumn {
                                items(viewModel.ariesAvailableModels) { model ->
                                    Text(
                                        text = model.id,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectAriesModel(model.id) }
                                            .then(
                                                Modifier.height(
                                                    dimensionResource(DesignR.dimen.m3t_compact_button_height)
                                                )
                                            ),
                                    )
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissAriesModelDialog() }) {
                                Text(stringResource(SettingsR.string.action_cancel))
                            }
                        },
                    )
                }
                // Aries 登录对话框
                if (viewModel.showAriesLoginDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!viewModel.ariesLoginLoading) viewModel.dismissAriesLoginDialog() },
                        title = { Text(stringResource(com.ai.phoneagent.R.string.aries_login_dialog_title)) },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = viewModel.ariesLoginUsername,
                                    onValueChange = { viewModel.onAriesLoginUsernameChange(it) },
                                    label = { Text(stringResource(com.ai.phoneagent.R.string.aries_login_username_label)) },
                                    singleLine = true,
                                    enabled = !viewModel.ariesLoginLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(dimensionResource(DesignR.dimen.m3t_spacing_sm)))
                                OutlinedTextField(
                                    value = viewModel.ariesLoginPassword,
                                    onValueChange = { viewModel.onAriesLoginPasswordChange(it) },
                                    label = { Text(stringResource(com.ai.phoneagent.R.string.aries_login_password_label)) },
                                    singleLine = true,
                                    enabled = !viewModel.ariesLoginLoading,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                viewModel.ariesLoginError?.let { error ->
                                    Spacer(modifier = Modifier.height(dimensionResource(DesignR.dimen.m3t_spacing_xs)))
                                    Text(
                                        text = error,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (viewModel.ariesLoginLoading) {
                                    Spacer(modifier = Modifier.height(dimensionResource(DesignR.dimen.m3t_spacing_sm)))
                                    CircularProgressIndicator()
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.submitAriesLogin(
                                        onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                                        onError = { },
                                    )
                                },
                                enabled = !viewModel.ariesLoginLoading,
                            ) {
                                Text(stringResource(com.ai.phoneagent.R.string.aries_login_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.dismissAriesLoginDialog() },
                                enabled = !viewModel.ariesLoginLoading,
                            ) {
                                Text(stringResource(SettingsR.string.action_cancel))
                            }
                        },
                    )
                }
            }

            SettingsViewModel.SettingsPage.Membership -> {
                MembershipScreen(
                    onBack = { viewModel.openHomePage() },
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
        onAliasTap = {
            viewModel.handleAliasTap()
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

