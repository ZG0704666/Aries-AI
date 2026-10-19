package com.ai.phoneagent.navigation

/** 导航路由定义，`route` 为 Compose Navigation 使用的路径字符串。 */
sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Settings : Routes("settings")
    data object About : Routes("about")
    data object UserAgreement : Routes("userAgreement")
    data object Licenses : Routes("licenses")
    data object Automation : Routes("automation")
    data object UpdateHistory : Routes("updateHistory")
    data object PermissionGuide : Routes("permissionGuide")

    /** 引导页路由，支持通过 `flow` 参数切换展示形态。 */
    data object Onboarding : Routes("onboarding") {
        const val FLOW_ARG = "flow"
        const val FLOW_ONBOARDING = "onboarding"
        const val FLOW_VIEW_ONLY = "view_only"
        const val FLOW_PERMISSION_ONLY = "permission_only"

        /** 带可选 flow 参数的路由模板。 */
        val routeWithOptionalFlow: String = "$route?$FLOW_ARG={$FLOW_ARG}"

        /** 构造指定 flow 的引导页路由。 */
        fun withFlow(flow: String): String = "$route?$FLOW_ARG=$flow"
    }
}
