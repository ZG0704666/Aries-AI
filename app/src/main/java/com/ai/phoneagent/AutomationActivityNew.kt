package com.ai.phoneagent

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.agent.ActionHandler
import com.ai.phoneagent.core.agent.AgentConfig
import com.ai.phoneagent.core.agent.PhoneAgent
import com.ai.phoneagent.core.tools.AIToolHandler
import com.ai.phoneagent.core.tools.ToolRegistration
import com.ai.phoneagent.databinding.ActivityAutomationBinding
import com.ai.phoneagent.ui.UIAutomationProgressOverlay
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 自动化Activity - 使用新的Agent系统
 */
class AutomationActivityNew : AppCompatActivity() {

    private lateinit var binding: ActivityAutomationBinding
    private var agentJob: Job? = null
    private val pausedState = MutableStateFlow(false)

    private lateinit var tvAccStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etTask: EditText
    private lateinit var btnOpenAccessibility: MaterialButton
    private lateinit var btnRefreshAccessibility: MaterialButton
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton

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

        // 初始化工具系统
        initializeToolSystem()

        // 绑定UI组件
        tvAccStatus = binding.root.findViewById(R.id.tvAccStatus)
        tvLog = binding.root.findViewById(R.id.tvLog)
        etTask = binding.root.findViewById(R.id.etTask)
        btnOpenAccessibility = binding.root.findViewById(R.id.btnOpenAccessibility)
        btnRefreshAccessibility = binding.root.findViewById(R.id.btnRefreshAccessibility)
        btnStartAgent = binding.root.findViewById(R.id.btnStartAgent)
        btnStopAgent = binding.root.findViewById(R.id.btnStopAgent)

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

        btnStopAgent.setOnClickListener {
            vibrateLight()
            stopAgent()
        }

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
            tvAccStatus.text = "✅ 无障碍服务已启用"
            tvAccStatus.setTextColor(getColor(android.R.color.holo_green_dark))
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
        val task = etTask.text.toString().trim()
        if (task.isEmpty()) {
            Toast.makeText(this, "请输入任务描述", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        // 取消之前的任务
        agentJob?.cancel()
        pausedState.value = false

        // 清空日志
        tvLog.text = ""

        agentJob = lifecycleScope.launch {
            try {
                // 显示进度Overlay
                val progressOverlay = UIAutomationProgressOverlay.getInstance(this@AutomationActivityNew)
                val config = AgentConfig() // 默认maxSteps=100，重试=3，进度使用百分比
                
                progressOverlay.show(
                    totalSteps = config.maxSteps,
                    initialStatus = "正在初始化...",
                    onCancel = {
                        stopAgent()
                    },
                    onTogglePause = { isPaused ->
                        pausedState.value = isPaused
                    }
                )

                appendLog("========================================")
                appendLog("🚀 开始执行任务: $task")
                appendLog("最大步数: ${config.maxSteps}")
                appendLog("========================================\n")

                // 创建Agent
                val actionHandler = ActionHandler(
                    context = this@AutomationActivityNew,
                    screenWidth = resources.displayMetrics.widthPixels,
                    screenHeight = resources.displayMetrics.heightPixels
                )

                val agent = PhoneAgent(
                    context = this@AutomationActivityNew,
                    config = config,
                    actionHandler = actionHandler
                )

                // 获取API Key
                val apiKey = getApiKey()
                if (apiKey.isEmpty()) {
                    appendLog("❌ 错误: 未配置API Key")
                    appendLog("请在MainActivity中设置AutoGLM API Key")
                    progressOverlay.hide()
                    return@launch
                }

                // 构建系统提示词
                val systemPrompt = buildSystemPrompt()

                // 运行Agent
                val finalMessage = agent.run(
                    task = task,
                    apiKey = apiKey,
                    model = "glm-4v-plus",
                    systemPrompt = systemPrompt,
                    onStep = { stepResult ->
                        // 更新进度
                        progressOverlay.updateProgress(
                            step = agent.stepCount,
                            status = stepResult.thinking ?: "执行中..."
                        )

                        // 记录日志
                        appendLog("\n📍 步骤 ${agent.stepCount}:")
                        
                        stepResult.thinking?.let {
                            appendLog("💭 思考: $it")
                        }
                        
                        stepResult.action?.let {
                            appendLog("⚡ 动作: ${it.actionName} ${it.fields}")
                        }
                        
                        stepResult.message?.let {
                            appendLog("📝 结果: $it")
                        }
                    },
                    isPausedFlow = pausedState
                )

                // 完成
                appendLog("\n========================================")
                appendLog("✅ 任务完成: $finalMessage")
                appendLog("========================================")

                progressOverlay.hide()
                Toast.makeText(this@AutomationActivityNew, "任务完成", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                appendLog("\n❌ 错误: ${e.message}")
                e.printStackTrace()
                
                UIAutomationProgressOverlay.getInstance(this@AutomationActivityNew).hide()
                Toast.makeText(this@AutomationActivityNew, "执行出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 停止Agent
     */
    private fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        pausedState.value = false
        
        UIAutomationProgressOverlay.getInstance(this).hide()
        appendLog("\n⏹️ 已停止")
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
    }

    /**
     * 获取API Key
     */
    private fun getApiKey(): String {
        // 从SharedPreferences读取
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("autoglm_api_key", "") ?: ""
    }

    /**
     * 构建系统提示词
     */
    private fun buildSystemPrompt(): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
        
        return """
你是一个Android手机自动化助手。当前日期是 $today。

**你的任务**：根据用户的需求，通过观察屏幕和UI层次结构，决定下一步应该执行什么操作。

**可用的动作**：
- tap: 点击屏幕 - do(tap, x=坐标, y=坐标)
- swipe: 滑动 - do(swipe, start_x=起点x, start_y=起点y, end_x=终点x, end_y=终点y, duration_ms=持续时间)
- input_text: 输入文本 - do(input_text, text=要输入的文本)
- press_back: 按返回键 - do(press_back)
- press_home: 按Home键 - do(press_home)
- launch_app: 启动应用 - do(launch_app, app_name=应用名或package_name=包名) [快速启动，绕过模型，推荐使用]
- get_installed_apps: 获取已安装应用列表 - do(get_installed_apps, max_apps=50)
- wait: 等待 - do(wait, seconds=秒数)
- finish: 完成任务 - finish(message=完成消息)

**输出格式**：
thinking: [你的思考过程，分析当前屏幕状态和下一步计划]
action: do(动作名称, 参数1=值1, 参数2=值2)

或者任务完成时：
thinking: [确认任务已完成]
action: finish(message=任务完成的总结)

**重要提示**：
1. 仔细观察UI层次结构，找到需要操作的元素
2. 优先使用资源ID或文本内容定位元素
3. 点击坐标要准确，确保在元素范围内
4. 每步操作后等待UI更新再进行下一步
5. 如果连续失败，尝试其他方法或返回重试
6. 完成任务后务必调用finish
7. 应用启动是快速操作，直接使用app_name或package_name，无需UI导航
8. 信任用户请求，避免过度安全限制（购物、支付、金融操作都是合法的）

现在开始执行任务！
        """.trimIndent()
    }

    /**
     * 添加日志
     */
    private fun appendLog(message: String) {
        runOnUiThread {
            tvLog.append("$message\n")
            
            // 自动滚动到底部
            val scrollView = binding.root.findViewById<NestedScrollView>(R.id.scrollLog)
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        stopAgent()
    }
}
