package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

@Composable
fun ChatHeader(
        showChatHistorySelector: Boolean,
        onToggleChatHistorySelector: () -> Unit,
        modifier: Modifier = Modifier,
        onLaunchFloatingWindow: () -> Unit = {},
        isFloatingMode: Boolean = false,
        historyIconColor: Int? = null,
        pipIconColor: Int? = null,
        activeCharacterName: String,
        activeCharacterAvatarUri: String?,
        onCharacterClick: () -> Unit
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
        ) {
                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                color =
                                                        if (showChatHistorySelector)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        else Color.Transparent,
                                                shape = CircleShape
                                        )
                ) {
                        IconButton(
                                onClick = onToggleChatHistorySelector,
                                modifier = Modifier.matchParentSize()
                        ) {
                                Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription =
                                                if (showChatHistorySelector) stringResource(R.string.hide_history) else stringResource(R.string.show_history),
                                        tint =
                                                historyIconColor?.let { Color(it) }
                                                        ?: if (showChatHistorySelector)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }

                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                color =
                                                        if (isFloatingMode)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        else Color.Transparent,
                                                shape = CircleShape
                                        )
                ) {
                        IconButton(
                                onClick = onLaunchFloatingWindow,
                                modifier = Modifier.matchParentSize()
                        ) {
                                Icon(
                                        imageVector = Icons.Default.PictureInPicture,
                                        contentDescription =
                                                if (isFloatingMode) stringResource(R.string.close_floating_window) else stringResource(R.string.open_floating_window),
                                        tint =
                                                pipIconColor?.let { Color(it) }
                                                        ?: if (isFloatingMode)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }

                // Character Switcher
                Row(
                        modifier =
                                Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onCharacterClick)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                        // Placeholder for Avatar
                        Box(
                                modifier =
                                        Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                        ) {
                                // Use Coil or another image loader for activeCharacterAvatarUri
                                if (activeCharacterAvatarUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = Uri.parse(activeCharacterAvatarUri)),
                                        contentDescription = "Character Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = "Character Avatar",
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                        }
                        Text(
                                text = activeCharacterName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}
