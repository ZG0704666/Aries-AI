package com.ai.assistance.operit.ui.features.announcement

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay

@Composable
fun ChatBindingAnnouncementDialog(
    onNavigateToChatManagement: () -> Unit,
    onDismiss: () -> Unit,
    countdownSeconds: Int = 5
) {
    var remainingSeconds by remember(countdownSeconds) { mutableStateOf(countdownSeconds) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    val acknowledgeEnabled = remainingSeconds == 0

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(id = R.string.chat_binding_announcement_title)) },
        text = { Text(text = stringResource(id = R.string.chat_binding_announcement_body)) },
        confirmButton = {
            TextButton(onClick = onNavigateToChatManagement) {
                Text(text = stringResource(id = R.string.chat_binding_announcement_primary_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = acknowledgeEnabled) {
                val label =
                    if (acknowledgeEnabled) {
                        stringResource(id = R.string.chat_binding_announcement_acknowledge)
                    } else {
                        stringResource(
                            id = R.string.chat_binding_announcement_acknowledge_countdown,
                            remainingSeconds
                        )
                    }
                Text(text = label)
            }
        }
    )
}

