package com.ai.phoneagent.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Settings : Routes("settings")
    data object About : Routes("about")
    data object UserAgreement : Routes("userAgreement")
    data object Licenses : Routes("licenses")
    data object Automation : Routes("automation")
    data object UpdateHistory : Routes("updateHistory")
    data object PermissionGuide : Routes("permissionGuide")
    data object Onboarding : Routes("onboarding") {
        const val FLOW_ARG = "flow"
        const val FLOW_ONBOARDING = "onboarding"
        const val FLOW_VIEW_ONLY = "view_only"
        const val FLOW_PERMISSION_ONLY = "permission_only"

        val routeWithOptionalFlow: String = "$route?$FLOW_ARG={$FLOW_ARG}"

        fun withFlow(flow: String): String = "$route?$FLOW_ARG=$flow"
    }
}
