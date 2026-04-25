package com.ai.phoneagent.ui.components.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPipelineTest {

    @Test
    fun rawHtmlDetailsUsesHtmlPipeline() {
        val markdown = """
            <details>
            <summary>展开</summary>
            <div style="color: red">内容</div>
            </details>
        """.trimIndent()

        assertTrue(shouldUseHtmlRenderPipeline(preProcess(markdown)))
    }

    @Test
    fun plainMarkdownKeepsAstPipeline() {
        val markdown = """
            # 标题

            这是一个普通段落，包含 `code` 和 [link](https://example.com)
        """.trimIndent()

        assertFalse(shouldUseHtmlRenderPipeline(preProcess(markdown)))
    }

    @Test
    fun mermaidFenceDoesNotForceHtmlPipeline() {
        val markdown = """
            ```mermaid
            graph TD
            A-->B
            ```
        """.trimIndent()

        assertFalse(shouldUseHtmlRenderPipeline(preProcess(markdown)))
    }

    @Test
    fun escapedDollarInlineMathIsNormalized() {
        val markdown = "积分因子为：\\$ \\mu(x) \\$。左边可视为 \\$ (y e^{x^2})' \\$ 的导数。"

        assertEquals(
            "积分因子为：$ \\mu(x) $。左边可视为 $ (y e^{x^2})' $ 的导数。",
            preProcess(markdown),
        )
    }

    @Test
    fun escapedShortInlineMathIsNormalized() {
        val markdown = "其中 \\$ C \\$ 为积分常数，且 \\$ k > 0 \\$。"

        assertEquals("其中 $ C $ 为积分常数，且 $ k > 0 $。", preProcess(markdown))
    }

    @Test
    fun escapedDollarInCodeIsPreserved() {
        val markdown = "`\\$ \\mu(x) \\$`"

        assertEquals(markdown, preProcess(markdown))
    }

    @Test
    fun inlineMathWithPaddedDelimitersIsDetected() {
        val segments = splitTextWithMath("积分因子 $ \\mu(x) $ 为，乘以 $ e^{x^2} $。")

        assertTrue(segments.any { it.isMath && it.content.trim() == "\\mu(x)" })
        assertTrue(segments.any { it.isMath && it.content.trim() == "e^{x^2}" })
    }

    @Test
    fun inlineMathInChineseParagraphIsDetected() {
        val markdown = "假设参数 $ theta(t) $ 随时间 $ t $ 的变化率满足方程。"

        assertTrue(containsInlineMath(markdown))
    }

    @Test
    fun commonModelLatexTyposAreNormalized() {
        assertEquals("\\theta(t)", normalizeInlineLatexFormula(" theta(t) "))
        assertEquals("\\mu(t)", normalizeInlineLatexFormula("\\mut"))
        assertEquals("\\theta_0", normalizeInlineLatexFormula("\\theta0"))
    }

    @Test
    fun bareShortAssignmentsAreNormalizedAsInlineMath() {
        val markdown = "其中 Px = 2x， Qx = e^{-x^2}。"

        assertEquals("其中 \$Px = 2x\$， \$Qx = e^{-x^2}\$。", preProcess(markdown))
    }

    @Test
    fun bareLatexAssignmentIsNormalizedAsInlineMath() {
        val markdown = "代入初始条件 \\theta0 = \\theta_0：所以 C = \\theta_0。"

        assertEquals(
            "代入初始条件 \$\\theta0 = \\theta_0\$：所以 \$C = \\theta_0\$。",
            preProcess(markdown),
        )
    }
}
