package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ai.phoneagent.core.designsystem.R

/**
 * 主题强调色枚举。
 *
 * @property storageKey 持久化到本地配置时使用的键名。
 */
enum class ThemeAccent(
    val storageKey: String,
) {
    DEFAULT("default"),
    OCEAN("ocean"),
    FOREST("forest"),
    SUNSET("sunset"),
    ROSE("rose"),
    ;

    companion object {
        /** 从持久化字符串还原强调色，无法识别时回退到 [DEFAULT]。 */
        fun fromStorage(value: String): ThemeAccent =
            entries.firstOrNull { it.storageKey == value.lowercase() } ?: DEFAULT
    }
}

/** 强调色预览所需的三种主色。 */
data class ThemeAccentPreview(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

/** 返回该强调色在当前明暗模式下的预览配色。 */
@Composable
fun ThemeAccent.previewColors(isDarkTheme: Boolean): ThemeAccentPreview =
    when (this) {
        ThemeAccent.DEFAULT ->
            ThemeAccentPreview(
                primary = colorResource(R.color.m3t_primary),
                secondary = colorResource(R.color.m3t_secondary),
                tertiary = colorResource(R.color.m3t_tertiary),
            )

        else -> {
            val palette = palette(isDarkTheme) ?: error("ThemeAccent palette missing for $this")
            ThemeAccentPreview(
                primary = palette.primary,
                secondary = palette.secondary,
                tertiary = palette.tertiary,
            )
        }
    }

/** 将强调色配色叠加到基础 [ColorScheme]；默认强调色保持原样返回。 */
internal fun ColorScheme.applyThemeAccent(
    themeAccent: ThemeAccent,
    isDarkTheme: Boolean,
): ColorScheme {
    val palette = themeAccent.palette(isDarkTheme) ?: return this
    return copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        inversePrimary = palette.inversePrimary,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        surfaceTint = palette.primary,
    )
}

/** 单个强调色的完整 M3 色板。 */
private data class ThemeAccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

/** 按明暗模式返回对应色板；[ThemeAccent.DEFAULT] 使用资源默认色，返回 null。 */
private fun ThemeAccent.palette(isDarkTheme: Boolean): ThemeAccentPalette? =
    when (this) {
        ThemeAccent.DEFAULT -> null
        ThemeAccent.OCEAN -> if (isDarkTheme) oceanDarkPalette else oceanLightPalette
        ThemeAccent.FOREST -> if (isDarkTheme) forestDarkPalette else forestLightPalette
        ThemeAccent.SUNSET -> if (isDarkTheme) sunsetDarkPalette else sunsetLightPalette
        ThemeAccent.ROSE -> if (isDarkTheme) roseDarkPalette else roseLightPalette
    }

// ---- 海洋 Ocean ----
private val oceanLightPalette =
    ThemeAccentPalette(
        primary = Color(0xFF006495),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDE5FF),
        onPrimaryContainer = Color(0xFF001D31),
        inversePrimary = Color(0xFF8CCFFF),
        secondary = Color(0xFF50606F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3E5F6),
        onSecondaryContainer = Color(0xFF0C1D29),
        tertiary = Color(0xFF65587B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEBDCFF),
        onTertiaryContainer = Color(0xFF201534),
    )

private val oceanDarkPalette =
    ThemeAccentPalette(
        primary = Color(0xFF8CCFFF),
        onPrimary = Color(0xFF00344F),
        primaryContainer = Color(0xFF004B71),
        onPrimaryContainer = Color(0xFFCDE5FF),
        inversePrimary = Color(0xFF006495),
        secondary = Color(0xFFB8C9DA),
        onSecondary = Color(0xFF22323F),
        secondaryContainer = Color(0xFF394856),
        onSecondaryContainer = Color(0xFFD3E5F6),
        tertiary = Color(0xFFD0BFE7),
        onTertiary = Color(0xFF362B4A),
        tertiaryContainer = Color(0xFF4D4162),
        onTertiaryContainer = Color(0xFFEBDCFF),
    )

// ---- 森林 Forest ----
private val forestLightPalette =
    ThemeAccentPalette(
        primary = Color(0xFF2F6B3F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB2F1BC),
        onPrimaryContainer = Color(0xFF00210D),
        inversePrimary = Color(0xFF97D5A1),
        secondary = Color(0xFF516350),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD4E8CF),
        onSecondaryContainer = Color(0xFF10200F),
        tertiary = Color(0xFF3F6374),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC2E8FC),
        onTertiaryContainer = Color(0xFF001F2B),
    )

private val forestDarkPalette =
    ThemeAccentPalette(
        primary = Color(0xFF97D5A1),
        onPrimary = Color(0xFF003919),
        primaryContainer = Color(0xFF15522A),
        onPrimaryContainer = Color(0xFFB2F1BC),
        inversePrimary = Color(0xFF2F6B3F),
        secondary = Color(0xFFB8CCB4),
        onSecondary = Color(0xFF243423),
        secondaryContainer = Color(0xFF394B38),
        onSecondaryContainer = Color(0xFFD4E8CF),
        tertiary = Color(0xFFA6CCDF),
        onTertiary = Color(0xFF083544),
        tertiaryContainer = Color(0xFF264B5B),
        onTertiaryContainer = Color(0xFFC2E8FC),
    )

// ---- 日落 Sunset ----
private val sunsetLightPalette =
    ThemeAccentPalette(
        primary = Color(0xFF9A4A17),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCC8),
        onPrimaryContainer = Color(0xFF351000),
        inversePrimary = Color(0xFFFFB68B),
        secondary = Color(0xFF77574A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDBCD),
        onSecondaryContainer = Color(0xFF2C160C),
        tertiary = Color(0xFF685E2F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF0E3A7),
        onTertiaryContainer = Color(0xFF211B00),
    )

private val sunsetDarkPalette =
    ThemeAccentPalette(
        primary = Color(0xFFFFB68B),
        onPrimary = Color(0xFF5A1F00),
        primaryContainer = Color(0xFF7B3100),
        onPrimaryContainer = Color(0xFFFFDCC8),
        inversePrimary = Color(0xFF9A4A17),
        secondary = Color(0xFFE7BEAB),
        onSecondary = Color(0xFF442A1F),
        secondaryContainer = Color(0xFF5D4033),
        onSecondaryContainer = Color(0xFFFFDBCD),
        tertiary = Color(0xFFD3C78D),
        onTertiary = Color(0xFF393007),
        tertiaryContainer = Color(0xFF50461B),
        onTertiaryContainer = Color(0xFFF0E3A7),
    )

// ---- 玫瑰 Rose ----
private val roseLightPalette =
    ThemeAccentPalette(
        primary = Color(0xFF9C415D),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001B),
        inversePrimary = Color(0xFFFFB1C5),
        secondary = Color(0xFF75565F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF2C151C),
        tertiary = Color(0xFF7B5B2E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDEA7),
        onTertiaryContainer = Color(0xFF2A1700),
    )

private val roseDarkPalette =
    ThemeAccentPalette(
        primary = Color(0xFFFFB1C5),
        onPrimary = Color(0xFF61112F),
        primaryContainer = Color(0xFF7E2946),
        onPrimaryContainer = Color(0xFFFFD9E2),
        inversePrimary = Color(0xFF9C415D),
        secondary = Color(0xFFE4BDC7),
        onSecondary = Color(0xFF43292F),
        secondaryContainer = Color(0xFF5B3F46),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFEDC48C),
        onTertiary = Color(0xFF452B04),
        tertiaryContainer = Color(0xFF604111),
        onTertiaryContainer = Color(0xFFFFDEA7),
    )