package com.ai.phoneagent.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.phoneagent.R
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)

    val themeMode by viewModel.themeMode.collectAsState()
    val amoledDarkEnabled by viewModel.amoledDarkEnabled.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val chatFontScale by viewModel.chatFontScale.collectAsState()
    val chatFontFamily by viewModel.chatFontFamily.collectAsState()
    val codeAutoWrap by viewModel.codeAutoWrap.collectAsState()
    val codeLineNumbers by viewModel.codeLineNumbers.collectAsState()
    val codeAutoCollapse by viewModel.codeAutoCollapse.collectAsState()

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
            contentPadding = PaddingValues(start = spacingLg, top = spacingSm, end = spacingLg, bottom = spacingXl),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            // 1. Theme Mode
            item {
                AppearanceSectionCard {
                    Text(
                        text = stringResource(R.string.settings_appearance_theme_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(spacingMd))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_appearance_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.settings_appearance_theme_light),
                            ThemeMode.DARK to stringResource(R.string.settings_appearance_theme_dark)
                        )
                        options.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                onClick = { viewModel.setThemeMode(mode) },
                                selected = themeMode == mode,
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // 2. AMOLED Dark Mode
            item {
                AppearanceSectionCard {
                    val isDarkThemeActive = themeMode == ThemeMode.DARK || 
                        (themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
                    
                    DetailSwitchRow(
                        title = stringResource(R.string.settings_appearance_amoled),
                        checked = amoledDarkEnabled,
                        onCheckedChange = { viewModel.setAmoledDarkEnabled(it) },
                        enabled = isDarkThemeActive,
                        modifier = Modifier.alpha(if (isDarkThemeActive) 1f else 0.5f)
                    )
                }
            }

            // 3. Dynamic Color (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    AppearanceSectionCard {
                        DetailSwitchRow(
                            title = stringResource(R.string.settings_appearance_dynamic_color),
                            checked = dynamicColorEnabled,
                            onCheckedChange = { viewModel.setDynamicColorEnabled(it) },
                        )
                    }
                }
            }

            // 4. Chat Font Size
            item {
                AppearanceSectionCard {
                    Text(
                        text = stringResource(R.string.settings_appearance_font_size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    Slider(
                        value = chatFontScale,
                        onValueChange = { viewModel.setChatFontScale(it) },
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

            // 5. Chat Font Family
            item {
                AppearanceSectionCard {
                    Text(
                        text = stringResource(R.string.settings_appearance_font_family),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    
                    val fontOptions = listOf(
                        "default" to stringResource(R.string.settings_appearance_font_default),
                        "sans_serif" to stringResource(R.string.settings_appearance_font_sans_serif),
                        "serif" to stringResource(R.string.settings_appearance_font_serif),
                        "monospace" to stringResource(R.string.settings_appearance_font_monospace)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                        fontOptions.forEach { (id, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setChatFontFamily(id) }
                                    .padding(vertical = spacingSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = chatFontFamily == id,
                                    onClick = { viewModel.setChatFontFamily(id) }
                                )
                                Spacer(modifier = Modifier.width(spacingMd))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 6. Code Block Options
            item {
                AppearanceSectionCard {
                    Text(
                        text = stringResource(R.string.settings_appearance_code_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(spacingMd))
                    
                    DetailSwitchRow(
                        title = stringResource(R.string.settings_appearance_code_auto_wrap),
                        checked = codeAutoWrap,
                        onCheckedChange = { viewModel.setCodeAutoWrap(it) },
                    )
                    Spacer(modifier = Modifier.height(spacingMd))
                    DetailSwitchRow(
                        title = stringResource(R.string.settings_appearance_code_line_numbers),
                        checked = codeLineNumbers,
                        onCheckedChange = { viewModel.setCodeLineNumbers(it) },
                    )
                    Spacer(modifier = Modifier.height(spacingMd))
                    DetailSwitchRow(
                        title = stringResource(R.string.settings_appearance_code_auto_collapse),
                        checked = codeAutoCollapse,
                        onCheckedChange = { viewModel.setCodeAutoCollapse(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacingLg),
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}

@Composable
private fun DetailSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
