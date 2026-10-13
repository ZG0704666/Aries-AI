package com.ai.phoneagent.ui.settings

import com.ai.phoneagent.ui.components.AriesPageTopBar
import android.app.Activity
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
import com.ai.phoneagent.R
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsSwitchItem
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationPreferencesScreen(onBack: () -> Unit, onOpenControl: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as ViewModelStoreOwner
    }
    val vm: AutomationViewModel = koinViewModel(viewModelStoreOwner = owner)
    val task by vm.taskSnapshot.collectAsState()
    val editable = task == null || task?.status?.isTerminal == true
    val spacing = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_page_padding)
    LaunchedEffect(Unit) { vm.onRefreshStatus() }
    Scaffold(containerColor = Color.Transparent, topBar = {
        AriesPageTopBar(stringResource(R.string.settings_entry_automation_title), onBack)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
            item { Text(vm.statusSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_background_execution), checked = vm.isBackgroundMode, enabled = editable, onCheckedChange = vm::onExecutionModeChange) }
            item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_shizuku_execution), checked = vm.useShizukuInteraction, enabled = editable, onCheckedChange = vm::onShizukuModeChange) }
            item { AriesSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_auto_confirmation), checked = vm.autoApprove, enabled = editable, onCheckedChange = vm::onAutoApproveChange) }
            item { Text(stringResource(R.string.ui_v2_auto_confirm_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick = onOpenControl, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.automation_console_overview_title)) } }
        }
    }
}
