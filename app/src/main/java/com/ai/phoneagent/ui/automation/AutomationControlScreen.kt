package com.ai.phoneagent.ui.automation

import com.ai.phoneagent.core.designsystem.theme.AriesGlassCard
import com.ai.phoneagent.core.designsystem.theme.AriesGlassButton
import com.composables.icons.lucide.Layers

import com.ai.phoneagent.ui.components.AriesPageTopBar
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.ai.phoneagent.R
import com.ai.phoneagent.ui.components.InfoTooltip

enum class AutomationStatusTone {
    Ready,
    Partial,
    Inactive,
}

private data class AutomationStatusPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutomationControlScreen(
    statusSummary: String,
    interactionModeText: String,
    accessibilityStatusText: String,
    shizukuStatusText: String,
    statusTone: AutomationStatusTone,
    showShizukuControls: Boolean,
    isBackgroundMode: Boolean,
    virtualDisplayStatus: String,
    useShizukuInteraction: Boolean,
    logText: String,
    showShizukuAuthorize: Boolean,
    onBack: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onRefreshStatus: () -> Unit,
    onExecutionModeChange: (Boolean) -> Unit,
    onShizukuModeChange: (Boolean) -> Unit,
    onCopyLog: () -> Unit,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val statusPalette = statusPalette(statusTone)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AriesPageTopBar(stringResource(R.string.automation_toolbar_title), onBack) {
                IconButton(onClick = onRefreshStatus) { Icon(Lucide.RefreshCw, stringResource(R.string.automation_refresh_status)) }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)),
        ) {
            // Reserve readable log space when settings wrap on narrow screens or large fonts.
            val reservedLogHeight = minOf(
                dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_log_panel_min_height),
                maxHeight * .45f,
            )
            val configurationMaxHeight = (maxHeight - reservedLogHeight - spacingMd)
                .coerceAtLeast(maxHeight * .2f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacingMd)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = configurationMaxHeight),
                    contentPadding = PaddingValues(spacingXs),
                    verticalArrangement = Arrangement.spacedBy(spacingMd),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacingXs)) {
                                Text(statusSummary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(interactionModeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AutomationStatusBadge(text = statusTone.badgeText(), containerColor = Color.Transparent, contentColor = statusPalette.accent)
                        }
                    }
                    item {
                        Text(stringResource(R.string.automation_console_runtime_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(spacingSm))
                        AriesGlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                                Icon(Lucide.Smartphone, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_suggestion_icon)), tint = statusPalette.accent)
                                Text(accessibilityStatusText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                            Text(stringResource(R.string.automation_console_runtime_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                                AriesGlassButton(onClick = onOpenAccessibility) { Text(stringResource(R.string.automation_open_accessibility)) }
                                if (showShizukuAuthorize) AriesGlassButton(onClick = onAuthorizeShizuku) { Text(stringResource(R.string.automation_one_tap_shizuku_authorize)) }
                            }
                        }
                        if (showShizukuControls) {
                            Spacer(Modifier.height(spacingSm))
                            AutomationConnectionBadge(value = shizukuStatusText, accentColor = statusPalette.accent)
                        }
                    }

                    item {
                        Text(stringResource(R.string.automation_execution_mode_label), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().padding(top = spacingSm), horizontalArrangement = Arrangement.spacedBy(spacingMd)) {
                            listOf(false, true).forEach { background ->
                                Surface(onClick = { onExecutionModeChange(background) }, modifier = Modifier.weight(1f).ariesGlass(MaterialTheme.shapes.large, selected = isBackgroundMode == background), shape = MaterialTheme.shapes.large,
                                    color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                                    Column(Modifier.padding(spacingMd), verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                                        Icon(if (background) Lucide.Layers else Lucide.Smartphone, null, tint = if (isBackgroundMode == background) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(stringResource(if (background) R.string.ui_v2_background else R.string.ui_v2_foreground), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(if (background) R.string.ui_v2_background_hint else R.string.ui_v2_foreground_hint), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (isBackgroundMode) item { Text(virtualDisplayStatus, style = MaterialTheme.typography.bodySmall) }
                    if (showShizukuControls) item {
                        AriesGlassCard(Modifier.fillMaxWidth()) {
                            AutomationSwitchRow(title = stringResource(R.string.automation_shizuku_mode_label), checked = useShizukuInteraction, onCheckedChange = onShizukuModeChange)
                        }
                    }
                }
                AutomationLogPanel(logText, onCopyLog, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun AutomationLogPanel(logText: String, onCopyLog: () -> Unit, modifier: Modifier = Modifier) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val shape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_card_radius))
    Surface(modifier = modifier.ariesGlass(shape), shape = shape, color = Color.Transparent) {
        Column(Modifier.fillMaxSize().padding(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_setting_padding))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                Text(stringResource(R.string.ui_v2_logs), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onCopyLog, enabled = logText.isNotBlank()) {
                    Text(stringResource(R.string.ui_v2_copy_logs))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (logText.isBlank()) {
                    Text(stringResource(R.string.ui_v2_no_logs), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = spacingSm))
                } else {
                    SelectionContainer { Text(logText, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun AutomationConnectionBadge(
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)

    Surface(
        modifier = modifier.ariesGlass(MaterialTheme.shapes.large),
        color = Color.Transparent,
        shape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_radius_lg)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingSm),
            horizontalArrangement = Arrangement.spacedBy(spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_automation_status_dot)).background(
                    color = accentColor,
                    shape = MaterialTheme.shapes.small,
                ),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun statusPalette(statusTone: AutomationStatusTone): AutomationStatusPalette =
    when (statusTone) {
        AutomationStatusTone.Ready ->
            AutomationStatusPalette(
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                content = MaterialTheme.colorScheme.onSurface,
                accent = MaterialTheme.colorScheme.primary,
            )

        AutomationStatusTone.Partial ->
            AutomationStatusPalette(
                container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                content = MaterialTheme.colorScheme.onSurface,
                accent = MaterialTheme.colorScheme.tertiary,
            )

        AutomationStatusTone.Inactive ->
            AutomationStatusPalette(
                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                content = MaterialTheme.colorScheme.onSurface,
                accent = MaterialTheme.colorScheme.error,
            )
    }

@Composable
private fun AutomationStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
            horizontalArrangement = Arrangement.spacedBy(spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_automation_status_dot))
                        .background(contentColor, MaterialTheme.shapes.small),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AutomationStatusTone.badgeText(): String =
    when (this) {
        AutomationStatusTone.Ready -> stringResource(R.string.perm_sheet_status_ready)
        AutomationStatusTone.Partial -> stringResource(R.string.ui_v2_setup_incomplete)
        AutomationStatusTone.Inactive -> stringResource(R.string.ui_v2_not_connected)
    }

@Composable
private fun AutomationSwitchRow(
    title: String,
    summary: String? = null,
    tooltipText: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (tooltipText != null) {
                    Spacer(modifier = Modifier.width(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)))
                    InfoTooltip(tooltipText = tooltipText)
                }
            }
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
