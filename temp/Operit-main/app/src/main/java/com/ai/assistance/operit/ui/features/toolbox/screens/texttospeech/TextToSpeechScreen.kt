package com.ai.assistance.operit.ui.features.toolbox.screens.texttospeech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.api.voice.VoiceService
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.ai.assistance.operit.api.voice.TtsException

/** 文本转语音演示屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechScreen(navController: NavController) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        // 获取VoiceService实例
        val voiceService = remember { VoiceServiceFactory.getInstance(context) }
        
        // 状态变量
        var inputText by remember { mutableStateOf("") }
        var speechRate by remember { mutableStateOf(1.0f) }
        var speechPitch by remember { mutableStateOf(1.0f) }
        var isInitialized by remember { mutableStateOf(false) }
        var isSpeaking by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var errorDetails by remember { mutableStateOf<String?>(null) }
        var debugInfo by remember { mutableStateOf<String?>(null) }

        // 监听语音服务状态
        LaunchedEffect(Unit) {
                // 初始化语音服务
                coroutineScope.launch {
                        try {
                                isInitialized = voiceService.initialize()
                                if (!isInitialized) {
                                        error = "初始化语音引擎失败"
                                        errorDetails = "服务初始化方法返回 false，但未抛出异常。"
                                }
                        } catch (e: Exception) {
                                error = "初始化语音引擎错误"
                                errorDetails = handleTtsError(e)
                                debugInfo = "服务类型: ${voiceService.javaClass.simpleName}"
                        }
                }

                // 监听发言状态
                voiceService.speakingStateFlow.collect { speaking -> isSpeaking = speaking }
        }

        // 播放文本
        fun speakText() {
                if (inputText.isBlank()) {
                        error = "请输入要转换为语音的文本"
                        errorDetails = null
                        debugInfo = null
                        return
                }

                // 清除之前的错误信息
                error = null
                errorDetails = null
                debugInfo = null

                coroutineScope.launch {
                        try {
                                val success =
                                        voiceService.speak(inputText, true, speechRate, speechPitch)
                                if (!success) {
                                        error = "播放文本失败"
                                        errorDetails = "TTS 服务返回失败状态，请检查配置和网络连接"
                                        debugInfo = "请求参数: 文本='$inputText', 语速=${speechRate}x, 音调=${speechPitch}x"
                                }
                        } catch (e: Exception) {
                                error = "播放文本错误: ${e.message}"
                                errorDetails = handleTtsError(e)
                                debugInfo = "请求参数: 文本='$inputText', 语速=${speechRate}x, 音调=${speechPitch}x\n" +
                                          "服务类型: ${voiceService.javaClass.simpleName}"
                        }
                }
        }

        // 停止播放
        fun stopSpeaking() {
                coroutineScope.launch {
                        try {
                                voiceService.stop()
                        } catch (e: Exception) {
                                error = "停止播放错误: ${e.message}"
                        }
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(scrollState)
                                .padding(16.dp)
        ) {
                // 标题
                Text(
                        text = "文本转语音演示",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                // 文本输入卡片
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = "输入文本",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                        placeholder = { Text("请输入要转换为语音的文本") },
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor =
                                                                MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor =
                                                                MaterialTheme.colorScheme.outline
                                                ),
                                        maxLines = 5
                                )
                        }
                }

                // 语音设置卡片
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = "语音设置",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 语速调节
                                Text(
                                        text = "语速: ${speechRate}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Slider(
                                        value = speechRate,
                                        onValueChange = { speechRate = it },
                                        valueRange = 0.5f..2.0f,
                                        steps = 5,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 音调调节
                                Text(
                                        text = "音调: ${speechPitch}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Slider(
                                        value = speechPitch,
                                        onValueChange = { speechPitch = it },
                                        valueRange = 0.5f..2.0f,
                                        steps = 5,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                        }
                }

                // 操作按钮
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Button(
                                onClick = { speakText() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = isInitialized && !isSpeaking && inputText.isNotBlank(),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("播放语音", style = MaterialTheme.typography.titleMedium)
                        }

                        Button(
                                onClick = { stopSpeaking() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = isSpeaking,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "停止",
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("停止播放", style = MaterialTheme.typography.titleMedium)
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 状态指示器
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.secondaryContainer
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isInitialized) Icons.Default.CheckCircle
                                                        else Icons.Default.Error,
                                                contentDescription = null,
                                                tint =
                                                        if (isInitialized) Color(0xFF4CAF50)
                                                        else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                                text =
                                                        if (isInitialized) "语音引擎已初始化"
                                                        else "语音引擎未初始化",
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp
                                                        else Icons.AutoMirrored.Filled.VolumeOff,
                                                contentDescription = null,
                                                tint =
                                                        if (isSpeaking) Color(0xFF2196F3)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer
                                        )
                                        Text(
                                                text = if (isSpeaking) "正在播放中..." else "未播放",
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }
                }

                // 错误提示
                if (error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Error,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                        text = "错误",
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                                text = error ?: "",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                        
                                        // 错误详情
                                        if (errorDetails != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                        text = "错误详情:",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = errorDetails ?: "",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                        
                                        // 调试信息
                                        if (debugInfo != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                        text = "调试信息:",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = debugInfo ?: "",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                        
                                        // 清除错误按钮
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                OutlinedButton(
                                                        onClick = {
                                                                error = null
                                                                errorDetails = null
                                                                debugInfo = null
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                                contentColor = MaterialTheme.colorScheme.error
                                                        )
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Clear,
                                                                contentDescription = "清除错误",
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("清除错误信息")
                                                }
                                        }
                                }
                        }
                }

                // 使用说明
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                )
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "使用说明",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text =
                                                "1. 在输入框中输入要转换为语音的文本\n" +
                                                        "2. 调整语速和音调设置\n" +
                                                        "3. 点击「播放语音」按钮开始播放\n" +
                                                        "4. 点击「停止播放」按钮停止播放\n" +
                                                        "5. 如果出现错误，查看详细的错误信息和调试信息\n" +
                                                        "6. 使用「清除错误信息」按钮清除错误显示",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = "注意：首次使用时需要在系统设置中启用无障碍服务",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                )
                        }
                }
        }
}

/**
 * 处理TTS异常并返回格式化的错误详情
 *
 * @param e 捕获到的异常
 * @return 格式化后的错误详情字符串
 */
private fun handleTtsError(e: Exception): String {
    return when (e) {
        is TtsException -> {
            // TTS 异常，优先显示服务器返回的具体信息
            val code = e.httpStatusCode
            val body = e.errorBody?.takeIf { it.isNotBlank() }
            if (code != null && body != null) {
                "TTS 服务错误 (HTTP $code): $body"
            } else if (code != null) {
                "TTS 服务返回错误，状态码: $code"
            } else if (body != null) {
                "TTS 服务返回错误: $body"
            } else {
                "TTS 服务发生未知异常: ${e.cause?.message ?: e.message}"
            }
        }
        is UnknownHostException -> "网络错误: 无法访问主机，请检查网络连接和DNS设置。"
        is SocketTimeoutException -> "网络超时: 服务器响应超时，请检查网络状况。"
        is ConnectException -> "网络错误: 无法连接到服务器，请检查服务地址和端口。"
        is ProtocolException -> "网络协议错误: ${e.message}"
        is IOException -> "网络IO错误，请检查设备网络连接。"
        else -> "发生未知错误: ${e.javaClass.simpleName}\n${e.stackTraceToString().take(300)}..."
    }
}
