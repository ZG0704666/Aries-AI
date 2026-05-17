package com.ai.phoneagent.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Smartphone
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.PermissionSetupSupport
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var accOk by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var micOk by remember { mutableStateOf(false) }

    val updateUi = {
        accOk = PermissionSetupSupport.isAccessibilityEnabled(context)
        overlayOk = PermissionSetupSupport.hasOverlayPermission(context)
        micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateUi()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micOk = isGranted
    }

    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(DesignSystemR.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xl)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.perm_sheet_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacingLg, vertical = spacingSm),
            verticalArrangement = Arrangement.spacedBy(spacingMd)
        ) {
            Text(
                text = stringResource(R.string.perm_sheet_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacingMd)
            )

            PermissionRow(
                icon = { Icon(Lucide.Smartphone, contentDescription = null) },
                title = stringResource(R.string.perm_sheet_accessibility_title),
                subtitle = stringResource(R.string.perm_sheet_accessibility_desc),
                isGranted = accOk,
                actionText = stringResource(R.string.perm_sheet_action_enable),
                onAction = { activity?.let { PermissionSetupSupport.openAccessibilitySettings(it) } }
            )

            PermissionRow(
                icon = { Icon(Lucide.Layers, contentDescription = null) },
                title = stringResource(R.string.perm_sheet_overlay_title),
                subtitle = stringResource(R.string.perm_sheet_overlay_desc),
                isGranted = overlayOk,
                actionText = stringResource(R.string.perm_sheet_action_settings),
                onAction = { activity?.let { PermissionSetupSupport.openOverlaySettings(it) } }
            )

            PermissionRow(
                icon = { Icon(Lucide.Mic, contentDescription = null) },
                title = stringResource(R.string.perm_sheet_microphone_title),
                subtitle = stringResource(R.string.perm_sheet_microphone_desc),
                isGranted = micOk,
                actionText = stringResource(R.string.perm_sheet_action_grant),
                onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            Spacer(modifier = Modifier.weight(1f))

            val allOk = accOk && overlayOk && micOk
            
            Button(
                onClick = {
                    if (allOk) {
                        navController.popBackStack()
                    } else {
                        activity?.let {
                            PermissionSetupSupport.guideAll(
                                activity = it,
                                requestShizukuPermissionCode = 2026,
                                requestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onReady = { navController.popBackStack() },
                                onUiRefresh = { updateUi() }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd)
            ) {
                Text(
                    stringResource(
                        if (allOk) R.string.perm_sheet_primary_action_ready
                        else R.string.perm_sheet_primary_action
                    )
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacingMd),
            verticalAlignment = Alignment.CenterVertically
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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacingMd),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(if (isGranted) R.string.perm_sheet_status_ready else R.string.perm_sheet_status_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onAction,
                enabled = !isGranted
            ) {
                Text(if (isGranted) stringResource(R.string.perm_sheet_action_ready) else actionText)
            }
        }
    }
}
