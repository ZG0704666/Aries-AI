package com.ai.phoneagent.ui.automation

import com.ai.phoneagent.ui.components.AriesPageTopBar
import com.ai.phoneagent.core.designsystem.theme.AriesGlassCard
import com.ai.phoneagent.core.designsystem.theme.AriesGlassButton
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.composables.icons.lucide.Info
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavHostController
import com.ai.phoneagent.R
import com.ai.phoneagent.core.automation.TaskStatus
import com.ai.phoneagent.viewmodel.AutomationViewModel
import org.koin.androidx.compose.koinViewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskScreen(navController: NavHostController, runId: String, resultPage: Boolean = false, onStartNextTask: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) {
        var current: Context = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as ViewModelStoreOwner
    }
    val vm: AutomationViewModel = koinViewModel(viewModelStoreOwner = owner)
    val current by vm.taskSnapshot.collectAsState()
    val saved by vm.latestResult.collectAsState(initial = null)
    val task = current?.takeIf { it.runId == runId }
    val result = saved?.takeIf { it.matchesRun(runId) }
    val status = task?.status ?: result?.let { runCatching { TaskStatus.valueOf(it.status) }.getOrDefault(TaskStatus.FAILED) }
    var showLogs by androidx.compose.runtime.saveable.rememberSaveable(runId, resultPage) { mutableStateOf(!resultPage) }
    val statusLabel = stringResource(when (status) {
        TaskStatus.RUNNING -> R.string.ui_v2_running
        TaskStatus.PAUSE_REQUESTED -> R.string.ui_v2_pause_requested
        TaskStatus.PAUSED -> R.string.ui_v2_paused
        TaskStatus.SUCCEEDED -> R.string.ui_v2_success
        TaskStatus.CANCELLED -> R.string.ui_v2_cancelled
        else -> R.string.ui_v2_failed
    })
    LaunchedEffect(runId, status, resultPage) {
        if (!resultPage && status?.isTerminal == true && navController.currentBackStackEntry?.arguments?.getString("runId") == runId) {
            navController.navigate("task/$runId/result") { popUpTo("task/{runId}") { inclusive = true }; launchSingleTop = true }
        }
    }
    val spacing = dimensionResource(DesignR.dimen.aries_page_padding)
    val gap = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val goal = task?.goal ?: result?.goal.orEmpty()
    val message = task?.message ?: result?.message.orEmpty()
    val log = (task?.log ?: result?.log).orEmpty()
    val steps = task?.steps ?: result?.steps ?: 0
    Scaffold(containerColor = Color.Transparent, topBar = {
        AriesPageTopBar(stringResource(if (resultPage) R.string.ui_v2_task_result else R.string.ui_v2_task_details), { navController.popBackStack() })
    }, bottomBar = {
        if (!resultPage && status != null && !status.isTerminal) {
            FlowRow(Modifier.fillMaxWidth().navigationBarsPadding().padding(spacing), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(gap)) {
                AriesGlassButton(onClick = vm::togglePause) {
                    Text(stringResource(if (status == TaskStatus.PAUSED || status == TaskStatus.PAUSE_REQUESTED) R.string.ui_v2_resume else R.string.ui_v2_pause))
                }
                AriesGlassButton(onClick = vm::stopAgent, contentColor = MaterialTheme.colorScheme.error) { Text(stringResource(R.string.ui_v2_stop)) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (status == null) {
                item { Text(stringResource(R.string.ui_v2_unavailable), style = MaterialTheme.typography.titleLarge) }
            } else {
                if (resultPage) {
                    item {
                        val shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_card_radius))
                        Box(Modifier.size(dimensionResource(DesignR.dimen.aries_brand_size)).ariesGlass(shape), contentAlignment = Alignment.Center) {
                            Icon(if (status == TaskStatus.SUCCEEDED) Lucide.CircleCheck else if (status.isTerminal) Lucide.CircleX else Lucide.Activity,
                                null, Modifier.size(dimensionResource(DesignR.dimen.aries_brand_icon)),
                                tint = if (status == TaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                    item { Text(statusLabel, style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = with(density) { dimensionResource(DesignR.dimen.aries_type_headline).toSp() }, fontWeight = FontWeight.Bold)) }
                    if (message.isNotBlank()) item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        Text(stringResource(R.string.ui_v2_goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(gap))
                        Text(goal, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
                item {
                    AriesGlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Icon(if (status == TaskStatus.SUCCEEDED) Lucide.CircleCheck else Lucide.Activity, null,
                                Modifier.size(dimensionResource(DesignR.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.primary)
                            Text(statusLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(R.string.ui_v2_actual_steps, steps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (resultPage) Text(goal, style = MaterialTheme.typography.bodyMedium)
                        else if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
                        if (status == TaskStatus.RUNNING || status == TaskStatus.PAUSE_REQUESTED) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val startedAt = task?.startedAt ?: result?.startedAt ?: 0L
                        if (startedAt > 0) Text(stringResource(R.string.ui_v2_started_at,
                            remember(startedAt) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(startedAt)) }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (resultPage) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                            Button(onClick = onStartNextTask,
                                shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius))) { Text(stringResource(R.string.ui_v2_new_task)) }
                            AriesGlassButton(onClick = { showLogs = !showLogs }) { Text(stringResource(R.string.ui_v2_details)) }
                        }
                    }
                    item {
                        AriesGlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Icon(Lucide.Info, null, Modifier.size(dimensionResource(DesignR.dimen.aries_nav_icon)), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.ui_v2_next_task_hint), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (showLogs) {
                    if (!resultPage) item {
                        Text(stringResource(R.string.ui_v2_execution_steps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(gap))
                        Text(stringResource(R.string.ui_v2_actual_steps, steps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_v2_logs), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { clipboard.setText(AnnotatedString(log)) }, enabled = log.isNotBlank()) { Text(stringResource(R.string.ui_v2_copy_logs)) }
                        }
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(log.ifBlank { context.getString(R.string.ui_v2_no_logs) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
