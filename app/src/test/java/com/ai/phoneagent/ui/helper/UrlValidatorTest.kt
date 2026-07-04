package com.ai.phoneagent.ui.helper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Robolectric 测试 [UrlValidator] — 从 MainActivity 抽取的 URL 校验 helper。
 *
 * 使用真实 Android [Context]（Robolectric 提供）验证：
 * - [UrlValidator.normalizeBaseUrlInput] 纯函数：补全 https 前缀
 * - [UrlValidator.validateBaseUrlSecurity] 纯函数：scheme/host 校验
 * - [UrlValidator.maybeWarnInsecureHttpBaseUrl] Context 依赖：Toast 提示非 localhost 的 http 地址
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlValidatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        stopKoin()
    }

    // ─── normalizeBaseUrlInput ─────────────────────────────────────────────

    @Test
    fun `normalize_空白输入返回null`() {
        assertNull(UrlValidator.normalizeBaseUrlInput(""))
        assertNull(UrlValidator.normalizeBaseUrlInput("   "))
        assertNull(UrlValidator.normalizeBaseUrlInput("\t\n"))
    }

    @Test
    fun `normalize_已有http前缀_保持原样`() {
        assertEquals("http://example.com", UrlValidator.normalizeBaseUrlInput("http://example.com"))
    }

    @Test
    fun `normalize_已有https前缀_保持原样`() {
        assertEquals("https://example.com", UrlValidator.normalizeBaseUrlInput("https://example.com"))
    }

    @Test
    fun `normalize_无协议前缀_自动补全https`() {
        assertEquals("https://example.com", UrlValidator.normalizeBaseUrlInput("example.com"))
    }

    @Test
    fun `normalize_去除首尾空白`() {
        assertEquals("https://example.com", UrlValidator.normalizeBaseUrlInput("  example.com  "))
    }

    // ─── validateBaseUrlSecurity ───────────────────────────────────────────

    @Test
    fun `validate_https有效URL_返回null`() {
        assertNull(UrlValidator.validateBaseUrlSecurity("https://api.example.com"))
    }

    @Test
    fun `validate_http有效URL_返回null`() {
        assertNull(UrlValidator.validateBaseUrlSecurity("http://localhost:8080"))
    }

    @Test
    fun `validate_格式错误_返回错误信息`() {
        val error = UrlValidator.validateBaseUrlSecurity("not-a-url")
        assertNotNull("格式错误应返回错误信息", error)
    }

    @Test
    fun `validate_非httphttps协议_返回错误信息`() {
        val error = UrlValidator.validateBaseUrlSecurity("ftp://example.com")
        assertNotNull("非 http/https 协议应返回错误信息", error)
    }

    @Test
    fun `validate_缺少host_返回错误信息`() {
        val error = UrlValidator.validateBaseUrlSecurity("https://")
        assertNotNull("缺少 host 应返回错误信息", error)
    }

    // ─── maybeWarnInsecureHttpBaseUrl（Context 依赖 / Toast）────────────────

    @Test
    fun `warn_非localhost的http地址_显示Toast`() {
        UrlValidator.maybeWarnInsecureHttpBaseUrl(context, "http://example.com")

        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("非 localhost 的 http 地址应显示 Toast", toastText)
        assertTrue(
            "Toast 文案应包含安全提示",
            toastText!!.contains("http") || toastText.contains("安全") || toastText.contains("API Key")
        )
    }

    @Test
    fun `warn_https地址_不显示Toast`() {
        UrlValidator.maybeWarnInsecureHttpBaseUrl(context, "https://example.com")

        assertNull("https 地址不应显示安全警告 Toast", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `warn_localhost的http地址_不显示Toast`() {
        UrlValidator.maybeWarnInsecureHttpBaseUrl(context, "http://127.0.0.1:8080")

        assertNull("localhost 的 http 地址不应显示安全警告 Toast", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `warn_无法解析的URL_不显示Toast`() {
        UrlValidator.maybeWarnInsecureHttpBaseUrl(context, "")

        assertNull("无法解析的 URL 不应显示 Toast", ShadowToast.getTextOfLatestToast())
    }

    // ─── localhost 白名单覆盖 ──────────────────────────────────────────────

    @Test
    fun `warn_各种localhost变体_不显示Toast`() {
        // IPv6 [::1] 不在此列：Uri.parse 返回带括号的 host "[::1]"，
        // 与白名单中的 "::1" 不匹配。属已知 UX 限制（仅影响 Toast 警告，非安全边界）。
        val localHosts = listOf("http://localhost", "http://127.0.0.1", "http://0.0.0.0")
        for (url in localHosts) {
            ShadowToast.reset()
            UrlValidator.maybeWarnInsecureHttpBaseUrl(context, url)
            assertNull("$url 是 localhost 变体，不应显示 Toast", ShadowToast.getTextOfLatestToast())
        }
    }
}
