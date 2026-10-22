package com.selfguard.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseGateTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `cooldown is twenty four hours`() {
        assertEquals(24L * 60 * 60 * 1000, ReleaseGate.COOLDOWN_MS)
        assertEquals(now + ReleaseGate.COOLDOWN_MS, ReleaseGate.cooldownUntil(now))
    }

    @Test
    fun `cooldown is active before it expires`() {
        val until = ReleaseGate.cooldownUntil(now)
        assertTrue(ReleaseGate.isCooldownActive(until, now + 1))
        assertFalse(ReleaseGate.isCooldownOver(until, now + 1))
    }

    @Test
    fun `cooldown ends exactly at the deadline`() {
        val until = ReleaseGate.cooldownUntil(now)
        assertFalse(ReleaseGate.isCooldownActive(until, until))
        assertTrue(ReleaseGate.isCooldownOver(until, until))
    }

    @Test
    fun `remaining time never goes negative`() {
        val until = ReleaseGate.cooldownUntil(now)
        assertEquals(0L, ReleaseGate.remainingMs(until, until + 12345))
        assertEquals(1000L, ReleaseGate.remainingMs(until, until - 1000))
    }

    @Test
    fun `zero cooldown means nothing is pending`() {
        assertFalse(ReleaseGate.isCooldownActive(0L, now))
        assertFalse(ReleaseGate.isCooldownOver(0L, now))
    }
}
