package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.runtime.Composable

enum class ThemeColorStyle(
    val storageKey: String,
    val accent: ThemeAccent?,
) {
    DEFAULT("default", ThemeAccent.DEFAULT),
    OCEAN("ocean", ThemeAccent.OCEAN),
    FOREST("forest", ThemeAccent.FOREST),
    SUNSET("sunset", ThemeAccent.SUNSET),
    ROSE("rose", ThemeAccent.ROSE),
    DYNAMIC("dynamic", null),
    ;

    val isDynamic: Boolean
        get() = this == DYNAMIC

    val accentOrDefault: ThemeAccent
        get() = accent ?: ThemeAccent.DEFAULT

    companion object {
        fun fromStorage(value: String): ThemeColorStyle =
            entries.firstOrNull { it.storageKey == value.lowercase() } ?: DEFAULT
    }
}

@Composable
fun ThemeColorStyle.previewColors(isDarkTheme: Boolean): ThemeAccentPreview? =
    accent?.previewColors(isDarkTheme)
