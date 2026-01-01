package com.ai.assistance.operit.ui.features.settings.sections

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun AdvancedSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var useApiKeyPool by remember(config.id) { mutableStateOf(config.useMultipleApiKeys) }
    var apiKeyPool by remember(config.id) { mutableStateOf(config.apiKeyPool) }

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyInfo?>(null) }
    
    // Save changes to the config
    fun saveChanges() {
        scope.launch {
            val updatedConfig = config.copy(
                useMultipleApiKeys = useApiKeyPool,
                apiKeyPool = apiKeyPool
            )
            configManager.saveModelConfig(updatedConfig)
            showNotification(context.getString(R.string.advanced_settings_saved))
        }
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    
                    val keys = content.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it.startsWith("sk-") }
                    
                    if (keys.isEmpty()) {
                        showNotification(context.getString(R.string.no_valid_keys_found))
                        return@launch
                    }
                    
                    val newKeys = keys.mapIndexed { _, key ->
                        ApiKeyInfo(
                            id = UUID.randomUUID().toString(),
                            name = "导入密钥 ${key.takeLast(4)}",
                            key = key,
                            isEnabled = true
                        )
                    }
                    
                    apiKeyPool = apiKeyPool + newKeys
                    saveChanges()
                    showNotification(context.getString(R.string.imported_keys_count, keys.size))
                } catch (e: Exception) {
                    showNotification(context.getString(R.string.batch_import_failed) + ": ${e.message}")
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.advanced_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // API Key Pool Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        useApiKeyPool = !useApiKeyPool
                        saveChanges()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.use_api_key_pool),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.api_key_pool_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useApiKeyPool,
                    onCheckedChange = {
                        useApiKeyPool = it
                        saveChanges()
                    }
                )
            }

            // API Key Pool Management UI
            AnimatedVisibility(
                visible = useApiKeyPool,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    if (apiKeyPool.isEmpty()) {
                        Text(
                            stringResource(R.string.api_key_pool_empty),
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            apiKeyPool.forEach { keyInfo ->
                                ApiKeyItem(
                                    keyInfo = keyInfo,
                                    onEdit = { editingKey = it },
                                    onDelete = { keyToDelete ->
                                        apiKeyPool = apiKeyPool.filter { it.id != keyToDelete.id }
                                        saveChanges()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { editingKey = null; showAddKeyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_api_key))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "text/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            filePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.batch_import_keys))
                    }
                    
                    Text(
                        text = stringResource(R.string.import_format_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }

    if (showAddKeyDialog || editingKey != null) {
        ApiKeyEditDialog(
            keyInfo = editingKey,
            onDismiss = { showAddKeyDialog = false; editingKey = null },
            onSave = { keyInfo ->
                if (editingKey == null) { // Add new key
                    apiKeyPool = apiKeyPool + keyInfo
                } else { // Update existing key
                    apiKeyPool = apiKeyPool.map { if (it.id == keyInfo.id) keyInfo else it }
                }
                saveChanges()
                showAddKeyDialog = false
                editingKey = null
            }
        )
    }
}

@Composable
private fun ApiKeyItem(
    keyInfo: ApiKeyInfo,
    onEdit: (ApiKeyInfo) -> Unit,
    onDelete: (ApiKeyInfo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${keyInfo.name} (sk-...${keyInfo.key.takeLast(4)})",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onEdit(keyInfo) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Edit, 
                contentDescription = stringResource(R.string.edit_api_key), 
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(
            onClick = { onDelete(keyInfo) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete, 
                contentDescription = stringResource(R.string.delete_api_key), 
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyEditDialog(
    keyInfo: ApiKeyInfo?,
    onDismiss: () -> Unit,
    onSave: (ApiKeyInfo) -> Unit
) {
    var key by remember { mutableStateOf(keyInfo?.key ?: "") }
    val isNew = keyInfo == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.add_api_key) else stringResource(R.string.edit_api_key)) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = "API Key ${key.takeLast(4)}"
                    val newKeyInfo = keyInfo?.copy(name = finalName, key = key)
                        ?: ApiKeyInfo(id = UUID.randomUUID().toString(), name = finalName, key = key, isEnabled = true)
                    onSave(newKeyInfo)
                },
                enabled = key.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
} 