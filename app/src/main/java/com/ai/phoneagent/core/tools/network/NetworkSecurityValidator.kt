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
package com.ai.phoneagent.core.tools.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * 网络安全校验器
 * 过滤内网 IP 地址段，防止 SSRF 攻击
 */
object NetworkSecurityValidator {

    // 私有 IP 地址段
    private val PRIVATE_IP_RANGES = listOf(
        // 10.0.0.0/8
        Pair(0x0A000000L, 0x0AFFFFFFL),
        // 172.16.0.0/12
        Pair(0xAC100000L, 0xAC1FFFFFL),
        // 192.168.0.0/16
        Pair(0xC0A80000L, 0xC0A8FFFFL),
        // 127.0.0.0/8 (loopback)
        Pair(0x7F000000L, 0x7FFFFFFFL),
        // 169.254.0.0/16 (link-local)
        Pair(0xA9FE0000L, 0xA9FEFFFFL),
        // 0.0.0.0/8
        Pair(0x00000000L, 0x00FFFFFFL)
    )

    /**
     * 校验 URL 是否安全
     * @param urlString 待校验的 URL
     * @return null 表示安全，非 null 表示错误信息
     */
    fun validateUrl(urlString: String): String? {
        if (urlString.isBlank()) return "URL 不能为空"

        return try {
            val url = URL(urlString)
            val scheme = url.protocol?.lowercase().orEmpty()
            if (scheme != "http" && scheme != "https") {
                return "仅允许 HTTP/HTTPS URL"
            }

            val host = url.host
            if (host.isBlank()) return "URL 缺少主机名"

            validateHost(host)
        } catch (e: Exception) {
            "URL 格式无效: ${e.message}"
        }
    }

    /**
     * 校验主机名解析结果是否安全。
     *
     * 无法解析、编码主机名、任一解析地址为内网/本机地址时均拒绝，避免 SSRF 绕过。
     */
    fun validateHost(host: String): String? {
        if (host.isBlank()) return "主机名不能为空"

        val normalizedHost = normalizeHost(host) ?: return "主机名格式无效: $host"
        if (normalizedHost.contains('%')) return "主机名包含非法编码: $host"

        val addresses = resolveHost(normalizedHost)
            ?: return "无法解析主机名: $host"
        if (addresses.isEmpty()) return "无法解析主机名: $host"

        val privateAddress = addresses.firstOrNull { isPrivateAddress(it) }
        if (privateAddress != null) {
            return "禁止访问内网地址: $host"
        }

        return null
    }

    /**
     * OkHttp DNS，确保请求实际解析时也执行同一套安全校验，降低 DNS rebinding 风险。
     */
    fun safeDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val normalizedHost = normalizeHost(hostname)
                ?: throw UnknownHostException("主机名格式无效: $hostname")
            val error = validateHost(normalizedHost)
            if (error != null) throw UnknownHostException(error)
            return InetAddress.getAllByName(normalizedHost).toList()
        }
    }

    private fun normalizeHost(host: String): String? {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    /**
     * 解析主机名获取全部 IP 地址
     */
    private fun resolveHost(host: String): List<InetAddress>? {
        return try {
            InetAddress.getAllByName(host).toList()
        } catch (e: UnknownHostException) {
            null
        }
    }

    /**
     * 检查 IP 地址是否属于私有/内网地址段
     */
    fun isPrivateIp(ip: String): Boolean {
        val normalized = normalizeHost(ip) ?: return false
        if (!isIpAddress(normalized) && !normalized.contains(':')) return false
        return try {
            isPrivateAddress(InetAddress.getByName(normalized))
        } catch (e: UnknownHostException) {
            false
        }
    }

    /**
     * 检查解析后的地址是否属于内网、环回、链路本地、本机或 IPv6 ULA 地址。
     */
    fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) {
            return true
        }

        val bytes = address.address
        if (bytes.size == 4) {
            val ipInt = ipv4BytesToLong(bytes)
            return PRIVATE_IP_RANGES.any { (start, end) -> ipInt in start..end }
        }

        if (bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            val secondByte = bytes[1].toInt() and 0xFF

            // fc00::/7 — IPv6 Unique Local Address
            if ((firstByte and 0xFE) == 0xFC) return true

            // fe80::/10 — IPv6 link-local（isLinkLocalAddress 理论上已覆盖，显式保留）
            if (firstByte == 0xFE && (secondByte and 0xC0) == 0x80) return true

            // ::ffff:IPv4 — IPv4-mapped IPv6，显式检查嵌入的 IPv4 地址
            val isIpv4Mapped = bytes.take(10).all { it.toInt() == 0 } &&
                (bytes[10].toInt() and 0xFF) == 0xFF &&
                (bytes[11].toInt() and 0xFF) == 0xFF
            if (isIpv4Mapped) {
                val mappedIp = ipv4BytesToLong(bytes.copyOfRange(12, 16))
                return PRIVATE_IP_RANGES.any { (start, end) -> mappedIp in start..end }
            }
        }

        return false
    }

    /**
     * 将 IP 地址字符串转换为整数
     */
    private fun ipToInt(ip: String): Long? {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return null

            var result = 0L
            for (part in parts) {
                val num = part.toInt()
                if (num < 0 || num > 255) return null
                result = (result shl 8) or num.toLong()
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun ipv4BytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (byte in bytes) {
            result = (result shl 8) or (byte.toInt() and 0xFF).toLong()
        }
        return result
    }

    /**
     * 检查主机名是否是 IP 地址格式
     */
    fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
    }
}
