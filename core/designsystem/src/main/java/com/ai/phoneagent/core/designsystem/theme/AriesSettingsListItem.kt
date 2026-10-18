package com.ai.phoneagent.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
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
// AriesSettingsListItem — Material 3 扁平列表设置项组件
//
// 通用设置行使用玻璃效果 + 透明 ListItem 内容。
// 颜色一律取自 MaterialTheme 令牌，禁止硬编码。
// ---------------------------------------------------------------------------

/**
 * 导航型设置行，尾部带箭头图标。
 *
 * 用于点击后跳转到子页面的条目。
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
            .ariesGlass(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
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
 * 开关型设置行，尾部为 [Switch]。
 *
 * 点击行内任意位置即切换开关。
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
            .ariesGlass(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
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
 * 取值型设置行，尾部展示当前值（可点击或只读）。
 *
 * 用于类似「字体：Roboto」或「语言：English」的条目。
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
        modifier.fillMaxWidth().ariesGlass(MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth().ariesGlass(MaterialTheme.shapes.large)
    }
    ListItem(
        modifier = clickModifier,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
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
 * 自定义设置行，可放置任意尾部内容与行下方内容。
 *
 * 用于 [Slider] 等需要更多空间的复杂控件：行内控件放 [trailingContent]，整行控件放 [belowContent]。
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
        modifier.fillMaxWidth().ariesGlass(MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth().ariesGlass(MaterialTheme.shapes.large)
    }
    Column(modifier = clickModifier) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
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
 * 分组标题，使用 [MaterialTheme.colorScheme.primary] 着色。
 *
 * 用于在相关设置行之间分隔分组，类似 Android 原生设置。
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
// 预览
// ---------------------------------------------------------------------------

/** 预览：浅色模式下的设置项。 */
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

/** 预览：深色模式下的设置项。 */
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
