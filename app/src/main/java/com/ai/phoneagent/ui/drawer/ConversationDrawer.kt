package com.ai.phoneagent.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import com.ai.phoneagent.R
import com.ai.phoneagent.navigation.Routes

sealed interface DrawerConversationUiItem {
    val stableKey: String

    data class Header(
        val label: String
    ) : DrawerConversationUiItem {
        override val stableKey: String = "header:$label"
    }

    data class Conversation(
        val conversationId: Long,
        val title: String,
        val preview: String,
        val selected: Boolean,
    ) : DrawerConversationUiItem {
        override val stableKey: String = "conversation:$conversationId"
    }
}

@Composable
fun ConversationDrawer(
    searchQuery: String,
    items: List<DrawerConversationUiItem>,
    emptyMessage: String,
    navController: NavHostController? = null,
    onSearchQueryChange: (String) -> Unit,
    onConversationClick: (Long) -> Unit,
    onConversationLongClick: (Long) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val itemHorizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val itemVerticalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_v)
    val itemRadius = dimensionResource(R.dimen.m3t_drawer_conversation_radius)

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            placeholder = {
                Text(text = stringResource(R.string.drawer_search_hint))
            },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
        )

        Text(
            text = stringResource(R.string.drawer_section_recent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = spacingXl, start = itemHorizontalPadding, end = itemHorizontalPadding),
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = spacingSm, bottom = spacingMd),
                verticalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                items(
                    items = items,
                    key = { it.stableKey },
                ) { item ->
                    when (item) {
                        is DrawerConversationUiItem.Header -> DrawerConversationHeader(item.label)
                        is DrawerConversationUiItem.Conversation -> {
                            DrawerConversationRow(
                                item = item,
                                horizontalPadding = itemHorizontalPadding,
                                verticalPadding = itemVerticalPadding,
                                itemRadius = itemRadius,
                                onClick = { onConversationClick(item.conversationId) },
                                onLongClick = { onConversationLongClick(item.conversationId) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacingMd))

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            onSettingsClick?.invoke()
                                ?: navController?.navigate(Routes.Settings.route)
                        },
                    ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingLg, vertical = spacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingMd),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(spacingXl),
                )
                Text(
                    text = stringResource(R.string.drawer_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DrawerConversationHeader(label: String) {
    val horizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, top = spacingSm),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerConversationRow(
    item: DrawerConversationUiItem.Conversation,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    itemRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember(item.conversationId) { mutableStateOf(false) }
    val backgroundColor =
        if (item.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(itemRadius))
                    .background(backgroundColor)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = stringResource(R.string.drawer_more_actions),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuExpanded = true
                        },
                    )
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xs)),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.preview.isNotBlank()) {
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.drawer_delete_conversation)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onLongClick()
                },
            )
        }
    }
}
