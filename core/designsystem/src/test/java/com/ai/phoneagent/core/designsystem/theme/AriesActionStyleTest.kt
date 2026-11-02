package com.ai.phoneagent.core.designsystem.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.ai.phoneagent.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AriesActionStyleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `fixed palettes expose the requested resource backed gradients`() {
        val lightContext = themedContext(isDark = false)
        val darkContext = themedContext(isDark = true)
        val expected =
            listOf(
                Triple(ThemeColorStyle.DEFAULT, 0xFF006C84L to 0xFFA3485EL, 0xFF8CDDEBL to 0xFFFED3DDL),
                Triple(ThemeColorStyle.OCEAN, 0xFF005EA6L to 0xFF00778AL, 0xFF68C0FFL to 0xFFA6F0FDL),
                Triple(ThemeColorStyle.FOREST, 0xFF5E7420L to 0xFF276878L, 0xFFF6FBD3L to 0xFFAFE8F9L),
                Triple(ThemeColorStyle.SUNSET, 0xFF005A6AL to 0xFF00788CL, 0xFFB5EFFAL to 0xFFFEFEFEL),
                Triple(ThemeColorStyle.ROSE, 0xFF963D69L to 0xFF77519BL, 0xFFF2AECFL to 0xFFD4C5ECL),
            )

        expected.forEach { (style, lightPair, darkPair) ->
            val light = createStaticAriesActionStyle(lightContext, style, isDark = false)
            val dark = createStaticAriesActionStyle(darkContext, style, isDark = true)
            assertEquals(Color(lightPair.first), light.gradientStart)
            assertEquals(Color(lightPair.second), light.gradientEnd)
            assertEquals(Color(darkPair.first), dark.gradientStart)
            assertEquals(Color(darkPair.second), dark.gradientEnd)
        }
    }

    @Test
    fun `action foreground keeps 4 point 5 contrast across highlighted gradients`() {
        val lightContext = themedContext(isDark = false)
        val darkContext = themedContext(isDark = true)
        ThemeColorStyle.entries.filterNot { it.isDynamic }.forEach { themeStyle ->
            listOf(
                createStaticAriesActionStyle(lightContext, themeStyle, isDark = false),
                createStaticAriesActionStyle(darkContext, themeStyle, isDark = true),
            ).forEach { actionStyle ->
                for (step in 0..20) {
                    val fraction = step / 20f
                    val gradient = lerp(actionStyle.gradientStart, actionStyle.gradientEnd, fraction)
                    val highlighted = actionStyle.glassHighlight.compositeOver(gradient)
                    assertTrue(
                        "${themeStyle.name} dark=${actionStyle.isDark} step=$step contrast=${contrast(actionStyle.onGradient, highlighted)}",
                        contrast(actionStyle.onGradient, highlighted) >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `material accent roles follow each action gradient`() {
        val base = lightColorScheme()
        listOf(false, true).forEach { isDark ->
            val context = themedContext(isDark)
            ThemeColorStyle.entries.filterNot { it.isDynamic }.forEach { style ->
                val action = createStaticAriesActionStyle(context, style, isDark)
                val colors =
                    if (style == ThemeColorStyle.DEFAULT) {
                        base.copy(
                            primary = context.requireThemeColor(R.color.m3t_primary),
                            secondary = context.requireThemeColor(R.color.m3t_secondary),
                            tertiary = context.requireThemeColor(R.color.m3t_tertiary),
                        )
                    } else {
                        base.applyThemeAccent(requireNotNull(style.accent), context)
                    }
                assertEquals(action.gradientStart, colors.primary)
                assertEquals(action.gradientEnd, colors.secondary)
                assertColorClose(lerp(action.gradientStart, action.gradientEnd, 0.5f), colors.tertiary)
            }
        }
    }

    @Test
    fun `forced light app mode selects light resources while system is dark`() {
        val systemContext = ApplicationProvider.getApplicationContext<Context>()
        systemContext.resources.configuration.uiMode =
            (systemContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES

        var capturedLightStyle: AriesActionStyle? = null
        var capturedLightPrimary: Color? = null
        composeRule.setContent {
            AriesMaterialTheme(themeMode = ThemeMode.LIGHT, themeColorStyle = ThemeColorStyle.DEFAULT) {
                val actionStyle = LocalAriesActionStyle.current
                val primary = MaterialTheme.colorScheme.primary
                SideEffect {
                    capturedLightStyle = actionStyle
                    capturedLightPrimary = primary
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(false, capturedLightStyle?.isDark)
        assertEquals(Color(0xFF006C84), capturedLightStyle?.gradientStart)
        assertEquals(themedContext(false).ariesThemeColor(R.color.m3t_primary), capturedLightPrimary)

    }

    @Test
    fun `forced dark app mode selects dark resources while system is light`() {
        val systemContext = ApplicationProvider.getApplicationContext<Context>()
        systemContext.resources.configuration.uiMode =
            (systemContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        var capturedDarkStyle: AriesActionStyle? = null
        var capturedDarkPrimary: Color? = null
        composeRule.setContent {
            AriesMaterialTheme(themeMode = ThemeMode.DARK, themeColorStyle = ThemeColorStyle.DEFAULT) {
                val actionStyle = LocalAriesActionStyle.current
                val primary = MaterialTheme.colorScheme.primary
                SideEffect {
                    capturedDarkStyle = actionStyle
                    capturedDarkPrimary = primary
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(true, capturedDarkStyle?.isDark)
        assertEquals(Color(0xFF8CDDEB), capturedDarkStyle?.gradientStart)
        assertEquals(themedContext(true).ariesThemeColor(R.color.m3t_primary), capturedDarkPrimary)
    }

    @Test
    fun `accent preview uses requested dark resources while system is light`() {
        val systemContext = ApplicationProvider.getApplicationContext<Context>()
        systemContext.resources.configuration.uiMode =
            (systemContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        var preview: ThemeAccentPreview? = null
        composeRule.setContent {
            val colors = ThemeAccent.DEFAULT.previewColors(isDarkTheme = true)
            SideEffect { preview = colors }
        }
        composeRule.waitForIdle()
        assertEquals(Color(0xFF8CDDEB), preview?.primary)
        assertEquals(Color(0xFFFED3DD), preview?.secondary)
    }

    @Test
    fun `accent preview uses requested light resources while system is dark`() {
        val systemContext = ApplicationProvider.getApplicationContext<Context>()
        systemContext.resources.configuration.uiMode =
            (systemContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES
        var preview: ThemeAccentPreview? = null
        composeRule.setContent {
            val colors = ThemeAccent.DEFAULT.previewColors(isDarkTheme = false)
            SideEffect { preview = colors }
        }
        composeRule.waitForIdle()
        assertEquals(Color(0xFF006C84), preview?.primary)
        assertEquals(Color(0xFFA3485E), preview?.secondary)
    }

    @Test
    fun `dynamic style reduces an unreadable mixed gradient`() {
        val context = themedContext(isDark = false)
        val scheme =
            lightColorScheme(
                primary = Color(0xFF0000AA),
                onPrimary = Color.White,
                secondary = Color(0xFFFFFF00),
            )
        val style = createDynamicAriesActionStyle(context, scheme, isDark = false)

        assertEquals(style.gradientStart, style.gradientEnd)
        for (step in 0..20) {
            val background = lerp(style.gradientStart, style.gradientEnd, step / 20f)
            val highlighted = style.glassHighlight.compositeOver(background)
            assertTrue(contrast(style.onGradient, highlighted) >= 4.5)
        }
    }

    @Test
    fun `fixed accents leave global surfaces and outlines unchanged while retaining accent tint`() {
        val context = themedContext(isDark = false)
        val base =
            lightColorScheme(
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFF1F1F1),
                outline = Color(0xFF777777),
                surfaceTint = Color(0xFF123456),
            )

        val legacyTints =
            mapOf(
                ThemeAccent.OCEAN to Color(0xFF006495),
                ThemeAccent.FOREST to Color(0xFF5E7420),
                ThemeAccent.SUNSET to Color(0xFF005A6A),
                ThemeAccent.ROSE to Color(0xFF9C415D),
            )
        legacyTints.forEach { (accent, legacyTint) ->
            val styled = base.applyThemeAccent(accent, context)
            assertEquals(base.background, styled.background)
            assertEquals(base.surface, styled.surface)
            assertEquals(base.outline, styled.outline)
            assertEquals(legacyTint, styled.surfaceTint)
        }

        val darkContext = themedContext(isDark = true)
        val legacyDarkTints =
            mapOf(
                ThemeAccent.OCEAN to Color(0xFF8CCFFF),
                ThemeAccent.FOREST to Color(0xFFD9E9B0),
                ThemeAccent.SUNSET to Color(0xFFB5EFFA),
                ThemeAccent.ROSE to Color(0xFFFFB1C5),
            )
        legacyDarkTints.forEach { (accent, legacyTint) ->
            val styled = base.applyThemeAccent(accent, darkContext)
            assertEquals(base.background, styled.background)
            assertEquals(base.surface, styled.surface)
            assertEquals(base.outline, styled.outline)
            assertEquals(legacyTint, styled.surfaceTint)
        }
    }

    private fun themedContext(isDark: Boolean): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            val mode = if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        }
        return context.createConfigurationContext(configuration)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
        val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        val tolerance = 1.1f / 255f
        assertTrue(kotlin.math.abs(expected.red - actual.red) <= tolerance)
        assertTrue(kotlin.math.abs(expected.green - actual.green) <= tolerance)
        assertTrue(kotlin.math.abs(expected.blue - actual.blue) <= tolerance)
    }

    private fun lerp(start: Color, end: Color, fraction: Float): Color =
        Color(
            red = start.red + (end.red - start.red) * fraction,
            green = start.green + (end.green - start.green) * fraction,
            blue = start.blue + (end.blue - start.blue) * fraction,
            alpha = start.alpha + (end.alpha - start.alpha) * fraction,
        )
}
