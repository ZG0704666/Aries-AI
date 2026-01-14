package com.ai.phoneagent

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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
import android.view.animation.OvershootInterpolator

/**
 * 自动化Activity - 使用新的Agent系统
 */
class AutomationActivityNew : AppCompatActivity() {

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

    private var sherpaSpeechRecognizer: SherpaSpeechRecognizer? = null
    private var isListening: Boolean = false
    private var micAnimator: ObjectAnimator? = null
    private var voiceInputAnimJob: Job? = null
    private var savedTaskText: String = ""
    private var voicePrefix: String = ""
    private var pendingStartVoice: Boolean = false
    
    // 推荐语句滚动相关
    private lateinit var tvRecommendTask: TextView
     private var recommendJob: Job? = null
    private val recommendTasks = listOf(
        "打开美团帮我预订一个明天中午11点的周围人气最高的火锅店的位置，4个人",
        "打开12306订一张1月19日南京到北京的票，选最便宜的",
        "打开航旅纵横订一张1月19日从南京飞往成都的机票"
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = true
        }

        val initialTop = binding.root.paddingTop
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (ime.bottom > sys.bottom) ime.bottom else sys.bottom
            
            // 将 top inset 应用到 AppBar
            binding.appBar.setPadding(0, sys.top, 0, 0)
            
            v.setPadding(
                v.paddingLeft,
                initialTop,
                v.paddingRight,
                initialBottom + bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        // 绑定UI组件
        tvAccStatus = binding.root.findViewById(R.id.tvAccStatus)
        statusIndicator = binding.root.findViewById(R.id.statusIndicator)
        tvLog = binding.root.findViewById(R.id.tvLog)
        etTask = binding.root.findViewById(R.id.etTask)
        btnVoiceTask = binding.root.findViewById(R.id.btnVoiceTask)
        btnOpenAccessibility = binding.root.findViewById(R.id.btnOpenAccessibility)
        btnRefreshAccessibility = binding.root.findViewById(R.id.btnRefreshAccessibility)
        btnStartAgent = binding.root.findViewById(R.id.btnStartAgent)
        btnPauseAgent = binding.root.findViewById(R.id.btnPauseAgent)
        btnStopAgent = binding.root.findViewById(R.id.btnStopAgent)
        tvRecommendTask = binding.root.findViewById(R.id.tvRecommendTask)

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

        initSherpaModel()
    }

    override fun onStop() {
        stopLocalVoiceInput(triggerRecognizerStop = true)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }

    /**
     * 初始化工具系统
     */
    private fun initializeToolSystem() {
        val toolHandler = AIToolHandler.getInstance(this)
        ToolRegistration.registerAllTools(toolHandler, this)
        appendLog("✅ 工具系统初始化完成")
    }

    /**
     * 检查无障碍服务状态
     */
    private fun checkAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        val connected = PhoneAgentAccessibilityService.instance != null
        
        tvAccStatus.text = when {
            !enabled -> "服务未开启：请前往设置"
            !connected -> "服务已开启：正在连接..."
            else -> "已就绪：无障碍连接正常"
        }
        
        statusIndicator.setBackgroundResource(
            when {
                !enabled -> R.drawable.bg_circle_red
                !connected -> R.drawable.bg_circle_yellow
                else -> R.drawable.bg_circle_green
            }
        )
        
        // 无障碍权限未开启时，显示提示
        if (!enabled) {
            etTask.hint = "请打开无障碍服务"
            etTask.isEnabled = false
        } else {
            etTask.hint = "在此输入或录入您的任务指令..."
            etTask.isEnabled = true
        }
        
