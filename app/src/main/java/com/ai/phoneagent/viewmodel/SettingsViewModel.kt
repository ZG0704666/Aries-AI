package com.ai.phoneagent.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.R
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ModelScopeModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    application: Application,
    private val prefs: AppPreferencesRepository,
) : AndroidViewModel(application) {

    enum class SettingsPage {
        Home,
        ModelApi,
        Appearance,
        About,
        Automation,
    }

    private var remoteApiOk: Boolean? = null
    private var remoteApiChecking: Boolean = false
    private var apiCheckSeq: Int = 0
    private var lastCheckedApiKey: String = ""
    private var qwenDownloadInFlight: Boolean = false
    private var apiInputTag: String = ""

    var localModelReady by mutableStateOf(false)
        private set

    var currentPage by mutableStateOf(SettingsPage.Home)
        private set

    var pageTransitionForward by mutableStateOf(true)
        private set

    var apiInputText by mutableStateOf("")
        private set

    var useThirdPartyApi by mutableStateOf(false)
        private set

    var useLocalModel by mutableStateOf(false)
        private set

    var apiBaseUrlText by mutableStateOf("")
        private set

    var apiModelText by mutableStateOf("")
        private set

    var apiStatusText by mutableStateOf("")
        private set

    var apiStatusPositive by mutableStateOf(false)
        private set

    var qwenButtonText by mutableStateOf("")
        private set

    var qwenButtonEnabled by mutableStateOf(true)
        private set

    init {
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(getApplication())
        restoreSettings()
    }

    fun restoreSettings() {
        val saved = prefs.getApiKeyBlocking()
        apiInputTag = saved
        apiInputText = maskKey(saved)
        useThirdPartyApi = prefs.getApiUseThirdPartyBlocking()
        useLocalModel = prefs.getApiUseLocalModelBlocking()
        apiBaseUrlText = prefs.getApiThirdPartyBaseUrlBlocking().ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        apiModelText = prefs.getApiThirdPartyModelBlocking().ifBlank { AutoGlmClient.DEFAULT_MODEL }

        val lastSig = prefs.getApiLastCheckSigBlocking()
        val currentSig =
            apiConfigSignature(
                apiKey = saved,
                baseUrl = resolveApiBaseUrl(),
                model = resolveApiModel(),
            )
        if (saved.isNotBlank() && prefs.hasApiLastCheckOkBlocking() && lastSig == currentSig) {
            remoteApiOk = prefs.getApiLastCheckOkBlocking()
            remoteApiChecking = false
            lastCheckedApiKey = saved
            apiStatusText =
                stringRes(
                    if (remoteApiOk == true) {
                        R.string.settings_api_available
                    } else {
                        R.string.settings_api_failed
                    },
                )
        } else {
            remoteApiOk = null
            remoteApiChecking = false
            lastCheckedApiKey = ""
            apiStatusText = stringRes(R.string.m3t_sidebar_api_not_checked)
        }
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    fun refreshLocalModelState() {
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(getApplication())
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    fun openModelApiPage() {
        pageTransitionForward = true
        currentPage = SettingsPage.ModelApi
    }

    fun openHomePage() {
        pageTransitionForward = false
        currentPage = SettingsPage.Home
    }

    fun navigateTo(page: SettingsPage) {
        pageTransitionForward = page != SettingsPage.Home
        currentPage = page
    }

    fun onApiInputChanged(value: String) {
        apiInputTag = ""
        apiInputText = value
        onApiConfigChanged(clearApiValue = value.isBlank())
    }

    fun onUseThirdPartyChange(checked: Boolean) {
        useThirdPartyApi = checked
        onApiConfigChanged(clearApiValue = false)
    }

    fun onApiBaseUrlChange(value: String) {
        apiBaseUrlText = value
        if (useThirdPartyApi) {
            onApiConfigChanged(clearApiValue = false)
        }
    }

    fun onApiModelChange(value: String) {
        apiModelText = value
        if (useThirdPartyApi) {
            onApiConfigChanged(clearApiValue = false)
        }
    }

    fun onUseLocalModelChange(checked: Boolean, onToast: (String) -> Unit) {
        useLocalModel = checked
        viewModelScope.launch { prefs.setApiUseLocalModel(checked) }
        if (checked && !localModelReady) {
            onToast(stringRes(R.string.m3t_sidebar_local_model_not_ready))
        }
        updateStatusText()
    }

    fun pasteApiKey(context: Context, onToast: (String) -> Unit) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val pasted =
            clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()
                .orEmpty()
        if (pasted.isBlank()) {
            onToast(stringRes(R.string.settings_clipboard_empty))
            return
        }
        apiInputTag = ""
        apiInputText = pasted
        onToast(stringRes(R.string.settings_api_key_pasted))
        onApiConfigChanged(clearApiValue = false)
    }

    fun openApiKeyPage(context: Context) {
        runCatching {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }

    fun onApiConfigChanged(clearApiValue: Boolean) {
        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        viewModelScope.launch {
            prefs.writeApiConfig(
                removeApiKey = clearApiValue,
                useThirdParty = useThirdPartyApi,
                useLocalModel = useLocalModel,
                thirdPartyBaseUrl = apiBaseUrlText.trim(),
                thirdPartyModel = apiModelText.trim(),
                clearCheckResults = true,
            )
        }
        apiStatusText = stringRes(R.string.m3t_sidebar_api_not_checked)
        updateStatusText()
    }

    fun checkApiConnection(onToast: (String) -> Unit) {
        if (useLocalModel) {
            localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(getApplication())
            updateQwenDownloadButtonState()
            apiStatusPositive = localModelReady
            apiStatusText =
                stringRes(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
            if (!localModelReady) {
                onToast(stringRes(R.string.m3t_sidebar_local_model_not_ready))
            }
            return
        }

        val key = resolveApiKeyFromInput()
        if (key.isBlank()) {
            onToast(stringRes(R.string.settings_api_key_required))
            return
        }

        viewModelScope.launch { prefs.setApiKey(key) }
        apiInputTag = key
        apiInputText = maskKey(key)
        val baseUrl = resolveApiBaseUrl()
        val model = resolveApiModel()
        startApiCheck(key = key, baseUrl = baseUrl, model = model, force = true, onToast = onToast)
    }

    fun enqueueQwenDownloads(onToast: (String) -> Unit) {
        if (qwenDownloadInFlight) return
        qwenDownloadInFlight = true
        updateQwenDownloadButtonState()
        viewModelScope.launch {
            val result = ModelScopeModelDownloader.enqueueQwen35Downloads(getApplication())
            qwenDownloadInFlight = false
            result.onSuccess {
                localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(getApplication())
                updateQwenDownloadButtonState()
                val message =
                    when {
                        it.enqueuedCount > 0 ->
                            stringRes(
                                R.string.m3t_sidebar_qwen_download_summary_format,
                                it.enqueuedCount,
                                it.skippedCount,
                                it.targetDir,
                            )
                        it.skippedCount > 0 -> stringRes(R.string.m3t_sidebar_qwen_download_cached)
                        else -> stringRes(R.string.m3t_sidebar_qwen_download_enqueued)
                    }
                onToast(message)
                updateStatusText()
            }.onFailure { err ->
                updateQwenDownloadButtonState()
                onToast(
                    stringRes(
                        R.string.m3t_sidebar_qwen_download_failed_format,
                        err.message?.trim().orEmpty().ifBlank {
                            stringRes(R.string.update_download_failed_unknown)
                        },
                    ),
                )
            }
        }
    }

    fun startApiCheck(
        key: String,
        baseUrl: String,
        model: String,
        force: Boolean,
        onToast: (String) -> Unit,
    ) {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val validationError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (validationError != null) {
            apiStatusText = stringRes(R.string.settings_api_unsafe)
            apiStatusPositive = false
            onToast(validationError)
            return
        }
        maybeWarnInsecureHttpBaseUrl(normalizedBaseUrl, onToast)
        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = key.trim()
        apiStatusText = stringRes(R.string.settings_api_checking)
        updateStatusText()

        val seq = ++apiCheckSeq
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    AutoGlmClient.checkApiDetailed(
                        apiKey = key.trim(),
                        baseUrl = normalizedBaseUrl,
                        model = model.ifBlank { AutoGlmClient.DEFAULT_MODEL },
                    )
                }
            if (seq != apiCheckSeq) return@launch

            remoteApiChecking = false
            remoteApiOk = result.ok
            apiStatusText =
                stringRes(
                    if (result.ok) {
                        R.string.settings_api_available
                    } else {
                        R.string.settings_api_failed
                    },
                )
            prefs.writeApiConfig(
                apiKey = key.trim(),
                lastCheckKey = key.trim(),
                lastCheckOk = result.ok,
                lastCheckTime = System.currentTimeMillis(),
                lastCheckSig = apiConfigSignature(key.trim(), normalizedBaseUrl, model),
            )
            if (!result.ok && force) {
                onToast(formatApiCheckFailureReason(result.statusCode, result.message))
            }
            updateStatusText()
        }
    }

    fun updateQwenDownloadButtonState() {
        when {
            qwenDownloadInFlight -> {
                qwenButtonEnabled = false
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download_preparing)
            }

            localModelReady -> {
                qwenButtonEnabled = true
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download_ready)
            }

            else -> {
                qwenButtonEnabled = true
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download)
            }
        }
    }

    fun updateStatusText() {
        apiStatusPositive = remoteApiOk == true || useLocalModel
        if (useLocalModel) {
            apiStatusText =
                stringRes(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
        } else if (!remoteApiChecking && remoteApiOk == null) {
            apiStatusText = stringRes(R.string.m3t_sidebar_api_not_checked)
        }
    }

    fun resolveApiKeyFromInput(): String {
        val displayed = apiInputText
        val tagKey = apiInputTag.trim()
        val savedKey = prefs.getApiKeyBlocking().trim()
        return when {
            tagKey.isNotBlank() && displayed == maskKey(tagKey) -> tagKey
            savedKey.isNotBlank() && displayed == maskKey(savedKey) -> savedKey
            displayed.contains("*") && savedKey.isNotBlank() -> savedKey
            else -> displayed
        }.trim()
    }

    fun resolveApiBaseUrl(): String {
        if (useLocalModel) return AutoGlmClient.DEFAULT_BASE_URL
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_BASE_URL
        return apiBaseUrlText.trim().ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    fun resolveApiModel(): String {
        if (useLocalModel) return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_MODEL
        return apiModelText.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    fun maskKey(raw: String): String {
        if (raw.length <= 8) return raw
        return raw.take(4) + "*".repeat(raw.length - 8) + raw.takeLast(4)
    }

    fun formatApiCheckFailureReason(statusCode: Int?, message: String?): String {
        val cleanMessage = message?.trim().orEmpty()
        return when {
            statusCode != null && cleanMessage.isNotBlank() ->
                stringRes(R.string.settings_api_failed_http_message, statusCode, cleanMessage)

            statusCode != null ->
                stringRes(R.string.settings_api_failed_http, statusCode)

            cleanMessage.isNotBlank() ->
                stringRes(R.string.settings_api_failed_message, cleanMessage)

            else ->
                stringRes(R.string.settings_api_failed_generic)
        }
    }

    fun apiConfigSignature(apiKey: String, baseUrl: String, model: String): String {
        return "${if (useThirdPartyApi) "1" else "0"}|${apiKey.trim()}|${baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }}|${model.ifBlank { AutoGlmClient.DEFAULT_MODEL }}"
    }

    fun validateBaseUrlSecurity(baseUrl: String): String? {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return stringRes(R.string.settings_api_invalid_url)
        }
        if (scheme != "https" && scheme != "http") {
            return stringRes(R.string.settings_api_invalid_scheme)
        }
        return null
    }

    fun maybeWarnInsecureHttpBaseUrl(baseUrl: String, onToast: (String) -> Unit) {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
        if (scheme == "http" && host !in localHosts) {
            onToast(stringRes(R.string.settings_api_http_warning))
        }
    }

    private fun stringRes(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
