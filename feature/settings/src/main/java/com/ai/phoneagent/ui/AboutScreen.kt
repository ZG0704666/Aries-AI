package com.ai.phoneagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ManageHistory
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appVersionText: String,
    promptVersionText: String,
    checkUpdateButtonText: String,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenWebsite: () -> Unit,
    onCopyContact: () -> Unit,
    onDeveloperTap: () -> Unit,
) {
    val spacingXs = dimensionResource(DesignSystemR.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(DesignSystemR.dimen.m3t_spacing_lg)
    val cardPadding = dimensionResource(DesignSystemR.dimen.m3t_about_card_padding)
    val iconCardSize = dimensionResource(DesignSystemR.dimen.m3t_about_icon_card_size)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(cardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier.size(iconCardSize),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.app_name),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(dimensionResource(DesignSystemR.dimen.m3t_spacing_xxl)),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(spacingXs))
                        Text(
                            text = appVersionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = promptVersionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(spacingSm)) {
                        Button(
                            onClick = onCheckUpdate,
                            modifier = Modifier.fillMaxWidth().height(dimensionResource(DesignSystemR.dimen.m3t_button_height)),
                        ) {
                            Icon(Icons.Outlined.Update, contentDescription = null)
                            Text(
                                text = checkUpdateButtonText,
                                modifier = Modifier.padding(start = spacingSm),
                            )
                        }

                        Spacer(modifier = Modifier.height(spacingSm))

                        AboutActionRow(
                            icon = { Icon(Icons.Outlined.ManageHistory, contentDescription = null) },
                            title = stringResource(R.string.about_changelog),
                            onClick = onOpenChangelog,
                        )
                        AboutActionRow(
                            icon = { Icon(Icons.Outlined.Policy, contentDescription = null) },
                            title = stringResource(R.string.user_agreement_title),
                            onClick = onOpenUserAgreement,
                        )
                        AboutActionRow(
                            icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
                            title = stringResource(R.string.about_open_source_licenses),
                            onClick = onOpenLicenses,
                        )
                        AboutActionRow(
                            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                            title = stringResource(R.string.about_website),
                            supporting = stringResource(R.string.about_website_domain),
                            onClick = onOpenWebsite,
                        )
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDeveloperTap)
                                .padding(spacingLg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.about_developer_info),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(spacingXs))
                        Text(
                            text = stringResource(R.string.about_developer_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.about_developer_alias),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(spacingSm))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = stringResource(R.string.about_contact),
                                modifier =
                                    Modifier
                                        .clickable(onClick = onCopyContact)
                                        .padding(horizontal = spacingMd, vertical = spacingXs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacingMd, vertical = spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier.padding(spacingSm),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = spacingMd, end = spacingSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
