package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日志查看器ViewModel - 使用AppLogger文件
 */
class LogcatViewModel(private val context: Context) : ViewModel() {
    private val logcatManager = LogcatManager(context)


    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()



    fun clearLogs() {
        logcatManager.clearLogs()
    }

    fun saveLogsToFile() {
        if (_isSaving.value) return

        _isSaving.value = true
        _saveResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "operit_log_$timestamp.txt"

                val logsToSave = logcatManager.loadInitialLogs()

                if (logsToSave.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _saveResult.value = "没有日志可保存"
                        delay(3000)
                        _saveResult.value = null
                    }
                    _isSaving.value = false
                    return@launch
                }

                val logContent = StringBuilder()
                logContent.append("=== Operit 日志 ===\n")
                logContent.append("日期: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                logContent.append("总条数: ${logsToSave.size}\n")
                logContent.append("===================================\n\n")

                logsToSave.forEach { record ->
                    val recordTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(record.timestamp))
                    val tag = record.tag ?: ""
                    val level = record.level.symbol
                    logContent.append("$recordTimestamp $level/$tag: ${record.message}\n")
                }

                val filePath = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        saveUsingMediaStore(fileName, logContent.toString())
                    } else {
                        saveUsingFileSystem(fileName, logContent.toString())
                    }
                } catch (e: Exception) {
                    "保存失败：${e.message ?: "未知错误"}"
                }

                withContext(Dispatchers.Main) {
                    if (filePath.startsWith("保存失败")) {
                        _saveResult.value = filePath
                    } else {
                        _saveResult.value = "日志已保存至：$filePath"
                    }
                    delay(3000)
                    _saveResult.value = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _saveResult.value = "保存失败: ${e.message ?: "未知错误"}"
                    delay(3000)
                    _saveResult.value = null
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun saveUsingMediaStore(fileName: String, content: String): String {
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/operit")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                return "保存失败：无法创建文件，可能是存储权限问题"
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: throw Exception("无法打开输出流")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "${downloadsDir.absolutePath}/operit/$fileName"
        } catch (e: Exception) {
            throw Exception("MediaStore保存失败：${e.message}")
        }
    }

    private fun saveUsingFileSystem(fileName: String, content: String): String {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null || !downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw Exception("无法创建下载目录")
            }
            val operitDir = File(downloadsDir, "operit")
            if (!operitDir.exists() && !operitDir.mkdirs()) {
                throw Exception("无法创建operit目录")
            }
            val file = File(operitDir, fileName)
            FileWriter(file).use { it.write(content) }
            if (!file.exists() || file.length() == 0L) {
                throw Exception("文件创建失败或为空")
            }
            return file.absolutePath
        } catch (e: Exception) {
            throw Exception("文件系统保存失败：${e.message}")
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogcatViewModel::class.java)) {
                return LogcatViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No-op, no more monitoring to stop
    }
}
