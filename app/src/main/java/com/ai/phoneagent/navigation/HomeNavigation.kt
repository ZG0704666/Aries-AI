package com.ai.phoneagent.navigation

enum class HomeBackAction { DISMISS_OVERLAY, SETTINGS_PARENT, HOME, SYSTEM }

fun homeBackAction(page: Int, settingsAtRoot: Boolean, overlayOpen: Boolean = false): HomeBackAction = when {
    overlayOpen -> HomeBackAction.DISMISS_OVERLAY
    page == 2 && !settingsAtRoot -> HomeBackAction.SETTINGS_PARENT
    page != 1 -> HomeBackAction.HOME
    else -> HomeBackAction.SYSTEM
}
