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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.composables.icons.lucide.MessageCircle
import com.ai.phoneagent.core.designsystem.R as DesignR
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

@Immutable
sealed interface DrawerConversationUiItem {
    val stableKey: String

    @Immutable
    data class Header(
        val label: String
    ) : DrawerConversationUiItem {
        override val stableKey: String = "header:$label"
    }

    @Immutable
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
    showSettingsEntry: Boolean = true,
) {
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xl)
    val itemHorizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val itemVerticalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_v)
    val itemRadius = dimensionResource(R.dimen.m3t_drawer_conversation_radius)

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth()
                .ariesGlass(RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius)))
                .heightIn(min = dimensionResource(DesignR.dimen.aries_search_height)),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                Row(Modifier.padding(horizontal = dimensionResource(DesignR.dimen.aries_setting_padding), vertical = spacingSm),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                    Icon(Lucide.Search, null, Modifier.size(dimensionResource(DesignR.dimen.aries_suggestion_icon)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) Text(stringResource(R.string.drawer_search_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            },
        )

        if (items.none { it is DrawerConversationUiItem.Header }) {
        Text(
            text = stringResource(R.string.drawer_section_recent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = spacingXl, start = itemHorizontalPadding, end = itemHorizontalPadding),
        )
        }

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

        if (showSettingsEntry) {
        Spacer(modifier = Modifier.height(spacingMd))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
            color = Color.Transparent,
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
                    imageVector = Lucide.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(spacingXl),
                )
                Text(
                    text = stringResource(R.string.drawer_settings),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
}

@Composable
private fun DrawerConversationHeader(label: String) {
    val horizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_action_radius))
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (item.selected) Modifier.ariesGlass(shape, selected = true) else Modifier)
                    .clip(shape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = stringResource(R.string.drawer_more_actions),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuExpanded = true
                        },
                    )
                    .heightIn(min = dimensionResource(DesignR.dimen.aries_history_row_height))
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesignR.dimen.m3t_spacing_sm)),
        ) {
            Icon(Lucide.MessageCircle, null, Modifier.size(dimensionResource(DesignR.dimen.aries_nav_icon)),
                tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesignR.dimen.m3t_spacing_xxxs))) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = with(density) { dimensionResource(DesignR.dimen.aries_row_title).toSp() }, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.preview.isNotBlank()) {
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = with(density) { dimensionResource(DesignR.dimen.aries_row_caption).toSp() }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            Box {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.drawer_delete_conversation)) },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLongClick()
                        },
                    )
                }
            }
        }
    }
}
