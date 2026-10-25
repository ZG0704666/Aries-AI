package com.ai.phoneagent.ui.settings

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.composables.icons.lucide.ChevronDown
import com.ai.phoneagent.core.designsystem.theme.AriesGlassButton

import com.ai.phoneagent.ui.components.AriesPageTopBar
import android.content.res.Configuration
import com.ai.phoneagent.core.designsystem.theme.glass.AriesLiquidButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Accessibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.RotateCw
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.AriesSoftHairline
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.ai.phoneagent.core.designsystem.theme.ThemeMode
import com.ai.phoneagent.ui.components.InfoTooltip
import com.ai.phoneagent.viewmodel.SettingsViewModel

private enum class SettingsEntryType {
    Appearance,
    ModelApi,
    Automation,
    About,
}

private data class SettingsEntryUi(
    val type: SettingsEntryType,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private data class SettingsSectionUi(
    val title: String,
    val entries: List<SettingsEntryUi>,
)

private data class ApiModeOptionUi(
    val mode: SettingsViewModel.ApiMode,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerSettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenModelApi: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenAbout: () -> Unit,
    embedded: Boolean = false,
    accountName: String = "",
    onOpenAccount: () -> Unit = onOpenModelApi,
    modelSummary: String = "",
    executionSummary: String = "",
    confirmationSummary: String = "",
    accessibilitySummary: String = "",
    onOpenControl: () -> Unit = onOpenAutomation,
    onOpenPermissions: () -> Unit = onOpenAutomation,
) {
    val padding = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)
    val gap = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_card_gap)
    val inner = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val cardShape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_card_radius))
    val colors = MaterialTheme.colorScheme
    val entries = listOf(
        SettingsEntryUi(SettingsEntryType.Appearance, stringResource(R.string.settings_entry_appearance_title), stringResource(R.string.settings_entry_appearance_subtitle), Lucide.Palette),
        SettingsEntryUi(SettingsEntryType.ModelApi, stringResource(R.string.ui_v2_model_title), modelSummary, Lucide.Cpu),
        SettingsEntryUi(SettingsEntryType.About, stringResource(R.string.ui_v2_about_title), stringResource(R.string.ui_v2_about_hint), Lucide.CircleHelp),
    )
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding().clipToBounds(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = padding, vertical = inner), 
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        item {
            // Material B：设置账户卡（滚动内容，静态玻璃）。
            Surface(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth().ariesGlass(cardShape),
                shape = cardShape, color = Color.Transparent, contentColor = colors.onSurface) {
                Row(Modifier.fillMaxWidth().padding(inner), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_avatar_size)).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text(accountName.take(1).ifBlank { stringResource(R.string.ui_v2_avatar) }, color = colors.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(accountName.ifBlank { stringResource(R.string.ui_v2_account) }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(if (accountName.isBlank()) R.string.membership_account_unavailable else R.string.ui_v2_account_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Icon(Lucide.ChevronRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(inner))
                }
            }
        }
        entries.forEach { entry ->
            item {
                Surface(onClick = { when(entry.type) {
                    SettingsEntryType.Appearance -> onOpenAppearance()
                    SettingsEntryType.ModelApi -> onOpenModelApi()
                    else -> onOpenAbout()
                } }, modifier = Modifier.fillMaxWidth().ariesGlass(cardShape), shape = cardShape,
                    color = Color.Transparent, contentColor = colors.onSurface) {
                    SettingsGlassRow(entry.title, entry.subtitle, entry.icon, Modifier.padding(horizontal = inner, vertical = padding + gap))
                }
            }
        }
        item {
            Surface(modifier = Modifier.fillMaxWidth().ariesGlass(cardShape), shape = cardShape,
                color = Color.Transparent, contentColor = colors.onSurface) {
                Column(Modifier.padding(inner)) {
                    Text(stringResource(R.string.ui_v2_quick_settings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_v2_quick_settings_hint), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    val rows = listOf(
                        Triple(stringResource(R.string.automation_execution_mode_label), executionSummary, Lucide.Smartphone),
                        Triple(stringResource(R.string.ui_v2_auto_confirmation), confirmationSummary, Lucide.ShieldCheck),
                        Triple(stringResource(R.string.automation_status_accessibility_short_label), accessibilitySummary, Lucide.Accessibility),
                    )
                    rows.forEachIndexed { index, row ->
                        SettingsGlassRow(row.first, row.second, row.third,
                            Modifier.clickable(onClick = if (index == 2) onOpenPermissions else onOpenAutomation).padding(vertical = gap))
                    }
                    AriesLiquidButton(
                        onClick = onOpenControl,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_action_radius)),
                        contentPadding = PaddingValues(gap),
                    ) {
                        Icon(Lucide.Cpu, null, Modifier.size(inner), tint = colors.primary)
                        Text(stringResource(R.string.ui_v2_open_control), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassRow(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val gap = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_home_icon_gap)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Icon(icon, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_icon)), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_icon)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val radiusXl = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_radius_xl)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search_24),
                contentDescription = null,
            )
        },
        placeholder = { Text(text = placeholder) },
        shape = RoundedCornerShape(radiusXl),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    entries: List<SettingsEntryUi>,
    onEntryClick: (SettingsEntryType) -> Unit,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = spacingLg, vertical = spacingXs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacingXs)) {
                entries.forEach { entry ->
                    SettingsEntryRow(
                        entry = entry,
                        onClick = { onEntryClick(entry.type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsEmptySearchState(text: String) {
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(spacingLg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntryUi,
    onClick: () -> Unit,
) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val iconSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_about_row_icon_size)
    val chevronSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_about_chevron_size)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg, vertical = spacingSm),
            horizontalArrangement = Arrangement.spacedBy(spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = settingsEntryIconContainerColor(entry.type),
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = settingsEntryIconContentColor(entry.type),
                    modifier = Modifier.padding(spacingSm).size(iconSize),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Lucide.ArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(chevronSize).graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

@Composable
private fun settingsEntryIconContainerColor(type: SettingsEntryType): androidx.compose.ui.graphics.Color =
    when (type) {
        SettingsEntryType.Appearance -> MaterialTheme.colorScheme.primaryContainer
        SettingsEntryType.ModelApi -> MaterialTheme.colorScheme.secondaryContainer
        SettingsEntryType.Automation -> MaterialTheme.colorScheme.surfaceVariant
        SettingsEntryType.About -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
private fun settingsEntryIconContentColor(type: SettingsEntryType): androidx.compose.ui.graphics.Color =
    when (type) {
        SettingsEntryType.Appearance -> MaterialTheme.colorScheme.onPrimaryContainer
        SettingsEntryType.ModelApi -> MaterialTheme.colorScheme.onSecondaryContainer
        SettingsEntryType.Automation -> MaterialTheme.colorScheme.onSurfaceVariant
        SettingsEntryType.About -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerModelApiConfigScreen(
    currentApiMode: SettingsViewModel.ApiMode,
    apiInput: String,
    apiBaseUrl: String,
    apiModel: String,
    apiStatus: String,
    apiStatusPositive: Boolean,
    qwenButtonText: String,
    qwenButtonEnabled: Boolean,
    showAriesApiSection: Boolean,
    ariesLoggedInUser: String,
    ariesSelectedModel: String,
    onChangeAriesModel: () -> Unit,
    onBack: () -> Unit,
    onApiModeChange: (SettingsViewModel.ApiMode) -> Unit,
    onApiInputChange: (String) -> Unit,
    onOpenApiKeyPage: () -> Unit,
    onOpenMembership: () -> Unit,
    onAriesLoginClick: () -> Unit,
    onAriesLogout: () -> Unit,
    onApiBaseUrlChange: (String) -> Unit,
    onApiModelChange: (String) -> Unit,
    onCheckApi: () -> Unit,
    onDownloadQwenModel: () -> Unit,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xl)
    val compactButtonHeight = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_compact_button_height)
    val isRemoteMode =
        currentApiMode == SettingsViewModel.ApiMode.Official ||
            currentApiMode == SettingsViewModel.ApiMode.ThirdParty
    val modeTitle =
        when (currentApiMode) {
            SettingsViewModel.ApiMode.Official -> stringResource(R.string.settings_model_api_mode_official)
            SettingsViewModel.ApiMode.ThirdParty -> stringResource(R.string.settings_model_api_mode_third_party)
            SettingsViewModel.ApiMode.Local -> stringResource(R.string.settings_model_api_mode_local)
            SettingsViewModel.ApiMode.Aries -> stringResource(R.string.settings_model_api_aries_mode)
        }
    val modeDescription =
        when (currentApiMode) {
            SettingsViewModel.ApiMode.Official -> stringResource(R.string.settings_model_api_mode_official_description)
            SettingsViewModel.ApiMode.ThirdParty -> stringResource(R.string.settings_model_api_mode_third_party_description)
            SettingsViewModel.ApiMode.Local -> stringResource(R.string.settings_model_api_mode_local_description)
            SettingsViewModel.ApiMode.Aries -> stringResource(R.string.settings_model_api_aries_mode_description)
        }
    val modeOptions =
        buildList {
            add(
                ApiModeOptionUi(
                    mode = SettingsViewModel.ApiMode.Official,
                    title = stringResource(R.string.settings_model_api_mode_official),
                    description = stringResource(R.string.settings_model_api_mode_official_description),
                    icon = Lucide.KeyRound,
                ),
            )
            add(
                ApiModeOptionUi(
                    mode = SettingsViewModel.ApiMode.ThirdParty,
                    title = stringResource(R.string.settings_model_api_mode_third_party),
                    description = stringResource(R.string.settings_model_api_mode_third_party_description),
                    icon = Lucide.Cloud,
                ),
            )
            if (showAriesApiSection) {
                add(
                    ApiModeOptionUi(
                        mode = SettingsViewModel.ApiMode.Local,
                        title = stringResource(R.string.settings_model_api_mode_local),
                        description = stringResource(R.string.settings_model_api_mode_local_description),
                        icon = Lucide.Cpu,
                    ),
                )
            }
            if (showAriesApiSection) {
                add(
                    ApiModeOptionUi(
                        mode = SettingsViewModel.ApiMode.Aries,
                        title = stringResource(R.string.settings_model_api_aries_mode),
                        description = stringResource(R.string.settings_model_api_aries_mode_description),
                        icon = Lucide.Sparkles,
                    ),
                )
            }
        }
    val statusContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val statusContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val statusNotCheckedText = stringResource(R.string.m3t_sidebar_api_not_checked)
    val statusCheckingText = stringResource(R.string.settings_api_checking)
    val statusPillContainerColor =
        when {
            apiStatusPositive -> colorResource(R.color.success_light)
            apiStatus.isNotBlank() && apiStatus != statusNotCheckedText && apiStatus != statusCheckingText -> colorResource(R.color.error_light)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val statusPillContentColor =
        when {
            apiStatusPositive -> colorResource(R.color.success_dark)
            apiStatus.isNotBlank() && apiStatus != statusNotCheckedText && apiStatus != statusCheckingText -> colorResource(R.color.error_dark)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    var providerMenu by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AriesPageTopBar(stringResource(R.string.settings_section_api), onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_gap)),
        ) {
            if (showAriesApiSection) item {
                ModelApiSectionCard {
                    SectionIntro(title = stringResource(R.string.ui_v2_account))
                    Spacer(Modifier.height(spacingSm))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ariesLoggedInUser.isNotBlank()) ariesLoggedInUser else stringResource(R.string.settings_model_api_aries_login_required),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        androidx.compose.material3.TextButton(onClick = onOpenMembership) { Text(stringResource(R.string.settings_model_api_aries_membership)) }
                    }
                    if (currentApiMode == SettingsViewModel.ApiMode.Aries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                            AriesGlassButton(onClick = if (ariesLoggedInUser.isBlank()) onAriesLoginClick else onAriesLogout) {
                                Text(stringResource(if (ariesLoggedInUser.isBlank()) R.string.settings_model_api_aries_login else R.string.settings_model_api_aries_logout))
                            }
                        }
                    }
                }
            }
            item {
                ModelApiSectionCard {
                    SectionIntro(title = stringResource(R.string.settings_model_api_mode_title))
                    Spacer(Modifier.height(spacingSm))
                    Box {
                        AriesLiquidButton(
                            onClick = { providerMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(spacingSm),
                        ) {
                            Icon(modeOptions.firstOrNull { it.mode == currentApiMode }?.icon ?: Lucide.Cloud, null,
                                Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.primary)
                            Text(modeTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Icon(Lucide.ChevronDown, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_icon)))
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            modeOptions.forEach { option ->
                                DropdownMenuItem(text = { Column { Text(option.title); Text(option.description, style = MaterialTheme.typography.bodySmall) } },
                                    leadingIcon = { Icon(option.icon, null) },
                                    trailingIcon = { if (currentApiMode == option.mode) Icon(Lucide.CircleCheck, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { providerMenu = false; onApiModeChange(option.mode) })
                            }
                        }
                    }
                    if (currentApiMode == SettingsViewModel.ApiMode.Aries) {
                        Spacer(Modifier.height(spacingSm))
                        AriesGlassButton(onClick = onChangeAriesModel, modifier = Modifier.fillMaxWidth()) {
                            Icon(Lucide.Sparkles, null)
                            Spacer(Modifier.width(spacingSm))
                            Text(if (ariesSelectedModel.isNotBlank()) stringResource(R.string.aries_current_model, ariesSelectedModel) else stringResource(R.string.aries_model_change))
                        }
                    }
                }
            }

            if (isRemoteMode) {
                item {
                    ModelApiSectionCard {
                        SectionIntro(
                            title = stringResource(R.string.settings_model_api_remote_title),
                            subtitle =
                                if (currentApiMode == SettingsViewModel.ApiMode.ThirdParty) {
                                    stringResource(R.string.settings_model_api_remote_subtitle)
                                } else {
                                    stringResource(R.string.settings_model_api_mode_official_description)
                                },
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        FilledInputField(
                            value = apiInput,
                            onValueChange = onApiInputChange,
                            label = stringResource(R.string.m3t_sidebar_api_hint),
                            secret = true,
                            placeholder = stringResource(R.string.settings_model_api_key_placeholder),
                            leadingIcon = {
                                Icon(Lucide.KeyRound, contentDescription = null)
                            },
                        )

                        Spacer(modifier = Modifier.height(spacingSm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        ) {
                            FilledTonalButton(
                                onClick = onCheckApi,
                                modifier = Modifier.weight(1f).height(compactButtonHeight),
                            ) {
                                Icon(Lucide.RotateCw, contentDescription = null)
                                Spacer(modifier = Modifier.width(spacingSm))
                                Text(stringResource(R.string.m3t_sidebar_check_connection))
                            }
                            FilledTonalButton(
                                onClick = onOpenApiKeyPage,
                                modifier = Modifier.weight(1f).height(compactButtonHeight),
                            ) {
                                Icon(Lucide.ExternalLink, contentDescription = null)
                                Spacer(modifier = Modifier.width(spacingSm))
                                Text(stringResource(R.string.settings_model_api_get_key_short))
                            }
                        }

                        if (currentApiMode == SettingsViewModel.ApiMode.ThirdParty) {
                            Spacer(modifier = Modifier.height(spacingMd))
                            AriesSoftHairline()
                            Spacer(modifier = Modifier.height(spacingMd))

                            FilledInputField(
                                value = apiBaseUrl,
                                onValueChange = onApiBaseUrlChange,
                                label = stringResource(R.string.drawer_api_base_url_label),
                                placeholder = stringResource(R.string.drawer_api_base_url_hint),
                                leadingIcon = { Icon(Lucide.Cloud, contentDescription = null) },
                            )

                            Spacer(modifier = Modifier.height(spacingSm))

                            FilledInputField(
                                value = apiModel,
                                onValueChange = onApiModelChange,
                                label = stringResource(R.string.drawer_api_model_label),
                                placeholder = stringResource(R.string.drawer_api_model_hint),
                                leadingIcon = { Icon(Lucide.CircleCheck, contentDescription = null) },
                            )
                        }
                    }
                }
            }

            if (showAriesApiSection && currentApiMode == SettingsViewModel.ApiMode.Local) {
                item {
                    ModelApiSectionCard {
                        SectionIntro(
                            title = stringResource(R.string.settings_model_api_local_title),
                            subtitle = stringResource(R.string.settings_model_api_local_subtitle),
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        FilledTonalButton(
                            onClick = onDownloadQwenModel,
                            enabled = qwenButtonEnabled,
                            modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                        ) {
                            Icon(Lucide.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(spacingSm))
                            Text(qwenButtonText)
                        }
                    }
                }
            }

            item {
                ModelApiSectionCard {
                    StatusPill(title = stringResource(R.string.settings_model_api_status_title), status = apiStatus,
                        containerColor = Color.Transparent, contentColor = statusPillContentColor)
                }
            }

        }
    }
}

@Composable
private fun ModelApiSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val shape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_radius))
    Card(
        modifier = Modifier.fillMaxWidth().ariesGlass(shape),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingLg, vertical = spacingMd),
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}

@Composable
private fun SectionIntro(
    title: String,
    subtitle: String? = null,
    tooltipText: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (tooltipText != null) {
            Spacer(modifier = Modifier.width(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)))
            InfoTooltip(tooltipText = tooltipText)
        }
    }
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusPill(
    title: String,
    status: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = containerColor,
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs),
            )
        }
    }
}

