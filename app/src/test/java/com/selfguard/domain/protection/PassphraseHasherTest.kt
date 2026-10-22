package com.selfguard.domain.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseHasherTest {

    @Test
    fun `hash then verify roundtrip succeeds`() {
        val stored = PassphraseHasher.hash("correct horse battery staple")
        assertTrue(PassphraseHasher.verify("correct horse battery staple", stored))
    }

    @Test
    fun `wrong phrase fails verification`() {
        val stored = PassphraseHasher.hash("correct horse battery staple")
        assertFalse(PassphraseHasher.verify("Tr0ub4dor&3", stored))
    }

    @Test
    fun `same phrase produces different salts and hashes`() {
        val a = PassphraseHasher.hash("same phrase")
        val b = PassphraseHasher.hash("same phrase")
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
    }

    @Test
    fun `verify is deterministic for a stored salt`() {
        val stored = PassphraseHasher.hash("passphrase")
        val recomputed = PassphraseHasher.hash("passphrase", stored.saltHex.hexToBytesForTest())
        assertTrue(PassphraseHasher.verify("passphrase", recomputed))
        assertTrue(stored.hashHex == recomputed.hashHex)
    }

    @Test
    fun `corrupted hash is rejected instead of crashing`() {
        val stored = PassphraseHasher.hash("passphrase")
        val corrupted = stored.copy(hashHex = stored.hashHex.dropLast(2) + "ff")
        assertFalse(PassphraseHasher.verify("passphrase", corrupted))
    }

    @Test
    fun `malformed hex is rejected`() {
        assertFalse(
            PassphraseHasher.verify(
                "passphrase",
                PassphraseHasher.Stored(saltHex = "zz", hashHex = "zz"),
            ),
        )
    }

    @Test
    fun `constant time compare rejects different lengths`() {
        assertFalse(PassphraseHasher.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
        assertTrue(PassphraseHasher.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
    }

    @Test
    fun `lockout engages at the fifth failure`() {
        val now = 1_000_000L
        assertTrue(PassphrasePolicy.lockedUntil(5, now) == now + PassphrasePolicy.LOCKOUT_MS)
        assertTrue(PassphrasePolicy.lockedUntil(4, now) == null)
    }

    private fun String.hexToBytesForTest(): ByteArray =
        ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) or Character.digit(this[i * 2 + 1], 16))
                .toByte()
        }
}
