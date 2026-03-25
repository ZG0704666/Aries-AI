package com.ai.phoneagent.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.feature.updates.BuildConfig
import com.ai.phoneagent.feature.updates.R
import com.ai.phoneagent.updates.ApkDownloadUtil
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.updates.ReleaseRepository
import com.ai.phoneagent.updates.ReleaseUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateHistoryUiState(
    val releases: List<ReleaseEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val includePrerelease: Boolean = false,
    val detailDialogState: ReleaseEntry? = null,
    val downloadOptionsDialogState: DownloadOptionsDialogState? = null
)

class UpdateHistoryViewModel(
    application: Application,
    private val repo: ReleaseRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdateHistoryUiState())
    val uiState: StateFlow<UpdateHistoryUiState> = _uiState.asStateFlow()

    init {
        loadPage(resetError = true)
    }

    fun setIncludePrerelease(include: Boolean) {
        _uiState.update { it.copy(includePrerelease = include, page = 1, releases = emptyList(), hasMore = true) }
        loadPage(resetError = true)
    }

    fun loadMore() {
        if (_uiState.value.loading || !_uiState.value.hasMore) return
        _uiState.update { it.copy(page = it.page + 1) }
        loadPage(resetError = false)
    }

    fun retry() {
        loadPage(resetError = true)
    }

    private fun loadPage(resetError: Boolean) {
        if (_uiState.value.loading) return

        _uiState.update { 
            it.copy(
                loading = true,
                error = if (resetError) null else it.error
            ) 
        }

        viewModelScope.launch {
            val currentPage = _uiState.value.page
            val includePrerelease = _uiState.value.includePrerelease
            
            val result = withContext(Dispatchers.IO) {
                repo.fetchReleasePage(page = currentPage, perPage = 20)
            }

            result.onSuccess { list ->
                val filtered = if (includePrerelease) list else list.filter { !it.isPrerelease }
                _uiState.update { state ->
                    val newReleases = if (currentPage == 1) filtered else state.releases + filtered
                    state.copy(
                        loading = false,
                        releases = newReleases,
                        hasMore = filtered.isNotEmpty(),
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        error = ReleaseUiUtil.formatError(e)
                    )
                }
            }
        }
    }

    fun showDetails(entry: ReleaseEntry) {
        _uiState.update { it.copy(detailDialogState = entry) }
    }

    fun dismissDetails() {
        _uiState.update { it.copy(detailDialogState = null) }
    }

    fun handleDownload(entry: ReleaseEntry) {
        val context = getApplication<Application>()
        runCatching {
            if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
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

            val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
            if (options.isEmpty()) {
                Toast.makeText(context, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, com.ai.phoneagent.feature.settings.R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    fun dismissDownloadOptionsDialog() {
        _uiState.update { it.copy(downloadOptionsDialogState = null) }
    }
}
