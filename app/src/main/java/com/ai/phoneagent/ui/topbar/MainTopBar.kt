package com.ai.phoneagent.ui.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.ai.phoneagent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    statusText: String,
    statusVisible: Boolean,
    onToggleStatus: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenFloatingWindow: () -> Unit,
    modelName: String = "",
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val titleGap = dimensionResource(R.dimen.m3t_top_bar_title_gap)
    val compactToolbarHeight = dimensionResource(R.dimen.m3t_toolbar_height) - spacingXs

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleStatus),
        ) {
            TopAppBar(
                expandedHeight = compactToolbarHeight,
                title = {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val modelChipMaxWidth = maxWidth * 0.62f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (modelName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(titleGap - spacingXxxs))
                                Surface(
                                    modifier = Modifier.widthIn(max = modelChipMaxWidth),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = spacingXs, vertical = spacingXxxs),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_24),
                            contentDescription = stringResource(R.string.drawer_open_navigation),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            painter = painterResource(R.drawable.ic_new_chat_24),
                            contentDescription = stringResource(R.string.top_bar_new_chat),
                        )
                    }
                    IconButton(onClick = onOpenFloatingWindow) {
                        Icon(
                            painter = painterResource(R.drawable.ic_floating_window_24),
                            contentDescription = stringResource(R.string.top_bar_floating_window),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                // Use default TopAppBarDefaults.windowInsets (status bars)
            )

            AnimatedVisibility(
                visible = statusVisible,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingXxxs),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingSm),
                    )
                }
            }
        }
    }
}
