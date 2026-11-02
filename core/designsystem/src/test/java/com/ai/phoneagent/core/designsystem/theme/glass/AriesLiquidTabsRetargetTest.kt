package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
class AriesLiquidTabsRetargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `latest tap wins when returning to settled tab before animation finishes`() {
        val harness = setTabs()

        tap(2, 50)
        tap(0, 80)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0f, sliderPosition(), 0.01f)
        assertEquals(listOf(2, 0), harness.requests)
    }

    @Test
    fun `retarget carries the in flight spring velocity into the new animation`() {
        setTabs()

        tap(2, 80)
        tap(0, 16)

        assertTrue("retarget discarded the in-flight velocity", kotlin.math.abs(retargetVelocity()) > 0.01f)
    }

    @Test
    fun `repeated tap on the same in flight target does not restart the spring`() {
        val harness = setTabs()

        tap(2, 80)
        val positionBeforeRepeat = sliderPosition()
        tap(2, 16)
        assertTrue(sliderPosition() >= positionBeforeRepeat)

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals(2f, sliderPosition(), 0.01f)
        assertEquals(listOf(2, 2), harness.requests)
    }

    @Test
    fun `latest of several rapid taps wins and pager progress resumes control`() {
        val harness = setTabs()

        tap(2, 50)
        tap(1, 80)
        tap(0, 120)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0f, sliderPosition(), 0.01f)
        assertEquals(listOf(2, 1, 0), harness.requests)

        harness.progress = 0.625f
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(0.625f, sliderPosition(), 0.01f)
    }

    @Test
    @Config(sdk = [30])
    fun `API 30 static fallback routes taps and follows pager progress`() {
        val harness = setTabs()

        tap(2, 50)
        assertEquals(listOf(2), harness.requests)

        harness.progress = 1.25f
        harness.scrolling = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(1.25f, sliderPosition(), 0.01f)
    }

    @Test
    fun `user pager swipe interrupts stale tab animation and owns slider position`() {
        val harness = setTabs()

        tap(2, 50)
        harness.progress = 0.4f
        harness.scrolling = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(0.4f, sliderPosition(), 0.01f)

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals(0.4f, sliderPosition(), 0.01f)
    }

    @Test
    fun `drag starts from currently displayed pager progress and cancels stale tap owner`() {
        val harness = setTabs()

        tap(2, 50)
        val visibleBeforeDrag = sliderPosition()
        harness.progress = 0.35f
        composeRule.onNodeWithTag(AriesLiquidTabSliderTestTag).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeByFrame()
        val positionAtDragStart = sliderPosition()
        assertTrue(positionAtDragStart >= visibleBeforeDrag)
        assertTrue(kotlin.math.abs(positionAtDragStart - harness.progress) > 0.2f)

        composeRule.onNodeWithTag(AriesLiquidTabSliderTestTag).performTouchInput {
            moveBy(Offset(24f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(48)
        val draggedPosition = sliderPosition()
        assertTrue(draggedPosition > positionAtDragStart)

        composeRule.onNodeWithTag(AriesLiquidTabSliderTestTag).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(2_000)
        assertTrue(sliderPosition() < 1.5f)
    }

    @Test
    fun `rapid taps stay aligned with a real pager after its tween settles`() {
        composeRule.mainClock.autoAdvance = false
        lateinit var pager: PagerState
        composeRule.setContent {
            MaterialTheme {
                pager = rememberPagerState(initialPage = 0) { 3 }
                val scope = rememberCoroutineScope()
                var navigationJob by remember { mutableStateOf<Job?>(null) }
                Box(Modifier.size(width = 300.dp, height = 200.dp)) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { }
                    CompositionLocalProvider(LocalAriesLiquidTabsStateReader provides reader) {
                        AriesLiquidTabs(
                            selectedIndex = pager.settledPage,
                            onSelect = { page ->
                                navigationJob?.cancel()
                                navigationJob = scope.launch {
                                    pager.animateScrollToPage(page, animationSpec = tween(360))
                                }
                            },
                            progress = { pager.currentPage + pager.currentPageOffsetFraction },
                            pagerScrolling = { false },
                            backdrop = emptyBackdrop(),
                            modifier = Modifier.size(width = 300.dp, height = 64.dp),
                        ) { index -> Box(Modifier.fillMaxSize().testTag("tab-$index")) }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        tap(2, 50)
        tap(1, 80)
        tap(0, 120)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, pager.settledPage)
        assertEquals(0f, sliderPosition(), 0.01f)
    }

    private fun setTabs(): TabsHarness {
        composeRule.mainClock.autoAdvance = false
        val harness = TabsHarness()
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAriesLiquidTabsStateReader provides reader) {
                    AriesLiquidTabs(
                        selectedIndex = harness.selected,
                        onSelect = { harness.requests += it },
                        progress = { harness.progress },
                        pagerScrolling = { harness.scrolling },
                        backdrop = emptyBackdrop(),
                        modifier = Modifier.size(width = 300.dp, height = 64.dp),
                    ) { index ->
                        Box(Modifier.fillMaxSize().testTag("tab-$index"))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        return harness
    }

    private fun tap(index: Int, elapsedMs: Long) {
        composeRule.onAllNodesWithTag("tab-$index", useUnmergedTree = true)[0]
            .performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(elapsedMs)
    }

    private fun sliderPosition(): Float = reader.sliderPosition

    private fun retargetVelocity(): Float = reader.retargetVelocity

    private val reader = AriesLiquidTabsStateReader()

    private class TabsHarness {
        var selected by mutableIntStateOf(0)
        var progress by mutableFloatStateOf(0f)
        var scrolling by mutableStateOf(false)
        val requests = mutableListOf<Int>()
    }
}
