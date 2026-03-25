package com.ai.phoneagent.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import com.ai.phoneagent.core.designsystem.R

/**
 * Aries Material Theme.
 *
 * Priority: AMOLED > Dynamic Color (Android 12+) > Custom token color scheme.
 *
 * @param themeMode    Controls light/dark selection. Defaults to system setting.
 * @param amoledDark   When true and dark is active, forces pure-black backgrounds.
 * @param dynamicColor When true (Android 12+), uses Material You dynamic color.
 * @param fontScale    Multiplier applied to all Material 3 type-scale font sizes.
 * @param content      Composable content within this theme.
 */
@Composable
fun AriesMaterialTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoledDark: Boolean = false,
    dynamicColor: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    // 1. Resolve dark/light from themeMode
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current

    // 2. Base color scheme: Dynamic Color (Android 12+) > Custom token scheme
    val baseColorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            darkColorScheme(
                primary = colorResource(R.color.m3t_primary),
                onPrimary = colorResource(R.color.m3t_on_primary),
                primaryContainer = colorResource(R.color.m3t_primary_container),
                onPrimaryContainer = colorResource(R.color.m3t_on_primary_container),
                secondary = colorResource(R.color.m3t_secondary),
                onSecondary = colorResource(R.color.m3t_on_secondary),
                secondaryContainer = colorResource(R.color.m3t_secondary_container),
                onSecondaryContainer = colorResource(R.color.m3t_on_secondary_container),
                tertiary = colorResource(R.color.m3t_tertiary),
                onTertiary = colorResource(R.color.m3t_on_tertiary),
                error = colorResource(R.color.m3t_error),
                onError = colorResource(R.color.m3t_on_error),
                errorContainer = colorResource(R.color.m3t_error_container),
                onErrorContainer = colorResource(R.color.m3t_on_error_container),
                background = colorResource(R.color.m3t_background),
                onBackground = colorResource(R.color.m3t_on_background),
                surface = colorResource(R.color.m3t_surface),
                onSurface = colorResource(R.color.m3t_on_surface),
                surfaceVariant = colorResource(R.color.m3t_surface_variant),
                onSurfaceVariant = colorResource(R.color.m3t_on_surface_variant),
                outline = colorResource(R.color.m3t_outline),
                outlineVariant = colorResource(R.color.m3t_outline_variant),
                inverseSurface = colorResource(R.color.m3t_inverse_surface),
                inverseOnSurface = colorResource(R.color.m3t_inverse_on_surface),
            )
        }
        else -> {
            lightColorScheme(
                primary = colorResource(R.color.m3t_primary),
                onPrimary = colorResource(R.color.m3t_on_primary),
                primaryContainer = colorResource(R.color.m3t_primary_container),
                onPrimaryContainer = colorResource(R.color.m3t_on_primary_container),
                secondary = colorResource(R.color.m3t_secondary),
                onSecondary = colorResource(R.color.m3t_on_secondary),
                secondaryContainer = colorResource(R.color.m3t_secondary_container),
                onSecondaryContainer = colorResource(R.color.m3t_on_secondary_container),
                tertiary = colorResource(R.color.m3t_tertiary),
                onTertiary = colorResource(R.color.m3t_on_tertiary),
                error = colorResource(R.color.m3t_error),
                onError = colorResource(R.color.m3t_on_error),
                errorContainer = colorResource(R.color.m3t_error_container),
                onErrorContainer = colorResource(R.color.m3t_on_error_container),
                background = colorResource(R.color.m3t_background),
                onBackground = colorResource(R.color.m3t_on_background),
                surface = colorResource(R.color.m3t_surface),
                onSurface = colorResource(R.color.m3t_on_surface),
                surfaceVariant = colorResource(R.color.m3t_surface_variant),
                onSurfaceVariant = colorResource(R.color.m3t_on_surface_variant),
                outline = colorResource(R.color.m3t_outline),
                outlineVariant = colorResource(R.color.m3t_outline_variant),
                inverseSurface = colorResource(R.color.m3t_inverse_surface),
                inverseOnSurface = colorResource(R.color.m3t_inverse_on_surface),
            )
        }
    }

    // 3. AMOLED override: pure-black backgrounds when dark && amoledDark
    val colorScheme: ColorScheme = if (darkTheme && amoledDark) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0A0A0A),
        )
    } else {
        baseColorScheme
    }

    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            small = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            medium = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_md)),
            large = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_lg)),
            extraLarge = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_xl)),
        )

    // 4. Font scale: multiply all M3 type-scale TextStyle fontSizes
    val baseTypography = Typography()
    val typography: Typography = if (fontScale == 1.0f) {
        baseTypography
    } else {
        Typography(
            displayLarge = baseTypography.displayLarge.scaledBy(fontScale),
            displayMedium = baseTypography.displayMedium.scaledBy(fontScale),
            displaySmall = baseTypography.displaySmall.scaledBy(fontScale),
            headlineLarge = baseTypography.headlineLarge.scaledBy(fontScale),
            headlineMedium = baseTypography.headlineMedium.scaledBy(fontScale),
            headlineSmall = baseTypography.headlineSmall.scaledBy(fontScale),
            titleLarge = baseTypography.titleLarge.scaledBy(fontScale),
            titleMedium = baseTypography.titleMedium.scaledBy(fontScale),
            titleSmall = baseTypography.titleSmall.scaledBy(fontScale),
            bodyLarge = baseTypography.bodyLarge.scaledBy(fontScale),
            bodyMedium = baseTypography.bodyMedium.scaledBy(fontScale),
            bodySmall = baseTypography.bodySmall.scaledBy(fontScale),
            labelLarge = baseTypography.labelLarge.scaledBy(fontScale),
            labelMedium = baseTypography.labelMedium.scaledBy(fontScale),
            labelSmall = baseTypography.labelSmall.scaledBy(fontScale),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography,
        content = content,
    )
}

private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor)
