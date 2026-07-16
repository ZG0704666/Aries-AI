package com.ai.phoneagent.data.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM 密文负载编解码器。
 *
 * 负载格式：`aes-gcm-v1:<base64(iv)>:<base64(ciphertext+tag)>`
 * - GCM tag 128 bit；
 * - 每次加密使用 Cipher 自动生成的随机 IV（要求密钥来源启用 randomized encryption）。
 *
 * 本类不依赖 Android Framework（密钥由调用方提供，Base64 使用 java.util），
 * 可在 JVM 单测中直接验证；Android 侧由 [AndroidKeyStoreSecretStore] 注入
 * AndroidKeyStore 托管的密钥。
 */
internal class GcmSecretPayloadCodec(
    private val key: SecretKey,
) {

    /** 加密明文；失败时返回 null，绝不抛出。 */
    fun encrypt(plainText: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            PREFIX + encode(cipher.iv) + ":" + encode(encrypted)
        }.getOrNull()
    }

    /** 解读存储值（仅处理带前缀的密文）。 */
    fun decrypt(storedValue: String): SecretStore.ReadResult {
        val payload = storedValue.removePrefix(PREFIX)
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return SecretStore.ReadResult.Corrupt
        return runCatching {
            val iv = decode(parts[0])
            val encrypted = decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            SecretStore.ReadResult.Available(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        }.getOrElse { SecretStore.ReadResult.Corrupt }
    }

    companion object {
        const val PREFIX = "aes-gcm-v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private fun encode(bytes: ByteArray): String =
            Base64.getEncoder().withoutPadding().encodeToString(bytes)

        private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
    }
}