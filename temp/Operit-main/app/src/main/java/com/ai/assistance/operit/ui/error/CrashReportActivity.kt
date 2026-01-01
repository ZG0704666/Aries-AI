package com.ai.assistance.operit.ui.error

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.ui.theme.OperitTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available."

        AppLogger.e("CrashReportActivity", "stackTrace: $stackTrace")
        setContent { OperitTheme { CrashReportScreen(stackTrace = stackTrace) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportScreen(stackTrace: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(id = R.string.title_activity_crash_report)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(id = R.string.crash_report_header),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // 主要操作按钮
                    Button(
                        onClick = { restartApp(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.crash_report_restart_button))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 次要操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, stackTrace)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(id = R.string.crash_report_copy_button))
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    exportToFile(context, stackTrace)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(id = R.string.crash_report_export_button))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        stringResource(id = R.string.crash_report_details_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = stackTrace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("error_stack_trace", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.crash_report_copy_success, Toast.LENGTH_SHORT).show()
}

private fun exportToFile(context: Context, text: String) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val errorDir = File(downloadsDir, "Operit/error")
        if (!errorDir.exists()) {
            errorDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(errorDir, "error-report-$timestamp.log")

        FileOutputStream(file).use {
            it.write(text.toByteArray())
        }
        val successMsg = context.getString(R.string.crash_report_export_success, file.absolutePath)
        Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        val errorMsg = context.getString(R.string.crash_report_export_failed, e.localizedMessage)
        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}

private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    kotlin.system.exitProcess(0)
}
