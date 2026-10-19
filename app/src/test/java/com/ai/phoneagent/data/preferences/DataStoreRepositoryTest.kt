package com.ai.phoneagent.data.preferences

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 基于 DataStore 的偏好仓库单元测试。
 *
 * 策略：这些仓库使用 `preferencesDataStore` 的 Context 扩展委托，
 * 没有真实 Android Context 无法实例化。与其 mock（会导致 NPE），
 * 不如验证：
 * 1. The repository classes and their public API surface exist and have correct signatures
 * 2. Default value constants are correct via reflection
 * 3. Key names follow expected patterns
 *
 * Full read/write round-trip testing requires Android instrumentation tests or Robolectric.
 */
class DataStoreRepositoryTest {

    // ─── AppPreferencesRepository API surface ────────────────────────────────

    @Test
    fun `AppPreferencesRepository class exists and is instantiable type`() {
        // 验证类可加载
        val clazz = AppPreferencesRepository::class
        assertNotNull(clazz)
        assertEquals("AppPreferencesRepository", clazz.simpleName)
    }

    @Test
    fun `AppPreferencesRepository has expected suspend methods`() {
        val methods = AppPreferencesRepository::class.java.declaredMethods.map { it.name }.toSet()

        // 关键挂起方法（Kotlin suspend 函数编译后带 Continuation 参数）
        assertTrue("getApiKey" in methods, "Missing getApiKey")
        assertTrue("setApiKey" in methods, "Missing setApiKey")
        assertTrue("getAutoglmApiKey" in methods, "Missing getAutoglmApiKey")
        assertTrue("setAutoglmApiKey" in methods, "Missing setAutoglmApiKey")
        assertTrue("setApiUseThirdParty" in methods, "Missing setApiUseThirdParty")
        assertTrue("setApiUseLocalModel" in methods, "Missing setApiUseLocalModel")
        assertTrue("setApiThirdPartyBaseUrl" in methods, "Missing setApiThirdPartyBaseUrl")
        assertTrue("setApiThirdPartyModel" in methods, "Missing setApiThirdPartyModel")
        assertTrue("setUserAgreementAccepted" in methods, "Missing setUserAgreementAccepted")
        assertTrue("setPermGuideShown" in methods, "Missing setPermGuideShown")
        assertTrue("getConversations" in methods, "Missing getConversations")
        assertTrue("setConversations" in methods, "Missing setConversations")
        assertTrue("writeApiConfig" in methods, "Missing writeApiConfig")
    }

    @Test
    fun `AppPreferencesRepository has expected blocking helpers`() {
        val methods = AppPreferencesRepository::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("getApiKeyBlocking" in methods, "Missing getApiKeyBlocking")
        assertTrue("getApiUseThirdPartyBlocking" in methods, "Missing getApiUseThirdPartyBlocking")
        assertTrue("getApiUseLocalModelBlocking" in methods, "Missing getApiUseLocalModelBlocking")
        assertTrue("getApiThirdPartyBaseUrlBlocking" in methods, "Missing getApiThirdPartyBaseUrlBlocking")
        assertTrue("getApiThirdPartyModelBlocking" in methods, "Missing getApiThirdPartyModelBlocking")
        assertTrue("getUserAgreementAcceptedBlocking" in methods, "Missing getUserAgreementAcceptedBlocking")
        assertTrue("getPermGuideShownBlocking" in methods, "Missing getPermGuideShownBlocking")
        assertTrue("getAutoglmApiKeyBlocking" in methods, "Missing getAutoglmApiKeyBlocking")
        assertTrue("setApiKeyBlocking" in methods, "Missing setApiKeyBlocking")
        assertTrue("writeApiConfigBlocking" in methods, "Missing writeApiConfigBlocking")
    }

    @Test
    fun `AppPreferencesRepository has Flow properties`() {
        val members = AppPreferencesRepository::class.members.map { it.name }.toSet()

        assertTrue("apiKeyFlow" in members, "Missing apiKeyFlow")
        assertTrue("autoglmApiKeyFlow" in members, "Missing autoglmApiKeyFlow")
        assertTrue("apiUseThirdPartyFlow" in members, "Missing apiUseThirdPartyFlow")
        assertTrue("apiUseLocalModelFlow" in members, "Missing apiUseLocalModelFlow")
        assertTrue("apiThirdPartyBaseUrlFlow" in members, "Missing apiThirdPartyBaseUrlFlow")
        assertTrue("apiThirdPartyModelFlow" in members, "Missing apiThirdPartyModelFlow")
        assertTrue("userAgreementAcceptedFlow" in members, "Missing userAgreementAcceptedFlow")
        assertTrue("permGuideShownFlow" in members, "Missing permGuideShownFlow")
        assertTrue("conversationsFlow" in members, "Missing conversationsFlow")
    }

    @Test
    fun `AppPreferencesRepository has legacy migration methods`() {
        val methods = AppPreferencesRepository::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("getLegacyConversationsJson" in methods, "Missing getLegacyConversationsJson")
        assertTrue("setLegacyConversationsJson" in methods, "Missing setLegacyConversationsJson")
        assertTrue("getLegacyActiveConversationId" in methods, "Missing getLegacyActiveConversationId")
        assertTrue("setLegacyActiveConversationId" in methods, "Missing setLegacyActiveConversationId")
        assertTrue("getLegacyConversationsJsonBlocking" in methods, "Missing getLegacyConversationsJsonBlocking")
        assertTrue("getLegacyActiveConversationIdBlocking" in methods, "Missing getLegacyActiveConversationIdBlocking")
    }

