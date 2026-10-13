package com.ai.phoneagent.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationTest {
    @Test fun backReturnsToHomeBeforeLeavingApp() {
        assertEquals(HomeBackAction.HOME, homeBackAction(0, true))
        assertEquals(HomeBackAction.HOME, homeBackAction(2, true))
        assertEquals(HomeBackAction.SYSTEM, homeBackAction(1, true))
    }

    @Test fun subpagesAndOverlaysTakePriority() {
        assertEquals(HomeBackAction.SETTINGS_PARENT, homeBackAction(2, false))
        for (page in 0..2) assertEquals(HomeBackAction.DISMISS_OVERLAY, homeBackAction(page, false, true))
    }
}
