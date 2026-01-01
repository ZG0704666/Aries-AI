package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Arrangement
import com.ai.assistance.operit.api.voice.VoiceServiceFactory.VoiceServiceType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.api.voice.SiliconFlowVoiceProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechServicesSettingsScreen(
    onBackPressed: () -> Unit,
    onNavigateToTextToSpeech: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { SpeechServicesPreferences(context) }

    // --- State for TTS Settings ---
    val ttsServiceType by prefs.ttsServiceTypeFlow.collectAsState(initial = VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS)
    val httpConfig by prefs.ttsHttpConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET)
    val ttsCleanerRegexs by prefs.ttsCleanerRegexsFlow.collectAsState(initial = emptyList())

    var ttsServiceTypeInput by remember(ttsServiceType) { mutableStateOf(ttsServiceType) }
    var ttsUrlTemplateInput by remember(httpConfig) { mutableStateOf(httpConfig.urlTemplate) }
    var ttsApiKeyInput by remember(httpConfig) { mutableStateOf(httpConfig.apiKey) }
    var ttsHeadersInput by remember(httpConfig) { mutableStateOf(Json.encodeToString(httpConfig.headers)) }
    var ttsHttpMethodInput by remember(httpConfig) { mutableStateOf(httpConfig.httpMethod) }
    var ttsRequestBodyInput by remember(httpConfig) { mutableStateOf(httpConfig.requestBody) }
    var ttsContentTypeInput by remember(httpConfig) { mutableStateOf(httpConfig.contentType) }
    var ttsVoiceIdInput by remember(httpConfig) { mutableStateOf(httpConfig.voiceId) }
    var ttsModelNameInput by remember(httpConfig) { mutableStateOf(httpConfig.modelName) }
    var ttsJsonError by remember { mutableStateOf<String?>(null) }
    var httpMethodDropdownExpanded by remember { mutableStateOf(false) }
    val ttsCleanerRegexsState = remember { mutableStateListOf<String>() }

    // --- State for STT Settings ---
    val sttServiceType by prefs.sttServiceTypeFlow.collectAsState(initial = SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN)
    var sttServiceTypeInput by remember(sttServiceType) { mutableStateOf(sttServiceType) }

    // 同步 DataStore 的数据到 State
    LaunchedEffect(ttsCleanerRegexs) {
        if (ttsCleanerRegexs != ttsCleanerRegexsState.toList()) {
            ttsCleanerRegexsState.clear()
            ttsCleanerRegexsState.addAll(ttsCleanerRegexs)
        }
    }

    // 保存状态和消息
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    
    // 保存函数
    val saveSettings: () -> Unit = {
        scope.launch {
            try {
                isSaving = true
                saveMessage = null
                
                // 验证 JSON headers
                val headers = try {
                    Json.decodeFromString<Map<String, String>>(ttsHeadersInput)
                } catch (e: Exception) {
                    if (ttsHeadersInput.isNotBlank() && ttsHeadersInput != "{}") {
                        throw IllegalArgumentException(context.getString(R.string.speech_services_http_headers_error))
                    }
                    emptyMap()
                }
                
                val httpConfigData = SpeechServicesPreferences.TtsHttpConfig(
                    urlTemplate = ttsUrlTemplateInput,
                    apiKey = ttsApiKeyInput,
                    headers = headers,
                    httpMethod = ttsHttpMethodInput,
                    requestBody = ttsRequestBodyInput,
                    contentType = ttsContentTypeInput,
                    voiceId = ttsVoiceIdInput,
                    modelName = ttsModelNameInput
                )
                
                // 保存 TTS 设置
                prefs.saveTtsSettings(
                    serviceType = ttsServiceTypeInput,
                    httpConfig = httpConfigData,
                    cleanerRegexs = ttsCleanerRegexsState.toList()
                )
                
                // 保存 STT 设置
                prefs.saveSttSettings(sttServiceTypeInput)
                
                // 重置服务实例以应用新设置
                VoiceServiceFactory.resetInstance()
                SpeechServiceFactory.resetInstance()
                
                saveMessage = context.getString(R.string.speech_services_save_success)
            } catch (e: Exception) {
                saveMessage = context.getString(R.string.speech_services_save_error, e.message ?: "Unknown error")
            } finally {
                isSaving = false
                // 3秒后清除消息
                delay(3000)
                saveMessage = null
            }
        }
    }


    CustomScaffold { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- TTS Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.speech_services_tts_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            text = stringResource(R.string.speech_services_tts_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.speech_services_service_type),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var ttsDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = ttsDropdownExpanded,
                            onExpandedChange = { ttsDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(ttsServiceTypeInput) {
                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> stringResource(R.string.speech_services_tts_type_simple)
                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> stringResource(R.string.speech_services_tts_type_http)
                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_tts_type_siliconflow)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.speech_services_tts_engine)) },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_dropdown_expand))
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = ttsDropdownExpanded,
                                onDismissRequest = { ttsDropdownExpanded = false }
                            ) {
                                VoiceServiceFactory.VoiceServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> stringResource(R.string.speech_services_tts_type_simple)
                                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> stringResource(R.string.speech_services_tts_type_http)
                                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_tts_type_siliconflow)
                                                },
                                                fontWeight = if (ttsServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            ttsServiceTypeInput = type
                                            ttsDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // TTS Cleaner Regex List
                        Text(
                            text = stringResource(R.string.speech_services_tts_cleaner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = stringResource(R.string.speech_services_tts_cleaner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Column {
                            ttsCleanerRegexsState.forEachIndexed { index, regex ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = regex,
                                        onValueChange = { ttsCleanerRegexsState[index] = it },
                                        placeholder = { Text(stringResource(R.string.speech_services_tts_cleaner_placeholder)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    IconButton(onClick = { ttsCleanerRegexsState.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.speech_services_tts_cleaner_delete))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { ttsCleanerRegexsState.add("") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.speech_services_tts_cleaner_add))
                                }
                                
                                var showTemplateMenu by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { showTemplateMenu = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.speech_services_tts_cleaner_template))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                
                                DropdownMenu(
                                    expanded = showTemplateMenu,
                                    onDismissRequest = { showTemplateMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_asterisk)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\*[^*]+\\*")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_double_asterisk)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\*\\*[^*]+\\*\\*")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_parenthesis)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\([^)]+\\)")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_chinese_parenthesis)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("（[^）]+）")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_xml)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("<[^>]+>")
                                            showTemplateMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_http_tts_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_http_url_template)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_http_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsHeadersInput,
                                    onValueChange = { 
                                        ttsHeadersInput = it
                                        try {
                                            Json.decodeFromString<Map<String, String>>(it)
                                            ttsJsonError = null
                                        } catch (e: Exception) {
                                            if (it.isNotBlank() && it != "{}") {
                                                ttsJsonError = context.getString(R.string.speech_services_http_headers_error)
                                            } else {
                                                ttsJsonError = null
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.speech_services_http_headers)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_headers_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    isError = ttsJsonError != null
                                )

                                if (ttsJsonError != null) {
                                    Text(
                                        text = ttsJsonError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                                                 Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = ttsHttpMethodInput,
                                        onValueChange = { },
                                        label = { Text(stringResource(R.string.speech_services_http_method)) },
                                        readOnly = true,
                                        modifier = Modifier.weight(1f),
                                        trailingIcon = {
                                            DropdownMenu(
                                                expanded = httpMethodDropdownExpanded,
                                                onDismissRequest = { httpMethodDropdownExpanded = false }
                                            ) {
                                                listOf("GET", "POST").forEach { method ->
                                                    DropdownMenuItem(
                                                        text = { Text(method) },
                                                        onClick = {
                                                            ttsHttpMethodInput = method
                                                            httpMethodDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { httpMethodDropdownExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_http_method_select))
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    OutlinedTextField(
                                        value = ttsContentTypeInput,
                                        onValueChange = { ttsContentTypeInput = it },
                                        label = { Text(stringResource(R.string.speech_services_http_content_type)) },
                                        placeholder = { Text(stringResource(R.string.speech_services_http_content_type_placeholder)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                if (ttsHttpMethodInput == "POST") {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = ttsRequestBodyInput,
                                        onValueChange = { ttsRequestBodyInput = it },
                                        label = { Text(stringResource(R.string.speech_services_http_request_body)) },
                                        placeholder = { Text(stringResource(R.string.speech_services_http_request_body_placeholder)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 模型设置
                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_model_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_model_name)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_model_name_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_siliconflow_model_name_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 音色选择
                                var voiceDropdownExpanded by remember { mutableStateOf(false) }
                                val availableVoices = remember { SiliconFlowVoiceProvider.AVAILABLE_VOICES }
                                val selectedVoiceName = remember(ttsVoiceIdInput) {
                                    availableVoices.find { it.id == ttsVoiceIdInput }?.name ?: context.getString(R.string.speech_services_siliconflow_voice_custom)
                                }

                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_voice_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 预设音色选择下拉菜单
                                ExposedDropdownMenuBox(
                                    expanded = voiceDropdownExpanded,
                                    onExpandedChange = { voiceDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedVoiceName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.speech_services_siliconflow_voice_select)) },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_siliconflow_voice_select_icon))
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = voiceDropdownExpanded,
                                        onDismissRequest = { voiceDropdownExpanded = false }
                                    ) {
                                        availableVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text(voice.name) },
                                                onClick = {
                                                    ttsVoiceIdInput = voice.id
                                                    voiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 自定义音色ID输入框
                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_siliconflow_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // --- STT Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_stt_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.speech_services_stt_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.speech_services_service_type),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var sttDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = sttDropdownExpanded,
                            onExpandedChange = { sttDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(sttServiceTypeInput) {
                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> stringResource(R.string.speech_services_stt_type_sherpa)
                                    SpeechServiceFactory.SpeechServiceType.SHERPA_MNN -> stringResource(R.string.speech_services_stt_type_sherpa_mnn)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.speech_services_stt_engine)) },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_dropdown_expand))
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = sttDropdownExpanded,
                                onDismissRequest = { sttDropdownExpanded = false }
                            ) {
                                SpeechServiceFactory.SpeechServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> stringResource(R.string.speech_services_stt_type_sherpa)
                                                    SpeechServiceFactory.SpeechServiceType.SHERPA_MNN -> stringResource(R.string.speech_services_stt_type_sherpa_mnn)
                                                },
                                                fontWeight = if (sttServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            sttServiceTypeInput = type
                                            sttDropdownExpanded = false
                                        },
                                        // 支持本地识别引擎
                                        enabled = type == SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN || 
                                                 type == SpeechServiceFactory.SpeechServiceType.SHERPA_MNN
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_stt_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_info_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsInfoRow(
                                title = stringResource(R.string.speech_services_info_tts_title),
                                description = stringResource(R.string.speech_services_info_tts_desc)
                            )
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            
                            SettingsInfoRow(
                                title = stringResource(R.string.speech_services_info_stt_title),
                                description = stringResource(R.string.speech_services_info_stt_desc)
                            )
                        }
                    }
                }
                
                // 底部保存按钮区域
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = saveSettings,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) {
                                Text(stringResource(R.string.speech_services_saving))
                            } else {
                                Text(stringResource(R.string.speech_services_save_button))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = onNavigateToTextToSpeech,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.speech_services_test_tts))
                        }
                        
                        // 显示保存消息
                        saveMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (message.contains(context.getString(R.string.speech_services_save_success))) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // 底部空间
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
    }
} 