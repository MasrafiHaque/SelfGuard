package com.selfguard.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBlocklistTest {

    @Test
    fun `reads sections and skips comments`() {
        val parsed = DefaultBlocklist.parse(
            """
            # a comment
            # domains
            example.com
            # keywords
            porn
            """.trimIndent()
        )
        assertEquals(listOf("example.com"), parsed.domains)
        assertEquals(listOf("porn"), parsed.keywords)
    }

    @Test
    fun `infers type from the value when no header is present`() {
        val parsed = DefaultBlocklist.parse("example.com\nsomething\n")
        assertEquals(listOf("example.com"), parsed.domains)
        assertEquals(listOf("something"), parsed.keywords)
    }

    @Test
    fun `normalises domains and reports invalid lines`() {
        val parsed = DefaultBlocklist.parse(
            """
            # domains
            HTTPS://WWW.Example.com/path
            not a domain
            """.trimIndent()
        )
        assertEquals(listOf("example.com"), parsed.domains)
        assertTrue(parsed.skipped.any { it.contains("not a domain") })
    }

    @Test
    fun `removes duplicates`() {
        val parsed = DefaultBlocklist.parse("example.com\nexample.com\n")
        assertEquals(listOf("example.com"), parsed.domains)
    }
}
