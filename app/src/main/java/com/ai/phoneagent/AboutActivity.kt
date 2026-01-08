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
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.blue_glass_primary)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 与主页一致：把系统栏 top inset 交给 AppBarLayout 的 padding，避免内容层遮挡导致点击无效。
            binding.appBar.setPadding(0, sys.top, 0, 0)
            insets
        }
    }

    private fun setupToolbar() {
        // 标题改为由页面内容区域展示，避免在沉浸式状态栏下出现重复/裁切。
        binding.topAppBar.title = ""
        binding.topAppBar.setNavigationOnClickListener {
            vibrateLight()
            finish()
        }

        // 返回按钮上移一点点，和主页顶栏图标对齐（主页是 -7dp）。
        val upOffsetPx = -7f * resources.displayMetrics.density
        binding.topAppBar.post {
            for (i in 0 until binding.topAppBar.childCount) {
                val child = binding.topAppBar.getChildAt(i)
                if (child is ImageButton) {
                    child.translationY = upOffsetPx
                }
            }
        }
    }

    private fun setupClickListeners() {
        // 检查更新（占位）
        binding.btnCheckUpdate.setOnClickListener {
            vibrateLight()
            checkForUpdates()
        }

        // 更新日志
        binding.root.findViewById<LinearLayout>(R.id.itemChangelog).setOnClickListener {
            vibrateLight()
            showChangelogDialog()
        }

        // 开源许可声明
        binding.root.findViewById<LinearLayout>(R.id.itemLicenses).setOnClickListener {
            vibrateLight()
            showLicensesDialog()
        }

        // 联系方式 - 点击复制邮箱
        binding.root.findViewById<LinearLayout>(R.id.itemContact).setOnClickListener {
            vibrateLight()
            copyToClipboard("jack666_2007@foxmail.com")
            Toast.makeText(this, "邮箱已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 开发者
        binding.root.findViewById<LinearLayout>(R.id.itemDeveloper).setOnClickListener {
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
        val clip = ClipData.newPlainText("email", text)
        clipboard.setPrimaryClip(clip)
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
