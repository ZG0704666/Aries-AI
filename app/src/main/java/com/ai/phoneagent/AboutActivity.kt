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
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.ai.phoneagent.updates.UpdateConfig
import com.ai.phoneagent.updates.UpdateLinkAdapter
import com.ai.phoneagent.updates.UpdateNotificationUtil
import com.ai.phoneagent.updates.UpdateStore
import com.ai.phoneagent.updates.ReleaseUiUtil
import com.ai.phoneagent.updates.UpdateHistoryActivity
import com.ai.phoneagent.updates.VersionComparator
import com.ai.phoneagent.updates.DialogSizingUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.view.animation.OvershootInterpolator
import android.text.Html
import android.view.animation.AccelerateInterpolator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    private val releaseRepo = ReleaseRepository()

    private data class InstalledVersion(
        val versionName: String,
        val versionCode: Long,
    )

    private fun getInstalledVersion(): InstalledVersion {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            InstalledVersion(versionName = pi.versionName ?: "", versionCode = code)
        } catch (_: Exception) {
            InstalledVersion(versionName = "", versionCode = 0)
        }
    }

    private fun normalizeTag(tag: String): String {
        val t = tag.trim()
        return if (t.startsWith("v", ignoreCase = true)) t.substring(1) else t
    }

    private fun localReleaseHighlights(): String {
        val versionName = getInstalledVersion().versionName
        return if (versionName == "1.1.0") {
            "\n\nV1.1.0 版本更新说明：\n- 引入动态规划算法，大幅提升模型处理速度！"
        } else {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupToolbar()
        setupClickListeners()

        // 同步显示当前版本（避免 layout 写死导致版本不一致）
        val installed = getInstalledVersion()
        findViewById<TextView>(R.id.tvAppVersion)?.text =
            if (installed.versionName.isNotBlank()) "v${installed.versionName} (${installed.versionCode})" else ""

        // 入场动画
        binding.root.post {
            animateEntrance()
            maybeShowUpdateDialogFromIntent()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
        }
        maybeShowUpdateDialogFromIntent()
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.cardAppInfo,
            binding.cardActions,
            binding.cardDeveloper
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(100L * index)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
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
        applySpringScaleEffect(binding.itemUserAgreement)
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

        // 用户协议与隐私政策
        binding.itemUserAgreement.setOnClickListener {
            vibrateLight()
            showUserAgreementDialog()
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

    private fun showUserAgreementDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = com.ai.phoneagent.databinding.DialogUserAgreementBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0f)
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        val cardView = dialogBinding.cardAgreement
        val containerView = dialogBinding.dialogContainer

        // 应用自适应高度
        DialogSizingUtil.applyCompactSizing(
            this,
            cardView,
            dialogBinding.scrollAgreement,
            null,
            false
        )

        val content = getString(R.string.user_agreement_content)
        dialogBinding.tvAgreementContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(content)
        }

        fun exitDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(450)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        dialogBinding.btnAgreementAgree.text = "返回"
        dialogBinding.btnAgreementAgree.setOnClickListener { exitDialog() }
        containerView.setOnClickListener { exitDialog() }
        cardView.setOnClickListener { } // 阻止点击卡片关闭

        dialog.show()

        // 入场动画
        cardView.post {
            cardView.translationY = cardView.height.toFloat() * 1.2f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    private fun showChangelogDialog() {
        showReleaseHistoryDialog()
    }

    private fun showReleaseHistoryDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val containerView = layoutInflater.inflate(R.layout.dialog_update_links, null)
        dialog.setContentView(containerView)

        val cardView = containerView.findViewById<View>(R.id.dialogCard)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window.setDimAmount(0f)
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        val tvTitle = containerView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = containerView.findViewById<TextView>(R.id.tvSubtitle)
        val tvBody = containerView.findViewById<TextView>(R.id.tvBody)
        val rvLinks = containerView.findViewById<RecyclerView>(R.id.rvLinks)
        val scrollBody = containerView.findViewById<View>(R.id.scrollBody)

        tvTitle.text = "更新日志"
        tvSubtitle.text = "${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}"
        tvBody.text = ""
        scrollBody.visibility = View.GONE
        rvLinks.visibility = View.GONE

        containerView.findViewById<View>(R.id.btnOpenRelease).visibility = View.GONE
        containerView.findViewById<View>(R.id.btnHistory).visibility = View.GONE

        val historyView = LayoutInflater.from(this).inflate(R.layout.dialog_release_history, null, false)
        val container = (rvLinks.parent as? ViewGroup)
        container?.addView(
            historyView,
            container.indexOfChild(rvLinks)
        )

        val tvTips = historyView.findViewById<TextView>(R.id.tvTips)
        tvTips.text = "下方可以选择历史版本"

        val switchPrerelease = historyView.findViewById<SwitchMaterial>(R.id.switchPrerelease)
        val progress = historyView.findViewById<ProgressBar>(R.id.progress)
        val tvError = historyView.findViewById<TextView>(R.id.tvError)
        val recycler = historyView.findViewById<RecyclerView>(R.id.recyclerReleases)

        recycler.layoutManager = LinearLayoutManager(this)

        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = cardView,
            scrollBody = null,
            listView = recycler,
            hasList = true,
        )

        var includePrerelease = false
        var loaded: List<ReleaseEntry> = emptyList()

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

        fun exitDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(450)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnClose).setOnClickListener { exitDialog() }
        containerView.setOnClickListener { exitDialog() }
        cardView.setOnClickListener { }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }

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
            .setMessage((entry.body.ifBlank { "（无更新说明）" }) + localReleaseHighlights())
            .setPositiveButton("打开发布") { _, _ ->
                ReleaseUiUtil.openUrl(this, entry.releaseUrl)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun maybeShowUpdateDialogFromIntent() {
        val show = intent?.getBooleanExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false) == true
        if (!show) return
        intent?.putExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false)

        val cached = UpdateStore.loadLatest(this)
        if (cached != null) {
            showUpdateLinksDialog(cached)
            return
        }

        checkForUpdates(showLinksDialogIfNew = true)
    }

    private fun showUpdateLinksDialog(entry: ReleaseEntry) {
        val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
        val links = if (options.isNotEmpty()) options else listOf("发布页" to entry.releaseUrl)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val containerView = layoutInflater.inflate(R.layout.dialog_update_links, null)
        dialog.setContentView(containerView)

        val cardView = containerView.findViewById<View>(R.id.dialogCard)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window.setDimAmount(0f)
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        val tvTitle = containerView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = containerView.findViewById<TextView>(R.id.tvSubtitle)
        val tvBody = containerView.findViewById<TextView>(R.id.tvBody)
        val rvLinks = containerView.findViewById<RecyclerView>(R.id.rvLinks)
        val scrollBody = containerView.findViewById<View>(R.id.scrollBody)

        tvTitle.text = "发现新版本 ${entry.versionTag}"
        tvSubtitle.text = "${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}  •  ${UpdateConfig.APK_ASSET_NAME}"
        tvBody.text = (entry.body.ifBlank { "（无更新说明）" }) + localReleaseHighlights()

        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = cardView,
            scrollBody = scrollBody,
            listView = rvLinks,
            hasList = true,
        )

        rvLinks.layoutManager = LinearLayoutManager(this)

        rvLinks.adapter =
            UpdateLinkAdapter(
                items = links,
                onOpen = { ReleaseUiUtil.openUrl(this@AboutActivity, it) },
                onCopy = {
                    copyToClipboard(it)
                    Toast.makeText(this@AboutActivity, "链接已复制", Toast.LENGTH_SHORT).show()
                },
            )

        fun exitDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(450)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnClose).setOnClickListener { exitDialog() }
        containerView.setOnClickListener { exitDialog() }
        cardView.setOnClickListener { }

        containerView.findViewById<View>(R.id.btnOpenRelease).setOnClickListener {
            exitDialog()
            ReleaseUiUtil.openUrl(this, entry.releaseUrl)
        }
        containerView.findViewById<View>(R.id.btnHistory).setOnClickListener {
            exitDialog()
            showReleaseHistoryDialog()
        }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
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

    private fun checkForUpdates(showLinksDialogIfNew: Boolean = false) {
        val currentVersion = getInstalledVersion().versionName

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { releaseRepo.fetchLatestReleaseResilient(includePrerelease = false) }
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

                    val latestVersion = normalizeTag(latest.versionTag)
                    val newer = VersionComparator.compare(latestVersion, currentVersion) > 0
                    if (newer) {
                        UpdateStore.saveLatest(this@AboutActivity, latest)
                        if (showLinksDialogIfNew) {
                            showUpdateLinksDialog(latest)
                        } else {
                            showUpdateLinksDialog(latest)
                        }
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

        val body = licenses.joinToString("\n\n") { lic ->
            "${lic.name}\n${lic.description}\n许可: ${lic.license}"
        }

        showSimpleSlideDialog(
            title = "开源许可声明",
            subtitle = "本项目使用的第三方库",
            body = body,
        )
    }

    private fun showSimpleSlideDialog(title: String, subtitle: String, body: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val containerView = layoutInflater.inflate(R.layout.dialog_update_links, null)
        dialog.setContentView(containerView)

        val cardView = containerView.findViewById<View>(R.id.dialogCard)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window.setDimAmount(0f)
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        val tvTitle = containerView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = containerView.findViewById<TextView>(R.id.tvSubtitle)
        val tvBody = containerView.findViewById<TextView>(R.id.tvBody)
        val rvLinks = containerView.findViewById<RecyclerView>(R.id.rvLinks)
        val scrollBody = containerView.findViewById<View>(R.id.scrollBody)

        tvTitle.text = title
        tvSubtitle.text = subtitle
        tvBody.text = body

        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = cardView,
            scrollBody = scrollBody,
            listView = null,
            hasList = false,
        )

        rvLinks.visibility = View.GONE
        containerView.findViewById<View>(R.id.btnHistory).visibility = View.GONE
        containerView.findViewById<View>(R.id.btnOpenRelease).visibility = View.GONE

        fun exitDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(450)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnClose).setOnClickListener { exitDialog() }
        containerView.setOnClickListener { exitDialog() }
        cardView.setOnClickListener { }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
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
