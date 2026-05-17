package com.ai.phoneagent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Shield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionBottomSheet(
    onDismissRequest: () -> Unit,
    permissionUiState: MainOnboardingOverlay.PermissionUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenMic: () -> Unit,
    onGuideAll: () -> Unit,
    onDone: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        PermissionBottomSheetContent(
            permissionUiState = permissionUiState,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlay = onOpenOverlay,
            onOpenMic = onOpenMic,
            onGuideAll = onGuideAll,
            onDone = onDone,
        )
    }
}

@Composable
fun PermissionBottomSheetContent(
    permissionUiState: MainOnboardingOverlay.PermissionUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenMic: () -> Unit,
    onGuideAll: () -> Unit,
    onDone: () -> Unit,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val buttonHeight = dimensionResource(R.dimen.m3t_button_height)
    val compactButtonHeight = dimensionResource(R.dimen.m3t_compact_button_height)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacingXl, vertical = spacingLg),
        verticalArrangement = Arrangement.spacedBy(spacingMd),
    ) {
        Text(
            text = stringResource(R.string.perm_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacingLg),
                horizontalArrangement = Arrangement.spacedBy(spacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = CircleShape) {
                    Box(modifier = Modifier.padding(spacingSm), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                    Text(
                        text = stringResource(R.string.perm_sheet_shizuku_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.perm_sheet_shizuku_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PermissionRow(Lucide.Smartphone, stringResource(R.string.perm_sheet_accessibility_title), stringResource(R.string.perm_sheet_accessibility_desc), permissionUiState.accessibilityReady, stringResource(R.string.perm_sheet_action_enable), onOpenAccessibility, compactButtonHeight)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PermissionRow(Lucide.ExternalLink, stringResource(R.string.perm_sheet_overlay_title), stringResource(R.string.perm_sheet_overlay_desc), permissionUiState.overlayReady, stringResource(R.string.perm_sheet_action_settings), onOpenOverlay, compactButtonHeight)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PermissionRow(Lucide.Mic, stringResource(R.string.perm_sheet_microphone_title), stringResource(R.string.perm_sheet_microphone_desc), permissionUiState.microphoneReady, stringResource(R.string.perm_sheet_action_grant), onOpenMic, compactButtonHeight)
            }
        }

        Button(
            onClick = onGuideAll,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
        ) {
            Text(stringResource(if (permissionUiState.allReady) R.string.perm_sheet_primary_action_ready else R.string.perm_sheet_primary_action))
        }

        if (!permissionUiState.allReady) {
            FilledTonalButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .navigationBarsPadding(),
            ) {
                Text(stringResource(R.string.perm_sheet_secondary_action))
            }
        } else {
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    ready: Boolean,
    pendingAction: String,
    onAction: () -> Unit,
    buttonHeight: Dp,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacingLg),
        horizontalArrangement = Arrangement.spacedBy(spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), shape = CircleShape) {
            Box(modifier = Modifier.padding(spacingSm), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(if (ready) R.string.perm_sheet_status_ready else R.string.perm_sheet_status_pending),
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onAction,
            enabled = !ready,
            modifier = Modifier.height(buttonHeight),
        ) {
            Text(if (ready) stringResource(R.string.perm_sheet_action_ready) else pendingAction)
        }
    }
}
