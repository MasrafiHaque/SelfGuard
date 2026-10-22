package com.selfguard.data.repo

import android.util.Log
import com.selfguard.data.local.prefs.ProtectionSnapshot
import com.selfguard.data.local.prefs.ProtectionStore
import com.selfguard.domain.protection.PassphraseHasher
import com.selfguard.domain.protection.PassphrasePolicy
import com.selfguard.domain.protection.ReleaseGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface ReleaseOutcome {
    /** A cooldown was started; release becomes possible after [cooldownUntil]. */
    data class Started(val cooldownUntil: Long) : ReleaseOutcome

    /** A cooldown is still running. */
    data class InProgress(val remainingMs: Long) : ReleaseOutcome

    /** Protection is off. */
    data class Completed(val passphraseUsed: Boolean) : ReleaseOutcome

    data class WrongPassphrase(val attemptsLeft: Int) : ReleaseOutcome

    data class LockedOut(val remainingMs: Long) : ReleaseOutcome

    /** A passphrase path was used before any passphrase was configured. */
    data object NoPassphrase : ReleaseOutcome
}

/**
 * Arm / release bookkeeping (FR-4.7 / FR-4.8 / FR-4.10 / FR-4.11).
 *
 * Release is never instant: either the 24-hour cooldown elapses or the
 * partner passphrase is presented. The settings shield stays armed for the
 * whole cooldown. Uninstall blocking is only toggled when the Device Owner
 * role is held; a plain Device Admin cannot block uninstall.
 */
class ProtectionRepository(
    private val store: ProtectionStore,
    private val admin: DeviceAdminGateway,
    private val startWatchdog: () -> Unit,
    private val stopWatchdog: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val protection: Flow<ProtectionSnapshot> = store.protection

    suspend fun arm() {
        store.setArmed(true)
        store.clearCooldown()
        store.setFailedAttempts(0, 0)
        if (admin.deviceOwner) admin.setUninstallBlocked(true)
        startWatchdog()
        Log.i(TAG, "Protection armed (deviceOwner=${admin.deviceOwner})")
    }

    /** No-passphrase release: starts the cooldown, or completes it if it is over. */
    suspend fun beginRelease(): ReleaseOutcome {
        val snapshot = store.protection.first()
        if (!snapshot.armed) return ReleaseOutcome.Completed(passphraseUsed = false)
        val now = clock()
        if (snapshot.cooldownUntil == 0L) {
            val until = ReleaseGate.cooldownUntil(now)
            store.setCooldown(releaseRequestedAt = now, cooldownUntil = until)
            Log.i(TAG, "Release requested, cooldown until $until")
            return ReleaseOutcome.Started(cooldownUntil = until)
        }
        if (ReleaseGate.isCooldownActive(snapshot.cooldownUntil, now)) {
            return ReleaseOutcome.InProgress(
                remainingMs = ReleaseGate.remainingMs(snapshot.cooldownUntil, now),
            )
        }
        completeRelease(passphraseUsed = false)
        return ReleaseOutcome.Completed(passphraseUsed = false)
    }

    /** Passphrase release: skips the cooldown entirely when the hash matches. */
    suspend fun releaseWithPassphrase(phrase: String): ReleaseOutcome {
        val snapshot = store.protection.first()
        if (!snapshot.hasPassphrase) return ReleaseOutcome.NoPassphrase
        val now = clock()
        if (snapshot.attemptsLockedUntil > now) {
            return ReleaseOutcome.LockedOut(snapshot.attemptsLockedUntil - now)
        }
        val stored = PassphraseHasher.Stored(
            saltHex = snapshot.passphraseSalt,
            hashHex = snapshot.passphraseHash,
        )
        if (!PassphraseHasher.verify(phrase, stored)) {
            val failures = snapshot.failedAttempts + 1
            val lockedUntil = PassphrasePolicy.lockedUntil(failures, now) ?: 0L
            store.setFailedAttempts(failures, lockedUntil)
            Log.i(TAG, "Passphrase rejected ($failures/${PassphrasePolicy.MAX_FAILURES})")
            return if (lockedUntil > 0) {
                ReleaseOutcome.LockedOut(lockedUntil - now)
            } else {
                ReleaseOutcome.WrongPassphrase(
                    attemptsLeft = PassphrasePolicy.MAX_FAILURES - failures,
                )
            }
        }
        store.setFailedAttempts(0, 0)
        completeRelease(passphraseUsed = true)
        return ReleaseOutcome.Completed(passphraseUsed = true)
    }

    /** True once an active cooldown has elapsed (release button becomes final). */
    suspend fun canCompleteRelease(): Boolean {
        val snapshot = store.protection.first()
        if (!snapshot.armed) return false
        val now = clock()
        return snapshot.cooldownUntil > 0L &&
            !ReleaseGate.isCooldownActive(snapshot.cooldownUntil, now)
    }

    suspend fun cooldownRemainingMs(): Long {
        val snapshot = store.protection.first()
        return ReleaseGate.remainingMs(snapshot.cooldownUntil, clock())
    }

    /** Stores a PBKDF2 hash of [phrase]; the phrase itself is never persisted. */
    suspend fun setPassphrase(phrase: String) {
        val trimmed = phrase.trim()
        require(trimmed.isNotEmpty()) { "Passphrase must not be blank" }
        store.setPassphrase(PassphraseHasher.hash(trimmed))
        Log.i(TAG, "Passphrase stored")
    }

    private suspend fun completeRelease(passphraseUsed: Boolean) {
        store.setArmed(false)
        store.clearCooldown()
        store.setFailedAttempts(0, 0)
        if (admin.deviceOwner) admin.setUninstallBlocked(false)
        stopWatchdog()
        Log.i(TAG, "Protection released (passphraseUsed=$passphraseUsed)")
    }

    companion object {
        private const val TAG = "SelfGuard"
    }
}
