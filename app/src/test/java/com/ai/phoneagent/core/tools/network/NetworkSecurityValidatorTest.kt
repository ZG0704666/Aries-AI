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

import org.junit.Assert.*
import org.junit.Test

/**
 * 网络安全校验器单元测试。
 *
 * 覆盖：
 * - 各内网 IPv4 段的边界值（含刚好在内/外的临界点）；
 * - IPv6 地址的当前处理行为（ipToInt 仅解析 IPv4，IPv6 一律视为非私有）；
 * - 端口号处理（端口不应绕过主机 IP 校验）；
 * - URL 编码（编码后的路径/查询不影响主机判定）；
 * - 格式错误的 IP 与 URL。
 *
 * 注意：validateUrl 对 IP 字面量不会触发 DNS 查询（Java 直接本地解析），
 * 因此基于 IP 字面量的 validateUrl 测试在离线环境下稳定。
 */
class NetworkSecurityValidatorTest {

    @Test
    fun `blank URL is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("")
        assertNotNull(error)
        assertTrue(error!!.contains("URL 不能为空"))
    }

    @Test
    fun `private IP 10 x is detected`() {
        assertTrue(NetworkSecurityValidator.isPrivateIp("10.0.0.1"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("10.255.255.255"))
    }

    @Test
    fun `private IP 172 16-31 x is detected`() {
        assertTrue(NetworkSecurityValidator.isPrivateIp("172.16.0.1"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("172.31.255.255"))
    }

    @Test
    fun `private IP 192 168 x is detected`() {
        assertTrue(NetworkSecurityValidator.isPrivateIp("192.168.1.1"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("192.168.0.0"))
    }

    @Test
    fun `loopback IP 127 x is detected`() {
        assertTrue(NetworkSecurityValidator.isPrivateIp("127.0.0.1"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("127.255.255.255"))
    }

    @Test
    fun `link-local IP 169 254 x is detected`() {
        assertTrue(NetworkSecurityValidator.isPrivateIp("169.254.1.1"))
    }

    @Test
    fun `public IP is not private`() {
        assertFalse(NetworkSecurityValidator.isPrivateIp("8.8.8.8"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("1.1.1.1"))
    }

    @Test
    fun `invalid IP returns false`() {
        assertFalse(NetworkSecurityValidator.isPrivateIp("invalid"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("999.999.999.999"))
    }

    @Test
    fun `isIpAddress detects IP format`() {
        assertTrue(NetworkSecurityValidator.isIpAddress("192.168.1.1"))
        assertFalse(NetworkSecurityValidator.isIpAddress("example.com"))
    }

    @Test
    fun `validateUrl rejects invalid format`() {
        val error = NetworkSecurityValidator.validateUrl("not-a-url")
        assertNotNull(error)
    }

    // ========== 内网 IP 段边界值 ==========

    @Test
    fun `10 0 0 0 slash 8 range boundaries`() {
        // 10.0.0.0/8 范围内
        assertTrue(NetworkSecurityValidator.isPrivateIp("10.0.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("10.255.255.255"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("10.128.0.1"))
        // 边界外（应为公网）
        assertFalse(NetworkSecurityValidator.isPrivateIp("9.255.255.255"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("11.0.0.0"))
    }

    @Test
    fun `172 16 0 0 slash 12 range boundaries`() {
        // 172.16.0.0/12 范围内（172.16.0.0 ~ 172.31.255.255）
        assertTrue(NetworkSecurityValidator.isPrivateIp("172.16.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("172.31.255.255"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("172.23.45.67"))
        // 边界外
        assertFalse(NetworkSecurityValidator.isPrivateIp("172.15.255.255"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("172.32.0.0"))
    }

    @Test
    fun `192 168 0 0 slash 16 range boundaries`() {
        // 192.168.0.0/16 范围内
        assertTrue(NetworkSecurityValidator.isPrivateIp("192.168.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("192.168.255.255"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("192.168.100.1"))
        // 边界外
        assertFalse(NetworkSecurityValidator.isPrivateIp("192.167.255.255"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("192.169.0.0"))
    }

    @Test
    fun `127 0 0 0 slash 8 loopback range boundaries`() {
        // 127.0.0.0/8 范围内
        assertTrue(NetworkSecurityValidator.isPrivateIp("127.0.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("127.255.255.255"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("127.1.2.3"))
        // 边界外
        assertFalse(NetworkSecurityValidator.isPrivateIp("126.255.255.255"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("128.0.0.0"))
    }

    @Test
    fun `169 254 0 0 slash 16 link-local range boundaries`() {
        // 169.254.0.0/16 范围内
        assertTrue(NetworkSecurityValidator.isPrivateIp("169.254.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("169.254.255.255"))
        // 边界外
        assertFalse(NetworkSecurityValidator.isPrivateIp("169.253.255.255"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("169.255.0.0"))
    }

    @Test
    fun `0 0 0 0 slash 8 range boundaries`() {
        // 0.0.0.0/8 范围内（“本网络”）
        assertTrue(NetworkSecurityValidator.isPrivateIp("0.0.0.0"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("0.255.255.255"))
        assertTrue(NetworkSecurityValidator.isPrivateIp("0.1.2.3"))
        // 边界外
        assertFalse(NetworkSecurityValidator.isPrivateIp("1.0.0.0"))
    }

    @Test
    fun `all private ranges are detected comprehensively`() {
        // 综合各段代表性地址
        val privateIps = listOf(
            "10.0.0.1", "172.16.0.1", "172.31.255.255",
            "192.168.1.1", "127.0.0.1", "169.254.1.1", "0.0.0.0"
        )
        for (ip in privateIps) {
            assertTrue("应识别为内网: $ip", NetworkSecurityValidator.isPrivateIp(ip))
        }
    }

    // ========== IPv6 地址处理 ==========
    // 当前实现 ipToInt 仅解析 IPv4（4 段点分十进制），IPv6 会被视为非法格式。
    // 这里记录当前行为：IPv6 一律返回 false（非私有），需在后续迭代补齐 IPv6 检测。

    @Test
    fun `ipv6 loopback is not detected as private due to ipv4-only parsing`() {
        // ::1 是 IPv6 环回，但当前实现不识别 IPv6
        assertFalse(NetworkSecurityValidator.isPrivateIp("::1"))
    }

    @Test
    fun `ipv6 unique local address is not detected as private`() {
        // fc00::/7 是 IPv6 唯一本地地址（类似 IPv4 私网），当前实现不识别
        assertFalse(NetworkSecurityValidator.isPrivateIp("fc00::1"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("fd12:3456:789a::1"))
    }

    @Test
    fun `ipv6 link-local address is not detected as private`() {
        // fe80::/10 是 IPv6 链路本地地址，当前实现不识别
        assertFalse(NetworkSecurityValidator.isPrivateIp("fe80::1"))
    }

    @Test
    fun `ipv4-mapped ipv6 is not detected as private`() {
        // ::ffff:10.0.0.1 是 IPv4 映射的 IPv6，当前实现不识别
        assertFalse(NetworkSecurityValidator.isPrivateIp("::ffff:10.0.0.1"))
    }

    @Test
    fun `isIpAddress rejects ipv6 format`() {
        // isIpAddress 仅匹配 IPv4 正则
        assertFalse(NetworkSecurityValidator.isIpAddress("::1"))
        assertFalse(NetworkSecurityValidator.isIpAddress("fe80::1"))
        assertFalse(NetworkSecurityValidator.isIpAddress("2001:db8::1"))
    }

    // ========== 端口号处理 ==========

    @Test
    fun `validateUrl with port still checks host IP`() {
        // 带端口的内网 IP 仍应被拒绝
        val error = NetworkSecurityValidator.validateUrl("http://10.0.0.1:8080")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with port and private IP in non-default port`() {
        val error = NetworkSecurityValidator.validateUrl("http://192.168.1.1:443")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with port and loopback IP is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("http://127.0.0.1:3000")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with high port and private IP is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("http://172.16.0.1:65535")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with port and public IP literal is allowed`() {
        // IP 字面量不触发 DNS；8.8.8.8 为公网地址，应放行
        val error = NetworkSecurityValidator.validateUrl("http://8.8.8.8:53")
        assertNull(error)
    }

    // ========== URL 编码 ==========

    @Test
    fun `validateUrl with encoded path does not bypass host check`() {
        // 路径中的 URL 编码不应绕过主机 IP 校验
        val error = NetworkSecurityValidator.validateUrl("http://10.0.0.1/%2e%2e/etc/passwd")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with encoded query and private IP is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("http://192.168.0.1/?redirect=%2Fadmin")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with null byte encoded in path is rejected`() {
        // %00 编码的 null 字节注入尝试
        val error = NetworkSecurityValidator.validateUrl("http://10.0.0.1/file%00.txt")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with userinfo and private IP is rejected`() {
        // 带 userinfo 的内网 IP 仍应被拒绝
        val error = NetworkSecurityValidator.validateUrl("http://user:pass@10.0.0.1/admin")
        assertNotNull(error)
        assertTrue(error!!.contains("禁止访问内网地址"))
    }

    @Test
    fun `validateUrl with encoded host percent does not crash`() {
        // 主机名带百分号编码的异常输入不应导致崩溃
        // 注意：%31%30%2e%30%2e%30%2e%31 解码后为 "10.0.0.1"，但 java.net.URL 不会
        // 自动解码 host 字段的百分号编码，因此主机无法解析、resolveHost 返回 null，
        // validateUrl 视为“无法判定为内网”而放行（返回 null）。本测试仅验证不抛异常。
        val error = NetworkSecurityValidator.validateUrl("http://%31%30%2e%30%2e%30%2e%31/")
        // 无论返回 null 还是非 null，关键是不抛异常
        // 当前实现返回 null（无法解析主机时保守放行）
        assertNull(error)
    }

    // ========== 格式错误与边界 ==========

    @Test
    fun `ip with wrong segment count is not private`() {
        assertFalse(NetworkSecurityValidator.isPrivateIp("192.168.1"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("192.168.1.1.1"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("10"))
        assertFalse(NetworkSecurityValidator.isPrivateIp(""))
    }

    @Test
    fun `ip with out-of-range octets is not private`() {
        assertFalse(NetworkSecurityValidator.isPrivateIp("256.0.0.1"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("10.0.0.256"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("-1.0.0.0"))
        assertFalse(NetworkSecurityValidator.isPrivateIp("10.0.0.999"))
    }

    @Test
    fun `isIpAddress detects various ipv4 formats`() {
        assertTrue(NetworkSecurityValidator.isIpAddress("0.0.0.0"))
        assertTrue(NetworkSecurityValidator.isIpAddress("255.255.255.255"))
        assertTrue(NetworkSecurityValidator.isIpAddress("1.2.3.4"))
        // 正则 \d{1,3} 会匹配前导零（当前实现不校验八位段范围）
        assertTrue(NetworkSecurityValidator.isIpAddress("01.02.03.04"))
        // 多余/缺少点号不匹配
        assertFalse(NetworkSecurityValidator.isIpAddress("192.168.1.1."))
        assertFalse(NetworkSecurityValidator.isIpAddress(".192.168.1.1"))
    }

    @Test
    fun `validateUrl rejects url without host`() {
        val error = NetworkSecurityValidator.validateUrl("http://")
        assertNotNull(error)
    }

    @Test
    fun `validateUrl with only scheme is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("ftp://")
        assertNotNull(error)
    }

    @Test
    fun `validateUrl with whitespace only is rejected`() {
        val error = NetworkSecurityValidator.validateUrl("   ")
        assertNotNull(error)
        assertTrue(error!!.contains("URL 不能为空"))
    }
}
