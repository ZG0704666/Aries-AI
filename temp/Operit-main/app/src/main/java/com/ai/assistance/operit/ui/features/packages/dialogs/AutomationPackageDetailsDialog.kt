package com.ai.assistance.operit.ui.features.packages.dialogs

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationPackageDetailsDialog(
    onDismiss: () -> Unit,
    message: String = "Kotlin UI 自动化配置功能已移除。"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
                title = { Text("提示") },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}