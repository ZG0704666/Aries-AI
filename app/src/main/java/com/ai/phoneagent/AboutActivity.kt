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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Html
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.databinding.ActivityAboutBinding
import com.ai.phoneagent.updates.ApkDownloadUtil
import com.ai.phoneagent.updates.DialogSizingUtil
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.updates.ReleaseHistoryAdapter
import com.ai.phoneagent.updates.ReleaseRepository
import com.ai.phoneagent.updates.ReleaseUiUtil
import com.ai.phoneagent.updates.UpdateConfig
import com.ai.phoneagent.updates.UpdateLinkAdapter
import com.ai.phoneagent.updates.UpdateNotificationUtil
import com.ai.phoneagent.updates.UpdateStore
import com.ai.phoneagent.updates.VersionComparator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private val releaseRepo = ReleaseRepository()
    private var isCheckingUpdates = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupToolbar()
        setupClickListeners()
        binding.tvAppVersion.text = getString(R.string.about_version_format, currentVersionName())

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

    private fun currentVersionName(): String {
        val fromBuildConfig = BuildConfig.VERSION_NAME?.trim().orEmpty()
        if (fromBuildConfig.isNotBlank()) return fromBuildConfig

        return try {
            packageManager.getPackageInfo(packageName, 0).versionName?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun animateEntrance() {
        val cards = listOf(binding.cardAppInfo, binding.cardActions, binding.cardDeveloper)
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 40f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520)
                .setStartDelay(90L * index)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applySpringScaleEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(340)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .start()
                }
            }
            false
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val controller = WindowCompat.getInsetsController(window, binding.root)
            controller.isAppearanceLightStatusBars = resources.getBoolean(R.bool.m3t_light_system_bars)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.setPadding(0, sys.top, 0, 0)
            insets
        }
    }

    private fun setupToolbar() {
        // 新布局中返回按钮ID为 btnBack
        androidx.core.widget.ImageViewCompat.setImageTintList(
            binding.btnBack,
            android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.m3t_on_surface_variant)
            ),
        )
        binding.btnBack.setOnClickListener {
            vibrateLight()
            finish()
        }
    }

    private fun setupClickListeners() {
        // 应用点击缩放动效
        listOf(
            binding.btnCheckUpdate,
            binding.itemChangelog,
            binding.itemUserAgreement,
            binding.itemLicenses,
            binding.itemWebsite,
            binding.itemDeveloper,
            binding.itemContact,
        ).forEach { applySpringScaleEffect(it) }

        // 检查更新
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

        // 官网入口
        binding.itemWebsite.setOnClickListener {
            vibrateLight()
            openUrl(getString(R.string.about_website_url))
        }

        // 联系方式 - 点击复制邮箱
        binding.itemContact.setOnClickListener {
            vibrateLight()
            copyToClipboard("zhangyongqi@njit.edu.cn")
            Toast.makeText(this, R.string.about_contact_copied, Toast.LENGTH_SHORT).show()
        }

        // 开发者
        binding.itemDeveloper.setOnClickListener {
            vibrateLight()
            Toast.makeText(this, R.string.about_thanks, Toast.LENGTH_SHORT).show()
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
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
        }

        // 应用自适应高度
        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = dialogBinding.cardAgreement,
            scrollBody = dialogBinding.scrollAgreement,
            listView = null,
            hasList = false,
        )

        val content = getString(R.string.user_agreement_content)
        dialogBinding.tvAgreementContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(content)
        }
        dialogBinding.btnAgreementAgree.text = getString(R.string.action_close)

        fun closeDialog() {
            vibrateLight()
            dialogBinding.cardAgreement.animate()
                .translationY(dialogBinding.cardAgreement.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(420)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        dialogBinding.btnAgreementAgree.setOnClickListener { closeDialog() }
        dialogBinding.dialogContainer.setOnClickListener { closeDialog() }
        dialogBinding.cardAgreement.setOnClickListener { }

        dialog.show()

        // 入场动画
        dialogBinding.cardAgreement.post {
            dialogBinding.cardAgreement.translationY = dialogBinding.cardAgreement.height.toFloat() * 1.2f
            dialogBinding.cardAgreement.alpha = 0f
            dialogBinding.cardAgreement.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(560)
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
        val tvTitle = containerView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = containerView.findViewById<TextView>(R.id.tvSubtitle)
        val tvBody = containerView.findViewById<TextView>(R.id.tvBody)
        val rvLinks = containerView.findViewById<RecyclerView>(R.id.rvLinks)
        val scrollBody = containerView.findViewById<View>(R.id.scrollBody)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0f)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        tvTitle.text = getString(R.string.m3t_updates_title)
        tvSubtitle.text = "${UpdateConfig.REPO_OWNER}/${UpdateConfig.REPO_NAME}"
        tvBody.text = ""
        scrollBody.visibility = View.GONE
        rvLinks.visibility = View.GONE

        containerView.findViewById<View>(R.id.actionRow).visibility = View.GONE

        val historyView = LayoutInflater.from(this).inflate(R.layout.dialog_release_history, null, false)
        val parent = rvLinks.parent as? ViewGroup
        parent?.addView(historyView, parent.indexOfChild(rvLinks))

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

        val adapter = ReleaseHistoryAdapter(
            onDetails = { entry -> showReleaseDetails(entry) },
            onOpenRelease = { entry -> openReleaseUrlWithFeedback(entry.releaseUrl) },
            onDownload = { entry -> handleDownload(entry) },
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

        fun closeDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(420)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnClose).setOnClickListener { closeDialog() }
        containerView.setOnClickListener { closeDialog() }
        cardView.setOnClickListener { }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(560)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }

        tvError.visibility = View.GONE
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                releaseRepo.fetchReleasePage(page = 1, perPage = 20)
            }
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
        if (isFinishing || isDestroyed) return
        runCatching {
            MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
                .setTitle(entry.versionTag)
                .setMessage(entry.body.ifBlank { getString(R.string.m3t_updates_no_changelog) })
                .setPositiveButton(R.string.m3t_updates_open_release) { _, _ ->
                    openReleaseUrlWithFeedback(entry.releaseUrl)
                }
                .setNegativeButton(R.string.m3t_updates_close, null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.update_open_detail_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setCheckUpdateLoading(isLoading: Boolean) {
        isCheckingUpdates = isLoading
        binding.btnCheckUpdate.isEnabled = !isLoading
        binding.btnCheckUpdate.alpha = if (isLoading) 0.75f else 1f
        binding.btnCheckUpdate.text =
            if (isLoading) getString(R.string.about_checking_updates) else getString(R.string.about_check_updates)
    }

    private fun openReleaseUrlWithFeedback(url: String): Boolean {
        val opened = ReleaseUiUtil.openUrl(this, url)
        if (!opened) {
            Toast.makeText(this, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    private fun maybeShowUpdateDialogFromIntent() {
        val shouldShow = intent?.getBooleanExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false) == true
        if (!shouldShow) return
        intent?.putExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false)

        val cached = UpdateStore.loadLatest(this)
        if (cached != null) {
            showUpdateLinksDialog(cached)
            return
        }
        checkForUpdates()
    }

    private fun showUpdateLinksDialog(entry: ReleaseEntry) {
        val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
        val links = if (options.isNotEmpty()) options else listOf(
            getString(R.string.m3t_updates_view_release) to entry.releaseUrl,
        )

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val containerView = layoutInflater.inflate(R.layout.dialog_update_links, null)
        dialog.setContentView(containerView)

        val cardView = containerView.findViewById<View>(R.id.dialogCard)
        val tvTitle = containerView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = containerView.findViewById<TextView>(R.id.tvSubtitle)
        val tvBody = containerView.findViewById<TextView>(R.id.tvBody)
        val rvLinks = containerView.findViewById<RecyclerView>(R.id.rvLinks)
        val scrollBody = containerView.findViewById<View>(R.id.scrollBody)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0f)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        tvTitle.text = getString(R.string.m3t_updates_found) + " ${entry.versionTag}"
        tvSubtitle.text = getString(
            R.string.m3t_updates_repo_subtitle,
            UpdateConfig.REPO_OWNER,
            UpdateConfig.REPO_NAME,
            UpdateConfig.APK_ASSET_NAME,
        )
        tvBody.text = entry.body.ifBlank { getString(R.string.m3t_updates_no_changelog) }

        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = cardView,
            scrollBody = scrollBody,
            listView = rvLinks,
            hasList = true,
        )

        rvLinks.layoutManager = LinearLayoutManager(this)
        rvLinks.adapter = UpdateLinkAdapter(
            items = links,
            onOpen = { openReleaseUrlWithFeedback(it) },
            onCopy = {
                copyToClipboard(it)
                Toast.makeText(this@AboutActivity, R.string.about_link_copied, Toast.LENGTH_SHORT).show()
            },
        )

        fun closeDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(420)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnClose).setOnClickListener { closeDialog() }
        containerView.setOnClickListener { closeDialog() }
        cardView.setOnClickListener { }

        containerView.findViewById<View>(R.id.btnOpenRelease).setOnClickListener {
            closeDialog()
            openReleaseUrlWithFeedback(entry.releaseUrl)
        }

        containerView.findViewById<View>(R.id.btnHistory).setOnClickListener {
            closeDialog()
            showReleaseHistoryDialog()
        }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(560)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    private fun handleDownload(entry: ReleaseEntry) {
        runCatching {
            if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                val submitted = ApkDownloadUtil.enqueueApkDownload(this, entry)
                if (!submitted) {
                    Toast.makeText(this, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
                    openReleaseUrlWithFeedback(entry.releaseUrl)
                }
                return
            }

            if (entry.apkUrl.isNullOrBlank()) {
                Toast.makeText(this, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
                openReleaseUrlWithFeedback(entry.releaseUrl)
                return
            }

            val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
            if (options.isEmpty()) {
                Toast.makeText(this, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
                openReleaseUrlWithFeedback(entry.releaseUrl)
                return
            }
            if (options.size == 1) {
                openReleaseUrlWithFeedback(options.first().second)
                return
            }

            val names = options.map { it.first }.toTypedArray()
            MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
                .setTitle(R.string.m3t_updates_choose_source)
                .setItems(names) { _, which ->
                    openReleaseUrlWithFeedback(options[which].second)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
            openReleaseUrlWithFeedback(entry.releaseUrl)
        }
    }

    private fun checkForUpdates() {
        if (isCheckingUpdates) return
        val currentVersion = currentVersionName()
        setCheckUpdateLoading(true)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    releaseRepo.fetchLatestReleaseResilient(includePrerelease = false)
                }
                if (isFinishing || isDestroyed) return@launch

                val latest = result.getOrNull()
                val error = result.exceptionOrNull()
                if (error != null) {
                    MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_failed)
                        .setMessage(ReleaseUiUtil.formatError(error))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> showReleaseHistoryDialog() }
                        .show()
                    return@launch
                }

                if (latest == null) {
                    MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_updates)
                        .setMessage(R.string.about_no_release_found)
                        .setPositiveButton(R.string.action_ok, null)
                        .show()
                    return@launch
                }

                val newer = VersionComparator.compare(latest.version, currentVersion) > 0
                if (newer) {
                    UpdateStore.saveLatest(this@AboutActivity, latest)
                    showUpdateLinksDialog(latest)
                } else {
                    MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_up_to_date)
                        .setMessage(getString(R.string.about_current_version_format, currentVersion))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> showReleaseHistoryDialog() }
                        .show()
                }
            } catch (e: Throwable) {
                if (!isFinishing && !isDestroyed) {
                    MaterialAlertDialogBuilder(this@AboutActivity, R.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_failed)
                        .setMessage(ReleaseUiUtil.formatError(e))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> showReleaseHistoryDialog() }
                        .show()
                }
            } finally {
                if (!isDestroyed) {
                    setCheckUpdateLoading(false)
                }
            }
        }
    }

    private fun showLicensesDialog() {
        val licenses = listOf(
            License("AndroidX Core KTX", "Kotlin extensions for Android core libraries", "Apache-2.0"),
            License("AndroidX AppCompat", "Backward-compatible Android UI components", "Apache-2.0"),
            License("Material Components", "Material Design components for Android", "Apache-2.0"),
            License("AndroidX RecyclerView", "Efficient list display widget", "Apache-2.0"),
            License("AndroidX ConstraintLayout", "Flexible layout manager", "Apache-2.0"),
            License("AndroidX Lifecycle", "Lifecycle-aware components", "Apache-2.0"),
            License("AndroidX Work", "Background task scheduling", "Apache-2.0"),
            License("Kotlin Coroutines", "Asynchronous programming support", "Apache-2.0"),
            License("OkHttp", "HTTP client for Android and Java", "Apache-2.0"),
            License("OkHttp Logging Interceptor", "HTTP logging interceptor", "Apache-2.0"),
            License("Retrofit", "Type-safe HTTP client", "Apache-2.0"),
            License("Gson", "JSON serialization/deserialization library", "Apache-2.0"),
            License("Markwon Core", "Markdown rendering for Android", "Apache-2.0"),
            License("Markwon Extensions", "Tables, Strikethrough, Syntax highlight", "Apache-2.0"),
            License("sherpa-ncnn", "Offline speech recognition engine", "Apache-2.0"),
            License("JetBrains Annotations", "Annotations for Kotlin", "Apache-2.0"),
        )

        val containerView = layoutInflater.inflate(R.layout.dialog_licenses, null, false)
        val container = containerView.findViewById<LinearLayout>(R.id.licenseContainer)
        val cardView = containerView.findViewById<View>(R.id.cardLicenses)
        val scrollView = containerView.findViewById<View>(R.id.scrollLicenses)

        licenses.forEach { license ->
            val row = layoutInflater.inflate(R.layout.item_license_row, container, false)
            row.findViewById<TextView>(R.id.tvLibName).text = license.name
            row.findViewById<TextView>(R.id.tvLibDesc).text = license.description
            row.findViewById<TextView>(R.id.tvLibLicense).text =
                getString(R.string.m3t_license_format, license.license)
            container.addView(row)
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(containerView)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0f)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        DialogSizingUtil.applyCompactSizing(
            context = this,
            cardView = cardView,
            scrollBody = scrollView,
            listView = null,
            hasList = false,
        )

        fun closeDialog() {
            vibrateLight()
            cardView.animate()
                .translationY(cardView.height.toFloat() * 1.5f)
                .alpha(0f)
                .setDuration(420)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .withEndAction { dialog.dismiss() }
                .start()
        }

        containerView.findViewById<View>(R.id.btnCloseLicenses).setOnClickListener { closeDialog() }
        containerView.findViewById<View>(R.id.dialogContainer).setOnClickListener { closeDialog() }
        cardView.setOnClickListener { }

        dialog.show()

        cardView.post {
            cardView.translationY = -cardView.height.toFloat() * 1.5f
            cardView.alpha = 0f
            cardView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(560)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (_: Throwable) {
        }
    }

    private data class License(
        val name: String,
        val description: String,
        val license: String,
    )
}
