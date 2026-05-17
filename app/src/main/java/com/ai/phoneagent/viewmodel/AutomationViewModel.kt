package com.ai.phoneagent.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.AutomationLiveNotification
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.MainActivity
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.R
import com.ai.phoneagent.ShizukuBridge
import com.ai.phoneagent.UiAutomationAgent
import com.ai.phoneagent.VirtualDisplayConfig
import com.ai.phoneagent.VirtualDisplayController
import com.ai.phoneagent.VirtualScreenPreviewOverlay
import com.ai.phoneagent.core.automation.AutomationInstructionRequest
import com.ai.phoneagent.core.automation.AutomationLogBridge
import com.ai.phoneagent.core.config.AgentConfiguration
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.ToolRegistration
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.AutomationResultsRepository
import com.ai.phoneagent.net.AriesApiClient
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.speech.SherpaSpeechRecognizer
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class AutomationViewModel(
    application: Application,
    private val appPrefsRepository: AppPreferencesRepository,
    private val automationResultsRepository: AutomationResultsRepository,
) : AndroidViewModel(application) {

    companion object {
        const val EXTRA_FORCE_TOP_ON_ENTRY = "force_top_on_entry"
        const val EXTRA_AUTOMATION_TASK = "automation_task"
        const val EXTRA_AUTOMATION_SOURCE = "automation_source"
        const val EXTRA_AUTOMATION_AUTO_START = "automation_auto_start"
        const val EXTRA_KEEP_MAIN_ON_TOP = "keep_main_on_top"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 2026

        /**
         * Pending launch args set by MainActivity when receiving automation intents.
         * AutomationScreen consumes these on composition.
         */
        @Volatile
        var pendingLaunchArgs: LaunchArgs? = null

        fun extractLaunchArgsFromIntent(intent: Intent?): LaunchArgs? {
            intent ?: return null
            val task = intent.getStringExtra(EXTRA_AUTOMATION_TASK)
            val forceTop = intent.getBooleanExtra(EXTRA_FORCE_TOP_ON_ENTRY, false)
            if (task.isNullOrBlank() && !forceTop) return null
            return LaunchArgs(
                forceTopOnEntry = forceTop,
                automationTask = task,
                automationSource = intent.getStringExtra(EXTRA_AUTOMATION_SOURCE),
                automationAutoStart = intent.getBooleanExtra(EXTRA_AUTOMATION_AUTO_START, false),
                keepMainOnTop = intent.getBooleanExtra(EXTRA_KEEP_MAIN_ON_TOP, false),
            )
        }
    }

    data class RuntimeConnectionState(
        val accessibilityEnabled: Boolean,
        val accessibilityConnected: Boolean,
        val shizukuBinderConnected: Boolean,
        val shizukuPermissionGranted: Boolean,
    ) {
        val shizukuReady: Boolean
            get() = shizukuBinderConnected && shizukuPermissionGranted
    }

    data class LaunchArgs(
        val forceTopOnEntry: Boolean = false,
        val automationTask: String? = null,
        val automationSource: String? = null,
        val automationAutoStart: Boolean = false,
        val keepMainOnTop: Boolean = false,
        val popBackImmediately: Boolean = false,
    )

    private val appContext: Context = application.applicationContext
    private var hostActivityRef: WeakReference<Activity>? = null

    private var agentJob: Job? = null
    private var paused: Boolean = false

    var isBackgroundMode by mutableStateOf(false)
        private set

    private var sherpaSpeechRecognizer: SherpaSpeechRecognizer? = null
    private var isListeningInternal: Boolean = false
    private var voiceInputAnimJob: Job? = null
    private var savedTaskText: String = ""
    private var voicePrefix: String = ""
    private var pendingStartVoice: Boolean = false
    private var sherpaInitializing: Boolean = false

    private var virtualDisplayStatusJob: Job? = null
    private var accessibilityStatusSyncJob: Job? = null

    private var mirrorLogsToMain: Boolean = false
    private var overlayClickReturnToMain: Boolean = false
    private var lastDispatchedTask: String? = null

    private var composeCanStart: Boolean = false
    private var composeHasPartialConnection: Boolean = false

    var statusText by mutableStateOf("")
        private set
    var statusSummary by mutableStateOf("")
        private set
    var interactionModeText by mutableStateOf("")
        private set
    var accessibilityStatusText by mutableStateOf("")
        private set
    var shizukuStatusText by mutableStateOf("")
        private set
    var taskText by mutableStateOf("")
        private set
    var taskHint by mutableStateOf("")
        private set
    var recommendText by mutableStateOf("")
        private set
    var logText by mutableStateOf("")
        private set
    var modeDescription by mutableStateOf("")
        private set
    var virtualDisplayStatus by mutableStateOf("")
        private set
    var useShizukuInteraction by mutableStateOf(false)
        private set
    var autoApprove by mutableStateOf(false)
        private set
    var showShizukuControls by mutableStateOf(false)
        private set
    var showShizukuAuthorize by mutableStateOf(false)
        private set
    var startButtonText by mutableStateOf("")
        private set
    var startButtonEnabled by mutableStateOf(false)
        private set
    var startButtonTerminateStyle by mutableStateOf(false)
        private set
    var pauseButtonText by mutableStateOf("")
        private set
    var pauseButtonEnabled by mutableStateOf(false)
        private set
    var stopButtonEnabled by mutableStateOf(false)
        private set
    var isListening by mutableStateOf(false)
        private set

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

    private var recommendJob: Job? = null
    private val recommendTasks by lazy {
        appContext.resources.getStringArray(R.array.automation_recommend_tasks).toList()
    }
    private var currentRecommendIndex = 0

    private val serviceId by lazy {
        "${appContext.packageName}/${PhoneAgentAccessibilityService::class.java.name}"
    }

    init {
        val savedUseVd = VirtualDisplayConfig.getUseVirtualDisplay(appContext)
        setExecutionMode(savedUseVd)
        setUseShizukuInteraction(
            checked = VirtualDisplayConfig.getUseShizukuInteraction(appContext),
            userInitiated = false,
        )
        applyAutoApprove(checked = VirtualDisplayConfig.getAutoApproveAutomation(appContext))
        updateComposeControlState(canStart = false)

        viewModelScope.launch(Dispatchers.Default) {
            initializeToolSystem()
        }
        startRecommendTaskRotation()
        checkAccessibilityStatus()
        initSherpaModel()
        registerOverlayReceivers()
    }

    fun attachHostActivity(activity: Activity?) {
        hostActivityRef = activity?.let { WeakReference(it) }
    }

    fun onResume() {
        scheduleAccessibilityStatusSync()
        restoreLastRunResult()
    }

    fun onStop() {
        accessibilityStatusSyncJob?.cancel()
        accessibilityStatusSyncJob = null
        stopLocalVoiceInput(triggerRecognizerStop = true)
    }

    fun onTaskChange(value: String) {
        taskText = value
    }

    fun onUseRecommendTask() {
        taskText = recommendText
    }

    fun onExecutionModeChange(backgroundMode: Boolean) {
        if (backgroundMode && !ensureBackgroundModeReady()) {
            checkAccessibilityStatus()
            return
        }
        setExecutionMode(backgroundMode)
        checkAccessibilityStatus()
    }

    fun onShizukuModeChange(checked: Boolean) {
        setUseShizukuInteraction(checked)
    }

    fun onAutoApproveChange(checked: Boolean) {
        applyAutoApprove(checked)
    }

    fun onRefreshStatus() {
        checkAccessibilityStatus()
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (granted) {
            if (pendingStartVoice) {
                pendingStartVoice = false
                startLocalVoiceInput()
            }
        } else {
            pendingStartVoice = false
            Toast.makeText(appContext, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
        }
    }

    fun onVoiceTaskClick(hasAudioPermission: Boolean): Boolean {
        if (isListeningInternal) {
            stopLocalVoiceInput()
            return false
        }
        if (hasAudioPermission) {
            pendingStartVoice = false
            startLocalVoiceInput()
            return false
        }
        pendingStartVoice = true
        return true
    }

    fun onStartOrTerminateClick() {
        if (agentJob != null) {
            stopAgent()
        } else {
            startAgent()
        }
    }

    fun onPauseClick() {
        togglePause()
    }

    fun onStopClick() {
        stopAgent()
    }

    fun copyLog() {
        val text = logText.trim()
        if (text.isBlank()) {
            Toast.makeText(appContext, "暂无可复制的日志", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Automation Log", text))
        Toast.makeText(appContext, "日志已复制", Toast.LENGTH_SHORT).show()
    }

    fun consumeLaunchArgs(args: LaunchArgs) {
        val task = args.automationTask?.trim().orEmpty()
        if (task.isBlank()) return

        val source = args.automationSource?.trim().orEmpty()
        val autoStart = args.automationAutoStart
        val keepMainOnTop = args.keepMainOnTop

        mirrorLogsToMain =
            source == AutomationInstructionRequest.Source.MANUAL_AGENT_MODE.wireValue ||
                source == AutomationInstructionRequest.Source.ADVANCED_AI.wireValue
        overlayClickReturnToMain = mirrorLogsToMain
        lastDispatchedTask = task

        taskText = task

        if (source.isNotBlank()) {
            appendLog("接收任务来源：$source")
        }
        appendLog("接收任务：$task")

        if (!autoStart) return

        if (agentJob != null) {
            appendLog("当前自动化任务仍在执行，暂不自动启动新任务")
            Toast.makeText(appContext, "当前有任务在执行，请先停止再重试", Toast.LENGTH_SHORT).show()
            return
        }

        if (keepMainOnTop) {
            bringMainActivityToFront()
        }
        startAgent()
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun checkAccessibilityStatus() {
        try {
            val state = collectRuntimeConnectionState()
            val shouldShowShizuku = state.shizukuBinderConnected
            val effectiveUseShizuku = useShizukuInteraction && shouldShowShizuku
            val canStart = resolveRuntimeInteractionPreference(effectiveUseShizuku, state) != null
            composeCanStart = canStart
            composeHasPartialConnection = state.accessibilityConnected || state.shizukuReady

            taskHint =
                if (canStart) {
                    "在此输入或录入您的任务指令..."
                } else {
                    "请先满足当前模式的连接条件后再开始"
                }
            showShizukuControls = shouldShowShizuku
            showShizukuAuthorize = shouldShowShizuku
            updateComposeControlState(canStart)
            updateComposeStatusSnapshot(effectiveUseShizuku, state, canStart)
        } catch (e: Exception) {
            Log.e("AutomationViewModel", "检查无障碍服务状态失败: ${e.message}", e)
        }
    }

    fun statusTone(): com.ai.phoneagent.ui.automation.AutomationStatusTone {
        return when {
            composeCanStart -> com.ai.phoneagent.ui.automation.AutomationStatusTone.Ready
            composeHasPartialConnection -> com.ai.phoneagent.ui.automation.AutomationStatusTone.Partial
            else -> com.ai.phoneagent.ui.automation.AutomationStatusTone.Inactive
        }
    }

    fun authorizeShizukuAndAccessibility() {
        val state = collectRuntimeConnectionState()
        if (!state.shizukuBinderConnected) {
            Toast.makeText(appContext, R.string.automation_shizuku_not_connected, Toast.LENGTH_SHORT).show()
            checkAccessibilityStatus()
            return
        }
        if (!state.shizukuPermissionGranted) {
            ensureShizukuPermissionGranted()
            Toast.makeText(appContext, R.string.automation_shizuku_permission_requested, Toast.LENGTH_SHORT)
                .show()
            checkAccessibilityStatus()
            return
        }
        val granted = grantAccessibilityViaShizuku()
        if (granted) {
            Toast.makeText(appContext, R.string.automation_shizuku_authorize_success, Toast.LENGTH_SHORT).show()
            checkAccessibilityStatus()
            refreshStatusAfterOneTapAuthorize()
            return
        }
        Toast.makeText(appContext, R.string.automation_shizuku_authorize_failed, Toast.LENGTH_SHORT).show()
        checkAccessibilityStatus()
    }

    private fun registerOverlayReceivers() {
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                stopFromOverlayReceiver,
                IntentFilter(VirtualScreenPreviewOverlay.ACTION_STOP_AUTOMATION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            ContextCompat.registerReceiver(
                appContext,
                pauseToggleReceiver,
                IntentFilter(VirtualScreenPreviewOverlay.ACTION_PAUSE_TOGGLE),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure {
            Log.e("AutomationViewModel", "BroadcastReceiver 注册失败: ${it.message}", it)
        }
    }

    private fun updateComposeControlState(canStart: Boolean) {
        val running = agentJob != null
        startButtonText =
            if (running) {
                appContext.getString(R.string.automation_terminate)
            } else {
                appContext.getString(R.string.automation_start_now)
            }
        startButtonEnabled = if (running) true else canStart
        startButtonTerminateStyle = running
        pauseButtonText =
            if (paused) {
                appContext.getString(R.string.automation_resume)
            } else {
                appContext.getString(R.string.automation_pause)
            }
        pauseButtonEnabled = running
        stopButtonEnabled = running
    }

    private fun ensureBackgroundModeReady(): Boolean {
        val state = collectRuntimeConnectionState()
        if (!state.shizukuBinderConnected) {
            Toast.makeText(appContext, R.string.automation_background_requires_shizuku, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!state.shizukuPermissionGranted) {
            ensureShizukuPermissionGranted()
            Toast.makeText(appContext, R.string.automation_background_requires_shizuku_permission, Toast.LENGTH_SHORT)
                .show()
            return false
        }
        return true
    }

    private fun setExecutionMode(backgroundMode: Boolean) {
        isBackgroundMode = backgroundMode
        VirtualDisplayController.setShouldUseVirtualDisplay(backgroundMode)
        VirtualDisplayConfig.setUseVirtualDisplay(appContext, backgroundMode)
        modeDescription =
            if (backgroundMode) {
                appContext.getString(R.string.automation_mode_description_background)
            } else {
                appContext.getString(R.string.automation_mode_description_front)
            }
        updateVirtualDisplayStatus()
    }

    private fun applyAutoApprove(checked: Boolean) {
        autoApprove = checked
        VirtualDisplayConfig.setAutoApproveAutomation(appContext, checked)
    }

    private fun setUseShizukuInteraction(
        checked: Boolean,
        userInitiated: Boolean = true,
    ) {
        if (checked && userInitiated) {
            val state = collectRuntimeConnectionState()
            if (!state.shizukuBinderConnected) {
                Toast.makeText(appContext, "未检测到 Shizuku 服务连接，请先启动 Shizuku", Toast.LENGTH_SHORT).show()
                checkAccessibilityStatus()
                return
            }
            if (!state.shizukuPermissionGranted && !ensureShizukuPermissionGranted()) {
                Toast.makeText(appContext, "未检测到 Shizuku 授权，已发起授权请求，请先在弹窗中授予", Toast.LENGTH_SHORT)
                    .show()
                checkAccessibilityStatus()
                return
            }
        }

        useShizukuInteraction = checked
        VirtualDisplayConfig.setUseShizukuInteraction(appContext, checked)

        if (checked && userInitiated) {
            val latestState = collectRuntimeConnectionState()
            if (!latestState.accessibilityEnabled && latestState.shizukuReady) {
                val granted = grantAccessibilityViaShizuku()
                if (granted) {
                    Toast.makeText(appContext, "已通过 Shizuku 自动开启无障碍", Toast.LENGTH_SHORT).show()
                    refreshStatusAfterOneTapAuthorize()
                } else {
                    Toast.makeText(appContext, "Shizuku 自动开启无障碍失败，请手动开启", Toast.LENGTH_SHORT).show()
                }
            } else if (latestState.accessibilityEnabled && !latestState.accessibilityConnected) {
                Toast.makeText(appContext, "无障碍已开启，正在等待服务连接…", Toast.LENGTH_SHORT).show()
                refreshStatusAfterOneTapAuthorize()
            }
        }
        checkAccessibilityStatus()
    }

    private fun updateComposeStatusSnapshot(
        useShizukuInteraction: Boolean,
        state: RuntimeConnectionState,
        canStart: Boolean,
    ) {
        val accLine =
            when {
                state.accessibilityConnected -> appContext.getString(R.string.automation_status_accessibility_connected)
                state.accessibilityEnabled -> appContext.getString(R.string.automation_status_accessibility_enabled_waiting)
                else -> appContext.getString(R.string.automation_status_accessibility_disabled)
            }
        val shizukuLine =
            when {
                state.shizukuReady -> appContext.getString(R.string.automation_status_shizuku_ready)
                state.shizukuBinderConnected -> appContext.getString(R.string.automation_status_shizuku_waiting_permission)
                else -> appContext.getString(R.string.automation_status_shizuku_disconnected)
            }
        val summary =
            when {
                canStart -> appContext.getString(R.string.automation_status_summary_ready)
                isBackgroundMode && !state.shizukuBinderConnected ->
                    appContext.getString(R.string.automation_status_summary_need_background_shizuku)
                isBackgroundMode && !state.shizukuPermissionGranted ->
                    appContext.getString(R.string.automation_status_summary_need_shizuku_permission)
                useShizukuInteraction && !state.shizukuBinderConnected ->
                    appContext.getString(R.string.automation_status_summary_need_shizuku_connection)
                useShizukuInteraction && !state.shizukuPermissionGranted ->
                    appContext.getString(R.string.automation_status_summary_need_shizuku_permission)
                useShizukuInteraction && !state.accessibilityEnabled ->
                    appContext.getString(R.string.automation_status_summary_need_accessibility_enabled)
                useShizukuInteraction && !state.accessibilityConnected ->
                    appContext.getString(R.string.automation_status_summary_need_accessibility_connection)
                state.shizukuReady -> appContext.getString(R.string.automation_status_summary_shizuku_detected)
                else -> appContext.getString(R.string.automation_status_summary_need_accessibility_generic)
            }
        val modeLine =
            if (useShizukuInteraction) {
                appContext.getString(R.string.automation_status_mode_shizuku)
            } else {
                appContext.getString(R.string.automation_status_mode_accessibility)
            }
        statusSummary = summary
        interactionModeText = modeLine
        accessibilityStatusText = accLine
        shizukuStatusText = shizukuLine
        statusText = listOf(summary, modeLine, "$accLine | $shizukuLine").joinToString("\n")
    }

    private fun scheduleAccessibilityStatusSync(
        totalDurationMs: Long = 12000L,
        pollMs: Long = 300L,
    ) {
        accessibilityStatusSyncJob?.cancel()
        accessibilityStatusSyncJob =
            viewModelScope.launch {
                val deadline = System.currentTimeMillis() + totalDurationMs
                while (System.currentTimeMillis() <= deadline) {
                    checkAccessibilityStatus()
                    delay(pollMs)
                }
            }
    }

    private fun saveRunResult(success: Boolean, message: String, steps: Int, logText: String) {
        viewModelScope.launch {
            runCatching {
                automationResultsRepository.saveResult(
                    success = success,
                    message = message,
                    steps = steps,
                    time = System.currentTimeMillis(),
                    log = logText,
                )
            }.onFailure { e ->
                Log.e("AutomationViewModel", "保存运行结果失败: ${e.message}", e)
            }
        }
    }

    private fun restoreLastRunResult() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = automationResultsRepository.getLastResultBlocking()
                if (result.time <= 0 || System.currentTimeMillis() - result.time >= 24 * 60 * 60 * 1000) {
                    return@runCatching
                }

                val timeStr =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(result.time))
                val statusText = if (result.success) "✅ 成功" else "❌ 失败"

                appendLog("\n=== 上一次运行结果 ($timeStr) ===")
                appendLog("状态: $statusText")
                appendLog("步数: ${result.steps}")
                appendLog("结果: ${result.message}")
                if (result.log.isNotBlank()) {
                    appendLog("\n--- 上次日志 ---")
                    appendLog(result.log.take(2000))
                }
                appendLog("=== 结果已恢复 ===\n")
            }.onFailure {
                Log.e("AutomationViewModel", "恢复运行结果失败: ${it.message}", it)
            }
        }
    }

    private fun initializeToolSystem() {
        try {
            val toolHandler = AIToolHandler.getInstance(appContext)
            ToolRegistration.registerAllTools(toolHandler, appContext)
            appendLog("✅ 工具系统初始化完成")
        } catch (e: Exception) {
            Log.e("AutomationViewModel", "工具系统初始化失败: ${e.message}", e)
            appendLog("⚠️ 工具系统初始化失败: ${e.message}")
        }
    }

    private fun grantAccessibilityViaShizuku(): Boolean {
        if (isAccessibilityServiceEnabled()) return true
        if (!ShizukuBridge.isShizukuAvailable()) return false

        val existing =
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
        val services = existing.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (!services.any { it.equals(serviceId, ignoreCase = true) }) {
            services.add(serviceId)
        }

        val enableList = services.joinToString(":")
        val setServicesResult =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("settings", "put", "secure", "enabled_accessibility_services", enableList),
                )
            }.getOrNull()
        val enableServiceResult =
            runCatching {
                ShizukuBridge.execResultArgs(
                    listOf("settings", "put", "secure", "accessibility_enabled", "1"),
                )
            }.getOrNull()

        if (setServicesResult == null || setServicesResult.exitCode != 0) return false
        if (enableServiceResult == null || enableServiceResult.exitCode != 0) return false
        return isAccessibilityServiceEnabled()
    }

    private fun refreshStatusAfterOneTapAuthorize() {
        viewModelScope.launch {
            repeat(12) {
                val state = collectRuntimeConnectionState()
                checkAccessibilityStatus()
                val canStart = resolveRuntimeInteractionPreference(useShizukuInteraction, state) != null
                if (canStart) return@launch
                delay(250L)
            }
        }
    }

    fun resolveRuntimeInteractionPreference(
        preferShizuku: Boolean,
        state: RuntimeConnectionState,
    ): Boolean? {
        return when {
            isBackgroundMode && !state.shizukuReady -> null
            preferShizuku && state.shizukuReady && state.accessibilityConnected -> true
            !preferShizuku && state.accessibilityConnected -> false
            else -> null
        }
    }

    fun collectRuntimeConnectionState(): RuntimeConnectionState {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val accessibilityConnected = PhoneAgentAccessibilityService.instance != null
        val shizukuBinderConnected = ShizukuBridge.pingBinder()
        val shizukuPermissionGranted = if (shizukuBinderConnected) ShizukuBridge.hasPermission() else false
        return RuntimeConnectionState(
            accessibilityEnabled = accessibilityEnabled,
            accessibilityConnected = accessibilityConnected,
            shizukuBinderConnected = shizukuBinderConnected,
            shizukuPermissionGranted = shizukuPermissionGranted,
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices =
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            !enabledServices.isNullOrEmpty() && enabledServices.contains(serviceId)
        } catch (e: Exception) {
            false
        }
    }

    fun startAgent() {
        if (agentJob != null) return

        val task = taskText.trim()
        if (task.isBlank()) {
            Toast.makeText(appContext, "请输入任务", Toast.LENGTH_SHORT).show()
            return
        }
        if (isBackgroundMode && !ensureBackgroundModeReady()) {
            checkAccessibilityStatus()
            return
        }
        val fromHomeDispatch = overlayClickReturnToMain && lastDispatchedTask == task

        val localModelEnabled = isLocalModelEnabled()
        val apiKey = getApiKey()
        if (!localModelEnabled && apiKey.isBlank()) {
            Toast.makeText(appContext, "请先在侧边栏配置 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        if (localModelEnabled && !ModelScopeModelDownloader.isQwen35ModelReady(appContext)) {
            Toast.makeText(appContext, "本地模型未就绪，请先在主界面下载模型", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = resolveApiBaseUrl()
        val model = resolveAutomationModel()
        val useThirdPartyApi = appPrefsRepository.getApiUseThirdPartyBlocking()

        var allowAccessibilityPendingConnection = false
        if (useShizukuInteraction) {
            val beforeState = collectRuntimeConnectionState()
            if (beforeState.shizukuBinderConnected && beforeState.shizukuPermissionGranted && !beforeState.accessibilityEnabled) {
                val granted = grantAccessibilityViaShizuku()
                if (granted) {
                    appendLog("Shizuku 模式：已自动开启无障碍")
                    refreshStatusAfterOneTapAuthorize()
                } else {
                    Toast.makeText(appContext, "Shizuku 模式下自动开启无障碍失败，请手动开启", Toast.LENGTH_SHORT).show()
                    checkAccessibilityStatus()
                    return
                }
            }
        } else if (fromHomeDispatch) {
            val beforeState = collectRuntimeConnectionState()
            if (beforeState.shizukuReady && !beforeState.accessibilityEnabled) {
                val granted = grantAccessibilityViaShizuku()
                if (granted) {
                    allowAccessibilityPendingConnection = true
                    appendLog("主页启动：检测到 Shizuku 已就绪，已自动开启无障碍")
                    refreshStatusAfterOneTapAuthorize()
                } else {
                    appendLog("主页启动：Shizuku 自动开启无障碍失败，将按当前连接状态继续校验")
                }
            }
        }

        val state = collectRuntimeConnectionState()
        val preferShizukuInteraction = useShizukuInteraction && state.shizukuBinderConnected
        var effectiveUseShizuku = resolveRuntimeInteractionPreference(preferShizukuInteraction, state)
        if (effectiveUseShizuku == null) {
            effectiveUseShizuku =
                when {
                    preferShizukuInteraction && state.shizukuReady && state.accessibilityEnabled -> true
                    !preferShizukuInteraction && state.accessibilityEnabled -> false
                    allowAccessibilityPendingConnection && state.accessibilityEnabled -> false
                    else -> null
                }
        }

        if (effectiveUseShizuku == null) {
            val msg =
                if (isBackgroundMode && !state.shizukuBinderConnected) {
                    appContext.getString(R.string.automation_background_requires_shizuku)
                } else if (isBackgroundMode && !state.shizukuPermissionGranted) {
                    ensureShizukuPermissionGranted()
                    appContext.getString(R.string.automation_background_requires_shizuku_permission)
                } else if (!state.shizukuBinderConnected && !state.accessibilityConnected) {
                    "未检测到可用连接，请先连接 Shizuku 或无障碍服务"
                } else if (!preferShizukuInteraction && !state.accessibilityConnected) {
                    "Shizuku 模式已关闭且无障碍未开启，请先开启无障碍或切换到 Shizuku 模式"
                } else if (preferShizukuInteraction && state.shizukuBinderConnected && !state.shizukuPermissionGranted) {
                    ensureShizukuPermissionGranted()
                    "Shizuku 未授权，已发起授权请求，请授权后重试"
                } else {
                    "当前无可用连接，请检查 Shizuku/无障碍状态"
                }
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            checkAccessibilityStatus()
            return
        }

        if (preferShizukuInteraction && !effectiveUseShizuku) {
            appendLog("Shizuku 未就绪，已自动回退到无障碍执行")
        }

        logText = ""
        val modeText = if (isBackgroundMode) "后台虚拟屏模式" else "前端执行模式"
        appendLog("执行模式：$modeText")
        appendLog("准备开始：baseUrl=$baseUrl, model=$model")
        appendLog("任务：$task")

        val liveNotificationStarted =
            AutomationLiveNotification.show(
                context = appContext,
                title = "分析中",
                subtitle = task.take(20),
                maxSteps = 100,
                navigateMainOnClick = fromHomeDispatch,
            )

        if (!liveNotificationStarted && AutomationOverlay.canDrawOverlays(appContext)) {
            val ok =
                AutomationOverlay.show(
                    context = appContext,
                    title = "分析中",
                    subtitle = task.take(20),
                    maxSteps = 100,
                    activity = hostActivityRef?.get(),
                    navigateMainOnClick = fromHomeDispatch,
                )
            if (!ok) {
                Toast.makeText(appContext, "悬浮窗显示失败，将保持前台显示日志", Toast.LENGTH_SHORT).show()
            }
        } else if (!liveNotificationStarted) {
            Toast.makeText(appContext, "如需显示进度悬浮窗，请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        }

        lastDispatchedTask = null
        paused = false
        updateComposeControlState(canStart = true)

        agentJob =
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val svc =
                        if (effectiveUseShizuku) {
                            waitForAccessibilityServiceConnection(timeoutMs = 6500L)
                        } else {
                            waitForAccessibilityServiceConnection(timeoutMs = 5000L)
                        }
                    if (!effectiveUseShizuku && svc == null) {
                        appendLog("无障碍服务连接失败：未获取到服务实例")
                        AutomationOverlay.complete("无障碍服务未连接")
                        return@launch
                    }
                    if (effectiveUseShizuku && svc == null) {
                        appendLog("Shizuku 模式：无障碍服务未连接，已停止执行")
                        AutomationOverlay.complete("Shizuku 模式需无障碍连接")
                        return@launch
                    }

                    val config =
                        AgentConfiguration(
                            useBackgroundVirtualDisplay = isBackgroundMode,
                            useShizukuInteraction = effectiveUseShizuku,
                        )

                    if (isBackgroundMode) {
                        withContext(Dispatchers.Main) {
                            virtualDisplayStatus = "虚拟屏状态: 正在创建..."
                        }
                    }

                    val agent = UiAutomationAgent(appContext, config)
                    val result =
                        agent.run(
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            useThirdPartyApi = useThirdPartyApi,
                            task = task,
                            service = svc,
                            control =
                                object : UiAutomationAgent.Control {
                                    override fun isPaused(): Boolean = paused

                                    override suspend fun confirm(message: String): Boolean {
                                        if (autoApprove) return true
                                        val hostActivity = hostActivityRef?.get()
                                        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
                                            return false
                                        }
                                        return suspendCancellableCoroutine { cont ->
                                            hostActivity.runOnUiThread {
                                                val dialog =
                                                    androidx.appcompat.app.AlertDialog.Builder(hostActivity)
                                                        .setTitle("需要确认")
                                                        .setMessage(message)
                                                        .setCancelable(false)
                                                        .setPositiveButton("确认") { _, _ ->
                                                            if (cont.isActive) cont.resume(true)
                                                        }
                                                        .setNegativeButton("拒绝") { _, _ ->
                                                            if (cont.isActive) cont.resume(false)
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
                                if (isBackgroundMode) {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        when {
                                            msg.contains("虚拟屏已准备就绪") -> {
                                                val displayId = VirtualDisplayController.getDisplayId()
                                                virtualDisplayStatus = "虚拟屏状态: 已启动 (displayId=$displayId)"
                                            }
                                            msg.contains("虚拟屏准备失败") || msg.contains("虚拟屏模式启动失败") -> {
                                                virtualDisplayStatus = "虚拟屏状态: 创建失败"
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    appendLog("结束：${result.message}（steps=${result.steps}）")
                    AutomationOverlay.complete(result.message)

                    val finalLog = withContext(Dispatchers.Main) { logText }
                    saveRunResult(result.success, result.message, result.steps, finalLog)
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        appendLog("已停止")
                        AutomationOverlay.hide()
                    } else {
                        appendLog("异常：${e.message}")
                        AutomationOverlay.complete(e.message.orEmpty().ifBlank { "执行异常" })
                        val finalLog = withContext(Dispatchers.Main) { logText }
                        saveRunResult(false, e.message ?: "执行异常", 0, finalLog)
                    }
                } finally {
                    agentJob = null
                    VirtualScreenPreviewOverlay.hide()
                    virtualDisplayStatusJob?.cancel()
                    virtualDisplayStatusJob = null
                    paused = false
                    if (isBackgroundMode) {
                        updateVirtualDisplayStatus()
                    }
                    checkAccessibilityStatus()
                }
            }

        if (isBackgroundMode) {
            virtualDisplayStatusJob?.cancel()
            virtualDisplayStatusJob =
                viewModelScope.launch {
                    while (agentJob != null) {
                        updateVirtualDisplayStatus()
                        delay(1000L)
                    }
                }
        }
    }

    private suspend fun waitForAccessibilityServiceConnection(
        timeoutMs: Long = 3500L,
        pollMs: Long = 120L,
    ): PhoneAgentAccessibilityService? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            PhoneAgentAccessibilityService.instance?.let { return it }
            delay(pollMs)
        }
        return PhoneAgentAccessibilityService.instance
    }

    private fun ensureShizukuPermissionGranted(): Boolean {
        if (!ShizukuBridge.pingBinder()) return false
        if (ShizukuBridge.hasPermission()) return true

        runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
        return false
    }

    fun stopAgent() {
        val job = agentJob
        val hadRunning = job != null
        if (job != null) {
            job.cancel()
        }
        agentJob = null
        paused = false
        updateComposeControlState(canStart = composeCanStart)
        appendLog("已请求停止")
        if (!hadRunning) {
            appendLog("已停止")
        }
        AutomationOverlay.hide()
        VirtualScreenPreviewOverlay.hide()
        virtualDisplayStatusJob?.cancel()
        virtualDisplayStatusJob = null
        if (isBackgroundMode ||
            VirtualDisplayController.shouldUseVirtualDisplay ||
            VirtualDisplayController.isVirtualDisplayStarted()
        ) {
            VirtualDisplayController.cleanupAsync(appContext)
            updateVirtualDisplayStatus()
        }
        checkAccessibilityStatus()
    }

    private fun handleStopFromOverlay() {
        stopAgent()
    }

    fun togglePause() {
        if (agentJob == null) return
        paused = !paused
        updateComposeControlState(canStart = composeCanStart)
        appendLog(if (paused) "已暂停（等待继续）" else "已继续")
        VirtualScreenPreviewOverlay.setPausedState(paused)
    }

    private fun initSherpaModel() {
        if (sherpaInitializing) return
        sherpaInitializing = true
        viewModelScope.launch {
            try {
                val (recognizer, success) =
                    withContext(Dispatchers.Default) {
                        val localRecognizer = SherpaSpeechRecognizer(appContext)
                        val ok = localRecognizer.initialize()
                        localRecognizer to ok
                    }
                sherpaSpeechRecognizer = if (success) recognizer else null
                if (!success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "语音模型初始化失败，请重试", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AutomationViewModel", "语音模型初始化异常: ${e.message}", e)
                sherpaSpeechRecognizer = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "语音模型异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                sherpaInitializing = false
            }
        }
    }

    private fun startVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        savedTaskText = taskText
        isListening = true
        voiceInputAnimJob =
            viewModelScope.launch {
                var dotCount = 1
                while (true) {
                    val dots = ".".repeat(dotCount)
                    taskText = "正在语音输入$dots"
                    dotCount = if (dotCount >= 3) 1 else dotCount + 1
                    delay(400)
                }
            }
    }

    private fun stopVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        voiceInputAnimJob = null
        isListening = false
    }

    fun startLocalVoiceInput() {
        val recognizer = sherpaSpeechRecognizer
        if (recognizer == null) {
            Toast.makeText(appContext, "语音模型未初始化，请稍候重试", Toast.LENGTH_SHORT).show()
            initSherpaModel()
            return
        }
        if (!recognizer.isReady()) {
            Toast.makeText(appContext, "语音模型加载中，请稍候…", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListeningInternal) return

        voicePrefix =
            taskText.trim().let { prefix ->
                if (prefix.isBlank()) "" else if (prefix.endsWith(" ")) prefix else "$prefix "
            }

        startVoiceInputAnimation()

        recognizer.startListening(
            object : SherpaSpeechRecognizer.RecognitionListener {
                override fun onPartialResult(text: String) {
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    taskText = txt
                }

                override fun onResult(text: String) {
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    taskText = txt
                }

                override fun onAmplitude(amplitude: Float) {
                }

                override fun onFinalResult(text: String) {
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    taskText = if (txt.isBlank()) savedTaskText else txt
                    stopLocalVoiceInput(triggerRecognizerStop = false)
                }

                override fun onError(exception: Exception) {
                    stopVoiceInputAnimation()
                    taskText = savedTaskText
                    Toast.makeText(appContext, "识别失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    stopLocalVoiceInput(triggerRecognizerStop = false)
                }

                override fun onTimeout() {
                    stopVoiceInputAnimation()
                    taskText = savedTaskText
                    Toast.makeText(appContext, "语音识别超时", Toast.LENGTH_SHORT).show()
                    stopLocalVoiceInput(triggerRecognizerStop = false)
                }
            },
        )

        isListeningInternal = true
    }

    fun stopLocalVoiceInput(triggerRecognizerStop: Boolean = true) {
        val recognizer = sherpaSpeechRecognizer
        stopVoiceInputAnimation()

        val currentText = taskText
        if (currentText.startsWith("正在语音输入")) {
            taskText = savedTaskText
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

        isListeningInternal = false
    }

    private fun getApiKey(): String {
        if (appPrefsRepository.getUseAriesApiBlocking()) {
            return appPrefsRepository.getActiveAriesApiKeyBlocking()
        }
        val key = appPrefsRepository.getApiKeyBlocking()
        if (key.isNotBlank()) return key
        return appPrefsRepository.getAutoglmApiKeyBlocking()
    }

    private fun isLocalModelEnabled(): Boolean {
        return appPrefsRepository.getApiUseLocalModelBlocking()
    }

    private fun resolveApiBaseUrl(): String {
        if (appPrefsRepository.getUseAriesApiBlocking()) {
            return AriesApiClient.ARIES_API_V1_BASE_URL
        }
        val useThirdParty = appPrefsRepository.getApiUseThirdPartyBlocking()
        val useLocalModel = appPrefsRepository.getApiUseLocalModelBlocking()
        if (useLocalModel) return AutoGlmClient.DEFAULT_BASE_URL
        if (!useThirdParty) return AutoGlmClient.DEFAULT_BASE_URL
        val rawUrl = appPrefsRepository.getApiThirdPartyBaseUrlBlocking().trim()
        return rawUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    private fun resolveAutomationModel(): String {
        if (appPrefsRepository.getUseAriesApiBlocking()) {
            return AriesApiClient.ARIES_AUTOMATION_MODEL
        }
        val useLocalModel = appPrefsRepository.getApiUseLocalModelBlocking()
        val useThirdParty = appPrefsRepository.getApiUseThirdPartyBlocking()
        if (useLocalModel) return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        if (!useThirdParty) return AutoGlmClient.PHONE_MODEL

        val rawModel = appPrefsRepository.getApiThirdPartyModelBlocking().trim()
        return rawModel.ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    fun appendLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            logText =
                buildString {
                    val existing = logText.trimEnd()
                    if (existing.isNotBlank()) {
                        append(existing)
                        append('\n')
                    }
                    append(message)
                }
            AutomationOverlay.updateFromLogLine(message)
            if (mirrorLogsToMain) {
                AutomationLogBridge.publish(appContext, message)
            }
        }
    }

    fun updateVirtualDisplayStatus() {
        try {
            val isStarted = VirtualDisplayController.isVirtualDisplayStarted()
            val displayId = VirtualDisplayController.getDisplayId()
            val shouldUse = VirtualDisplayController.shouldUseVirtualDisplay

            val statusValue =
                buildString {
                    if (isStarted && displayId != null) {
                        val configSummary = VirtualDisplayConfig.summary(appContext)
                        append(
                            appContext.getString(
                                R.string.automation_virtual_display_status_running_detail,
                                displayId.toString(),
                                configSummary,
                            ),
                        )
                    } else if (shouldUse) {
                        append(appContext.getString(R.string.automation_virtual_display_status_preparing))
                    } else {
                        append(appContext.getString(R.string.automation_virtual_display_status_not_started))
                    }
                }
            virtualDisplayStatus = statusValue
        } catch (e: Exception) {
            virtualDisplayStatus =
                appContext.getString(
                    R.string.automation_virtual_display_status_error,
                    e.message ?: "未知错误",
                )
        }
    }

    fun startRecommendTaskRotation() {
        if (recommendTasks.isEmpty()) return
        try {
            currentRecommendIndex = 0
            recommendText = recommendTasks[currentRecommendIndex]

            recommendJob?.cancel()
            recommendJob =
                viewModelScope.launch {
                    delay(4000)
                    while (true) {
                        currentRecommendIndex = (currentRecommendIndex + 1) % recommendTasks.size
                        recommendText = recommendTasks[currentRecommendIndex]
                        delay(4000)
                    }
                }
        } catch (e: Exception) {
            Log.e("AutomationViewModel", "推荐任务轮换启动失败: ${e.message}", e)
        }
    }

    private fun bringMainActivityToFront() {
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }
        appContext.startActivity(intent)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { appContext.unregisterReceiver(stopFromOverlayReceiver) }
        runCatching { appContext.unregisterReceiver(pauseToggleReceiver) }
        recommendJob?.cancel()
        recommendJob = null
        stopLocalVoiceInput(triggerRecognizerStop = true)
        sherpaSpeechRecognizer?.shutdown()
        sherpaSpeechRecognizer = null
        stopAgent()
    }
}
