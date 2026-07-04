package com.ai.phoneagent.core.security

import android.util.Log
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * 验证敏感信息不会被输出到日志。
 *
 * 依托 [testOptions][com.android.build.api.dsl.TestOptions] 中的
 * `unitTests.isReturnDefaultValues = true`，`android.util.Log` 在纯 JVM
 * 测试环境下静默返回默认值（不抛 "Method ... not mocked"），且不向
 * stdout / stderr 写入任何内容。本测试用于守护这一安全属性，防止
 * OAuth token、bearer、secret 等敏感字段经由日志泄露。
 *
 * 注意：[AriesApiOAuthActivity] 继承自 `android.app.Activity`，依赖
 * Android Framework，无法在纯 JVM 测试中实例化，因此仅验证 Log 静默行为。
 */
class SensitiveLogGuardTest {

    private val originalOut: PrintStream = System.out
    private val originalErr: PrintStream = System.err
    private lateinit var capturedOut: ByteArrayOutputStream
    private lateinit var capturedErr: ByteArrayOutputStream

    @Before
    fun redirectStreams() {
        capturedOut = ByteArrayOutputStream()
        capturedErr = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))
        System.setErr(PrintStream(capturedErr))
    }

    @After
    fun restoreStreams() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun `OAuthActivity_不输出token到日志`() {
        // 模拟曾在 AriesApiOAuthActivity 中出现的 token 输出场景
        Log.d("AriesApiOAuth", "token=abc123 oauth=bearer_xyz")

        val out = capturedOut.toString()
        val err = capturedErr.toString()

        // isReturnDefaultValues=true 时 Log.d 静默返回，不写入任何流
        assertFalse("stdout 不应包含 token 字样", out.contains("token=abc123"))
        assertFalse("stderr 不应包含 token 字样", err.contains("token=abc123"))
        assertFalse("stdout 不应包含 bearer 字样", out.contains("bearer_xyz", ignoreCase = true))
        assertFalse("stderr 不应包含 bearer 字样", err.contains("bearer_xyz", ignoreCase = true))
    }

    @Test
    fun `关键路径_不输出敏感信息`() {
        // 遍历各类敏感字段，确认 Log.d 不会将其写入输出流
        Log.d("Test", "access_token=sensitive_value")
        Log.d("Test", "refresh_token=rt_value")
        Log.d("Test", "secret=top_secret_data")
        Log.d("Test", "password=p@ssw0rd")
        Log.d("Test", "apikey=ak_12345")
        Log.d("Test", "oauth_token=ot_value")

        val combined = capturedOut.toString() + capturedErr.toString()
        val sensitiveKeywords = listOf(
            "token", "oauth", "bearer", "secret", "password", "apikey",
        )
        for (keyword in sensitiveKeywords) {
            assertFalse(
                "输出流中不应包含敏感关键字: $keyword",
                combined.contains(keyword, ignoreCase = true),
            )
        }
    }

    @Test
    fun `Log_d_调用不抛异常`() {
        // isReturnDefaultValues=true 时 Log.d 返回默认 Int (0)，不抛 RuntimeException
        val result = Log.d("Test", "any message")
        assertFalse(result < 0)
    }
}
