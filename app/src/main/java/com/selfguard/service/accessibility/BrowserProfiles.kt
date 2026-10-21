package com.selfguard.service.accessibility

object BrowserProfiles {

    val BROWSER_PACKAGES: Set<String> = setOf(
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.alpha",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.microsoft.emmx.playstore",
        "com.microsoft.msedge",
        "com.opera.browser",
        "com.opera.gx",
        "com.opera.mini.native",
        "com.opera.news",
        "com.duckduckgo.mobile.android",
        "com.android.browser",
        "com.android.chrome.webapps",
        "com.UCMobile.intl",
        "com.uc.mobile.browser",
        "org.torproject.torbrowser",
        "com.kiwibrowser.browser",
        "com.hzy.opera",
        "mark.via.gp",
        "com.kaolin.tinybrowser",
        "com.termux",
        "com.lookerbrowser.android",
        "org.fdroid.fdroid",
    )

    private val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/location_bar",
            "com.android.chrome:id/url_bar_container",
            "com.android.chrome:id/search_omnibox_textbox",
        ),
        "com.google.android.apps.chrome" to listOf(
            "com.google.android.apps.chrome:id/url_bar",
            "com.google.android.apps.chrome:id/location_bar",
        ),
        "com.sec.android.app.sbrowser" to listOf(
            "com.sec.android.app.sbrowser:id/keyword",
            "com.sec.android.app.sbrowser:id/url",
            "com.sec.android.app.sbrowser:id/url_text",
            "com.sec.android.app.sbrowser:id/address_bar",
            "com.sec.android.app.sbrowser:id/search_keyword",
        ),
        "org.mozilla.firefox" to listOf(
            "org.mozilla.firefox:id/url",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/toolbar_button",
        ),
        "com.brave.browser" to listOf(
            "com.brave.browser:id/url_bar",
            "com.brave.browser:id/location_bar",
        ),
        "com.microsoft.emmx" to listOf(
            "com.microsoft.emmx:id/url_bar",
            "com.microsoft.emmx:id/search_text",
            "com.microsoft.emmx:id/url_text",
        ),
        "com.opera.browser" to listOf(
            "com.opera.browser:id/url_field",
            "com.opera.browser:id/url_text",
        ),
        "com.duckduckgo.mobile.android" to listOf(
            "com.duckduckgo.mobile.android:id/urlInput",
            "com.duckduckgo.mobile.android:id/urlEditText",
        ),
        "com.android.browser" to listOf(
            "com.android.browser:id/url",
            "com.android.browser:id/url_bar",
        ),
        "com.UCMobile.intl" to listOf(
            "com.UCMobile.intl:id/webViewUrlEditText",
            "com.UCMobile.intl:id/urlText",
        ),
        "org.torproject.torbrowser" to listOf(
            "org.torproject.torbrowser:id/url_display",
        ),
    )

    fun isBrowser(packageName: String): Boolean = packageName in BROWSER_PACKAGES

    fun isUrlBarId(packageName: String, viewIdResourceName: String?): Boolean {
        if (viewIdResourceName.isNullOrEmpty()) return false
        val candidates = URL_BAR_IDS[packageName] ?: return false
        return candidates.any { it.equals(viewIdResourceName, ignoreCase = true) }
    }
}
