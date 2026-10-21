package com.selfguard.data.local

import android.content.Context
import com.selfguard.domain.match.HostNormalizer
import com.selfguard.domain.match.PublicSuffix

object DefaultBlocklist {

    data class Defaults(
        val domains: List<String>,
        val keywords: List<String>,
        val skipped: List<String>,
    )

    private const val ASSET_FILE = "default_blocklist.txt"

    fun load(context: Context): Defaults = try {
        parse(context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        Defaults(emptyList(), emptyList(), emptyList())
    }

    fun parse(text: String): Defaults {
        val domains = LinkedHashSet<String>()
        val keywords = LinkedHashSet<String>()
        val skipped = mutableListOf<String>()
        var section: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                val header = line.removePrefix("#").trim().lowercase()
                if (header == "domains" || header == "domain") section = "domains"
                if (header == "keywords" || header == "keyword") section = "keywords"
                continue
            }
            val value = line.removePrefix("*.").trim().lowercase()
            if (value.isEmpty()) {
                skipped += rawLine
                continue
            }
            val isDomain = when (section) {
                "domains" -> true
                "keywords" -> false
                else -> value.contains('.')
            }
            if (isDomain) {
                val normalized = HostNormalizer.normalizeHost(value)
                if (normalized == null) {
                    skipped += rawLine
                } else {
                    domains += normalized
                }
            } else {
                keywords += value
            }
        }
        return Defaults(domains.toList(), keywords.toList(), skipped)
    }
}
