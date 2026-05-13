package com.ai.phoneagent.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ai.phoneagent.ui.automation.AutomationScreen
import com.ai.phoneagent.ui.onboarding.OnboardingRoute
import com.ai.phoneagent.ui.settings.SettingsRoute
import com.ai.phoneagent.ui.settings.AboutRoute
import com.ai.phoneagent.ui.settings.LicensesScreen
import com.ai.phoneagent.ui.settings.PermissionGuideScreen
import com.ai.phoneagent.ui.settings.UserAgreementScreen
import com.ai.phoneagent.ui.updates.UpdateHistoryScreen

@Composable
fun AriesNavGraph(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var isRoutePopInFlight by remember(currentRoute) { mutableStateOf(false) }

    BackHandler(enabled = currentRoute != null && currentRoute != Routes.Home.route && !isRoutePopInFlight) {
        isRoutePopInFlight = true
        if (!navController.popBackStack()) {
            isRoutePopInFlight = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route,
    ) {
        composable(Routes.Home.route) { homeContent() }
        composable(Routes.Settings.route) { SettingsRoute(navController = navController) }
        composable(Routes.About.route) { AboutRoute(navController = navController) }
        composable(Routes.Automation.route) { AutomationScreen(navController = navController) }
        composable(Routes.UpdateHistory.route) { UpdateHistoryScreen(navController = navController) }
        composable(Routes.PermissionGuide.route) { PermissionGuideScreen(navController = navController) }
        composable(Routes.UserAgreement.route) { UserAgreementScreen(navController = navController) }
        composable(Routes.Licenses.route) { LicensesScreen(navController = navController) }
        composable(Routes.Onboarding.route) {
            OnboardingRoute(
                navController = navController,
                flow = null,
            )
        }
        composable(
            route = Routes.Onboarding.routeWithOptionalFlow,
            arguments =
                listOf(
                    navArgument(Routes.Onboarding.FLOW_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = Routes.Onboarding.FLOW_ONBOARDING
                    },
                ),
        ) { entry ->
            OnboardingRoute(
                navController = navController,
                flow = entry.arguments?.getString(Routes.Onboarding.FLOW_ARG),
            )
        }
    }
}

@Composable
private fun PlaceholderRouteScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "TODO — T13/T14/T15 will fill this")
    }
}
