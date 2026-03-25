package com.ai.phoneagent.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.R
import com.ai.phoneagent.navigation.Routes
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

    if (viewModel.currentPage == SettingsViewModel.SettingsPage.ModelApi) {
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
                    onOpenAppearance = {
                        Toast.makeText(
                            context,
                            R.string.settings_appearance_coming_soon,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onOpenModelApi = { viewModel.openModelApiPage() },
                    onOpenAutomation = { navController.navigate(Routes.Automation.route) },
                    onOpenAbout = { navController.navigate(Routes.About.route) },
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
        }
    }
}
