package com.ai.phoneagent.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ai.phoneagent.feature.settings.R
import com.ai.phoneagent.helper.FeedbackLogExporter
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.ui.AboutScreen
import com.ai.phoneagent.viewmodel.AboutViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun AboutRoute(
    navController: NavController,
    viewModel: AboutViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFeedbackConfirmDialog by remember { mutableStateOf(false) }
    var isExportingFeedbackBundle by remember { mutableStateOf(false) }

    val vibrateLight = {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
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
            navController.popBackStack()
        },
        onCheckUpdate = {
            vibrateLight()
            viewModel.checkForUpdates()
        },
        onOpenChangelog = {
            vibrateLight()
            navController.navigate(Routes.UpdateHistory.route)
        },
        onOpenUserAgreement = {
            vibrateLight()
            navController.navigate(Routes.UserAgreement.route)
        },
        onOpenLicenses = {
            vibrateLight()
            navController.navigate(Routes.Licenses.route)
        },
        onOpenWebsite = {
            vibrateLight()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.about_website_url))))
            } catch (_: Exception) {
                Toast.makeText(context, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onOpenSourceCode = {
            vibrateLight()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.about_source_code_url))))
            } catch (_: Exception) {
                Toast.makeText(context, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onTaskFeedback = {
            vibrateLight()
            showFeedbackConfirmDialog = true
        },
        onCopyContact = {
            vibrateLight()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("text", "zhangyongqi@njit.edu.cn"))
            Toast.makeText(context, R.string.about_contact_copied, Toast.LENGTH_SHORT).show()
        },
        onDeveloperTap = {
            vibrateLight()
            viewModel.handleDeveloperTap()
        },
        onAliasTap = {
            viewModel.handleAliasTap()
        },
    )

    if (showFeedbackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackConfirmDialog = false },
            title = { Text(stringResource(R.string.about_feedback_confirm_title)) },
            text = { Text(stringResource(R.string.about_feedback_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFeedbackConfirmDialog = false
                        isExportingFeedbackBundle = true
                        scope.launch {
                            val result = FeedbackLogExporter.exportSanitizedBundle(context)
                            isExportingFeedbackBundle = false
                            result.onSuccess { bundleFile ->
                                FeedbackLogExporter.shareBundle(
                                    context = context,
                                    file = bundleFile,
                                    chooserTitle = context.getString(R.string.about_feedback_share_chooser),
                                    subject = context.getString(R.string.about_feedback_share_subject),
                                ).onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.about_feedback_export_failed,
                                            it.message.orEmpty(),
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.about_feedback_export_failed,
                                        it.message.orEmpty(),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.about_feedback_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (isExportingFeedbackBundle) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.about_feedback_confirm_title)) },
            text = {
                Column {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.about_feedback_exporting))
                }
            },
            confirmButton = {},
        )
    }

    uiState.updateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.m3t_updates_found) + " ${state.entry.versionTag}") },
            text = { Text(state.entry.body.ifBlank { stringResource(R.string.m3t_updates_no_changelog) }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdateDialog()
                    viewModel.handleDownload(state.entry)
                }) {
                    Text(stringResource(R.string.about_check_updates))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdateDialog()
                    navController.navigate(Routes.UpdateHistory.route)
                }) {
                    Text(stringResource(R.string.about_changelog))
                }
            }
        )
    }

    uiState.errorDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            title = { Text(stringResource(R.string.about_check_failed)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissErrorDialog()
                    navController.navigate(Routes.UpdateHistory.route)
                }) {
                    Text(stringResource(R.string.about_changelog))
                }
            }
        )
    }

    uiState.upToDateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpToDateDialog() },
            title = { Text(stringResource(R.string.about_up_to_date)) },
            text = { Text(stringResource(R.string.about_current_version_format, state.currentVersion)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpToDateDialog() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissUpToDateDialog()
                    navController.navigate(Routes.UpdateHistory.route)
                }) {
                    Text(stringResource(R.string.about_changelog))
                }
            }
        )
    }

    uiState.downloadOptionsDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadOptionsDialog() },
            title = { Text(stringResource(R.string.m3t_updates_choose_source)) },
            text = {
                Column {
                    state.options.forEach { option ->
                        TextButton(
                            onClick = {
                                viewModel.dismissDownloadOptionsDialog()
                                viewModel.openReleaseUrlWithFeedback(option.second)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.first)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDownloadOptionsDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
