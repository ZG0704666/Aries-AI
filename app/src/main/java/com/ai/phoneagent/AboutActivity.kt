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
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.databinding.ActivityAboutBinding
import com.ai.phoneagent.updates.ApkDownloadUtil
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.updates.ReleaseHistoryAdapter
import com.ai.phoneagent.updates.ReleaseRepository
import com.ai.phoneagent.updates.ReleaseUiUtil
import com.ai.phoneagent.updates.UpdateHistoryActivity
import com.ai.phoneagent.updates.VersionComparator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.view.animation.OvershootInterpolator
import android.annotation.SuppressLint
import android.view.MotionEvent

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    private val releaseRepo = ReleaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupToolbar()
        setupClickListeners()

        // 入场动画
        binding.root.post {
            animateEntrance()
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.cardAppInfo,
            binding.cardActions,
            binding.cardDeveloper
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 80f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(150L * index)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applySpringScaleEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(150).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(2.5f)).start()
                }
            }
            false
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val controller = WindowCompat.getInsetsController(window, binding.root)
            controller.isAppearanceLightStatusBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.setPadding(0, sys.top, 0, 0)
            insets
        }
    }

    private fun setupToolbar() {
        // 新布局中返回按钮ID为 btnBack
        val btnBack = binding.root.findViewById<ImageButton>(R.id.btnBack)
        btnBack?.let { btn ->
            // 设置返回按钮的颜色（兼容各API级别）
            androidx.core.widget.ImageViewCompat.setImageTintList(
                btn,
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.blue_glass_primary)
                )
            )
            btn.setOnClickListener {
                vibrateLight()
                finish()
            }
        }
    }

    private fun setupClickListeners() {
        // 应用点击缩放动效
        applySpringScaleEffect(binding.btnCheckUpdate)
        applySpringScaleEffect(binding.itemChangelog)
        applySpringScaleEffect(binding.itemLicenses)
        applySpringScaleEffect(binding.itemDeveloper)
        applySpringScaleEffect(binding.itemContact)
        
        // 尝试绑定官网项（如果布局中存在）
        findViewById<View>(R.id.itemWebsite)?.let {
            applySpringScaleEffect(it)
            it.setOnClickListener {
                vibrateLight()
                openUrl("https://aries-agent.com/")
            }
        }

        // 检查更新（占位）
        binding.btnCheckUpdate.setOnClickListener {
            vibrateLight()
            checkForUpdates()
        }

        // 更新日志
        binding.itemChangelog.setOnClickListener {
            vibrateLight()
            showChangelogDialog()
        }

        // 开源许可声明
        binding.itemLicenses.setOnClickListener {
            vibrateLight()
            showLicensesDialog()
        }

        // 联系方式 - 点击复制邮箱
        binding.itemContact.setOnClickListener {
            vibrateLight()
            copyToClipboard("zhangyongqi@njit.edu.cn")
            Toast.makeText(this, "邮箱已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 开发者
        binding.itemDeveloper.setOnClickListener {
            vibrateLight()
            Toast.makeText(this, "感谢使用 Aries AI！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangelogDialog() {
        showReleaseHistoryDialog()
    }

    private fun showReleaseHistoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_release_history, null, false)

        val tvTips = view.findViewById<TextView>(R.id.tvTips)
        tvTips.text = "下方可以选择历史版本"

        val switchPrerelease = view.findViewById<SwitchMaterial>(R.id.switchPrerelease)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val tvError = view.findViewById<TextView>(R.id.tvError)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerReleases)

        recycler.layoutManager = LinearLayoutManager(this)

        var includePrerelease = false
        var loaded: List<ReleaseEntry> = emptyList()

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val adapter =
            ReleaseHistoryAdapter(
                onDetails = { showReleaseDetails(it) },
                onOpenRelease = { ReleaseUiUtil.openUrl(this, it.releaseUrl) },
                onDownload = { handleDownload(it) },
            )

        recycler.adapter = adapter

        fun applyFilter() {
            val list = if (includePrerelease) loaded else loaded.filter { !it.isPrerelease }
            adapter.submitList(list)
        }

        switchPrerelease.setOnCheckedChangeListener { _, checked ->
            includePrerelease = checked
            applyFilter()
        }


        dialog =
            MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
                .setView(view)
                .setPositiveButton("关闭", null)
                .create()

        dialog.show()

        tvError.visibility = View.GONE
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { releaseRepo.fetchReleasePage(page = 1, perPage = 20) }
            progress.visibility = View.GONE

            result
                .onSuccess { list ->
                    loaded = list
                    applyFilter()
                }
                .onFailure { e ->
                    tvError.visibility = View.VISIBLE
                    tvError.text = ReleaseUiUtil.formatError(e)
                }
        }
    }

    private fun showReleaseDetails(entry: ReleaseEntry) {
        MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
            .setTitle(entry.versionTag)
            .setMessage(entry.body.ifBlank { "（无更新说明）" })
            .setPositiveButton("打开发布") { _, _ ->
                ReleaseUiUtil.openUrl(this, entry.releaseUrl)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun handleDownload(entry: ReleaseEntry) {
        if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
            ApkDownloadUtil.enqueueApkDownload(this, entry)
            return
        }

        val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
        if (options.isEmpty()) {
            ReleaseUiUtil.openUrl(this, entry.releaseUrl)
            return
        }

        if (options.size == 1) {
            ReleaseUiUtil.openUrl(this, options.first().second)
            return
        }

        val names = options.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
            .setTitle("选择下载源")
            .setItems(names) { _, which ->
                ReleaseUiUtil.openUrl(this, options[which].second)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkForUpdates() {
        val currentVersion =
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { releaseRepo.fetchLatestRelease(includePrerelease = false) }
            result
                .onSuccess { latest ->
                    if (latest == null) {
                        MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                            .setTitle("检查更新")
                            .setMessage("未获取到 Release。")
                            .setPositiveButton("确定", null)
                            .show()
                        return@onSuccess
                    }

                    val newer = VersionComparator.compare(latest.version, currentVersion) > 0
                    if (newer) {
                        MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                            .setTitle("发现新版本 ${latest.versionTag}")
                            .setMessage(latest.body.ifBlank { "（无更新说明）" })
                            .setPositiveButton("下载") { _, _ -> handleDownload(latest) }
                            .setNegativeButton("查看发布") { _, _ -> ReleaseUiUtil.openUrl(this@AboutActivity, latest.releaseUrl) }
                            .setNeutralButton("更新历史") { _, _ -> showReleaseHistoryDialog() }
                            .show()
                    } else {
                        MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                            .setTitle("已是最新")
                            .setMessage("当前版本：$currentVersion")
                            .setPositiveButton("确定", null)
                            .setNeutralButton("更新历史") { _, _ -> showReleaseHistoryDialog() }
                            .show()
                    }
                }
                .onFailure { e ->
                    MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                        .setTitle("检查更新失败")
                        .setMessage(ReleaseUiUtil.formatError(e))
                        .setPositiveButton("确定", null)
                        .setNeutralButton("更新历史") { _, _ -> showReleaseHistoryDialog() }
                        .show()
                }
        }
    }

    private fun showLicensesDialog() {
        val licenses = listOf(
            License("AndroidX Core KTX", "Kotlin extensions for Android core libraries", "Apache-2.0"),
            License("AndroidX AppCompat", "Backward-compatible Android UI components", "Apache-2.0"),
            License("Material Components", "Material Design components for Android", "Apache-2.0"),
            License("Kotlin Coroutines", "Kotlin coroutines support", "Apache-2.0"),
            License("OkHttp", "HTTP client for Android and Java", "Apache-2.0"),
            License("Gson", "JSON serialization/deserialization library", "Apache-2.0"),
            License("sherpa-ncnn", "Offline speech recognition engine", "Apache-2.0"),
            License("AndroidX RecyclerView", "Efficient list display widget", "Apache-2.0"),
            License("AndroidX ConstraintLayout", "Flexible layout manager", "Apache-2.0"),
        )

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_licenses, null, false)
        val container = view.findViewById<LinearLayout>(R.id.licenseContainer)

        licenses.forEach { lic ->
            val row = layoutInflater.inflate(R.layout.item_license_row, container, false)
            row.findViewById<TextView>(R.id.tvLibName).text = lic.name
            row.findViewById<TextView>(R.id.tvLibDesc).text = lic.description
            row.findViewById<TextView>(R.id.tvLibLicense).text = "许可: ${lic.license}"
            container.addView(row)
        }

        MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
            .setView(view)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开网页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrateLight() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    private data class License(val name: String, val description: String, val license: String)
}
