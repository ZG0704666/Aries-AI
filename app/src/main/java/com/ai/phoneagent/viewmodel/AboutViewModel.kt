package com.ai.phoneagent.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.core.common.VersionComparator
import com.ai.phoneagent.core.prompt.MainChatPromptRepository
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.feature.settings.R
import com.ai.phoneagent.feature.updates.BuildConfig as UpdatesBuildConfig
import com.ai.phoneagent.updates.ApkDownloadUtil
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.updates.ReleaseRepository
import com.ai.phoneagent.updates.ReleaseUiUtil
import com.ai.phoneagent.updates.UpdateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class AboutUiState(
    val appVersionText: String = "",
    val promptVersionText: String = "",
    val checkUpdateButtonText: String = "",
    val isCheckingUpdates: Boolean = false,
    val showLicensesDialog: Boolean = false,
    val updateDialogState: UpdateDialogState? = null,
    val errorDialogState: ErrorDialogState? = null,
    val upToDateDialogState: UpToDateDialogState? = null,
    val downloadOptionsDialogState: DownloadOptionsDialogState? = null
)

data class UpdateDialogState(val entry: ReleaseEntry)
data class ErrorDialogState(val message: String)
data class UpToDateDialogState(val currentVersion: String)
data class DownloadOptionsDialogState(val entry: ReleaseEntry, val options: List<Pair<String, String>>)

class AboutViewModel(
    application: Application,
    private val releaseRepo: ReleaseRepository,
    private val prefs: AppPreferencesRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val localModelDownloadButtonVisiblePref = "local_model_download_button_visible"
    private val localModelDownloadToggleTapRequired = 5
    private val localModelDownloadToggleTapIntervalMs = 1200L
    private var developerTapCount = 0
    private var lastDeveloperTapAtMs = 0L

    // ─── Aries API 解锁计数 ──────────────────────────────────────────────────
    private val ariesApiUnlockTapRequired = 20
    private val ariesApiUnlockTapIntervalMs = 1200L
    private var aliasTapCount = 0
    private var lastAliasTapAtMs = 0L

    init {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                appVersionText = context.getString(R.string.about_version_format, currentVersionName(context)),
                promptVersionText = context.getString(
                    R.string.about_prompt_version_format,
                    MainChatPromptRepository.getMainChatSystemPromptVersion(context)
                ),
                checkUpdateButtonText = context.getString(R.string.about_check_updates)
            )
        }
    }

    private fun currentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim().orEmpty().removePrefix("v")
        } catch (_: Exception) {
            ""
        }
    }

    fun handleDeveloperTap() {
        val context = getApplication<Application>()
        val now = SystemClock.elapsedRealtime()
        developerTapCount = if (now - lastDeveloperTapAtMs <= localModelDownloadToggleTapIntervalMs) {
            developerTapCount + 1
        } else {
            1
        }
        lastDeveloperTapAtMs = now

        if (developerTapCount >= localModelDownloadToggleTapRequired) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val nextVisible = !prefs.getBoolean(localModelDownloadButtonVisiblePref, false)
            prefs.edit().putBoolean(localModelDownloadButtonVisiblePref, nextVisible).apply()
            developerTapCount = 0
            lastDeveloperTapAtMs = 0L
            Toast.makeText(
                context,
                if (nextVisible) R.string.about_local_model_download_button_shown else R.string.about_local_model_download_button_hidden,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, R.string.about_thanks, Toast.LENGTH_SHORT).show()
        }
    }

    /** 点击 xuanyu.xyla 别名文本 20 次切换 Aries API 区段可见性。 */
    fun handleAliasTap() {
        val context = getApplication<Application>()
        val now = SystemClock.elapsedRealtime()
        aliasTapCount = if (now - lastAliasTapAtMs <= ariesApiUnlockTapIntervalMs) {
            aliasTapCount + 1
        } else {
            1
        }
        lastAliasTapAtMs = now

        if (aliasTapCount >= ariesApiUnlockTapRequired) {
            aliasTapCount = 0
            lastAliasTapAtMs = 0L
            viewModelScope.launch {
                val current = prefs.getAriesApiSectionUnlocked()
                val next = !current
                prefs.setAriesApiSectionUnlocked(next)
                Toast.makeText(
                    context,
                    context.getString(
                        if (next) R.string.aries_api_section_unlocked
                        else R.string.aries_api_section_locked
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.isCheckingUpdates) return
        val context = getApplication<Application>()
        val currentVersion = currentVersionName(context)
        
        _uiState.update { 
            it.copy(
                isCheckingUpdates = true,
                checkUpdateButtonText = context.getString(R.string.about_checking_updates)
            ) 
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    releaseRepo.fetchLatestReleaseResilient(includePrerelease = false)
                }

                val latest = result.getOrNull()
                val error = result.exceptionOrNull()
                
                if (error != null) {
                    _uiState.update { it.copy(errorDialogState = ErrorDialogState(ReleaseUiUtil.formatError(error))) }
                    return@launch
                }

                if (latest == null) {
                    _uiState.update { it.copy(errorDialogState = ErrorDialogState(context.getString(R.string.about_no_release_found))) }
                    return@launch
                }

                val newer = VersionComparator.compare(latest.version, currentVersion) > 0
                if (newer) {
                    UpdateStore.saveLatest(context, latest)
                    _uiState.update { it.copy(updateDialogState = UpdateDialogState(latest)) }
                } else {
                    _uiState.update { it.copy(upToDateDialogState = UpToDateDialogState(currentVersion)) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorDialogState = ErrorDialogState(ReleaseUiUtil.formatError(e))) }
            } finally {
                _uiState.update { 
                    it.copy(
                        isCheckingUpdates = false,
                        checkUpdateButtonText = context.getString(R.string.about_check_updates)
                    ) 
                }
            }
        }
    }

    fun handleDownload(entry: ReleaseEntry) {
        val context = getApplication<Application>()
        val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
        
        runCatching {
            if (UpdatesBuildConfig.GITHUB_TOKEN.isNotBlank()) {
                val submitted = ApkDownloadUtil.enqueueApkDownload(context, entry)
                if (!submitted) {
                    Toast.makeText(context, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
                    openReleaseUrlWithFeedback(entry.releaseUrl)
                }
                return
            }

            if (entry.apkUrl.isNullOrBlank()) {
                Toast.makeText(context, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
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

            _uiState.update { it.copy(downloadOptionsDialogState = DownloadOptionsDialogState(entry, options)) }
        }.onFailure {
            Toast.makeText(context, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
            openReleaseUrlWithFeedback(entry.releaseUrl)
        }
    }

    fun openReleaseUrlWithFeedback(url: String): Boolean {
        val context = getApplication<Application>()
        val opened = ReleaseUiUtil.openUrl(context, url)
        if (!opened) {
            Toast.makeText(context, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    fun showLicensesDialog() {
        _uiState.update { it.copy(showLicensesDialog = true) }
    }

    fun dismissLicensesDialog() {
        _uiState.update { it.copy(showLicensesDialog = false) }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateDialogState = null) }
    }

    fun dismissErrorDialog() {
        _uiState.update { it.copy(errorDialogState = null) }
    }

    fun dismissUpToDateDialog() {
        _uiState.update { it.copy(upToDateDialogState = null) }
    }

    fun dismissDownloadOptionsDialog() {
        _uiState.update { it.copy(downloadOptionsDialogState = null) }
    }
}
