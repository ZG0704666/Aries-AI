package com.ai.phoneagent.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

/**
 * 关于页面：展示应用品牌信息、运行信息与各类入口（检查更新、源码、反馈、协议等）。
 *
 * 所有可点击项通过回调上抛，页面自身不持有业务逻辑。
 */
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.about_title), modifier = Modifier.padding(start = dimensionResource(DesignSystemR.dimen.aries_toolbar_title_gap)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                expandedHeight = dimensionResource(DesignSystemR.dimen.aries_toolbar_height),
                navigationIcon = {
                    // 与全局顶栏一致：无独立玻璃圆钮/遮罩，图标按主题着色，保留 48dp 触控热区。
                    Box(
                        modifier =
                            Modifier
                                .padding(start = dimensionResource(DesignSystemR.dimen.aries_composer_inset_x))
                                .size(dimensionResource(DesignSystemR.dimen.aries_touch_target))
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.about_back),
                            modifier = Modifier.size(dimensionResource(DesignSystemR.dimen.aries_back_icon)),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacingXl, vertical = spacingXl),
            verticalArrangement = Arrangement.spacedBy(spacingXl),
        ) {
            item {
                AboutHeroSection(
                    appVersionText = appVersionText,
                    onDeveloperTap = onDeveloperTap,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignSystemR.dimen.m3t_spacing_sm))) {
                    AriesSettingsSectionHeader(title = stringResource(R.string.about_runtime_info_title))
                    AriesSettingsValueItem(
                        headlineText = stringResource(R.string.about_runtime_info_title),
                        value = promptVersionText,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignSystemR.dimen.m3t_spacing_md))) {
                    AriesSettingsSectionHeader(title = stringResource(R.string.about_title))
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
            }

            item {
                Spacer(modifier = Modifier.height(spacingXl))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)),
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

/** 顶部品牌区：应用图标、名称、副标题与版本号，整体可点击进入开发者入口。 */
@Composable
private fun AboutHeroSection(
    appVersionText: String,
    onDeveloperTap: () -> Unit,
) {
    val spacingXs = dimensionResource(DesignSystemR.dimen.m3t_spacing_xs)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingXxl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xxl)
    val appIconSize = dimensionResource(DesignSystemR.dimen.aries_about_brand_size)
    val appIconVisualSize = dimensionResource(DesignSystemR.dimen.aries_about_brand_icon)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onDeveloperTap)
                .padding(vertical = spacingXxl, horizontal = spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacingMd),
    ) {
        Box(Modifier.size(appIconSize).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
            RoundedCornerShape(dimensionResource(DesignSystemR.dimen.m3t_spacing_xl))),
            contentAlignment = Alignment.Center) {
            Image(painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                colorFilter = ColorFilter.tint(Color.White),
                // adaptive foreground 自带安全区，1.3x 后可见图形约占容器 36%，与品牌区比例更协调。
                modifier = Modifier.size(appIconVisualSize).scale(1.3f))
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

/** 预览：浅色模式。 */
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

/** 预览：深色模式。 */
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
