package com.ai.phoneagent.core.designsystem.theme

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.phoneagent.core.designsystem.R

/** 默认中文字体族：Noto Sans SC，按字重映射可变字体轴。 */
@OptIn(ExperimentalTextApi::class)
val AriesFontFamily = FontFamily(
    Font(R.font.noto_sans_sc, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_sc, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_sc, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_sc, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/**
 * Aries Material 主题。
 *
 * 配色优先级：AMOLED 纯黑 > 所选配色风格 > 资源表面阶梯。
 *
 * @param themeMode 明暗模式，默认跟随系统。
 * @param themeColorStyle 主题配色的唯一来源。
 * @param amoledDark 深色模式下是否强制纯黑背景。
 * @param fontScale 应用于全部 M3 字号的比例系数。
 * @param fontFamily 应用于全部 M3 文本样式的字体族。
 * @param content 主题内的可组合内容。
 */
@Composable
fun AriesMaterialTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColorStyle: ThemeColorStyle = ThemeColorStyle.DEFAULT,
    amoledDark: Boolean = false,
    fontScale: Float = 1.0f,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    // 1. 根据 themeMode 解析深/浅色
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val themeResourceContext = remember(context, configuration, darkTheme) {
        val themedConfiguration = Configuration(configuration).apply {
            val nightMode = if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        context.createConfigurationContext(themedConfiguration)
    }

    val effectiveThemeColorStyle =
        if (themeColorStyle.isDynamic && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ThemeColorStyle.DEFAULT
        } else {
            themeColorStyle
        }

    // 2. 基础配色方案：动态取色或基于资源的预设风格
    val baseColorScheme: ColorScheme = when {
        effectiveThemeColorStyle.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            buildDarkTokenColorScheme(themeResourceContext)
        }
        else -> {
            buildLightTokenColorScheme(themeResourceContext)
        }
    }

    val styledColorScheme =
        if (effectiveThemeColorStyle.isDynamic) {
            baseColorScheme
        } else {
            baseColorScheme
                .applyThemeAccent(
                    themeAccent = effectiveThemeColorStyle.accentOrDefault,
                    isDarkTheme = darkTheme,
                )
                .applyTokenSurfaceLadder(themeResourceContext)
        }

    val colorScheme: ColorScheme = if (darkTheme && amoledDark) {
        styledColorScheme.copy(
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
        styledColorScheme
    }

    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            small = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
            medium = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_md)),
            large = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_lg)),
            extraLarge = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_xl)),
        )

    // 4. 字号缩放 + 字体族：更新全部 M3 字号文本样式
    val effectiveFontFamily = if (fontFamily == FontFamily.Default) AriesFontFamily else fontFamily
    val baseTypography = Typography(
        headlineLarge = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_hero).toSp() }, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_headline).toSp() }, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_title).toSp() }, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_body).toSp() }),
        bodyMedium = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_body).toSp() }),
        bodySmall = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { dimensionResource(R.dimen.aries_type_caption).toSp() }),
    )
    val typography: Typography =
        Typography(
            displayLarge = baseTypography.displayLarge.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            displayMedium = baseTypography.displayMedium.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            displaySmall = baseTypography.displaySmall.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            headlineLarge = baseTypography.headlineLarge.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            headlineMedium = baseTypography.headlineMedium.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            headlineSmall = baseTypography.headlineSmall.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            titleLarge = baseTypography.titleLarge.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            titleMedium = baseTypography.titleMedium.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            titleSmall = baseTypography.titleSmall.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            bodyLarge = baseTypography.bodyLarge.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            bodyMedium = baseTypography.bodyMedium.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            bodySmall = baseTypography.bodySmall.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            labelLarge = baseTypography.labelLarge.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            labelMedium = baseTypography.labelMedium.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
            labelSmall = baseTypography.labelSmall.scaledBy(fontScale).withFontFamily(effectiveFontFamily),
        )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography,
        content = {
            CompositionLocalProvider(
                LocalAriesBackdrop provides AriesBackdropStyle(darkTheme, amoledDark, effectiveThemeColorStyle == ThemeColorStyle.DEFAULT),
                androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
            ) {
                AriesBackdrop { content() }
            }
        },
    )
}

/** 按比例缩放文本字号。 */
private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor)

/** 替换文本样式的字体族。 */
private fun TextStyle.withFontFamily(fontFamily: FontFamily): TextStyle =
    copy(fontFamily = fontFamily)

/** 基于资源令牌构建深色配色方案。 */
private fun buildDarkTokenColorScheme(context: Context): ColorScheme =
    darkColorScheme(
        primary = context.themeColor(R.color.m3t_primary),
        onPrimary = context.themeColor(R.color.m3t_on_primary),
        primaryContainer = context.themeColor(R.color.m3t_primary_container),
        onPrimaryContainer = context.themeColor(R.color.m3t_on_primary_container),
        secondary = context.themeColor(R.color.m3t_secondary),
        onSecondary = context.themeColor(R.color.m3t_on_secondary),
        secondaryContainer = context.themeColor(R.color.m3t_secondary_container),
        onSecondaryContainer = context.themeColor(R.color.m3t_on_secondary_container),
        tertiary = context.themeColor(R.color.m3t_tertiary),
        onTertiary = context.themeColor(R.color.m3t_on_tertiary),
        error = context.themeColor(R.color.m3t_error),
        onError = context.themeColor(R.color.m3t_on_error),
        errorContainer = context.themeColor(R.color.m3t_error_container),
        onErrorContainer = context.themeColor(R.color.m3t_on_error_container),
        background = context.themeColor(R.color.m3t_background),
        onBackground = context.themeColor(R.color.m3t_on_background),
        surface = context.themeColor(R.color.m3t_surface),
        onSurface = context.themeColor(R.color.m3t_on_surface),
        surfaceVariant = context.themeColor(R.color.m3t_surface_variant),
        onSurfaceVariant = context.themeColor(R.color.m3t_on_surface_variant),
        outline = context.themeColor(R.color.m3t_outline),
        outlineVariant = context.themeColor(R.color.m3t_outline_variant),
        inverseSurface = context.themeColor(R.color.m3t_inverse_surface),
        inverseOnSurface = context.themeColor(R.color.m3t_inverse_on_surface),
    )

