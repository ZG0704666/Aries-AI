package com.ai.phoneagent.data.security

/**
 * 敏感配置（如 API Key）的加密存储抽象。
 *
 * 读取结果是显式的，调用方可以区分"从未设置"与"存了但解不开"：
 * 解不开时密文必须保留，让用户重新输入覆盖，而不是静默丢失。
 */
interface SecretStore {

    /** 读取一个敏感配置的显式结果。 */
    sealed interface ReadResult {
        /** 成功取得明文。 */
        data class Available(val value: String) : ReadResult

        /** 没有存储任何值。 */
        data object Missing : ReadResult

        /**
         * 有密文，但解密当前不可用（例如 Keystore 暂时失败）。
         * 调用方应保留密文，稍后重试或提示用户重新输入。
         */
        data object Unavailable : ReadResult

        /**
         * 有密文但已损坏（格式非法、被篡改或密钥已永久丢失）。
         * 调用方应保留密文，等待用户重新输入覆盖。
         */
        data object Corrupt : ReadResult
    }

    /**
     * 加密明文，返回可持久化的密文负载。
     *
     * @return 密文负载；加密不可用（如 Keystore 失败）时返回 `null`，绝不抛出异常。
     */
    fun encrypt(plainText: String): String?

    /**
     * 解读存储值。
     *
     * 为兼容历史版本，没有密文前缀的值按 [ReadResult.Available] 原样返回（视为明文），
     * 供迁移流程读取后再加密。
     */
    fun decrypt(storedValue: String?): ReadResult

    /** 是否为尚未加密的历史明文（非空且不带密文前缀）。 */
    fun isLegacyPlaintext(storedValue: String?): Boolean
}