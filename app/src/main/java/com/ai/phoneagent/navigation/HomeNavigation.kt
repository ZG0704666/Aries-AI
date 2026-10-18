package com.ai.phoneagent.navigation

/** 首页返回键应执行的动作。 */
enum class HomeBackAction { DISMISS_OVERLAY, SETTINGS_PARENT, HOME, SYSTEM }

/**
 * 根据当前页与状态决定返回键行为。
 *
 * 优先级：浮层打开时关闭浮层 → 设置页非根时回上级 → 非首页时回首页 → 其余交给系统。
 */
fun homeBackAction(page: Int, settingsAtRoot: Boolean, overlayOpen: Boolean = false): HomeBackAction = when {
    overlayOpen -> HomeBackAction.DISMISS_OVERLAY
    page == 2 && !settingsAtRoot -> HomeBackAction.SETTINGS_PARENT
    page != 1 -> HomeBackAction.HOME
    else -> HomeBackAction.SYSTEM
}
