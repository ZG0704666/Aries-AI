package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutAdjustmentSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 读取当前设置
    val chatSettingsButtonEndPadding by userPreferences.chatSettingsButtonEndPadding
        .collectAsState(initial = 2f)
    val chatAreaHorizontalPadding by userPreferences.chatAreaHorizontalPadding
        .collectAsState(initial = 16f)

    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            EditableDpSettingCard(
                title = stringResource(R.string.chat_settings_button_end_padding),
                description = stringResource(R.string.chat_settings_button_end_padding_desc),
                currentValue = chatSettingsButtonEndPadding,
                onSave = { newValue ->
                    scope.launch {
                        userPreferences.saveChatSettingsButtonEndPadding(newValue)
                    }
                },
                defaultValue = 2f
            )

            Spacer(modifier = Modifier.height(16.dp))

            EditableDpSettingCard(
                title = stringResource(R.string.chat_area_horizontal_padding),
                description = stringResource(R.string.chat_area_horizontal_padding_desc),
                currentValue = chatAreaHorizontalPadding,
                onSave = { newValue ->
                    scope.launch {
                        userPreferences.saveChatAreaHorizontalPadding(newValue)
                    }
                },
                defaultValue = 16f
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditableDpSettingCard(
    title: String,
    description: String,
    currentValue: Float,
    onSave: (Float) -> Unit,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..50f
) {
    val focusManager = LocalFocusManager.current
    val df = remember { DecimalFormat("#.#") }

    var textValue by remember { mutableStateOf(df.format(currentValue)) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        textValue = df.format(currentValue)
        isError = false
    }

    val validateAndSave = {
        val floatValue = textValue.toFloatOrNull()
        if (floatValue != null && floatValue in valueRange) {
            onSave(floatValue)
            isError = false
            focusManager.clearFocus()
        } else {
            isError = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.current_value),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            isError = it.toFloatOrNull() == null
                        },
                        modifier = Modifier
                            .width(60.dp)
                            .background(
                                color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndSave() }
                        ),
                        singleLine = true
                    )
                    Text(
                        text = "dp",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (isError) {
                Text(
                    text = stringResource(R.string.invalid_value_range, valueRange.start, valueRange.endInclusive),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onSave(defaultValue) }
                ) {
                    Text(stringResource(R.string.reset_to_default))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = validateAndSave) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

