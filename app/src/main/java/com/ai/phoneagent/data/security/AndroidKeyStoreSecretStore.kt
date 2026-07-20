package com.ai.phoneagent.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 基于 AndroidKeyStore 的 [SecretStore] 实现。
 *
 * - 密钥由 AndroidKeyStore 生成并托管（AES-256-GCM，启用 randomized encryption），永不进入应用内存之外的存储；
 * - Keystore 不可用时：加密返回 null（写入方放弃本次写入、保留旧值），解密返回
 *   [SecretStore.ReadResult.Unavailable]（密文保留，用户可重试或重新输入）；
 * - 密文格式非法/校验失败返回 [SecretStore.ReadResult.Corrupt]（密文保留，等待用户重新输入覆盖）；
 * - 任何路径都不抛出异常、不导致崩溃。
 */
class AndroidKeyStoreSecretStore : SecretStore {

    @Volatile
    private var codec: GcmSecretPayloadCodec? = null

    override fun encrypt(plainText: String): String? {
        val codec = codecOrNull() ?: return null
        return codec.encrypt(plainText)
    }

    override fun decrypt(storedValue: String?): SecretStore.ReadResult {
        if (storedValue.isNullOrBlank()) return SecretStore.ReadResult.Missing
        if (!storedValue.startsWith(GcmSecretPayloadCodec.PREFIX)) {
            // 历史明文：原样放行，供读取与迁移。
            return SecretStore.ReadResult.Available(storedValue)
        }
        val codec = codecOrNull() ?: return SecretStore.ReadResult.Unavailable
        return codec.decrypt(storedValue)
    }

    override fun isLegacyPlaintext(storedValue: String?): Boolean {
        return !storedValue.isNullOrBlank() && !storedValue.startsWith(GcmSecretPayloadCodec.PREFIX)
    }

    private fun codecOrNull(): GcmSecretPayloadCodec? {
        codec?.let { return it }
        return synchronized(this) {
            codec ?: runCatching { GcmSecretPayloadCodec(getOrCreateKey()) }
                .onFailure { Log.w(TAG, "AndroidKeyStore unavailable", it) }
                .getOrNull()
                ?.also { codec = it }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "AndroidKeyStoreSecrets"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "aries_ai_preference_secrets"
    }
}