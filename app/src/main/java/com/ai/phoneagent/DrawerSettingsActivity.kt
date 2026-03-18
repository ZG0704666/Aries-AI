package com.ai.phoneagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.system.applyMaterialCloseTransition
import com.ai.phoneagent.system.startActivityWithMaterialForwardTransition
import com.ai.phoneagent.ui.settings.DrawerModelApiConfigScreen
import com.ai.phoneagent.ui.settings.DrawerSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawerSettingsActivity : AppCompatActivity() {

    private enum class SettingsPage {
        Home,
        ModelApi,
    }

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }

    private val apiLastCheckKeyPref = "api_last_check_key"
    private val apiLastCheckOkPref = "api_last_check_ok"
    private val apiLastCheckTimePref = "api_last_check_time"
    private val apiLastCheckSigPref = "api_last_check_sig"
    private val apiUseThirdPartyPref = "api_use_third_party"
    private val apiThirdPartyBaseUrlPref = "api_third_party_base_url"
    private val apiThirdPartyModelPref = "api_third_party_model"
    private val apiUseLocalModelPref = "api_use_local_model"

    private var remoteApiOk: Boolean? = null
    private var remoteApiChecking: Boolean = false
    private var apiCheckSeq: Int = 0
    private var lastCheckedApiKey: String = ""
    private var qwenDownloadInFlight: Boolean = false
    private var localModelReady: Boolean = false
    private var currentPage by mutableStateOf(SettingsPage.Home)
    private var pageTransitionForward by mutableStateOf(true)

    private var apiInputText by mutableStateOf("")
    private var apiInputTag by mutableStateOf("")
    private var useThirdPartyApi by mutableStateOf(false)
    private var useLocalModel by mutableStateOf(false)
    private var apiBaseUrlText by mutableStateOf("")
    private var apiModelText by mutableStateOf("")
    private var apiStatusText by mutableStateOf("")
    private var apiStatusPositive by mutableStateOf(false)
    private var qwenButtonText by mutableStateOf("")
    private var qwenButtonEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
        restoreSettings()

        setContent {
            AriesMaterialTheme {
                if (currentPage == SettingsPage.ModelApi) {
                    BackHandler {
                        pageTransitionForward = false
                        currentPage = SettingsPage.Home
                    }
                }
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (pageTransitionForward) {
                            slideInHorizontally(
                                animationSpec = tween(260),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = tween(220)) togetherWith
                                slideOutHorizontally(
                                    animationSpec = tween(260),
                                    targetOffsetX = { -it },
                                ) + fadeOut(animationSpec = tween(220))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(260),
                                initialOffsetX = { -it },
                            ) + fadeIn(animationSpec = tween(220)) togetherWith
                                slideOutHorizontally(
                                    animationSpec = tween(260),
                                    targetOffsetX = { it },
                                ) + fadeOut(animationSpec = tween(220))
                        }
                    },
                    label = "settingsPageTransition",
                ) { page ->
                    when (page) {
                        SettingsPage.Home -> {
                            DrawerSettingsScreen(
                                onBack = { finishWithTransition() },
                                onOpenAppearance = {
                                    Toast.makeText(
                                        this@DrawerSettingsActivity,
                                        R.string.settings_appearance_coming_soon,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onOpenModelApi = {
                                    pageTransitionForward = true
                                    currentPage = SettingsPage.ModelApi
                                },
                                onOpenAutomation = {
                                    startActivityWithMaterialForwardTransition(
                                        Intent(this@DrawerSettingsActivity, AutomationActivityNew::class.java),
                                    )
                                },
                                onOpenAbout = {
                                    startActivityWithMaterialForwardTransition(
                                        Intent(this@DrawerSettingsActivity, AboutActivity::class.java),
                                    )
                                },
                            )
                        }

                        SettingsPage.ModelApi -> {
                            DrawerModelApiConfigScreen(
                                apiInput = apiInputText,
                                useThirdPartyApi = useThirdPartyApi,
                                useLocalModel = useLocalModel,
                                apiBaseUrl = apiBaseUrlText,
                                apiModel = apiModelText,
                                apiStatus = apiStatusText,
                                apiStatusPositive = apiStatusPositive,
                                qwenButtonText = qwenButtonText,
                                qwenButtonEnabled = qwenButtonEnabled,
                                onBack = {
                                    pageTransitionForward = false
                                    currentPage = SettingsPage.Home
                                },
                                onApiInputChange = { value ->
                                    apiInputTag = ""
                                    apiInputText = value
                                    if (value.isBlank()) {
                                        onApiConfigChanged(clearApiValue = true)
                                    } else {
                                        onApiConfigChanged(clearApiValue = false)
                                    }
                                },
                                onPasteApi = { pasteApiKey() },
                                onOpenApiKeyPage = { openApiKeyPage() },
                                onUseThirdPartyChange = { checked ->
                                    useThirdPartyApi = checked
                                    onApiConfigChanged(clearApiValue = false)
                                },
                                onApiBaseUrlChange = { value ->
                                    apiBaseUrlText = value
                                    if (useThirdPartyApi) {
                                        onApiConfigChanged(clearApiValue = false)
                                    }
                                },
                                onApiModelChange = { value ->
                                    apiModelText = value
                                    if (useThirdPartyApi) {
                                        onApiConfigChanged(clearApiValue = false)
                                    }
                                },
                                onUseLocalModelChange = { checked ->
                                    useLocalModel = checked
                                    prefs.edit().putBoolean(apiUseLocalModelPref, checked).apply()
                                    if (checked && !localModelReady) {
                                        Toast.makeText(
                                            this@DrawerSettingsActivity,
                                            R.string.m3t_sidebar_local_model_not_ready,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    updateStatusText()
                                },
                                onCheckApi = { checkApiConnection() },
                                onDownloadQwenModel = { enqueueQwenDownloads() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
        restoreSettings()
    }

    override fun onBackPressed() {
        if (currentPage == SettingsPage.ModelApi) {
            pageTransitionForward = false
            currentPage = SettingsPage.Home
            return
        }
        super.onBackPressed()
        applyMaterialCloseTransition()
    }

    private fun finishWithTransition() {
        finish()
        applyMaterialCloseTransition()
    }

    private fun pasteApiKey() {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val pasted =
            clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
                .orEmpty()
        if (pasted.isBlank()) {
            Toast.makeText(this, R.string.settings_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        apiInputTag = ""
        apiInputText = pasted
        Toast.makeText(this, R.string.settings_api_key_pasted, Toast.LENGTH_SHORT).show()
        onApiConfigChanged(clearApiValue = false)
    }

    private fun openApiKeyPage() {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys"),
                ),
            )
        }
    }

    private fun restoreSettings() {
        val saved = prefs.getString("api_key", "").orEmpty()
        apiInputTag = saved
        apiInputText = maskKey(saved)
        useThirdPartyApi = prefs.getBoolean(apiUseThirdPartyPref, false)
        useLocalModel = prefs.getBoolean(apiUseLocalModelPref, false)
        apiBaseUrlText = prefs.getString(apiThirdPartyBaseUrlPref, AutoGlmClient.DEFAULT_BASE_URL).orEmpty()
        apiModelText = prefs.getString(apiThirdPartyModelPref, AutoGlmClient.DEFAULT_MODEL).orEmpty()

        val lastSig = prefs.getString(apiLastCheckSigPref, "").orEmpty()
        val currentSig =
            apiConfigSignature(
                apiKey = saved,
                baseUrl = resolveApiBaseUrl(),
                model = resolveApiModel(),
            )
        if (saved.isNotBlank() && prefs.contains(apiLastCheckOkPref) && lastSig == currentSig) {
            remoteApiOk = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiChecking = false
            lastCheckedApiKey = saved
            apiStatusText =
                getString(
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
            apiStatusText = getString(R.string.m3t_sidebar_api_not_checked)
        }
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    private fun updateQwenDownloadButtonState() {
        when {
            qwenDownloadInFlight -> {
                qwenButtonEnabled = false
                qwenButtonText = getString(R.string.m3t_sidebar_qwen_download_preparing)
            }
            localModelReady -> {
                qwenButtonEnabled = true
                qwenButtonText = getString(R.string.m3t_sidebar_qwen_download_ready)
            }
            else -> {
                qwenButtonEnabled = true
                qwenButtonText = getString(R.string.m3t_sidebar_qwen_download)
            }
        }
    }

    private fun resolveApiKeyFromInput(): String {
        val displayed = apiInputText
        val tagKey = apiInputTag.trim()
        val savedKey = prefs.getString("api_key", "").orEmpty().trim()
        return when {
            tagKey.isNotBlank() && displayed == maskKey(tagKey) -> tagKey
            savedKey.isNotBlank() && displayed == maskKey(savedKey) -> savedKey
            displayed.contains("*") && savedKey.isNotBlank() -> savedKey
            else -> displayed
        }.trim()
    }

    private fun resolveApiBaseUrl(): String {
        if (useLocalModel) return AutoGlmClient.DEFAULT_BASE_URL
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_BASE_URL
        return apiBaseUrlText.trim().ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    private fun resolveApiModel(): String {
        if (useLocalModel) return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_MODEL
        return apiModelText.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    private fun apiConfigSignature(apiKey: String, baseUrl: String, model: String): String {
        return "${if (useThirdPartyApi) "1" else "0"}|${apiKey.trim()}|${baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }}|${model.ifBlank { AutoGlmClient.DEFAULT_MODEL }}"
    }

    private fun onApiConfigChanged(clearApiValue: Boolean) {
        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        val editor =
            prefs.edit()
                .remove(apiLastCheckSigPref)
                .remove(apiLastCheckKeyPref)
                .remove(apiLastCheckOkPref)
                .remove(apiLastCheckTimePref)
                .putBoolean(apiUseThirdPartyPref, useThirdPartyApi)
                .putBoolean(apiUseLocalModelPref, useLocalModel)
                .putString(apiThirdPartyBaseUrlPref, apiBaseUrlText.trim())
                .putString(apiThirdPartyModelPref, apiModelText.trim())
        if (clearApiValue) {
            editor.remove("api_key")
        }
        editor.apply()
        apiStatusText = getString(R.string.m3t_sidebar_api_not_checked)
        updateStatusText()
    }

    private fun checkApiConnection() {
        if (useLocalModel) {
            localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
            updateQwenDownloadButtonState()
            apiStatusPositive = localModelReady
            apiStatusText =
                getString(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
            if (!localModelReady) {
                Toast.makeText(this, R.string.m3t_sidebar_local_model_not_ready, Toast.LENGTH_LONG).show()
            }
            return
        }
        val key = resolveApiKeyFromInput()
        if (key.isBlank()) {
            Toast.makeText(this, R.string.settings_api_key_required, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("api_key", key).apply()
        apiInputTag = key
        apiInputText = maskKey(key)
        val baseUrl = resolveApiBaseUrl()
        val model = resolveApiModel()
        startApiCheck(key = key, baseUrl = baseUrl, model = model, force = true)
    }

    private fun enqueueQwenDownloads() {
        if (qwenDownloadInFlight) return
        qwenDownloadInFlight = true
        updateQwenDownloadButtonState()
        lifecycleScope.launch {
            val result = ModelScopeModelDownloader.enqueueQwen35Downloads(this@DrawerSettingsActivity)
            qwenDownloadInFlight = false
            result.onSuccess {
                localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this@DrawerSettingsActivity)
                updateQwenDownloadButtonState()
                val message =
                    when {
                        it.enqueuedCount > 0 ->
                            getString(
                                R.string.m3t_sidebar_qwen_download_summary_format,
                                it.enqueuedCount,
                                it.skippedCount,
                                it.targetDir,
                            )
                        it.skippedCount > 0 -> getString(R.string.m3t_sidebar_qwen_download_cached)
                        else -> getString(R.string.m3t_sidebar_qwen_download_enqueued)
                    }
                Toast.makeText(this@DrawerSettingsActivity, message, Toast.LENGTH_LONG).show()
                updateStatusText()
            }.onFailure { err ->
                updateQwenDownloadButtonState()
                Toast.makeText(
                    this@DrawerSettingsActivity,
                    getString(
                        R.string.m3t_sidebar_qwen_download_failed_format,
                        err.message?.trim().orEmpty().ifBlank {
                            getString(R.string.update_download_failed_unknown)
                        },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun startApiCheck(key: String, baseUrl: String, model: String, force: Boolean) {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val validationError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (validationError != null) {
            apiStatusText = getString(R.string.settings_api_unsafe)
            apiStatusPositive = false
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
            return
        }
        maybeWarnInsecureHttpBaseUrl(normalizedBaseUrl)
        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = key.trim()
        apiStatusText = getString(R.string.settings_api_checking)
        updateStatusText()
        val seq = ++apiCheckSeq
        lifecycleScope.launch {
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
                getString(
                    if (result.ok) {
                        R.string.settings_api_available
                    } else {
                        R.string.settings_api_failed
                    },
                )
            prefs.edit()
                .putString("api_key", key.trim())
                .putString(apiLastCheckKeyPref, key.trim())
                .putBoolean(apiLastCheckOkPref, result.ok)
                .putLong(apiLastCheckTimePref, System.currentTimeMillis())
                .putString(apiLastCheckSigPref, apiConfigSignature(key.trim(), normalizedBaseUrl, model))
                .apply()
            if (!result.ok && force) {
                Toast.makeText(
                    this@DrawerSettingsActivity,
                    formatApiCheckFailureReason(result.statusCode, result.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
            updateStatusText()
        }
    }

    private fun updateStatusText() {
        apiStatusPositive = remoteApiOk == true || useLocalModel
        if (useLocalModel) {
            apiStatusText =
                getString(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
        } else if (!remoteApiChecking && remoteApiOk == null) {
            apiStatusText = getString(R.string.m3t_sidebar_api_not_checked)
        }
    }

    private fun validateBaseUrlSecurity(baseUrl: String): String? {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return getString(R.string.settings_api_invalid_url)
        }
        if (scheme != "https" && scheme != "http") {
            return getString(R.string.settings_api_invalid_scheme)
        }
        return null
    }

    private fun maybeWarnInsecureHttpBaseUrl(baseUrl: String) {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
        if (scheme == "http" && host !in localHosts) {
            Toast.makeText(this, R.string.settings_api_http_warning, Toast.LENGTH_LONG).show()
        }
    }

    private fun formatApiCheckFailureReason(statusCode: Int?, message: String?): String {
        val cleanMessage = message?.trim().orEmpty()
        return when {
            statusCode != null && cleanMessage.isNotBlank() ->
                getString(R.string.settings_api_failed_http_message, statusCode, cleanMessage)
            statusCode != null ->
                getString(R.string.settings_api_failed_http, statusCode)
            cleanMessage.isNotBlank() ->
                getString(R.string.settings_api_failed_message, cleanMessage)
            else ->
                getString(R.string.settings_api_failed_generic)
        }
    }

    private fun maskKey(raw: String): String {
        if (raw.length <= 8) return raw
        return raw.take(4) + "*".repeat(raw.length - 8) + raw.takeLast(4)
    }
}
