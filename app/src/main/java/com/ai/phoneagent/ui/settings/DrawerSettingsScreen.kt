package com.ai.phoneagent.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R

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
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val radiusXl = dimensionResource(R.dimen.m3t_radius_xl)
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val entries =
        listOf(
            SettingsEntryUi(
                type = SettingsEntryType.Appearance,
                title = stringResource(R.string.settings_entry_appearance_title),
                subtitle = stringResource(R.string.settings_entry_appearance_subtitle),
            ),
            SettingsEntryUi(
                type = SettingsEntryType.ModelApi,
                title = stringResource(R.string.settings_entry_model_api_title),
                subtitle = stringResource(R.string.settings_entry_model_api_subtitle),
            ),
            SettingsEntryUi(
                type = SettingsEntryType.Automation,
                title = stringResource(R.string.settings_entry_automation_title),
                subtitle = stringResource(R.string.settings_entry_automation_subtitle),
            ),
            SettingsEntryUi(
                type = SettingsEntryType.About,
                title = stringResource(R.string.settings_entry_about_title),
                subtitle = stringResource(R.string.settings_entry_about_subtitle),
            ),
        )

    val query = searchQuery.trim()
    val filteredEntries =
        entries.filter { entry ->
            query.isBlank() ||
                entry.title.contains(query, ignoreCase = true) ||
                entry.subtitle.contains(query, ignoreCase = true)
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24),
                            contentDescription = null,
                        )
                    },
                    placeholder = { Text(text = stringResource(R.string.settings_search_hint)) },
                    shape = RoundedCornerShape(radiusXl),
                )
            }

            items(filteredEntries, key = { it.type.name }) { entry ->
                SettingsEntryRow(
                    entry = entry,
                    onClick = {
                        when (entry.type) {
                            SettingsEntryType.Appearance -> onOpenAppearance()
                            SettingsEntryType.ModelApi -> onOpenModelApi()
                            SettingsEntryType.Automation -> onOpenAutomation()
                            SettingsEntryType.About -> onOpenAbout()
                        }
                    },
                )
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_search_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = spacingMd),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerModelApiConfigScreen(
    apiInput: String,
    useThirdPartyApi: Boolean,
    useLocalModel: Boolean,
    apiBaseUrl: String,
    apiModel: String,
    apiStatus: String,
    apiStatusPositive: Boolean,
    qwenButtonText: String,
    qwenButtonEnabled: Boolean,
    onBack: () -> Unit,
    onApiInputChange: (String) -> Unit,
    onPasteApi: () -> Unit,
    onOpenApiKeyPage: () -> Unit,
    onUseThirdPartyChange: (Boolean) -> Unit,
    onApiBaseUrlChange: (String) -> Unit,
    onApiModelChange: (String) -> Unit,
    onUseLocalModelChange: (Boolean) -> Unit,
    onCheckApi: () -> Unit,
    onDownloadQwenModel: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)
    val modeTitle =
        when {
            useLocalModel -> stringResource(R.string.settings_model_api_mode_local)
            useThirdPartyApi -> stringResource(R.string.settings_model_api_mode_third_party)
            else -> stringResource(R.string.settings_model_api_mode_official)
        }
    val modeDescription =
        when {
            useLocalModel -> stringResource(R.string.settings_model_api_mode_local_description)
            useThirdPartyApi -> stringResource(R.string.settings_model_api_mode_third_party_description)
            else -> stringResource(R.string.settings_model_api_mode_official_description)
        }
    val statusContainerColor =
        if (apiStatusPositive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        }
    val statusContentColor =
        if (apiStatusPositive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            item {
                ModelApiSectionCard {
                    SectionIntro(
                        title = stringResource(R.string.settings_model_api_summary_title),
                        subtitle = stringResource(R.string.settings_model_api_summary_subtitle),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        ) {
                            Icon(
                                imageVector = if (useLocalModel) Icons.Outlined.Memory else Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(spacingSm),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
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
                        StatusBadge(
                            text = apiStatus,
                            containerColor = statusContainerColor,
                            contentColor = statusContentColor,
                        )
                    }
                }
            }

            item {
                ModelApiSectionCard {
                    SectionIntro(
                        title = stringResource(R.string.settings_model_api_remote_title),
                        subtitle = stringResource(R.string.settings_model_api_remote_subtitle),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    FilledInputField(
                        value = apiInput,
                        onValueChange = onApiInputChange,
                        label = stringResource(R.string.m3t_sidebar_api_hint),
                        placeholder = stringResource(R.string.settings_model_api_key_placeholder),
                        leadingIcon = {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                        },
                    )

                    Spacer(modifier = Modifier.height(spacingSm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        FilledTonalButton(
                            onClick = onPasteApi,
                            modifier = Modifier.weight(1f).height(compactButtonHeight),
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                            Spacer(modifier = Modifier.width(spacingSm))
                            Text(stringResource(R.string.m3t_sidebar_api_paste))
                        }
                        FilledTonalButton(
                            onClick = onOpenApiKeyPage,
                            modifier = Modifier.weight(1f).height(compactButtonHeight),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(spacingSm))
                            Text(stringResource(R.string.settings_model_api_get_key_short))
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(spacingMd))

                    DetailSwitchRow(
                        title = stringResource(R.string.m3t_sidebar_third_party_api),
                        summary = stringResource(R.string.settings_model_api_third_party_switch_subtitle),
                        checked = useThirdPartyApi,
                        onCheckedChange = onUseThirdPartyChange,
                    )

                    AnimatedVisibility(
                        visible = useThirdPartyApi,
                        enter = fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) + expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing)) + shrinkVertically(animationSpec = tween(140, easing = FastOutLinearInEasing)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = spacingMd).animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(spacingSm),
                        ) {
                            FilledInputField(
                                value = apiBaseUrl,
                                onValueChange = onApiBaseUrlChange,
                                label = stringResource(R.string.drawer_api_base_url_label),
                                placeholder = stringResource(R.string.drawer_api_base_url_hint),
                                leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                            )
                            FilledInputField(
                                value = apiModel,
                                onValueChange = onApiModelChange,
                                label = stringResource(R.string.drawer_api_model_label),
                                placeholder = stringResource(R.string.drawer_api_model_hint),
                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                            )
                        }
                    }
                }
            }

            item {
                ModelApiSectionCard {
                    SectionIntro(
                        title = stringResource(R.string.settings_model_api_local_title),
                        subtitle = stringResource(R.string.settings_model_api_local_subtitle),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    DetailSwitchRow(
                        title = stringResource(R.string.m3t_sidebar_local_model_mode),
                        summary = stringResource(R.string.settings_model_api_local_switch_subtitle),
                        checked = useLocalModel,
                        onCheckedChange = onUseLocalModelChange,
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    FilledTonalButton(
                        onClick = onDownloadQwenModel,
                        enabled = qwenButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacingSm))
                        Text(qwenButtonText)
                    }
                }
            }

            item {
                ModelApiSectionCard {
                    SectionIntro(
                        title = stringResource(R.string.settings_model_api_action_title),
                        subtitle = stringResource(R.string.settings_model_api_action_subtitle),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    Button(
                        onClick = onCheckApi,
                        modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacingSm))
                        Text(stringResource(R.string.m3t_sidebar_check_connection))
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    Surface(
                        color = statusContainerColor,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(spacingMd),
                            verticalArrangement = Arrangement.spacedBy(spacingXs),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_model_api_status_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = statusContentColor,
                            )
                            Text(
                                text = apiStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusContentColor,
                            )
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
private fun SectionIntro(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
        )
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
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}

@Composable
private fun DetailSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    val iconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = spacingMd, vertical = spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (entry.type == SettingsEntryType.About) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacingSm),
                    )
                } else {
                    Icon(
                        painter = painterResource(resolveEntryIcon(entry.type)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacingSm),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_sm)))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun resolveEntryIcon(type: SettingsEntryType): Int =
    when (type) {
        SettingsEntryType.Appearance -> R.drawable.palette_24px
        SettingsEntryType.ModelApi -> R.drawable.ic_key_24
        SettingsEntryType.Automation -> R.drawable.ic_settings_24
        SettingsEntryType.About -> R.drawable.ic_settings_24
    }
