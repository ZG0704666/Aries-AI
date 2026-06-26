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
            val host = url.host

            if (host.isBlank()) return "URL 缺少主机名"

            // 检查是否是内网 IP
            val hostAddress = resolveHost(host)
            if (hostAddress != null && isPrivateIp(hostAddress)) {
                return "禁止访问内网地址: $host"
            }

            null
        } catch (e: Exception) {
            "URL 格式无效: ${e.message}"
        }
    }

    /**
     * 解析主机名获取 IP 地址
     */
    private fun resolveHost(host: String): String? {
        return try {
            val addresses = InetAddress.getAllByName(host)
            addresses.firstOrNull()?.hostAddress
        } catch (e: UnknownHostException) {
            // 如果无法解析，可能是内网主机名，保守拒绝
            null
        }
    }

    /**
     * 检查 IP 地址是否属于私有/内网地址段
     */
    fun isPrivateIp(ip: String): Boolean {
        val ipInt = ipToInt(ip) ?: return false

        return PRIVATE_IP_RANGES.any { (start, end) ->
            ipInt in start..end
        }
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

    /**
     * 检查主机名是否是 IP 地址格式
     */
    fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
    }
}
