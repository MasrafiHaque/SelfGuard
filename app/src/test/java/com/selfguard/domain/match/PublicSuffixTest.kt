package com.selfguard.domain.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSuffixTest {

    private val suffix = PublicSuffix(PublicSuffix.BUILT_IN)

    @Test
    fun `detects public suffixes`() {
        assertTrue(suffix.isPublicSuffix("com"))
        assertTrue(suffix.isPublicSuffix("co.uk"))
        assertTrue(suffix.isPublicSuffix("it.com"))
        assertFalse(suffix.isPublicSuffix("example.com"))
        assertFalse(suffix.isPublicSuffix("reddit.com"))
    }

    @Test
    fun `computes registrable domain`() {
        assertEquals("example.co.uk", suffix.registrableDomain("www.example.co.uk"))
        assertEquals("example.com", suffix.registrableDomain("a.b.example.com"))
    }
}
