package com.ai.assistance.operit.ui.features.workflow.components

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Schedule Configuration Dialog
 * 
 * Provides a user-friendly interface for configuring workflow schedules
 */
@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfigDialog(
    initialScheduleType: String = "interval",
    initialConfig: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (scheduleType: String, config: Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var scheduleType by remember { mutableStateOf(initialScheduleType) }
    var scheduleTypeExpanded by remember { mutableStateOf(false) }
    
    // Interval configuration
    var intervalValue by remember { 
        mutableStateOf(initialConfig["interval_ms"]?.toLongOrNull()?.let { it / 60000 }?.toString() ?: "15") 
    }
    var intervalUnit by remember { mutableStateOf("minutes") }
    var intervalUnitExpanded by remember { mutableStateOf(false) }
    
    // Specific time configuration - 使用 Calendar 来管理日期和时间
    val calendar = remember { Calendar.getInstance() }
    
    // 尝试从初始配置解析日期时间
    remember(initialConfig) {
        initialConfig["specific_time"]?.let { dateTimeStr ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.parse(dateTimeStr)?.let { date ->
                    calendar.time = date
                }
            } catch (e: Exception) {
                // 解析失败，使用默认时间
            }
        }
    }
    
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }
    
    // Cron configuration
    var cronExpression by remember { mutableStateOf(initialConfig["cron_expression"] ?: "0 0 * * *") }
    var cronPresetExpanded by remember { mutableStateOf(false) }
    
    // Common settings
    var repeat by remember { mutableStateOf(initialConfig["repeat"]?.toBoolean() ?: true) }
    var enabled by remember { mutableStateOf(initialConfig["enabled"]?.toBoolean() ?: true) }
    
    val scheduleTypes = mapOf(
        "interval" to "固定间隔",
        "specific_time" to "特定时间",
        "cron" to "Cron表达式"
    )
    
    val intervalUnits = mapOf(
        "minutes" to "分钟",
        "hours" to "小时",
        "days" to "天"
    )
    
    val cronPresets = mapOf(
        "0 0 * * *" to "每天午夜",
        "0 9 * * *" to "每天上午9点",
        "0 12 * * *" to "每天中午12点",
        "0 18 * * *" to "每天下午6点",
        "0 */2 * * *" to "每2小时",
        "0 */6 * * *" to "每6小时",
        "*/15 * * * *" to "每15分钟",
        "*/30 * * * *" to "每30分钟",
        "0 0 * * 1" to "每周一午夜",
        "0 9 * * 1-5" to "工作日上午9点"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Schedule type selector
                ExposedDropdownMenuBox(
                    expanded = scheduleTypeExpanded,
                    onExpandedChange = { scheduleTypeExpanded = !scheduleTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = scheduleTypes[scheduleType] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("定时类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = scheduleTypeExpanded,
                        onDismissRequest = { scheduleTypeExpanded = false }
                    ) {
                        scheduleTypes.forEach { (key, value) ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    scheduleType = key
                                    scheduleTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Configuration based on schedule type
                when (scheduleType) {
                    "interval" -> {
                        Text(
                            text = "固定间隔配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = intervalValue,
                                onValueChange = { intervalValue = it },
                                label = { Text("间隔") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = intervalUnitExpanded,
                                onExpandedChange = { intervalUnitExpanded = !intervalUnitExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = intervalUnits[intervalUnit] ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("单位") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalUnitExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = intervalUnitExpanded,
                                    onDismissRequest = { intervalUnitExpanded = false }
                                ) {
                                    intervalUnits.forEach { (key, value) ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                intervalUnit = key
                                                intervalUnitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = "注意：WorkManager 最小间隔为 15 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    "specific_time" -> {
                        Text(
                            text = "特定时间配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // 日期选择器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            selectedYear = year
                                            selectedMonth = month
                                            selectedDay = dayOfMonth
                                        },
                                        selectedYear,
                                        selectedMonth,
                                        selectedDay
                                    ).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("日期") },
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = "选择日期")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 时间选择器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            selectedHour = hourOfDay
                                            selectedMinute = minute
                                        },
                                        selectedHour,
                                        selectedMinute,
                                        true // 24小时制
                                    ).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = String.format("%02d:%02d", selectedHour, selectedMinute),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("时间") },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = "选择时间")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示完整的日期时间
                        Text(
                            text = "执行时间：${String.format("%04d-%02d-%02d %02d:%02d:00", 
                                selectedYear, selectedMonth + 1, selectedDay, selectedHour, selectedMinute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    "cron" -> {
                        Text(
                            text = "Cron 表达式配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = cronPresetExpanded,
                            onExpandedChange = { cronPresetExpanded = !cronPresetExpanded }
                        ) {
                            OutlinedTextField(
                                value = "选择预设模板",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("预设模板") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cronPresetExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = cronPresetExpanded,
                                onDismissRequest = { cronPresetExpanded = false }
                            ) {
                                cronPresets.forEach { (expression, description) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(description)
                                                Text(
                                                    text = expression,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            cronExpression = expression
                                            cronPresetExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = cronExpression,
                            onValueChange = { cronExpression = it },
                            label = { Text("Cron 表达式") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("0 0 * * *") }
                        )
                        
                        Text(
                            text = "格式：分 时 日 月 周\n支持：固定值、*、*/N",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Common settings
                Text(
                    text = "通用设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("重复执行")
                    Switch(
                        checked = repeat,
                        onCheckedChange = { repeat = it }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用定时")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = mutableMapOf<String, String>()
                    config["schedule_type"] = scheduleType
                    config["enabled"] = enabled.toString()
                    config["repeat"] = repeat.toString()
                    
                    when (scheduleType) {
                        "interval" -> {
                            val value = intervalValue.toLongOrNull() ?: 15
                            val multiplier = when (intervalUnit) {
                                "hours" -> 60
                                "days" -> 60 * 24
                                else -> 1 // minutes
                            }
                            config["interval_ms"] = (value * multiplier * 60 * 1000).toString()
                        }
                        "specific_time" -> {
                            // 构造日期时间字符串
                            val dateTimeStr = String.format(
                                "%04d-%02d-%02d %02d:%02d:00",
                                selectedYear,
                                selectedMonth + 1,
                                selectedDay,
                                selectedHour,
                                selectedMinute
                            )
                            config["specific_time"] = dateTimeStr
                        }
                        "cron" -> {
                            if (cronExpression.isNotBlank()) {
                                config["cron_expression"] = cronExpression
                            }
                        }
                    }
                    
                    onConfirm(scheduleType, config)
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

