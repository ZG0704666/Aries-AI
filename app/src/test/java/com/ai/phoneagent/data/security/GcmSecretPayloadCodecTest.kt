package com.ai.phoneagent.data.security

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * JVM round-trip tests for [GcmSecretPayloadCodec].
 *
 * The codec is Android-free (key is injected), so the full AES-256-GCM payload
 * contract can be verified without AndroidKeyStore.
 */
class GcmSecretPayloadCodecTest {

    private fun newKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return generator.generateKey()
    }

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val codec = GcmSecretPayloadCodec(newKey())
        val secret = "sk-test-1234567890-abcdef"

        val payload = codec.encrypt(secret)

        assertNotNull(payload)
        val result = codec.decrypt(payload)
        assertIs<SecretStore.ReadResult.Available>(result)
        assertEquals(secret, result.value)
    }

    @Test
    fun `encrypt produces versioned payload with iv and ciphertext parts`() {
        val codec = GcmSecretPayloadCodec(newKey())

        val payload = codec.encrypt("hello")

        assertNotNull(payload)
        assertTrue(payload.startsWith(GcmSecretPayloadCodec.PREFIX), "payload must carry version prefix")
        val parts = payload.removePrefix(GcmSecretPayloadCodec.PREFIX).split(':', limit = 2)
        assertEquals(2, parts.size, "payload must contain iv and ciphertext")
        assertTrue(parts[0].isNotBlank(), "iv must not be blank")
        assertTrue(parts[1].isNotBlank(), "ciphertext must not be blank")
    }

    @Test
    fun `encrypt uses a fresh random iv per call`() {
        val codec = GcmSecretPayloadCodec(newKey())

        val first = codec.encrypt("same-plaintext")
        val second = codec.encrypt("same-plaintext")

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second, "GCM must randomize the IV per encryption")
    }

    @Test
    fun `decrypt tampered ciphertext returns Corrupt`() {
        val codec = GcmSecretPayloadCodec(newKey())
        val payload = codec.encrypt("top-secret")!!
        val parts = payload.removePrefix(GcmSecretPayloadCodec.PREFIX).split(':', limit = 2)
        val tamperedCipher = parts[1].replaceFirst(parts[1][0], if (parts[1][0] == 'A') 'B' else 'A')
        val tampered = GcmSecretPayloadCodec.PREFIX + parts[0] + ":" + tamperedCipher

        assertEquals(SecretStore.ReadResult.Corrupt, codec.decrypt(tampered))
    }

    @Test
    fun `decrypt malformed payload returns Corrupt`() {
        val codec = GcmSecretPayloadCodec(newKey())

        assertEquals(SecretStore.ReadResult.Corrupt, codec.decrypt(GcmSecretPayloadCodec.PREFIX + "not-two-parts"))
        assertEquals(SecretStore.ReadResult.Corrupt, codec.decrypt(GcmSecretPayloadCodec.PREFIX + "!!!:%%%"))
    }

    @Test
    fun `decrypt with a different key returns Corrupt`() {
        val writer = GcmSecretPayloadCodec(newKey())
        val other = GcmSecretPayloadCodec(newKey())
        val payload = writer.encrypt("secret")!!

        assertEquals(SecretStore.ReadResult.Corrupt, other.decrypt(payload))
    }

    @Test
    fun `encrypt with incompatible key returns null instead of throwing`() {
        val desKey = KeyGenerator.getInstance("DES").generateKey()
        val codec = GcmSecretPayloadCodec(desKey)

        assertNull(codec.encrypt("anything"))
    }
}