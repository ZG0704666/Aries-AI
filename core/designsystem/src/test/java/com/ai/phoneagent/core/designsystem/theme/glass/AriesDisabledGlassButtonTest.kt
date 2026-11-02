package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AriesDisabledGlassButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledButtonDoesNotHighlightOrGrowOnTouch() {
        assertDisabledSurfaceIsStable(AriesGlassTier.Pill)
    }

    @Test fun disabledButtonWithCustomTierDoesNotHighlightOrGrowOnTouch() {
        assertDisabledSurfaceIsStable(AriesGlassTier.Card)
    }

    private fun assertDisabledSurfaceIsStable(tier: AriesGlassTier) {
        var clicks = 0
        compose.setContent {
            Box(Modifier.size(160.dp).testTag("capture")) {
                AriesLiquidButton(
                    onClick = { clicks++ }, enabled = false, tier = tier,
                    modifier = Modifier.testTag("button"),
                    surfaceColor = Color.DarkGray,
                ) { Text("Disabled") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = pixels()
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue("Disabled glass must not animate its surface", before == pixels())
        compose.onNodeWithTag("button").performTouchInput { up() }
        assertEquals(0, clicks)
    }

    private fun pixels(): List<Color> {
        val pixels = compose.onNodeWithTag("capture").captureToImage().toPixelMap()
        return List(pixels.width * pixels.height) { pixels[it % pixels.width, it / pixels.width] }
    }
}
