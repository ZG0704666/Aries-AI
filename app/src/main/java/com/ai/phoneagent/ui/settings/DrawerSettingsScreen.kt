package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsSuggest
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.ai.phoneagent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerSettingsScreen(
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
    onOpenAutomation: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = spacingLg,
                top = spacingMd,
                end = spacingLg,
                bottom = dimensionResource(R.dimen.m3t_spacing_xxl),
            ),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_api),
                    subtitle = stringResource(R.string.settings_api_description),
                ) {
                    OutlinedTextField(
                        value = apiInput,
                        onValueChange = onApiInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.m3t_sidebar_api_hint)) },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    Row(horizontalArrangement = Arrangement.spacedBy(spacingMd)) {
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

                        Spacer(modifier = Modifier.height(spacingMd))

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

                    Spacer(modifier = Modifier.height(spacingLg))

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
                        color =
                            if (apiStatusPositive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_actions),
                    subtitle = null,
                ) {
                    SettingsActionButton(
                        title = stringResource(R.string.settings_open_automation),
                        icon = { Icon(Icons.Outlined.SettingsSuggest, contentDescription = null) },
                        onClick = onOpenAutomation,
                    )

                    Spacer(modifier = Modifier.height(spacingSm))

                    SettingsActionButton(
                        title = stringResource(R.string.settings_open_about),
                        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dialogPadding),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(spacingXs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.m3t_spacing_lg)))
            content()
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
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionButton(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.m3t_compact_button_height)),
    ) {
        icon()
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_sm)))
        Text(
            text = title,
        )
    }
}
