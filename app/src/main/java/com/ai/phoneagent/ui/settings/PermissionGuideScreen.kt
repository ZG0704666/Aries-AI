package com.ai.phoneagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.ui.onboarding.OnboardingFlowMode
import com.ai.phoneagent.ui.onboarding.OnboardingScreen
import org.koin.compose.koinInject

@Composable
fun PermissionGuideScreen(navController: NavController) {
    val preferences: AppPreferencesRepository = koinInject()
    OnboardingScreen(flowMode = OnboardingFlowMode.PERMISSION_ONLY,
        appPreferencesRepository = preferences,
        onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
}
