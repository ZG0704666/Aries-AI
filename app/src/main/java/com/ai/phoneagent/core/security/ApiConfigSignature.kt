package com.ai.phoneagent.core.security

import java.security.MessageDigest

/**
 * API 配置变更检测签名工具。
 *
 * 签名仅用于「配置是否与上次成功校验时一致」的相等性比较，因此不应保留原始
 * API Key：密钥材料经 SHA-256 单向哈希后参与签名，避免可还原的 Key 出现在任何
 * 存储介质、日志或调试输出中（PR #17 安全要求；PR #25 review 阻断项 #1）。
 *
 * v1 签名直接拼接原始 Key（`mode|apiKey|baseUrl|model`），已废弃。升级后旧签名
 * 与新签名不相等，仅触发一次后台重新校验，无数据丢失。
 */
object ApiConfigSignature {

    /** 签名格式版本：v2 = 密钥材料 SHA-256 哈希化。 */
    private const val VERSION = "v2"

    /**
     * 计算配置签名。
     *
     * @param apiKey 原始 API Key（仅参与哈希，不出现在返回值中）
     * @param baseUrl 已归一化的 Base URL
     * @param model 已归一化的模型名
     * @param mode API 模式标识（aries / third_party / default）
     * @return 形如 `v2|<mode>|<sha256-hex>` 的签名，不含可还原密钥材料
     */
    fun compute(apiKey: String, baseUrl: String, model: String, mode: String): String {
        val material = "$mode|${apiKey.trim()}|$baseUrl|$model"
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
        return "$VERSION|$mode|" + digest.joinToString("") { "%02x".format(it) }
    }
}
