package com.ai.phoneagent.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AriesNavGraph(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BackHandler(enabled = currentRoute != null && currentRoute != Routes.Home.route) {
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route,
    ) {
        composable(Routes.Home.route) { homeContent() }
        composable(Routes.Settings.route) { PlaceholderRouteScreen() }
        composable(Routes.About.route) { PlaceholderRouteScreen() }
        composable(Routes.Automation.route) { PlaceholderRouteScreen() }
        composable(Routes.UpdateHistory.route) { PlaceholderRouteScreen() }
        composable(Routes.PermissionGuide.route) { PlaceholderRouteScreen() }
        composable(Routes.Onboarding.route) { PlaceholderRouteScreen() }
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
