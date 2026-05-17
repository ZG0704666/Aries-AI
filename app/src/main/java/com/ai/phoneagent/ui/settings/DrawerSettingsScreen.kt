package com.ai.phoneagent.ui.settings

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
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
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val entries =
        listOf(
            SettingsEntryUi(
                type = SettingsEntryType.Appearance,
                title = stringResource(R.string.settings_entry_appearance_title),
                subtitle = stringResource(R.string.settings_entry_appearance_subtitle),
                icon = Lucide.Sparkles,
            ),
            SettingsEntryUi(
                type = SettingsEntryType.ModelApi,
                title = stringResource(R.string.settings_entry_model_api_title),
                subtitle = stringResource(R.string.settings_entry_model_api_subtitle),
                icon = Lucide.KeyRound,
            ),
            SettingsEntryUi(
                type = SettingsEntryType.Automation,
                title = stringResource(R.string.settings_entry_automation_title),
                subtitle = stringResource(R.string.settings_entry_automation_subtitle),
                icon = Lucide.Cpu,
            ),
            SettingsEntryUi(
                type = SettingsEntryType.About,
                title = stringResource(R.string.settings_entry_about_title),
                subtitle = stringResource(R.string.settings_entry_about_subtitle),
                icon = Lucide.Info,
            ),
        )

    val query = searchQuery.trim()
    val filteredEntries =
        entries.filter { entry ->
            query.isBlank() ||
                entry.title.contains(query, ignoreCase = true) ||
                entry.subtitle.contains(query, ignoreCase = true)
        }
    val sections =
        listOf(
            SettingsSectionUi(
                title = stringResource(R.string.settings_section_appearance),
                entries = entries.filter { it.type == SettingsEntryType.Appearance },
            ),
            SettingsSectionUi(
                title = stringResource(R.string.settings_section_model_automation),
                entries = entries.filter {
                    it.type == SettingsEntryType.ModelApi || it.type == SettingsEntryType.Automation
                },
            ),
            SettingsSectionUi(
                title = stringResource(R.string.settings_section_about),
                entries = entries.filter { it.type == SettingsEntryType.About },
            ),
        )
    val openEntry: (SettingsEntryType) -> Unit = { type ->
        when (type) {
            SettingsEntryType.Appearance -> onOpenAppearance()
            SettingsEntryType.ModelApi -> onOpenModelApi()
            SettingsEntryType.Automation -> onOpenAutomation()
            SettingsEntryType.About -> onOpenAbout()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            item {
                SettingsSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.settings_search_hint),
                )
            }

            if (searchQuery.isBlank()) {
                sections.forEach { section ->
                    item(key = "section_${section.title}") {
                        SettingsSectionCard(
                            title = section.title,
                            entries = section.entries,
                            onEntryClick = openEntry,
                        )
                    }
                }
            } else {
                item(key = "search_results") {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_search_results),
                        entries = filteredEntries,
                        onEntryClick = openEntry,
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    SettingsEmptySearchState(text = stringResource(R.string.settings_search_empty))
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val radiusXl = dimensionResource(R.dimen.m3t_radius_xl)
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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

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
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val iconSize = dimensionResource(R.dimen.m3t_about_row_icon_size)
    val chevronSize = dimensionResource(R.dimen.m3t_about_chevron_size)

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
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xs)),
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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_section_api)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            item {
                ModelApiSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(
                                imageVector =
                                    when (currentApiMode) {
                                        SettingsViewModel.ApiMode.Official -> Lucide.KeyRound
                                        SettingsViewModel.ApiMode.ThirdParty -> Lucide.Cloud
                                        SettingsViewModel.ApiMode.Local -> Lucide.Cpu
                                        SettingsViewModel.ApiMode.Aries -> Lucide.Sparkles
                                    },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(spacingSm),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacingXs),
                        ) {
                            Text(
                                text = modeTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = modeDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    StatusPill(
                        title = stringResource(R.string.settings_model_api_status_title),
                        status = apiStatus,
                        containerColor = statusPillContainerColor,
                        contentColor = statusPillContentColor,
                    )
                }
            }

            item {
                ModelApiSectionCard {
                    SectionIntro(
                        title = stringResource(R.string.settings_model_api_mode_title),
                        subtitle = stringResource(R.string.settings_model_api_mode_subtitle),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    Column {
                        modeOptions.forEachIndexed { index, option ->
                            ModeOptionRow(
                                title = option.title,
                                description = option.description,
                                icon = option.icon,
                                selected = currentApiMode == option.mode,
                                onClick = { onApiModeChange(option.mode) },
                            )
                            if (index != modeOptions.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

            if (currentApiMode == SettingsViewModel.ApiMode.Aries) {
                item {
                    ModelApiSectionCard {
                        SectionIntro(
                            title = stringResource(R.string.settings_model_api_aries_title),
                            subtitle = stringResource(R.string.settings_model_api_aries_subtitle),
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        StatusPanel(
                            title = stringResource(R.string.settings_model_api_status_title),
                            body =
                                if (ariesLoggedInUser.isNotBlank()) {
                                    stringResource(R.string.settings_model_api_aries_logged_in, ariesLoggedInUser)
                                } else {
                                    stringResource(R.string.settings_model_api_aries_login_required)
                                },
                            containerColor = statusContainerColor,
                            contentColor = statusContentColor,
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        Column(verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                            if (ariesLoggedInUser.isNotBlank()) {
                                OutlinedButton(
                                    onClick = onChangeAriesModel,
                                    modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                                ) {
                                    Icon(Lucide.Cpu, contentDescription = null)
                                    Spacer(modifier = Modifier.width(spacingSm))
                                    if (ariesSelectedModel.isNotBlank()) {
                                        Text(stringResource(R.string.aries_current_model, ariesSelectedModel))
                                    } else {
                                        Text(stringResource(R.string.aries_model_change))
                                    }
                                }
                                OutlinedButton(
                                    onClick = onAriesLogout,
                                    modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                                ) {
                                    Text(stringResource(R.string.settings_model_api_aries_logout))
                                }
                            } else {
                                Button(
                                    onClick = onAriesLoginClick,
                                    modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                                ) {
                                    Icon(Lucide.LogIn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(spacingSm))
                                    Text(stringResource(R.string.settings_model_api_aries_login))
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenMembership,
                                modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                            ) {
                                Icon(Lucide.Crown, contentDescription = null)
                                Spacer(modifier = Modifier.width(spacingSm))
                                Text(stringResource(R.string.settings_model_api_aries_membership))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelApiSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
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
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_xs)))
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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)

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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
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
                modifier = Modifier.size(dimensionResource(R.dimen.m3t_about_row_icon_size)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xs)),
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
private fun FilledInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
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
