package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

typealias ThemeMode = com.ai.phoneagent.core.designsystem.theme.ThemeMode
typealias ThemeAccent = com.ai.phoneagent.core.designsystem.theme.ThemeAccent
typealias ThemeColorStyle = com.ai.phoneagent.core.designsystem.theme.ThemeColorStyle

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
        
        val useAriesApi = booleanPreferencesKey("use_aries_api")
        val ariesApiSectionUnlocked = booleanPreferencesKey("aries_api_section_unlocked")
        val ariesLoggedInUser = stringPreferencesKey("aries_logged_in_user")
        val ariesSelectedModel = stringPreferencesKey("aries_selected_model")
        val ariesApiKey = stringPreferencesKey("aries_api_key")

        // ─── Appearance preferences ──────────────────────────────────────────
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColorStyle = stringPreferencesKey("theme_color_style")
        val themeAccent = stringPreferencesKey("theme_accent")
        val amoledDarkEnabled = booleanPreferencesKey("amoled_dark_enabled")
        val dynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
        val chatFontScale = floatPreferencesKey("chat_font_scale")
        val chatFontFamily = stringPreferencesKey("chat_font_family")
        val codeAutoWrap = booleanPreferencesKey("code_auto_wrap")
        val codeLineNumbers = booleanPreferencesKey("code_line_numbers")
        val codeAutoCollapse = booleanPreferencesKey("code_auto_collapse")
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

    val useAriesApiFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.useAriesApi] ?: false
        }

    val ariesApiSectionUnlockedFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.ariesApiSectionUnlocked] ?: false
        }

    val ariesLoggedInUserFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.ariesLoggedInUser] ?: ""
        }

    val ariesSelectedModelFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.ariesSelectedModel] ?: ""
        }

    val ariesApiKeyFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.ariesApiKey] ?: ""
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

    val themeModeFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.themeMode] ?: "system"
        }

    val themeColorStyleFlow: Flow<String> =
        context.appPreferencesDataStore.data.map(::resolveThemeColorStyleStorage)

    val themeAccentFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.themeAccent] ?: "default"
        }

    val amoledDarkEnabledFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.amoledDarkEnabled] ?: false
        }

    val dynamicColorEnabledFlow: Flow<Boolean> =
        themeColorStyleFlow.map { raw -> ThemeColorStyle.fromStorage(raw).isDynamic }

    val chatFontScaleFlow: Flow<Float> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.chatFontScale] ?: 1.0f
        }

    val chatFontFamilyFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.chatFontFamily] ?: "default"
        }

    val codeAutoWrapFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.codeAutoWrap] ?: true
        }

    val codeLineNumbersFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.codeLineNumbers] ?: true
        }

    val codeAutoCollapseFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[Keys.codeAutoCollapse] ?: false
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

    suspend fun setUseAriesApi(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.useAriesApi] = value
        }
    }

    suspend fun setAriesApiSectionUnlocked(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.ariesApiSectionUnlocked] = value
        }
    }

    suspend fun getAriesApiSectionUnlocked(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.ariesApiSectionUnlocked] ?: false
    }

    suspend fun setAriesLoggedInUser(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(Keys.ariesLoggedInUser)
            else prefs[Keys.ariesLoggedInUser] = value
        }
    }

    suspend fun getAriesLoggedInUser(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.ariesLoggedInUser] ?: ""
    }

    suspend fun setAriesSelectedModel(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(Keys.ariesSelectedModel)
            else prefs[Keys.ariesSelectedModel] = value
        }
    }

    suspend fun getAriesSelectedModel(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.ariesSelectedModel] ?: ""
    }

    suspend fun setAriesApiKey(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(Keys.ariesApiKey)
            else prefs[Keys.ariesApiKey] = value
        }
    }

    suspend fun getAriesApiKey(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.ariesApiKey] ?: ""
    }

    suspend fun getActiveAriesApiKey(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        val ariesKey = prefs[Keys.ariesApiKey].orEmpty()
        if (ariesKey.isNotBlank()) return ariesKey
        val loggedInUser = prefs[Keys.ariesLoggedInUser].orEmpty()
        return if (loggedInUser.isNotBlank()) {
            prefs[Keys.apiKey].orEmpty()
        } else {
            ""
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

    suspend fun getThemeMode(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.themeMode] ?: "system"
    }

    suspend fun getThemeColorStyle(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return resolveThemeColorStyleStorage(prefs)
    }

    suspend fun setThemeColorStyle(value: String) {
        val style = ThemeColorStyle.fromStorage(value)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.themeColorStyle] = style.storageKey
            prefs[Keys.dynamicColorEnabled] = style.isDynamic
            if (!style.isDynamic) {
                prefs[Keys.themeAccent] = style.accentOrDefault.storageKey
            }
        }
    }

    suspend fun setThemeMode(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.themeMode] = value
        }
    }

    suspend fun getThemeAccent(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.themeAccent] ?: ThemeColorStyle.DEFAULT.storageKey
    }

    suspend fun setThemeAccent(value: String) {
        val accent = ThemeAccent.fromStorage(value)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.themeAccent] = accent.storageKey
            prefs[Keys.themeColorStyle] = accent.storageKey
            prefs[Keys.dynamicColorEnabled] = false
        }
    }

    suspend fun getAmoledDarkEnabled(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.amoledDarkEnabled] ?: false
    }

    suspend fun setAmoledDarkEnabled(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.amoledDarkEnabled] = value
        }
    }

    suspend fun getDynamicColorEnabled(): Boolean {
        return ThemeColorStyle.fromStorage(getThemeColorStyle()).isDynamic
    }

    suspend fun setDynamicColorEnabled(value: Boolean) {
        val accent = getThemeAccent()
        setThemeColorStyle(
            if (value) ThemeColorStyle.DYNAMIC.storageKey else accent
        )
    }

    suspend fun getChatFontScale(): Float {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.chatFontScale] ?: 1.0f
    }

    suspend fun setChatFontScale(value: Float) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.chatFontScale] = value
        }
    }

    suspend fun getChatFontFamily(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.chatFontFamily] ?: "default"
    }

    suspend fun setChatFontFamily(value: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.chatFontFamily] = value
        }
    }

    suspend fun getCodeAutoWrap(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.codeAutoWrap] ?: true
    }

    suspend fun setCodeAutoWrap(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.codeAutoWrap] = value
        }
    }

    suspend fun getCodeLineNumbers(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.codeLineNumbers] ?: true
    }

    suspend fun setCodeLineNumbers(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.codeLineNumbers] = value
        }
    }

    suspend fun getCodeAutoCollapse(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[Keys.codeAutoCollapse] ?: false
    }

    suspend fun setCodeAutoCollapse(value: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.codeAutoCollapse] = value
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
    fun getUseAriesApiBlocking(): Boolean = runBlocking {
        context.appPreferencesDataStore.data.first()[Keys.useAriesApi] ?: false
    }
    fun getAriesApiSectionUnlockedBlocking(): Boolean = runBlocking { getAriesApiSectionUnlocked() }
    fun setAriesApiSectionUnlockedBlocking(value: Boolean) = runBlocking { setAriesApiSectionUnlocked(value) }
    fun getAriesLoggedInUserBlocking(): String = runBlocking { getAriesLoggedInUser() }
    fun setAriesLoggedInUserBlocking(value: String) = runBlocking { setAriesLoggedInUser(value) }
    fun getAriesSelectedModelBlocking(): String = runBlocking { getAriesSelectedModel() }
    fun setAriesSelectedModelBlocking(value: String) = runBlocking { setAriesSelectedModel(value) }
    fun getAriesApiKeyBlocking(): String = runBlocking { getAriesApiKey() }
    fun setAriesApiKeyBlocking(value: String) = runBlocking { setAriesApiKey(value) }
    fun getActiveAriesApiKeyBlocking(): String = runBlocking { getActiveAriesApiKey() }
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
    fun setUseAriesApiBlocking(value: Boolean) = runBlocking { setUseAriesApi(value) }
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

    fun getThemeModeBlocking(): String = runBlocking { getThemeMode() }
    fun setThemeModeBlocking(value: String) = runBlocking { setThemeMode(value) }
    fun getThemeColorStyleBlocking(): String = runBlocking { getThemeColorStyle() }
    fun setThemeColorStyleBlocking(value: String) = runBlocking { setThemeColorStyle(value) }
    fun getThemeAccentBlocking(): String = runBlocking { getThemeAccent() }
    fun setThemeAccentBlocking(value: String) = runBlocking { setThemeAccent(value) }
    fun getAmoledDarkEnabledBlocking(): Boolean = runBlocking { getAmoledDarkEnabled() }
    fun setAmoledDarkEnabledBlocking(value: Boolean) = runBlocking { setAmoledDarkEnabled(value) }
    fun getDynamicColorEnabledBlocking(): Boolean = runBlocking { getDynamicColorEnabled() }
    fun setDynamicColorEnabledBlocking(value: Boolean) = runBlocking { setDynamicColorEnabled(value) }
    fun getChatFontScaleBlocking(): Float = runBlocking { getChatFontScale() }
    fun setChatFontScaleBlocking(value: Float) = runBlocking { setChatFontScale(value) }
    fun getChatFontFamilyBlocking(): String = runBlocking { getChatFontFamily() }
    fun setChatFontFamilyBlocking(value: String) = runBlocking { setChatFontFamily(value) }
    fun getCodeAutoWrapBlocking(): Boolean = runBlocking { getCodeAutoWrap() }
    fun setCodeAutoWrapBlocking(value: Boolean) = runBlocking { setCodeAutoWrap(value) }
    fun getCodeLineNumbersBlocking(): Boolean = runBlocking { getCodeLineNumbers() }
    fun setCodeLineNumbersBlocking(value: Boolean) = runBlocking { setCodeLineNumbers(value) }
    fun getCodeAutoCollapseBlocking(): Boolean = runBlocking { getCodeAutoCollapse() }
    fun setCodeAutoCollapseBlocking(value: Boolean) = runBlocking { setCodeAutoCollapse(value) }

    private fun resolveThemeColorStyleStorage(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): String {
        prefs[Keys.themeColorStyle]?.let { return it }
        if (prefs[Keys.dynamicColorEnabled] == true) {
            return ThemeColorStyle.DYNAMIC.storageKey
        }
        return prefs[Keys.themeAccent] ?: ThemeColorStyle.DEFAULT.storageKey
    }
}
