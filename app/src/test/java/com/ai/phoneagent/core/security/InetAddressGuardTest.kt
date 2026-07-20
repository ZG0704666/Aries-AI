package com.ai.phoneagent.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Inet6Address

/**
 * [InetAddressGuard] 单元测试。
 *
 * 使用纯 JVM JUnit 4 测试，覆盖 IPv4 / IPv6 / 域名解析场景，
 * 验证 SSRF 防护栏对内网 / 私有 / 链路本地 / 云元数据地址的拦截能力。
 *
 * **可重复性**：不依赖真实 DNS。域名解析场景直接构造固定 [InetAddress] 后调用
 * [InetAddressGuard.requirePublic] 的注入重载，避免 VPN/代理将 example.com 解析到
 * 198.18.0.0/15 等基准测试段时误判为"公网"。
 */
class InetAddressGuardTest {

    // ========== 公网地址应通过 ==========

    @Test
    fun `公网IPv4_应通过`() {
        assertFalse(InetAddressGuard.isInternal("8.8.8.8"))
        assertFalse(InetAddressGuard.isInternal("1.1.1.1"))
    }

    @Test
    fun `保留IPv4网段相邻公网地址_应通过`() {
        assertFalse(InetAddressGuard.isInternal("192.0.1.1"))
        assertFalse(InetAddressGuard.isInternal("192.88.98.1"))
        assertFalse(InetAddressGuard.isInternal("198.51.99.1"))
        assertFalse(InetAddressGuard.isInternal("203.0.112.1"))
    }

    @Test
    fun `精确保留IPv4网段_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("192.0.0.1"))
        assertTrue(InetAddressGuard.isInternal("192.0.2.1"))
        assertTrue(InetAddressGuard.isInternal("192.88.99.1"))
        assertTrue(InetAddressGuard.isInternal("198.51.100.1"))
        assertTrue(InetAddressGuard.isInternal("203.0.113.1"))
    }

    @Test
    fun `公网域名_注入固定解析地址_应通过`() {
        // 不调真实 DNS：直接注入 example.com 的固定公网地址 93.184.216.34，
        // 保证 VPN/代理将 example.com 解析到 198.18.0.0/15 基准测试段时测试仍稳定通过。
        val fixedPublic = InetAddress.getByName("93.184.216.34")
        InetAddressGuard.requirePublic("example.com", listOf(fixedPublic))
        assertFalse(InetAddressGuard.isInternalAddress(fixedPublic))
    }

    @Test
    fun `公网域名_注入多解析地址_任一内网应拒绝`() {
        // 模拟" Split-Horizon DNS"：DNS 同时返回一个公网与一个内网地址，
        // 防护栏必须因任一内网地址而拒绝整个主机。
        val publicAddr = InetAddress.getByName("93.184.216.34")
        val internalAddr = InetAddress.getByName("10.0.0.1")
        assertThrows(SecurityException::class.java) {
            InetAddressGuard.requirePublic("example.com", listOf(publicAddr, internalAddr))
        }
    }

    // ========== RFC 1918 私有地址应拒绝 ==========

    @Test
    fun `RFC1918_10段_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("10.0.0.1"))
        assertTrue(InetAddressGuard.isInternal("10.255.255.255"))
    }

    @Test
    fun `RFC1918_172段_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("172.16.0.1"))
        assertTrue(InetAddressGuard.isInternal("172.31.255.255"))
    }

    @Test
    fun `RFC1918_192段_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("192.168.0.1"))
        assertTrue(InetAddressGuard.isInternal("192.168.1.100"))
    }

    // ========== 云元数据 / 链路本地应拒绝 ==========

    @Test
    fun `云元数据_169_254_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("169.254.169.254"))
    }

    @Test
    fun `IPv4映射IPv6_内嵌云元数据地址_应拒绝`() {
        val mappedMetadataAddress = Inet6Address.getByAddress(
            null,
            byteArrayOf(
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0xFF.toByte(), 0xFF.toByte(),
                169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()
            ),
            -1
        )

        assertTrue(InetAddressGuard.isInternalAddress(mappedMetadataAddress))
        assertThrows(SecurityException::class.java) {
            InetAddressGuard.requirePublic("metadata.example", listOf(mappedMetadataAddress))
        }
    }

    // ========== 回环应拒绝 ==========

    @Test
    fun `本地回环_127_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("127.0.0.1"))
        assertTrue(InetAddressGuard.isInternal("127.1.2.3"))
    }

    // ========== CGNAT 应拒绝 ==========

    @Test
    fun `CGNAT_100_64_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("100.64.0.1"))
    }

    // ========== IPv6 受限地址应拒绝 ==========

    @Test
    fun `IPv6_回环_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("::1"))
    }

    @Test
    fun `IPv6_链路本地_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("fe80::1"))
    }

    @Test
    fun `IPv6_站点本地_应拒绝`() {
        assertTrue(InetAddressGuard.isInternal("fd00::1"))
    }

    @Test
    fun `IPv6文档网段及相邻公网地址_应精确判断`() {
        assertTrue(InetAddressGuard.isInternal("2001:db8::1"))
        assertFalse(InetAddressGuard.isInternal("2001:db7::1"))
    }

    // ========== requirePublic 行为 ==========

    @Test
    fun `requirePublic_内网_应抛SecurityException`() {
        assertThrows(SecurityException::class.java) {
            InetAddressGuard.requirePublic("10.0.0.1")
        }
    }

    @Test
    fun `requirePublic_公网_应通过`() {
        // 不应抛出任何异常
        InetAddressGuard.requirePublic("8.8.8.8")
    }
}
