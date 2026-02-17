/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.ToolRegistration
import com.ai.phoneagent.databinding.ActivityAutomationBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.speech.SherpaSpeechRecognizer
import com.google.android.material.button.MaterialButton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 自动化Activity - 使用新的Agent系统 */
class AutomationActivityNew : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_TOP_ON_ENTRY = "force_top_on_entry"
    }

    private lateinit var binding: ActivityAutomationBinding
    private var agentJob: Job? = null

    @Volatile private var paused: Boolean = false

    private lateinit var tvAccStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var tvLog: TextView
    private lateinit var etTask: EditText
    private lateinit var btnVoiceTask: View
    private lateinit var btnOpenAccessibility: View
    private lateinit var btnRefreshAccessibility: View
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnPauseAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton

    // 执行模式相关
    private lateinit var rgExecutionMode: RadioGroup
    private lateinit var tvModeDescription: TextView
    private lateinit var tvVirtualDisplayStatus: TextView
    private var isBackgroundMode: Boolean = false // true = 后台虚拟屏模式, false = 前端执行模式

    private var sherpaSpeechRecognizer: SherpaSpeechRecognizer? = null
    private var isListening: Boolean = false
    private var micAnimator: ObjectAnimator? = null
    private var voiceInputAnimJob: Job? = null
    private var savedTaskText: String = ""
    private var voicePrefix: String = ""
    private var pendingStartVoice: Boolean = false

    private var virtualDisplayStatusJob: Job? = null

    private var autoScrollLogToBottom: Boolean = true

    // 运行结果保存相关
    private val PREFS_NAME = "automation_results"
    private val KEY_LAST_RESULT_SUCCESS = "last_result_success"
    private val KEY_LAST_RESULT_MESSAGE = "last_result_message"
    private val KEY_LAST_RESULT_STEPS = "last_result_steps"
    private val KEY_LAST_RESULT_TIME = "last_result_time"
    private val KEY_LAST_LOG = "last_log"
    private val apiUseThirdPartyPref = "api_use_third_party"
    private val apiThirdPartyBaseUrlPref = "api_third_party_base_url"
    private val apiThirdPartyModelPref = "api_third_party_model"

    private val stopFromOverlayReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == VirtualScreenPreviewOverlay.ACTION_STOP_AUTOMATION) {
                        appendLog("[虚拟屏] 收到关闭请求，正在停止任务并清理虚拟屏…")
                        handleStopFromOverlay()
                    }
                }
            }

    private val pauseToggleReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == VirtualScreenPreviewOverlay.ACTION_PAUSE_TOGGLE) {
                        togglePause()
                    }
                }
            }

    // 推荐语句滚动相关
    private lateinit var tvRecommendTask: TextView
    private var recommendJob: Job? = null
    private val recommendTasks =
            listOf(
                    "打开大众点评帮我预订一个明天中午11点的周围火锅店的位置，4个人",
                    "打开12306订一张2月5日南京到北京的票，选最便宜的",
                    "打开航旅纵横订一张2月5日从南京飞往成都的机票"
            )
    private var currentRecommendIndex = 0

    private val serviceId by lazy {
        "$packageName/${PhoneAgentAccessibilityService::class.java.name}"
    }

    private val audioPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    if (pendingStartVoice) {
                        pendingStartVoice = false
                        startLocalVoiceInput()
                    }
                } else {
                    pendingStartVoice = false
                    Toast.makeText(this, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAutomationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val forceTopOnEntry = intent?.getBooleanExtra(EXTRA_FORCE_TOP_ON_ENTRY, false) == true
        autoScrollLogToBottom = !forceTopOnEntry

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars =
                    true
        }

        val initialTop = binding.root.paddingTop
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (ime.bottom > sys.bottom) ime.bottom else sys.bottom

            // 将 top inset 应用到 AppBar
            binding.appBar.setPadding(0, sys.top, 0, 0)

            v.setPadding(v.paddingLeft, initialTop, v.paddingRight, initialBottom + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        // 绑定UI组件 - 带null检查和异常处理
        try {
            tvAccStatus =
                    binding.root.findViewById(R.id.tvAccStatus)
                            ?: throw NullPointerException("tvAccStatus not found")
            statusIndicator =
                    binding.root.findViewById(R.id.statusIndicator)
                            ?: throw NullPointerException("statusIndicator not found")
            tvLog =
                    binding.root.findViewById(R.id.tvLog)
                            ?: throw NullPointerException("tvLog not found")
            etTask =
                    binding.root.findViewById(R.id.etTask)
                            ?: throw NullPointerException("etTask not found")
            btnVoiceTask =
                    binding.root.findViewById(R.id.btnVoiceTask)
                            ?: throw NullPointerException("btnVoiceTask not found")
            btnOpenAccessibility =
                    binding.root.findViewById(R.id.btnOpenAccessibility)
                            ?: throw NullPointerException("btnOpenAccessibility not found")
            btnRefreshAccessibility =
                    binding.root.findViewById(R.id.btnRefreshAccessibility)
                            ?: throw NullPointerException("btnRefreshAccessibility not found")
            btnStartAgent =
                    binding.root.findViewById(R.id.btnStartAgent)
                            ?: throw NullPointerException("btnStartAgent not found")
            btnPauseAgent =
                    binding.root.findViewById(R.id.btnPauseAgent)
                            ?: throw NullPointerException("btnPauseAgent not found")
            btnStopAgent =
                    binding.root.findViewById(R.id.btnStopAgent)
                            ?: throw NullPointerException("btnStopAgent not found")
            tvRecommendTask =
                    binding.root.findViewById(R.id.tvRecommendTask)
                            ?: throw NullPointerException("tvRecommendTask not found")

            // 执行模式选择
            rgExecutionMode =
                    binding.root.findViewById(R.id.rgExecutionMode)
                            ?: throw NullPointerException("rgExecutionMode not found")
            tvModeDescription =
                    binding.root.findViewById(R.id.tvModeDescription)
                            ?: throw NullPointerException("tvModeDescription not found")
            tvVirtualDisplayStatus =
                    binding.root.findViewById(R.id.tvVirtualDisplayStatus)
                            ?: throw NullPointerException("tvVirtualDisplayStatus not found")
        } catch (e: NullPointerException) {
            Log.e("AutomationActivityNew", "UI组件初始化失败: ${e.message}", e)
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (forceTopOnEntry) {
            binding.root.post { scrollLogToTop() }
        }

        setupLogAutoScrollBehavior()

        // 初始化虚拟屏状态显示
        // 恢复上次保存的执行模式
        val savedUseVd = VirtualDisplayConfig.getUseVirtualDisplay(this)
        if (savedUseVd) {
            rgExecutionMode.check(R.id.rbBackgroundMode)
            isBackgroundMode = true
            VirtualDisplayController.setShouldUseVirtualDisplay(true)
        } else {
            rgExecutionMode.check(R.id.rbFrontMode)
            isBackgroundMode = false
            VirtualDisplayController.setShouldUseVirtualDisplay(false)
        }
        updateVirtualDisplayStatus()

        rgExecutionMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbFrontMode -> {
                    isBackgroundMode = false
                    VirtualDisplayController.setShouldUseVirtualDisplay(false)
                    VirtualDisplayConfig.setUseVirtualDisplay(this@AutomationActivityNew, false)
                    tvModeDescription.text = "在前台屏幕执行自动化任务，您可以看到操作过程"
                }
                R.id.rbBackgroundMode -> {
                    isBackgroundMode = true
                    VirtualDisplayController.setShouldUseVirtualDisplay(true)
                    VirtualDisplayConfig.setUseVirtualDisplay(this@AutomationActivityNew, true)
                    tvModeDescription.text = "在后台虚拟屏执行自动化任务，不影响前台操作"
                }
            }
            updateVirtualDisplayStatus()
        }

        setupLogCopy()

        // 初始化工具系统
        initializeToolSystem()

        // 推荐语句点击发送
        tvRecommendTask.setOnClickListener {
            vibrateLight()
            val recommendText = recommendTasks[currentRecommendIndex]
            etTask.setText(recommendText)
            Toast.makeText(this, "已填入推荐任务", Toast.LENGTH_SHORT).show()
        }

        // 设置按钮事件
        binding.topAppBar.setNavigationOnClickListener {
            vibrateLight()
            finish()
        }

        btnOpenAccessibility.setOnClickListener {
            vibrateLight()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnRefreshAccessibility.setOnClickListener {
            vibrateLight()
            checkAccessibilityStatus()
        }

        btnVoiceTask.setOnClickListener {
            vibrateLight()
            if (isListening) {
                stopLocalVoiceInput()
            } else {
                ensureAudioPermission { startLocalVoiceInput() }
            }
        }

        btnStartAgent.setOnClickListener {
            vibrateLight()
            startAgent()
        }

        btnPauseAgent.setOnClickListener {
            vibrateLight()
            togglePause()
        }

        btnStopAgent.setOnClickListener {
            vibrateLight()
            stopAgent()
        }

        // 启动推荐语句滚动
        startRecommendTaskRotation()

        btnPauseAgent.isEnabled = false
        btnStopAgent.isEnabled = false

        // 初始检查
        checkAccessibilityStatus()

        // 监听虚拟屏预览窗关闭事件 - 添加异常处理
        try {
            val stopFilter = IntentFilter(VirtualScreenPreviewOverlay.ACTION_STOP_AUTOMATION)
            val pauseFilter = IntentFilter(VirtualScreenPreviewOverlay.ACTION_PAUSE_TOGGLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerReceiver(stopFromOverlayReceiver, stopFilter, Context.RECEIVER_EXPORTED)
                registerReceiver(pauseToggleReceiver, pauseFilter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(stopFromOverlayReceiver, stopFilter)
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(pauseToggleReceiver, pauseFilter)
            }
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "BroadcastReceiver 注册失败: ${e.message}", e)
        }

        initSherpaModel()
    }

    override fun onStop() {
        stopLocalVoiceInput(triggerRecognizerStop = true)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
        // 恢复上一次运行结果
        restoreLastRunResult()
    }

    /** 保存运行结果到本地 */
    private fun saveRunResult(success: Boolean, message: String, steps: Int, logText: String) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_LAST_RESULT_SUCCESS, success)
                putString(KEY_LAST_RESULT_MESSAGE, message)
                putInt(KEY_LAST_RESULT_STEPS, steps)
                putLong(KEY_LAST_RESULT_TIME, System.currentTimeMillis())
                putString(KEY_LAST_LOG, logText)
                apply()
            }
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "保存运行结果失败: ${e.message}", e)
        }
    }

    /** 恢复上一次运行结果 */
    private fun restoreLastRunResult() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val success = prefs.getBoolean(KEY_LAST_RESULT_SUCCESS, false)
            val message = prefs.getString(KEY_LAST_RESULT_MESSAGE, "") ?: ""
            val steps = prefs.getInt(KEY_LAST_RESULT_STEPS, 0)
            val time = prefs.getLong(KEY_LAST_RESULT_TIME, 0L)
            val logText = prefs.getString(KEY_LAST_LOG, "") ?: ""

            // 只显示24小时内的结果
            if (time > 0 && System.currentTimeMillis() - time < 24 * 60 * 60 * 1000) {
                val timeStr =
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(time))
                val statusText = if (success) "✅ 成功" else "❌ 失败"
                appendLog("\n=== 上一次运行结果 ($timeStr) ===")
                appendLog("状态: $statusText")
                appendLog("步数: $steps")
                appendLog("结果: $message")
                if (logText.isNotBlank()) {
                    appendLog("\n--- 上次日志 ---")
                    appendLog(logText.take(2000)) // 限制显示的日志长度
                }
                appendLog("=== 结果已恢复 ===\n")
            }
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "恢复运行结果失败: ${e.message}", e)
        }
    }

    /** 清除保存的运行结果 */
    private fun clearLastRunResult() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "清除运行结果失败: ${e.message}", e)
        }
    }

    /** 初始化工具系统 */
    private fun initializeToolSystem() {
        try {
            val toolHandler = AIToolHandler.getInstance(this)
            ToolRegistration.registerAllTools(toolHandler, this)
            appendLog("✅ 工具系统初始化完成")
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "工具系统初始化失败: ${e.message}", e)
            appendLog("⚠️ 工具系统初始化失败: ${e.message}")
        }
    }

    /** 检查无障碍服务状态 */
    private fun checkAccessibilityStatus() {
        try {
            val enabled = isAccessibilityServiceEnabled()
            val connected = PhoneAgentAccessibilityService.instance != null

            tvAccStatus?.text =
                    when {
                        !enabled -> "服务未开启：请前往设置"
                        !connected -> "服务已开启：正在连接..."
                        else -> "已就绪：无障碍连接正常"
                    }

            statusIndicator?.setBackgroundResource(
                    when {
                        !enabled -> R.drawable.bg_circle_red
                        !connected -> R.drawable.bg_circle_yellow
                        else -> R.drawable.bg_circle_green
                    }
            )

            // 无障碍权限未开启时，显示提示
            if (!enabled) {
                etTask?.hint = "请打开无障碍服务"
                etTask?.isEnabled = false
            } else {
                etTask?.hint = "在此输入或录入您的任务指令..."
                etTask?.isEnabled = true
            }

            btnOpenAccessibility?.visibility = if (enabled) View.GONE else View.VISIBLE
            btnStartAgent?.isEnabled = enabled
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "检查无障碍服务状态失败: ${e.message}", e)
        }
    }

    /** 判断无障碍服务是否启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        var enabled = false
        try {
            val string =
                    Settings.Secure.getString(
                            contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
            if (!string.isNullOrEmpty()) {
                enabled = string.contains(serviceId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return enabled
    }

    /** 启动Agent */
    private fun startAgent() {
        if (agentJob != null) return

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        val svc = PhoneAgentAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "服务已开启但尚未连接，请稍等或返回重进", Toast.LENGTH_SHORT).show()
            return
        }

        val task = etTask.text?.toString().orEmpty().trim()
        if (task.isBlank()) {
            Toast.makeText(this, "请输入任务", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "请先在侧边栏配置 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = resolveApiBaseUrl()
        val model = resolveAutomationModel()

        tvLog.text = ""
        val modeText = if (isBackgroundMode) "后台虚拟屏模式" else "前端执行模式"
        appendLog("执行模式：$modeText")
        appendLog("准备开始：baseUrl=$baseUrl, model=$model")
        appendLog("任务：$task")

        if (AutomationOverlay.canDrawOverlays(this)) {
            val ok =
                    AutomationOverlay.show(
                            context = this,
                            title = "分析中",
                            subtitle = task.take(20),
                            maxSteps = 100,
                            activity = this,
                    )
            if (ok) {
                // 延迟一点让动画播放
                window.decorView.postDelayed({ moveTaskToBack(true) }, 100)
            } else {
                Toast.makeText(this, "悬浮窗显示失败，将保持前台显示日志", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "如需显示进度悬浮窗，请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        }

        btnStartAgent.isEnabled = false
        btnPauseAgent.isEnabled = true
        paused = false
        btnPauseAgent.text = "暂停"
        btnStopAgent.isEnabled = true

        agentJob =
                lifecycleScope.launch {
                    try {
                        val config =
                                AgentConfiguration(useBackgroundVirtualDisplay = isBackgroundMode)

                        // 更新虚拟屏状态显示
                        runOnUiThread {
                            if (isBackgroundMode) {
                                tvVirtualDisplayStatus.text = "虚拟屏状态: 正在创建..."
                            }
                        }

                        val agent = UiAutomationAgent(config)
                        val result =
                                agent.run(
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        model = model,
                                        task = task,
                                        service = svc,
                                        control =
                                                object : UiAutomationAgent.Control {
                                                    override fun isPaused(): Boolean = paused

                                                    override suspend fun confirm(
                                                            message: String
                                                    ): Boolean {
                                                        return suspendCancellableCoroutine { cont ->
                                                            runOnUiThread {
                                                                val dialog =
                                                                        AlertDialog.Builder(
                                                                                        this@AutomationActivityNew
                                                                                )
                                                                                .setTitle("需要确认")
                                                                                .setMessage(message)
                                                                                .setCancelable(
                                                                                        false
                                                                                )
                                                                                .setPositiveButton(
                                                                                        "确认"
                                                                                ) { _, _ ->
                                                                                    if (cont.isActive
                                                                                    )
                                                                                            cont.resume(
                                                                                                    true
                                                                                            )
                                                                                }
                                                                                .setNegativeButton(
                                                                                        "拒绝"
                                                                                ) { _, _ ->
                                                                                    if (cont.isActive
                                                                                    )
                                                                                            cont.resume(
                                                                                                    false
                                                                                            )
                                                                                }
                                                                                .create()
                                                                dialog.show()
                                                                cont.invokeOnCancellation {
                                                                    runCatching { dialog.dismiss() }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                        onLog = { msg ->
                                            appendLog(msg)

                                            // 检测虚拟屏状态并更新 UI
                                            if (isBackgroundMode) {
                                                runOnUiThread {
                                                    when {
                                                        msg.contains("虚拟屏已准备就绪") -> {
                                                            val displayId =
                                                                    VirtualDisplayController
                                                                            .getDisplayId()
                                                            tvVirtualDisplayStatus.text =
                                                                    "虚拟屏状态: 已启动 (displayId=$displayId)"
                                                        }
                                                        msg.contains("虚拟屏准备失败") ||
                                                                msg.contains("虚拟屏模式启动失败") -> {
                                                            tvVirtualDisplayStatus.text =
                                                                    "虚拟屏状态: 创建失败"
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                )
                        appendLog("结束：${result.message}（steps=${result.steps}）")
                        AutomationOverlay.complete(result.message)

                        // 保存运行结果
                        val logText = tvLog.text?.toString() ?: ""
                        saveRunResult(result.success, result.message, result.steps, logText)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            appendLog("已停止")
                            AutomationOverlay.hide()
                        } else {
                            appendLog("异常：${e.message}")
                            AutomationOverlay.complete(e.message.orEmpty().ifBlank { "执行异常" })
                            // 保存异常结果
                            val logText = tvLog.text?.toString() ?: ""
                            saveRunResult(false, e.message ?: "执行异常", 0, logText)
                        }
                    } finally {
                        agentJob = null
                        // 清理虚拟屏预览悬浮窗
                        VirtualScreenPreviewOverlay.hide()
                        virtualDisplayStatusJob?.cancel()
                        virtualDisplayStatusJob = null
                        runOnUiThread {
                            btnStartAgent.isEnabled = true
                            btnPauseAgent.isEnabled = false
                            paused = false
                            btnPauseAgent.text = "暂停"
                            btnStopAgent.isEnabled = false
                            if (isBackgroundMode) {
                                updateVirtualDisplayStatus()
                            }
                        }
                    }
                }

        // 运行期间周期刷新虚拟屏状态
        if (isBackgroundMode) {
            virtualDisplayStatusJob?.cancel()
            virtualDisplayStatusJob =
                    lifecycleScope.launch {
                        while (agentJob != null) {
                            updateVirtualDisplayStatus()
                            delay(1000L)
                        }
                    }
        }
    }

    /** 停止Agent */
    private fun stopAgent() {
        val job = agentJob
        if (job != null) {
            job.cancel()
        }
        agentJob = null
        btnStartAgent.isEnabled = true
        btnPauseAgent.isEnabled = false
        paused = false
        btnPauseAgent.text = "暂停"
        btnStopAgent.isEnabled = false
        appendLog("已请求停止")
        AutomationOverlay.hide()
        // 清理虚拟屏预览
        VirtualScreenPreviewOverlay.hide()
        virtualDisplayStatusJob?.cancel()
        virtualDisplayStatusJob = null
        if (isBackgroundMode ||
                        VirtualDisplayController.shouldUseVirtualDisplay ||
                        VirtualDisplayController.isVirtualDisplayStarted()
        ) {
            VirtualDisplayController.cleanupAsync(this)
            updateVirtualDisplayStatus()
        }
    }

    private fun handleStopFromOverlay() {
        runOnUiThread { stopAgent() }
    }

    private fun togglePause() {
        if (agentJob == null) return
        paused = !paused
        btnPauseAgent.text = if (paused) "继续" else "暂停"
        appendLog(if (paused) "已暂停（等待继续）" else "已继续")
        // 同步暂停状态到虚拟屏预览悬浮窗
        VirtualScreenPreviewOverlay.setPausedState(paused)
    }

    private fun initSherpaModel() {
        lifecycleScope.launch {
            try {
                // 检查Activity是否已销毁
                if (isDestroyed || isFinishing) {
                    return@launch
                }

                sherpaSpeechRecognizer = SherpaSpeechRecognizer(this@AutomationActivityNew)
                val success = sherpaSpeechRecognizer?.initialize() == true
                if (!success) {
                    if (!isDestroyed && !isFinishing) {
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed && !isFinishing) {
                                Toast.makeText(
                                                this@AutomationActivityNew,
                                                "语音模型初始化失败，请重试",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AutomationActivityNew", "语音模型初始化异常: ${e.message}", e)
                if (!isDestroyed && !isFinishing) {
                    try {
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed && !isFinishing) {
                                Toast.makeText(
                                                this@AutomationActivityNew,
                                                "语音模型异常: ${e.message}",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    } catch (toastException: Exception) {
                        Log.e("AutomationActivityNew", "Toast显示失败: ${toastException.message}")
                    }
                }
            }
        }
    }

    private fun ensureAudioPermission(onGranted: () -> Unit) {
        val granted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
        if (granted) {
            pendingStartVoice = false
            onGranted()
        } else {
            pendingStartVoice = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        savedTaskText = etTask.text?.toString().orEmpty()
        voiceInputAnimJob =
                lifecycleScope.launch {
                    var dotCount = 1
                    while (true) {
                        val dots = ".".repeat(dotCount)
                        etTask.setText("正在语音输入$dots")
                        etTask.setSelection(etTask.text?.length ?: 0)
                        dotCount = if (dotCount >= 3) 1 else dotCount + 1
                        delay(400)
                    }
                }
    }

    private fun stopVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        voiceInputAnimJob = null
    }

    private fun startLocalVoiceInput() {
        val recognizer = sherpaSpeechRecognizer
        if (recognizer == null) {
            Toast.makeText(this, "语音模型未初始化，请稍候重试", Toast.LENGTH_SHORT).show()
            initSherpaModel()
            return
        }
        if (!recognizer.isReady()) {
            Toast.makeText(this, "语音模型加载中，请稍候…", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListening) return

        voicePrefix =
                etTask.text?.toString().orEmpty().trim().let { prefix ->
                    if (prefix.isBlank()) "" else if (prefix.endsWith(" ")) prefix else "$prefix "
                }

        startVoiceInputAnimation()

        recognizer.startListening(
                object : SherpaSpeechRecognizer.RecognitionListener {
                    override fun onPartialResult(text: String) {
                        runOnUiThread {
                            stopVoiceInputAnimation()
                            val txt = (voicePrefix + text).trimStart()
                            etTask.setText(txt)
                            etTask.setSelection(etTask.text?.length ?: 0)
                        }
                    }

                    override fun onResult(text: String) {
                        runOnUiThread {
                            stopVoiceInputAnimation()
                            val txt = (voicePrefix + text).trimStart()
                            etTask.setText(txt)
                            etTask.setSelection(etTask.text?.length ?: 0)
                        }
                    }

                    override fun onFinalResult(text: String) {
                        runOnUiThread {
                            stopVoiceInputAnimation()
                            val txt = (voicePrefix + text).trimStart()
                            etTask.setText(if (txt.isBlank()) savedTaskText else txt)
                            etTask.setSelection(etTask.text?.length ?: 0)
                            stopLocalVoiceInput(triggerRecognizerStop = false)
                        }
                    }

                    override fun onError(exception: Exception) {
                        runOnUiThread {
                            stopVoiceInputAnimation()
                            etTask.setText(savedTaskText)
                            Toast.makeText(
                                            this@AutomationActivityNew,
                                            "识别失败: ${exception.message}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                            stopLocalVoiceInput(triggerRecognizerStop = false)
                        }
                    }

                    override fun onTimeout() {
                        runOnUiThread {
                            stopVoiceInputAnimation()
                            etTask.setText(savedTaskText)
                            Toast.makeText(this@AutomationActivityNew, "语音识别超时", Toast.LENGTH_SHORT)
                                    .show()
                            stopLocalVoiceInput(triggerRecognizerStop = false)
                        }
                    }
                }
        )

        isListening = true
        startMicAnimation()
    }

    private fun stopLocalVoiceInput(triggerRecognizerStop: Boolean = true) {
        val recognizer = sherpaSpeechRecognizer
        stopVoiceInputAnimation()

        val currentText = etTask.text?.toString().orEmpty()
        if (currentText.startsWith("正在语音输入")) {
            etTask.setText(savedTaskText)
            etTask.setSelection(etTask.text?.length ?: 0)
        }

        if (triggerRecognizerStop) {
            if (recognizer?.isListening() == true) {
                recognizer.stopListening()
            } else {
                recognizer?.cancel()
            }
        } else {
            recognizer?.cancel()
        }

        isListening = false
        stopMicAnimation()
    }

    private fun startMicAnimation() {
        if (micAnimator != null) return

        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f)
        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f)
        val a = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.75f)

        micAnimator =
                ObjectAnimator.ofPropertyValuesHolder(btnVoiceTask, sx, sy, a).apply {
                    duration = 520
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = LinearInterpolator()
                    start()
                }
    }

    private fun stopMicAnimation() {
        micAnimator?.cancel()
        micAnimator = null
        btnVoiceTask.scaleX = 1f
        btnVoiceTask.scaleY = 1f
        btnVoiceTask.alpha = 1f
    }

    /** 获取API Key */
    private fun getApiKey(): String {
        // 从SharedPreferences读取
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isNotBlank()) return key
        return prefs.getString("autoglm_api_key", "") ?: ""
    }

    private fun resolveApiBaseUrl(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val useThirdParty = prefs.getBoolean(apiUseThirdPartyPref, false)
        if (!useThirdParty) return AutoGlmClient.DEFAULT_BASE_URL
        val rawUrl = prefs.getString(apiThirdPartyBaseUrlPref, "")?.trim().orEmpty()
        return rawUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    private fun resolveAutomationModel(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val useThirdParty = prefs.getBoolean(apiUseThirdPartyPref, false)
        if (!useThirdParty) return AutoGlmClient.PHONE_MODEL

        val rawModel = prefs.getString(apiThirdPartyModelPref, "")?.trim().orEmpty()
        return rawModel.ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    /** 添加日志 */
    private fun appendLog(message: String) {
        runOnUiThread {
            tvLog.append("$message\n")
            AutomationOverlay.updateFromLogLine(message)

            if (autoScrollLogToBottom) {
                // 自动滚动到底部
                val scrollView = binding.root.findViewById<NestedScrollView>(R.id.scrollLog)
                scrollView?.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
    }

    private fun scrollLogToTop() {
        val scrollView = binding.root.findViewById<NestedScrollView>(R.id.scrollLog)
        scrollView?.post { scrollView.fullScroll(android.view.View.FOCUS_UP) }
    }

    private fun setupLogAutoScrollBehavior() {
        val scrollView = binding.root.findViewById<NestedScrollView>(R.id.scrollLog) ?: return
        autoScrollLogToBottom = false
        scrollView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
            val view = v as NestedScrollView
            val isAtBottom = !view.canScrollVertically(1)
            val isScrollingDown = scrollY > oldScrollY
            autoScrollLogToBottom = isAtBottom && isScrollingDown
        }
    }

    private fun updateVirtualDisplayStatus() {
        if (!::tvVirtualDisplayStatus.isInitialized) return

        try {
            val isStarted = VirtualDisplayController.isVirtualDisplayStarted()
            val displayId = VirtualDisplayController.getDisplayId()
            val shouldUse = VirtualDisplayController.shouldUseVirtualDisplay

            val statusText = buildString {
                append("虚拟屏: ")
                if (isStarted && displayId != null) {
                    val configSummary = VirtualDisplayConfig.summary(this@AutomationActivityNew)
                    append("✅ 运行中 | ID=$displayId | $configSummary")
                } else if (shouldUse) {
                    append("⏳ 准备中...")
                } else {
                    append("⏸ 未启动")
                }
            }
            tvVirtualDisplayStatus.text = statusText
        } catch (e: Exception) {
            tvVirtualDisplayStatus.text = "虚拟屏: ❌ 错误 - ${e.message}"
        }
    }

    private fun setupLogCopy() {
        tvLog.isClickable = true
        tvLog.isLongClickable = true
        tvLog.setOnLongClickListener {
            val text = tvLog.text?.toString().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "暂无可复制的日志", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Automation Log", text))
            tvLog.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            playLogCopyAnim(tvLog)
            Toast.makeText(this, "日志已复制（长按可再次复制）", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun playLogCopyAnim(target: TextView) {
        target.animate().cancel()
        target.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(90L)
                .withEndAction {
                    target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(220L)
                            .setInterpolator(OvershootInterpolator(1.4f))
                            .start()
                }
                .start()
    }

    /** 轻微振动 */
    private fun vibrateLight() {
        val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                            getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(30)
        }
    }

    /** 启动推荐任务滚动播放 */
    private fun startRecommendTaskRotation() {
        if (recommendTasks.isEmpty()) return

        try {
            // 初始显示第一条
            currentRecommendIndex = 0
            tvRecommendTask.text = recommendTasks[currentRecommendIndex]

            // 启动协程，每4秒切换
            recommendJob?.cancel()
            recommendJob =
                    lifecycleScope.launch {
                        delay(4000) // 第一条显示4秒
                        while (true) {
                            // 检查Activity是否已销毁
                            if (isDestroyed) {
                                break
                            }

                            currentRecommendIndex =
                                    (currentRecommendIndex + 1) % recommendTasks.size
                            val nextText = recommendTasks[currentRecommendIndex]

                            // 简单淡出淡入效果 - 添加null检查和安全防护
                            try {
                                tvRecommendTask
                                        .animate()
                                        .alpha(0.3f)
                                        .setDuration(200)
                                        .withEndAction {
                                            // 再次检查Activity状态
                                            if (!isDestroyed && !isFinishing) {
                                                tvRecommendTask.text = nextText
                                                tvRecommendTask
                                                        .animate()
                                                        .alpha(0.65f)
                                                        .setDuration(200)
                                                        .start()
                                            }
                                        }
                                        .start()
                            } catch (e: Exception) {
                                Log.d("AutomationActivityNew", "推荐任务动画错误: ${e.message}")
                            }

                            delay(4000)
                        }
                    }
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "推荐任务轮换启动失败: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopFromOverlayReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d("AutomationActivityNew", "BroadcastReceiver 未注册或已注销: ${e.message}")
        } catch (e: Exception) {
            Log.e("AutomationActivityNew", "注销 BroadcastReceiver 失败: ${e.message}", e)
        }
        try {
            unregisterReceiver(pauseToggleReceiver)
        } catch (_: Exception) {}
        recommendJob?.cancel()
        recommendJob = null
        stopLocalVoiceInput(triggerRecognizerStop = true)
        sherpaSpeechRecognizer?.shutdown()
        sherpaSpeechRecognizer = null
        stopAgent()
    }
}
