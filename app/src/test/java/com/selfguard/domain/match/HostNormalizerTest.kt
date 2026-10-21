package com.selfguard.domain.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostNormalizerTest {

    @Test
    fun `strips scheme www port and path`() {
        assertEquals("example.com", HostNormalizer.normalizeHost("https://www.example.com/path?a=1"))
        assertEquals("example.com", HostNormalizer.normalizeHost("HTTP://Example.com"))
        assertEquals("example.com", HostNormalizer.normalizeHost("example.com:8080"))
        assertEquals("example.com", HostNormalizer.normalizeHost("  example.com  "))
        assertEquals("example.com", HostNormalizer.normalizeHost("example.com."))
        assertEquals("example.com", HostNormalizer.normalizeHost("user:pass@example.com"))
        assertEquals("example.com", HostNormalizer.normalizeHost("*.example.com"))
    }

    @Test
    fun `rejects text that is not a host`() {
        assertNull(HostNormalizer.normalizeHost("Search or type web address"))
        assertNull(HostNormalizer.normalizeHost("localhost"))
        assertNull(HostNormalizer.normalizeHost(""))
        assertNull(HostNormalizer.normalizeHost(null))
        assertNull(HostNormalizer.normalizeHost("not a url at all"))
    }

    @Test
    fun `keeps subdomains`() {
        assertEquals("video.example.com", HostNormalizer.normalizeHost("video.example.com"))
        assertEquals("video.example.com", HostNormalizer.normalizeHost("www.video.example.com"))
    }
}
