package com.selfguard.domain.protection

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 passphrase hashing (FR-4.10). Only the salt and the
 * derived hash are stored; the phrase itself never persists anywhere.
 */
object PassphraseHasher {

    const val ITERATIONS = 200_000
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32

    data class Stored(val saltHex: String, val hashHex: String)

    fun hash(phrase: String, salt: ByteArray = randomSalt()): Stored {
        val hash = derive(phrase, salt, ITERATIONS, HASH_BYTES)
        return Stored(saltHex = salt.toHex(), hashHex = hash.toHex())
    }

    /** Constant-time verification of [phrase] against a stored salt + hash. */
    fun verify(phrase: String, stored: Stored): Boolean {
        val salt = stored.saltHex.hexToBytes() ?: return false
        val expected = stored.hashHex.hexToBytes() ?: return false
        val actual = derive(phrase, salt, ITERATIONS, expected.size)
        return constantTimeEquals(expected, actual)
    }

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derive(phrase: String, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val spec = PBEKeySpec(phrase.toCharArray(), salt, iterations, length * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { i ->
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            ((hi shl 4) or lo).toByte()
        }
    }
}

/** Failed-attempt rate limiting for the passphrase (FR-4.11). */
object PassphrasePolicy {

    const val MAX_FAILURES = 5
    const val LOCKOUT_MS = 15L * 60L * 1000L

    /** The moment a lockout ends, or null while attempts are still allowed. */
    fun lockedUntil(failCount: Int, now: Long): Long? =
        if (failCount >= MAX_FAILURES) now + LOCKOUT_MS else null
}