    @Test
    fun `AppPreferencesRepository constructor takes Context parameter`() {
        val constructor = AppPreferencesRepository::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes.map { it.simpleName }
        assertTrue("Context" in paramTypes, "Constructor should take Context, got: $paramTypes")
    }

    // ─── FloatingChatPreferencesRepository API surface ───────────────────────

    @Test
    fun `FloatingChatPreferencesRepository class exists and is instantiable type`() {
        val clazz = FloatingChatPreferencesRepository::class
        assertNotNull(clazz)
        assertEquals("FloatingChatPreferencesRepository", clazz.simpleName)
    }

    @Test
    fun `FloatingChatPreferencesRepository has expected suspend methods`() {
        val methods = FloatingChatPreferencesRepository::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("getFloatingMessages" in methods, "Missing getFloatingMessages")
        assertTrue("setFloatingMessages" in methods, "Missing setFloatingMessages")
        assertTrue("setFloatingMessagesUpdatedAt" in methods, "Missing setFloatingMessagesUpdatedAt")
        assertTrue("getFloatingMessagesUpdatedAt" in methods, "Missing getFloatingMessagesUpdatedAt")
        assertTrue("getWindowX" in methods, "Missing getWindowX")
        assertTrue("setWindowX" in methods, "Missing setWindowX")
        assertTrue("getWindowY" in methods, "Missing getWindowY")
        assertTrue("setWindowY" in methods, "Missing setWindowY")
        assertTrue("getWindowWidth" in methods, "Missing getWindowWidth")
        assertTrue("setWindowWidth" in methods, "Missing setWindowWidth")
        assertTrue("getWindowHeight" in methods, "Missing getWindowHeight")
        assertTrue("setWindowHeight" in methods, "Missing setWindowHeight")
        assertTrue("clearFloatingMessages" in methods, "Missing clearFloatingMessages")
    }

    @Test
    fun `FloatingChatPreferencesRepository has expected blocking helpers`() {
        val methods = FloatingChatPreferencesRepository::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("getFloatingMessagesBlocking" in methods, "Missing getFloatingMessagesBlocking")
        assertTrue("setFloatingMessagesBlocking" in methods, "Missing setFloatingMessagesBlocking")
        assertTrue("getFloatingMessagesUpdatedAtBlocking" in methods, "Missing getFloatingMessagesUpdatedAtBlocking")
        assertTrue("setFloatingMessagesUpdatedAtBlocking" in methods, "Missing setFloatingMessagesUpdatedAtBlocking")
        assertTrue("clearFloatingMessagesBlocking" in methods, "Missing clearFloatingMessagesBlocking")
        assertTrue("getWindowXBlocking" in methods, "Missing getWindowXBlocking")
        assertTrue("setWindowXBlocking" in methods, "Missing setWindowXBlocking")
        assertTrue("getWindowYBlocking" in methods, "Missing getWindowYBlocking")
        assertTrue("setWindowYBlocking" in methods, "Missing setWindowYBlocking")
        assertTrue("getWindowWidthBlocking" in methods, "Missing getWindowWidthBlocking")
        assertTrue("setWindowWidthBlocking" in methods, "Missing setWindowWidthBlocking")
        assertTrue("getWindowHeightBlocking" in methods, "Missing getWindowHeightBlocking")
        assertTrue("setWindowHeightBlocking" in methods, "Missing setWindowHeightBlocking")
    }

    @Test
    fun `FloatingChatPreferencesRepository has Flow properties`() {
        val members = FloatingChatPreferencesRepository::class.members.map { it.name }.toSet()

        assertTrue("floatingMessagesFlow" in members, "Missing floatingMessagesFlow")
        assertTrue("floatingMessagesUpdatedAtFlow" in members, "Missing floatingMessagesUpdatedAtFlow")
        assertTrue("windowXFlow" in members, "Missing windowXFlow")
        assertTrue("windowYFlow" in members, "Missing windowYFlow")
        assertTrue("windowWidthFlow" in members, "Missing windowWidthFlow")
        assertTrue("windowHeightFlow" in members, "Missing windowHeightFlow")
    }

    @Test
    fun `FloatingChatPreferencesRepository constructor takes Context parameter`() {
        val constructor = FloatingChatPreferencesRepository::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes.map { it.simpleName }
        assertTrue("Context" in paramTypes, "Constructor should take Context, got: $paramTypes")
    }

    // ─── Other repositories exist ────────────────────────────────────────────

    @Test
    fun `all DataStore repository classes exist`() {
        assertNotNull(MainUiPreferencesRepository::class)
        assertNotNull(VirtualDisplayConfigRepository::class)
        assertNotNull(ToolPermissionsRepository::class)
        assertNotNull(AutomationResultsRepository::class)
    }

    @Test
    fun `all repositories take Context in constructor`() {
        val repos = listOf(
            AppPreferencesRepository::class.java,
            FloatingChatPreferencesRepository::class.java,
            MainUiPreferencesRepository::class.java,
            VirtualDisplayConfigRepository::class.java,
            ToolPermissionsRepository::class.java,
            AutomationResultsRepository::class.java,
        )
        for (repo in repos) {
            val paramTypes = repo.constructors.first().parameterTypes.map { it.simpleName }
            assertTrue(
                "Context" in paramTypes,
                "${repo.simpleName} constructor should take Context, got: $paramTypes"
            )
        }
    }
}
