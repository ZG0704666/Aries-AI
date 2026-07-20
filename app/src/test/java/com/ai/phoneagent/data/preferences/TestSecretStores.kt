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

import com.ai.phoneagent.data.security.GcmSecretPayloadCodec
import com.ai.phoneagent.data.security.SecretStore
import javax.crypto.KeyGenerator

/**
 * 测试专用 [SecretStore]：底层用 [GcmSecretPayloadCodec] + 注入式 AES-256 key，
 * 纯 JVM 可跑，不依赖 AndroidKeyStore。
 *
 * @param encryptAvailable 是否启用加密；`false` 时 [encrypt] 返回 null、[decrypt] 返回
 *   [SecretStore.ReadResult.Unavailable]，用于验证 `writeApiConfig` 失败分支与旧值保留。
 */
internal class TestSecretStore(
    encryptAvailable: Boolean = true,
) : SecretStore {

    private val codec: GcmSecretPayloadCodec? =
        if (encryptAvailable) GcmSecretPayloadCodec(newKey()) else null

    override fun encrypt(plainText: String): String? = codec?.encrypt(plainText)

    override fun decrypt(storedValue: String?): SecretStore.ReadResult {
        if (storedValue.isNullOrBlank()) return SecretStore.ReadResult.Missing
        if (!storedValue.startsWith(GcmSecretPayloadCodec.PREFIX)) {
            return SecretStore.ReadResult.Available(storedValue)
        }
        val c = codec ?: return SecretStore.ReadResult.Unavailable
        return c.decrypt(storedValue)
    }

    override fun isLegacyPlaintext(storedValue: String?): Boolean =
        !storedValue.isNullOrBlank() && !storedValue.startsWith(GcmSecretPayloadCodec.PREFIX)

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

/**
 * 测试专用 [SecretStore]：不做任何加密，原样放行明文，用于构造历史版本明文存量。
 */
internal class PlaintextPassThroughStore : SecretStore {

    override fun encrypt(plainText: String): String? = plainText

    override fun decrypt(storedValue: String?): SecretStore.ReadResult {
        if (storedValue.isNullOrBlank()) return SecretStore.ReadResult.Missing
        return SecretStore.ReadResult.Available(storedValue)
    }

    override fun isLegacyPlaintext(storedValue: String?): Boolean =
        !storedValue.isNullOrBlank()
}
