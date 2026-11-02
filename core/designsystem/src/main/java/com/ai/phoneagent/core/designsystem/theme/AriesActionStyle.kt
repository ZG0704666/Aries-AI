package com.ai.phoneagent.core.designsystem.theme

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ai.phoneagent.core.designsystem.R

@Immutable
data class AriesActionStyle(
    val gradientStart: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val selectedTint: Color,
    val homeGlowStart: Color,
    val homeGlowEnd: Color,
    val pressHighlightAlpha: Float,
    val isDark: Boolean,
)

val LocalAriesActionStyle = staticCompositionLocalOf<AriesActionStyle?> { null }

@Composable
fun ariesActionStyle(): AriesActionStyle {
    LocalAriesActionStyle.current?.let { return it }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = LocalAriesBackdrop.current.dark
    return createStaticAriesActionStyle(
        context = context.withNightMode(configuration, dark),
        themeColorStyle = ThemeColorStyle.DEFAULT,
        isDark = dark,
    )
}

fun AriesActionStyle.gradientBrush(): Brush =
    Brush.linearGradient(
        0f to gradientStart,
        0.5f to interpolate(gradientStart, gradientEnd),
        1f to gradientEnd,
    )

internal fun createStaticAriesActionStyle(
    context: Context,
    themeColorStyle: ThemeColorStyle,
    isDark: Boolean,
): AriesActionStyle {
    val resources = actionResources(themeColorStyle)
    return AriesActionStyle(
        gradientStart = context.requireThemeColor(resources.gradientStart),
        gradientEnd = context.requireThemeColor(resources.gradientEnd),
        onGradient = context.requireThemeColor(R.color.aries_action_on_gradient),
        glassSurface = context.requireThemeColor(R.color.aries_action_glass_surface),
        glassBorder = context.requireThemeColor(R.color.aries_action_glass_border),
        glassHighlight = context.requireThemeColor(R.color.aries_action_glass_highlight),
        selectedTint = context.requireThemeColor(resources.selectedTint),
        homeGlowStart = context.requireThemeColor(resources.homeGlowStart),
        homeGlowEnd = context.requireThemeColor(resources.homeGlowEnd),
        pressHighlightAlpha = context.themeFloat(R.dimen.aries_action_press_highlight_alpha),
        isDark = isDark,
    )
}

internal fun createDynamicAriesActionStyle(
    context: Context,
    colorScheme: ColorScheme,
    isDark: Boolean,
): AriesActionStyle {
    val configuredHighlight = context.themeFloat(R.dimen.aries_action_press_highlight_alpha)
    val darkForeground = context.requireThemeColor(R.color.aries_action_contrast_dark)
    val lightForeground = context.requireThemeColor(R.color.aries_action_contrast_light)
    val highlightBase = context.requireThemeColor(R.color.aries_action_highlight_base)
    val foreground = bestForeground(colorScheme.onPrimary, colorScheme.primary, darkForeground, lightForeground)
    val end =
        if (minimumHighlightedContrast(foreground, colorScheme.primary, colorScheme.secondary, configuredHighlight, highlightBase) >= MIN_CONTRAST) {
            colorScheme.secondary
        } else {
            colorScheme.primary
        }
    val highlightAlpha =
        if (minimumHighlightedContrast(foreground, colorScheme.primary, end, configuredHighlight, highlightBase) >= MIN_CONTRAST) {
            configuredHighlight
        } else {
            0f
        }
    val selectedAlpha = context.themeFloat(R.dimen.aries_action_selected_alpha)
    val glowStartAlpha = context.themeFloat(R.dimen.aries_action_glow_start_alpha)
    val glowEndAlpha = context.themeFloat(R.dimen.aries_action_glow_end_alpha)
    return AriesActionStyle(
        gradientStart = colorScheme.primary,
        gradientEnd = end,
        onGradient = foreground,
        glassSurface = context.requireThemeColor(R.color.aries_action_glass_surface),
        glassBorder = context.requireThemeColor(R.color.aries_action_glass_border),
        glassHighlight = highlightBase.copy(alpha = highlightAlpha),
        selectedTint = colorScheme.primary.copy(alpha = selectedAlpha),
        homeGlowStart = colorScheme.primary.copy(alpha = glowStartAlpha),
        homeGlowEnd = end.copy(alpha = glowEndAlpha),
        pressHighlightAlpha = highlightAlpha,
        isDark = isDark,
    )
}

