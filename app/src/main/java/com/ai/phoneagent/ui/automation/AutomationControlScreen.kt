package com.ai.phoneagent.ui.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.WandSparkles
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.CircleStop
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    autoApprove: Boolean,
    isListening: Boolean,
    taskText: String,
    taskHint: String,
    recommendText: String,
    logText: String,
    showShizukuAuthorize: Boolean,
    startButtonText: String,
    startButtonEnabled: Boolean,
    startButtonTerminateStyle: Boolean,
    pauseButtonText: String,
    pauseButtonEnabled: Boolean,
    stopButtonEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onRefreshStatus: () -> Unit,
    onExecutionModeChange: (Boolean) -> Unit,
    onShizukuModeChange: (Boolean) -> Unit,
    onAutoApproveChange: (Boolean) -> Unit,
    onTaskChange: (String) -> Unit,
    onVoiceTask: () -> Unit,
    onUseRecommendTask: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onCopyLog: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val statusPalette = statusPalette(statusTone)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_toolbar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                         Icon(
                             imageVector = Lucide.ArrowLeft,
                             contentDescription = stringResource(R.string.about_back),
                         )
                     }
                },
                actions = {
                    IconButton(onClick = onRefreshStatus) {
                         Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.automation_refresh_status))
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
                AutomationSectionCard(containerColor = statusPalette.container.copy(alpha = 0.52f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacingXs),
                        ) {
                            Text(
                                text = stringResource(R.string.automation_console_runtime_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = statusPalette.content.copy(alpha = 0.8f),
                            )
                            Text(
                                text = stringResource(R.string.automation_console_runtime_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusPalette.content.copy(alpha = 0.72f),
                            )
                        }
                        Spacer(modifier = Modifier.width(spacingSm))
                        AutomationStatusBadge(
                            text = statusTone.badgeText(),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            contentColor = statusPalette.accent,
                        )
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.titleLarge,
                        color = statusPalette.content,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(spacingXs))
                    Text(
                        text = interactionModeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusPalette.content.copy(alpha = 0.82f),
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        verticalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        AutomationConnectionBadge(
                            value = accessibilityStatusText,
                            modifier = if (showShizukuControls) Modifier else Modifier.fillMaxWidth(),
                            accentColor = statusPalette.accent,
                        )
                        if (showShizukuControls) {
                            AutomationConnectionBadge(
                                value = shizukuStatusText,
                                accentColor = statusPalette.accent,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        FilledTonalButton(
                             onClick = onOpenAccessibility,
                             modifier = Modifier.weight(1f),
                         ) {
                             Icon(Lucide.Smartphone, contentDescription = null)
                             Spacer(modifier = Modifier.width(spacingXs))
                             Text(stringResource(R.string.automation_open_accessibility))
                         }
                        AnimatedVisibility(visible = showShizukuAuthorize, modifier = Modifier.weight(1f)) {
                            FilledTonalButton(
                                 onClick = onAuthorizeShizuku,
                                 modifier = Modifier.fillMaxWidth(),
                             ) {
                                 Icon(Lucide.Shield, contentDescription = null)
                                 Spacer(modifier = Modifier.width(spacingXs))
                                 Text(stringResource(R.string.automation_one_tap_shizuku_authorize))
                             }
                        }
                    }
                }
            }

            item {
                AutomationSectionCard {
                    SectionHeading(
                        title = stringResource(R.string.automation_execution_mode_label),
                    )
                    Spacer(modifier = Modifier.height(spacingMd))

                    AutomationSwitchRow(
                        title = stringResource(R.string.automation_execution_background_experimental),
                        summary = stringResource(R.string.automation_execution_background_experimental_summary),
                        checked = isBackgroundMode,
                        onCheckedChange = onExecutionModeChange,
                    )

                    AnimatedVisibility(
                        visible = isBackgroundMode,
                        enter =
                            fadeIn(animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
                        exit =
                            fadeOut(animationSpec = tween(durationMillis = 100, easing = FastOutLinearInEasing)) +
                                shrinkVertically(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacingLg)) {
                            Spacer(modifier = Modifier.height(spacingSm))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(spacingMd),
                                    verticalArrangement = Arrangement.spacedBy(spacingXs),
                                ) {
                                    Text(
                                        text = stringResource(R.string.automation_virtual_display_status_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = virtualDisplayStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(spacingMd))

                    if (showShizukuControls) {
                        AutomationSwitchRow(
                            title = stringResource(R.string.automation_shizuku_mode_label),
                            tooltipText = stringResource(R.string.automation_console_shizuku_subtitle),
                            checked = useShizukuInteraction,
                            onCheckedChange = onShizukuModeChange,
                        )
                        Spacer(modifier = Modifier.height(spacingSm))
                    }
                    AutomationSwitchRow(
                        title = stringResource(R.string.automation_auto_approve_label),
                        tooltipText = stringResource(R.string.automation_console_auto_approve_subtitle),
                        checked = autoApprove,
                        onCheckedChange = onAutoApproveChange,
                    )
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_lg)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingSm),
            horizontalArrangement = Arrangement.spacedBy(spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(dimensionResource(R.dimen.m3t_automation_status_dot)).background(
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
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                accent = MaterialTheme.colorScheme.primary,
            )

        AutomationStatusTone.Partial ->
            AutomationStatusPalette(
                container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                accent = MaterialTheme.colorScheme.tertiary,
            )

        AutomationStatusTone.Inactive ->
            AutomationStatusPalette(
                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                content = MaterialTheme.colorScheme.onErrorContainer,
                accent = MaterialTheme.colorScheme.error,
            )
    }

@Composable
private fun AutomationSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(dimensionResource(R.dimen.m3t_spacing_lg)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(durationMillis = 180, easing = FastOutSlowInEasing))
                    .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String? = null,
    tooltipText: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
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
private fun AutomationStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

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
                        .size(dimensionResource(R.dimen.m3t_automation_status_dot))
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

private fun AutomationStatusTone.badgeText(): String =
    when (this) {
        AutomationStatusTone.Ready -> "已就绪"
        AutomationStatusTone.Partial -> "待补齐"
        AutomationStatusTone.Inactive -> "未连接"
    }

@Composable
private fun AutomationModeOption(
    selected: Boolean,
    title: String,
    summary: String? = null,
    tooltipText: String? = null,
    onClick: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        }
    val titleColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val summaryColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val animatedContainerColor by
        animateColorAsState(
            targetValue = containerColor,
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            label = "modeContainerColor",
        )
    val animatedTitleColor by
        animateColorAsState(
            targetValue = titleColor,
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            label = "modeTitleColor",
        )
    val animatedSummaryColor by
        animateColorAsState(
            targetValue = summaryColor,
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            label = "modeSummaryColor",
        )

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = animatedContainerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(durationMillis = 160, easing = FastOutSlowInEasing))
                    .padding(spacingMd),
            verticalArrangement = Arrangement.spacedBy(spacingXs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = animatedTitleColor,
                    fontWeight = FontWeight.Medium,
                )
                if (tooltipText != null) {
                    Spacer(modifier = Modifier.width(spacingXs))
                    InfoTooltip(tooltipText = tooltipText)
                }
            }
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = animatedSummaryColor,
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter =
                    fadeIn(animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                exit =
                    fadeOut(animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing)) +
                        shrinkVertically(animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)),
            ) {
                Text(
                    text = stringResource(R.string.automation_console_mode_selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
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
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm)),
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
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_xs)))
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
