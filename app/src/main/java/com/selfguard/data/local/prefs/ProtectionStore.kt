package com.selfguard.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.protectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selfguard_protection",
)

data class ProtectionSnapshot(
    val armed: Boolean = false,
    /** Epoch millis until which release is blocked by the cooldown; 0 = none. */
    val cooldownUntil: Long = 0L,
    /** Epoch millis of the release request that started the cooldown; 0 = none. */
    val releaseRequestedAt: Long = 0L,
    /** PBKDF2 salt (hex); empty = no passphrase configured. */
    val passphraseSalt: String = "",
    /** PBKDF2 hash (hex); empty = no passphrase configured. */
    val passphraseHash: String = "",
    val failedAttempts: Int = 0,
    /** Epoch millis until which passphrase attempts are locked out; 0 = none. */
    val attemptsLockedUntil: Long = 0L,
) {
    val hasPassphrase: Boolean
        get() = passphraseSalt.isNotEmpty() && passphraseHash.isNotEmpty()
}

class ProtectionStore(private val context: Context) {

    private val store = context.protectionDataStore

    val protection: Flow<ProtectionSnapshot> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            ProtectionSnapshot(
                armed = prefs[KEY_ARMED] ?: false,
                cooldownUntil = prefs[KEY_COOLDOWN_UNTIL] ?: 0L,
                releaseRequestedAt = prefs[KEY_RELEASE_REQUESTED_AT] ?: 0L,
                passphraseSalt = prefs[KEY_PASSPHRASE_SALT] ?: "",
                passphraseHash = prefs[KEY_PASSPHRASE_HASH] ?: "",
                failedAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0,
                attemptsLockedUntil = prefs[KEY_ATTEMPTS_LOCKED_UNTIL] ?: 0L,
            )
        }

    suspend fun setArmed(armed: Boolean) {
        store.edit { it[KEY_ARMED] = armed }
    }

    suspend fun setCooldown(releaseRequestedAt: Long, cooldownUntil: Long) {
        store.edit {
            it[KEY_RELEASE_REQUESTED_AT] = releaseRequestedAt
            it[KEY_COOLDOWN_UNTIL] = cooldownUntil
        }
    }

    suspend fun clearCooldown() {
        store.edit {
            it[KEY_RELEASE_REQUESTED_AT] = 0L
            it[KEY_COOLDOWN_UNTIL] = 0L
        }
    }

    suspend fun setPassphrase(stored: com.selfguard.domain.protection.PassphraseHasher.Stored) {
        store.edit {
            it[KEY_PASSPHRASE_SALT] = stored.saltHex
            it[KEY_PASSPHRASE_HASH] = stored.hashHex
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_ATTEMPTS_LOCKED_UNTIL] = 0L
        }
    }

    suspend fun setFailedAttempts(count: Int, lockedUntil: Long) {
        store.edit {
            it[KEY_FAILED_ATTEMPTS] = count
            it[KEY_ATTEMPTS_LOCKED_UNTIL] = lockedUntil
        }
    }

    private companion object {
        val KEY_ARMED = booleanPreferencesKey("protection_armed")
        val KEY_COOLDOWN_UNTIL = longPreferencesKey("cooldown_until")
        val KEY_RELEASE_REQUESTED_AT = longPreferencesKey("release_requested_at")
        val KEY_PASSPHRASE_SALT = stringPreferencesKey("passphrase_salt")
        val KEY_PASSPHRASE_HASH = stringPreferencesKey("passphrase_hash")
        val KEY_FAILED_ATTEMPTS = intPreferencesKey("passphrase_failed_attempts")
        val KEY_ATTEMPTS_LOCKED_UNTIL = longPreferencesKey("passphrase_locked_until")
    }
}
