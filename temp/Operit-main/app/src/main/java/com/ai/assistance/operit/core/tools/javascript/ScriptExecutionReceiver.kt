package com.ai.assistance.operit.core.tools.javascript

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 接收通过ADB发送的广播命令，用于执行JavaScript文件 */
class ScriptExecutionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScriptExecutionReceiver"
        const val ACTION_EXECUTE_JS = "com.ai.assistance.operit.EXECUTE_JS"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FUNCTION_NAME = "function_name"
        const val EXTRA_PARAMS = "params"
        const val EXTRA_TEMP_FILE = "temp_file"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_EXECUTE_JS) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            val functionName = intent.getStringExtra(EXTRA_FUNCTION_NAME)
            val paramsJson = intent.getStringExtra(EXTRA_PARAMS) ?: "{}"
            val isTempFile = intent.getBooleanExtra(EXTRA_TEMP_FILE, false)

            if (filePath == null || functionName == null) {
                AppLogger.e(
                        TAG,
                        "Missing required parameters: filePath=$filePath, functionName=$functionName"
                )
                return
            }

            AppLogger.d(TAG, "Received request to execute JS file: $filePath, function: $functionName")
            executeJavaScript(context, filePath, functionName, paramsJson, isTempFile)
        }
    }

    private fun executeJavaScript(
            context: Context,
            filePath: String,
            functionName: String,
            paramsJson: String,
            isTempFile: Boolean
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    AppLogger.e(TAG, "JavaScript file not found: $filePath")
                    return@launch
                }

                // 读取文件内容
                val scriptContent = file.readText()
                AppLogger.d(TAG, "Loaded JavaScript file, size: ${scriptContent.length} bytes")

                // 获取JsEngine实例
                val aiToolHandler = AIToolHandler.getInstance(context)
                val packageManager = PackageManager.getInstance(context, aiToolHandler)
                val jsEngine = JsEngine(context)

                // 解析参数
                val params =
                        try {
                            val jsonObject = JSONObject(paramsJson)
                            val paramsMap = mutableMapOf<String, String>()
                            jsonObject.keys().forEach { key ->
                                paramsMap[key] = jsonObject.opt(key)?.toString() ?: ""
                            }
                            paramsMap
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error parsing params: $paramsJson", e)
                            mapOf<String, String>()
                        }

                // 执行JavaScript
                val result = jsEngine.executeScriptFunction(scriptContent, functionName, params)

                AppLogger.d(TAG, "JavaScript execution result: $result")

                // 如果是临时文件，执行完成后删除
                if (isTempFile) {
                    try {
                        file.delete()
                        AppLogger.d(TAG, "Deleted temporary file: $filePath")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error deleting temporary file: $filePath", e)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error executing JavaScript: ${e.message}", e)
            }
        }
    }
}
