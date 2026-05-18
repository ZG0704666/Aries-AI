package com.ai.phoneagent.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsNavigationItem
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsSectionHeader
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsValueItem
import com.ai.phoneagent.core.designsystem.theme.ThemeMode
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bug
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Shield
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
    onTaskFeedback: () -> Unit,
    onCopyContact: () -> Unit,
    onDeveloperTap: () -> Unit,
    onAliasTap: () -> Unit = {},
) {
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
            contentPadding = PaddingValues(bottom = spacingXl),
        ) {
            item {
                AboutHeroSection(
                    appVersionText = appVersionText,
                    onDeveloperTap = onDeveloperTap,
                )
            }

            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.about_runtime_info_title))
                AriesSettingsValueItem(
                    headlineText = stringResource(R.string.about_runtime_info_title), // Or a specific string for prompt version
                    value = promptVersionText,
                )
            }

            item {
                AriesSettingsSectionHeader(title = stringResource(R.string.about_title)) // Or "Actions"
                AriesSettingsNavigationItem(
                    headlineText = checkUpdateButtonText,
                    supportingText = stringResource(R.string.about_action_check_updates_desc),
                    leadingIcon = { Icon(Lucide.RefreshCw, contentDescription = null) },
                    onClick = onCheckUpdate,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_changelog),
                    supportingText = stringResource(R.string.about_action_changelog_desc),
                    leadingIcon = { Icon(Lucide.History, contentDescription = null) },
                    onClick = onOpenChangelog,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_source_code),
                    supportingText = stringResource(R.string.about_source_code_short),
                    leadingIcon = { Icon(Lucide.Code, contentDescription = null) },
                    onClick = onOpenSourceCode,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_feedback),
                    supportingText = stringResource(R.string.about_feedback_desc),
                    leadingIcon = { Icon(Lucide.Bug, contentDescription = null) },
                    onClick = onTaskFeedback,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.user_agreement_title),
                    supportingText = stringResource(R.string.about_action_policy_desc),
                    leadingIcon = { Icon(Lucide.Shield, contentDescription = null) },
                    onClick = onOpenUserAgreement,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_open_source_licenses),
                    supportingText = stringResource(R.string.about_action_licenses_desc),
                    leadingIcon = { Icon(Lucide.ScrollText, contentDescription = null) },
                    onClick = onOpenLicenses,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_website),
                    supportingText = stringResource(R.string.about_website_short),
                    leadingIcon = { Icon(Lucide.Globe, contentDescription = null) },
                    onClick = onOpenWebsite,
                )
                AriesSettingsNavigationItem(
                    headlineText = stringResource(R.string.about_contact_title),
                    supportingText = stringResource(R.string.about_contact_short),
                    leadingIcon = { Icon(Lucide.Mail, contentDescription = null) },
                    onClick = onCopyContact,
                )
            }

            item {
                Spacer(modifier = Modifier.height(spacingXl))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAliasTap),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeroSection(
    appVersionText: String,
    onDeveloperTap: () -> Unit,
) {
    val spacingXs = dimensionResource(DesignSystemR.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingXxl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xxl)
    val appIconSize = dimensionResource(DesignSystemR.dimen.m3t_about_icon_card_size)
    val appIconVisualSize = appIconSize - spacingXs

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onDeveloperTap)
                .padding(vertical = spacingXxl, horizontal = spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(appIconVisualSize),
        )

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

@Preview(name = "AboutScreen — Light", showBackground = true)
@Composable
private fun AboutScreenLightPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.LIGHT) {
        AboutScreen(
            appVersionText = "v1.0.0",
            promptVersionText = "v2.1",
            checkUpdateButtonText = "Check for Updates",
            onBack = {},
            onCheckUpdate = {},
            onOpenChangelog = {},
            onOpenUserAgreement = {},
            onOpenLicenses = {},
            onOpenWebsite = {},
            onOpenSourceCode = {},
            onTaskFeedback = {},
            onCopyContact = {},
            onDeveloperTap = {},
        )
    }
}

@Preview(
    name = "AboutScreen — Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AboutScreenDarkPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.DARK) {
        AboutScreen(
            appVersionText = "v1.0.0",
            promptVersionText = "v2.1",
            checkUpdateButtonText = "Check for Updates",
            onBack = {},
            onCheckUpdate = {},
            onOpenChangelog = {},
            onOpenUserAgreement = {},
            onOpenLicenses = {},
            onOpenWebsite = {},
            onOpenSourceCode = {},
            onTaskFeedback = {},
            onCopyContact = {},
            onDeveloperTap = {},
        )
    }
}
