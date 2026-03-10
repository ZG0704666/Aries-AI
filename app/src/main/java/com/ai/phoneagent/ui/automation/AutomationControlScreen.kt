package com.ai.phoneagent.ui.automation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val voiceTransition = rememberInfiniteTransition(label = "automationVoice")
    val voiceScale by
        voiceTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.12f else 1f,
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
            verticalArrangement = Arrangement.spacedBy(spacingLg),
        ) {
            item {
                AutomationCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(dimensionResource(R.dimen.m3t_automation_status_dot))
                                    .padding(dimensionResource(R.dimen.m3t_size_zero)),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.small,
                                color =
                                    when (statusTone) {
                                        AutomationStatusTone.Ready -> MaterialTheme.colorScheme.primary
                                        AutomationStatusTone.Partial -> MaterialTheme.colorScheme.tertiary
                                        AutomationStatusTone.Inactive -> MaterialTheme.colorScheme.error
                                    },
                            ) {}
                        }

                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = spacingLg, end = spacingMd),
                        ) {
                            Text(
                                text = stringResource(R.string.automation_status_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(onClick = onOpenAccessibility) {
                                Icon(Icons.Outlined.SettingsAccessibility, contentDescription = null)
                            }
                            if (showShizukuAuthorize) {
                                Spacer(modifier = Modifier.height(spacingXs))
                                OutlinedButton(onClick = onAuthorizeShizuku) {
                                    Icon(Icons.Outlined.Shield, contentDescription = null)
                                }
                            }
                        }

                        IconButton(onClick = onRefreshStatus) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        }
                    }
                }
            }

            item {
                AutomationCard {
                    Text(
                        text = stringResource(R.string.automation_execution_mode_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                        FilterChip(
                            selected = !isBackgroundMode,
                            onClick = { onExecutionModeChange(false) },
                            label = { Text(stringResource(R.string.automation_execution_front_mode)) },
                        )
                        FilterChip(
                            selected = isBackgroundMode,
                            onClick = { onExecutionModeChange(true) },
                            label = { Text(stringResource(R.string.automation_execution_background_mode)) },
                        )
                    }
                    Spacer(modifier = Modifier.height(spacingSm))
                    Text(
                        text = modeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = virtualDisplayStatus,
                            modifier = Modifier.fillMaxWidth().padding(spacingSm),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(spacingSm))
                    SwitchRow(
                        title = stringResource(R.string.automation_shizuku_mode_label),
                        checked = useShizukuInteraction,
                        onCheckedChange = onShizukuModeChange,
                    )
                    Spacer(modifier = Modifier.height(spacingXs))
                    SwitchRow(
                        title = stringResource(R.string.automation_auto_approve_label),
                        checked = autoApprove,
                        onCheckedChange = onAutoApproveChange,
                    )
                }
            }

            item {
                AutomationCard {
                    Text(
                        text = stringResource(R.string.automation_task_confirm_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    OutlinedTextField(
                        value = taskText,
                        onValueChange = onTaskChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text(taskHint) },
                        trailingIcon = {
                            IconButton(
                                onClick = onVoiceTask,
                                modifier =
                                    Modifier.graphicsLayer {
                                        scaleX = if (isListening) voiceScale else 1f
                                        scaleY = if (isListening) voiceScale else 1f
                                        alpha = if (isListening) voiceAlpha else 1f
                                    },
                            ) {
                                Icon(Icons.Outlined.KeyboardVoice, contentDescription = null)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(spacingSm))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onUseRecommendTask)
                                    .padding(spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Text(
                                text = recommendText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = spacingSm),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPause,
                        enabled = pauseButtonEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(pauseButtonText)
                    }
                    Button(
                        onClick = onStart,
                        enabled = startButtonEnabled,
                        modifier = Modifier.weight(1.2f),
                    ) {
                        if (startButtonTerminateStyle) {
                            Icon(Icons.Outlined.StopCircle, contentDescription = null)
                            Text(
                                text = startButtonText,
                                modifier = Modifier.padding(start = spacingSm),
                            )
                        } else {
                            Icon(Icons.Outlined.Tune, contentDescription = null)
                            Text(
                                text = startButtonText,
                                modifier = Modifier.padding(start = spacingSm),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onStop,
                        enabled = stopButtonEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.automation_stop))
                    }
                }
            }

            item {
                AutomationCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.automation_log_system_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onCopyLog) {
                            Text(stringResource(R.string.common_copy))
                        }
                    }
                    Spacer(modifier = Modifier.height(spacingMd))
                    SelectionContainer {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationCard(
    content: @Composable () -> Unit,
) {
    val cardPadding = dimensionResource(R.dimen.m3t_about_row_text_gap)
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
        ) {
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