internal fun Context.ariesThemeColor(@ColorRes colorResId: Int): Color? =
    runCatching { Color(ContextCompat.getColor(this, colorResId)) }.getOrNull()

internal fun Context.requireThemeColor(@ColorRes colorResId: Int): Color =
    requireNotNull(ariesThemeColor(colorResId)) { "Missing theme color resource: $colorResId" }

internal fun Context.withNightMode(configuration: Configuration, isDark: Boolean): Context {
    val themedConfiguration = Configuration(configuration).apply {
        val mode = if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
    }
    return createConfigurationContext(themedConfiguration)
}

private data class ActionResourceSet(
    @ColorRes val gradientStart: Int,
    @ColorRes val gradientEnd: Int,
    @ColorRes val selectedTint: Int,
    @ColorRes val homeGlowStart: Int,
    @ColorRes val homeGlowEnd: Int,
)

private fun actionResources(style: ThemeColorStyle): ActionResourceSet =
    when (style) {
        ThemeColorStyle.DEFAULT, ThemeColorStyle.DYNAMIC -> ActionResourceSet(R.color.aries_action_default_gradient_start, R.color.aries_action_default_gradient_end, R.color.aries_action_default_selected_tint, R.color.aries_action_default_home_glow_start, R.color.aries_action_default_home_glow_end)
        ThemeColorStyle.OCEAN -> ActionResourceSet(R.color.aries_action_ocean_gradient_start, R.color.aries_action_ocean_gradient_end, R.color.aries_action_ocean_selected_tint, R.color.aries_action_ocean_home_glow_start, R.color.aries_action_ocean_home_glow_end)
        ThemeColorStyle.FOREST -> ActionResourceSet(R.color.aries_action_forest_gradient_start, R.color.aries_action_forest_gradient_end, R.color.aries_action_forest_selected_tint, R.color.aries_action_forest_home_glow_start, R.color.aries_action_forest_home_glow_end)
        ThemeColorStyle.SUNSET -> ActionResourceSet(R.color.aries_action_sunset_gradient_start, R.color.aries_action_sunset_gradient_end, R.color.aries_action_sunset_selected_tint, R.color.aries_action_sunset_home_glow_start, R.color.aries_action_sunset_home_glow_end)
        ThemeColorStyle.ROSE -> ActionResourceSet(R.color.aries_action_rose_gradient_start, R.color.aries_action_rose_gradient_end, R.color.aries_action_rose_selected_tint, R.color.aries_action_rose_home_glow_start, R.color.aries_action_rose_home_glow_end)
    }

private fun Context.themeFloat(@DimenRes resourceId: Int): Float {
    val value = TypedValue()
    resources.getValue(resourceId, value, true)
    return value.float
}

private fun bestForeground(
    preferred: Color,
    background: Color,
    darkCandidate: Color,
    lightCandidate: Color,
): Color = listOf(preferred, darkCandidate, lightCandidate).maxBy { contrast(it, background) }

private fun minimumHighlightedContrast(
    foreground: Color,
    start: Color,
    end: Color,
    highlightAlpha: Float,
    highlightBase: Color,
): Double {
    val highlight = highlightBase.copy(alpha = highlightAlpha)
    return (0..20).minOf { step ->
        contrast(foreground, highlight.compositeOver(interpolate(start, end, step / 20f)))
    }
}

private fun contrast(foreground: Color, background: Color): Double {
    val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
    val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
    return (lighter + 0.05) / (darker + 0.05)
}

private fun interpolate(start: Color, end: Color, fraction: Float = 0.5f): Color =
    Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction,
    )

private const val MIN_CONTRAST = 4.5
