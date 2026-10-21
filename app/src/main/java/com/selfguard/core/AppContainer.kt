package com.selfguard.core

import android.content.Context
import androidx.room.Room
import com.selfguard.data.local.DefaultBlocklist
import com.selfguard.data.local.db.SelfGuardDatabase
import com.selfguard.data.local.prefs.SettingsStore
import com.selfguard.data.repo.BlocklistRepository
import com.selfguard.domain.match.PublicSuffix
import com.selfguard.domain.match.RuleMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SelfGuardDatabase = Room
        .databaseBuilder(appContext, SelfGuardDatabase::class.java, "selfguard.db")
        .fallbackToDestructiveMigration()
        .build()

    val settingsStore = SettingsStore(appContext)

    val publicSuffix: PublicSuffix = PublicSuffix(PublicSuffix.BUILT_IN + loadSuffixAssets())

    val ruleMatcher = RuleMatcher(publicSuffix)

    val blocklistRepository = BlocklistRepository(
        database = database,
        siteDao = database.siteDao(),
        keywordDao = database.keywordDao(),
        ruleMatcher = ruleMatcher,
    )

    suspend fun seedDefaultsIfNeeded() {
        val defaults = DefaultBlocklist.load(appContext)
        blocklistRepository.seedIfNeeded(defaults)
        settingsStore.setDefaultsSeeded(true)
    }

    fun currentDefaults(): DefaultBlocklist.Defaults = DefaultBlocklist.load(appContext)

    private fun loadSuffixAssets(): Set<String> = try {
        appContext.assets.open("public_suffixes.txt")
            .bufferedReader()
            .use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toSet()
            }
    } catch (e: Exception) {
        emptySet()
    }
}
