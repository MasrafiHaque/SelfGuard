package com.selfguard.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selfguard_settings",
)

data class SettingsSnapshot(
    val blockingEnabled: Boolean = true,
    val matchKeywordsInPageText: Boolean = true,
    val onboardingComplete: Boolean = false,
    val defaultsSeeded: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private val store = context.settingsDataStore

    val settings: Flow<SettingsSnapshot> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            SettingsSnapshot(
                blockingEnabled = prefs[KEY_BLOCKING_ENABLED] ?: true,
                matchKeywordsInPageText = prefs[KEY_MATCH_PAGE_TEXT] ?: true,
                onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] ?: false,
                defaultsSeeded = prefs[KEY_DEFAULTS_SEEDED] ?: false,
            )
        }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        store.edit { it[KEY_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setMatchKeywordsInPageText(enabled: Boolean) {
        store.edit { it[KEY_MATCH_PAGE_TEXT] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDefaultsSeeded(seeded: Boolean) {
        store.edit { it[KEY_DEFAULTS_SEEDED] = seeded }
    }

    private companion object {
        val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
        val KEY_MATCH_PAGE_TEXT = booleanPreferencesKey("match_keywords_in_page_text")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_DEFAULTS_SEEDED = booleanPreferencesKey("defaults_seeded")
    }
}
