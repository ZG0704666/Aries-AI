package com.ai.phoneagent.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.ariesGlass

@Composable
fun AriesAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    textScrollable: Boolean = true,
) {
    BackHandler(onBack = onDismissRequest)

    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val cardMargin = dimensionResource(R.dimen.m3t_dialog_card_margin_h)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)

    val textScrollState = rememberScrollState()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "ariesDialogScrimAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f * scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(animationSpec = tween(durationMillis = 120)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.92f
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = cardMargin)
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxHeight * 0.92f)
                        .ariesGlass(shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(horizontal = dialogPadding)
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = spacingXl),
                                contentAlignment = Alignment.Center
                            ) {
                                icon()
                            }
                        }

                        if (title != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurface
                            ) {
                                ProvideTextStyle(value = MaterialTheme.typography.headlineSmall) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = if (icon == null) spacingXl else spacingMd)
                                    ) {
                                        title()
                                    }
                                }
                            }
                        }

                        if (text != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .fillMaxWidth()
                                            .padding(top = if (title == null) spacingXl else spacingLg)
                                            .then(
                                                if (textScrollable) {
                                                    Modifier.verticalScroll(textScrollState)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        text()
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacingSm),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = spacingSm,
                            alignment = Alignment.End
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
