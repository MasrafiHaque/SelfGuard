package com.selfguard.domain.match

import java.net.IDN
import java.util.Locale

object HostNormalizer {

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
    private val LABEL = Regex("^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?$")

    fun normalizeHost(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        if (s.isEmpty()) return null
        s = SCHEME.replace(s, "")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.lowercase(Locale.US)
        s = s.trimEnd('.')
        if (s.startsWith("*.")) s = s.substring(2)
        if (s.startsWith("www.")) s = s.substring(4)
        if (s.isEmpty()) return null
        s = toAscii(s)
        if (!isValidHost(s)) return null
        return s
    }

    fun toAscii(host: String): String {
        if (host.isEmpty()) return host
        if (host.all { it.isDigit() || it == '.' }) return host
        return try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
        } catch (e: IllegalArgumentException) {
            host
        }
    }

    fun isValidHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (host.startsWith(".") || host.contains("..")) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        return labels.all { label ->
            label.isNotEmpty() && label.length <= 63 && LABEL.matches(label)
        }
    }

    fun looksLikeUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (trimmed.length > 300) return false
        if (trimmed.contains(' ')) return false
        return normalizeHost(trimmed) != null
    }
}
