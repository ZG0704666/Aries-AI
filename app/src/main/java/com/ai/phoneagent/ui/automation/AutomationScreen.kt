package com.ai.phoneagent.ui.automation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import com.ai.phoneagent.viewmodel.AutomationViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AutomationScreen(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = remember(context) { context.findActivity() }
    // Scope ViewModel to Activity so it survives nav pop (agent keeps running in background)
    val activityOwner = hostActivity as? androidx.lifecycle.ViewModelStoreOwner
    val viewModel: AutomationViewModel = koinViewModel(
        viewModelStoreOwner = activityOwner ?: LocalViewModelStoreOwner.current!!,
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

    // Consume pending launch args from MainActivity intent dispatch
    LaunchedEffect(Unit) {
        AutomationViewModel.pendingLaunchArgs?.let { args ->
            AutomationViewModel.pendingLaunchArgs = null
            viewModel.consumeLaunchArgs(args)
            if (args.popBackImmediately) {
                navController.popBackStack()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AutomationControlScreen(
        statusSummary = viewModel.statusSummary,
        interactionModeText = viewModel.interactionModeText,
        accessibilityStatusText = viewModel.accessibilityStatusText,
        shizukuStatusText = viewModel.shizukuStatusText,
        statusTone = viewModel.statusTone(),
        showShizukuControls = viewModel.showShizukuControls,
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
        onBack = { navController.popBackStack() },
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
