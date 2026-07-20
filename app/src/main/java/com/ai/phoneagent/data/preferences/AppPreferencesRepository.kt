package com.ai.phoneagent.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.phoneagent.data.security.SecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

typealias ThemeMode = com.ai.phoneagent.core.designsystem.theme.ThemeMode
typealias ThemeAccent = com.ai.phoneagent.core.designsystem.theme.ThemeAccent
typealias ThemeColorStyle = com.ai.phoneagent.core.designsystem.theme.ThemeColorStyle

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")
private val Context.appSecretsDataStore by preferencesDataStore(name = "app_secrets")
private val Context.legacyAppPreferencesDataStore by preferencesDataStore(name = "app_prefs")

class AppPreferencesRepository(
    private val context: Context,
    private val secretStore: SecretStore,
    prefsStoreOverride: DataStore<Preferences>? = null,
    secretsStoreOverride: DataStore<Preferences>? = null,
    legacyStoreOverride: DataStore<Preferences>? = null,
    backgroundMigrationEnabled: Boolean = true,
) {
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrationReady = CompletableDeferred<Unit>()

    // 新的非敏感偏好和 secrets 分别落到独立 DataStore；旧 app_prefs 永久只作为迁移源。
    // 这样备份规则可以始终排除旧混合文件，避免首次启动前的备份窗口泄露存量 Key。
    private val prefsStore: DataStore<Preferences> = prefsStoreOverride ?: context.appPreferencesDataStore
    private val secretsStore: DataStore<Preferences> = secretsStoreOverride ?: context.appSecretsDataStore
    private val legacyStore: DataStore<Preferences> = legacyStoreOverride ?: context.legacyAppPreferencesDataStore

    private val readyPrefsFlow: Flow<Preferences> = flow {
        migrationReady.await()
        emitAll(prefsStore.data)
    }
    private val readySecretsFlow: Flow<Preferences> = flow {
        migrationReady.await()
        emitAll(secretsStore.data)
    }

    init {
        // 顺序：先把旧混合 app_prefs 拆到新的非敏感/敏感 DataStore，再加密 legacy secrets。
        // 所有公开读写都会等待 migrationReady，首次升级不会短暂读到空 Key，也不会与搬迁并发写入。
        if (backgroundMigrationEnabled) {
            migrationScope.launch {
                try {
                    runCatching { relocateLegacyPreferences() }
                        .onFailure { Log.w(TAG, "Legacy preferences relocation failed; will retry on next launch", it) }
                    runCatching { migrateLegacySecrets() }
                        .onFailure { Log.w(TAG, "Legacy secret migration failed; will retry on next launch", it) }
                } finally {
                    migrationReady.complete(Unit)
                }
            }
        } else {
            migrationReady.complete(Unit)
        }
    }

    private suspend fun editPrefs(transform: suspend (MutablePreferences) -> Unit) {
        migrationReady.await()
        prefsStore.edit(transform)
    }

    private suspend fun editSecrets(transform: suspend (MutablePreferences) -> Unit) {
        migrationReady.await()
        secretsStore.edit(transform)
    }

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

    /**
     * 读取敏感配置。密文解不开时（[SecretStore.ReadResult.Unavailable]/[SecretStore.ReadResult.Corrupt]）
     * 返回空串，但绝不删除存储内容——保留密文让用户重试或重新输入。
     */
    private fun Preferences.readSecret(key: Preferences.Key<String>): String {
        return when (val result = secretStore.decrypt(this[key])) {
            is SecretStore.ReadResult.Available -> result.value
            SecretStore.ReadResult.Missing -> ""
            SecretStore.ReadResult.Unavailable -> {
                Log.w(TAG, "Secret ${key.name} temporarily unavailable; ciphertext kept")
                ""
            }
            SecretStore.ReadResult.Corrupt -> {
                Log.w(TAG, "Secret ${key.name} is corrupted; ciphertext kept for re-entry")
                ""
            }
        }
    }

    /**
     * 写入敏感配置。
     *
     * @return `true` 表示已持久化（空值视为删除，返回 true）；
     *         `false` 表示加密不可用，本次未写入、旧值保留（显式失败，不崩溃）。
     */
    private fun MutablePreferences.writeSecret(key: Preferences.Key<String>, value: String): Boolean {
        if (value.isBlank()) {
            remove(key)
            return true
        }
        val encrypted = secretStore.encrypt(value)
        return if (encrypted != null) {
            this[key] = encrypted
            true
        } else {
            Log.w(TAG, "Encryption unavailable; secret ${key.name} was NOT updated")
            false
        }
    }

    val apiKeyFlow: Flow<String> =
        readySecretsFlow.map { prefs ->
            prefs.readSecret(Keys.apiKey)
        }

    val autoglmApiKeyFlow: Flow<String> =
        readySecretsFlow.map { prefs ->
            prefs.readSecret(Keys.autoglmApiKey)
        }

    val apiUseThirdPartyFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.apiUseThirdParty] ?: false
        }

    val apiUseLocalModelFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.apiUseLocalModel] ?: false
        }

    val useAriesApiFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.useAriesApi] ?: false
        }

    val ariesApiSectionUnlockedFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.ariesApiSectionUnlocked] ?: false
        }

    val ariesLoggedInUserFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.ariesLoggedInUser] ?: ""
        }

    val ariesSelectedModelFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.ariesSelectedModel] ?: ""
        }

    val ariesApiKeyFlow: Flow<String> =
        readySecretsFlow.map { prefs ->
            prefs.readSecret(Keys.ariesApiKey)
        }

    val apiThirdPartyBaseUrlFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.apiThirdPartyBaseUrl] ?: ""
        }

    val apiThirdPartyModelFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.apiThirdPartyModel] ?: ""
        }

    val userAgreementAcceptedFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.userAgreementAccepted] ?: false
        }

    val permGuideShownFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.permGuideShown] ?: false
        }

    val conversationsFlow: Flow<String?> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.conversations]
        }

    val qwenPendingDownloadIdsFlow: Flow<Set<String>> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.qwenPendingDownloadIds] ?: emptySet()
        }

    val themeModeFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.themeMode] ?: "system"
        }

    val themeColorStyleFlow: Flow<String> =
        readyPrefsFlow.map(::resolveThemeColorStyleStorage)

    val themeAccentFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.themeAccent] ?: "default"
        }

    val amoledDarkEnabledFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.amoledDarkEnabled] ?: false
        }

    val dynamicColorEnabledFlow: Flow<Boolean> =
        themeColorStyleFlow.map { raw -> ThemeColorStyle.fromStorage(raw).isDynamic }

    val chatFontScaleFlow: Flow<Float> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.chatFontScale] ?: 1.0f
        }

    val chatFontFamilyFlow: Flow<String> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.chatFontFamily] ?: "default"
        }

    val codeAutoWrapFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.codeAutoWrap] ?: true
        }

    val codeLineNumbersFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.codeLineNumbers] ?: true
        }

    val codeAutoCollapseFlow: Flow<Boolean> =
        readyPrefsFlow.map { prefs ->
            prefs[Keys.codeAutoCollapse] ?: false
        }

    suspend fun getApiKey(): String {
        val prefs = readySecretsFlow.first()
        return prefs.readSecret(Keys.apiKey)
    }

    /**
     * @return `false` 表示加密不可用、本次未保存（旧值保留），调用方可提示用户稍后重试。
     */
    suspend fun setApiKey(value: String): Boolean {
        var stored = false
        editSecrets { prefs ->
            stored = prefs.writeSecret(Keys.apiKey, value)
        }
        return stored
    }

    suspend fun getAutoglmApiKey(): String {
        val prefs = readySecretsFlow.first()
        return prefs.readSecret(Keys.autoglmApiKey)
    }

    /**
     * @return `false` 表示加密不可用、本次未保存（旧值保留）。
     */
    suspend fun setAutoglmApiKey(value: String): Boolean {
        var stored = false
        editSecrets { prefs ->
            stored = prefs.writeSecret(Keys.autoglmApiKey, value)
        }
        return stored
    }

    suspend fun setApiUseThirdParty(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.apiUseThirdParty] = value
        }
    }

    suspend fun setApiUseLocalModel(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.apiUseLocalModel] = value
        }
    }

    suspend fun setUseAriesApi(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.useAriesApi] = value
        }
    }

    suspend fun setAriesApiSectionUnlocked(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.ariesApiSectionUnlocked] = value
        }
    }

    suspend fun getAriesApiSectionUnlocked(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.ariesApiSectionUnlocked] ?: false
    }

    suspend fun setAriesLoggedInUser(value: String) {
        editPrefs { prefs ->
            if (value.isBlank()) prefs.remove(Keys.ariesLoggedInUser)
            else prefs[Keys.ariesLoggedInUser] = value
        }
    }

    suspend fun getAriesLoggedInUser(): String {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.ariesLoggedInUser] ?: ""
    }

    suspend fun setAriesSelectedModel(value: String) {
        editPrefs { prefs ->
            if (value.isBlank()) prefs.remove(Keys.ariesSelectedModel)
            else prefs[Keys.ariesSelectedModel] = value
        }
    }

    suspend fun getAriesSelectedModel(): String {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.ariesSelectedModel] ?: ""
    }

    /**
     * @return `false` 表示加密不可用、本次未保存（旧值保留）。
     */
    suspend fun setAriesApiKey(value: String): Boolean {
        var stored = false
        editSecrets { prefs ->
            stored = prefs.writeSecret(Keys.ariesApiKey, value)
        }
        return stored
    }

    suspend fun getAriesApiKey(): String {
        val prefs = readySecretsFlow.first()
        return prefs.readSecret(Keys.ariesApiKey)
    }

    suspend fun getActiveAriesApiKey(): String {
        val prefs = readySecretsFlow.first()
        val ariesKey = prefs.readSecret(Keys.ariesApiKey)
        if (ariesKey.isNotBlank()) return ariesKey
        val loggedInUser = readyPrefsFlow.first()[Keys.ariesLoggedInUser].orEmpty()
        return if (loggedInUser.isNotBlank()) {
            prefs.readSecret(Keys.apiKey)
        } else {
            ""
        }
    }

    suspend fun setApiThirdPartyBaseUrl(value: String) {
        editPrefs { prefs ->
            prefs[Keys.apiThirdPartyBaseUrl] = value
        }
    }

    suspend fun setApiThirdPartyModel(value: String) {
        editPrefs { prefs ->
            prefs[Keys.apiThirdPartyModel] = value
        }
    }

    suspend fun getApiLastCheckKey(): String {
        val prefs = readySecretsFlow.first()
        return prefs.readSecret(Keys.apiLastCheckKey)
    }

    suspend fun setApiLastCheckKey(value: String): Boolean {
        var stored = false
        editSecrets { prefs ->
            stored = prefs.writeSecret(Keys.apiLastCheckKey, value)
        }
        return stored
    }

    suspend fun getApiLastCheckOk(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.apiLastCheckOk] ?: false
    }

    suspend fun setApiLastCheckOk(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.apiLastCheckOk] = value
        }
    }

    suspend fun getApiLastCheckTime(): Long {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.apiLastCheckTime] ?: 0L
    }

    suspend fun setApiLastCheckTime(value: Long) {
        editPrefs { prefs ->
            prefs[Keys.apiLastCheckTime] = value
        }
    }

    suspend fun getApiLastCheckSig(): String {
        val prefs = readySecretsFlow.first()
        return prefs.readSecret(Keys.apiLastCheckSig)
    }

    suspend fun setApiLastCheckSig(value: String): Boolean {
        var stored = false
        editSecrets { prefs ->
            stored = prefs.writeSecret(Keys.apiLastCheckSig, value)
        }
        return stored
    }

    suspend fun setUserAgreementAccepted(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.userAgreementAccepted] = value
        }
    }

    suspend fun setPermGuideShown(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.permGuideShown] = value
        }
    }

    suspend fun getConversations(): String? {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.conversations]
    }

    suspend fun setConversations(value: String?) {
        editPrefs { prefs ->
            if (value == null) {
                prefs.remove(Keys.conversations)
            } else {
                prefs[Keys.conversations] = value
            }
        }
    }

    suspend fun getLegacyConversationsJson(): String? {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.legacyConversationsJson]
    }

    suspend fun setLegacyConversationsJson(value: String?) {
        editPrefs { prefs ->
            if (value == null) {
                prefs.remove(Keys.legacyConversationsJson)
            } else {
                prefs[Keys.legacyConversationsJson] = value
            }
        }
    }

    suspend fun getLegacyActiveConversationId(defaultValue: Long = -1L): Long {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.legacyActiveConversationId] ?: defaultValue
    }

    suspend fun setLegacyActiveConversationId(value: Long?) {
        editPrefs { prefs ->
            if (value == null) {
                prefs.remove(Keys.legacyActiveConversationId)
            } else {
                prefs[Keys.legacyActiveConversationId] = value
            }
        }
    }

    suspend fun getQwenPendingDownloadIds(): Set<String> {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.qwenPendingDownloadIds] ?: emptySet()
    }

    suspend fun setQwenPendingDownloadIds(value: Set<String>) {
        editPrefs { prefs ->
            prefs[Keys.qwenPendingDownloadIds] = value
        }
    }

    suspend fun getThemeMode(): String {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.themeMode] ?: "system"
    }

    suspend fun getThemeColorStyle(): String {
        val prefs = readyPrefsFlow.first()
        return resolveThemeColorStyleStorage(prefs)
    }

    suspend fun setThemeColorStyle(value: String) {
        val style = ThemeColorStyle.fromStorage(value)
        editPrefs { prefs ->
            prefs[Keys.themeColorStyle] = style.storageKey
            prefs[Keys.dynamicColorEnabled] = style.isDynamic
            if (!style.isDynamic) {
                prefs[Keys.themeAccent] = style.accentOrDefault.storageKey
            }
        }
    }

    suspend fun setThemeMode(value: String) {
        editPrefs { prefs ->
            prefs[Keys.themeMode] = value
        }
    }

    suspend fun getThemeAccent(): String {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.themeAccent] ?: ThemeColorStyle.DEFAULT.storageKey
    }

    suspend fun setThemeAccent(value: String) {
        val accent = ThemeAccent.fromStorage(value)
        editPrefs { prefs ->
            prefs[Keys.themeAccent] = accent.storageKey
            prefs[Keys.themeColorStyle] = accent.storageKey
            prefs[Keys.dynamicColorEnabled] = false
        }
    }

    suspend fun getAmoledDarkEnabled(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.amoledDarkEnabled] ?: false
    }

    suspend fun setAmoledDarkEnabled(value: Boolean) {
        editPrefs { prefs ->
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
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.chatFontScale] ?: 1.0f
    }

    suspend fun setChatFontScale(value: Float) {
        editPrefs { prefs ->
            prefs[Keys.chatFontScale] = value
        }
    }

    suspend fun getChatFontFamily(): String {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.chatFontFamily] ?: "default"
    }

    suspend fun setChatFontFamily(value: String) {
        editPrefs { prefs ->
            prefs[Keys.chatFontFamily] = value
        }
    }

    suspend fun getCodeAutoWrap(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.codeAutoWrap] ?: true
    }

    suspend fun setCodeAutoWrap(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.codeAutoWrap] = value
        }
    }

    suspend fun getCodeLineNumbers(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.codeLineNumbers] ?: true
    }

    suspend fun setCodeLineNumbers(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.codeLineNumbers] = value
        }
    }

    suspend fun getCodeAutoCollapse(): Boolean {
        val prefs = readyPrefsFlow.first()
        return prefs[Keys.codeAutoCollapse] ?: false
    }

    suspend fun setCodeAutoCollapse(value: Boolean) {
        editPrefs { prefs ->
            prefs[Keys.codeAutoCollapse] = value
        }
    }

    // ─── Blocking helpers for non-coroutine call sites ───────────────────────

    /** Blocking snapshot of the entire prefs — use only from non-suspend call sites. */
    fun getApiKeyBlocking(): String = runBlocking { getApiKey() }
    fun getApiUseThirdPartyBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first()[Keys.apiUseThirdParty] ?: false
    }
    fun getApiUseLocalModelBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first()[Keys.apiUseLocalModel] ?: false
    }
    fun getUseAriesApiBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first()[Keys.useAriesApi] ?: false
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
        readyPrefsFlow.first()[Keys.apiThirdPartyBaseUrl] ?: ""
    }
    fun getApiThirdPartyModelBlocking(): String = runBlocking {
        readyPrefsFlow.first()[Keys.apiThirdPartyModel] ?: ""
    }
    fun getApiLastCheckSigBlocking(): String = runBlocking { getApiLastCheckSig() }
    fun getApiLastCheckOkBlocking(): Boolean = runBlocking { getApiLastCheckOk() }
    fun hasApiLastCheckOkBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first().contains(Keys.apiLastCheckOk)
    }
    fun getUserAgreementAcceptedBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first()[Keys.userAgreementAccepted] ?: false
    }
    fun getPermGuideShownBlocking(): Boolean = runBlocking {
        readyPrefsFlow.first()[Keys.permGuideShown] ?: false
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

    /**
     * Batch-write API config; pass null to leave a key untouched, blank to remove a secret.
     *
     * 敏感字段（apiKey / lastCheckKey / lastCheckSig）统一走 [writeSecret] 加密落盘；
     * 任一加密不可用都会中止本次该字段的更新（旧值保留）并反映在返回值中。
     *
     * @return `true` 表示全部敏感字段已成功持久化（或未触发敏感字段写入）；
     *         `false` 表示至少一个敏感字段因加密不可用而未更新，旧值保留——调用方可提示用户稍后重试。
     */
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
    ): Boolean {
        var secretsOk = true
        // 敏感字段（apiKey / lastCheckKey / lastCheckSig）写入独立 secrets DataStore；
        // 任一加密不可用都会中止本次该字段的更新（旧值保留）并反映在返回值中。
        editSecrets { secrets ->
            if (removeApiKey) {
                secrets.remove(Keys.apiKey)
            } else if (apiKey != null) {
                secretsOk = secrets.writeSecret(Keys.apiKey, apiKey) && secretsOk
            }
            if (clearCheckResults) {
                secrets.remove(Keys.apiLastCheckSig)
                secrets.remove(Keys.apiLastCheckKey)
            }
            lastCheckKey?.let { secretsOk = secrets.writeSecret(Keys.apiLastCheckKey, it) && secretsOk }
            lastCheckSig?.let { secretsOk = secrets.writeSecret(Keys.apiLastCheckSig, it) && secretsOk }
        }
        // 非敏感字段（开关 / URL / Model / 检查时间与结果布尔）仍写主 prefs，参与云备份与设备迁移。
        editPrefs { prefs ->
            useThirdParty?.let { prefs[Keys.apiUseThirdParty] = it }
            useLocalModel?.let { prefs[Keys.apiUseLocalModel] = it }
            thirdPartyBaseUrl?.let { prefs[Keys.apiThirdPartyBaseUrl] = it }
            thirdPartyModel?.let { prefs[Keys.apiThirdPartyModel] = it }
            if (clearCheckResults) {
                prefs.remove(Keys.apiLastCheckOk)
                prefs.remove(Keys.apiLastCheckTime)
            }
            lastCheckOk?.let { prefs[Keys.apiLastCheckOk] = it }
            lastCheckTime?.let { prefs[Keys.apiLastCheckTime] = it }
        }
        if (!secretsOk) {
            Log.w(TAG, "writeApiConfig: encryption unavailable for at least one secret; old values kept")
        }
        return secretsOk
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
    ): Boolean = runBlocking {
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

    /**
     * 将历史明文保存的密钥迁移为 AndroidKeyStore 加密存储。
     *
     * **竞态安全**：整个迁移在单次 `edit` 闭包内完成。闭包内先读取当前值，只有当
     * 当前值仍等于启动快照中的 legacy 明文时才加密覆盖——若用户在期间保存了新值
     * （新值必然非 legacy 明文，或与 legacy 不同），该键直接跳过，绝不回写覆盖。
     * 加密后立即在闭包内回读验证，验证失败则恢复原明文，密钥绝不丢失。
     *
     * 幂等，可在每次启动时安全调用。
     *
     * @return 本次成功迁移的键数量。
     */
    suspend fun migrateLegacySecrets(): Int {
        val secretKeys = listOf(Keys.apiKey, Keys.autoglmApiKey, Keys.ariesApiKey, Keys.apiLastCheckKey, Keys.apiLastCheckSig)
        val before = secretsStore.data.first()
        val legacy = linkedMapOf<Preferences.Key<String>, String>()
        for (key in secretKeys) {
            val value = before[key]
            if (secretStore.isLegacyPlaintext(value) && value != null) {
                legacy[key] = value
            }
        }
        if (legacy.isEmpty()) return 0

        var migrated = 0
        secretsStore.edit { prefs ->
            for ((key, plain) in legacy) {
                // 竞态守卫：当前值必须仍是启动快照里的 legacy 明文，否则跳过——用户期间已改，绝不覆盖。
                val current = prefs[key]
                if (current != plain) {
                    Log.i(TAG, "Migration skipped for ${key.name}: value changed since snapshot")
                    continue
                }
                val encrypted = secretStore.encrypt(plain)
                if (encrypted == null) {
                    Log.w(TAG, "Migration skipped for ${key.name}: encryption unavailable")
                    continue
                }
                prefs[key] = encrypted
                // 闭包内就地回读验证：密文能解回原明文才算成功，否则恢复原值，绝不丢密钥。
                val stored = prefs[key]
                val verified =
                    stored != null &&
                        !secretStore.isLegacyPlaintext(stored) &&
                        (secretStore.decrypt(stored) as? SecretStore.ReadResult.Available)?.value == plain
                if (!verified) {
                    prefs[key] = plain
                    Log.w(TAG, "Migration rolled back for ${key.name}; original value restored")
                } else {
                    migrated++
                }
            }
        }
        if (migrated > 0) {
            Log.i(TAG, "Migrated $migrated legacy plaintext secret(s) to encrypted storage")
        }
        return migrated
    }

    /**
     * 把历史混合型 `app_prefs` 拆到新的 `app_preferences` 与 `app_secrets`。
     *
     * 旧文件永久列入备份排除清单；非敏感偏好搬到可备份的新文件，secrets 搬到不可备份文件。
     * 所有公开读写都会等待该方法结束，因此首次升级不会先读到空值，也不会与搬迁并发写入。
     * 每一步均为幂等操作：目标已有值时尊重目标值，最后只删除仍等于启动快照的 legacy 项。
     *
     * @return 本次从 legacy 文件移除的条目数。
     */
    private suspend fun relocateLegacyPreferences(): Int {
        val legacySnapshot = legacyStore.data.first()
        val entries = legacySnapshot.asMap()
        if (entries.isEmpty()) return 0

        val secretKeyNames =
            setOf(
                Keys.apiKey.name,
                Keys.autoglmApiKey.name,
                Keys.ariesApiKey.name,
                Keys.apiLastCheckKey.name,
                Keys.apiLastCheckSig.name,
            )

        secretsStore.edit { target ->
            for ((key, value) in entries) {
                if (key.name !in secretKeyNames || value !is String) continue
                @Suppress("UNCHECKED_CAST")
                val stringKey = key as Preferences.Key<String>
                if (!target.contains(stringKey)) target[stringKey] = value
            }
        }

        prefsStore.edit { target ->
            for ((key, value) in entries) {
                if (key.name in secretKeyNames) continue
                putUntypedIfAbsent(target, key, value)
            }
        }

        var removed = 0
        legacyStore.edit { legacy ->
            val current = legacy.asMap()
            for ((key, value) in entries) {
                if (current[key] == value) {
                    removeUntyped(legacy, key)
                    removed++
                }
            }
        }
        if (removed > 0) {
            Log.i(TAG, "Relocated $removed preference(s) out of legacy app_prefs")
        }
        return removed
    }

    @Suppress("UNCHECKED_CAST")
    private fun putUntypedIfAbsent(
        target: MutablePreferences,
        key: Preferences.Key<*>,
        value: Any,
    ) {
        val typedKey = key as Preferences.Key<Any>
        if (!target.contains(typedKey)) target[typedKey] = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeUntyped(target: MutablePreferences, key: Preferences.Key<*>) {
        target.remove(key as Preferences.Key<Any>)
    }

    private companion object {
        const val TAG = "AppPreferencesRepo"
    }
}
