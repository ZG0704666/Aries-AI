package com.ai.phoneagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bug
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onOpenSourceCode: () -> Unit,
    onCopyContact: () -> Unit,
    onDeveloperTap: () -> Unit,
) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(DesignSystemR.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xl)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.about_title)) },
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
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            item {
                AboutHeroCard(
                    appVersionText = appVersionText,
                    onDeveloperTap = onDeveloperTap,
                )
            }

            item {
                AboutInfoCard(promptVersionText = promptVersionText)
            }

            item {
                AboutActionsCard(
                    checkUpdateButtonText = checkUpdateButtonText,
                    onCheckUpdate = onCheckUpdate,
                    onOpenChangelog = onOpenChangelog,
                    onOpenSourceCode = onOpenSourceCode,
                    onOpenUserAgreement = onOpenUserAgreement,
                    onOpenLicenses = onOpenLicenses,
                    onOpenWebsite = onOpenWebsite,
                    onCopyContact = onCopyContact,
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignSystemR.dimen.m3t_spacing_xxxs)),
                ) {
                    Text(
                        text = stringResource(R.string.about_copyright),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.about_developer_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.about_developer_alias),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeroCard(
    appVersionText: String,
    onDeveloperTap: () -> Unit,
) {
    val spacingXs = dimensionResource(DesignSystemR.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingXxl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xxl)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDeveloperTap)
                    .padding(vertical = spacingXxl, horizontal = spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(dimensionResource(DesignSystemR.dimen.m3t_about_icon_card_size)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(dimensionResource(DesignSystemR.dimen.m3t_spacing_xxl)),
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.about_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = appVersionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacingXs),
            )
        }
    }
}

@Composable
private fun AboutInfoCard(promptVersionText: String) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacingMd),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Text(
                text = stringResource(R.string.about_runtime_info_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = promptVersionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutActionsCard(
    checkUpdateButtonText: String,
    onCheckUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenSourceCode: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenWebsite: () -> Unit,
    onCopyContact: () -> Unit,
) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacingSm)) {
            AboutActionRow(
                icon = { Icon(Lucide.RefreshCw, contentDescription = null) },
                title = checkUpdateButtonText,
                supporting = stringResource(R.string.about_action_check_updates_desc),
                onClick = onCheckUpdate,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.History, contentDescription = null) },
                title = stringResource(R.string.about_changelog),
                supporting = stringResource(R.string.about_action_changelog_desc),
                onClick = onOpenChangelog,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.Code, contentDescription = null) },
                title = stringResource(R.string.about_source_code),
                supporting = stringResource(R.string.about_source_code_short),
                tooltipText = stringResource(R.string.about_source_code_url),
                onClick = onOpenSourceCode,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.Bug, contentDescription = null) },
                title = stringResource(R.string.about_feedback),
                supporting = stringResource(R.string.about_feedback_desc),
                onClick = onCopyContact,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.Shield, contentDescription = null) },
                title = stringResource(R.string.user_agreement_title),
                supporting = stringResource(R.string.about_action_policy_desc),
                onClick = onOpenUserAgreement,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.ScrollText, contentDescription = null) },
                title = stringResource(R.string.about_open_source_licenses),
                supporting = stringResource(R.string.about_action_licenses_desc),
                onClick = onOpenLicenses,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.Globe, contentDescription = null) },
                title = stringResource(R.string.about_website),
                supporting = stringResource(R.string.about_website_short),
                tooltipText = stringResource(R.string.about_website_domain),
                onClick = onOpenWebsite,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutActionRow(
                icon = { Icon(Lucide.Mail, contentDescription = null) },
                title = stringResource(R.string.about_contact_title),
                supporting = stringResource(R.string.about_contact_short),
                tooltipText = stringResource(R.string.about_contact_subtitle),
                onClick = onCopyContact,
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    tooltipText: String? = null,
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
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                modifier = Modifier.padding(spacingSm),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(start = spacingMd, end = spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignSystemR.dimen.m3t_spacing_xxxs)),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tooltipText != null) {
                    Spacer(modifier = Modifier.width(dimensionResource(DesignSystemR.dimen.m3t_spacing_xs)))
                    com.ai.phoneagent.feature.settings.ui.components.InfoTooltip(tooltipText = tooltipText)
                }
            }
        }

        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(DesignSystemR.dimen.m3t_about_row_icon_size)),
        )
    }
}
