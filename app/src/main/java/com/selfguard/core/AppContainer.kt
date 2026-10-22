package com.selfguard.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.room.Room
import com.selfguard.data.local.AppCatalog
import com.selfguard.data.local.DefaultBlocklist
import com.selfguard.data.local.db.SelfGuardDatabase
import com.selfguard.data.local.prefs.SettingsStore
import com.selfguard.data.repo.BlocklistRepository
import com.selfguard.domain.app.CriticalApps
import com.selfguard.domain.app.ProtectionReason
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
        .addMigrations(SelfGuardDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    val settingsStore = SettingsStore(appContext)

    val publicSuffix: PublicSuffix = PublicSuffix(PublicSuffix.BUILT_IN + loadSuffixAssets())

    val ruleMatcher = RuleMatcher(publicSuffix)

    val appCatalog = AppCatalog(appContext)

    /**
     * Resolves the launcher and keyboard at call time (FR-2.6) so a later
     * change of either one is picked up without restarting the app.
     */
    fun protectReason(packageName: String): ProtectionReason? = CriticalApps.protectReason(
        packageName = packageName,
        selfPackage = appContext.packageName,
        launcherPackages = launcherPackages(),
        imePackage = imePackage(),
    )

    /** Every package that can act as the home launcher, so none of them can be blocked. */
    private fun launcherPackages(): Set<String> = runCatching {
        appContext.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0,
            )
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun imePackage(): String? = runCatching {
        Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.substringBefore('/')
    }.getOrNull()

    val blocklistRepository = BlocklistRepository(
        database = database,
        siteDao = database.siteDao(),
        keywordDao = database.keywordDao(),
        appDao = database.appDao(),
        ruleMatcher = ruleMatcher,
        protectReason = ::protectReason,
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
