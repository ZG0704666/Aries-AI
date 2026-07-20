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
package com.ai.phoneagent.core.security

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 网络地址安全护栏。
 *
 * 防止 SSRF（服务端请求伪造）：在执行 HTTP / 下载等出站网络操作前，
 * 校验目标主机解析后的 IP 是否属于内网 / 私有 / 链路本地 / 云元数据等受限地址段，
 * 若是则拒绝访问。
 *
 * 覆盖的受限地址段：
 * - IPv4: `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`（RFC 1918 私有）
 * - IPv4: `169.254.0.0/16`（链路本地，含云元数据 `169.254.169.254`）
 * - IPv4: `127.0.0.0/8`（回环）、`0.0.0.0/8`（本网络）、`100.64.0.0/10`（CGNAT）
 * - IPv6: `::1/128`（回环）、`fe80::/10`（链路本地）、`fc00::/7`（站点本地 ULA）、`ff00::/8`（组播）
 * - IPv4-mapped IPv6 / IPv4-compatible IPv6 中的受限 IPv4 地址同样拦截
 */
object InetAddressGuard {

    private const val TAG = "InetAddressGuard"

    /**
     * 判断 [host] 解析后的地址是否为内网 / 私有 / 受限地址。
     *
     * 实现说明：
     * - 使用 [InetAddress.getByName] 解析 [host]，主机名为域名时会触发 DNS 查询。
     * - 当 [host] 无法解析（[UnknownHostException]）时返回 `true`（拒绝执行），
     *   并打印警告日志——宁可误拒也不放过可疑目标。
     * - 对 IP 字面量（如 `10.0.0.1`）不会发起 DNS 请求，直接解析。
     *
     * @param host 主机名或 IP 字面量
     * @return `true` 表示目标为内网 / 受限地址，应拒绝访问；`false` 表示为公网地址
     */
    fun isInternal(host: String): Boolean {
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (e: UnknownHostException) {
            warn("无法解析主机 '$host'，按内网拒绝: ${e.message}")
            return true
        }
        return addresses.isEmpty() || addresses.any(::isInternalAddress)
    }

    /**
     * 要求 [host] 解析后的地址为公网地址，否则抛出 [SecurityException]。
     *
     * 供 [com.ai.phoneagent.core.tools.network.NetworkToolExecutor] 等出站工具在发起
     * 网络请求前调用，作为 SSRF 防护的最后一道闸门。
     *
     * @param host 主机名或 IP 字面量
     * @throws SecurityException 当 [host] 解析为内网 / 受限地址时
     */
    fun requirePublic(host: String) {
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (e: UnknownHostException) {
            throw SecurityException("Host '$host' cannot be resolved", e)
        }
        requirePublic(host, addresses)
    }

    /**
     * 校验 DNS 返回的完整地址集合。只要其中任一地址不可公开路由，就拒绝整个请求。
     */
    fun requirePublic(host: String, addresses: List<InetAddress>) {
        if (addresses.isEmpty()) {
            throw SecurityException("Host '$host' resolved to no addresses")
        }
        val blocked = addresses.firstOrNull(::isInternalAddress)
        if (blocked != null) {
            throw SecurityException(
                "Host '$host' resolves to blocked address ${blocked.hostAddress}"
            )
        }
    }

    // ============ 内部实现 ============

    internal fun isInternalAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        return when (bytes.size) {
            4 -> isPrivateIPv4(bytes)
            16 -> isPrivateIPv6(bytes)
            else -> true // 未知地址格式，安全起见拒绝
        }
    }

    private fun isPrivateIPv4(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF
        return when {
            b0 == 0 -> true                            // 0.0.0.0/8    本网络
            b0 == 10 -> true                           // 10.0.0.0/8   RFC 1918 私有
            b0 == 127 -> true                          // 127.0.0.0/8  回环
            b0 == 169 && b1 == 254 -> true             // 169.254.0.0/16 链路本地（含云元数据）
            b0 == 172 && (b1 and 0xF0) == 0x10 -> true // 172.16.0.0/12 RFC 1918 私有
            b0 == 192 && b1 == 0 && b2 == 0 -> true    // 192.0.0.0/24 IETF 协议分配
            b0 == 192 && b1 == 0 && b2 == 2 -> true    // 192.0.2.0/24 文档地址
            b0 == 192 && b1 == 168 -> true             // 192.168.0.0/16 RFC 1918 私有
            b0 == 192 && b1 == 88 && b2 == 99 -> true  // 192.88.99.0/24 已弃用中继
            b0 == 198 && (b1 == 18 || b1 == 19) -> true // 198.18.0.0/15 基准测试
            b0 == 198 && b1 == 51 && b2 == 100 -> true // 198.51.100.0/24 文档地址
            b0 == 203 && b1 == 0 && b2 == 113 -> true  // 203.0.113.0/24 文档地址
            b0 == 100 && (b1 and 0xC0) == 0x40 -> true // 100.64.0.0/10 CGNAT (RFC 6598)
            b0 >= 224 -> true                           // 组播、保留及广播
            else -> false
        }
    }

    private fun isPrivateIPv6(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        return when {
            b0 == 0xFF -> true                         // ff00::/8   组播
            (b0 and 0xFE) == 0xFC -> true              // fc00::/7   站点本地 ULA
            b0 == 0xFE && (b1 and 0xC0) == 0x80 -> true // fe80::/10 链路本地
            b0 == 0x20 && b1 == 0x01 &&
                (b[2].toInt() and 0xFF) == 0x0D &&
                (b[3].toInt() and 0xFF) == 0xB8 -> true // 2001:db8::/32 文档地址
            b0 == 0x00 -> checkIPv6ZeroPrefix(b)        // ::1 / :: / IPv4-mapped / IPv4-compat
            else -> false
        }
    }

    /**
     * 处理首字节为 0 的 IPv6 地址：
     * - `::1`（回环）、`::`（任意本地）→ 拒绝
     * - `::ffff:a.b.c.d`（IPv4-mapped）→ 按内嵌 IPv4 判定
     * - `::a.b.c.d`（IPv4-compatible，已废弃）→ 按内嵌 IPv4 判定
     */
    private fun checkIPv6ZeroPrefix(b: ByteArray): Boolean {
        // ::1 / :: — 除末字节外全 0
        if ((0..14).all { b[it].toInt() == 0 }) {
            val last = b[15].toInt() and 0xFF
            return last == 0 || last == 1
        }
        // ::ffff:a.b.c.d — 字节 0-9 为 0，字节 10-11 为 0xFF
        if ((0..9).all { b[it].toInt() == 0 } &&
            (b[10].toInt() and 0xFF) == 0xFF &&
            (b[11].toInt() and 0xFF) == 0xFF
        ) {
            return isPrivateIPv4(b.copyOfRange(12, 16))
        }
        // ::a.b.c.d — 字节 0-11 为 0（已废弃的 IPv4-compatible）
        if ((0..11).all { b[it].toInt() == 0 }) {
            return isPrivateIPv4(b.copyOfRange(12, 16))
        }
        return false
    }

    /**
     * 打印警告日志。
     *
     * JVM 单元测试环境下 `android.util.Log` 未 mock，调用会抛
     * "Method ... not mocked" 异常，此时回退到 [System.err] 以保证测试可用。
     */
    private fun warn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (e: Throwable) {
            System.err.println("W/$TAG: $message")
        }
    }
}
