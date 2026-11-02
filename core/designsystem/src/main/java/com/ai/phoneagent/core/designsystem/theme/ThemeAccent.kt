package com.ai.phoneagent.core.designsystem.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ai.phoneagent.core.designsystem.R

/**
 * 主题强调色枚举。
 *
 * 色值统一由 `action_tokens.xml`（values / values-night）提供，经主题化
 * Context 解析后自动跟随明暗限定符；[DEFAULT] 使用 m3t 默认色板资源。
 * 强调色主色与 `AriesActionStyle` 的动作渐变起点同源，保证按钮渐变与
 * 全局 Material 角色色一致。
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

/** 返回该强调色在指定明暗模式下的预览配色；不依赖系统当前明暗设置。 */
@Composable
fun ThemeAccent.previewColors(isDarkTheme: Boolean): ThemeAccentPreview {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val themedContext = remember(context, configuration, isDarkTheme) {
        context.withNightMode(configuration, isDarkTheme)
    }
    return when (this) {
        ThemeAccent.DEFAULT ->
            ThemeAccentPreview(
                primary = themedContext.requireThemeColor(R.color.m3t_primary),
                secondary = themedContext.requireThemeColor(R.color.m3t_secondary),
                tertiary = themedContext.requireThemeColor(R.color.m3t_tertiary),
            )

        else -> {
            val palette =
                requireNotNull(resourcePalette(themedContext)) { "ThemeAccent palette missing for $this" }
            ThemeAccentPreview(
                primary = palette.primary,
                secondary = palette.secondary,
                tertiary = palette.tertiary,
            )
        }
    }
}

/**
 * 将强调色配色叠加到基础 [ColorScheme]；颜色取自主题化资源，
 * 与 `AriesActionStyle` 的动作渐变同源（`aries_<accent>_primary` 即渐变起点色）。
 * [ThemeAccent.DEFAULT] 强调色保持原样返回。
 */
internal fun ColorScheme.applyThemeAccent(
    themeAccent: ThemeAccent,
    context: Context,
): ColorScheme {
    val palette = themeAccent.resourcePalette(context) ?: return this
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
        surfaceTint = palette.surfaceTint,
    )
}

/** 资源驱动的完整强调色色板。 */
internal data class ThemeAccentResourcePalette(
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
    val surfaceTint: Color,
)

/** 各强调色对应的资源 ID 组。 */
private data class ThemeAccentResourceIds(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surfaceTint: Int,
)

private fun ThemeAccent.resourceIds(): ThemeAccentResourceIds = when (this) {
    ThemeAccent.DEFAULT -> throw IllegalStateException("DEFAULT accent uses m3t resources directly")
    ThemeAccent.OCEAN -> ThemeAccentResourceIds(
        primary = R.color.aries_ocean_primary,
        onPrimary = R.color.aries_ocean_on_primary,
        primaryContainer = R.color.aries_ocean_primary_container,
        onPrimaryContainer = R.color.aries_ocean_on_primary_container,
        inversePrimary = R.color.aries_ocean_inverse_primary,
        secondary = R.color.aries_ocean_secondary,
        onSecondary = R.color.aries_ocean_on_secondary,
        secondaryContainer = R.color.aries_ocean_secondary_container,
        onSecondaryContainer = R.color.aries_ocean_on_secondary_container,
        tertiary = R.color.aries_ocean_tertiary,
        onTertiary = R.color.aries_ocean_on_tertiary,
        tertiaryContainer = R.color.aries_ocean_tertiary_container,
        onTertiaryContainer = R.color.aries_ocean_on_tertiary_container,
        surfaceTint = R.color.aries_ocean_surface_tint,
    )

    ThemeAccent.FOREST -> ThemeAccentResourceIds(
        primary = R.color.aries_forest_primary,
        onPrimary = R.color.aries_forest_on_primary,
        primaryContainer = R.color.aries_forest_primary_container,
        onPrimaryContainer = R.color.aries_forest_on_primary_container,
        inversePrimary = R.color.aries_forest_inverse_primary,
        secondary = R.color.aries_forest_secondary,
        onSecondary = R.color.aries_forest_on_secondary,
        secondaryContainer = R.color.aries_forest_secondary_container,
        onSecondaryContainer = R.color.aries_forest_on_secondary_container,
        tertiary = R.color.aries_forest_tertiary,
        onTertiary = R.color.aries_forest_on_tertiary,
        tertiaryContainer = R.color.aries_forest_tertiary_container,
        onTertiaryContainer = R.color.aries_forest_on_tertiary_container,
        surfaceTint = R.color.aries_forest_surface_tint,
    )

    ThemeAccent.SUNSET -> ThemeAccentResourceIds(
        primary = R.color.aries_sunset_primary,
        onPrimary = R.color.aries_sunset_on_primary,
        primaryContainer = R.color.aries_sunset_primary_container,
        onPrimaryContainer = R.color.aries_sunset_on_primary_container,
        inversePrimary = R.color.aries_sunset_inverse_primary,
        secondary = R.color.aries_sunset_secondary,
        onSecondary = R.color.aries_sunset_on_secondary,
        secondaryContainer = R.color.aries_sunset_secondary_container,
        onSecondaryContainer = R.color.aries_sunset_on_secondary_container,
        tertiary = R.color.aries_sunset_tertiary,
        onTertiary = R.color.aries_sunset_on_tertiary,
        tertiaryContainer = R.color.aries_sunset_tertiary_container,
        onTertiaryContainer = R.color.aries_sunset_on_tertiary_container,
        surfaceTint = R.color.aries_sunset_surface_tint,
    )

    ThemeAccent.ROSE -> ThemeAccentResourceIds(
        primary = R.color.aries_rose_primary,
        onPrimary = R.color.aries_rose_on_primary,
        primaryContainer = R.color.aries_rose_primary_container,
        onPrimaryContainer = R.color.aries_rose_on_primary_container,
        inversePrimary = R.color.aries_rose_inverse_primary,
        secondary = R.color.aries_rose_secondary,
        onSecondary = R.color.aries_rose_on_secondary,
        secondaryContainer = R.color.aries_rose_secondary_container,
        onSecondaryContainer = R.color.aries_rose_on_secondary_container,
        tertiary = R.color.aries_rose_tertiary,
        onTertiary = R.color.aries_rose_on_tertiary,
        tertiaryContainer = R.color.aries_rose_tertiary_container,
        onTertiaryContainer = R.color.aries_rose_on_tertiary_container,
        surfaceTint = R.color.aries_rose_surface_tint,
    )
}

private fun ThemeAccent.resourcePalette(context: Context): ThemeAccentResourcePalette? =
    when (this) {
        ThemeAccent.DEFAULT -> null
        else -> {
            val ids = resourceIds()
            fun color(id: Int) = Color(ContextCompat.getColor(context, id))
            ThemeAccentResourcePalette(
                primary = color(ids.primary),
                onPrimary = color(ids.onPrimary),
                primaryContainer = color(ids.primaryContainer),
                onPrimaryContainer = color(ids.onPrimaryContainer),
                inversePrimary = color(ids.inversePrimary),
                secondary = color(ids.secondary),
                onSecondary = color(ids.onSecondary),
                secondaryContainer = color(ids.secondaryContainer),
                onSecondaryContainer = color(ids.onSecondaryContainer),
                tertiary = color(ids.tertiary),
                onTertiary = color(ids.onTertiary),
                tertiaryContainer = color(ids.tertiaryContainer),
                onTertiaryContainer = color(ids.onTertiaryContainer),
                surfaceTint = color(ids.surfaceTint),
            )
        }
    }
