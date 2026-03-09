package com.ai.phoneagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.databinding.ActivityDrawerSettingsBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.system.applyMaterialCloseTransition
import com.ai.phoneagent.system.startActivityWithMaterialForwardTransition
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawerSettingsBinding
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }

    private val apiLastCheckKeyPref = "api_last_check_key"
    private val apiLastCheckOkPref = "api_last_check_ok"
    private val apiLastCheckTimePref = "api_last_check_time"
    private val apiLastCheckSigPref = "api_last_check_sig"
    private val apiUseThirdPartyPref = "api_use_third_party"
    private val apiThirdPartyBaseUrlPref = "api_third_party_base_url"
    private val apiThirdPartyModelPref = "api_third_party_model"
    private val apiUseLocalModelPref = "api_use_local_model"

    private var suppressApiInputWatcher = false
    private var suppressModelSwitchWatcher = false
    private var remoteApiOk: Boolean? = null
    private var remoteApiChecking: Boolean = false
    private var apiCheckSeq: Int = 0
    private var lastCheckedApiKey: String = ""
    private var qwenDownloadInFlight: Boolean = false
    private var localModelReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finishWithTransition() }
        binding.btnOpenAutomation.setOnClickListener {
            val intent = Intent(this, AutomationActivityNew::class.java)
            startActivityWithMaterialForwardTransition(intent)
        }
        binding.btnOpenAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivityWithMaterialForwardTransition(intent)
        }
        binding.btnGetApiKey.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys"),
                    ),
                )
            }
        }
        binding.btnPasteApiInput.setOnClickListener {
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
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
                return@setOnClickListener
            }
            binding.apiInput.tag = ""
            binding.apiInput.setText(pasted)
            binding.apiInput.setSelection(binding.apiInput.text?.length ?: 0)
            Toast.makeText(this, R.string.settings_api_key_pasted, Toast.LENGTH_SHORT).show()
        }
        binding.swUseThirdPartyApi.setOnCheckedChangeListener { _, checked ->
            if (suppressModelSwitchWatcher) return@setOnCheckedChangeListener
            binding.apiThirdPartyContainer.visibility =
                if (checked) android.view.View.VISIBLE else android.view.View.GONE
            prefs.edit().putBoolean(apiUseThirdPartyPref, checked).apply()
            onApiConfigChanged(clearApiValue = false)
        }
        binding.swUseLocalModel.setOnCheckedChangeListener { _, checked ->
            if (suppressModelSwitchWatcher) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(apiUseLocalModelPref, checked).apply()
            if (checked && !localModelReady) {
                Toast.makeText(this, R.string.m3t_sidebar_local_model_not_ready, Toast.LENGTH_LONG).show()
            }
            updateStatusText()
        }
        binding.apiInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppressApiInputWatcher) return
                    val displayed = s?.toString().orEmpty()
                    val tagKey = (binding.apiInput.tag as? String).orEmpty()
                    val savedKey = prefs.getString("api_key", "").orEmpty()
                    val isMaskedUnchanged = displayed.contains("*") && displayed == maskKey(tagKey)
                    if (isMaskedUnchanged && tagKey.isNotBlank() && tagKey == savedKey) return
                    if (displayed.isBlank()) {
                        onApiConfigChanged(clearApiValue = true)
                        return
                    }
                    onApiConfigChanged(clearApiValue = false)
                }
            },
        )
        val thirdPartyWatcher =
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppressApiInputWatcher || !binding.swUseThirdPartyApi.isChecked) return
                    prefs.edit()
                        .putString(apiThirdPartyBaseUrlPref, binding.apiBaseUrlInput.text?.toString()?.trim().orEmpty())
                        .putString(apiThirdPartyModelPref, binding.apiModelInput.text?.toString()?.trim().orEmpty())
                        .apply()
                    onApiConfigChanged(clearApiValue = false)
                }
            }
        binding.apiBaseUrlInput.addTextChangedListener(thirdPartyWatcher)
        binding.apiModelInput.addTextChangedListener(thirdPartyWatcher)
        binding.btnCheckApi.setOnClickListener {
            val key = resolveApiKeyFromInput()
            if (key.isBlank()) {
                Toast.makeText(this, R.string.settings_api_key_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("api_key", key).apply()
            binding.apiInput.tag = key
            suppressApiInputWatcher = true
            binding.apiInput.setText(maskKey(key))
            binding.apiInput.setSelection(binding.apiInput.text?.length ?: 0)
            suppressApiInputWatcher = false
            val baseUrl = resolveApiBaseUrl()
            val model = resolveApiModel()
            startApiCheck(key = key, baseUrl = baseUrl, model = model, force = true)
        }
        binding.btnDownloadQwenModel.setOnClickListener {
            if (qwenDownloadInFlight) return@setOnClickListener
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

        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
        restoreSettings()
    }

    override fun onResume() {
        super.onResume()
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
        restoreSettings()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        applyMaterialCloseTransition()
    }

    private fun finishWithTransition() {
        finish()
        applyMaterialCloseTransition()
    }

    private fun restoreSettings() {
        val saved = prefs.getString("api_key", "").orEmpty()
        suppressApiInputWatcher = true
        suppressModelSwitchWatcher = true
        binding.apiInput.tag = saved
        binding.apiInput.setText(maskKey(saved))
        binding.apiInput.setSelection(binding.apiInput.text?.length ?: 0)
        binding.swUseThirdPartyApi.isChecked = prefs.getBoolean(apiUseThirdPartyPref, false)
        binding.swUseLocalModel.isChecked = prefs.getBoolean(apiUseLocalModelPref, false)
        binding.apiThirdPartyContainer.visibility =
            if (binding.swUseThirdPartyApi.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        binding.apiBaseUrlInput.setText(
            prefs.getString(apiThirdPartyBaseUrlPref, AutoGlmClient.DEFAULT_BASE_URL),
        )
        binding.apiModelInput.setText(
            prefs.getString(apiThirdPartyModelPref, AutoGlmClient.DEFAULT_MODEL),
        )
        suppressApiInputWatcher = false
        suppressModelSwitchWatcher = false

        val lastSig = prefs.getString(apiLastCheckSigPref, "").orEmpty()
        val currentSig = apiConfigSignature(
            apiKey = saved,
            baseUrl = resolveApiBaseUrl(),
            model = resolveApiModel(),
        )
        if (saved.isNotBlank() && prefs.contains(apiLastCheckOkPref) && lastSig == currentSig) {
            remoteApiOk = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiChecking = false
            lastCheckedApiKey = saved
            binding.apiStatus.text =
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
            binding.apiStatus.text = getString(R.string.m3t_sidebar_api_not_checked)
        }
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    private fun updateQwenDownloadButtonState() {
        when {
            qwenDownloadInFlight -> {
                binding.btnDownloadQwenModel.isEnabled = false
                binding.btnDownloadQwenModel.text = getString(R.string.m3t_sidebar_qwen_download_preparing)
            }
            localModelReady -> {
                binding.btnDownloadQwenModel.isEnabled = true
                binding.btnDownloadQwenModel.text = getString(R.string.m3t_sidebar_qwen_download_ready)
            }
            else -> {
                binding.btnDownloadQwenModel.isEnabled = true
                binding.btnDownloadQwenModel.text = getString(R.string.m3t_sidebar_qwen_download)
            }
        }
    }

    private fun resolveApiKeyFromInput(): String {
        val displayed = binding.apiInput.text?.toString().orEmpty()
        val tagKey = (binding.apiInput.tag as? String).orEmpty().trim()
        val savedKey = prefs.getString("api_key", "").orEmpty().trim()
        return when {
            tagKey.isNotBlank() && displayed == maskKey(tagKey) -> tagKey
            savedKey.isNotBlank() && displayed == maskKey(savedKey) -> savedKey
            displayed.contains("*") && savedKey.isNotBlank() -> savedKey
            else -> displayed
        }.trim()
    }

    private fun resolveApiBaseUrl(): String {
        if (binding.swUseLocalModel.isChecked) return AutoGlmClient.DEFAULT_BASE_URL
        if (!binding.swUseThirdPartyApi.isChecked) return AutoGlmClient.DEFAULT_BASE_URL
        return binding.apiBaseUrlInput.text?.toString()?.trim().orEmpty().ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    private fun resolveApiModel(): String {
        if (binding.swUseLocalModel.isChecked) return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        if (!binding.swUseThirdPartyApi.isChecked) return AutoGlmClient.DEFAULT_MODEL
        return binding.apiModelInput.text?.toString()?.trim().orEmpty().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    private fun apiConfigSignature(apiKey: String, baseUrl: String, model: String): String {
        return "${if (binding.swUseThirdPartyApi.isChecked) "1" else "0"}|${apiKey.trim()}|${baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }}|${model.ifBlank { AutoGlmClient.DEFAULT_MODEL }}"
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
                .putBoolean(apiUseThirdPartyPref, binding.swUseThirdPartyApi.isChecked)
                .putBoolean(apiUseLocalModelPref, binding.swUseLocalModel.isChecked)
                .putString(apiThirdPartyBaseUrlPref, binding.apiBaseUrlInput.text?.toString()?.trim().orEmpty())
                .putString(apiThirdPartyModelPref, binding.apiModelInput.text?.toString()?.trim().orEmpty())
        if (clearApiValue) {
            editor.remove("api_key")
        }
        editor.apply()
        binding.apiStatus.text = getString(R.string.m3t_sidebar_api_not_checked)
        updateStatusText()
    }

    private fun startApiCheck(key: String, baseUrl: String, model: String, force: Boolean) {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val validationError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (validationError != null) {
            binding.apiStatus.text = getString(R.string.settings_api_unsafe)
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
            return
        }
        maybeWarnInsecureHttpBaseUrl(normalizedBaseUrl)
        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = key.trim()
        binding.apiStatus.text = getString(R.string.settings_api_checking)
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
            binding.apiStatus.text =
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
        binding.apiStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (remoteApiOk == true || binding.swUseLocalModel.isChecked) {
                    R.color.m3t_primary
                } else {
                    R.color.m3t_on_surface_variant
                },
            ),
        )
        if (binding.swUseLocalModel.isChecked) {
            binding.apiStatus.text =
                getString(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
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
