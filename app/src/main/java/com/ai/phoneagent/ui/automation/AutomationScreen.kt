package com.ai.phoneagent.ui.automation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    val task by viewModel.taskSnapshot.collectAsState()
    LaunchedEffect(task?.runId) {
        task?.takeIf { !it.status.isTerminal && navController.currentDestination?.route == "automation" }?.let { navController.navigate("task/${it.runId}") { launchSingleTop = true } }
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
        logText = viewModel.logText,
        showShizukuAuthorize = viewModel.showShizukuAuthorize,
        onBack = { navController.popBackStack() },
        onOpenAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onAuthorizeShizuku = { viewModel.authorizeShizukuAndAccessibility() },
        onRefreshStatus = { viewModel.onRefreshStatus() },
        onExecutionModeChange = { viewModel.onExecutionModeChange(it) },
        onShizukuModeChange = { viewModel.onShizukuModeChange(it) },
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
