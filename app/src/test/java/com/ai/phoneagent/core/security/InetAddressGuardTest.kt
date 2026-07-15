package com.ai.phoneagent.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * [InetAddressGuard] 单元测试。
 *
 * 使用纯 JVM JUnit 4 测试，覆盖 IPv4 / IPv6 / 域名解析场景，
 * 验证 SSRF 防护栏对内网 / 私有 / 链路本地 / 云元数据地址的拦截能力。
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

    @Test(timeout = 15000L)
    fun `公网域名_应通过`() {
        // 探测 DNS 是否可用，沙箱无网络时回退到公网 IP 验证
        val dnsOk = try {
            InetAddress.getByName("example.com")
            true
        } catch (e: UnknownHostException) {
            false
        }
        val target = if (dnsOk) "example.com" else "8.8.8.8"
        assertFalse(InetAddressGuard.isInternal(target))
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
