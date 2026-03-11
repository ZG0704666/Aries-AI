package com.ai.phoneagent.ui.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
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
import com.ai.phoneagent.R

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationControlScreen(
    statusText: String,
    statusTone: AutomationStatusTone,
    isBackgroundMode: Boolean,
    modeDescription: String,
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
    val modeTitle =
        stringResource(
            if (isBackgroundMode) {
                R.string.automation_execution_background_mode
            } else {
                R.string.automation_execution_front_mode
            },
        )
    val interactionModeText =
        stringResource(
            if (useShizukuInteraction) {
                R.string.automation_status_mode_shizuku
            } else {
                R.string.automation_status_mode_accessibility
            },
        )
    val voiceTransition = rememberInfiniteTransition(label = "automationVoice")
    val voiceScale by
        voiceTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.08f else 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 520),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "automationVoiceScale",
        )
    val voiceAlpha by
        voiceTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 0.72f else 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 520),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "automationVoiceAlpha",
        )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_toolbar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshStatus) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.automation_refresh_status))
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
                AutomationSectionCard {
                    SectionHeading(
                        title = stringResource(R.string.automation_console_runtime_title),
                        subtitle = stringResource(R.string.automation_console_runtime_subtitle),
                    )
                    Spacer(modifier = Modifier.height(spacingMd))

                    AutomationInfoRow(
                        label = stringResource(R.string.automation_status_label),
                        value = statusText,
                        accentColor = statusPalette.accent,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AutomationInfoRow(
                        label = stringResource(R.string.automation_execution_mode_label),
                        value = modeTitle,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AutomationInfoRow(
                        label = stringResource(R.string.automation_console_interaction_mode_label),
                        value = interactionModeText,
                    )

                    Spacer(modifier = Modifier.height(spacingMd))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        FilledTonalButton(
                            onClick = onOpenAccessibility,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.SettingsAccessibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(spacingXs))
                            Text(stringResource(R.string.automation_open_accessibility))
                        }
                        AnimatedVisibility(visible = showShizukuAuthorize, modifier = Modifier.weight(1f)) {
                            FilledTonalButton(
                                onClick = onAuthorizeShizuku,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Shield, contentDescription = null)
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
                        subtitle = stringResource(R.string.automation_console_mode_subtitle),
                    )
                    Spacer(modifier = Modifier.height(spacingMd))

                    Column(verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                        AutomationModeOption(
                            selected = !isBackgroundMode,
                            title = stringResource(R.string.automation_execution_front_mode),
                            summary = stringResource(R.string.automation_mode_description_front),
                            onClick = { onExecutionModeChange(false) },
                        )
                        AutomationModeOption(
                            selected = isBackgroundMode,
                            title = stringResource(R.string.automation_execution_background_mode),
                            summary = stringResource(R.string.automation_mode_description_background),
                            onClick = { onExecutionModeChange(true) },
                        )
                    }

                    if (isBackgroundMode) {
                        Spacer(modifier = Modifier.height(spacingMd))
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

                    Spacer(modifier = Modifier.height(spacingMd))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(spacingMd))

                    AutomationSwitchRow(
                        title = stringResource(R.string.automation_shizuku_mode_label),
                        summary = stringResource(R.string.automation_console_shizuku_subtitle),
                        checked = useShizukuInteraction,
                        onCheckedChange = onShizukuModeChange,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    AutomationSwitchRow(
                        title = stringResource(R.string.automation_auto_approve_label),
                        summary = stringResource(R.string.automation_console_auto_approve_subtitle),
                        checked = autoApprove,
                        onCheckedChange = onAutoApproveChange,
                    )
                }
            }

            item {
                AutomationSectionCard {
                    SectionHeading(
                        title = stringResource(R.string.automation_task_confirm_label),
                        subtitle = stringResource(R.string.automation_console_task_subtitle),
                    )
                    Spacer(modifier = Modifier.height(spacingMd))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TextField(
                            value = taskText,
                            onValueChange = onTaskChange,
                            modifier = Modifier.weight(1f),
                            minLines = 4,
                            maxLines = 7,
                            placeholder = {
                                Text(
                                    text = taskHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            shape = MaterialTheme.shapes.large,
                        )
                        FilledTonalIconButton(
                            onClick = onVoiceTask,
                            modifier =
                                Modifier
                                    .size(dimensionResource(R.dimen.m3t_automation_voice_button_size))
                                    .graphicsLayer {
                                        scaleX = if (isListening) voiceScale else 1f
                                        scaleY = if (isListening) voiceScale else 1f
                                        alpha = if (isListening) voiceAlpha else 1f
                                    },
                        ) {
                            Icon(Icons.Outlined.KeyboardVoice, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onUseRecommendTask),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(spacingMd),
                            horizontalArrangement = Arrangement.spacedBy(spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.automation_console_recommend_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = recommendText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = stringResource(R.string.automation_console_recommend_action),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            item {
                AutomationSectionCard {
                    SectionHeading(
                        title = stringResource(R.string.automation_console_controls_title),
                        subtitle = stringResource(R.string.automation_console_controls_subtitle),
                    )
                    Spacer(modifier = Modifier.height(spacingMd))

                    Button(
                        onClick = onStart,
                        enabled = startButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.m3t_button_height)),
                    ) {
                        Icon(
                            imageVector = if (startButtonTerminateStyle) Icons.Outlined.StopCircle else Icons.Outlined.Tune,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(spacingSm))
                        Text(startButtonText)
                    }

                    Spacer(modifier = Modifier.height(spacingSm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        FilledTonalButton(
                            onClick = onPause,
                            enabled = pauseButtonEnabled,
                            modifier = Modifier.weight(1f).height(dimensionResource(R.dimen.m3t_compact_button_height)),
                        ) {
                            Text(pauseButtonText)
                        }
                        FilledTonalButton(
                            onClick = onStop,
                            enabled = stopButtonEnabled,
                            modifier = Modifier.weight(1f).height(dimensionResource(R.dimen.m3t_compact_button_height)),
                        ) {
                            Text(stringResource(R.string.automation_stop))
                        }
                    }
                }
            }

            item {
                AutomationSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.automation_log_system_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.automation_console_log_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = onCopyLog) {
                            Text(stringResource(R.string.common_copy))
                        }
                    }

                    Spacer(modifier = Modifier.height(spacingMd))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        SelectionContainer {
                            Text(
                                text = logText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth().padding(spacingMd),
                            )
                        }
                    }
                }
            }
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
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

@Composable
private fun AutomationInfoRow(
    label: String,
    value: String,
    accentColor: Color? = null,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd),
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (accentColor != null) {
            Box(
                modifier =
                    Modifier
                        .size(dimensionResource(R.dimen.m3t_automation_status_dot))
                        .background(accentColor, MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun AutomationModeOption(
    selected: Boolean,
    title: String,
    summary: String,
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

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacingMd),
            verticalArrangement = Arrangement.spacedBy(spacingXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor,
            )
            if (selected) {
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
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm)),
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
