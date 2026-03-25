package com.ai.phoneagent.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.ai.phoneagent.core.designsystem.R
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.UiMode
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// AriesSettingsListItem — Material 3 flat-list settings components
//
// All variants are built on top of androidx.compose.material3.ListItem.
// No Card wrapping, no elevation shadow, no border/outline.
// Colors are always driven by MaterialTheme tokens — never hardcoded.
// ---------------------------------------------------------------------------

/**
 * A navigation-style settings row with a chevron trailing icon.
 *
 * Use for items that navigate to a sub-screen when tapped.
 */
@Composable
fun AriesSettingsNavigationItem(
    headlineText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
            )
        },
    )
}

/**
 * A toggle settings row with a [Switch] trailing widget.
 *
 * Tapping anywhere on the row flips the switch.
 */
@Composable
fun AriesSettingsSwitchItem(
    headlineText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/**
 * A read-only or tappable settings row that displays a current value on the trailing side.
 *
 * Use for items like "Font: Roboto" or "Language: English".
 */
@Composable
fun AriesSettingsValueItem(
    headlineText: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    ListItem(
        modifier = clickModifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * A flexible settings row that accepts arbitrary trailing and below-row content.
 *
 * Use for complex controls like [Slider] or [SegmentedButton] that need more space.
 * Put inline controls in [trailingContent]; put full-width controls in [belowContent].
 */
@Composable
fun AriesSettingsCustomItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    belowContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Column(modifier = clickModifier) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            headlineContent = {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = supportingText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = leadingIcon,
            trailingContent = trailingContent,
        )
        if (belowContent != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                belowContent()
            }
        }
    }
}

/**
 * A section header label styled with [MaterialTheme.colorScheme.primary].
 *
 * Use between groups of related settings rows, like Android's native Settings app.
 */
@Composable
fun AriesSettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Settings Items — Light", showBackground = true)
@Composable
private fun AriesSettingsItemsLightPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.LIGHT) {
        Column {
            AriesSettingsSectionHeader(title = "外观")
            AriesSettingsNavigationItem(
                headlineText = "主题",
                supportingText = "跟随系统",
                onClick = {},
            )
            AriesSettingsSwitchItem(
                headlineText = "AMOLED 纯黑",
                checked = true,
                onCheckedChange = {},
            )
            AriesSettingsValueItem(
                headlineText = "字体",
                value = "Roboto",
                onClick = {},
            )
            AriesSettingsSectionHeader(title = "显示")
            AriesSettingsCustomItem(
                headlineText = "字体大小",
                supportingText = "1.0×",
                belowContent = {
                    var sliderValue by remember { mutableStateOf(0.5f) }
                    Slider(value = sliderValue, onValueChange = { sliderValue = it })
                },
            )
        }
    }
}

@Preview(
    name = "Settings Items — Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AriesSettingsItemsDarkPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.DARK) {
        Column {
            AriesSettingsSectionHeader(title = "外观")
            AriesSettingsNavigationItem(
                headlineText = "主题",
                supportingText = "跟随系统",
                onClick = {},
            )
            AriesSettingsSwitchItem(
                headlineText = "AMOLED 纯黑",
                checked = false,
                onCheckedChange = {},
            )
            AriesSettingsValueItem(
                headlineText = "字体",
                value = "Roboto",
                onClick = {},
            )
            AriesSettingsSectionHeader(title = "显示")
            AriesSettingsCustomItem(
                headlineText = "字体大小",
                supportingText = "1.0×",
                belowContent = {
                    var sliderValue by remember { mutableStateOf(0.5f) }
                    Slider(value = sliderValue, onValueChange = { sliderValue = it })
                },
            )
        }
    }
}