@Composable
private fun StatusPanel(
    title: String,
    body: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingMd, vertical = spacingSm),
            verticalArrangement = Arrangement.spacedBy(spacingXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ModeOptionRow(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacingMd),
            horizontalArrangement = Arrangement.spacedBy(spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_about_row_icon_size)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun FilledInputField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null, secret: Boolean = false) {
    val gap = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_search_height)).ariesGlass(MaterialTheme.shapes.medium),
        decorationBox = { field ->
            Row(Modifier.fillMaxWidth().padding(horizontal = gap, vertical = gap), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                leadingIcon?.invoke()
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            }
        })
}

@Preview(name = "DrawerSettingsScreen - Light", showBackground = true)
@Composable
private fun DrawerSettingsScreenLightPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.LIGHT) {
        DrawerSettingsScreen(
            onBack = {},
            onOpenAppearance = {},
            onOpenModelApi = {},
            onOpenAutomation = {},
            onOpenAbout = {},
        )
    }
}

@Preview(name = "DrawerSettingsScreen - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DrawerSettingsScreenDarkPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.DARK) {
        DrawerSettingsScreen(
            onBack = {},
            onOpenAppearance = {},
            onOpenModelApi = {},
            onOpenAutomation = {},
            onOpenAbout = {},
        )
    }
}
