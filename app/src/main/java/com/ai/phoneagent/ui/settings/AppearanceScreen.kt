package com.ai.phoneagent.ui.settings

import android.content.res.Configuration
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
) {
    val isDarkThemeActive = themeMode == ThemeMode.DARK ||
        (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
        ) {
            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.settings_appearance_theme_mode))
            }
            
            item {
                AriesSettingsCustomItem(
                    headlineText = stringResource(R.string.settings_appearance_theme_mode),
                    belowContent = {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(
                                ThemeMode.SYSTEM to stringResource(R.string.settings_appearance_theme_system),
                                ThemeMode.LIGHT to stringResource(R.string.settings_appearance_theme_light),
                                ThemeMode.DARK to stringResource(R.string.settings_appearance_theme_dark)
                            )
                            options.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = { onThemeModeChange(mode) },
                                    selected = themeMode == mode,
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                )
            }

            item {
                AriesSettingsSwitchItem(
                    headlineText = stringResource(R.string.settings_appearance_amoled),
                    checked = amoledDarkEnabled,
                    onCheckedChange = onAmoledDarkEnabledChange,
                    enabled = isDarkThemeActive,
                )
            }

            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.settings_appearance_theme_palette))
            }

            item {
                AriesSettingsCustomItem(
                    headlineText = stringResource(R.string.settings_appearance_theme_palette),
                    supportingText = stringResource(R.string.settings_appearance_theme_palette_summary),
                    belowContent = {
                        val options = buildList {
                            add(ThemeColorStyle.DEFAULT to stringResource(R.string.settings_appearance_theme_palette_default))
                            add(ThemeColorStyle.OCEAN to stringResource(R.string.settings_appearance_theme_palette_ocean))
                            add(ThemeColorStyle.FOREST to stringResource(R.string.settings_appearance_theme_palette_forest))
                            add(ThemeColorStyle.SUNSET to stringResource(R.string.settings_appearance_theme_palette_sunset))
                            add(ThemeColorStyle.ROSE to stringResource(R.string.settings_appearance_theme_palette_rose))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                add(ThemeColorStyle.DYNAMIC to stringResource(R.string.settings_appearance_theme_palette_dynamic))
                            }
                        }

                        Column {
                            options.forEach { (colorStyle, label) ->
                                ThemeColorStyleOptionRow(
                                    colorStyle = colorStyle,
                                    label = label,
                                    supportingText =
                                        if (colorStyle == ThemeColorStyle.DYNAMIC) {
                                            stringResource(R.string.settings_appearance_theme_palette_dynamic_description)
                                        } else {
                                            null
                                        },
                                    selected = themeColorStyle == colorStyle,
                                    darkPreview = isDarkThemeActive,
                                    onClick = { onThemeColorStyleChange(colorStyle) },
                                )
                            }
                        }
                    },
                )
            }

            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.settings_appearance_font_size))
            }

            item {
                AriesSettingsCustomItem(
                    headlineText = stringResource(R.string.settings_appearance_font_size),
                    supportingText = "${chatFontScale}×",
                    belowContent = {
                        Column {
                            Slider(
                                value = chatFontScale,
                                onValueChange = onChatFontScaleChange,
                                valueRange = 0.8f..1.4f,
                                steps = 2, // 3 intervals: 0.8, 1.0, 1.2, 1.4
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.settings_appearance_font_size_small), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.settings_appearance_font_size_default), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.settings_appearance_font_size_large), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.settings_appearance_font_size_xlarge), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                )
            }

            item {
                AriesSettingsCustomItem(
                    headlineText = stringResource(R.string.settings_appearance_font_family),
                    belowContent = {
                        val fontOptions = listOf(
                            "default" to stringResource(R.string.settings_appearance_font_default),
                            "sans_serif" to stringResource(R.string.settings_appearance_font_sans_serif),
                            "serif" to stringResource(R.string.settings_appearance_font_serif),
                            "monospace" to stringResource(R.string.settings_appearance_font_monospace)
                        )
                        
                        Column {
                            fontOptions.forEach { (id, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChatFontFamilyChange(id) }
                                        .padding(vertical = dimensionResource(R.dimen.m3t_spacing_sm)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = chatFontFamily == id,
                                        onClick = { onChatFontFamilyChange(id) }
                                    )
                                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_md)))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                )
            }

            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.settings_appearance_code_section))
            }

            item {
                AriesSettingsSwitchItem(
                    headlineText = stringResource(R.string.settings_appearance_code_auto_wrap),
                    checked = codeAutoWrap,
                    onCheckedChange = onCodeAutoWrapChange,
                )
            }
            
            item {
                AriesSettingsSwitchItem(
                    headlineText = stringResource(R.string.settings_appearance_code_line_numbers),
                    checked = codeLineNumbers,
                    onCheckedChange = onCodeLineNumbersChange,
                )
            }
            
            item {
                AriesSettingsSwitchItem(
                    headlineText = stringResource(R.string.settings_appearance_code_auto_collapse),
                    checked = codeAutoCollapse,
                    onCheckedChange = onCodeAutoCollapseChange,
                )
            }
        }
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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val swatchSize = dimensionResource(R.dimen.m3t_spacing_md)
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
