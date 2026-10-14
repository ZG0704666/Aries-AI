package com.ai.phoneagent.ui.settings

import android.content.res.Configuration
import com.ai.phoneagent.ui.components.AriesPageTopBar
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Type
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.ChevronRight
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsCustomItem
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsSectionHeader
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsSwitchItem
import com.ai.phoneagent.core.designsystem.theme.previewColors
import com.ai.phoneagent.data.preferences.ThemeColorStyle
import com.ai.phoneagent.data.preferences.ThemeMode
import com.ai.phoneagent.viewmodel.AppearanceViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppearanceViewModel = koinViewModel(),
) {
    val plainMessages by viewModel.plainMessages.collectAsState()
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val themeColorStyle by viewModel.themeColorStyle.collectAsState()
    val amoledDarkEnabled by viewModel.amoledDarkEnabled.collectAsState()
    val chatFontScale by viewModel.chatFontScale.collectAsState()
    val chatFontFamily by viewModel.chatFontFamily.collectAsState()
    val codeAutoWrap by viewModel.codeAutoWrap.collectAsState()
    val codeLineNumbers by viewModel.codeLineNumbers.collectAsState()
    val codeAutoCollapse by viewModel.codeAutoCollapse.collectAsState()

    AppearanceScreenContent(
        onNavigateBack = onNavigateBack,
        thinkingExpanded = thinkingExpanded,
        onThinkingExpandedChange = viewModel::setThinkingExpanded,
        plainMessages = plainMessages,
        onPlainMessagesChange = viewModel::setPlainMessages,
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        themeColorStyle = themeColorStyle,
        onThemeColorStyleChange = viewModel::setThemeColorStyle,
        amoledDarkEnabled = amoledDarkEnabled,
        onAmoledDarkEnabledChange = viewModel::setAmoledDarkEnabled,
        chatFontScale = chatFontScale,
        onChatFontScaleChange = viewModel::setChatFontScale,
        chatFontFamily = chatFontFamily,
        onChatFontFamilyChange = viewModel::setChatFontFamily,
        codeAutoWrap = codeAutoWrap,
        onCodeAutoWrapChange = viewModel::setCodeAutoWrap,
        codeLineNumbers = codeLineNumbers,
        onCodeLineNumbersChange = viewModel::setCodeLineNumbers,
        codeAutoCollapse = codeAutoCollapse,
        onCodeAutoCollapseChange = viewModel::setCodeAutoCollapse,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreenContent(
    onNavigateBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColorStyle: ThemeColorStyle,
    onThemeColorStyleChange: (ThemeColorStyle) -> Unit,
    amoledDarkEnabled: Boolean,
    onAmoledDarkEnabledChange: (Boolean) -> Unit,
    chatFontScale: Float,
    onChatFontScaleChange: (Float) -> Unit,
    chatFontFamily: String,
    onChatFontFamilyChange: (String) -> Unit,
    codeAutoWrap: Boolean,
    onCodeAutoWrapChange: (Boolean) -> Unit,
    codeLineNumbers: Boolean,
    onCodeLineNumbersChange: (Boolean) -> Unit,
    codeAutoCollapse: Boolean,
    onCodeAutoCollapseChange: (Boolean) -> Unit,
    thinkingExpanded: Boolean = false,
    onThinkingExpandedChange: (Boolean) -> Unit = {},
    plainMessages: Boolean = false,
    onPlainMessagesChange: (Boolean) -> Unit = {},
) {
    val dark = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    var dialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val padding = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)
    val modes = listOf(ThemeMode.SYSTEM to R.string.settings_appearance_theme_system, ThemeMode.LIGHT to R.string.settings_appearance_theme_light, ThemeMode.DARK to R.string.settings_appearance_theme_dark)
    val fonts = listOf("default" to R.string.settings_appearance_font_default, "sans_serif" to R.string.settings_appearance_font_sans_serif, "serif" to R.string.settings_appearance_font_serif, "monospace" to R.string.settings_appearance_font_monospace)
    val palettes = listOf(ThemeColorStyle.DEFAULT to R.string.settings_appearance_theme_palette_default, ThemeColorStyle.OCEAN to R.string.settings_appearance_theme_palette_ocean, ThemeColorStyle.FOREST to R.string.settings_appearance_theme_palette_forest, ThemeColorStyle.SUNSET to R.string.settings_appearance_theme_palette_sunset, ThemeColorStyle.ROSE to R.string.settings_appearance_theme_palette_rose) + if (Build.VERSION.SDK_INT >= 31) listOf(ThemeColorStyle.DYNAMIC to R.string.settings_appearance_theme_palette_dynamic) else emptyList()
    Scaffold(containerColor = Color.Transparent, topBar = {
        AriesPageTopBar(stringResource(R.string.settings_appearance_title), onNavigateBack)
    }) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(padding),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_gap))) {
            item { AppearanceEntry(Lucide.Moon, stringResource(R.string.settings_appearance_theme_mode), stringResource(modes.first { it.first == themeMode }.second)) { dialog = "mode" } }
            item { AppearanceEntry(Lucide.Palette, stringResource(R.string.settings_appearance_theme_palette), stringResource(palettes.firstOrNull { it.first == themeColorStyle }?.second ?: R.string.settings_appearance_theme_palette_default)) { dialog = "palette" } }
            item { AppearanceEntry(Lucide.Type, stringResource(R.string.ui_v2_font_and_size), stringResource(fonts.firstOrNull { it.first == chatFontFamily }?.second ?: R.string.settings_appearance_font_default) + " · ${chatFontScale}×") { dialog = "fontSize" } }
            item { AppearanceEntry(Lucide.MessageSquare, stringResource(R.string.ui_v2_message_style), stringResource(if (plainMessages) R.string.ui_v2_plain else R.string.ui_v2_bubble)) { dialog = "style" } }
            item { AppearanceEntry(Lucide.Brain, stringResource(R.string.ui_v2_thinking_process), stringResource(if (thinkingExpanded) R.string.ui_v2_expanded else R.string.ui_v2_collapsed)) { dialog = "thinking" } }
            item { AppearanceEntry(Lucide.Code, stringResource(R.string.settings_appearance_code_section), stringResource(R.string.ui_v2_code_options)) { dialog = "code" } }
            item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.settings_appearance_amoled), checked = amoledDarkEnabled, onCheckedChange = onAmoledDarkEnabledChange, enabled = dark) }
        }
    }
    if (dialog != null) androidx.compose.material3.AlertDialog(onDismissRequest = { dialog = null }, confirmButton = {
        androidx.compose.material3.TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_close)) }
    }, text = {
        LazyColumn {
            when (dialog) {
                "mode" -> modes.forEach { (value, label) -> item { AppearanceChoice(stringResource(label), themeMode == value) { onThemeModeChange(value); dialog = null } } }
                "palette" -> palettes.forEach { (value, label) -> item { ThemeColorStyleOptionRow(value, stringResource(label), null, themeColorStyle == value, dark) { onThemeColorStyleChange(value); dialog = null } } }
                "fontSize" -> {
                    item { Text(stringResource(R.string.settings_appearance_font_size)); Slider(value = chatFontScale, onValueChange = onChatFontScaleChange, valueRange = .8f..1.4f, steps = 2) }
                    fonts.forEach { (value, label) -> item { AppearanceChoice(stringResource(label), chatFontFamily == value) { onChatFontFamilyChange(value) } } }
                }
                "style" -> listOf(false to R.string.ui_v2_bubble, true to R.string.ui_v2_plain).forEach { (value, label) -> item { AppearanceChoice(stringResource(label), plainMessages == value) { onPlainMessagesChange(value); dialog = null } } }
                "thinking" -> listOf(false to R.string.ui_v2_collapsed, true to R.string.ui_v2_expanded).forEach { (value, label) -> item { AppearanceChoice(stringResource(label), thinkingExpanded == value) { onThinkingExpandedChange(value); dialog = null } } }
                "code" -> {
                    item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.settings_appearance_code_auto_wrap), checked = codeAutoWrap, onCheckedChange = onCodeAutoWrapChange) }
                    item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.settings_appearance_code_line_numbers), checked = codeLineNumbers, onCheckedChange = onCodeLineNumbersChange) }
                    item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.settings_appearance_code_auto_collapse), checked = codeAutoCollapse, onCheckedChange = onCodeAutoCollapseChange) }
                }
            }
        }
    })
}

