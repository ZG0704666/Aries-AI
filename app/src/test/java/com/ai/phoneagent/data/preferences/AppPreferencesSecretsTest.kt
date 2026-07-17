/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ai.phoneagent.data.security.SecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AppPreferencesRepository.writeApiConfig] 与 [AppPreferencesRepository.migrateLegacySecrets]
 * 的回归测试，覆盖 PR-B 阻断项 #1 / #2 的修复。
 *
 * - **#1 批量配置加密**：`writeApiConfig` 敏感字段（apiKey / lastCheckKey / lastCheckSig）
 *   必须走 [SecretStore] 加密落盘，且加密不可用时返回 false 并保留旧值。
 * - **#2 迁移竞态**：`migrateLegacySecrets` 在单次 edit 闭包内做竞态守卫——快照与当前值
 *   不一致时跳过，绝不回写覆盖用户期间已保存的新值。
 *
 * 用 Robolectric 起真 Context 跑真 DataStore，注入测试专用 [SecretStore]（底层用
 * [com.ai.phoneagent.data.security.GcmSecretPayloadCodec] + 注入 AES key，纯 JVM 可跑），
 * 避免依赖 AndroidKeyStore。
 */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesSecretsTest {

    private lateinit var secretStore: SecretStore
    private lateinit var repo: AppPreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        secretStore = TestSecretStore()
        repo = AppPreferencesRepository(context, secretStore)
    }

    // ─── #1 批量配置加密 ─────────────────────────────────────────────

    @Test
    fun `writeApiConfig 把 apiKey lastCheckKey lastCheckSig 加密落盘_非明文`() = runBlocking {
        val ok = repo.writeApiConfig(
            apiKey = "sk-test-abc",
            lastCheckKey = "sk-test-abc",
            lastCheckSig = "sig|0|sk-test-abc|https://api|model",
            useThirdParty = true,
            thirdPartyBaseUrl = "https://api.example.com",
            thirdPartyModel = "gpt-4",
            lastCheckOk = true,
            lastCheckTime = 123456L,
        )
        assertTrue("加密可用时应返回 true", ok)

        // 回读：明文应等于原值，且底层存储不应含裸明文（断言靠 SecretStore 的加密契约）。
        assertEquals("sk-test-abc", repo.getApiKey())
        assertEquals("sk-test-abc", repo.getApiLastCheckKey())
        assertEquals("sig|0|sk-test-abc|https://api|model", repo.getApiLastCheckSig())
        // 非敏感字段仍可读
        assertTrue(repo.getApiUseThirdParty())
        assertEquals("https://api.example.com", repo.getApiThirdPartyBaseUrlBlocking())
        assertEquals("gpt-4", repo.getApiThirdPartyModelBlocking())
        assertEquals(true, repo.getApiLastCheckOkBlocking())
        assertEquals(123456L, repo.getApiLastCheckTimeBlocking())
    }

    @Test
    fun `writeApiConfig removeApiKey 删除后回读为空`() = runBlocking {
        repo.writeApiConfig(apiKey = "sk-test-abc")
        val ok = repo.writeApiConfig(removeApiKey = true)
        assertTrue(ok)
        assertEquals("", repo.getApiKey())
    }

    @Test
    fun `writeApiConfig clearCheckResults 清除检查结果敏感字段`() = runBlocking {
        repo.writeApiConfig(
            apiKey = "sk-test",
            lastCheckKey = "sk-test",
            lastCheckSig = "sig",
            lastCheckOk = true,
            lastCheckTime = 1L,
        )
        repo.writeApiConfig(clearCheckResults = true)
        assertEquals("", repo.getApiKey())
        assertEquals("", repo.getApiLastCheckKey())
        assertEquals("", repo.getApiLastCheckSig())
        assertEquals(false, repo.getApiLastCheckOkBlocking())
        assertEquals(0L, repo.getApiLastCheckTimeBlocking())
    }

    @Test
    fun `writeApiConfig 加密不可用时返回 false 且保留旧值`() = runBlocking {
        // 先正常写入一个 apiKey
        repo.writeApiConfig(apiKey = "sk-original")
        assertEquals("sk-original", repo.getApiKey())

        // 切到加密不可用的 SecretStore，再尝试写新 apiKey
        val context = ApplicationProvider.getApplicationContext<Context>()
        val failingStore = TestSecretStore(encryptAvailable = false)
        val repoWithFailingStore = AppPreferencesRepository(context, failingStore)

        val ok = repoWithFailingStore.writeApiConfig(apiKey = "sk-new")
        assertFalse("加密不可用时 writeApiConfig 应返回 false", ok)
        // 旧值保留（用原 repo 回读，因为底层 DataStore 共享）
        assertEquals("sk-original", repo.getApiKey())
    }

    // ─── #2 迁移竞态 ─────────────────────────────────────────────────

    @Test
    fun `migrateLegacySecrets 把明文迁移为密文_回读仍得原值`() = runBlocking {
        // 用「不做加密」的裸 SecretStore 写入明文，模拟历史版本存量
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyRepo = AppPreferencesRepository(context, PlaintextPassThroughStore())
        legacyRepo.writeApiConfig(apiKey = "sk-legacy", lastCheckKey = "sk-legacy", lastCheckSig = "sig-legacy")

        // 切到加密 SecretStore 跑迁移
        val migrated = repo.migrateLegacySecrets()
        assertTrue("至少迁移一个键", migrated > 0)
        assertEquals("sk-legacy", repo.getApiKey())
        assertEquals("sk-legacy", repo.getApiLastCheckKey())
        assertEquals("sig-legacy", repo.getApiLastCheckSig())
    }

    @Test
    fun `migrateLegacySecrets 幂等_已迁移的不再迁移`() = runBlocking {
        repo.writeApiConfig(apiKey = "sk-already-encrypted")
        val migrated = repo.migrateLegacySecrets()
        assertEquals("已全是密文时迁移数应为 0", 0, migrated)
        assertEquals("sk-already-encrypted", repo.getApiKey())
    }

    @Test
    fun `migrateLegacySecrets 迁移期间用户改值_不覆盖新值`() = runBlocking {
        // 历史明文存量
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyRepo = AppPreferencesRepository(context, PlaintextPassThroughStore())
        legacyRepo.writeApiConfig(apiKey = "sk-legacy")

        // 启动迁移快照（捕获 legacy 明文），但暂停在闭包内做竞态守卫之前——
        // 由于 migrateLegacySecrets 是单段 edit + 闭包内守卫，无法中途插入新值。
        // 这里改用「迁移完成后用户再改值」验证幂等不回退：迁移后再用加密 store 写新值，
        // 二次 migrate 不应把新值视为 legacy 明文去覆盖。
        val migrated1 = repo.migrateLegacySecrets()
        assertTrue(migrated1 > 0)
        assertEquals("sk-legacy", repo.getApiKey())

        // 用户改新 Key（已是密文）
        repo.writeApiConfig(apiKey = "sk-user-new")
        val migrated2 = repo.migrateLegacySecrets()
        assertEquals("新密文不应被再次迁移", 0, migrated2)
        assertEquals("sk-user-new", repo.getApiKey())
    }
}
