package com.ai.phoneagent.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R

@Composable
fun AriesMaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
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

    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            small = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            medium = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_md)),
            large = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_lg)),
            extraLarge = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_xl)),
        )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = Typography(),
        content = content,
    )
}
