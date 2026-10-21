package com.selfguard.domain.match

import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite

enum class RuleKind { DOMAIN, KEYWORD }

data class MatchedRule(
    val kind: RuleKind,
    val ruleId: Long,
    val label: String,
    val matchedText: String,
    val host: String?,
)

data class Candidate(
    val host: String?,
    val urlText: String?,
    val title: String?,
    val pageText: String?,
)

class RuleMatcher(private val publicSuffix: PublicSuffix) {

    fun match(
        candidate: Candidate,
        sites: List<BlockedSite>,
        keywords: List<BlockedKeyword>,
    ): MatchedRule? {
        matchDomain(candidate.host, sites)?.let { return it }
        return matchKeyword(candidate, keywords)
    }

    fun matchDomain(host: String?, sites: List<BlockedSite>): MatchedRule? {
        if (host.isNullOrEmpty()) return null
        for (rule in sites) {
            if (!rule.isEnabled) continue
            val target = HostNormalizer.normalizeHost(rule.domain) ?: continue
            val hit = host == target || (rule.includeSubdomains && host.endsWith("." + target))
            if (hit) {
                return MatchedRule(
                    kind = RuleKind.DOMAIN,
                    ruleId = rule.id,
                    label = rule.label.ifBlank { target },
                    matchedText = target,
                    host = host,
                )
            }
        }
        return null
    }

    fun matchKeyword(candidate: Candidate, keywords: List<BlockedKeyword>): MatchedRule? {
        for (rule in keywords) {
            if (!rule.isEnabled) continue
            val needle = rule.keyword.trim()
            if (needle.isEmpty()) continue
            if (candidate.urlText != null && candidate.urlText.contains(needle, ignoreCase = true)) {
                return keywordHit(rule, needle)
            }
            if (candidate.title != null && candidate.title.contains(needle, ignoreCase = true)) {
                return keywordHit(rule, needle)
            }
            if (rule.matchInPageText &&
                candidate.pageText != null &&
                candidate.pageText.contains(needle, ignoreCase = true)
            ) {
                return keywordHit(rule, needle)
            }
        }
        return null
    }

    fun isAcceptableDomain(domain: String): Boolean {
        val normalized = HostNormalizer.normalizeHost(domain) ?: return false
        return !publicSuffix.isPublicSuffix(normalized)
    }

    private fun keywordHit(rule: BlockedKeyword, needle: String) = MatchedRule(
        kind = RuleKind.KEYWORD,
        ruleId = rule.id,
        label = rule.label.ifBlank { needle },
        matchedText = needle,
        host = null,
    )
}
