package com.ai.phoneagent.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ai.phoneagent.R

data class ConversationHistoryItemUi(
    val id: Long,
    val title: String,
    val preview: String,
)

@Composable
fun ConversationHistoryDialog(
    items: List<ConversationHistoryItemUi>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val cardMargin = dimensionResource(R.dimen.m3t_dialog_card_margin_h)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val listMaxHeight = dimensionResource(R.dimen.m3t_license_list_height)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = cardMargin)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = dimensionResource(R.dimen.m3t_spacing_xxxs),
            shadowElevation = dimensionResource(R.dimen.m3t_spacing_sm),
        ) {
            Column(modifier = Modifier.padding(dialogPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_dialog_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacingLg, bottom = spacingSm),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = listMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        items(items = items, key = { it.id }) { item ->
                            ConversationHistoryRow(
                                item = item,
                                onClick = { onSelect(item.id) },
                                onDelete = { onDelete(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationHistoryRow(
    item: ConversationHistoryItemUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = dimensionResource(R.dimen.m3t_size_zero),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingLg, vertical = spacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.MessageCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(R.dimen.m3t_about_row_icon_size)),
            )

            Column(
                modifier = Modifier.weight(1f).padding(start = spacingLg, end = spacingSm),
                verticalArrangement = Arrangement.spacedBy(spacingXs),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Lucide.Trash2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
