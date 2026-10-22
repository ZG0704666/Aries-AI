package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.runtime.Composable

/**
 * 主题配色风格。
 *
 * 在固定强调色之外额外提供 [DYNAMIC]（跟随系统动态取色，此时 [accent] 为 null）。
 *
 * @property storageKey 持久化到本地配置时使用的键名。
 * @property accent 对应的固定强调色；动态取色时为 null。
 */
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

    /** 是否为动态取色风格。 */
    val isDynamic: Boolean
        get() = this == DYNAMIC

    /** 返回对应强调色，动态取色时回退到 [ThemeAccent.DEFAULT]。 */
    val accentOrDefault: ThemeAccent
        get() = accent ?: ThemeAccent.DEFAULT

    companion object {
        /** 从持久化字符串还原配色风格，无法识别时回退到 [DEFAULT]。 */
        fun fromStorage(value: String): ThemeColorStyle =
            entries.firstOrNull { it.storageKey == value.lowercase() } ?: DEFAULT
    }
}

/** 返回预览配色；动态取色无固定色板时返回 null。 */
@Composable
fun ThemeColorStyle.previewColors(isDarkTheme: Boolean): ThemeAccentPreview? =
    accent?.previewColors(isDarkTheme)
