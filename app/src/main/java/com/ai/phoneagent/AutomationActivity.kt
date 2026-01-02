package com.ai.phoneagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.databinding.ActivityAutomationBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.google.android.material.button.MaterialButton
import android.view.HapticFeedbackConstants
import android.view.animation.OvershootInterpolator
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AutomationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutomationBinding

    private var agentJob: Job? = null

    private lateinit var tvAccStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etTask: EditText
    private lateinit var btnOpenAccessibility: MaterialButton
    private lateinit var btnRefreshAccessibility: MaterialButton
    private lateinit var btnRunDemo: MaterialButton
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnPauseAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton

    @Volatile private var paused: Boolean = false

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

        tvAccStatus = binding.root.findViewById(R.id.tvAccStatus)
        tvLog = binding.root.findViewById(R.id.tvLog)
        etTask = binding.root.findViewById(R.id.etTask)
        btnOpenAccessibility = binding.root.findViewById(R.id.btnOpenAccessibility)
        btnRefreshAccessibility = binding.root.findViewById(R.id.btnRefreshAccessibility)
        btnRunDemo = binding.root.findViewById(R.id.btnRunDemo)
        btnStartAgent = binding.root.findViewById(R.id.btnStartAgent)
        btnPauseAgent = binding.root.findViewById(R.id.btnPauseAgent)
        btnStopAgent = binding.root.findViewById(R.id.btnStopAgent)

        setupLogCopy()

        binding.topAppBar.setNavigationOnClickListener { finish() }

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnRefreshAccessibility.setOnClickListener { refreshAccessibilityStatus() }

        btnRunDemo.setOnClickListener {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "").orEmpty()
            if (apiKey.isBlank()) {
                Toast.makeText(this, "请先在侧边栏配置 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val svc = PhoneAgentAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(this, "服务已开启但尚未连接，请稍等或返回重进", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            svc.runDemo()
            Toast.makeText(this, "已发送示例动作", Toast.LENGTH_SHORT).show()
        }

        btnPauseAgent.isEnabled = false
        btnStopAgent.isEnabled = false
        btnStartAgent.setOnClickListener { startModelDrivenAutomation() }
        btnPauseAgent.setOnClickListener { togglePause() }
        btnStopAgent.setOnClickListener { stopModelDrivenAutomation() }

        refreshAccessibilityStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    override fun onDestroy() {
        AutomationOverlay.hide()
        super.onDestroy()
    }

    private fun refreshAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        tvAccStatus.text =
                when {
                    !enabled -> "无障碍状态：未开启"
                    PhoneAgentAccessibilityService.instance == null -> "无障碍状态：已开启（连接中）"
                    else -> "无障碍状态：已开启"
                }
    }

    private fun startModelDrivenAutomation() {
        if (agentJob != null) return

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        val svc = PhoneAgentAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "服务已开启但尚未连接，请稍等或返回重进", Toast.LENGTH_SHORT).show()
            return
        }

        val taskRaw = etTask.text?.toString().orEmpty().trim()
        if (taskRaw.isBlank()) {
            Toast.makeText(this, "请输入任务", Toast.LENGTH_SHORT).show()
            return
        }

        run {
            val match = AppPackageMapping.bestMatchInText(taskRaw)
            if (match == null || match.start > 10) return@run

            val pm = packageManager
            fun buildLaunchIntent(pkgName: String): Intent? {
                val direct = pm.getLaunchIntentForPackage(pkgName)
                if (direct != null) return direct
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val ri =
                        runCatching { pm.queryIntentActivities(query, 0) }
                                .getOrNull()
                                ?.firstOrNull { it.activityInfo?.packageName == pkgName }
                                ?: return null
                val ai = ri.activityInfo ?: return null
                return Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ai.packageName, ai.name)
            }

            val intent = buildLaunchIntent(match.packageName)
            if (intent == null) {
                Toast.makeText(this, "暂未在手机中找到${match.appLabel}应用", Toast.LENGTH_SHORT)
                        .show()
                return
            }
        }

        val shortcut = tryLocalLaunchShortcut(taskRaw)
        if (shortcut != null) {
            val (remaining, launchedLabel) = shortcut
            if (AutomationOverlay.canDrawOverlays(this)) {
                val ok =
                    AutomationOverlay.show(
                        context = this,
                        title = "分析中",
                        subtitle = launchedLabel,
                        maxSteps = 12,
                        activity = this,
                    )
                if (!ok) {
                    Toast.makeText(this, "悬浮窗显示失败，将保持前台显示日志", Toast.LENGTH_SHORT)
                            .show()
                }
            } else {
                Toast.makeText(this, "如需显示进度悬浮窗，请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            }

            if (remaining.isBlank()) {
                appendLog("本地直开完成：$launchedLabel")
                AutomationOverlay.complete("已打开：$launchedLabel")
                return
            }
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "").orEmpty()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "请先在侧边栏配置 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val task = shortcut?.first ?: taskRaw

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
                            maxSteps = 12,
                            activity = this,
                        )
            if (ok) {
                // 延迟一点让动画播放
                window.decorView.postDelayed({
                    moveTaskToBack(true)
                }, 100)
            } else {
                Toast.makeText(this, "悬浮窗显示失败，将保持前台显示日志", Toast.LENGTH_SHORT)
                        .show()
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
                                                                                        this@AutomationActivity
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
                                        onLog = { msg -> runOnUiThread { appendLog(msg) } }
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

    private fun togglePause() {
        if (agentJob == null) return
        paused = !paused
        btnPauseAgent.text = if (paused) "继续" else "暂停"
        appendLog(if (paused) "已暂停（等待继续）" else "已继续")
    }

    private fun stopModelDrivenAutomation() {
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

    private fun appendLog(line: String) {
        val old = tvLog.text?.toString().orEmpty()
        tvLog.text = if (old.isBlank()) line else (old + "\n" + line)
        AutomationOverlay.updateFromLogLine(line)
        tvLog.post {
            val sv = tvLog.parent as? android.widget.ScrollView
            sv?.fullScroll(android.view.View.FOCUS_DOWN)
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
            val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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

    private fun tryLocalLaunchShortcut(task: String): Pair<String, String>? {
        val t = task.trim()
        if (t.isBlank()) return null

        val prefixes = listOf("打开", "启动", "进入")
        if (prefixes.none { t.startsWith(it) }) return null

        val match = AppPackageMapping.bestMatchInText(t) ?: return null
        if (match.start > 10) return null

        val pm = packageManager
        val svc = PhoneAgentAccessibilityService.instance

        fun buildLaunchIntent(pkgName: String): Intent? {
            val direct = pm.getLaunchIntentForPackage(pkgName)
            if (direct != null) return direct
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val ri =
                    runCatching { pm.queryIntentActivities(query, 0) }
                            .getOrNull()
                            ?.firstOrNull { it.activityInfo?.packageName == pkgName }
                            ?: return null
            val ai = ri.activityInfo ?: return null
            return Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(ai.packageName, ai.name)
        }

        val intent = buildLaunchIntent(match.packageName) ?: return null
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        // 统一通过透明跳板拉起，最大化减少后台启动弹窗
        val launched = runCatching { LaunchProxyActivity.launch(this, intent) }.isSuccess
        if (!launched && svc != null) {
            runCatching { LaunchProxyActivity.launch(svc, intent) }
        }
        if (!launched) return null
        appendLog("本地直开：${match.appLabel}（${match.packageName}）")

        var rest = t.substring(match.end)
        rest = rest.trimStart(
                ' ',
                '\n',
                '\t',
                '，',
                ',',
                '。',
                '.',
                '！',
                '!',
                '？',
                '?',
                '；',
                ';',
                '：',
                ':',
                '-',
        )
        val connectors = listOf("并", "然后", "再", "接着")
        var changed = true
        while (changed) {
            changed = false
            val before = rest
            for (c in connectors) {
                if (rest.startsWith(c)) {
                    rest = rest.removePrefix(c).trimStart()
                    changed = true
                }
            }
            if (before == rest) break
        }
        return rest to match.appLabel
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled =
                Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val setting =
                Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                        ?: return false
        return setting.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }
}