@Composable
private fun AppearanceEntry(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_radius))
    androidx.compose.material3.Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().ariesGlass(shape),
        shape = shape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().padding(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_padding)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_home_icon_gap))) {
            Icon(icon, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Lucide.ChevronRight, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_icon)))
        }
    }
}

@Composable
private fun AppearanceChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label)
    }
}

@Composable
private fun ThemeColorStyleOptionRow(
    colorStyle: ThemeColorStyle,
    label: String,
    supportingText: String?,
    selected: Boolean,
    darkPreview: Boolean,
    onClick: () -> Unit,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val swatchSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val preview = colorStyle.previewColors(isDarkTheme = darkPreview)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(spacingMd))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacingXs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (preview != null) {
                    ThemeAccentSwatch(color = preview.primary, size = swatchSize)
                    ThemeAccentSwatch(color = preview.secondary, size = swatchSize)
                    ThemeAccentSwatch(color = preview.tertiary, size = swatchSize)
                    Spacer(modifier = Modifier.width(spacingMd))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentSwatch(
    color: Color,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color = color, shape = CircleShape),
    )
}

@Preview(name = "AppearanceScreen - Light", showBackground = true)
@Composable
private fun AppearanceScreenLightPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.LIGHT, themeColorStyle = ThemeColorStyle.DEFAULT) {
        AppearanceScreenContent(
            onNavigateBack = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            themeColorStyle = ThemeColorStyle.DEFAULT,
            onThemeColorStyleChange = {},
            amoledDarkEnabled = false,
            onAmoledDarkEnabledChange = {},
            chatFontScale = 1.0f,
            onChatFontScaleChange = {},
            chatFontFamily = "default",
            onChatFontFamilyChange = {},
            codeAutoWrap = true,
            onCodeAutoWrapChange = {},
            codeLineNumbers = false,
            onCodeLineNumbersChange = {},
            codeAutoCollapse = false,
            onCodeAutoCollapseChange = {},
        )
    }
}

@Preview(name = "AppearanceScreen - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearanceScreenDarkPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.DARK, themeColorStyle = ThemeColorStyle.OCEAN) {
        AppearanceScreenContent(
            onNavigateBack = {},
            themeMode = ThemeMode.DARK,
            onThemeModeChange = {},
            themeColorStyle = ThemeColorStyle.OCEAN,
            onThemeColorStyleChange = {},
            amoledDarkEnabled = true,
            onAmoledDarkEnabledChange = {},
            chatFontScale = 1.2f,
            onChatFontScaleChange = {},
            chatFontFamily = "monospace",
            onChatFontFamilyChange = {},
            codeAutoWrap = false,
            onCodeAutoWrapChange = {},
            codeLineNumbers = true,
            onCodeLineNumbersChange = {},
            codeAutoCollapse = true,
            onCodeAutoCollapseChange = {},
        )
    }
}
