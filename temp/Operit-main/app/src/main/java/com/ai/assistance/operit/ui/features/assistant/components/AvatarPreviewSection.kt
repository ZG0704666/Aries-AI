package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.avatar.common.view.AvatarView
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarControllerFactoryImpl
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarRendererFactoryImpl
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AvatarPreviewSection(
        modifier: Modifier = Modifier,
        uiState: AssistantConfigViewModel.UiState,
        onDeleteCurrentModel: (() -> Unit)? = null
) {
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        // Create factories for abstract avatar system
        val controllerFactory = remember { AvatarControllerFactoryImpl() }
        val rendererFactory = remember { AvatarRendererFactoryImpl() }
        
        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border =
                        BorderStroke(
                                width = 1.dp,
                                brush =
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.5f),
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.2f)
                                                        )
                                        )
                        )
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        val currentModel = uiState.currentAvatarModel
                        if (currentModel != null) {
                                // Create avatar controller
                                val avatarController = controllerFactory.createController(currentModel)
                                
                                if (avatarController != null) {
                                        // Apply current settings to controller
                                        uiState.config?.let { settings ->
                                                avatarController.updateSettings(mapOf(
                                                        "scale" to settings.scale,
                                                        "translateX" to settings.translateX,
                                                        "translateY" to settings.translateY
                                                ))
                                        }

                                        // Use abstract avatar view
                                        AvatarView(
                                        modifier = Modifier.fillMaxSize(),
                                                model = currentModel,
                                                controller = avatarController,
                                                rendererFactory = rendererFactory,
                                                onError = { error -> println("Avatar error: $error") }
                                )
                                        
                                        // Animation control area for skeletal models
                                        if (avatarController.availableAnimations.isNotEmpty()) {
                                var selectedAnim by remember { mutableStateOf<String?>(null) }

                                // A quick effect to de-select the chip after a short time
                                LaunchedEffect(selectedAnim) {
                                        if (selectedAnim != null) {
                                                delay(500) // Keep it highlighted for half a second
                                                selectedAnim = null
                                        }
                                }

                                FlowRow(
                                        modifier =
                                                Modifier.align(Alignment.BottomCenter)
                                                        .padding(8.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.3f),
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalArrangement = Arrangement.Center,
                                        maxItemsInEachRow = 4
                                ) {
                                                        avatarController.availableAnimations.forEach { name ->
                                                FilterChip(
                                                        modifier =
                                                                Modifier.padding(horizontal = 4.dp),
                                                        selected = selectedAnim == name,
                                                        onClick = {
                                                                selectedAnim = name
                                                                                avatarController.playAnimation(name, 1)
                                                        },
                                                        label = { Text(name) }
                                                )
                                        }
                                }
                                        }
                                } else {
                                        Text(
                                                text = stringResource(R.string.unsupported_model_type, currentModel.type.name),
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                }
                        } else {
                                Text(
                                        text =
                                                if (uiState.avatarConfigs.isEmpty()) stringResource(R.string.no_models_available)
                                                else stringResource(R.string.please_select_model),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.align(Alignment.Center)
                                )
                        }
                }
        }
}
