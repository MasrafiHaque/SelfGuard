package com.selfguard.domain.protection

/**
 * Release is never instant: either the 24-hour cooldown elapses or the
 * partner passphrase is presented (FR-4.7 / FR-4.8).
 */
object ReleaseGate {

    const val COOLDOWN_MS = 24L * 60L * 60L * 1000L

    fun cooldownUntil(startedAt: Long): Long = startedAt + COOLDOWN_MS

    fun remainingMs(cooldownUntil: Long, now: Long): Long =
        (cooldownUntil - now).coerceAtLeast(0L)

    fun isCooldownOver(cooldownUntil: Long, now: Long): Boolean =
        cooldownUntil > 0L && now >= cooldownUntil

    /** True when a cooldown is currently running (started, not yet expired). */
    fun isCooldownActive(cooldownUntil: Long, now: Long): Boolean =
        cooldownUntil > 0L && now < cooldownUntil
}