/** 基于资源令牌构建浅色配色方案。 */
private fun buildLightTokenColorScheme(context: Context): ColorScheme =
    lightColorScheme(
        primary = context.themeColor(R.color.m3t_primary),
        onPrimary = context.themeColor(R.color.m3t_on_primary),
        primaryContainer = context.themeColor(R.color.m3t_primary_container),
        onPrimaryContainer = context.themeColor(R.color.m3t_on_primary_container),
        secondary = context.themeColor(R.color.m3t_secondary),
        onSecondary = context.themeColor(R.color.m3t_on_secondary),
        secondaryContainer = context.themeColor(R.color.m3t_secondary_container),
        onSecondaryContainer = context.themeColor(R.color.m3t_on_secondary_container),
        tertiary = context.themeColor(R.color.m3t_tertiary),
        onTertiary = context.themeColor(R.color.m3t_on_tertiary),
        error = context.themeColor(R.color.m3t_error),
        onError = context.themeColor(R.color.m3t_on_error),
        errorContainer = context.themeColor(R.color.m3t_error_container),
        onErrorContainer = context.themeColor(R.color.m3t_on_error_container),
        background = context.themeColor(R.color.m3t_background),
        onBackground = context.themeColor(R.color.m3t_on_background),
        surface = context.themeColor(R.color.m3t_surface),
        onSurface = context.themeColor(R.color.m3t_on_surface),
        surfaceVariant = context.themeColor(R.color.m3t_surface_variant),
        onSurfaceVariant = context.themeColor(R.color.m3t_on_surface_variant),
        outline = context.themeColor(R.color.m3t_outline),
        outlineVariant = context.themeColor(R.color.m3t_outline_variant),
        inverseSurface = context.themeColor(R.color.m3t_inverse_surface),
        inverseOnSurface = context.themeColor(R.color.m3t_inverse_on_surface),
    )

/** 用资源令牌覆盖表面层级（surfaceContainer 系列）与描边色。 */
private fun ColorScheme.applyTokenSurfaceLadder(context: Context): ColorScheme =
    copy(
        background = context.themeColor(R.color.m3t_background),
        onBackground = context.themeColor(R.color.m3t_on_background),
        surface = context.themeColor(R.color.m3t_surface),
        onSurface = context.themeColor(R.color.m3t_on_surface),
        surfaceVariant = context.themeColor(R.color.m3t_surface_variant),
        onSurfaceVariant = context.themeColor(R.color.m3t_on_surface_variant),
        outline = context.themeColor(R.color.m3t_outline),
        outlineVariant = context.themeColor(R.color.m3t_outline_variant),
        inverseSurface = context.themeColor(R.color.m3t_inverse_surface),
        inverseOnSurface = context.themeColor(R.color.m3t_inverse_on_surface),
        surfaceDim = context.themeColor(R.color.m3t_surface_container),
        surfaceBright = context.themeColor(R.color.m3t_surface),
        surfaceContainerLowest = context.themeColor(R.color.m3t_background),
        surfaceContainerLow = context.themeColor(R.color.m3t_surface),
        surfaceContainer = context.themeColor(R.color.m3t_surface_container),
        surfaceContainerHigh = context.themeColor(R.color.m3t_surface_container_high),
        surfaceContainerHighest = context.themeColor(R.color.m3t_surface_variant),
    )

/** 将颜色资源解析为 Compose [Color]。 */
private fun Context.themeColor(colorResId: Int): Color =
    Color(ContextCompat.getColor(this, colorResId))

/** 主题预览用的示例内容。 */
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

/** 预览：AMOLED + 动态取色（深色）。 */
@Preview(showBackground = true, name = "AMOLED + Dynamic (Dark)")
@Composable
private fun AriesMaterialThemeAmoledDynamicDarkPreview() {
    AriesMaterialTheme(
        themeMode = ThemeMode.DARK,
        themeColorStyle = ThemeColorStyle.DYNAMIC,
        amoledDark = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            AriesThemePreviewContent()
        }
    }
}

/** 预览：非 AMOLED + 动态取色（深色）。 */
@Preview(showBackground = true, name = "Non-AMOLED + Dynamic (Dark)")
@Composable
private fun AriesMaterialThemeDynamicDarkPreview() {
    AriesMaterialTheme(
        themeMode = ThemeMode.DARK,
        themeColorStyle = ThemeColorStyle.DYNAMIC,
        amoledDark = false,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            AriesThemePreviewContent()
        }
    }
}
