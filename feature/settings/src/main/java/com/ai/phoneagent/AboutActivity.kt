package com.ai.phoneagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.common.VersionComparator
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.prompt.MainChatPromptRepository
import com.ai.phoneagent.feature.settings.R
import com.ai.phoneagent.feature.updates.BuildConfig as UpdatesBuildConfig
import com.ai.phoneagent.system.applyMaterialCloseTransition
import com.ai.phoneagent.system.startActivityWithMaterialForwardTransition
import com.ai.phoneagent.ui.AboutScreen
import com.ai.phoneagent.updates.ApkDownloadUtil
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.updates.ReleaseRepository
import com.ai.phoneagent.updates.ReleaseUiUtil
import com.ai.phoneagent.updates.UpdateNotificationUtil
import com.ai.phoneagent.updates.UpdateStore
import com.ai.phoneagent.updates.UpdateHistoryActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : AppCompatActivity() {

    private val releaseRepo = ReleaseRepository()
    private var isCheckingUpdates = false
    private val localModelDownloadButtonVisiblePref = "local_model_download_button_visible"
    private val localModelDownloadToggleTapRequired = 5
    private val localModelDownloadToggleTapIntervalMs = 1200L
    private var developerTapCount = 0
    private var lastDeveloperTapAtMs = 0L
    private var checkUpdateButtonText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setCheckUpdateLoading(false)

        setContent {
            AriesMaterialTheme {
                AboutScreen(
                    appVersionText = getString(R.string.about_version_format, currentVersionName()),
                    promptVersionText =
                        getString(
                            R.string.about_prompt_version_format,
                            MainChatPromptRepository.getMainChatSystemPromptVersion(this),
                        ),
                    checkUpdateButtonText = checkUpdateButtonText,
                    onBack = {
                        vibrateLight()
                        finish()
                    },
                    onCheckUpdate = {
                        vibrateLight()
                        checkForUpdates()
                    },
                    onOpenChangelog = {
                        vibrateLight()
                        openUpdateHistory()
                    },
                    onOpenUserAgreement = {
                        vibrateLight()
                        startActivityWithMaterialForwardTransition(UserAgreementActivity.createViewIntent(this))
                    },
                    onOpenLicenses = {
                        vibrateLight()
                        showLicensesDialog()
                    },
                    onOpenWebsite = {
                        vibrateLight()
                        openUrl(getString(R.string.about_website_url))
                    },
                    onOpenSourceCode = {
                        vibrateLight()
                        openUrl(getString(R.string.about_source_code_url))
                    },
                    onCopyContact = {
                        vibrateLight()
                        copyToClipboard("zhangyongqi@njit.edu.cn")
                        Toast.makeText(this, R.string.about_contact_copied, Toast.LENGTH_SHORT).show()
                    },
                    onDeveloperTap = {
                        vibrateLight()
                        handleDeveloperTap()
                    },
                )
            }
        }

        maybeShowUpdateDialogFromIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
        }
        maybeShowUpdateDialogFromIntent()
    }

    override fun finish() {
        super.finish()
        applyMaterialCloseTransition()
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                resources.getBoolean(DesignSystemR.bool.m3t_light_system_bars)
        }
    }

    private fun currentVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName?.trim().orEmpty().removePrefix("v")
        } catch (_: Exception) {
            ""
        }
    }

    private fun openUpdateHistory() {
        startActivityWithMaterialForwardTransition(Intent(this, UpdateHistoryActivity::class.java))
    }

    private fun handleDeveloperTap() {
        val now = SystemClock.elapsedRealtime()
        developerTapCount =
            if (now - lastDeveloperTapAtMs <= localModelDownloadToggleTapIntervalMs) {
                developerTapCount + 1
            } else {
                1
            }
        lastDeveloperTapAtMs = now

        if (developerTapCount >= localModelDownloadToggleTapRequired) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val nextVisible = !prefs.getBoolean(localModelDownloadButtonVisiblePref, false)
            prefs.edit().putBoolean(localModelDownloadButtonVisiblePref, nextVisible).apply()
            developerTapCount = 0
            lastDeveloperTapAtMs = 0L
            Toast.makeText(
                this,
                if (nextVisible) {
                    R.string.about_local_model_download_button_shown
                } else {
                    R.string.about_local_model_download_button_hidden
                },
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(this, R.string.about_thanks, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLicensesDialog() {
        val licenses =
            listOf(
                License("AndroidX Core KTX", "Kotlin extensions for Android core libraries", "Apache-2.0"),
                License("AndroidX AppCompat", "Backward-compatible Android UI components", "Apache-2.0"),
                License("Material Components", "Material Design components for Android", "Apache-2.0"),
                License("AndroidX RecyclerView", "Efficient list display widget", "Apache-2.0"),
                License("AndroidX ConstraintLayout", "Flexible layout manager", "Apache-2.0"),
                License("AndroidX Lifecycle", "Lifecycle-aware components", "Apache-2.0"),
                License("AndroidX Work", "Background task scheduling", "Apache-2.0"),
                License("Kotlin Coroutines", "Asynchronous programming support", "Apache-2.0"),
                License("OkHttp", "HTTP client for Android and Java", "Apache-2.0"),
                License("Retrofit", "Type-safe HTTP client", "Apache-2.0"),
                License("Gson", "JSON serialization/deserialization library", "Apache-2.0"),
                License("Markwon Core", "Markdown rendering for Android", "Apache-2.0"),
                License("sherpa-ncnn", "Offline speech recognition engine", "Apache-2.0"),
            )
        val message =
            licenses.joinToString(separator = "\n\n") { license ->
                "${license.name}\n${license.description}\n${getString(R.string.m3t_license_format, license.license)}"
            }
        MaterialAlertDialogBuilder(this, DesignSystemR.style.BlueGlassAlertDialog)
            .setTitle(R.string.about_open_source_licenses)
            .setMessage(message)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun maybeShowUpdateDialogFromIntent() {
        val shouldShow = intent?.getBooleanExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false) == true
        if (!shouldShow) return
        intent?.putExtra(UpdateNotificationUtil.EXTRA_SHOW_UPDATE_DIALOG, false)

        val cached = UpdateStore.loadLatest(this)
        if (cached != null) {
            showUpdateFoundDialog(cached)
            return
        }
        checkForUpdates()
    }

    private fun showUpdateFoundDialog(entry: ReleaseEntry) {
        val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
        MaterialAlertDialogBuilder(this, DesignSystemR.style.BlueGlassAlertDialog)
            .setTitle(getString(R.string.m3t_updates_found) + " ${entry.versionTag}")
            .setMessage(entry.body.ifBlank { getString(R.string.m3t_updates_no_changelog) })
            .setPositiveButton(R.string.about_check_updates) { _, _ ->
                handleDownload(entry, options)
            }
            .setNeutralButton(R.string.about_changelog) { _, _ ->
                openUpdateHistory()
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun handleDownload(entry: ReleaseEntry, options: List<Pair<String, String>>) {
        runCatching {
            if (UpdatesBuildConfig.GITHUB_TOKEN.isNotBlank()) {
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
            if (options.isEmpty()) {
                openReleaseUrlWithFeedback(entry.releaseUrl)
                return
            }
            if (options.size == 1) {
                openReleaseUrlWithFeedback(options.first().second)
                return
            }

            MaterialAlertDialogBuilder(this, DesignSystemR.style.BlueGlassAlertDialog)
                .setTitle(R.string.m3t_updates_choose_source)
                .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                    openReleaseUrlWithFeedback(options[which].second)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
            openReleaseUrlWithFeedback(entry.releaseUrl)
        }
    }

    private fun setCheckUpdateLoading(isLoading: Boolean) {
        isCheckingUpdates = isLoading
        checkUpdateButtonText =
            if (isLoading) getString(R.string.about_checking_updates) else getString(R.string.about_check_updates)
    }

    private fun openReleaseUrlWithFeedback(url: String): Boolean {
        val opened = ReleaseUiUtil.openUrl(this, url)
        if (!opened) {
            Toast.makeText(this, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    private fun checkForUpdates() {
        if (isCheckingUpdates) return
        val currentVersion = currentVersionName()
        setCheckUpdateLoading(true)

        lifecycleScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        releaseRepo.fetchLatestReleaseResilient(includePrerelease = false)
                    }
                if (isFinishing || isDestroyed) return@launch

                val latest = result.getOrNull()
                val error = result.exceptionOrNull()
                if (error != null) {
                    MaterialAlertDialogBuilder(this@AboutActivity, DesignSystemR.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_failed)
                        .setMessage(ReleaseUiUtil.formatError(error))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> openUpdateHistory() }
                        .show()
                    return@launch
                }

                if (latest == null) {
                    MaterialAlertDialogBuilder(this@AboutActivity, DesignSystemR.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_updates)
                        .setMessage(R.string.about_no_release_found)
                        .setPositiveButton(R.string.action_ok, null)
                        .show()
                    return@launch
                }

                val newer = VersionComparator.compare(latest.version, currentVersion) > 0
                if (newer) {
                    UpdateStore.saveLatest(this@AboutActivity, latest)
                    showUpdateFoundDialog(latest)
                } else {
                    MaterialAlertDialogBuilder(this@AboutActivity, DesignSystemR.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_up_to_date)
                        .setMessage(getString(R.string.about_current_version_format, currentVersion))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> openUpdateHistory() }
                        .show()
                }
            } catch (e: Throwable) {
                if (!isFinishing && !isDestroyed) {
                    MaterialAlertDialogBuilder(this@AboutActivity, DesignSystemR.style.BlueGlassAlertDialog)
                        .setTitle(R.string.about_check_failed)
                        .setMessage(ReleaseUiUtil.formatError(e))
                        .setPositiveButton(R.string.action_ok, null)
                        .setNeutralButton(R.string.about_changelog) { _, _ -> openUpdateHistory() }
                        .show()
                }
            } finally {
                if (!isDestroyed) {
                    setCheckUpdateLoading(false)
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrateLight() {
        try {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
