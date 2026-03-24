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
import kotlinx.coroutines.runBlocking

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
        val legacyConversationsJson = stringPreferencesKey("conversations_json")
        val legacyActiveConversationId = longPreferencesKey("active_conversation_id")
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

    suspend fun getLegacyConversationsJson(): String? {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.legacyConversationsJson]
    }

    suspend fun setLegacyConversationsJson(value: String?) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.legacyConversationsJson)
            } else {
                prefs[Keys.legacyConversationsJson] = value
            }
        }
    }

    suspend fun getLegacyActiveConversationId(defaultValue: Long = -1L): Long {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.legacyActiveConversationId] ?: defaultValue
    }

    suspend fun setLegacyActiveConversationId(value: Long?) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.legacyActiveConversationId)
            } else {
                prefs[Keys.legacyActiveConversationId] = value
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

    // ─── Blocking helpers for non-coroutine call sites ───────────────────────

    /** Blocking snapshot of the entire prefs — use only from non-suspend call sites. */
    fun getApiKeyBlocking(): String = runBlocking { getApiKey() }
    fun getApiUseThirdPartyBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.apiUseThirdParty] ?: false
    }
    fun getApiUseLocalModelBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.apiUseLocalModel] ?: false
    }
    fun getApiThirdPartyBaseUrlBlocking(): String = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.apiThirdPartyBaseUrl] ?: ""
    }
    fun getApiThirdPartyModelBlocking(): String = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.apiThirdPartyModel] ?: ""
    }
    fun getApiLastCheckSigBlocking(): String = runBlocking { getApiLastCheckSig() }
    fun getApiLastCheckOkBlocking(): Boolean = runBlocking { getApiLastCheckOk() }
    fun hasApiLastCheckOkBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first().contains(Keys.apiLastCheckOk)
    }
    fun getUserAgreementAcceptedBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.userAgreementAccepted] ?: false
    }
    fun getPermGuideShownBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.permGuideShown] ?: false
    }
    fun getAutoglmApiKeyBlocking(): String = runBlocking { getAutoglmApiKey() }
    fun getApiLastCheckKeyBlocking(): String = runBlocking { getApiLastCheckKey() }
    fun getApiLastCheckTimeBlocking(): Long = runBlocking { getApiLastCheckTime() }
    fun getLegacyConversationsJsonBlocking(): String? = runBlocking { getLegacyConversationsJson() }
    fun getLegacyActiveConversationIdBlocking(defaultValue: Long = -1L): Long =
        runBlocking { getLegacyActiveConversationId(defaultValue) }

    fun setApiKeyBlocking(value: String) = runBlocking { setApiKey(value) }
    fun setApiUseThirdPartyBlocking(value: Boolean) = runBlocking { setApiUseThirdParty(value) }
    fun setApiUseLocalModelBlocking(value: Boolean) = runBlocking { setApiUseLocalModel(value) }
    fun setApiThirdPartyBaseUrlBlocking(value: String) = runBlocking { setApiThirdPartyBaseUrl(value) }
    fun setApiThirdPartyModelBlocking(value: String) = runBlocking { setApiThirdPartyModel(value) }
    fun setApiLastCheckKeyBlocking(value: String) = runBlocking { setApiLastCheckKey(value) }
    fun setApiLastCheckOkBlocking(value: Boolean) = runBlocking { setApiLastCheckOk(value) }
    fun setApiLastCheckTimeBlocking(value: Long) = runBlocking { setApiLastCheckTime(value) }
    fun setApiLastCheckSigBlocking(value: String) = runBlocking { setApiLastCheckSig(value) }
    fun setConversationsBlocking(value: String?) = runBlocking { setConversations(value) }
    fun setLegacyConversationsJsonBlocking(value: String?) = runBlocking { setLegacyConversationsJson(value) }
    fun setLegacyActiveConversationIdBlocking(value: Long?) = runBlocking {
        setLegacyActiveConversationId(value)
    }
    fun getQwenPendingDownloadIdsBlocking(): Set<String> = runBlocking { getQwenPendingDownloadIds() }
    fun setQwenPendingDownloadIdsBlocking(value: Set<String>) = runBlocking { setQwenPendingDownloadIds(value) }

    /** Batch-write API config; pass null to remove a key. */
    suspend fun writeApiConfig(
        apiKey: String? = null,
        removeApiKey: Boolean = false,
        useThirdParty: Boolean? = null,
        useLocalModel: Boolean? = null,
        thirdPartyBaseUrl: String? = null,
        thirdPartyModel: String? = null,
        lastCheckKey: String? = null,
        lastCheckOk: Boolean? = null,
        lastCheckTime: Long? = null,
        lastCheckSig: String? = null,
        clearCheckResults: Boolean = false,
    ) {
        context.appPreferencesDataStore.edit { prefs ->
            if (removeApiKey) prefs.remove(Keys.apiKey)
            else if (apiKey != null) prefs[Keys.apiKey] = apiKey
            useThirdParty?.let { prefs[Keys.apiUseThirdParty] = it }
            useLocalModel?.let { prefs[Keys.apiUseLocalModel] = it }
            thirdPartyBaseUrl?.let { prefs[Keys.apiThirdPartyBaseUrl] = it }
            thirdPartyModel?.let { prefs[Keys.apiThirdPartyModel] = it }
            if (clearCheckResults) {
                prefs.remove(Keys.apiLastCheckSig)
                prefs.remove(Keys.apiLastCheckKey)
                prefs.remove(Keys.apiLastCheckOk)
                prefs.remove(Keys.apiLastCheckTime)
            }
            lastCheckKey?.let { prefs[Keys.apiLastCheckKey] = it }
            lastCheckOk?.let { prefs[Keys.apiLastCheckOk] = it }
            lastCheckTime?.let { prefs[Keys.apiLastCheckTime] = it }
            lastCheckSig?.let { prefs[Keys.apiLastCheckSig] = it }
        }
    }

    fun writeApiConfigBlocking(
        apiKey: String? = null,
        removeApiKey: Boolean = false,
        useThirdParty: Boolean? = null,
        useLocalModel: Boolean? = null,
        thirdPartyBaseUrl: String? = null,
        thirdPartyModel: String? = null,
        lastCheckKey: String? = null,
        lastCheckOk: Boolean? = null,
        lastCheckTime: Long? = null,
        lastCheckSig: String? = null,
        clearCheckResults: Boolean = false,
    ) = runBlocking {
        writeApiConfig(
            apiKey = apiKey,
            removeApiKey = removeApiKey,
            useThirdParty = useThirdParty,
            useLocalModel = useLocalModel,
            thirdPartyBaseUrl = thirdPartyBaseUrl,
            thirdPartyModel = thirdPartyModel,
            lastCheckKey = lastCheckKey,
            lastCheckOk = lastCheckOk,
            lastCheckTime = lastCheckTime,
            lastCheckSig = lastCheckSig,
            clearCheckResults = clearCheckResults,
        )
    }

    fun setUserAgreementAcceptedBlocking(value: Boolean) = runBlocking { setUserAgreementAccepted(value) }
    fun setPermGuideShownBlocking(value: Boolean) = runBlocking { setPermGuideShown(value) }
}
