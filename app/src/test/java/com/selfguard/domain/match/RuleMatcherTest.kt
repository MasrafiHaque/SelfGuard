package com.selfguard.domain.match

import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private val suffix = PublicSuffix(PublicSuffix.BUILT_IN)
    private val matcher = RuleMatcher(suffix)

    private val sites = listOf(
        BlockedSite(id = 1, domain = "pornhub.com", label = "pornhub.com"),
        BlockedSite(id = 2, domain = "example.com", label = "example.com"),
    )

    private val keywords = listOf(
        BlockedKeyword(id = 10, keyword = "porn", label = "porn"),
        BlockedKeyword(id = 11, keyword = "escort", label = "escort", matchInPageText = true),
    )

    @Test
    fun `blocks exact host`() {
        val result = matcher.match(
            Candidate(host = "pornhub.com", urlText = "https://pornhub.com/", title = null, pageText = null),
            sites,
            keywords,
        )
        assertEquals(RuleKind.DOMAIN, result?.kind)
        assertEquals(1L, result?.ruleId)
    }

    @Test
    fun `blocks subdomain of a blocked domain`() {
        val result = matcher.match(
            Candidate(host = "www.pornhub.com", urlText = null, title = null, pageText = null),
            sites,
            keywords,
        )
        assertEquals(RuleKind.DOMAIN, result?.kind)
    }

    @Test
    fun `does not block a different domain that merely ends with the same text`() {
        val result = matcher.match(
            Candidate(host = "notexample.com", urlText = null, title = null, pageText = null),
            sites,
            keywords,
        )
        assertNull(result)
    }

    @Test
    fun `blocking a public suffix is rejected`() {
        assertTrue(matcher.isAcceptableDomain("pornhub.com"))
        assertTrue(!matcher.isAcceptableDomain("it.com"))
        assertTrue(!matcher.isAcceptableDomain("co.uk"))
    }

    @Test
    fun `matches keyword in the address bar`() {
        val result = matcher.match(
            Candidate(host = "example.org", urlText = "example.org/porn-video", title = null, pageText = null),
            sites,
            keywords,
        )
        assertEquals(RuleKind.KEYWORD, result?.kind)
    }

    @Test
    fun `matches keyword in page text`() {
        val result = matcher.match(
            Candidate(
                host = "example.org",
                urlText = "https://example.org/",
                title = "Welcome",
                pageText = "Everything you want to know about escort services",
            ),
            sites,
            keywords,
        )
        assertEquals(RuleKind.KEYWORD, result?.kind)
        assertEquals(11L, result?.ruleId)
    }

    @Test
    fun `ignores disabled rules`() {
        val disabled = sites.map { it.copy(isEnabled = false) }
        val result = matcher.match(
            Candidate(host = "pornhub.com", urlText = null, title = null, pageText = null),
            disabled,
            keywords,
        )
        assertNull(result)
    }
}
