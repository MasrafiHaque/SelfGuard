package com.selfguard.data.repo

import androidx.room.withTransaction
import com.selfguard.data.local.DefaultBlocklist
import com.selfguard.data.local.db.BlockedAppDao
import com.selfguard.data.local.db.BlockedAppEntity
import com.selfguard.data.local.db.BlockedKeywordDao
import com.selfguard.data.local.db.BlockedKeywordEntity
import com.selfguard.data.local.db.BlockedSiteDao
import com.selfguard.data.local.db.BlockedSiteEntity
import com.selfguard.data.local.db.SelfGuardDatabase
import com.selfguard.domain.app.ProtectionReason
import com.selfguard.domain.match.HostNormalizer
import com.selfguard.domain.match.RuleMatcher
import com.selfguard.domain.model.BlockedApp
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class AddResult {
    data object Added : AddResult()
    data class Rejected(val reason: String) : AddResult()
    data object Duplicate : AddResult()
}

class BlocklistRepository(
    private val database: SelfGuardDatabase,
    private val siteDao: BlockedSiteDao,
    private val keywordDao: BlockedKeywordDao,
    private val appDao: BlockedAppDao,
    private val ruleMatcher: RuleMatcher,
    private val protectReason: (String) -> ProtectionReason?,
) {

    val sites: Flow<List<BlockedSite>> = siteDao.observeAll().map { list ->
        list.map { entity ->
            BlockedSite(
                id = entity.id,
                domain = entity.domain,
                label = entity.label,
                includeSubdomains = entity.includeSubdomains,
                isEnabled = entity.isEnabled,
                source = entity.source,
                addedAt = entity.addedAt,
            )
        }
    }

    val keywords: Flow<List<BlockedKeyword>> = keywordDao.observeAll().map { list ->
        list.map { entity ->
            BlockedKeyword(
                id = entity.id,
                keyword = entity.keyword,
                label = entity.label,
                matchInPageText = entity.matchInPageText,
                isEnabled = entity.isEnabled,
                source = entity.source,
                addedAt = entity.addedAt,
            )
        }
    }

    val apps: Flow<List<BlockedApp>> = appDao.observeAll().map { list ->
        list.map { entity ->
            BlockedApp(
                id = entity.id,
                packageName = entity.packageName,
                label = entity.label,
                isEnabled = entity.isEnabled,
                source = entity.source,
                addedAt = entity.addedAt,
            )
        }
    }

    suspend fun addSite(rawDomain: String): AddResult {
        val normalized = HostNormalizer.normalizeHost(rawDomain)
            ?: return AddResult.Rejected("Not a valid domain")
        if (!ruleMatcher.isAcceptableDomain(normalized)) {
            return AddResult.Rejected("That is a public suffix, blocking it would break other sites")
        }
        val existing = siteDao.getAll()
        if (existing.any { it.domain == normalized }) return AddResult.Duplicate
        siteDao.insert(
            BlockedSiteEntity(
                domain = normalized,
                label = normalized,
                source = BlockedSite.SOURCE_USER,
                addedAt = System.currentTimeMillis(),
            )
        )
        return AddResult.Added
    }

    suspend fun addKeyword(rawKeyword: String): AddResult {
        val keyword = rawKeyword.trim().lowercase()
        if (keyword.isEmpty()) return AddResult.Rejected("Keyword is empty")
        if (keyword.length < 3) return AddResult.Rejected("Keyword is too short, it would match everything")
        val existing = keywordDao.getAll()
        if (existing.any { it.keyword == keyword }) return AddResult.Duplicate
        keywordDao.insert(
            BlockedKeywordEntity(
                keyword = keyword,
                label = keyword,
                source = BlockedKeyword.SOURCE_USER,
                addedAt = System.currentTimeMillis(),
            )
        )
        return AddResult.Added
    }

    suspend fun addApp(packageName: String, label: String): AddResult {
        val clean = packageName.trim()
        if (clean.isEmpty()) return AddResult.Rejected("Package name is empty")
        protectReason(clean)?.let { return AddResult.Rejected(it.userMessage(clean)) }
        val existing = appDao.getAll()
        if (existing.any { it.packageName == clean }) return AddResult.Duplicate
        appDao.insert(
            BlockedAppEntity(
                packageName = clean,
                label = label.trim().ifBlank { clean },
                source = BlockedApp.SOURCE_USER,
                addedAt = System.currentTimeMillis(),
            )
        )
        return AddResult.Added
    }

    suspend fun deleteApp(id: Long) = appDao.delete(id)

    suspend fun deleteAppByPackage(packageName: String) {
        appDao.getAll().filter { it.packageName == packageName }.forEach { appDao.delete(it.id) }
    }

    suspend fun setSiteEnabled(id: Long, enabled: Boolean) = siteDao.setEnabled(id, enabled)

    suspend fun setKeywordEnabled(id: Long, enabled: Boolean) = keywordDao.setEnabled(id, enabled)

    suspend fun deleteSite(id: Long) = siteDao.delete(id)

    suspend fun deleteKeyword(id: Long) = keywordDao.delete(id)

    suspend fun seedIfNeeded(defaults: DefaultBlocklist.Defaults): Boolean {
        if (siteDao.count() > 0 || keywordDao.count() > 0) return false
        insertDefaults(defaults)
        return true
    }

    /** Resets the list to exactly the bundled defaults, dropping user entries. */
    suspend fun restoreDefaults(defaults: DefaultBlocklist.Defaults) {
        database.withTransaction {
            siteDao.deleteAll()
            keywordDao.deleteAll()
            insertDefaults(defaults)
        }
    }

    private suspend fun insertDefaults(defaults: DefaultBlocklist.Defaults) {
        val now = System.currentTimeMillis()
        val sites = defaults.domains.map {
            BlockedSiteEntity(
                domain = it,
                label = it,
                source = BlockedSite.SOURCE_DEFAULT,
                addedAt = now,
            )
        }
        val keywords = defaults.keywords.map {
            BlockedKeywordEntity(
                keyword = it,
                label = it,
                source = BlockedKeyword.SOURCE_DEFAULT,
                addedAt = now,
            )
        }
        if (sites.isNotEmpty()) siteDao.insertAll(sites)
        if (keywords.isNotEmpty()) keywordDao.insertAll(keywords)
    }
}
