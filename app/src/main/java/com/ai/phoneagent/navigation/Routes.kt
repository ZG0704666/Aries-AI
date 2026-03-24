package com.ai.phoneagent.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Settings : Routes("settings")
    data object About : Routes("about")
    data object Automation : Routes("automation")
    data object UpdateHistory : Routes("updateHistory")
    data object PermissionGuide : Routes("permissionGuide")
    data object Onboarding : Routes("onboarding")
}
