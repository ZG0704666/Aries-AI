package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)

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
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(dialogPadding),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_api_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        OutlinedTextField(
                            value = apiInput,
                            onValueChange = onApiInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.m3t_sidebar_api_hint)) },
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(spacingSm))

                        Row(horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                            TextButton(onClick = onPasteApi) {
                                Text(stringResource(R.string.m3t_sidebar_api_paste))
                            }
                            TextButton(onClick = onOpenApiKeyPage) {
                                Text(stringResource(R.string.m3t_sidebar_get_api_key))
                            }
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        SwitchRow(
                            title = stringResource(R.string.m3t_sidebar_third_party_api),
                            checked = useThirdPartyApi,
                            onCheckedChange = onUseThirdPartyChange,
                        )

                        if (useThirdPartyApi) {
                            Spacer(modifier = Modifier.height(spacingSm))
                            OutlinedTextField(
                                value = apiBaseUrl,
                                onValueChange = onApiBaseUrlChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.drawer_api_base_url_label)) },
                                placeholder = { Text(stringResource(R.string.drawer_api_base_url_hint)) },
                                singleLine = true,
                            )

                            Spacer(modifier = Modifier.height(spacingSm))

                            OutlinedTextField(
                                value = apiModel,
                                onValueChange = onApiModelChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.drawer_api_model_label)) },
                                placeholder = { Text(stringResource(R.string.drawer_api_model_hint)) },
                                singleLine = true,
                            )
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        SwitchRow(
                            title = stringResource(R.string.m3t_sidebar_local_model_mode),
                            checked = useLocalModel,
                            onCheckedChange = onUseLocalModelChange,
                        )

                        Spacer(modifier = Modifier.height(spacingMd))

                        Button(
                            onClick = onCheckApi,
                            modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                        ) {
                            Text(stringResource(R.string.m3t_sidebar_check_connection))
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                        ) {
                            TextButton(
                                onClick = onDownloadQwenModel,
                                enabled = qwenButtonEnabled,
                                modifier = Modifier.fillMaxWidth().height(compactButtonHeight),
                            ) {
                                Text(qwenButtonText)
                            }
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        Text(
                            text = apiStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (apiStatusPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
        SettingsEntryType.Appearance -> R.drawable.ic_tune_24
        SettingsEntryType.ModelApi -> R.drawable.ic_key_24
        SettingsEntryType.Automation -> R.drawable.ic_settings_24
        SettingsEntryType.About -> R.drawable.ic_settings_24
    }
