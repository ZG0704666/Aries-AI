package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_prefs")

class AppPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val autoglmApiKey = stringPreferencesKey("autoglm_api_key")
        val apiUseThirdParty = booleanPreferencesKey("api_use_third_party")
        val apiUseLocalModel = booleanPreferencesKey("api_use_local_model")
        val apiThirdPartyBaseUrl = stringPreferencesKey("api_third_party_base_url")
        val apiThirdPartyModel = stringPreferencesKey("api_third_party_model")
        val apiLastCheckKey = stringPreferencesKey("api_last_check_key")
        val apiLastCheckOk = booleanPreferencesKey("api_last_check_ok")
        val apiLastCheckTime = longPreferencesKey("api_last_check_time")
        val apiLastCheckSig = stringPreferencesKey("api_last_check_sig")
        val userAgreementAccepted = booleanPreferencesKey("user_agreement_accepted")
        val permGuideShown = booleanPreferencesKey("perm_guide_shown")
        val conversations = stringPreferencesKey("conversations")
        val qwenPendingDownloadIds = stringSetPreferencesKey("qwen_pending_download_ids")
    }

    val apiKeyFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.apiKey] ?: ""
        }

    val autoglmApiKeyFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.autoglmApiKey] ?: ""
        }

    val apiUseThirdPartyFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.apiUseThirdParty] ?: false
        }

    val apiUseLocalModelFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.apiUseLocalModel] ?: false
        }

    val apiThirdPartyBaseUrlFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.apiThirdPartyBaseUrl] ?: ""
        }

    val apiThirdPartyModelFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.apiThirdPartyModel] ?: ""
        }

    val userAgreementAcceptedFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.userAgreementAccepted] ?: false
        }

    val permGuideShownFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.permGuideShown] ?: false
        }

    val conversationsFlow: Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.conversations]
        }

    val qwenPendingDownloadIdsFlow: Flow<Set<String>> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.qwenPendingDownloadIds] ?: emptySet()
        }

    suspend fun getApiKey(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.apiKey] ?: ""
    }

    suspend fun setApiKey(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiKey] = value
        }
    }

    suspend fun getAutoglmApiKey(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.autoglmApiKey] ?: ""
    }

    suspend fun setAutoglmApiKey(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.autoglmApiKey] = value
        }
    }

    suspend fun setApiUseThirdParty(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiUseThirdParty] = value
        }
    }

    suspend fun setApiUseLocalModel(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiUseLocalModel] = value
        }
    }

    suspend fun setApiThirdPartyBaseUrl(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiThirdPartyBaseUrl] = value
        }
    }

    suspend fun setApiThirdPartyModel(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiThirdPartyModel] = value
        }
    }

    suspend fun getApiLastCheckKey(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.apiLastCheckKey] ?: ""
    }

    suspend fun setApiLastCheckKey(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiLastCheckKey] = value
        }
    }

    suspend fun getApiLastCheckOk(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.apiLastCheckOk] ?: false
    }

    suspend fun setApiLastCheckOk(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiLastCheckOk] = value
        }
    }

    suspend fun getApiLastCheckTime(): Long {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.apiLastCheckTime] ?: 0L
    }

    suspend fun setApiLastCheckTime(value: Long) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiLastCheckTime] = value
        }
    }

    suspend fun getApiLastCheckSig(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.apiLastCheckSig] ?: ""
    }

    suspend fun setApiLastCheckSig(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.apiLastCheckSig] = value
        }
    }

    suspend fun setUserAgreementAccepted(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.userAgreementAccepted] = value
        }
    }

    suspend fun setPermGuideShown(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.permGuideShown] = value
        }
    }

    suspend fun getConversations(): String? {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.conversations]
    }

    suspend fun setConversations(value: String?) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.conversations)
            } else {
                prefs[Keys.conversations] = value
            }
        }
    }

    suspend fun getQwenPendingDownloadIds(): Set<String> {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.qwenPendingDownloadIds] ?: emptySet()
    }

    suspend fun setQwenPendingDownloadIds(value: Set<String>) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.qwenPendingDownloadIds] = value
        }
    }
}
