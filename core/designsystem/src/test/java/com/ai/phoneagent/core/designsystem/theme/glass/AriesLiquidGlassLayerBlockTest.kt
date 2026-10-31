package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P0-2 回归：ariesLiquidGlass 的 layerBlock 只能被应用一次。
 *
 * 修复前：外层 graphicsLayer 调用一次 layerBlock，随后又把同一回调传给
 * drawBackdrop（其内部再次创建 graphicsLayer 应用同一回调），缩放/速度挤压
 * 等形变被叠加两次。此测试通过计数器 + 状态驱动的单帧更新验证每次
 * graphicsLayer 更新只执行一次 layerBlock。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AriesLiquidGlassLayerBlockTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 模拟按压缩放：读取内部状态以便测试驱动 graphicsLayer 更新。 */
    private class LayerBlockProbe {
        // 计数器不能是 Snapshot 状态：graphicsLayer 内写入被自身观察的状态会不断触发失效。
        var applications = 0
        var scale by mutableStateOf(1f)

        val block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
            applications++
            scaleX = scale
            scaleY = scale
        }
    }

    @Test
    fun `layerBlock executes once per graphicsLayer update`() {
        val probe = LayerBlockProbe()
        composeRule.setContent {
            Box(
                Modifier
                    .size(64.dp)
                    .ariesLiquidGlass(
                        shape = RoundedCornerShape(32.dp),
                        tier = AriesGlassTier.Pill,
                        backdrop = emptyBackdrop(),
                        layerBlock = probe.block,
                    )
            )
        }
        composeRule.waitForIdle()
        // 至少应用一次：玻璃层已挂上缩放。
        assertTrue("layerBlock 从未执行（玻璃层未创建）", probe.applications >= 1)

        // 驱动一次状态变化 → graphicsLayer 应只重新执行一次 layerBlock（修复前为两次）。
        val before = probe.applications
        probe.scale = 1.25f
        composeRule.waitForIdle()
        assertEquals(
            "layerBlock 在一次更新中被应用多次（形变被叠加）",
            before + 1,
            probe.applications,
        )
    }

    @Test
    fun `chased updates do not stack deformation beyond one application per update`() {
        val probe = LayerBlockProbe()
        composeRule.setContent {
            Box(
                Modifier
                    .size(64.dp)
                    .ariesLiquidGlass(
                        shape = RoundedCornerShape(32.dp),
                        tier = AriesGlassTier.Pill,
                        backdrop = emptyBackdrop(),
                        layerBlock = probe.block,
                    )
            )
        }
        composeRule.waitForIdle()

        // 连续三次“追赶”式更新：应用次数只随更新数线性增长。
        repeat(3) { i ->
            val before = probe.applications
            probe.scale = 1f + 0.1f * (i + 1)
            composeRule.waitForIdle()
            assertEquals(
                "第 ${i + 1} 次更新形变被叠加",
                before + 1,
                probe.applications,
            )
        }
    }
}
