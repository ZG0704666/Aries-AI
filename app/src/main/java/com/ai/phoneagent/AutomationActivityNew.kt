package com.ai.phoneagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.ToolRegistration
import com.ai.phoneagent.databinding.ActivityAutomationBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.google.android.material.button.MaterialButton
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.animation.OvershootInterpolator

/**
 * 自动化Activity - 使用新的Agent系统
 */
class AutomationActivityNew : AppCompatActivity() {

    private lateinit var binding: ActivityAutomationBinding
    private var agentJob: Job? = null

    @Volatile private var paused: Boolean = false

    private lateinit var tvAccStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etTask: EditText
    private lateinit var btnOpenAccessibility: MaterialButton
    private lateinit var btnRefreshAccessibility: MaterialButton
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnPauseAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAutomationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialTop = binding.root.paddingTop
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (ime.bottom > sys.bottom) ime.bottom else sys.bottom
            v.setPadding(
                v.paddingLeft,
                initialTop + sys.top,
                v.paddingRight,
                initialBottom + bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        // 绑定UI组件
        tvAccStatus = binding.root.findViewById(R.id.tvAccStatus)
        tvLog = binding.root.findViewById(R.id.tvLog)
        etTask = binding.root.findViewById(R.id.etTask)
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
        if (enabled) {
            val connected = PhoneAgentAccessibilityService.instance != null
            if (connected) {
                tvAccStatus.text = "✅ 无障碍服务已启用"
                tvAccStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvAccStatus.text = "⚠️ 无障碍服务已启用（连接中）"
                tvAccStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            btnStartAgent.isEnabled = true
        } else {
            tvAccStatus.text = "❌ 无障碍服务未启用"
            tvAccStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnStartAgent.isEnabled = false
        }
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
        stopAgent()
    }
}
