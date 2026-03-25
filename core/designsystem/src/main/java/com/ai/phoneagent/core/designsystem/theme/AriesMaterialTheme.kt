package com.ai.phoneagent.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * @param fontFamily   Font family applied to all Material 3 text styles.
 * @param content      Composable content within this theme.
 */
@Composable
fun AriesMaterialTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoledDark: Boolean = false,
    dynamicColor: Boolean = true,
    fontScale: Float = 1.0f,
    fontFamily: FontFamily = FontFamily.Default,
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

    // 3. AMOLED override: keep semantic colors, remap only surface roles to deep ladder
    val colorScheme: ColorScheme = if (darkTheme && amoledDark) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF050505),
            surfaceVariant = Color(0xFF1A1A1A),
            surfaceDim = Color(0xFF030303),
            surfaceBright = Color(0xFF2A2A2A),
            surfaceContainerLowest = Color(0xFF020202),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF2A2A2A),
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

    // 4. Font scale + font family: update all M3 type-scale TextStyles
    val baseTypography = Typography()
    val typography: Typography =
        Typography(
            displayLarge = baseTypography.displayLarge.scaledBy(fontScale).withFontFamily(fontFamily),
            displayMedium = baseTypography.displayMedium.scaledBy(fontScale).withFontFamily(fontFamily),
            displaySmall = baseTypography.displaySmall.scaledBy(fontScale).withFontFamily(fontFamily),
            headlineLarge = baseTypography.headlineLarge.scaledBy(fontScale).withFontFamily(fontFamily),
            headlineMedium = baseTypography.headlineMedium.scaledBy(fontScale).withFontFamily(fontFamily),
            headlineSmall = baseTypography.headlineSmall.scaledBy(fontScale).withFontFamily(fontFamily),
            titleLarge = baseTypography.titleLarge.scaledBy(fontScale).withFontFamily(fontFamily),
            titleMedium = baseTypography.titleMedium.scaledBy(fontScale).withFontFamily(fontFamily),
            titleSmall = baseTypography.titleSmall.scaledBy(fontScale).withFontFamily(fontFamily),
            bodyLarge = baseTypography.bodyLarge.scaledBy(fontScale).withFontFamily(fontFamily),
            bodyMedium = baseTypography.bodyMedium.scaledBy(fontScale).withFontFamily(fontFamily),
            bodySmall = baseTypography.bodySmall.scaledBy(fontScale).withFontFamily(fontFamily),
            labelLarge = baseTypography.labelLarge.scaledBy(fontScale).withFontFamily(fontFamily),
            labelMedium = baseTypography.labelMedium.scaledBy(fontScale).withFontFamily(fontFamily),
            labelSmall = baseTypography.labelSmall.scaledBy(fontScale).withFontFamily(fontFamily),
        )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography,
        content = content,
    )
}

private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor)

private fun TextStyle.withFontFamily(fontFamily: FontFamily): TextStyle =
    copy(fontFamily = fontFamily)

@Composable
private fun AriesThemePreviewContent() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Primary", color = MaterialTheme.colorScheme.primary)
        Text(text = "Secondary", color = MaterialTheme.colorScheme.secondary)
        Text(text = "Tertiary", color = MaterialTheme.colorScheme.tertiary)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        )
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
            Text(
                text = "Surface Container Low",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
            Text(
                text = "Surface Container High",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Preview(showBackground = true, name = "AMOLED + Dynamic (Dark)")
@Composable
private fun AriesMaterialThemeAmoledDynamicDarkPreview() {
    AriesMaterialTheme(
        themeMode = ThemeMode.DARK,
        amoledDark = true,
        dynamicColor = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            AriesThemePreviewContent()
        }
    }
}

@Preview(showBackground = true, name = "Non-AMOLED + Dynamic (Dark)")
@Composable
private fun AriesMaterialThemeDynamicDarkPreview() {
    AriesMaterialTheme(
        themeMode = ThemeMode.DARK,
        amoledDark = false,
        dynamicColor = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            AriesThemePreviewContent()
        }
    }
}