        btnOpenAccessibility.visibility = if (enabled) View.GONE else View.VISIBLE
        btnStartAgent.isEnabled = enabled
    }

    /**
     * 判断无障碍服务是否启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        var enabled = false
        try {
            val string = Settings.Secure.getString(
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

    /**
     * 启动Agent
     */
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

        val model = AutoGlmClient.PHONE_MODEL

        tvLog.text = ""
        appendLog("准备开始：model=$model")
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
                window.decorView.postDelayed({
                    moveTaskToBack(true)
                }, 100)
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
                        val agent = UiAutomationAgent()
                        val result =
                                agent.run(
                                        apiKey = apiKey,
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
                                        onLog = { msg -> appendLog(msg) },
                                )
                        appendLog("结束：${result.message}（steps=${result.steps}）")
                        AutomationOverlay.complete(result.message)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            appendLog("已停止")
                            AutomationOverlay.hide()
                        } else {
                            appendLog("异常：${e.message}")
                            AutomationOverlay.complete(e.message.orEmpty().ifBlank { "执行异常" })
                        }
                    } finally {
                        agentJob = null
                        runOnUiThread {
                            btnStartAgent.isEnabled = true
                            btnPauseAgent.isEnabled = false
                            paused = false
                            btnPauseAgent.text = "暂停"
                            btnStopAgent.isEnabled = false
                        }
                    }
                }
    }

    /**
     * 停止Agent
     */
    private fun stopAgent() {
        val job = agentJob ?: return
        job.cancel()
        agentJob = null
        btnStartAgent.isEnabled = true
        btnPauseAgent.isEnabled = false
        paused = false
        btnPauseAgent.text = "暂停"
        btnStopAgent.isEnabled = false
        appendLog("已请求停止")
        AutomationOverlay.hide()
    }

    private fun togglePause() {
        if (agentJob == null) return
        paused = !paused
        btnPauseAgent.text = if (paused) "继续" else "暂停"
        appendLog(if (paused) "已暂停（等待继续）" else "已继续")
    }

    private fun initSherpaModel() {
        lifecycleScope.launch {
            try {
                sherpaSpeechRecognizer = SherpaSpeechRecognizer(this@AutomationActivityNew)
                val success = sherpaSpeechRecognizer?.initialize() == true
                if (!success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                        this@AutomationActivityNew,
                                        "语音模型初始化失败，请重试",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                                    this@AutomationActivityNew,
                                    "语音模型异常: ${e.message}",
                                    Toast.LENGTH_LONG
                            )
                            .show()
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
        voiceInputAnimJob = lifecycleScope.launch {
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

    /**
     * 获取API Key
     */
    private fun getApiKey(): String {
        // 从SharedPreferences读取
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isNotBlank()) return key
        return prefs.getString("autoglm_api_key", "") ?: ""
    }

    /**
     * 添加日志
     */
    private fun appendLog(message: String) {
        runOnUiThread {
            tvLog.append("$message\n")
            AutomationOverlay.updateFromLogLine(message)
            
            // 自动滚动到底部
            val scrollView = binding.root.findViewById<NestedScrollView>(R.id.scrollLog)
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
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

    /**
     * 轻微振动
     */
    private fun vibrateLight() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    /**
     * 启动推荐任务滚动播放
     */
    private fun startRecommendTaskRotation() {
        if (recommendTasks.isEmpty()) return
        
        // 初始显示第一条
        currentRecommendIndex = 0
        tvRecommendTask.text = recommendTasks[currentRecommendIndex]
        
        // 启动协程，每4秒切换
        recommendJob?.cancel()
        recommendJob = lifecycleScope.launch {
            delay(4000) // 第一条显示4秒
            while (true) {
                currentRecommendIndex = (currentRecommendIndex + 1) % recommendTasks.size
                val nextText = recommendTasks[currentRecommendIndex]
                
                // 简单淡出淡入效果
                tvRecommendTask.animate()
                    .alpha(0.3f)
                    .setDuration(200)
                    .withEndAction {
                        tvRecommendTask.text = nextText
                        tvRecommendTask.animate()
                            .alpha(0.65f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
                
                delay(4000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recommendJob?.cancel()
        recommendJob = null
        stopLocalVoiceInput(triggerRecognizerStop = true)
        sherpaSpeechRecognizer?.shutdown()
        sherpaSpeechRecognizer = null
        stopAgent()
    }
}
